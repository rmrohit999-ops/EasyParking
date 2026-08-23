package com.parkease.feature.ownerparking.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.core.model.VehicleCategory
import com.parkease.core.model.VehicleType
import com.parkease.feature.ownerparking.data.ParkingRepository
import com.parkease.feature.ownerparking.data.ParkingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddSectionUiState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class AddSectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ParkingRepository,
) : ViewModel() {

    val listingId: String = checkNotNull(savedStateHandle["listingId"]) { "listingId is required" }

    private val _uiState = MutableStateFlow(AddSectionUiState())
    val uiState: StateFlow<AddSectionUiState> = _uiState.asStateFlow()

    fun createSection(
        name: String,
        vehicleCategory: VehicleCategory,
        supportedVehicleTypes: List<VehicleType>,
        capacity: Int,
        hourlyRateMinorUnits: Int,
        isCovered: Boolean,
        hasSecurity: Boolean,
        hasCctv: Boolean,
        hasEvCharging: Boolean,
        instantModeEnabled: Boolean,
    ) {
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            val result = repository.createSection(
                listingId, name, vehicleCategory, supportedVehicleTypes, capacity, hourlyRateMinorUnits,
                isCovered, hasSecurity, hasCctv, hasEvCharging, instantModeEnabled,
            )
            when (result) {
                is ParkingResult.Success -> _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
                is ParkingResult.Error -> _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = result.message)
            }
        }
    }
}
