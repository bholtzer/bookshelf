package com.bihstudio.madafim.data.analytics

import android.os.Bundle
import com.bihstudio.madafim.domain.analytics.AnalyticsLogger
import com.bihstudio.madafim.domain.analytics.AnalyticsParam
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAnalyticsLogger @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics,
) : AnalyticsLogger {

    override fun setUserId(userId: String?) {
        firebaseAnalytics.setUserId(userId)
    }

    override fun trackScreen(screenName: String) {
        track(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            mapOf(
                FirebaseAnalytics.Param.SCREEN_NAME to screenName,
                AnalyticsParam.SCREEN_NAME to screenName,
            ),
        )
    }

    override fun track(eventName: String, params: Map<String, Any?>) {
        firebaseAnalytics.logEvent(eventName, params.toBundle())
    }
}

private fun Map<String, Any?>.toBundle(): Bundle =
    Bundle().also { bundle ->
        forEach { (key, value) ->
            when (value) {
                null -> Unit
                is String -> bundle.putString(key, value.take(100))
                is Int -> bundle.putLong(key, value.toLong())
                is Long -> bundle.putLong(key, value)
                is Float -> bundle.putDouble(key, value.toDouble())
                is Double -> bundle.putDouble(key, value)
                is Boolean -> bundle.putString(key, if (value) "true" else "false")
                else -> bundle.putString(key, value.toString().take(100))
            }
        }
    }
