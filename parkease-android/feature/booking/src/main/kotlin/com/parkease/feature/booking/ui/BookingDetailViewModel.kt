package com.parkease.feature.booking.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.core.model.BookingStatus
import com.parkease.core.network.payments.RazorpayCheckoutResult
import com.parkease.core.network.payments.RazorpayResultBus
import com.parkease.feature.booking.data.BookingActionResult
import com.parkease.feature.booking.data.BookingRepository
import com.parkease.feature.booking.data.BookingUi
import com.parkease.feature.booking.data.PassUi
import com.parkease.feature.booking.data.PaymentOrderUi
import com.parkease.feature.booking.data.QuoteUi
import com.parkease.feature.booking.data.RefundUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

data class BookingDetailUiState(
    val isLoading: Boolean = true,
    val booking: BookingUi? = null,
    val actionInProgress: Boolean = false,
    val errorMessage: String? = null,
    val paymentOrder: PaymentOrderUi? = null,
    val paymentInProgress: Boolean = false,
    val paymentMessage: String? = null,
    /** Set once per created order so the screen knows to launch Razorpay Checkout exactly once for it. */
    val readyToLaunchCheckoutForOrderId: String? = null,
    val pass: PassUi? = null,
    val passLoading: Boolean = false,
    val passMessage: String? = null,
    val refunds: List<RefundUi> = emptyList(),
    val quote: QuoteUi? = null,
    val cashInProgress: Boolean = false,
)

@HiltViewModel
class BookingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookingRepository,
    private val razorpayResultBus: RazorpayResultBus,
    @Named("razorpayKeyId") val razorpayKeyId: String,
) : ViewModel() {

    val bookingId: String = checkNotNull(savedStateHandle["bookingId"]) { "bookingId is required" }

    private val _uiState = MutableStateFlow(BookingDetailUiState())
    val uiState: StateFlow<BookingDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            razorpayResultBus.results.collect { result -> onCheckoutResult(result) }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.getBooking(bookingId)) {
                is BookingActionResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, booking = result.value)
                    // The quote (Parking Fee / Total Payable) is only meaningful
                    // while payment is still pending — once paid, the booking's
                    // own cashAmount/paymentOrder already shows what was
                    // actually charged, which is what matters at that point.
                    if (result.value.status == BookingStatus.PENDING_PAYMENT) loadQuote()
                }
                is BookingActionResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
            loadRefunds()
        }
    }

    private suspend fun loadQuote() {
        when (val result = repository.getQuote(bookingId)) {
            is BookingActionResult.Success -> _uiState.value = _uiState.value.copy(quote = result.value)
            is BookingActionResult.Error -> Unit // Secondary display concern — Pay Now/Pay with Cash still work without it.
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
     * Starts a payment order for this booking, then — if a Razorpay key_id
     * is configured in this build (see NetworkConfigModule) — signals the
     * screen to actually launch Razorpay Checkout for it via
     * readyToLaunchCheckoutForOrderId. If no key is configured, this
     * honestly reports the gateway as unavailable rather than opening a
     * checkout sheet that could never succeed (same "no fakes" rule as
     * every other unconfigured integration in this codebase).
     */
    fun payNow() {
        _uiState.value = _uiState.value.copy(paymentInProgress = true, paymentMessage = null)
        viewModelScope.launch {
            when (val result = repository.createPaymentOrder(bookingId)) {
                is BookingActionResult.Success -> {
                    val order = result.value
                    _uiState.value = _uiState.value.copy(
                        paymentInProgress = false,
                        paymentOrder = order,
                        paymentMessage = when {
                            razorpayKeyId.isBlank() ->
                                "Payment order ${order.status.lowercase()}. Online checkout isn't configured in this build yet."
                            order.gatewayOrderId.isNullOrBlank() ->
                                "Payment order ${order.status.lowercase()}, but the gateway didn't return a checkout order."
                            else -> null
                        },
                        readyToLaunchCheckoutForOrderId =
                            order.id.takeIf { razorpayKeyId.isNotBlank() && !order.gatewayOrderId.isNullOrBlank() },
                    )
                }
                is BookingActionResult.Error -> _uiState.value = _uiState.value.copy(
                    paymentInProgress = false,
                    paymentMessage = result.message,
                )
            }
        }
    }

    /**
     * Driver taps "Pay with Cash" — records intent server-side (notifying
     * both sides) and refreshes so the screen switches to the "Cash
     * Payment Pending" state. Booking status itself doesn't change here;
     * it only moves to CONFIRMED once the owner/attendant actually
     * confirms cash in hand.
     */
    fun payWithCash() {
        _uiState.value = _uiState.value.copy(cashInProgress = true, paymentMessage = null)
        viewModelScope.launch {
            when (val result = repository.payCash(bookingId)) {
                is BookingActionResult.Success -> {
                    _uiState.value = _uiState.value.copy(cashInProgress = false, booking = result.value)
                }
                is BookingActionResult.Error -> _uiState.value = _uiState.value.copy(cashInProgress = false, paymentMessage = result.message)
            }
        }
    }

    /** Called by the screen right after it hands the order off to Checkout.open(), so it isn't relaunched on recomposition. */
    fun onCheckoutLaunched() {
        _uiState.value = _uiState.value.copy(readyToLaunchCheckoutForOrderId = null)
    }

    private fun onCheckoutResult(result: RazorpayCheckoutResult) {
        val order = _uiState.value.paymentOrder ?: return
        when (result) {
            is RazorpayCheckoutResult.Success -> {
                if (result.razorpayOrderId != null && result.razorpayOrderId != order.gatewayOrderId) return
                _uiState.value = _uiState.value.copy(paymentMessage = "Payment submitted — confirming with the bank…")
                pollUntilSettled(order.id)
            }
            is RazorpayCheckoutResult.Failure -> {
                _uiState.value = _uiState.value.copy(
                    paymentMessage = result.description?.takeIf { it.isNotBlank() } ?: "Payment was not completed.",
                )
            }
        }
    }

    /**
     * Razorpay's client-side success callback isn't itself proof of payment
     * — the backend only marks a PaymentOrder SUCCESSFUL once Razorpay's
     * signed webhook confirms it server-side (payments.service.ts). This
     * polls the order a few times to reflect that real status once it
     * lands, rather than trusting the client callback directly.
     */
    private fun pollUntilSettled(paymentOrderId: String) {
        viewModelScope.launch {
            repeat(8) { attempt ->
                delay(2000)
                when (val result = repository.getPayment(paymentOrderId)) {
                    is BookingActionResult.Success -> {
                        _uiState.value = _uiState.value.copy(paymentOrder = result.value)
                        if (result.value.status == "SUCCESSFUL" || result.value.status == "FAILED") {
                            _uiState.value = _uiState.value.copy(
                                paymentMessage = if (result.value.status == "SUCCESSFUL") {
                                    "Payment successful."
                                } else {
                                    "Payment failed. You can try again."
                                },
                            )
                            if (result.value.status == "SUCCESSFUL") refresh()
                            return@launch
                        }
                    }
                    is BookingActionResult.Error -> Unit
                }
                if (attempt == 7) {
                    _uiState.value = _uiState.value.copy(
                        paymentMessage = "Still confirming your payment — check back in a moment.",
                    )
                }
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
