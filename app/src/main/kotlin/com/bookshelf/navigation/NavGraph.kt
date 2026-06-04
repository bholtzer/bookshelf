package com.bookshelf.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.bookshelf.presentation.auth.AuthScreen
import com.bookshelf.presentation.bookshelf.BookShelfScreen
import com.bookshelf.presentation.detail.BookDetailScreen
import com.bookshelf.presentation.viewer.BookViewerScreen

sealed class Route(val path: String) {
    data object Auth        : Route("auth")
    data object BookShelf   : Route("bookshelf")
    data class  BookViewer(val bookId: String = "{bookId}") : Route("viewer/{bookId}") {
        fun go(id: String) = "viewer/$id"
    }
    data class  BookDetail(val bookId: String = "{bookId}") : Route("detail/{bookId}") {
        fun go(id: String) = "detail/$id"
    }
}

@Composable
fun BookshelfNavGraph(
    navController: NavHostController,
    startDestination: String,
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Route.Auth.path) {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate(Route.BookShelf.path) {
                        popUpTo(Route.Auth.path) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.BookShelf.path) {
            BookShelfScreen(
                onOpenBook = { bookId ->
                    navController.navigate(Route.BookViewer().go(bookId))
                },
                onEditBook = { bookId ->
                    navController.navigate(Route.BookDetail().go(bookId))
                },
            )
        }

        composable(Route.BookViewer().path) { backStack ->
            val bookId = backStack.arguments?.getString("bookId") ?: return@composable
            BookViewerScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Route.BookDetail().go(bookId)) },
            )
        }

        composable(Route.BookDetail().path) { backStack ->
            val bookId = backStack.arguments?.getString("bookId") ?: return@composable
            BookDetailScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
