package com.parkease.core.network.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QrPassResponse(
    val qrPassId: String,
    val bookingId: String,
    val token: String,
    val issuedAt: String,
    val expiresAt: String,
    val status: String,
)

@JsonClass(generateAdapter = true)
data class CashCollectRequest(val confirmationMethod: String? = null, val auditNote: String? = null)

@JsonClass(generateAdapter = true)
data class ScanQrRequest(val token: String)

@JsonClass(generateAdapter = true)
data class ScanQrResponse(
    val qrPassId: String,
    val bookingId: String,
    val passStatus: String,
    val outcome: String,
    val booking: BookingOpsSummary,
)

@JsonClass(generateAdapter = true)
data class BookingOpsSummary(
    val id: String,
    val status: String,
    val vehicleCategory: String,
    val vehicleRegistration: String,
    val sectionName: String,
    val parkingId: String,
)

@JsonClass(generateAdapter = true)
data class CheckInRequest(val token: String, val presentedRegistrationNumber: String? = null)

@JsonClass(generateAdapter = true)
data class CheckOutRequest(val token: String)

@JsonClass(generateAdapter = true)
data class CheckActionResponse(val outcome: String, val booking: BookingResponse)

@JsonClass(generateAdapter = true)
data class ReportMismatchRequest(
    val actualVehicleRegistration: String,
    val actualCategory: String? = null,
    val note: String? = null,
)

@JsonClass(generateAdapter = true)
data class ResolveMismatchRequest(val resolution: String, val reason: String? = null)
