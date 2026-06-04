package com.bookshelf

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.bookshelf.domain.usecase.auth.GetCurrentUserUseCase
import com.bookshelf.navigation.BookshelfNavGraph
import com.bookshelf.navigation.Route
import com.bookshelf.presentation.share.ShareReceiverBottomSheet
import com.bookshelf.presentation.share.ShareReceiverViewModel
import com.bookshelf.ui.theme.BookShelfTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var getCurrentUser: GetCurrentUserUseCase

    private val shareViewModel: ShareReceiverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle share intent that launched this activity
        handleIncomingIntent(intent)

        setContent {
            BookShelfTheme {
                val navController = rememberNavController()
                val startDestination = if (getCurrentUser() != null) {
                    Route.BookShelf.path
                } else {
                    Route.Auth.path
                }

                BookshelfNavGraph(
                    navController    = navController,
                    startDestination = startDestination,
                )

                // Share receiver bottom sheet — appears on top of whatever screen is active
                val sharedUris by shareViewModel.pendingUris.collectAsStateWithLifecycle()
                if (sharedUris.isNotEmpty()) {
                    ShareReceiverBottomSheet(
                        uris      = sharedUris,
                        onDismiss = { shareViewModel.clearPending() },
                    )
                }
            }
        }
    }

    // Handle share intents arriving while the app is already running
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.clipData?.getItemAt(0)?.uri
                    ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
                val mimeType = intent.type ?: "application/octet-stream"
                if (uri != null) {
                    shareViewModel.onUrisReceived(listOf(uri.toString()), mimeType)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.clipData?.let { clip ->
                    (0 until clip.itemCount).map { clip.getItemAt(it).uri.toString() }
                } ?: emptyList()
                val mimeType = intent.type ?: "application/octet-stream"
                if (uris.isNotEmpty()) {
                    shareViewModel.onUrisReceived(uris, mimeType)
                }
            }
        }
    }
}
