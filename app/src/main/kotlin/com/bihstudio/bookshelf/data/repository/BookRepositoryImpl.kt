package com.bihstudio.bookshelf.data.repository

import android.content.Context
import com.bihstudio.bookshelf.data.local.db.BookDao
import com.bihstudio.bookshelf.data.local.model.BookEntity
import com.bihstudio.bookshelf.data.local.model.toEntity
import com.bihstudio.bookshelf.data.local.model.toDomain
import com.bihstudio.bookshelf.data.remote.SyncWorker
import com.bihstudio.bookshelf.domain.model.AppResult
import com.bihstudio.bookshelf.domain.model.Book
import com.bihstudio.bookshelf.domain.repository.BookRepository
import com.bihstudio.bookshelf.domain.repository.PageRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val pageRepository: PageRepository,
    private val firestore: FirebaseFirestore,
) : BookRepository {

    // ── Local reads (Room → domain) ───────────────────────────────────────────

    override fun observeBooks(ownerId: String): Flow<List<Book>> =
        bookDao.observeAccessibleBooks(
            userId = ownerId,
            editorToken = ownerId.toEditorToken(),
        ).map { list -> list.map { it.toDomain() } }

    override suspend fun getBook(bookId: String): Book? =
        bookDao.getBook(bookId)?.toDomain()

    // ── Writes (Room first, then flag dirty for sync) ─────────────────────────

    override suspend fun createBook(book: Book): AppResult<Book> = runCatching {
        bookDao.insertBook(book.copy(isSynced = false).toEntity())
        SyncWorker.enqueueImmediateSync(context)
        book
    }.toAppResult()

    override suspend fun updateBook(book: Book): AppResult<Book> = runCatching {
        bookDao.updateBook(book.copy(isSynced = false).toEntity())
        SyncWorker.enqueueImmediateSync(context)
        book
    }.toAppResult()

    override suspend fun shareBookWithEditor(
        bookId: String,
        ownerId: String,
        editorUserId: String,
    ): AppResult<Book> = runCatching {
        val book = bookDao.getBook(bookId)?.toDomain() ?: error("Book not found")
        require(book.ownerId == ownerId) { "Only the book owner can share editor access" }
        require(editorUserId.isNotBlank()) { "Editor user id cannot be empty" }

        val updated = book.copy(
            sharedEditorIds = (book.sharedEditorIds + editorUserId.trim()).distinct(),
            updatedAt = Instant.now(),
            isSynced = false,
        )
        bookDao.updateBook(updated.toEntity())
        SyncWorker.enqueueImmediateSync(context)
        updated
    }.toAppResult()

    override suspend fun deleteBook(bookId: String): AppResult<Unit> = runCatching {
        val book = bookDao.getBook(bookId)?.toDomain()
        bookDao.deleteBook(bookId)
        // Best-effort remote delete (silently ignore failure — WorkManager retries)
        runCatching {
            book?.ownerId?.let { ownerId ->
                firestore.collection("users")
                    .document(ownerId)
                    .collection("books")
                    .document(bookId)
                    .delete()
                    .await()
            }
        }
        Unit
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
        val ownedSnapshot = booksCollection(ownerId).get().await()
        val sharedSnapshot = firestore.collectionGroup("books")
            .whereArrayContains("sharedEditorIds", ownerId)
            .get()
            .await()

        val entities = (ownedSnapshot.documents + sharedSnapshot.documents)
            .distinctBy { it.id }
            .mapNotNull { doc ->
                val bookOwnerId = doc.getString("ownerId") ?: ownerId
                doc.toBook(bookOwnerId)?.copy(isSynced = true)?.toEntity()
            }
        val books = entities.map { it.toDomain() }
        bookDao.insertBooks(entities)
        books.forEach { book ->
            pageRepository.syncFromRemote(book.ownerId, book.id)
        }
        books.size
    }.toAppResult()

    // ── Firestore helpers ─────────────────────────────────────────────────────

    private fun booksCollection(ownerId: String) =
        firestore.collection("users").document(ownerId).collection("books")

    private fun BookEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id"          to id,
        "ownerId"     to ownerId,
        "title"       to title,
        "description" to description,
        "coverPageId" to coverPageId,
        "sharedEditorIds" to sharedEditorIds.toEditorIdList(),
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
            sharedEditorIds = get("sharedEditorIds").toStringList(),
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

private fun String.toEditorToken(): String = "|$this|"

private fun String.toEditorIdList(): List<String> =
    split("|").filter { it.isNotBlank() }

private fun Any?.toStringList(): List<String> =
    (this as? List<*>)?.mapNotNull { it as? String }.orEmpty()
