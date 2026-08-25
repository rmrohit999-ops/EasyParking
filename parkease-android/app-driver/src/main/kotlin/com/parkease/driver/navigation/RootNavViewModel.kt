package com.parkease.driver.navigation

import com.parkease.driver.notifications.PushTokenProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.core.datastore.SessionStore
import com.parkease.feature.auth.data.AuthRepository
import com.parkease.feature.auth.data.AuthResult
import com.parkease.feature.notifications.data.NotificationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DeleteAccountState {
    data object Idle : DeleteAccountState()
    data object InProgress : DeleteAccountState()
    data class Error(val message: String) : DeleteAccountState()
}

@HiltViewModel
class RootNavViewModel @Inject constructor(
    sessionStore: SessionStore,
    private val authRepository: AuthRepository,
    private val notificationsRepository: NotificationsRepository,
    private val pushTokenProvider: PushTokenProvider,
) : ViewModel() {

    /**
     * Nullable — null means "session state not resolved yet" (DataStore's
     * disk read is async; a plain non-nullable StateFlow would need some
     * default before that resolves, and `false` would be actively wrong
     * for an already-logged-in user on a cold start, briefly bouncing them
     * to the login screen since this app has no neutral Welcome screen to
     * sit on while it resolves, unlike the old single-app shell). RootNavHost
     * shows a small loading screen for exactly this gap, then navigates
     * once a real true/false value arrives.
     */
    val isLoggedIn: StateFlow<Boolean?> = sessionStore.isLoggedIn
        .map { it as Boolean? }
        .distinctUntilChanged()
        .onEach { loggedIn -> if (loggedIn == true) syncPushToken() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
        )

    private fun syncPushToken() {
        viewModelScope.launch {
            val token = pushTokenProvider.currentToken() ?: return@launch
            notificationsRepository.registerDevice(token)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            pushTokenProvider.currentToken()?.let { notificationsRepository.unregisterDevice(it) }
            authRepository.logout()
        }
    }

    private val _deleteAccountState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteAccountState: StateFlow<DeleteAccountState> = _deleteAccountState.asStateFlow()

    fun clearDeleteAccountError() {
        if (_deleteAccountState.value is DeleteAccountState.Error) {
            _deleteAccountState.value = DeleteAccountState.Idle
        }
    }

    fun deleteAccount() {
        if (_deleteAccountState.value is DeleteAccountState.InProgress) return
        _deleteAccountState.value = DeleteAccountState.InProgress
        viewModelScope.launch {
            pushTokenProvider.currentToken()?.let { notificationsRepository.unregisterDevice(it) }
            when (val result = authRepository.deleteAccount()) {
                is AuthResult.Success -> _deleteAccountState.value = DeleteAccountState.Idle
                is AuthResult.Error -> _deleteAccountState.value = DeleteAccountState.Error(result.message)
            }
        }
    }
}
