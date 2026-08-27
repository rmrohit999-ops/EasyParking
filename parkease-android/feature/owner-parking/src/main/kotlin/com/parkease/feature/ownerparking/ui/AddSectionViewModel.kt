package com.parkease.feature.ownerparking.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.core.model.VehicleCategory
import com.parkease.core.model.VehicleType
import com.parkease.feature.ownerparking.data.ParkingRepository
import com.parkease.feature.ownerparking.data.ParkingResult
import com.parkease.feature.ownerparking.data.SectionUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddSectionUiState(
    val isLoadingExisting: Boolean = false,
    val existing: SectionUi? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
)

/**
 * Same screen/ViewModel handles both "add" and "edit" — a null sectionId
 * (nav arg) means add. Editing was previously impossible anywhere in the
 * app: ParkingApi.updateSection existed with zero callers, so an owner's
 * only way to fix a typo'd section name/price/capacity was delete-and-
 * recreate (or not at all, once it had booking history — removeSection
 * refuses to delete a section with bookings).
 */
@HiltViewModel
class AddSectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ParkingRepository,
) : ViewModel() {

    val listingId: String = checkNotNull(savedStateHandle["listingId"]) { "listingId is required" }
    val sectionId: String? = savedStateHandle.get<String>("sectionId")
    val isEditing: Boolean get() = sectionId != null

    private val _uiState = MutableStateFlow(AddSectionUiState())
    val uiState: StateFlow<AddSectionUiState> = _uiState.asStateFlow()

    init {
        if (sectionId != null) loadExisting(sectionId)
    }

    private fun loadExisting(sectionId: String) {
        _uiState.value = _uiState.value.copy(isLoadingExisting = true)
        viewModelScope.launch {
            when (val result = repository.getListing(listingId)) {
                is ParkingResult.Success -> {
                    val section = result.value.sections.firstOrNull { it.id == sectionId }
                    _uiState.value = _uiState.value.copy(
                        isLoadingExisting = false,
                        existing = section,
                        errorMessage = if (section == null) "Couldn't find that section." else null,
                    )
                }
                is ParkingResult.Error -> _uiState.value = _uiState.value.copy(isLoadingExisting = false, errorMessage = result.message)
            }
        }
    }

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

    fun saveEdits(
        name: String,
        supportedVehicleTypes: List<VehicleType>,
        capacity: Int,
        hourlyRateMinorUnits: Int,
        isCovered: Boolean,
        hasSecurity: Boolean,
        hasCctv: Boolean,
        hasEvCharging: Boolean,
        instantModeEnabled: Boolean,
    ) {
        val id = sectionId ?: return
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            val result = repository.updateSection(
                listingId, id, name, supportedVehicleTypes, capacity, hourlyRateMinorUnits,
                isCovered, hasSecurity, hasCctv, hasEvCharging, instantModeEnabled,
            )
            when (result) {
                is ParkingResult.Success -> _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
                is ParkingResult.Error -> _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = result.message)
            }
        }
    }
}
