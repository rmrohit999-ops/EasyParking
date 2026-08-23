package com.parkease.feature.ownerparking.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.feature.ownerparking.data.ParkingRepository
import com.parkease.feature.ownerparking.data.ParkingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocationFormUiState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class LocationFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ParkingRepository,
) : ViewModel() {

    val listingId: String = checkNotNull(savedStateHandle["listingId"]) { "listingId is required" }

    private val _uiState = MutableStateFlow(LocationFormUiState())
    val uiState: StateFlow<LocationFormUiState> = _uiState.asStateFlow()

    fun save(
        latitude: Double,
        longitude: Double,
        addressLine: String,
        city: String,
        state: String,
        postalCode: String,
        entranceNotes: String?,
    ) {
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.upsertLocation(listingId, latitude, longitude, addressLine, city, state, postalCode, entranceNotes)) {
                is ParkingResult.Success -> _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
                is ParkingResult.Error -> _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = result.message)
            }
        }
    }
}
