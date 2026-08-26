package com.parkease.feature.booking.data

import com.parkease.core.model.BookingStatus
import com.parkease.core.model.BookingType
import com.parkease.core.model.toEnumOrNull
import com.parkease.core.network.api.BookingApi
import com.parkease.core.network.api.PaymentsApi
import com.parkease.core.network.api.QrApi
import com.parkease.core.network.api.RefundsApi
import com.parkease.core.network.api.VehiclesApi
import com.parkease.core.network.model.CancelBookingRequest
import com.parkease.core.network.model.CashCollectRequest
import com.parkease.core.network.model.ConfirmBookingRequest
import com.parkease.core.network.model.CreateHoldRequest
import com.parkease.core.network.model.CreateInstantBookingRequest
import com.parkease.core.network.model.CreatePaymentOrderRequest
import com.parkease.core.network.model.MyReviewResponse
import com.parkease.core.network.model.PaymentOrderResponse
import com.parkease.core.network.model.QrPassResponse
import com.parkease.core.network.model.RefundResponse
import com.parkease.core.network.model.RetryPaymentRequest
import com.parkease.core.network.model.SubmitReviewRequest
import com.parkease.core.model.Money
import java.math.BigInteger
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class BookingUi(
    val id: String,
    val sectionId: String,
    val parkingId: String,
    val bookingType: BookingType?,
    val status: BookingStatus?,
    val intendedPaymentMethod: String?,
    val startTime: Instant?,
    val endTime: Instant?,
    val actualCheckInAt: Instant?,
    val actualCheckOutAt: Instant?,
    val priceSnapshot: Map<String, Any?>?,
    val vehicleRegistrationNumber: String?,
    val driverContact: String?,
    val parkingName: String?,
    val parkingLatitude: Double?,
    val parkingLongitude: Double?,
    val cashAmount: Money?,
    val cashConfirmedAt: Instant?,
)

data class QuoteUi(
    val parkingAmount: Money,
    val taxAmount: Money,
    val totalPayable: Money,
)

data class PaymentOrderUi(
    val id: String,
    val bookingId: String,
    val gateway: String,
    val gatewayOrderId: String?,
    val status: String,
    val amountMinorUnits: String,
    val currency: String,
)

data class PassUi(
    val qrPassId: String,
    val bookingId: String,
    val token: String,
    val status: String,
    val expiresAt: Instant?,
)

data class ReviewUi(
    val id: String,
    val bookingId: String,
    val overall: Int,
    val cleanliness: Int?,
    val security: Int?,
    val location: Int?,
    val comment: String?,
    val createdAt: Instant?,
)

data class RefundUi(
    val id: String,
    val refundType: String,
    val reasonCode: String,
    val amount: Money,
    val status: String,
    val createdAt: Instant?,
)

sealed class BookingActionResult<out T> {
    data class Success<T>(val value: T) : BookingActionResult<T>()
    data class Error(val message: String) : BookingActionResult<Nothing>()
}

private const val FRIENDLY_ERROR = "Something went wrong. Please try again."

