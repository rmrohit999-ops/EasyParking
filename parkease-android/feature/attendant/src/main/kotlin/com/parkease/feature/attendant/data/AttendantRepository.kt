package com.parkease.feature.attendant.data

import com.parkease.core.model.BookingStatus
import com.parkease.core.model.toEnumOrNull
import com.parkease.core.network.api.AttendantApi
import com.parkease.core.network.model.CheckInRequest
import com.parkease.core.network.model.CheckOutRequest
import com.parkease.core.network.model.ReportMismatchRequest
import com.parkease.core.network.model.ResolveMismatchRequest
import com.parkease.core.network.model.ScanQrRequest
import javax.inject.Inject
import javax.inject.Singleton

data class ScanResultUi(
    val bookingId: String,
    val outcome: String,
    val bookingStatus: BookingStatus?,
    val vehicleRegistration: String,
    val vehicleCategory: String,
    val sectionName: String,
)

data class CheckActionResultUi(val outcome: String, val bookingStatus: BookingStatus?)

sealed class AttendantActionResult<out T> {
    data class Success<T>(val value: T) : AttendantActionResult<T>()
    data class Error(val message: String) : AttendantActionResult<Nothing>()
}

private const val FRIENDLY_ERROR = "Something went wrong. Please try again."

@Singleton
class AttendantRepository @Inject constructor(
    private val attendantApi: AttendantApi,
) {
    /**
     * Manual-entry token lookup rather than live camera scanning — see
     * feature:attendant's module doc comment (README-equivalent, this
     * file) for the disclosed Milestone 8 scope decision: the backend's
     * scan/verify/check-in/check-out endpoints are fully real, but this
     * build's UI reads the token as typed/pasted text rather than via
     * CameraX + ML Kit, which the Milestone 0 blueprint itself allows as
     * an operationally-reasonable fallback (§14.2).
     */
    suspend fun scan(token: String): AttendantActionResult<ScanResultUi> = runCatchingApi {
        val response = attendantApi.scan(ScanQrRequest(token))
        ScanResultUi(
            bookingId = response.bookingId,
            outcome = response.outcome,
            bookingStatus = response.booking.status.toEnumOrNull<BookingStatus>(),
            vehicleRegistration = response.booking.vehicleRegistration,
            vehicleCategory = response.booking.vehicleCategory,
            sectionName = response.booking.sectionName,
        )
    }

    suspend fun checkIn(bookingId: String, token: String, presentedRegistration: String?): AttendantActionResult<CheckActionResultUi> =
        runCatchingApi {
            val response = attendantApi.checkIn(bookingId, CheckInRequest(token, presentedRegistration?.ifBlank { null }))
            CheckActionResultUi(response.outcome, response.booking.status.toEnumOrNull<BookingStatus>())
        }

    suspend fun checkOut(bookingId: String, token: String): AttendantActionResult<CheckActionResultUi> = runCatchingApi {
        val response = attendantApi.checkOut(bookingId, CheckOutRequest(token))
        CheckActionResultUi(response.outcome, response.booking.status.toEnumOrNull<BookingStatus>())
    }

    suspend fun reportMismatch(bookingId: String, actualRegistration: String, note: String?): AttendantActionResult<BookingStatus?> =
        runCatchingApi {
            attendantApi.reportMismatch(bookingId, ReportMismatchRequest(actualRegistration, note = note?.ifBlank { null }))
                .status.toEnumOrNull<BookingStatus>()
        }

    suspend fun resolveMismatch(bookingId: String, resolution: String, reason: String?): AttendantActionResult<BookingStatus?> =
        runCatchingApi {
            attendantApi.resolveMismatch(bookingId, ResolveMismatchRequest(resolution, reason?.ifBlank { null })).status.toEnumOrNull<BookingStatus>()
        }

    private inline fun <T> runCatchingApi(block: () -> T): AttendantActionResult<T> = try {
        AttendantActionResult.Success(block())
    } catch (e: retrofit2.HttpException) {
        AttendantActionResult.Error(
            when (e.code()) {
                400 -> "That QR code isn't valid."
                403 -> "You're not authorized to operate on this parking listing."
                404 -> "We couldn't find that booking or QR code."
                409 -> "That booking isn't in the right state for this action right now."
                else -> FRIENDLY_ERROR
            },
        )
    } catch (e: Exception) {
        AttendantActionResult.Error(FRIENDLY_ERROR)
    }
}
