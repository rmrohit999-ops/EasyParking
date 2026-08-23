package com.parkease.feature.ownerparking.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.core.model.ListingStatus
import com.parkease.feature.ownerparking.data.ListingDetailUi
import com.parkease.feature.ownerparking.data.ParkingRepository
import com.parkease.feature.ownerparking.data.ParkingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListingDetailUiState(
    val isLoading: Boolean = true,
    val detail: ListingDetailUi? = null,
    val errorMessage: String? = null,
    val actionInProgress: Boolean = false,
)

@HiltViewModel
class ListingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ParkingRepository,
) : ViewModel() {

    val listingId: String = checkNotNull(savedStateHandle["listingId"]) { "listingId is required" }

    private val _uiState = MutableStateFlow(ListingDetailUiState())
    val uiState: StateFlow<ListingDetailUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.getListing(listingId)) {
                is ParkingResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false, detail = result.value)
                is ParkingResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }

    fun setStatus(status: ListingStatus) {
        _uiState.value = _uiState.value.copy(actionInProgress = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.updateListingStatus(listingId, status)) {
                is ParkingResult.Success -> refresh()
                is ParkingResult.Error -> _uiState.value = _uiState.value.copy(actionInProgress = false, errorMessage = result.message)
            }
        }
    }

    fun submitForApproval() {
        _uiState.value = _uiState.value.copy(actionInProgress = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.submitForApproval(listingId)) {
                is ParkingResult.Success -> refresh()
                is ParkingResult.Error -> _uiState.value = _uiState.value.copy(actionInProgress = false, errorMessage = result.message)
            }
        }
    }

    fun removeSection(sectionId: String) {
        _uiState.value = _uiState.value.copy(actionInProgress = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.removeSection(listingId, sectionId)) {
                is ParkingResult.Success -> refresh()
                is ParkingResult.Error -> _uiState.value = _uiState.value.copy(actionInProgress = false, errorMessage = result.message)
            }
        }
    }
}
