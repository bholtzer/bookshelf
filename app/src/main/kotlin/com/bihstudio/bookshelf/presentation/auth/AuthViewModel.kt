package com.bihstudio.bookshelf.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bihstudio.bookshelf.domain.analytics.AnalyticsEvent
import com.bihstudio.bookshelf.domain.analytics.AnalyticsLogger
import com.bihstudio.bookshelf.domain.analytics.AnalyticsParam
import com.bihstudio.bookshelf.domain.model.AppResult
import com.bihstudio.bookshelf.domain.usecase.auth.RegisterWithEmailPasswordUseCase
import com.bihstudio.bookshelf.domain.usecase.auth.RestoreUserLibraryUseCase
import com.bihstudio.bookshelf.domain.usecase.auth.SignInWithEmailPasswordUseCase
import com.bihstudio.bookshelf.domain.usecase.auth.SignInWithGoogleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val error: String? = null,
    val mode: AuthMode = AuthMode.SIGN_IN,
)

enum class AuthMode { SIGN_IN, REGISTER }

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val signInWithEmail: SignInWithEmailPasswordUseCase,
    private val registerWithEmail: RegisterWithEmailPasswordUseCase,
    private val restoreUserLibrary: RestoreUserLibraryUseCase,
    private val analytics: AnalyticsLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        analytics.trackScreen("auth")
    }

    fun onGoogleIdTokenReceived(idToken: String) {
        viewModelScope.launch {
            analytics.track(
                AnalyticsEvent.AUTH_GOOGLE_STARTED,
                mapOf(AnalyticsParam.AUTH_METHOD to "google"),
            )
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = signInWithGoogle(idToken)) {
                is AppResult.Success -> finishSignIn(result.data.uid, "google")
                is AppResult.Error -> {
                    analytics.trackAuthResult(AnalyticsEvent.AUTH_GOOGLE_RESULT, "google", "failure")
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    fun onSignIn(email: String, password: String) {
        viewModelScope.launch {
            analytics.track(
                AnalyticsEvent.AUTH_EMAIL_STARTED,
                mapOf(AnalyticsParam.AUTH_METHOD to "email"),
            )
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = signInWithEmail(email, password)) {
                is AppResult.Success -> finishSignIn(result.data.uid, "email")
                is AppResult.Error -> {
                    analytics.trackAuthResult(AnalyticsEvent.AUTH_EMAIL_RESULT, "email", "failure")
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    fun onRegister(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            analytics.track(
                AnalyticsEvent.AUTH_REGISTER_STARTED,
                mapOf(AnalyticsParam.AUTH_METHOD to "email"),
            )
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = registerWithEmail(email, password, displayName)) {
                is AppResult.Success -> finishSignIn(result.data.uid, "register")
                is AppResult.Error -> {
                    analytics.trackAuthResult(AnalyticsEvent.AUTH_REGISTER_RESULT, "email", "failure")
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }

    private suspend fun finishSignIn(userId: String, method: String) {
        analytics.setUserId(userId)
        analytics.trackAuthResult(method.toAuthResultEvent(), method, "success")
        _uiState.update {
            it.copy(isLoading = false, isSignedIn = true, error = null)
        }

        val restore = withTimeoutOrNull(8_000) {
            restoreUserLibrary(userId)
        }
        when (restore) {
            null -> analytics.trackAuthResult(method.toAuthResultEvent(), method, "restore_timeout")
            is AppResult.Error -> analytics.trackAuthResult(method.toAuthResultEvent(), method, "restore_failure")
            is AppResult.Success -> Unit
        }
    }

    fun toggleMode() {
        _uiState.update {
            val nextMode = if (it.mode == AuthMode.SIGN_IN) AuthMode.REGISTER else AuthMode.SIGN_IN
            analytics.track(
                AnalyticsEvent.AUTH_MODE_CHANGED,
                mapOf(AnalyticsParam.SOURCE to nextMode.name.lowercase()),
            )
            it.copy(
                mode  = nextMode,
                error = null,
            )
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

private fun AnalyticsLogger.trackAuthResult(eventName: String, method: String, result: String) {
    track(
        eventName,
        mapOf(
            AnalyticsParam.AUTH_METHOD to method,
            AnalyticsParam.RESULT to result,
        ),
    )
}

private fun String.toAuthResultEvent(): String =
    when (this) {
        "google" -> AnalyticsEvent.AUTH_GOOGLE_RESULT
        "register" -> AnalyticsEvent.AUTH_REGISTER_RESULT
        else -> AnalyticsEvent.AUTH_EMAIL_RESULT
    }
