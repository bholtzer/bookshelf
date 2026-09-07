package com.bihstudio.madafim.domain.model

enum class SubscriptionPlan(val productId: String) {
    MONTHLY("bookshelf_pro_monthly"),
    YEARLY("bookshelf_pro_yearly"),
}

data class SubscriptionOffer(
    val plan: SubscriptionPlan,
    val formattedPrice: String,
    val billingPeriod: String,
    val offerToken: String,
)

data class SubscriptionState(
    val isPro: Boolean = false,
    val offers: List<SubscriptionOffer> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
