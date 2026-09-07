package com.bihstudio.madafim

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.bihstudio.madafim.domain.analytics.AnalyticsEvent
import com.bihstudio.madafim.domain.analytics.AnalyticsLogger
import com.bihstudio.madafim.domain.model.extractBookInviteCode
import com.bihstudio.madafim.domain.model.extractBookInviteTarget
import com.bihstudio.madafim.domain.usecase.auth.GetCurrentUserUseCase
import com.bihstudio.madafim.navigation.BookshelfNavGraph
import com.bihstudio.madafim.navigation.Route
import com.bihstudio.madafim.presentation.share.ShareReceiverBottomSheet
import com.bihstudio.madafim.presentation.share.ShareReceiverViewModel
import com.bihstudio.madafim.ui.theme.BookShelfTheme
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLDecoder
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var getCurrentUser: GetCurrentUserUseCase
    @Inject lateinit var analytics: AnalyticsLogger

    private val shareViewModel: ShareReceiverViewModel by viewModels()
    private var pendingInitialBookInvite: String? = null
    private var pendingBookInviteState: MutableState<String?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        analytics.track(AnalyticsEvent.APP_OPENED)
        handleIncomingIntent(intent)
        loadDeferredBookInvite()

        setContent {
            BookShelfTheme {
                val navController = rememberNavController()
                val pendingBookInvite = remember { mutableStateOf(pendingInitialBookInvite) }
                SideEffect {
                    pendingBookInviteState = pendingBookInvite
                }
                val startDestination = if (getCurrentUser() != null) {
                    Route.BOOKSHELF
                } else {
                    Route.AUTH
                }

                BookshelfNavGraph(
                    navController    = navController,
                    startDestination = Route.OPENING,
                    afterOpeningDestination = startDestination,
                    analytics = analytics,
                    pendingBookInvite = pendingBookInvite.value,
                    onBookInviteConsumed = { pendingBookInvite.value = null },
                )

                val sharedUris by shareViewModel.pendingUris.collectAsStateWithLifecycle()
                if (sharedUris.isNotEmpty()) {
                    ShareReceiverBottomSheet(
                        uris      = sharedUris,
                        onDismiss = { shareViewModel.dismissPending() },
                        viewModel = shareViewModel,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        Log.d("BookShelfIntent", "action=${intent?.action}, type=${intent?.type}, data=${intent?.data}")
        when (intent?.action) {
            Intent.ACTION_VIEW -> {
                val invite = intent.data?.toString()?.toBookInviteText()
                if (invite != null) {
                    setPendingBookInvite(invite)
                } else {
                    val uri = intent.data
                    val mimeType = uri?.let { intent.type ?: contentResolver.getType(it) ?: it.toMimeTypeFromPath() }
                    if (uri != null && mimeType != null && mimeType.isSupportedBookshelfFile()) {
                        shareViewModel.onUrisReceived(listOf(toSharedUri(uri, mimeType)))
                    }
                }
            }
            Intent.ACTION_SEND -> {
                val uri = intent.clipData?.getItemAt(0)?.uri
                    ?: @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                val mimeType = intent.type ?: return
                if (uri != null) {
                    shareViewModel.onUrisReceived(listOf(toSharedUri(uri, mimeType)))
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val mimeType = intent.type ?: return
                val uris = intent.clipData?.let { clip ->
                    (0 until clip.itemCount).map { toSharedUri(clip.getItemAt(it).uri, mimeType) }
                } ?: emptyList()
                if (uris.isNotEmpty()) {
                    shareViewModel.onUrisReceived(uris)
                }
            }
        }
    }

    private fun loadDeferredBookInvite() {
        val client = InstallReferrerClient.newBuilder(this).build()
        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                    runCatching {
                        client.installReferrer.installReferrer
                            .toDeferredBookInviteText()
                            ?.let(::setPendingBookInvite)
                    }
                }
                client.endConnection()
            }

            override fun onInstallReferrerServiceDisconnected() = Unit
        })
    }

    private fun setPendingBookInvite(inviteText: String) {
        pendingInitialBookInvite = inviteText
        pendingBookInviteState?.value = inviteText
    }
}

private fun ComponentActivity.toDisplayName(uri: Uri): String =
    runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }.orEmpty()
    }.getOrDefault("")

private fun ComponentActivity.toSharedUri(uri: Uri, mimeType: String): com.bihstudio.madafim.presentation.share.SharedUri =
    com.bihstudio.madafim.presentation.share.SharedUri(
        uri = uri.toString(),
        mimeType = mimeType,
        displayName = toDisplayName(uri).ifBlank { uri.lastPathSegment.orEmpty() },
    )

private fun String.isSupportedBookshelfFile(): Boolean =
    startsWith("image/") || equals("application/pdf", ignoreCase = true)

private fun Uri.toMimeTypeFromPath(): String? {
    val path = toString().lowercase()
    return when {
        path.endsWith(".pdf") -> "application/pdf"
        path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".webp") -> "image/webp"
        else -> null
    }
}

private fun String.toBookInviteText(): String? {
    val code = extractBookInviteCode() ?: return null
    val target = extractBookInviteTarget()
    return if (target == null) {
        "madafim://book-invite/$code"
    } else {
        this
    }
}

private fun String.toDeferredBookInviteText(): String? {
    val decoded = URLDecoder.decode(this, Charsets.UTF_8.name())
    val inviteLink = decoded.substringAfter("book_invite_link=", missingDelimiterValue = "")
        .substringBefore("&")
        .takeIf { it.isNotBlank() }
        ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
        ?.toBookInviteText()
    if (inviteLink != null) return inviteLink

    val code = decoded.substringAfter("book_invite=", missingDelimiterValue = "")
        .substringBefore("&")
        .extractBookInviteCode()
        ?: decoded.extractBookInviteCode()
        ?: return null
    return "madafim://book-invite/$code"
}
