package com.bihstudio.madafim.domain.repository

import android.app.Activity
import com.bihstudio.madafim.domain.model.SubscriptionOffer
import com.bihstudio.madafim.domain.model.SubscriptionState
import kotlinx.coroutines.flow.StateFlow

interface SubscriptionRepository {
    val state: StateFlow<SubscriptionState>
    fun refresh()
    fun launchPurchase(activity: Activity, offer: SubscriptionOffer)
}
