package com.bihstudio.bookshelf

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.bihstudio.bookshelf.domain.usecase.auth.GetCurrentUserUseCase
import com.bihstudio.bookshelf.navigation.BookshelfNavGraph
import com.bihstudio.bookshelf.navigation.Route
import com.bihstudio.bookshelf.presentation.share.ShareReceiverBottomSheet
import com.bihstudio.bookshelf.presentation.share.ShareReceiverViewModel
import com.bihstudio.bookshelf.ui.theme.BookShelfTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var getCurrentUser: GetCurrentUserUseCase

    private val shareViewModel: ShareReceiverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            BookShelfTheme {
                val navController = rememberNavController()
                val startDestination = if (getCurrentUser() != null) {
                    Route.BOOKSHELF
                } else {
                    Route.AUTH
                }

                BookshelfNavGraph(
                    navController    = navController,
                    startDestination = Route.OPENING,
                    afterOpeningDestination = startDestination,
                )

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.clipData?.getItemAt(0)?.uri
                    ?: @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                val mimeType = intent.type ?: return
                if (uri != null) {
                    shareViewModel.onUrisReceived(listOf(uri.toString()), mimeType)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.clipData?.let { clip ->
                    (0 until clip.itemCount).map { clip.getItemAt(it).uri.toString() }
                } ?: emptyList()
                val mimeType = intent.type ?: return
                if (uris.isNotEmpty()) {
                    shareViewModel.onUrisReceived(uris, mimeType)
                }
            }
        }
    }
}
