package com.parkease.feature.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.feature.admin.data.AdminRepository
import com.parkease.feature.admin.data.AdminResult
import com.parkease.feature.admin.data.PendingListingUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminPendingListingsUiState(
    val isLoading: Boolean = true,
    val listings: List<PendingListingUi> = emptyList(),
    val actionInProgressId: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class AdminPendingListingsViewModel @Inject constructor(
    private val repository: AdminRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminPendingListingsUiState())
    val uiState: StateFlow<AdminPendingListingsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.listPendingListings()) {
                is AdminResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false, listings = result.value)
                is AdminResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }

    fun approve(listingId: String) {
        _uiState.value = _uiState.value.copy(actionInProgressId = listingId, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.approveListing(listingId)) {
                is AdminResult.Success -> {
                    _uiState.value = _uiState.value.copy(actionInProgressId = null)
                    refresh()
                }
                is AdminResult.Error -> _uiState.value = _uiState.value.copy(actionInProgressId = null, errorMessage = result.message)
            }
        }
    }

    fun reject(listingId: String, reason: String) {
        _uiState.value = _uiState.value.copy(actionInProgressId = listingId, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.rejectListing(listingId, reason)) {
                is AdminResult.Success -> {
                    _uiState.value = _uiState.value.copy(actionInProgressId = null)
                    refresh()
                }
                is AdminResult.Error -> _uiState.value = _uiState.value.copy(actionInProgressId = null, errorMessage = result.message)
            }
        }
    }
}
