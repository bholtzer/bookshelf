package com.bihstudio.madafim.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.bihstudio.madafim.data.local.db.BookDao
import com.bihstudio.madafim.data.local.model.BookEntity
import com.bihstudio.madafim.data.local.model.toEntity
import com.bihstudio.madafim.data.local.model.toDomain
import com.bihstudio.madafim.data.remote.SyncWorker
import com.bihstudio.madafim.domain.model.AppResult
import com.bihstudio.madafim.domain.model.Book
import com.bihstudio.madafim.domain.model.extractBookInviteCode
import com.bihstudio.madafim.domain.model.toBookInviteCode
import com.bihstudio.madafim.domain.model.toBookInviteWebLink
import com.bihstudio.madafim.domain.repository.BookRepository
import com.bihstudio.madafim.domain.repository.PageRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val pageRepository: PageRepository,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
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

    override suspend fun setCustomCoverFromUri(bookId: String, sourceUri: String): AppResult<Book> = runCatching {
        val book = bookDao.getBook(bookId)?.toDomain() ?: error("Book not found")
        val coverFile = copyCoverToInternalStorage(bookId, sourceUri)
        val updated = book.copy(
            coverPageId = null,
            customCoverUri = coverFile.absolutePath,
            customCoverRemoteUrl = null,
            customCoverPrompt = null,
            updatedAt = Instant.now(),
            isSynced = false,
        )
        bookDao.updateBook(updated.toEntity())
        SyncWorker.enqueueImmediateSync(context)
        updated
    }.toAppResult()

    override suspend fun createCustomCoverImage(bookId: String, prompt: String): AppResult<Book> = runCatching {
        require(prompt.isNotBlank()) { "Cover prompt cannot be empty" }
        val book = bookDao.getBook(bookId)?.toDomain() ?: error("Book not found")
        val coverFile = createPromptCover(book, prompt.trim())
        val updated = book.copy(
            coverPageId = null,
            customCoverUri = coverFile.absolutePath,
            customCoverRemoteUrl = null,
            customCoverPrompt = prompt.trim(),
            updatedAt = Instant.now(),
            isSynced = false,
        )
        bookDao.updateBook(updated.toEntity())
        SyncWorker.enqueueImmediateSync(context)
        updated
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

        // Make user-code sharing visible to the other user immediately, instead of
        // waiting for WorkManager to eventually upload the updated permission list.
        runCatching { pageRepository.uploadPendingPages(ownerId) }
        val synced = updated.copy(isSynced = true)
        booksCollection(ownerId)
            .document(bookId)
            .set(synced.toEntity().toFirestoreMap(), SetOptions.merge())
            .await()
        bookDao.updateBook(synced.toEntity())
        synced
    }.toAppResult()

    override suspend fun createBookEditorInvite(bookId: String, ownerId: String): AppResult<String> = runCatching {
        withTimeout(15_000) {
            val book = bookDao.getBook(bookId)?.toDomain() ?: error("Book not found")
            require(book.ownerId == ownerId) { "Only the book owner can create a share link" }

            val pageUploadWarning = when (val pageUpload = pageRepository.uploadPendingPages(ownerId)) {
                is AppResult.Error -> pageUpload.message
                is AppResult.Success -> null
            }

            val code = "$ownerId:$bookId".toBookInviteCode()
            firestore.collection("bookInvites")
                .limit(1)
                .get(Source.SERVER)
                .await()
            val batch = firestore.batch()
            batch.set(
                firestore.collection("bookInvites").document(code),
                mapOf(
                    "code" to code,
                    "ownerId" to ownerId,
                    "bookId" to bookId,
                    "title" to book.title,
                    "permission" to "editor",
                    "pageUploadWarning" to pageUploadWarning,
                    "updatedAt" to System.currentTimeMillis(),
                ),
                SetOptions.merge(),
            )
            batch.set(
                booksCollection(ownerId).document(bookId),
                book.copy(isSynced = true).toEntity().toFirestoreMap() +
                    ("activeInviteCode" to code),
                SetOptions.merge(),
            )
            batch.commit().await()
            code.toBookInviteWebLink(ownerId, bookId)
        }
    }.toAppResult()

    override suspend fun acceptBookEditorInvite(
        inviteText: String,
        editorUserId: String,
    ): AppResult<Book> = runCatching {
        require(context.hasNetworkConnection()) {
            "You are offline. Connect to the internet and try joining the shared book again."
        }
        val code = inviteText.extractBookInviteCode()
            ?: error("Paste a valid MaDaFim invite link or code")
        val invite = firestore.collection("bookInvites")
            .document(code)
            .get()
            .await()
        require(invite.exists()) { "This invite link was not found or is not ready yet" }
        val ownerId = invite.getString("ownerId") ?: error("Invite is missing an owner")
        val bookId = invite.getString("bookId") ?: error("Invite is missing a book")

        val bookRef = booksCollection(ownerId).document(bookId)
        if (ownerId == editorUserId) {
            val bookDoc = bookRef.get().await()
            val ownedBook = bookDoc.toBook(ownerId)
                ?: error("This invite belongs to your account, but the book was not found in cloud backup")
            val restoredBook = ownedBook.copy(isSynced = true)
            bookDao.insertBook(restoredBook.toEntity())
            when (pageRepository.syncFromRemote(ownerId, bookId)) {
                is AppResult.Error -> Unit
                is AppResult.Success -> Unit
            }
            return@runCatching restoredBook
        }

        bookRef.update(
            mapOf(
                "sharedEditorIds" to FieldValue.arrayUnion(editorUserId),
                "updatedAt" to System.currentTimeMillis(),
            ),
        ).await()
        val bookDoc = bookRef.get().await()
        val book = bookDoc.toBook(ownerId) ?: error("Shared book was not found")
        val acceptedBook = book.copy(isSynced = true)
        bookDao.insertBook(acceptedBook.toEntity())
        when (pageRepository.syncFromRemote(ownerId, bookId)) {
            is AppResult.Error -> Unit
            is AppResult.Success -> Unit
        }
        acceptedBook
    }.toAppResult()

    override suspend fun deleteBook(bookId: String): AppResult<Unit> = runCatching {
        val book = bookDao.getBook(bookId)?.toDomain()
        val ownerId = book?.ownerId
        if (ownerId != null) {
            val bookRef = booksCollection(ownerId).document(bookId)
            val pages = bookRef.collection("pages").get().await()
            for (page in pages.documents) page.reference.delete().await()
            deleteStorageTree(storage.reference.child("users/$ownerId/books/$bookId"))
            bookRef.delete().await()
        }
        bookDao.deleteBook(bookId)
        Unit
    }.toAppResult()

    override suspend fun removeBookLocally(bookId: String, currentUserId: String): AppResult<Unit> = runCatching {
        val book = bookDao.getBook(bookId)?.toDomain()
        if (book != null && book.ownerId != currentUserId) {
            booksCollection(book.ownerId)
                .document(bookId)
                .update(
                    mapOf(
                        "sharedEditorIds" to FieldValue.arrayRemove(currentUserId),
                        "updatedAt" to System.currentTimeMillis(),
                    ),
                )
                .await()
        }
        bookDao.deleteBook(bookId)
        Unit
    }.toAppResult()

    // ── Sync: local → Firestore ───────────────────────────────────────────────

    override suspend fun syncToRemote(ownerId: String): AppResult<Int> = runCatching {
        val unsynced = bookDao.getUnsyncedAccessibleBooks(
            userId = ownerId,
            editorToken = ownerId.toEditorToken(),
        )
        var count = 0
        for (entity in unsynced) {
            val entityForUpload = entity.withUploadedCustomCover()
            val docRef = booksCollection(entity.ownerId).document(entity.id)
            val data   = entityForUpload.toFirestoreMap()
            docRef.set(data, SetOptions.merge()).await()
            bookDao.updateBook(entityForUpload.copy(isSynced = true))
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
        "coverStyle"  to coverStyle,
        "customCoverRemoteUrl" to customCoverRemoteUrl,
        "customCoverPrompt" to customCoverPrompt,
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
            coverStyle = getString("coverStyle"),
            customCoverUri = null,
            customCoverRemoteUrl = getString("customCoverRemoteUrl"),
            customCoverPrompt = getString("customCoverPrompt"),
            sharedEditorIds = get("sharedEditorIds").toStringList(),
            pageCount   = getLong("pageCount")?.toInt() ?: 0,
            createdAt   = Instant.ofEpochMilli(getLong("createdAt") ?: 0L),
            updatedAt   = Instant.ofEpochMilli(getLong("updatedAt") ?: 0L),
            isSynced    = true,
        )
    }

    private suspend fun BookEntity.withUploadedCustomCover(): BookEntity {
        val localFile = customCoverUri?.let(::File)?.takeIf { it.exists() } ?: return this
        val remotePath = "users/$ownerId/books/$id/custom-cover.png"
        val ref = storage.reference.child(remotePath)
        ref.putFile(Uri.fromFile(localFile)).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        return copy(customCoverRemoteUrl = downloadUrl)
    }

    private fun copyCoverToInternalStorage(bookId: String, sourceUri: String): File {
        val dir = File(context.filesDir, "covers").also { it.mkdirs() }
        val dest = File(dir, "$bookId-custom-cover")
        context.contentResolver.openInputStream(Uri.parse(sourceUri))
            ?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            ?: error("Cannot open cover image")
        return dest
    }

    private fun createPromptCover(book: Book, prompt: String): File {
        val dir = File(context.filesDir, "covers").also { it.mkdirs() }
        val dest = File(dir, "${book.id}-created-cover.png")
        val bitmap = Bitmap.createBitmap(900, 1350, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = promptCoverColors(prompt)
        val gradient = LinearGradient(
            0f,
            0f,
            900f,
            1350f,
            colors.first,
            colors.second,
            Shader.TileMode.CLAMP,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
        canvas.drawRect(0f, 0f, 900f, 1350f, paint)
        paint.shader = null
        paint.color = Color.argb(46, 0, 0, 0)
        canvas.drawRect(0f, 0f, 96f, 1350f, paint)
        paint.color = Color.argb(54, 255, 255, 255)
        canvas.drawCircle(676f, 326f, 178f, paint)
        paint.color = Color.argb(42, 255, 255, 255)
        canvas.drawCircle(258f, 816f, 132f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        paint.textSize = 78f
        drawCenteredWrappedText(canvas, book.title.ifBlank { "Untitled" }, paint, 450f, 520f, 720f, 92f, 3)
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        paint.textSize = 38f
        paint.color = Color.argb(220, 255, 255, 255)
        drawCenteredWrappedText(canvas, prompt, paint, 450f, 860f, 680f, 50f, 4)
        dest.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 95, output) }
        bitmap.recycle()
        return dest
    }
}

