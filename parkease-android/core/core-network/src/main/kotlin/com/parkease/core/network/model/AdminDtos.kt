package com.parkease.core.network.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AdminDashboardSummaryResponse(
    val totalUsers: Int,
    val suspendedUsers: Int,
    val pendingListings: Int,
    val openFraudAlerts: Int,
    val openSupportTickets: Int,
    val openDisputes: Int,
)

@JsonClass(generateAdapter = true)
data class AdminUserSummaryResponse(
    val id: String,
    val phone: String?,
    val email: String?,
    val status: String,
    val roles: List<String>,
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class SuspendUserRequest(val reason: String)

@JsonClass(generateAdapter = true)
data class AdminRejectRequest(val reason: String)

@JsonClass(generateAdapter = true)
data class AdminPendingListingResponse(
    val id: String,
    val name: String,
    val parkingType: String,
    val approvalStatus: String,
    val createdAt: String,
)
