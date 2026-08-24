package com.parkease.feature.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.feature.admin.data.AdminRepository
import com.parkease.feature.admin.data.AdminResult
import com.parkease.feature.admin.data.AdminUserUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminUsersUiState(
    val isLoading: Boolean = true,
    val users: List<AdminUserUi> = emptyList(),
    val query: String = "",
    val actionInProgressUserId: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class AdminUsersViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUsersUiState())
    val uiState: StateFlow<AdminUsersUiState> = _uiState.asStateFlow()

    init { search() }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun search() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.listUsers(_uiState.value.query)) {
                is AdminResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false, users = result.value)
                is AdminResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }

    fun suspendUser(userId: String, reason: String) {
        _uiState.value = _uiState.value.copy(actionInProgressUserId = userId, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.suspendUser(userId, reason)) {
                is AdminResult.Success -> {
                    _uiState.value = _uiState.value.copy(actionInProgressUserId = null)
                    search()
                }
                is AdminResult.Error -> _uiState.value = _uiState.value.copy(actionInProgressUserId = null, errorMessage = result.message)
            }
        }
    }

    fun reinstateUser(userId: String) {
        _uiState.value = _uiState.value.copy(actionInProgressUserId = userId, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.reinstateUser(userId)) {
                is AdminResult.Success -> {
                    _uiState.value = _uiState.value.copy(actionInProgressUserId = null)
                    search()
                }
                is AdminResult.Error -> _uiState.value = _uiState.value.copy(actionInProgressUserId = null, errorMessage = result.message)
            }
        }
    }
}
