package com.bihstudio.madafim.domain.usecase.auth

import com.bihstudio.madafim.domain.model.AppResult
import com.bihstudio.madafim.domain.model.User
import com.bihstudio.madafim.domain.repository.AuthRepository
import com.bihstudio.madafim.domain.repository.BookRepository
import com.bihstudio.madafim.domain.repository.PageRepository
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

class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> = authRepository.deleteAccount()
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
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank()) return AppResult.Error("Email cannot be empty")
        if (!normalizedEmail.isValidEmail()) {
            return AppResult.Error("Enter a valid email address, for example name@example.com")
        }
        if (password.length < 6) return AppResult.Error("Password must be at least 6 characters")
        return authRepository.signInWithEmailPassword(normalizedEmail, password)
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
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank()) return AppResult.Error("Email cannot be empty")
        if (!normalizedEmail.isValidEmail()) {
            return AppResult.Error("Enter a valid email address, for example name@example.com")
        }
        if (password.length < 6) return AppResult.Error("Password must be at least 6 characters")
        return authRepository.registerWithEmailPassword(normalizedEmail, password, displayName.trim())
    }
}

private val EMAIL_PATTERN = Regex(
    pattern = "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
    option = RegexOption.IGNORE_CASE,
)

private fun String.isValidEmail(): Boolean =
    length <= 254 && '\\' !in this && EMAIL_PATTERN.matches(this)

class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> = authRepository.signOut()
}

class RestoreUserLibraryUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val pageRepository: PageRepository,
) {
    suspend operator fun invoke(userId: String): AppResult<Int> {
        when (val booksUploaded = bookRepository.syncToRemote(userId)) {
            is AppResult.Error -> return booksUploaded
            is AppResult.Success -> Unit
        }
        when (val pagesUploaded = pageRepository.uploadPendingPages(userId)) {
            is AppResult.Error -> return pagesUploaded
            is AppResult.Success -> Unit
        }
        return bookRepository.syncFromRemote(userId)
    }
}
