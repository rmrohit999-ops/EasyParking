package com.parkease.feature.booking.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.feature.booking.data.BookingActionResult
import com.parkease.feature.booking.data.BookingRepository
import com.parkease.feature.booking.data.BookingUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class BookingConfirmUiState(
    val isSubmitting: Boolean = false,
    val hasVehicle: Boolean = true,
    val errorMessage: String? = null,
    val confirmedBooking: BookingUi? = null,
)

@HiltViewModel
class BookingConfirmViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookingRepository,
) : ViewModel() {

    val sectionId: String = checkNotNull(savedStateHandle["sectionId"]) { "sectionId is required" }
    val isInstant: Boolean = savedStateHandle["isInstant"] ?: false

    /** Set only when navigated here from a flow that already resolved a date/time (e.g. advance-booking destination search) — null falls back to the screen's own "now+15min / now+2h" defaults. */
    val prefilledStartEpochMillis: Long? = savedStateHandle.get<String>("startEpochMillis")?.toLongOrNull()
    val prefilledEndEpochMillis: Long? = savedStateHandle.get<String>("endEpochMillis")?.toLongOrNull()

    private val _uiState = MutableStateFlow(BookingConfirmUiState())
    val uiState: StateFlow<BookingConfirmUiState> = _uiState.asStateFlow()

    fun bookAdvance(startTime: Instant, endTime: Instant) {
        _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val vehicleId = repository.getDefaultVehicleId()
            if (vehicleId == null) {
                _uiState.value = _uiState.value.copy(isSubmitting = false, hasVehicle = false)
                return@launch
            }
            when (val result = repository.bookAdvance(sectionId, vehicleId, startTime, endTime)) {
                is BookingActionResult.Success ->
                    _uiState.value = _uiState.value.copy(isSubmitting = false, confirmedBooking = result.value)
                is BookingActionResult.Error ->
                    _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = result.message)
            }
        }
    }

    fun bookInstant() {
        _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val vehicleId = repository.getDefaultVehicleId()
            if (vehicleId == null) {
                _uiState.value = _uiState.value.copy(isSubmitting = false, hasVehicle = false)
                return@launch
            }
            when (val result = repository.bookInstant(sectionId, vehicleId)) {
                is BookingActionResult.Success ->
                    _uiState.value = _uiState.value.copy(isSubmitting = false, confirmedBooking = result.value)
                is BookingActionResult.Error ->
                    _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = result.message)
            }
        }
    }
}
