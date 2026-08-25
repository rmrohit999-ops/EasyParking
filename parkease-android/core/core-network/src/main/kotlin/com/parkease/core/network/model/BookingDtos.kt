package com.parkease.core.network.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateHoldRequest(val sectionId: String, val vehicleId: String)

@JsonClass(generateAdapter = true)
data class HoldResponse(val id: String, val sectionId: String, val vehicleId: String, val expiresAt: String)

@JsonClass(generateAdapter = true)
data class ConfirmBookingRequest(val holdId: String, val startTime: String, val endTime: String)

@JsonClass(generateAdapter = true)
data class CreateInstantBookingRequest(val sectionId: String, val vehicleId: String)

@JsonClass(generateAdapter = true)
data class CancelBookingRequest(val reason: String? = null)

@JsonClass(generateAdapter = true)
data class BookingResponse(
    val id: String,
    val driverId: String,
    val vehicleId: String,
    val parkingId: String,
    val sectionId: String,
    val vehicleCategory: String,
    val bookingType: String,
    val status: String,
    /** Null until the driver picks "pay with cash" on the payment screen — see BookingApi.payCash. */
    val intendedPaymentMethod: String?,
    val startTime: String?,
    val endTime: String?,
    val actualCheckInAt: String?,
    val actualCheckOutAt: String?,
    // Left as a raw JSON-shaped map rather than a typed DTO — the
    // snapshot's shape is intentionally still evolving (Milestone 7 adds
    // tax/commission fields to it), and this screen only ever displays it,
    // never recomputes from it.
    val priceSnapshot: Map<String, Any?>?,
    val createdAt: String,
    // Owner-list enrichment — populated by listBookings/getBooking, null
    // elsewhere (e.g. right after confirmBooking/payCash).
    val vehicleRegistrationNumber: String?,
    val driverContact: String?,
    val parkingName: String?,
    val parkingLatitude: Double?,
    val parkingLongitude: Double?,
    val cashAmountMinorUnits: String?,
    val cashConfirmedAt: String?,
)

/** GET /bookings/:id/quote — the authoritative payable amount before the driver has picked a payment method at all. */
@JsonClass(generateAdapter = true)
data class BookingQuoteResponse(
    val bookingId: String,
    val currency: String,
    val parkingAmountMinorUnits: String,
    val taxAmountMinorUnits: String,
    val totalPayableMinorUnits: String,
)
