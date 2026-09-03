package com.bihstudio.bookshelf.domain.repository

import com.bihstudio.bookshelf.domain.model.AppResult
import com.bihstudio.bookshelf.domain.model.Page
import kotlinx.coroutines.flow.Flow

interface PageRepository {

    /** Live stream of all pages for a book, ordered by position. */
    fun observePages(bookId: String): Flow<List<Page>>

    /** Live stream of selected pages, used for book cover thumbnails. */
    fun observePagesByIds(pageIds: List<String>): Flow<List<Page>>

    /** Live stream of the first page for each book, used for open-book previews. */
    fun observeFirstPagesForBooks(bookIds: List<String>): Flow<Map<String, Page>>

    suspend fun getPage(pageId: String): Page?

    /**
     * Add a new page to a book.
     * Copies the file from the given URI into internal storage so the
     * original content:// URI doesn't become stale, then queues a background
     * upload to Firebase Storage.
     */
    suspend fun addPage(page: Page, sourceUri: String): AppResult<Page>

    /**
     * Reorder pages. [orderedIds] must contain all current page IDs for the book.
     * Updates the position field for each and marks them dirty for sync.
     */
    suspend fun reorderPages(bookId: String, orderedIds: List<String>): AppResult<Unit>

    suspend fun deletePage(pageId: String): AppResult<Unit>

    suspend fun recommendPageRemoval(pageId: String, userId: String): AppResult<Page>

    /**
     * Upload any locally-stored pages that haven't been pushed to Firebase Storage yet.
     * Called by SyncWorker.
     */
    suspend fun uploadPendingPages(ownerId: String): AppResult<Int>

    /**
     * Download all remote pages for a book (after account restore).
     */
    suspend fun syncFromRemote(ownerId: String, bookId: String): AppResult<Int>
}
