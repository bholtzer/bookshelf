package com.bihstudio.bookshelf.domain.repository

import com.bihstudio.bookshelf.domain.model.AppResult
import com.bihstudio.bookshelf.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    /** Emits the current user, or null when signed out. Survives process death. */
    val currentUser: Flow<User?>

    /** Synchronous snapshot — useful in ViewModels before the flow emits. */
    fun getCurrentUserSnapshot(): User?

    /**
     * Sign in with a Google ID token obtained from Credential Manager.
     * On success, creates or updates the user document in Firestore.
     */
    suspend fun signInWithGoogle(idToken: String): AppResult<User>

    /**
     * Sign in with email + password (Firebase email/password provider).
     */
    suspend fun signInWithEmailPassword(email: String, password: String): AppResult<User>

    /**
     * Register a new account with email + password.
     */
    suspend fun registerWithEmailPassword(
        email: String,
        password: String,
        displayName: String,
    ): AppResult<User>

    suspend fun resolveShareTargetToUserId(input: String): AppResult<String>

    suspend fun sendPasswordResetEmail(email: String): AppResult<Unit>

    suspend fun signOut(): AppResult<Unit>
}
