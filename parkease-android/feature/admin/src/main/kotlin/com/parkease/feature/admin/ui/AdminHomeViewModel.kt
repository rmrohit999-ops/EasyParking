package com.parkease.feature.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.feature.admin.data.AdminRepository
import com.parkease.feature.admin.data.AdminResult
import com.parkease.feature.admin.data.DashboardSummaryUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminHomeUiState(
    val isLoading: Boolean = true,
    val summary: DashboardSummaryUi? = null,
    /** 403 here (not ADMIN despite reaching this screen) is the real gate — the Welcome-screen role card is only routing, per Milestone 0's role-security requirement. */
    val accessDenied: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AdminHomeViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminHomeUiState())
    val uiState: StateFlow<AdminHomeUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.dashboardSummary()) {
                is AdminResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false, summary = result.value)
                is AdminResult.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    accessDenied = result.message.contains("admin access"),
                    errorMessage = result.message,
                )
            }
        }
    }
}
