package com.parkease.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.app.notifications.PushTokenProvider
import com.parkease.core.datastore.SessionStore
import com.parkease.feature.auth.data.AuthRepository
import com.parkease.feature.notifications.data.NotificationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootNavViewModel @Inject constructor(
    sessionStore: SessionStore,
    private val authRepository: AuthRepository,
    private val notificationsRepository: NotificationsRepository,
    private val pushTokenProvider: PushTokenProvider,
) : ViewModel() {

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
}
