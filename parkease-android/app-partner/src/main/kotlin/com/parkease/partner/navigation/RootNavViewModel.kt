package com.parkease.partner.navigation

import com.parkease.partner.notifications.PushTokenProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.map
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

/** Which home screen a signed-in account lands on — decided from its real roles, never assumed. */
sealed class PartnerLanding {
    data object Owner : PartnerLanding()
    data object AttendantOnly : PartnerLanding()
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

    private val _landing = MutableStateFlow<PartnerLanding?>(null)
    val landing: StateFlow<PartnerLanding?> = _landing.asStateFlow()

    /** Nullable — see app-driver's RootNavViewModel for why (no neutral screen to sit on while DataStore's async read resolves). */
    val isLoggedIn: StateFlow<Boolean?> = sessionStore.isLoggedIn
        .map { it as Boolean? }
        .distinctUntilChanged()
        .onEach { loggedIn ->
            if (loggedIn == true) {
                syncPushToken()
                resolveLanding()
            } else {
                _landing.value = null
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
        )

    /**
     * Unlike the old single-app shell (where "Own" was a card the user
     * explicitly tapped), this app has exactly one non-attendant landing,
     * so which dashboard to show has to come from the account's real
     * roles rather than a client-side choice — an attendant who was never
     * onboarded as an owner must never see (or be silently granted) the
     * owner dashboard. Only an account with NEITHER role yet (a brand new
     * signup through this app) defaults to owner self-service onboarding,
     * matching the old app's "Own" card flow exactly.
     */
    private fun resolveLanding() {
        viewModelScope.launch {
            _landing.value = try {
                val profile = usersApi.getMe()
                when {
                    "OWNER" in profile.roles -> PartnerLanding.Owner
                    "ATTENDANT" in profile.roles -> PartnerLanding.AttendantOnly
                    else -> {
                        becomeOwner()
                        PartnerLanding.Owner
                    }
                }
            } catch (e: Exception) {
                // Fail open to the owner dashboard rather than stranding the
                // user on a loading screen — every screen it links to
                // re-checks its own access server-side regardless.
                PartnerLanding.Owner
            }
        }
    }

    /**
     * Wires up UsersApi.becomeOwner (POST /v1/users/me/roles/owner) —
     * idempotent: a 409 ("already an owner") is treated as success.
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
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 409) BecomeOwnerState.Success else BecomeOwnerState.Error("Something went wrong. Please try again.")
            } catch (e: Exception) {
                BecomeOwnerState.Error("Something went wrong. Please try again.")
            }
        }
    }

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
