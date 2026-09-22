package com.bihstudio.madafim.data.repository

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.bihstudio.madafim.domain.analytics.AnalyticsLogger
import com.bihstudio.madafim.domain.analytics.AnalyticsEvent
import com.bihstudio.madafim.domain.analytics.AnalyticsParam
import com.bihstudio.madafim.domain.model.SubscriptionOffer
import com.bihstudio.madafim.domain.model.SubscriptionPlan
import com.bihstudio.madafim.domain.model.SubscriptionState
import com.bihstudio.madafim.domain.repository.SubscriptionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaySubscriptionRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val analytics: AnalyticsLogger,
) : SubscriptionRepository {
    private val _state = MutableStateFlow(SubscriptionState())
    override val state: StateFlow<SubscriptionState> = _state.asStateFlow()
    private val detailsByProduct = mutableMapOf<String, ProductDetails>()

    private val billingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            val outcome = when {
                result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> "cancelled"
                result.responseCode != BillingClient.BillingResponseCode.OK -> "failure"
                purchases.orEmpty().any { it.purchaseState == Purchase.PurchaseState.PENDING } -> "pending"
                purchases.orEmpty().any { it.purchaseState == Purchase.PurchaseState.PURCHASED } -> "purchased"
                else -> "empty"
            }
            analytics.track(AnalyticsEvent.PURCHASE_RESULT, mapOf(
                AnalyticsParam.RESULT to outcome,
                "response_code" to result.responseCode,
                AnalyticsParam.SOURCE to "purchase_update",
            ))
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.orEmpty().forEach(::acknowledgeIfNeeded)
                refreshPurchases()
            } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                _state.value = _state.value.copy(errorMessage = result.debugMessage)
            }
        }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    init { connect() }

    override fun refresh() {
        if (billingClient.isReady) loadStoreState() else connect()
    }

    override fun launchPurchase(activity: Activity, offer: SubscriptionOffer) {
        val details = detailsByProduct[offer.plan.productId] ?: run {
            analytics.track(AnalyticsEvent.PURCHASE_RESULT, mapOf(AnalyticsParam.RESULT to "unavailable", "product_id" to offer.plan.productId))
            _state.value = _state.value.copy(errorMessage = "This plan is not available yet.")
            refresh()
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offer.offerToken)
            .build()
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build(),
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            analytics.track(AnalyticsEvent.PURCHASE_RESULT, mapOf(
                AnalyticsParam.RESULT to if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) "cancelled" else "failure",
                AnalyticsParam.SOURCE to "launch",
                "product_id" to offer.plan.productId,
                "response_code" to result.responseCode,
            ))
            _state.value = _state.value.copy(errorMessage = result.debugMessage)
        }
    }

    private fun trackBilling(operation: String, result: BillingResult) {
        analytics.track(AnalyticsEvent.BILLING_RESULT, mapOf(
            AnalyticsParam.SOURCE to operation,
            AnalyticsParam.RESULT to if (result.responseCode == BillingClient.BillingResponseCode.OK) "success" else "failure",
            "response_code" to result.responseCode,
        ))
    }

    private fun connect() {
        if (billingClient.isReady) return loadStoreState()
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                trackBilling("connection", result)
                if (result.responseCode == BillingClient.BillingResponseCode.OK) loadStoreState()
                else _state.value = SubscriptionState(isLoading = false, errorMessage = result.debugMessage)
            }

            override fun onBillingServiceDisconnected() {
                _state.value = _state.value.copy(isLoading = false, errorMessage = "Google Play is unavailable.")
            }
        })
    }

    private fun loadStoreState() {
        queryOffers()
        refreshPurchases()
    }

    private fun queryOffers() {
        val products = SubscriptionPlan.entries.map { plan ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(plan.productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(products).build(),
        ) { result, queryResult ->
            trackBilling("offers", result)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = result.debugMessage)
                return@queryProductDetailsAsync
            }
            detailsByProduct.clear()
            queryResult.productDetailsList.forEach { detailsByProduct[it.productId] = it }
            val offers = queryResult.productDetailsList.mapNotNull { details ->
                val plan = SubscriptionPlan.entries.firstOrNull { it.productId == details.productId }
                    ?: return@mapNotNull null
                val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return@mapNotNull null
                val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
                SubscriptionOffer(plan, phase.formattedPrice, phase.billingPeriod, offer.offerToken)
            }.sortedBy { it.plan.ordinal }
            _state.value = _state.value.copy(offers = offers, isLoading = false, errorMessage = null)
        }
    }

    private fun refreshPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ) { result, purchases ->
            trackBilling("restore_purchases", result)
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach(::acknowledgeIfNeeded)
                val isPro = purchases.any { purchase ->
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        purchase.products.any { id -> SubscriptionPlan.entries.any { it.productId == id } }
                }
                _state.value = _state.value.copy(isPro = isPro, isLoading = false)
            }
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) return
        billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
        ) { result ->
            trackBilling("acknowledge", result)
            if (result.responseCode == BillingClient.BillingResponseCode.OK) refreshPurchases()
        }
    }
}
