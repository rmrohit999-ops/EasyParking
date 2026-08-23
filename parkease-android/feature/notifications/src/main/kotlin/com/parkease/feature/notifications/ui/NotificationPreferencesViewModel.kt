package com.parkease.feature.notifications.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.feature.notifications.data.NOTIFICATION_CATEGORIES
import com.parkease.feature.notifications.data.NotificationsRepository
import com.parkease.feature.notifications.data.NotificationsResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CHANNEL = "PUSH"

data class PreferenceRowUi(val category: String, val label: String, val enabled: Boolean)

data class NotificationPreferencesUiState(
    val isLoading: Boolean = true,
    val rows: List<PreferenceRowUi> = emptyList(),
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class NotificationPreferencesViewModel @Inject constructor(
    private val repository: NotificationsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationPreferencesUiState())
    val uiState: StateFlow<NotificationPreferencesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            when (val result = repository.getPreferences()) {
                is NotificationsResult.Success -> {
                    // A category with no explicit row means the user never
                    // touched it — the backend defaults that to enabled
                    // (NotificationsService.isChannelEnabled), so mirror
                    // that default here rather than showing it as off.
                    val explicit = result.value.filter { it.channel == CHANNEL }.associateBy { it.category }
                    val rows = NOTIFICATION_CATEGORIES.map { (category, label) ->
                        PreferenceRowUi(category, label, explicit[category]?.enabled ?: true)
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, rows = rows, message = null, isError = false)
                }
                is NotificationsResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false, message = result.message, isError = true)
            }
        }
    }

    fun toggle(category: String, enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            rows = _uiState.value.rows.map { if (it.category == category) it.copy(enabled = enabled) else it },
        )
        viewModelScope.launch {
            val result = repository.updatePreference(category, CHANNEL, enabled)
            if (result is NotificationsResult.Error) {
                _uiState.value = _uiState.value.copy(message = result.message, isError = true)
                load() // roll back the optimistic toggle to whatever the server actually has
            }
        }
    }
}
