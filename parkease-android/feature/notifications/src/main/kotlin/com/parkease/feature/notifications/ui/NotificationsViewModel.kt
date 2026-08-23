package com.parkease.feature.notifications.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.feature.notifications.data.NotificationUi
import com.parkease.feature.notifications.data.NotificationsRepository
import com.parkease.feature.notifications.data.NotificationsResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val notifications: List<NotificationUi> = emptyList(),
    val message: String? = null,
    val isError: Boolean = false,
) {
    val unreadCount: Int get() = notifications.count { !it.isRead }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            _uiState.value = when (val result = repository.listMine()) {
                is NotificationsResult.Success -> _uiState.value.copy(isLoading = false, notifications = result.value, message = null, isError = false)
                is NotificationsResult.Error -> _uiState.value.copy(isLoading = false, message = result.message, isError = true)
            }
        }
    }

    /** Tapping an unread notification marks it read in place — no full reload needed for the common case. */
    fun onNotificationTapped(notification: NotificationUi) {
        if (notification.isRead) return
        _uiState.value = _uiState.value.copy(
            notifications = _uiState.value.notifications.map { if (it.id == notification.id) it.copy(isRead = true) else it },
        )
        viewModelScope.launch { repository.markRead(notification.id) }
    }

    fun markAllRead() {
        _uiState.value = _uiState.value.copy(notifications = _uiState.value.notifications.map { it.copy(isRead = true) })
        viewModelScope.launch { repository.markAllRead() }
    }
}
