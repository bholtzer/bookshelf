package com.bookshelf.data.repository

import com.bookshelf.domain.model.AppResult
import com.bookshelf.domain.model.User
import com.bookshelf.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {

    override val currentUser: Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toDomain())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override fun getCurrentUserSnapshot(): User? =
        firebaseAuth.currentUser?.toDomain()

    override suspend fun signInWithGoogle(idToken: String): AppResult<User> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = firebaseAuth.signInWithCredential(credential).await()
        val user = result.user ?: error("Sign-in succeeded but user is null")
        upsertUserDocument(user)
        user.toDomain()
    }.toAppResult()

    override suspend fun signInWithEmailPassword(
        email: String,
        password: String,
    ): AppResult<User> = runCatching {
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        val user = result.user ?: error("Sign-in succeeded but user is null")
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
        upsertUserDocument(user, overrideName = displayName)
        user.reload().await()
        firebaseAuth.currentUser!!.toDomain()
    }.toAppResult()

    override suspend fun sendPasswordResetEmail(email: String): AppResult<Unit> =
        runCatching {
            firebaseAuth.sendPasswordResetEmail(email).await()
        }.toAppResult()

    override suspend fun signOut(): AppResult<Unit> = runCatching {
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
            "lastSeen"    to System.currentTimeMillis(),
        )
        firestore.collection("users")
            .document(user.uid)
            .set(data)
            .await()
    }
}

// ── Extension helpers ─────────────────────────────────────────────────────────

private fun FirebaseUser.toDomain() = User(
    uid = uid,
    displayName = displayName,
    email = email,
    photoUrl = photoUrl?.toString(),
)

private fun <T> Result<T>.toAppResult(): AppResult<T> =
    fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { AppResult.Error(it.message ?: "Unknown error", it) },
    )
