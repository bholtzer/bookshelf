package com.bihstudio.bookshelf.data.repository

import android.content.Context
import android.net.Uri
import com.bihstudio.bookshelf.data.local.db.BookDao
import com.bihstudio.bookshelf.data.local.db.PageDao
import com.bihstudio.bookshelf.data.local.model.PageEntity
import com.bihstudio.bookshelf.data.local.model.toEntity
import com.bihstudio.bookshelf.data.local.model.toDomain
import com.bihstudio.bookshelf.data.local.model.toRemovalSuggestionIdStorage
import com.bihstudio.bookshelf.data.remote.SyncWorker
import com.bihstudio.bookshelf.domain.model.AppResult
import com.bihstudio.bookshelf.domain.model.Page
import com.bihstudio.bookshelf.domain.repository.PageRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pageDao: PageDao,
    private val bookDao: BookDao,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) : PageRepository {

    override fun observePages(bookId: String): Flow<List<Page>> =
        pageDao.observePages(bookId).map { list -> list.map { it.toDomain() } }

    override fun observePagesByIds(pageIds: List<String>): Flow<List<Page>> =
        pageDao.observePagesByIds(pageIds).map { list -> list.map { it.toDomain() } }

    override suspend fun getPage(pageId: String): Page? =
        pageDao.getPage(pageId)?.toDomain()

    // ── Add page ──────────────────────────────────────────────────────────────

    override suspend fun addPage(page: Page, sourceUri: String): AppResult<Page> = runCatching {
        // 1. Copy the incoming URI into app-internal storage so it survives
        //    content:// URI expiry and the original app being uninstalled.
        val localFile = copyToInternalStorage(sourceUri, page.id, page.pageType.name.lowercase())
        val localUri  = localFile.absolutePath

        // 2. Determine insert position (append if -1)
        val position = if (page.position < 0) {
            pageDao.countPages(page.bookId)
        } else {
            page.position
        }

        val savedPage = page.copy(localUri = localUri, position = position)
        pageDao.insertPage(savedPage.toEntity())

        // 3. Update book's pageCount + updatedAt
        val newCount = pageDao.countPages(page.bookId)
        bookDao.updatePageCount(
            bookId    = page.bookId,
            count     = newCount,
            updatedAt = Instant.now().toEpochMilli(),
        )
        SyncWorker.enqueueImmediateSync(context)

        savedPage
    }.toAppResult()

    // ── Reorder ───────────────────────────────────────────────────────────────

    override suspend fun reorderPages(
        bookId: String,
        orderedIds: List<String>,
    ): AppResult<Unit> = runCatching {
        orderedIds.forEachIndexed { index, pageId ->
            pageDao.updatePosition(pageId, index)
        }
    }.toAppResult()

    // ── Delete ────────────────────────────────────────────────────────────────

    override suspend fun deletePage(pageId: String): AppResult<Unit> = runCatching {
        val entity = pageDao.getPage(pageId) ?: return@runCatching
        pageDao.deletePage(pageId)

        // Delete local file
        entity.localUri?.let { File(it).delete() }

        // Best-effort remote delete
        entity.remoteUrl?.let {
            runCatching { storage.getReferenceFromUrl(it).delete().await() }
        }

        // Update book pageCount
        val newCount = pageDao.countPages(entity.bookId)
        bookDao.updatePageCount(
            bookId    = entity.bookId,
            count     = newCount,
            updatedAt = Instant.now().toEpochMilli(),
        )
        SyncWorker.enqueueImmediateSync(context)
    }.toAppResult()

    override suspend fun recommendPageRemoval(pageId: String, userId: String): AppResult<Page> = runCatching {
        val entity = pageDao.getPage(pageId) ?: error("Page not found")
        val page = entity.toDomain()
        val updated = page.copy(
            removalSuggestedByIds = (page.removalSuggestedByIds + userId).distinct(),
            isSynced = false,
        )
        pageDao.updateRemovalSuggestions(
            pageId = pageId,
            suggestedByIds = updated.removalSuggestedByIds.toRemovalSuggestionIdStorage(),
        )
        SyncWorker.enqueueImmediateSync(context)
        updated
    }.toAppResult()

    // ── Sync: upload pending pages to Firebase Storage ────────────────────────

    override suspend fun uploadPendingPages(ownerId: String): AppResult<Int> = runCatching {
        val unsynced = pageDao.getUnsyncedPagesForAccessibleBooks(
            userId = ownerId,
            editorToken = "|$ownerId|",
        )
        var count = 0
        val failures = mutableListOf<String>()
        var firstFailure: Throwable? = null
        for (entity in unsynced) {
            try {
                val bookOwnerId = bookDao.getBook(entity.bookId)?.ownerId ?: entity.ownerId
                val localFile = entity.localUri?.let { File(it) }
                if ((entity.remoteUrl == null || entity.remoteUrl.isBlank()) && (localFile == null || !localFile.exists())) {
                    pageDao.markSyncError(entity.id, "Local file missing")
                    failures += "${entity.originalFileName.ifBlank { entity.id }}: local file missing"
                    continue
                }

                // Upload to: users/{ownerId}/books/{bookId}/pages/{pageId}.ext
                val downloadUrl = if (localFile != null && localFile.exists()) {
                    val extension = localFile.extension
                    val remotePath = "users/$bookOwnerId/books/${entity.bookId}/pages/${entity.id}.$extension"
                    val ref = storage.reference.child(remotePath)
                    ref.putFile(Uri.fromFile(localFile)).await()
                    ref.downloadUrl.await().toString()
                } else {
                    entity.remoteUrl.orEmpty()
                }

                // Update Firestore page document
                pagesCollection(bookOwnerId, entity.bookId)
                    .document(entity.id)
                    .set(entity.toFirestoreMap(downloadUrl), SetOptions.merge())
                    .await()

                pageDao.markSynced(entity.id, downloadUrl)
                count++
            } catch (e: Exception) {
                if (firstFailure == null) firstFailure = e
                val message = e.toPageSyncMessage()
                pageDao.markSyncError(entity.id, message)
                failures += "${entity.originalFileName.ifBlank { entity.id }}: $message"
            }
        }
        if (failures.isNotEmpty()) {
            throw PageUploadBatchException(
                message = failures.joinToString(prefix = "Page upload failed: ", separator = "; "),
                cause = firstFailure,
            )
        }
        count
    }.toAppResult()

    // ── Sync: pull from Firestore (restore) ───────────────────────────────────

    override suspend fun syncFromRemote(ownerId: String, bookId: String): AppResult<Int> = runCatching {
        // Pages under a book are stored at: users/{uid}/books/{bookId}/pages
        // We'd need the ownerId here — in practice the SyncWorker passes it separately.
        // For now, no-op placeholder — full implementation wired in SyncWorker.
        val snapshot = pagesCollection(ownerId, bookId).get().await()
        val pages = snapshot.documents.mapNotNull { doc ->
            doc.toPageEntity(ownerId, bookId)
        }.map { entity -> entity.withDownloadedPdf() }
        pageDao.insertPages(pages)
        pages.size
    }.toAppResult()

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun copyToInternalStorage(sourceUri: String, pageId: String, ext: String): File {
        val dir = File(context.filesDir, "pages").also { it.mkdirs() }
        val dest = File(dir, "$pageId.$ext")
        context.contentResolver.openInputStream(Uri.parse(sourceUri))
            ?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            ?: error("Cannot open input stream for $sourceUri")
        return dest
    }

    private fun pagesCollection(ownerId: String, bookId: String) =
        firestore.collection("users")
            .document(ownerId)
            .collection("books")
            .document(bookId)
            .collection("pages")

    private fun com.bihstudio.bookshelf.data.local.model.PageEntity.toFirestoreMap(
        downloadUrl: String,
    ): Map<String, Any?> = mapOf(
        "id"               to id,
        "bookId"           to bookId,
        "ownerId"          to ownerId,
        "position"         to position,
        "pageType"         to pageType,
        "remoteUrl"        to downloadUrl,
        "originalFileName" to originalFileName,
        "removalSuggestedByIds" to removalSuggestedByIds.toIdList(),
        "createdAt"        to createdAt,
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toPageEntity(
        ownerId: String,
        bookId: String,
    ): PageEntity? {
        val id = getString("id") ?: return null
        return PageEntity(
            id = id,
            bookId = getString("bookId") ?: bookId,
            ownerId = getString("ownerId") ?: ownerId,
            position = getLong("position")?.toInt() ?: 0,
            pageType = getString("pageType") ?: return null,
            localUri = null,
            remoteUrl = getString("remoteUrl"),
            originalFileName = getString("originalFileName") ?: "",
            removalSuggestedByIds = get("removalSuggestedByIds").toStringList().toRemovalSuggestionIdStorage(),
            createdAt = getLong("createdAt") ?: Instant.now().toEpochMilli(),
            isSynced = true,
            syncError = null,
        )
    }

    private suspend fun PageEntity.withDownloadedPdf(): PageEntity {
        if (pageType != com.bihstudio.bookshelf.domain.model.PageType.PDF.name) return this
        val url = remoteUrl?.takeIf { it.isNotBlank() } ?: return this
        val dir = File(context.filesDir, "pages").also { it.mkdirs() }
        val destination = File(dir, "$id.pdf")
        storage.getReferenceFromUrl(url).getFile(destination).await()
        return copy(localUri = destination.absolutePath)
    }
}

private fun String.toIdList(): List<String> =
    split("|").filter { it.isNotBlank() }

private fun Any?.toStringList(): List<String> =
    (this as? List<*>)?.mapNotNull { it as? String }.orEmpty()

private fun <T> Result<T>.toAppResult(): AppResult<T> =
    fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { error -> AppResult.Error(error.toPageSyncMessage(), error) },
    )

private fun Throwable.toPageSyncMessage(): String {
    val storageError = findCause<StorageException>()
    return when {
        storageError?.httpResultCode == 404 ->
            "Cloud backup is not available: Firebase Storage returned 404. Upgrade the Firebase project to Blaze, then open Storage and create the default bucket."
        storageError?.errorCode == StorageException.ERROR_NOT_AUTHENTICATED ->
            "Sign in again before uploading book pages."
        storageError?.errorCode == StorageException.ERROR_NOT_AUTHORIZED ->
            "Firebase Storage rules denied access to this book's pages."
        else -> message ?: "Unknown page synchronization error"
    }
}

private class PageUploadBatchException(
    message: String,
    cause: Throwable?,
) : Exception(message, cause)

private inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return current
        current = current.cause
    }
    return null
}
