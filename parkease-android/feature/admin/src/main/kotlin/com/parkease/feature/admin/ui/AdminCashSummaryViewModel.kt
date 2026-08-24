package com.parkease.feature.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.feature.admin.data.AdminRepository
import com.parkease.feature.admin.data.AdminResult
import com.parkease.feature.admin.data.CashSummaryUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminCashSummaryUiState(
    val isLoading: Boolean = true,
    val summary: CashSummaryUi? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class AdminCashSummaryViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminCashSummaryUiState())
    val uiState: StateFlow<AdminCashSummaryUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.cashSummary()) {
                is AdminResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false, summary = result.value)
                is AdminResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }
}
