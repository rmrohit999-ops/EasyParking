package com.parkease.core.network.model

import com.squareup.moshi.JsonClass

// ---------------------------------------------------------------------
// Payout accounts (Milestone 9)
// ---------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class CreatePayoutAccountRequest(
    val method: String, // "BANK" | "UPI"
    val accountHolderName: String,
    val accountNumber: String? = null,
    val ifsc: String? = null,
    val upiVpa: String? = null,
)

@JsonClass(generateAdapter = true)
data class PayoutAccountResponse(
    val id: String,
    val method: String,
    val accountHolderName: String,
    val accountNumberMasked: String?,
    val ifsc: String?,
    val upiVpaMasked: String?,
    val verificationStatus: String,
    val isPrimary: Boolean,
    val createdAt: String,
)

// ---------------------------------------------------------------------
// Earnings (Milestone 9)
// ---------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class EarningsStatusBucket(val amountMinorUnits: String, val count: Int)

@JsonClass(generateAdapter = true)
data class EarningsSummaryResponse(val ownerId: String, val byStatus: Map<String, EarningsStatusBucket>)

@JsonClass(generateAdapter = true)
data class LedgerEntryResponse(
    val id: String,
    val bookingId: String,
    val parkingId: String,
    val sectionId: String,
    val vehicleCategory: String,
    val amountMinorUnits: String,
    val status: String,
    val createdAt: String,
)

// ---------------------------------------------------------------------
// Settlements (Milestone 9)
// ---------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class SettlementResponse(
    val id: String,
    val ownerId: String,
    val requestedAmountMinorUnits: String,
    val status: String,
    val payoutAccountId: String,
    val gatewayPayoutId: String?,
    val requestedAt: String,
    val processedAt: String?,
)

// ---------------------------------------------------------------------
// Refunds (Milestone 9)
// ---------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class RefundResponse(
    val id: String,
    val transactionId: String,
    val bookingId: String,
    val refundType: String,
    val reasonCode: String,
    val amountMinorUnits: String,
    val status: String,
    val gatewayRefundId: String?,
    val initiatedBy: String?,
    val approvedBy: String?,
    val createdAt: String,
    val completedAt: String?,
)
