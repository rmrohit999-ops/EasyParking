package com.parkease.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.feature.auth.data.AuthRepository
import com.parkease.feature.auth.data.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val otpSentTo: String? = null,
    val isAuthenticated: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) = launchAuthAction {
        authRepository.login(email, password)
    }

    fun register(fullName: String, email: String?, phone: String?, password: String?) = launchAuthAction {
        authRepository.register(fullName, email, phone, password)
    }

    fun requestOtp(phone: String, purpose: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = authRepository.requestOtp(phone, purpose)) {
                is AuthResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false, otpSentTo = phone)
                is AuthResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }

    fun verifyOtp(phone: String, purpose: String, code: String) = launchAuthAction {
        authRepository.verifyOtp(phone, purpose, code)
    }

    fun googleSignIn(idToken: String) = launchAuthAction {
        authRepository.googleSignIn(idToken)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Deliberately ignores the result and always lets the screen show the
     * same "if an account exists…" message — mirrors the backend's
     * non-enumerating behavior (AuthService.forgotPassword) on the client
     * side too, so a network-level timing difference is the only thing
     * that could ever hint at account existence.
     */
    suspend fun forgotPasswordDirect(email: String) {
        authRepository.forgotPassword(email)
    }

    private fun launchAuthAction(action: suspend () -> AuthResult) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = action()) {
                is AuthResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false, isAuthenticated = true)
                is AuthResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }
}