private suspend fun deleteStorageTree(reference: com.google.firebase.storage.StorageReference) {
    val result = reference.listAll().await()
    result.items.forEach { it.delete().await() }
    result.prefixes.forEach { deleteStorageTree(it) }
}

private fun <T> Result<T>.toAppResult(): AppResult<T> =
    fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { error ->
            AppResult.Error(error.toUserMessage(), error)
        },
    )

private fun Throwable.toUserMessage(): String =
    when {
        this is kotlinx.coroutines.TimeoutCancellationException ->
            "Firebase did not respond. Check emulator DNS/network and Firestore configuration."
        hasCause<java.net.UnknownHostException>() ->
            "Cannot reach Firebase because DNS lookup failed. Reconnect Wi-Fi or restart the emulator, then try again."
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Firebase is temporarily unreachable. Check emulator DNS/network and try again."
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Firebase denied creating the invite. Firestore sharing rules must be configured."
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.NOT_FOUND ->
            "Cloud Firestore is not enabled for this Firebase project. Create the default Firestore database, then try again."
        findCause<StorageException>()?.httpResultCode == 404 ->
            "Cloud backup is not available: Firebase Storage returned 404. Upgrade the Firebase project to Blaze, then open Storage and create the default bucket."
        findCause<StorageException>()?.errorCode == StorageException.ERROR_NOT_AUTHENTICATED ->
            "Sign in again before uploading this book cover."
        findCause<StorageException>()?.errorCode == StorageException.ERROR_NOT_AUTHORIZED ->
            "Firebase Storage rules denied access to this book cover."
        else -> message ?: "Unknown error"
    }

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return true
        current = current.cause
    }
    return false
}

private inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return current
        current = current.cause
    }
    return null
}

private fun String.toEditorToken(): String = "|$this|"

private fun Context.hasNetworkConnection(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private fun String.toEditorIdList(): List<String> =
    split("|").filter { it.isNotBlank() }

private fun Any?.toStringList(): List<String> =
    (this as? List<*>)?.mapNotNull { it as? String }.orEmpty()

private fun promptCoverColors(prompt: String): Pair<Int, Int> {
    val palettes = listOf(
        Color.rgb(45, 27, 105) to Color.rgb(247, 37, 133),
        Color.rgb(18, 53, 91) to Color.rgb(233, 196, 106),
        Color.rgb(122, 46, 32) to Color.rgb(244, 162, 97),
        Color.rgb(0, 95, 115) to Color.rgb(148, 210, 189),
        Color.rgb(29, 53, 87) to Color.rgb(168, 218, 220),
        Color.rgb(111, 63, 40) to Color.rgb(214, 161, 95),
    )
    return palettes[Math.floorMod(prompt.lowercase().hashCode(), palettes.size)]
}

private fun drawCenteredWrappedText(
    canvas: Canvas,
    text: String,
    paint: Paint,
    centerX: Float,
    firstBaseline: Float,
    maxWidth: Float,
    lineHeight: Float,
    maxLines: Int,
) {
    val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
    val lines = mutableListOf<String>()
    var current = ""
    for (word in words) {
        val candidate = if (current.isBlank()) word else "$current $word"
        if (paint.measureText(candidate) <= maxWidth || current.isBlank()) {
            current = candidate
        } else {
            lines += current
            current = word
            if (lines.size == maxLines - 1) break
        }
    }
    if (current.isNotBlank() && lines.size < maxLines) lines += current
    lines.take(maxLines).forEachIndexed { index, line ->
        canvas.drawText(line, centerX, firstBaseline + (index * lineHeight), paint)
    }
}
