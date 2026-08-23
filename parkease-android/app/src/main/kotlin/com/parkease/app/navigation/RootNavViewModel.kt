package com.parkease.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.app.notifications.PushTokenProvider
import com.parkease.core.datastore.SessionStore
import com.parkease.core.network.api.BecomeOwnerRequest
import com.parkease.core.network.api.UsersApi
import com.parkease.feature.auth.data.AuthRepository
import com.parkease.feature.auth.data.AuthResult
import com.parkease.feature.notifications.data.NotificationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BecomeOwnerState {
    data object Idle : BecomeOwnerState()
    data object InProgress : BecomeOwnerState()
    data object Success : BecomeOwnerState()
    data class Error(val message: String) : BecomeOwnerState()
}

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
    private val usersApi: UsersApi,
) : ViewModel() {

    private val _becomeOwnerState = MutableStateFlow<BecomeOwnerState>(BecomeOwnerState.Idle)
    val becomeOwnerState: StateFlow<BecomeOwnerState> = _becomeOwnerState.asStateFlow()

    /**
     * Wires up UsersApi.becomeOwner (POST /v1/users/me/roles/owner) — the
     * endpoint existed and worked from Milestone 2, but no screen ever
     * called it, so there was no way to reach the owner-parking graph
     * without a manual database role grant. JwtAuthGuard re-checks roles
     * from the DB on every request, so the very next API call after this
     * succeeds already sees OWNER — no re-login needed.
     */
    fun becomeOwner() {
        if (_becomeOwnerState.value is BecomeOwnerState.InProgress) return
        _becomeOwnerState.value = BecomeOwnerState.InProgress
        viewModelScope.launch {
            _becomeOwnerState.value = try {
                val profile = usersApi.becomeOwner(BecomeOwnerRequest())
                if ("OWNER" in profile.roles) {
                    BecomeOwnerState.Success
                } else {
                    BecomeOwnerState.Error("Something went wrong. Please try again.")
                }
            } catch (e: Exception) {
                BecomeOwnerState.Error("Something went wrong. Please try again.")
            }
        }
    }

    /**
     * Milestone 11: the moment the session becomes (or is found to already
     * be) authenticated is the one real choke point for "make sure this
     * device's push token is registered" — covers sign-in and a cold start
     * where the user was already signed in from a previous session alike,
     * without every individual sign-in method (OTP/password/Google, see
     * AuthRepository) needing to remember to do it separately.
     */
    val isLoggedIn: StateFlow<Boolean> = sessionStore.isLoggedIn
        .distinctUntilChanged()
        .onEach { loggedIn -> if (loggedIn) syncPushToken() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    /**
     * Best-effort — registering this device's current FCM token is never
     * allowed to block or fail sign-in. The register endpoint is an upsert
     * keyed on the token itself (NotificationsService.registerDevice), so
     * calling it again on every session-becomes-active event is always
     * safe: it just refreshes last_seen_at / un-revokes a previously
     * unregistered device.
     */
    private fun syncPushToken() {
        viewModelScope.launch {
            val token = pushTokenProvider.currentToken() ?: return@launch
            notificationsRepository.registerDevice(token)
        }
    }

    /** Best-effort unregister of this device's push token before the real sign-out (AuthRepository.logout) invalidates the session that call needs. */
    fun signOut() {
        viewModelScope.launch {
            pushTokenProvider.currentToken()?.let { notificationsRepository.unregisterDevice(it) }
            authRepository.logout()
        }
    }

    private val _deleteAccountState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteAccountState: StateFlow<DeleteAccountState> = _deleteAccountState.asStateFlow()

    /**
     * Called only after the caller (RootNavHost) has shown an explicit
     * confirmation dialog — this is a destructive, irreversible action.
     * On success, AuthRepository.deleteAccount clears the local session,
     * which flips isLoggedIn to false and navigates back to the auth graph
     * the same way signOut() does.
     */
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
