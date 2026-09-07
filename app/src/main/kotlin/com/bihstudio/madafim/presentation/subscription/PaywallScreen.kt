package com.bihstudio.madafim.presentation.subscription

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bihstudio.madafim.domain.analytics.AnalyticsEvent
import com.bihstudio.madafim.domain.analytics.AnalyticsLogger
import com.bihstudio.madafim.domain.analytics.AnalyticsParam
import com.bihstudio.madafim.domain.model.SubscriptionOffer
import com.bihstudio.madafim.domain.model.SubscriptionPlan
import com.bihstudio.madafim.domain.repository.SubscriptionRepository
import com.bihstudio.madafim.presentation.legal.LegalUrls
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val subscriptions: SubscriptionRepository,
    private val analytics: AnalyticsLogger,
) : ViewModel() {
    val state = subscriptions.state

    init {
        analytics.trackScreen("pro_paywall")
        analytics.track(AnalyticsEvent.PAYWALL_VIEWED)
        subscriptions.refresh()
    }

    fun purchase(activity: Activity, offer: SubscriptionOffer) {
        analytics.track(
            AnalyticsEvent.PURCHASE_STARTED,
            mapOf(AnalyticsParam.SOURCE to offer.plan.productId),
        )
        subscriptions.launchPurchase(activity, offer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onBack: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()

    LaunchedEffect(state.isPro) { if (state.isPro) onBack() }

    Scaffold(
        containerColor = Color(0xFF050B18),
        topBar = {
            TopAppBar(
                title = { Text("MaDaFim Pro") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF00F2FF), Color(0xFF7000FF))),
                        RoundedCornerShape(28.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Text("Unlock your complete archive", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text("More books, secure cloud sync, advanced PDF tools and upcoming full-text OCR search.", color = Color.White.copy(alpha = 0.65f), textAlign = TextAlign.Center)

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Unlimited books", "Cloud backup and device sync", "Advanced PDF export", "More collaborators", "OCR search when available").forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Text(feature, color = Color.White)
                    }
                }
            }

            when {
                state.isLoading -> CircularProgressIndicator()
                state.offers.isEmpty() -> Text(
                    state.errorMessage ?: "Plans will appear after they are activated in Google Play.",
                    color = Color.White.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                )
                else -> state.offers.forEach { offer ->
                    val annual = offer.plan == SubscriptionPlan.YEARLY
                    Surface(
                        onClick = { if (activity != null) viewModel.purchase(activity, offer) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (annual) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, if (annual) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)),
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (annual) "YEARLY · BEST VALUE" else "MONTHLY", color = if (annual) MaterialTheme.colorScheme.primary else Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(if (annual) "One payment every year" else "Flexible monthly access", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(offer.formattedPrice, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            Text("Subscriptions renew automatically until cancelled in Google Play.", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            OutlinedButton(onClick = { context.openUrl(LegalUrls.MANAGE_SUBSCRIPTIONS) }, modifier = Modifier.fillMaxWidth()) { Text("Manage or cancel subscription") }
            Row(horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { context.openUrl(LegalUrls.TERMS) }) { Text("Terms") }
                TextButton(onClick = { context.openUrl(LegalUrls.PRIVACY) }) { Text("Privacy") }
            }
        }
    }
}

private fun Context.openUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
