package com.parkease.core.network.api

import com.parkease.core.network.model.*
import retrofit2.http.*

/** Owner-facing earnings/payout-account/settlement endpoints (Milestone 9). */
interface EarningsApi {
    @POST("v1/owner/payout-accounts")
    suspend fun createPayoutAccount(@Body body: CreatePayoutAccountRequest): PayoutAccountResponse

    @GET("v1/owner/payout-accounts")
    suspend fun listPayoutAccounts(): List<PayoutAccountResponse>

    @POST("v1/owner/payout-accounts/{accountId}/primary")
    suspend fun setPrimaryPayoutAccount(@Path("accountId") accountId: String): PayoutAccountResponse

    @DELETE("v1/owner/payout-accounts/{accountId}")
    suspend fun removePayoutAccount(@Path("accountId") accountId: String)

    @GET("v1/owner/earnings/summary")
    suspend fun earningsSummary(): EarningsSummaryResponse

    @GET("v1/owner/earnings/ledger")
    suspend fun earningsLedger(@Query("status") status: String? = null): List<LedgerEntryResponse>

    @POST("v1/owner/settlements")
    suspend fun requestSettlement(): SettlementResponse

    @GET("v1/owner/settlements")
    suspend fun listSettlements(): List<SettlementResponse>
}

/** Refund reads, usable by driver/owner/admin alike (role/ownership scoped server-side). */
interface RefundsApi {
    @GET("v1/bookings/{bookingId}/refunds")
    suspend fun listForBooking(@Path("bookingId") bookingId: String): List<RefundResponse>
}
