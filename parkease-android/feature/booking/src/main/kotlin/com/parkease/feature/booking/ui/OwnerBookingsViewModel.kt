package com.parkease.feature.booking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.feature.booking.data.BookingActionResult
import com.parkease.feature.booking.data.BookingRepository
import com.parkease.feature.booking.data.BookingUi
import com.parkease.feature.booking.data.QuoteUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OwnerBookingsUiState(
    val isLoading: Boolean = true,
    val bookings: List<BookingUi> = emptyList(),
    val actionInProgressBookingId: String? = null,
    val errorMessage: String? = null,
    /** Non-null while the "confirm cash received" dialog is showing for this booking. */
    val confirmDialogBookingId: String? = null,
    val confirmDialogAmount: QuoteUi? = null,
    val confirmDialogLoading: Boolean = false,
)

/**
 * BookingRepository.listBookings() already returns exactly the right rows
 * for an OWNER caller (bookings across their own parking listings — see
 * BookingService.listBookings' OWNER branch), so this needs no separate
 * "owner bookings" backend endpoint — just its own screen/state, since the
 * available actions (confirm cash received) are entirely different from
 * the driver-facing MyBookingsScreen.
 */
@HiltViewModel
class OwnerBookingsViewModel @Inject constructor(
    private val repository: BookingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OwnerBookingsUiState())
    val uiState: StateFlow<OwnerBookingsUiState> = _uiState.asStateFlow()

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

    /**
     * Opens the confirmation dialog with the authoritative amount fetched
     * fresh from the server (same computePayableBreakdown() cashCollect
     * itself uses) — never the list row's rough priceSnapshot estimate, so
     * what the owner is asked to confirm can never drift from what
     * actually gets recorded.
     */
    fun requestConfirmDialog(bookingId: String) {
        _uiState.value = _uiState.value.copy(confirmDialogBookingId = bookingId, confirmDialogLoading = true, confirmDialogAmount = null)
        viewModelScope.launch {
            when (val result = repository.getQuote(bookingId)) {
                is BookingActionResult.Success -> _uiState.value = _uiState.value.copy(confirmDialogLoading = false, confirmDialogAmount = result.value)
                is BookingActionResult.Error -> _uiState.value = _uiState.value.copy(
                    confirmDialogLoading = false,
                    confirmDialogBookingId = null,
                    errorMessage = result.message,
                )
            }
        }
    }

    fun dismissConfirmDialog() {
        _uiState.value = _uiState.value.copy(confirmDialogBookingId = null, confirmDialogAmount = null)
    }

    /**
     * The amount confirmed here always comes back from the server
     * (QrService.cashCollect recomputes it itself — see that method's doc
     * comment) — there is no amount field in this call for the owner to
     * adjust, by design (Milestone: "amount protection").
     */
    fun confirmPaymentReceived(bookingId: String) {
        _uiState.value = _uiState.value.copy(
            actionInProgressBookingId = bookingId,
            confirmDialogBookingId = null,
            confirmDialogAmount = null,
            errorMessage = null,
        )
        viewModelScope.launch {
            when (val result = repository.confirmCashReceived(bookingId)) {
                is BookingActionResult.Success -> {
                    _uiState.value = _uiState.value.copy(actionInProgressBookingId = null)
                    refresh()
                }
                is BookingActionResult.Error -> _uiState.value = _uiState.value.copy(actionInProgressBookingId = null, errorMessage = result.message)
            }
        }
    }
}
