package com.parkease.core.network.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreatePaymentOrderRequest(val bookingId: String, val idempotencyKey: String)

@JsonClass(generateAdapter = true)
data class RetryPaymentRequest(val idempotencyKey: String)

@JsonClass(generateAdapter = true)
data class PaymentOrderResponse(
    val id: String,
    val bookingId: String,
    val gateway: String,
    val gatewayOrderId: String?,
    val amountMinorUnits: String,
    val currency: String,
    val status: String,
    val createdAt: String,
)
