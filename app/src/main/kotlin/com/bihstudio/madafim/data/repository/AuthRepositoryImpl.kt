package com.bihstudio.madafim.data.repository

import com.bihstudio.madafim.data.local.db.BookDao
import com.bihstudio.madafim.domain.model.AppResult
import com.bihstudio.madafim.domain.model.User
import com.bihstudio.madafim.domain.model.extractEditorShareCode
import com.bihstudio.madafim.domain.model.toEditorShareCode
import com.bihstudio.madafim.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val bookDao: BookDao,
) : AuthRepository {

    override val currentUser: Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toDomain())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override fun getCurrentUserSnapshot(): User? =
        firebaseAuth.currentUser?.also(::refreshUserDocument)?.toDomain()

    override suspend fun signInWithGoogle(idToken: String): AppResult<User> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = firebaseAuth.signInWithCredential(credential).await()
        val user = result.user ?: error("Sign-in succeeded but user is null")
        upsertUserDocumentBestEffort(user)
        user.toDomain()
    }.toAppResult()

    override suspend fun signInWithEmailPassword(
        email: String,
        password: String,
    ): AppResult<User> = runCatching {
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        val user = result.user ?: error("Sign-in succeeded but user is null")
        upsertUserDocumentBestEffort(user)
        user.toDomain()
    }.toAppResult()

    override suspend fun registerWithEmailPassword(
        email: String,
        password: String,
        displayName: String,
    ): AppResult<User> = runCatching {
        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user ?: error("Registration succeeded but user is null")
        // Set display name
        user.updateProfile(
            UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
        ).await()
        upsertUserDocumentBestEffort(user, overrideName = displayName)
        user.reload().await()
        firebaseAuth.currentUser!!.toDomain()
    }.toAppResult()

    override suspend fun resolveShareTargetToUserId(input: String): AppResult<String> =
        runCatching {
            val trimmed = input.trim()
            require(trimmed.isNotBlank()) { "Share code cannot be empty" }

            val shareCode = trimmed.extractEditorShareCode()
            if (shareCode == null) {
                return@runCatching trimmed
            }

            val snapshot = firestore.collection("users")
                .whereEqualTo("editorShareCode", shareCode)
                .limit(1)
                .get()
                .await()
            val userId = snapshot.documents.firstOrNull()?.getString("uid")
                ?: error("No user found for share code $shareCode")
            userId
        }.toAppResult()

    override suspend fun sendPasswordResetEmail(email: String): AppResult<Unit> =
        runCatching {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Unit
        }.toAppResult()

    override suspend fun signOut(): AppResult<Unit> = runCatching {
        firebaseAuth.signOut()
    }.toAppResult()

    override suspend fun deleteAccount(): AppResult<Unit> = runCatching {
        val user = firebaseAuth.currentUser ?: error("Sign in again before deleting your account")
        val uid = user.uid

        // Remove files first; Firestore document deletion does not remove Storage objects.
        deleteStorageTree(storage.reference.child("users/$uid"))

        // Remove every page document before its parent book document.
        val ownedBooks = firestore.collection("users").document(uid).collection("books").get().await()
        for (book in ownedBooks.documents) {
            val pages = book.reference.collection("pages").get().await()
            for (page in pages.documents) page.reference.delete().await()
            book.reference.delete().await()
        }

        // Remove outstanding invitations created by this account.
        val invites = firestore.collection("bookInvites").whereEqualTo("ownerId", uid).get().await()
        for (invite in invites.documents) invite.reference.delete().await()

        // Revoke this user from books owned by other people.
        val sharedBooks = firestore.collectionGroup("books").whereArrayContains("sharedEditorIds", uid).get().await()
        for (book in sharedBooks.documents) {
            book.reference.update("sharedEditorIds", FieldValue.arrayRemove(uid)).await()
        }

        firestore.collection("users").document(uid).delete().await()
        bookDao.deleteAllBooks()
        user.delete().await()
        firebaseAuth.signOut()
    }.toAppResult()

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Creates or updates the user document in Firestore.
     * Stored at users/{uid} so the user's books can be queried server-side.
     */
    private suspend fun upsertUserDocument(
        user: FirebaseUser,
        overrideName: String? = null,
    ) {
        val data = mapOf(
            "uid"         to user.uid,
            "displayName" to (overrideName ?: user.displayName),
            "email"       to user.email,
            "photoUrl"    to user.photoUrl?.toString(),
            "editorShareCode" to user.uid.toEditorShareCode(),
            "lastSeen"    to System.currentTimeMillis(),
        )
        firestore.collection("users")
            .document(user.uid)
            .set(data, SetOptions.merge())
            .await()
    }

    private suspend fun upsertUserDocumentBestEffort(
        user: FirebaseUser,
        overrideName: String? = null,
    ) {
        withTimeoutOrNull(4_000) {
            runCatching { upsertUserDocument(user, overrideName) }
        }
    }

    private fun refreshUserDocument(user: FirebaseUser) {
        val data = mapOf(
            "uid" to user.uid,
            "displayName" to user.displayName,
            "email" to user.email,
            "photoUrl" to user.photoUrl?.toString(),
            "editorShareCode" to user.uid.toEditorShareCode(),
            "lastSeen" to System.currentTimeMillis(),
        )
        firestore.collection("users")
            .document(user.uid)
            .set(data, SetOptions.merge())
    }
}

private suspend fun deleteStorageTree(reference: StorageReference) {
    val result = reference.listAll().await()
    result.items.forEach { it.delete().await() }
    result.prefixes.forEach { deleteStorageTree(it) }
}

// ── Extension helpers ─────────────────────────────────────────────────────────

private fun FirebaseUser.toDomain() = User(
    uid = uid,
    displayName = displayName,
    email = email,
    photoUrl = photoUrl?.toString(),
    editorShareCode = uid.toEditorShareCode(),
)

private fun <T> Result<T>.toAppResult(): AppResult<T> =
    fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(it.toUserMessage(), it) },
    )

private fun Throwable.toUserMessage(): String {
    val rawMessage = message.orEmpty()
    return when {
        rawMessage.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ->
            "Firebase Auth is not configured for this project. Enable Authentication and the Email/Password sign-in provider in Firebase Console."

        else -> rawMessage.ifBlank { "Unknown error" }
    }
}
