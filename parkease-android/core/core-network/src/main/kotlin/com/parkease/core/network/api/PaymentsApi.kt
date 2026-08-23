package com.parkease.core.network.api

import com.parkease.core.network.model.*
import retrofit2.http.*

interface PaymentsApi {
    @POST("v1/payments/orders")
    suspend fun createOrder(@Body body: CreatePaymentOrderRequest): PaymentOrderResponse

    @GET("v1/payments/{paymentOrderId}")
    suspend fun getOne(@Path("paymentOrderId") paymentOrderId: String): PaymentOrderResponse

    @POST("v1/payments/{paymentOrderId}/retry")
    suspend fun retry(@Path("paymentOrderId") paymentOrderId: String, @Body body: RetryPaymentRequest): PaymentOrderResponse
}
