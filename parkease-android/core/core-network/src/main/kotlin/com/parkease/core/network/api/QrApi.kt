package com.parkease.core.network.api

import com.parkease.core.network.model.*
import retrofit2.http.*

interface QrApi {
    @GET("v1/bookings/{bookingId}/pass")
    suspend fun getPass(@Path("bookingId") bookingId: String): QrPassResponse

    @POST("v1/bookings/{bookingId}/cash-collect")
    suspend fun cashCollect(@Path("bookingId") bookingId: String, @Body body: CashCollectRequest): BookingResponse
}
