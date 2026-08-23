package com.parkease.feature.vehicles.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.core.model.VehicleCategory
import com.parkease.core.model.VehicleType
import com.parkease.feature.vehicles.data.VehicleUi
import com.parkease.feature.vehicles.data.VehiclesRepository
import com.parkease.feature.vehicles.data.VehiclesResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VehiclesUiState(
    val isLoading: Boolean = true,
    val vehicles: List<VehicleUi> = emptyList(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val justAdded: Boolean = false,
)

@HiltViewModel
class VehiclesViewModel @Inject constructor(
    private val repository: VehiclesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VehiclesUiState())
    val uiState: StateFlow<VehiclesUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val vehicles = repository.list()
                _uiState.value = _uiState.value.copy(isLoading = false, vehicles = vehicles)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "We couldn't load your vehicles right now. Please try again.",
                )
            }
        }
    }

    fun addVehicle(
        category: VehicleCategory,
        vehicleType: VehicleType,
        registrationNumber: String,
        make: String?,
        model: String?,
        setAsDefault: Boolean,
    ) {
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.addVehicle(category, vehicleType, registrationNumber, make, model, setAsDefault)) {
                is VehiclesResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, justAdded = true)
                    refresh()
                }
                is VehiclesResult.Error -> _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = result.message)
            }
        }
    }

    fun setDefault(vehicleId: String) {
        viewModelScope.launch {
            runCatching { repository.setDefault(vehicleId) }
            refresh()
        }
    }

    fun remove(vehicleId: String) {
        viewModelScope.launch {
            runCatching { repository.remove(vehicleId) }
            refresh()
        }
    }

    fun consumeJustAdded() {
        _uiState.value = _uiState.value.copy(justAdded = false)
    }
}
