package com.parkease.feature.booking.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.feature.booking.data.BookingActionResult
import com.parkease.feature.booking.data.BookingRepository
import com.parkease.feature.booking.data.BookingUi
import com.parkease.feature.booking.data.PassUi
import com.parkease.feature.booking.data.PaymentOrderUi
import com.parkease.feature.booking.data.RefundUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookingDetailUiState(
    val isLoading: Boolean = true,
    val booking: BookingUi? = null,
    val actionInProgress: Boolean = false,
    val errorMessage: String? = null,
    val paymentOrder: PaymentOrderUi? = null,
    val paymentInProgress: Boolean = false,
    val paymentMessage: String? = null,
    val pass: PassUi? = null,
    val passLoading: Boolean = false,
    val passMessage: String? = null,
    val refunds: List<RefundUi> = emptyList(),
)

@HiltViewModel
class BookingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookingRepository,
) : ViewModel() {

    val bookingId: String = checkNotNull(savedStateHandle["bookingId"]) { "bookingId is required" }

    private val _uiState = MutableStateFlow(BookingDetailUiState())
    val uiState: StateFlow<BookingDetailUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.getBooking(bookingId)) {
                is BookingActionResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false, booking = result.value)
                is BookingActionResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
            loadRefunds()
        }
    }

    /**
     * Cancelling (Milestone 9) always triggers a refund server-side as part
     * of the same request — see BookingRepository.cancelBooking's doc
     * comment — so a successful cancel here also re-fetches this booking's
     * refunds to show what was decided/dispatched.
     */
    fun cancel() {
        _uiState.value = _uiState.value.copy(actionInProgress = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.cancelBooking(bookingId, reason = null)) {
                is BookingActionResult.Success -> {
                    _uiState.value = _uiState.value.copy(actionInProgress = false, booking = result.value)
                    loadRefunds()
                }
                is BookingActionResult.Error -> _uiState.value = _uiState.value.copy(actionInProgress = false, errorMessage = result.message)
            }
        }
    }

    private suspend fun loadRefunds() {
        when (val result = repository.getRefunds(bookingId)) {
            is BookingActionResult.Success -> _uiState.value = _uiState.value.copy(refunds = result.value)
            is BookingActionResult.Error -> Unit // Refunds are a secondary display concern — silently leave the list as-is on failure.
        }
    }

    /**
     * Starts a payment order for this booking (Milestone 7). Does not — and,
     * without a real gateway SDK integrated client-side, cannot — actually
     * collect a card here; it surfaces whatever the backend reports
     * (typically "Online payments are temporarily unavailable" in this
     * build, since no live gateway credentials exist anywhere in this
     * environment). See BookingRepository.createPaymentOrder's doc comment.
     */
    fun payNow() {
        _uiState.value = _uiState.value.copy(paymentInProgress = true, paymentMessage = null)
        viewModelScope.launch {
            when (val result = repository.createPaymentOrder(bookingId)) {
                is BookingActionResult.Success -> _uiState.value = _uiState.value.copy(
                    paymentInProgress = false,
                    paymentOrder = result.value,
                    paymentMessage = "Payment order ${result.value.status.lowercase()}. Gateway checkout isn't wired into this build yet.",
                )
                is BookingActionResult.Error -> _uiState.value = _uiState.value.copy(
                    paymentInProgress = false,
                    paymentMessage = result.message,
                )
            }
        }
    }

    /** Fetches (issuing on first tap) the entry QR pass — shown to an attendant/owner at the gate. */
    fun showPass() {
        _uiState.value = _uiState.value.copy(passLoading = true, passMessage = null)
        viewModelScope.launch {
            when (val result = repository.getPass(bookingId)) {
                is BookingActionResult.Success -> _uiState.value = _uiState.value.copy(passLoading = false, pass = result.value)
                is BookingActionResult.Error -> _uiState.value = _uiState.value.copy(passLoading = false, passMessage = result.message)
            }
        }
    }
}
