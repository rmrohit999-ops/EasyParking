package com.parkease.feature.ownerparking.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.core.model.VehicleCategory
import com.parkease.feature.ownerparking.data.AttendantAssignmentUi
import com.parkease.feature.ownerparking.data.ParkingRepository
import com.parkease.feature.ownerparking.data.ParkingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AttendantsUiState(
    val isLoading: Boolean = true,
    val attendants: List<AttendantAssignmentUi> = emptyList(),
    val emailInput: String = "",
    val selectedCategories: Set<VehicleCategory> = setOf(VehicleCategory.TWO_WHEELER, VehicleCategory.FOUR_WHEELER),
    val isAssigning: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Manages who can operate this listing's gate (scan passes, check
 * vehicles in/out) — real backend endpoints existed since Milestone 8
 * with zero UI anywhere in either app; an owner had no way to actually
 * onboard an attendant to a listing before this.
 */
@HiltViewModel
class AttendantsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ParkingRepository,
) : ViewModel() {

    val listingId: String = checkNotNull(savedStateHandle["listingId"]) { "listingId is required" }

    private val _uiState = MutableStateFlow(AttendantsUiState())
    val uiState: StateFlow<AttendantsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.listAttendants(listingId)) {
                is ParkingResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false, attendants = result.value)
                is ParkingResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }

    fun setEmailInput(value: String) {
        _uiState.value = _uiState.value.copy(emailInput = value)
    }

    fun toggleCategory(category: VehicleCategory) {
        val current = _uiState.value.selectedCategories
        _uiState.value = _uiState.value.copy(selectedCategories = if (category in current) current - category else current + category)
    }

    fun assign() {
        val email = _uiState.value.emailInput.trim()
        val categories = _uiState.value.selectedCategories.toList()
        if (email.isBlank() || categories.isEmpty()) return
        _uiState.value = _uiState.value.copy(isAssigning = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.assignAttendant(listingId, email, categories)) {
                is ParkingResult.Success -> {
                    _uiState.value = _uiState.value.copy(isAssigning = false, emailInput = "")
                    refresh()
                }
                is ParkingResult.Error -> _uiState.value = _uiState.value.copy(isAssigning = false, errorMessage = result.message)
            }
        }
    }

    fun revoke(assignmentId: String) {
        _uiState.value = _uiState.value.copy(errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.revokeAttendant(listingId, assignmentId)) {
                is ParkingResult.Success -> refresh()
                is ParkingResult.Error -> _uiState.value = _uiState.value.copy(errorMessage = result.message)
            }
        }
    }
}
