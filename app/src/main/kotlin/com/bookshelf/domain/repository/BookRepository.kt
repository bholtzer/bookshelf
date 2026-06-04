package com.bookshelf.domain.repository

import com.bookshelf.domain.model.AppResult
import com.bookshelf.domain.model.Book
import kotlinx.coroutines.flow.Flow

interface BookRepository {

    /** Live stream of all books belonging to the current user, ordered by updatedAt desc. */
    fun observeBooks(ownerId: String): Flow<List<Book>>

    /** Single book by ID, or null if not found. */
    suspend fun getBook(bookId: String): Book?

    suspend fun createBook(book: Book): AppResult<Book>

    suspend fun updateBook(book: Book): AppResult<Book>

    suspend fun deleteBook(bookId: String): AppResult<Unit>

    /**
     * Push local changes to Firestore. Called by WorkManager's SyncWorker.
     * Returns the number of books successfully synced.
     */
    suspend fun syncToRemote(ownerId: String): AppResult<Int>

    /**
     * Pull all books from Firestore and merge into local Room DB.
     * Used on first login or after account restore.
     */
    suspend fun syncFromRemote(ownerId: String): AppResult<Int>
}
