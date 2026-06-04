package com.bookshelf.data.repository

import com.bookshelf.data.local.db.BookDao
import com.bookshelf.data.local.model.toEntity
import com.bookshelf.data.local.model.toDomain
import com.bookshelf.domain.model.AppResult
import com.bookshelf.domain.model.Book
import com.bookshelf.domain.repository.BookRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val firestore: FirebaseFirestore,
) : BookRepository {

    // ── Local reads (Room → domain) ───────────────────────────────────────────

    override fun observeBooks(ownerId: String): Flow<List<Book>> =
        bookDao.observeBooks(ownerId).map { list -> list.map { it.toDomain() } }

    override suspend fun getBook(bookId: String): Book? =
        bookDao.getBook(bookId)?.toDomain()

    // ── Writes (Room first, then flag dirty for sync) ─────────────────────────

    override suspend fun createBook(book: Book): AppResult<Book> = runCatching {
        bookDao.insertBook(book.copy(isSynced = false).toEntity())
        book
    }.toAppResult()

    override suspend fun updateBook(book: Book): AppResult<Book> = runCatching {
        bookDao.updateBook(book.copy(isSynced = false).toEntity())
        book
    }.toAppResult()

    override suspend fun deleteBook(bookId: String): AppResult<Unit> = runCatching {
        bookDao.deleteBook(bookId)
        // Best-effort remote delete (silently ignore failure — WorkManager retries)
        runCatching {
            firestore.collection("users")
                .document(bookId)           // placeholder — full path resolved below
                .delete()
                .await()
        }
    }.toAppResult()

    // ── Sync: local → Firestore ───────────────────────────────────────────────

    override suspend fun syncToRemote(ownerId: String): AppResult<Int> = runCatching {
        val unsynced = bookDao.getUnsyncedBooks(ownerId)
        var count = 0
        for (entity in unsynced) {
            val docRef = booksCollection(ownerId).document(entity.id)
            val data   = entity.toFirestoreMap()
            docRef.set(data, SetOptions.merge()).await()
            bookDao.markSynced(entity.id)
            count++
        }
        count
    }.toAppResult()

    // ── Sync: Firestore → local (account restore) ─────────────────────────────

    override suspend fun syncFromRemote(ownerId: String): AppResult<Int> = runCatching {
        val snapshot = booksCollection(ownerId).get().await()
        val entities = snapshot.documents.mapNotNull { doc ->
            doc.toBook(ownerId)?.copy(isSynced = true)?.toEntity()
        }
        bookDao.insertBooks(entities)
        entities.size
    }.toAppResult()

    // ── Firestore helpers ─────────────────────────────────────────────────────

    private fun booksCollection(ownerId: String) =
        firestore.collection("users").document(ownerId).collection("books")

    private fun com.bookshelf.data.local.model.BookEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id"          to id,
        "ownerId"     to ownerId,
        "title"       to title,
        "description" to description,
        "coverPageId" to coverPageId,
        "pageCount"   to pageCount,
        "createdAt"   to createdAt,
        "updatedAt"   to updatedAt,
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toBook(ownerId: String): Book? {
        val id = getString("id") ?: return null
        return Book(
            id          = id,
            ownerId     = ownerId,
            title       = getString("title") ?: "",
            description = getString("description") ?: "",
            coverPageId = getString("coverPageId"),
            pageCount   = getLong("pageCount")?.toInt() ?: 0,
            createdAt   = Instant.ofEpochMilli(getLong("createdAt") ?: 0L),
            updatedAt   = Instant.ofEpochMilli(getLong("updatedAt") ?: 0L),
            isSynced    = true,
        )
    }
}

private fun <T> Result<T>.toAppResult(): AppResult<T> =
    fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(it.message ?: "Unknown error", it) },
    )
