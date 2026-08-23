package com.parkease.core.network.api

import com.parkease.core.network.model.*
import retrofit2.http.*

interface AttendantApi {
    @POST("v1/attendant/qr/scan")
    suspend fun scan(@Body body: ScanQrRequest): ScanQrResponse

    @POST("v1/attendant/bookings/{bookingId}/check-in")
    suspend fun checkIn(@Path("bookingId") bookingId: String, @Body body: CheckInRequest): CheckActionResponse

    @POST("v1/attendant/bookings/{bookingId}/check-out")
    suspend fun checkOut(@Path("bookingId") bookingId: String, @Body body: CheckOutRequest): CheckActionResponse

    @POST("v1/attendant/bookings/{bookingId}/mismatch")
    suspend fun reportMismatch(@Path("bookingId") bookingId: String, @Body body: ReportMismatchRequest): BookingResponse

    @POST("v1/attendant/bookings/{bookingId}/mismatch/resolve")
    suspend fun resolveMismatch(@Path("bookingId") bookingId: String, @Body body: ResolveMismatchRequest): BookingResponse
}
