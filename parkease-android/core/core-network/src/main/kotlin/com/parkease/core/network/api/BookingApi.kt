package com.parkease.core.network.api

import com.parkease.core.network.model.*
import retrofit2.http.*

interface BookingApi {
    @POST("v1/bookings/holds")
    suspend fun createHold(@Body body: CreateHoldRequest): HoldResponse

    @POST("v1/bookings")
    suspend fun confirmBooking(@Body body: ConfirmBookingRequest): BookingResponse

    @POST("v1/bookings/instant")
    suspend fun createInstantBooking(@Body body: CreateInstantBookingRequest): BookingResponse

    @GET("v1/bookings")
    suspend fun list(): List<BookingResponse>

    @GET("v1/bookings/{bookingId}")
    suspend fun getOne(@Path("bookingId") bookingId: String): BookingResponse

    @POST("v1/bookings/{bookingId}/cancel")
    suspend fun cancel(@Path("bookingId") bookingId: String, @Body body: CancelBookingRequest): BookingResponse

    @GET("v1/bookings/{bookingId}/quote")
    suspend fun getQuote(@Path("bookingId") bookingId: String): BookingQuoteResponse

    @POST("v1/bookings/{bookingId}/pay-cash")
    suspend fun payCash(@Path("bookingId") bookingId: String): BookingResponse

    @POST("v1/bookings/{bookingId}/review")
    suspend fun submitReview(@Path("bookingId") bookingId: String, @Body body: SubmitReviewRequest): MyReviewResponse

    @GET("v1/bookings/{bookingId}/review")
    suspend fun getMyReview(@Path("bookingId") bookingId: String): MyReviewResponse?
}
