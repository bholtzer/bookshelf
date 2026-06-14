package com.bihstudio.bookshelf.data.local.db

import androidx.room.*
import com.bihstudio.bookshelf.data.local.model.BookEntity
import com.bihstudio.bookshelf.data.local.model.PageEntity
import kotlinx.coroutines.flow.Flow

// ── BookDao ───────────────────────────────────────────────────────────────────

@Dao
interface BookDao {

    @Query(
        """
        SELECT * FROM books
        WHERE ownerId = :userId OR sharedEditorIds LIKE '%' || :editorToken || '%'
        ORDER BY updatedAt DESC
        """
    )
    fun observeAccessibleBooks(userId: String, editorToken: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBook(bookId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBook(bookId: String)

    /** Returns all books pending upload (isSynced = false). */
    @Query("SELECT * FROM books WHERE ownerId = :ownerId AND isSynced = 0")
    suspend fun getUnsyncedBooks(ownerId: String): List<BookEntity>

    @Query(
        """
        SELECT * FROM books
        WHERE isSynced = 0
        AND (ownerId = :userId OR sharedEditorIds LIKE '%' || :editorToken || '%')
        """
    )
    suspend fun getUnsyncedAccessibleBooks(userId: String, editorToken: String): List<BookEntity>

    /** Bulk-insert used during remote restore. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Query("UPDATE books SET isSynced = 1 WHERE id = :bookId")
    suspend fun markSynced(bookId: String)

    @Query("UPDATE books SET pageCount = :count, updatedAt = :updatedAt, isSynced = 0 WHERE id = :bookId")
    suspend fun updatePageCount(bookId: String, count: Int, updatedAt: Long)
}

// ── PageDao ───────────────────────────────────────────────────────────────────

@Dao
interface PageDao {

    @Query("SELECT * FROM pages WHERE bookId = :bookId ORDER BY position ASC")
    fun observePages(bookId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE id IN (:pageIds)")
    fun observePagesByIds(pageIds: List<String>): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE id = :pageId LIMIT 1")
    suspend fun getPage(pageId: String): PageEntity?

    @Query("SELECT COUNT(*) FROM pages WHERE bookId = :bookId")
    suspend fun countPages(bookId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PageEntity)

    @Update
    suspend fun updatePage(page: PageEntity)

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deletePage(pageId: String)

    @Query("SELECT * FROM pages WHERE ownerId = :ownerId AND isSynced = 0")
    suspend fun getUnsyncedPages(ownerId: String): List<PageEntity>

    @Query(
        """
        SELECT pages.* FROM pages
        INNER JOIN books ON books.id = pages.bookId
        WHERE pages.isSynced = 0
        AND (books.ownerId = :userId OR books.sharedEditorIds LIKE '%' || :editorToken || '%')
        """
    )
    suspend fun getUnsyncedPagesForAccessibleBooks(userId: String, editorToken: String): List<PageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>)

    @Query("UPDATE pages SET isSynced = 1, remoteUrl = :remoteUrl WHERE id = :pageId")
    suspend fun markSynced(pageId: String, remoteUrl: String)

    @Query("UPDATE pages SET syncError = :error WHERE id = :pageId")
    suspend fun markSyncError(pageId: String, error: String)

    @Query("UPDATE pages SET removalSuggestedByIds = :suggestedByIds, isSynced = 0 WHERE id = :pageId")
    suspend fun updateRemovalSuggestions(pageId: String, suggestedByIds: String)

    /** Used by reorder: update position for one page. */
    @Query("UPDATE pages SET position = :position, isSynced = 0 WHERE id = :pageId")
    suspend fun updatePosition(pageId: String, position: Int)

    @Query("SELECT * FROM pages WHERE bookId = :bookId ORDER BY position ASC")
    suspend fun getPagesForBook(bookId: String): List<PageEntity>
}
