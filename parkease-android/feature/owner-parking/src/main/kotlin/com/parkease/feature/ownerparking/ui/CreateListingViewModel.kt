package com.parkease.feature.ownerparking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.core.model.ParkingType
import com.parkease.feature.ownerparking.data.ParkingRepository
import com.parkease.feature.ownerparking.data.ParkingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateListingUiState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val createdListingId: String? = null,
)

@HiltViewModel
class CreateListingViewModel @Inject constructor(
    private val repository: ParkingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateListingUiState())
    val uiState: StateFlow<CreateListingUiState> = _uiState.asStateFlow()

    fun createListing(name: String, parkingType: ParkingType, description: String?) {
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.createListing(name, parkingType, description)) {
                is ParkingResult.Success ->
                    _uiState.value = _uiState.value.copy(isSaving = false, createdListingId = result.value.id)
                is ParkingResult.Error ->
                    _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = result.message)
            }
        }
    }
}
