package com.bihstudio.bookshelf.domain.usecase.auth

import com.bihstudio.bookshelf.domain.model.AppResult
import com.bihstudio.bookshelf.domain.model.User
import com.bihstudio.bookshelf.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCurrentUserUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): Flow<User?> = authRepository.currentUser
}

class GetCurrentUserUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): User? = authRepository.getCurrentUserSnapshot()
}

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(idToken: String): AppResult<User> =
        authRepository.signInWithGoogle(idToken)
}

class SignInWithEmailPasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): AppResult<User> {
        if (email.isBlank()) return AppResult.Error("Email cannot be empty")
        if (password.length < 6) return AppResult.Error("Password must be at least 6 characters")
        return authRepository.signInWithEmailPassword(email, password)
    }
}

class RegisterWithEmailPasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        displayName: String,
    ): AppResult<User> {
        if (displayName.isBlank()) return AppResult.Error("Display name cannot be empty")
        if (email.isBlank()) return AppResult.Error("Email cannot be empty")
        if (password.length < 6) return AppResult.Error("Password must be at least 6 characters")
        return authRepository.registerWithEmailPassword(email, password, displayName)
    }
}

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> = authRepository.signOut()
}
