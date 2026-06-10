package com.bihstudio.bookshelf.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bihstudio.bookshelf.domain.model.AppResult
import com.bihstudio.bookshelf.domain.usecase.auth.RegisterWithEmailPasswordUseCase
import com.bihstudio.bookshelf.domain.usecase.auth.SignInWithEmailPasswordUseCase
import com.bihstudio.bookshelf.domain.usecase.auth.SignInWithGoogleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onGoogleIdTokenReceived(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = signInWithGoogle(idToken)) {
                is AppResult.Success -> _uiState.update { it.copy(isLoading = false, isSignedIn = true) }
                is AppResult.Error   -> _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    fun onSignIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = signInWithEmail(email, password)) {
                is AppResult.Success -> _uiState.update { it.copy(isLoading = false, isSignedIn = true) }
                is AppResult.Error   -> _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    fun onRegister(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = registerWithEmail(email, password, displayName)) {
                is AppResult.Success -> _uiState.update { it.copy(isLoading = false, isSignedIn = true) }
                is AppResult.Error   -> _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    fun toggleMode() {
        _uiState.update {
            it.copy(
                mode  = if (it.mode == AuthMode.SIGN_IN) AuthMode.REGISTER else AuthMode.SIGN_IN,
                error = null,
            )
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
