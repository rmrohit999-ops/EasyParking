package com.parkease.feature.booking.ui

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
import javax.inject.Inject

data class MyBookingsUiState(
    val isLoading: Boolean = true,
    val bookings: List<BookingUi> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class MyBookingsViewModel @Inject constructor(
    private val repository: BookingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyBookingsUiState())
    val uiState: StateFlow<MyBookingsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.listBookings()) {
                is BookingActionResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false, bookings = result.value)
                is BookingActionResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }
}
