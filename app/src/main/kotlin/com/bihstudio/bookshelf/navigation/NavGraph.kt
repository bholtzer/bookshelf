package com.bihstudio.bookshelf.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.bihstudio.bookshelf.presentation.auth.AuthScreen
import com.bihstudio.bookshelf.presentation.bookshelf.BookShelfScreen
import com.bihstudio.bookshelf.presentation.detail.BookDetailScreen
import com.bihstudio.bookshelf.presentation.opening.OpeningScreen
import com.bihstudio.bookshelf.presentation.viewer.BookViewerScreen

object Route {
    const val OPENING    = "opening"
    const val AUTH       = "auth"
    const val BOOKSHELF  = "com.bihstudio.bookshelf"
    const val VIEWER     = "viewer/{bookId}"
    const val DETAIL     = "detail/{bookId}"

    fun viewer(bookId: String) = "viewer/$bookId"
    fun detail(bookId: String) = "detail/$bookId"
}

@Composable
fun BookshelfNavGraph(
    navController: NavHostController,
    startDestination: String,
    afterOpeningDestination: String,
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Route.OPENING) {
            OpeningScreen(
                onFinished = {
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
                onOpenBook = { bookId -> navController.navigate(Route.viewer(bookId)) },
                onEditBook = { bookId -> navController.navigate(Route.detail(bookId)) },
            )
        }

        composable(Route.VIEWER) { back ->
            val bookId = back.arguments?.getString("bookId") ?: return@composable
            BookViewerScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Route.detail(bookId)) },
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