@Singleton
class BookingRepository @Inject constructor(
    private val bookingApi: BookingApi,
    private val vehiclesApi: VehiclesApi,
    private val paymentsApi: PaymentsApi,
    private val qrApi: QrApi,
    private val refundsApi: RefundsApi,
) {
    /** Null if the driver has no vehicles — booking can't proceed without one. */
    suspend fun getDefaultVehicleId(): String? = try {
        val vehicles = vehiclesApi.list()
        (vehicles.firstOrNull { it.isDefault } ?: vehicles.firstOrNull())?.id
    } catch (e: Exception) {
        null
    }

    suspend fun bookAdvance(sectionId: String, vehicleId: String, startTime: Instant, endTime: Instant): BookingActionResult<BookingUi> =
        runCatchingApi {
            val hold = bookingApi.createHold(CreateHoldRequest(sectionId, vehicleId))
            bookingApi.confirmBooking(ConfirmBookingRequest(hold.id, startTime.toString(), endTime.toString())).toUi()
        }

    suspend fun bookInstant(sectionId: String, vehicleId: String): BookingActionResult<BookingUi> = runCatchingApi {
        bookingApi.createInstantBooking(CreateInstantBookingRequest(sectionId, vehicleId)).toUi()
    }

    suspend fun listBookings(): BookingActionResult<List<BookingUi>> = runCatchingApi {
        bookingApi.list().map { it.toUi() }
    }

    suspend fun getBooking(bookingId: String): BookingActionResult<BookingUi> = runCatchingApi {
        bookingApi.getOne(bookingId).toUi()
    }

    suspend fun cancelBooking(bookingId: String, reason: String?): BookingActionResult<BookingUi> = runCatchingApi {
        bookingApi.cancel(bookingId, CancelBookingRequest(reason)).toUi()
    }

    /**
     * Starts (or, on the same call, replays) a payment order for a
     * PENDING_PAYMENT booking. A fresh UUID idempotency key is generated
     * per call — that's correct here because a *new* tap on "Pay Now" is a
     * new attempt, not a retry of a specific failed one; retrying a
     * specific FAILED order is [retryPayment] instead, which reuses the
     * same booking but still needs its own fresh key (Milestone 0 §3).
     *
     * This build has no live PAYMENT_KEY_ID/PAYMENT_KEY_SECRET configured
     * on the backend (see Milestone 7's backend README notes), so this
     * will reliably come back as a 503 "Online payments are temporarily
     * unavailable" error in this environment — that's the honest,
     * documented "unavailable state," not a bug, matching Milestone 0
     * §14.6's disclosed behavior when no gateway credentials exist.
     */
    suspend fun createPaymentOrder(bookingId: String): BookingActionResult<PaymentOrderUi> = runCatchingApi {
        paymentsApi.createOrder(CreatePaymentOrderRequest(bookingId, UUID.randomUUID().toString())).toUi()
    }

    suspend fun retryPayment(paymentOrderId: String): BookingActionResult<PaymentOrderUi> = runCatchingApi {
        paymentsApi.retry(paymentOrderId, RetryPaymentRequest(UUID.randomUUID().toString())).toUi()
    }

    /** The same computePayableBreakdown() the gateway order and cashCollect both use — shows "Parking Fee / Total Payable" before the driver has picked a payment method. */
    suspend fun getQuote(bookingId: String): BookingActionResult<QuoteUi> = runCatchingApi {
        val q = bookingApi.getQuote(bookingId)
        QuoteUi(
            parkingAmount = Money(BigInteger(q.parkingAmountMinorUnits), q.currency),
            taxAmount = Money(BigInteger(q.taxAmountMinorUnits), q.currency),
            totalPayable = Money(BigInteger(q.totalPayableMinorUnits), q.currency),
        )
    }

    /** Driver picks "pay with cash" — records intent, notifies both sides. Does not change booking status; cashCollect (owner/attendant side) does that. */
    suspend fun payCash(bookingId: String): BookingActionResult<BookingUi> = runCatchingApi {
        bookingApi.payCash(bookingId).toUi()
    }

    /** Owner/attendant confirms cash was physically received — the amount always comes from the server, never a client-supplied field, so it can't be silently adjusted here. */
    suspend fun confirmCashReceived(bookingId: String): BookingActionResult<BookingUi> = runCatchingApi {
        qrApi.cashCollect(bookingId, CashCollectRequest()).toUi()
    }

    suspend fun getPayment(paymentOrderId: String): BookingActionResult<PaymentOrderUi> = runCatchingApi {
        paymentsApi.getOne(paymentOrderId).toUi()
    }

    /**
     * Fetches (issuing on first call) the driver's QR pass for a booking.
     * The pass is shown to an attendant/owner to scan or manually enter —
     * see feature:attendant, which is the other side of this exchange.
     */
    suspend fun getPass(bookingId: String): BookingActionResult<PassUi> = runCatchingApi {
        qrApi.getPass(bookingId).toUi()
    }

    /**
     * Refunds (Milestone 9) are never requested directly by the client —
     * cancelling a booking (bookingApi.cancel above) triggers one
     * server-side as part of the same request. This just reads back
     * whatever the server has already decided/dispatched, for display.
     */
    suspend fun getRefunds(bookingId: String): BookingActionResult<List<RefundUi>> = runCatchingApi {
        refundsApi.listForBooking(bookingId).map { it.toUi() }
    }

    /** One review per COMPLETED booking — the backend rejects anything else (still-active bookings, a second attempt) with a real error, never silently accepted. */
    suspend fun submitReview(bookingId: String, overall: Int, cleanliness: Int?, security: Int?, location: Int?, comment: String?): BookingActionResult<ReviewUi> =
        runCatchingApi {
            bookingApi.submitReview(bookingId, SubmitReviewRequest(overall, cleanliness, security, location, comment)).toReviewUi(bookingId)
        }

    /** Null (never an error) when the driver hasn't reviewed this booking yet — that's the expected common case, not a failure. */
    suspend fun getMyReview(bookingId: String): ReviewUi? = try {
        bookingApi.getMyReview(bookingId)?.toReviewUi(bookingId)
    } catch (e: Exception) {
        null
    }

    private inline fun <T> runCatchingApi(block: () -> T): BookingActionResult<T> = try {
        BookingActionResult.Success(block())
    } catch (e: retrofit2.HttpException) {
        BookingActionResult.Error(
            when (e.code()) {
                400 -> "That vehicle isn't compatible with this section, or your dates are invalid."
                403 -> "You don't have permission to do that."
                404 -> "We couldn't find that."
                409 -> "No spaces are available right now — please try another listing or time."
                410 -> "That hold expired. Please search again."
                503 -> "Online payments are temporarily unavailable. Please try again later."
                else -> FRIENDLY_ERROR
            },
        )
    } catch (e: Exception) {
        BookingActionResult.Error(FRIENDLY_ERROR)
    }
}

