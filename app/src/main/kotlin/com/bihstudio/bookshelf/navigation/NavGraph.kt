package com.bihstudio.bookshelf.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.bihstudio.bookshelf.domain.analytics.AnalyticsEvent
import com.bihstudio.bookshelf.domain.analytics.AnalyticsLogger
import com.bihstudio.bookshelf.domain.analytics.AnalyticsParam
import com.bihstudio.bookshelf.presentation.auth.AuthScreen
import com.bihstudio.bookshelf.presentation.bookshelf.BookShelfScreen
import com.bihstudio.bookshelf.presentation.detail.BookDetailScreen
import com.bihstudio.bookshelf.presentation.opening.OpeningScreen
import com.bihstudio.bookshelf.presentation.subscription.PaywallScreen
import com.bihstudio.bookshelf.presentation.viewer.BookViewerScreen

object Route {
    const val OPENING    = "opening"
    const val AUTH       = "auth"
    const val BOOKSHELF  = "com.bihstudio.bookshelf"
    const val VIEWER     = "viewer/{bookId}"
    const val DETAIL     = "detail/{bookId}"
    const val PRO        = "pro"

    fun viewer(bookId: String) = "viewer/$bookId"
    fun detail(bookId: String) = "detail/$bookId"
}

@Composable
fun BookshelfNavGraph(
    navController: NavHostController,
    startDestination: String,
    afterOpeningDestination: String,
    analytics: AnalyticsLogger,
    pendingBookInvite: String?,
    onBookInviteConsumed: () -> Unit,
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Route.OPENING) {
            OpeningScreen(
                onFinished = {
                    analytics.track(
                        AnalyticsEvent.OPENING_FINISHED,
                        mapOf(AnalyticsParam.SOURCE to afterOpeningDestination),
                    )
                    navController.navigate(afterOpeningDestination) {
                        popUpTo(Route.OPENING) { inclusive = true }
                    }
                },
            )
        }

        composable(Route.AUTH) {
            AuthScreen(onAuthSuccess = {
                navController.navigate(Route.BOOKSHELF) {
                    popUpTo(Route.AUTH) { inclusive = true }
                }
            })
        }

        composable(Route.BOOKSHELF) {
            BookShelfScreen(
                onUpgrade = { navController.navigate(Route.PRO) },
                onOpenBook = { bookId ->
                    analytics.track(
                        AnalyticsEvent.BOOK_OPENED,
                        mapOf(
                            AnalyticsParam.BOOK_ID to bookId,
                            AnalyticsParam.SOURCE to "shelf",
                        ),
                    )
                    navController.navigate(Route.viewer(bookId))
                },
                onEditBook = { bookId ->
                    analytics.track(
                        AnalyticsEvent.BOOK_EDIT_OPENED,
                        mapOf(
                            AnalyticsParam.BOOK_ID to bookId,
                            AnalyticsParam.SOURCE to "shelf",
                        ),
                    )
                    navController.navigate(Route.detail(bookId))
                },
                pendingInviteText = pendingBookInvite,
                onInviteConsumed = onBookInviteConsumed,
            )
        }

        composable(Route.PRO) {
            PaywallScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.VIEWER) { back ->
            val bookId = back.arguments?.getString("bookId") ?: return@composable
            BookViewerScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() },
                onEdit = {
                    analytics.track(
                        AnalyticsEvent.BOOK_EDIT_OPENED,
                        mapOf(
                            AnalyticsParam.BOOK_ID to bookId,
                            AnalyticsParam.SOURCE to "viewer",
                        ),
                    )
                    navController.navigate(Route.detail(bookId))
                },
            )
        }

        composable(Route.DETAIL) { back ->
            val bookId = back.arguments?.getString("bookId") ?: return@composable
            BookDetailScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
