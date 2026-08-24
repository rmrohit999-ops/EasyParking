package com.parkease.core.network.api

import com.parkease.core.network.model.*
import retrofit2.http.*

/** Mirrors parkease-backend's AdminController/ParkingAdminController/ReportsController (ADMIN-role-only routes) 1:1. */
interface AdminApi {
    @GET("v1/admin/dashboard/summary")
    suspend fun dashboardSummary(): AdminDashboardSummaryResponse

    @GET("v1/admin/users")
    suspend fun listUsers(
        @Query("role") role: String? = null,
        @Query("status") status: String? = null,
        @Query("q") q: String? = null,
    ): List<AdminUserSummaryResponse>

    @POST("v1/admin/users/{userId}/suspend")
    suspend fun suspendUser(@Path("userId") userId: String, @Body body: SuspendUserRequest): AdminUserSummaryResponse

    @POST("v1/admin/users/{userId}/reinstate")
    suspend fun reinstateUser(@Path("userId") userId: String): AdminUserSummaryResponse

    @GET("v1/admin/parking/listings/pending")
    suspend fun listPendingListings(): List<AdminPendingListingResponse>

    @POST("v1/admin/parking/listings/{listingId}/approve")
    suspend fun approveListing(@Path("listingId") listingId: String): AdminPendingListingResponse

    @POST("v1/admin/parking/listings/{listingId}/reject")
    suspend fun rejectListing(@Path("listingId") listingId: String, @Body body: AdminRejectRequest): AdminPendingListingResponse
}