private fun PaymentOrderResponse.toUi() = PaymentOrderUi(
    id = id,
    bookingId = bookingId,
    gateway = gateway,
    gatewayOrderId = gatewayOrderId,
    status = status,
    amountMinorUnits = amountMinorUnits,
    currency = currency,
)

private fun RefundResponse.toUi() = RefundUi(
    id = id,
    refundType = refundType,
    reasonCode = reasonCode,
    amount = Money(BigInteger(amountMinorUnits)),
    status = status,
    createdAt = runCatching { Instant.parse(createdAt) }.getOrNull(),
)

private fun QrPassResponse.toUi() = PassUi(
    qrPassId = qrPassId,
    bookingId = bookingId,
    token = token,
    status = status,
    expiresAt = runCatching { Instant.parse(expiresAt) }.getOrNull(),
)

private fun MyReviewResponse.toReviewUi(fallbackBookingId: String) = ReviewUi(
    id = id,
    bookingId = bookingId.ifBlank { fallbackBookingId },
    overall = ratings["overall"]?.toInt() ?: 0,
    cleanliness = ratings["cleanliness"]?.toInt(),
    security = ratings["security"]?.toInt(),
    location = ratings["location"]?.toInt(),
    comment = comment,
    createdAt = runCatching { Instant.parse(createdAt) }.getOrNull(),
)

private fun com.parkease.core.network.model.BookingResponse.toUi() = BookingUi(
    id = id,
    sectionId = sectionId,
    parkingId = parkingId,
    bookingType = bookingType.toEnumOrNull<BookingType>(),
    status = status.toEnumOrNull<BookingStatus>(),
    intendedPaymentMethod = intendedPaymentMethod,
    startTime = startTime?.let { runCatching { Instant.parse(it) }.getOrNull() },
    endTime = endTime?.let { runCatching { Instant.parse(it) }.getOrNull() },
    actualCheckInAt = actualCheckInAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    actualCheckOutAt = actualCheckOutAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    priceSnapshot = priceSnapshot,
    vehicleRegistrationNumber = vehicleRegistrationNumber,
    driverContact = driverContact,
    parkingName = parkingName,
    parkingLatitude = parkingLatitude,
    parkingLongitude = parkingLongitude,
    cashAmount = cashAmountMinorUnits?.let { Money(BigInteger(it)) },
    cashConfirmedAt = cashConfirmedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
)
