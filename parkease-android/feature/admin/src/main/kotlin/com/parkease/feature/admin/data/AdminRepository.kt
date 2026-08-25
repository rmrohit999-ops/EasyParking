package com.parkease.feature.admin.data

import com.parkease.core.model.Money
import com.parkease.core.network.api.AdminApi
import com.parkease.core.network.model.AdminRejectRequest
import com.parkease.core.network.model.SuspendUserRequest
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

data class DashboardSummaryUi(
    val totalUsers: Int,
    val suspendedUsers: Int,
    val pendingListings: Int,
    val openFraudAlerts: Int,
    val openSupportTickets: Int,
    val openDisputes: Int,
)

data class AdminUserUi(
    val id: String,
    val phone: String?,
    val email: String?,
    val status: String,
    val roles: List<String>,
)

data class PendingListingUi(
    val id: String,
    val name: String,
    val parkingType: String,
    val approvalStatus: String,
)

data class CashByOwnerUi(
    val ownerId: String,
    val label: String,
    val transactionCount: Int,
    val totalCollected: Money,
    val commission: Money,
    val netEarnings: Money,
)

data class CashSummaryUi(
    val totalCollected: Money,
    val totalCommission: Money,
    val totalOwnerNet: Money,
    val completedCount: Int,
    val pendingCount: Int,
    val byOwner: List<CashByOwnerUi>,
)

data class MapsQuotaSkuUi(
    val sku: String,
    val count: Int,
    val cap: Int,
    val percentUsed: Int,
    val capReached: Boolean,
)

data class MapsQuotaSnapshotUi(
    val date: String,
    val globallyTripped: Boolean,
    val skus: List<MapsQuotaSkuUi>,
)

sealed class AdminResult<out T> {
    data class Success<T>(val value: T) : AdminResult<T>()
    data class Error(val message: String) : AdminResult<Nothing>()
}

private const val FRIENDLY_ERROR = "Something went wrong. Please try again."

@Singleton
class AdminRepository @Inject constructor(
    private val adminApi: AdminApi,
) {
    suspend fun dashboardSummary(): AdminResult<DashboardSummaryUi> = runCatchingApi {
        val r = adminApi.dashboardSummary()
        DashboardSummaryUi(r.totalUsers, r.suspendedUsers, r.pendingListings, r.openFraudAlerts, r.openSupportTickets, r.openDisputes)
    }

    suspend fun listUsers(query: String? = null): AdminResult<List<AdminUserUi>> = runCatchingApi {
        adminApi.listUsers(q = query?.takeIf { it.isNotBlank() }).map {
            AdminUserUi(id = it.id, phone = it.phone, email = it.email, status = it.status, roles = it.roles)
        }
    }

    suspend fun suspendUser(userId: String, reason: String): AdminResult<Unit> = runCatchingApi {
        adminApi.suspendUser(userId, SuspendUserRequest(reason))
        Unit
    }

    suspend fun reinstateUser(userId: String): AdminResult<Unit> = runCatchingApi {
        adminApi.reinstateUser(userId)
        Unit
    }

    suspend fun listPendingListings(): AdminResult<List<PendingListingUi>> = runCatchingApi {
        adminApi.listPendingListings().map { PendingListingUi(it.id, it.name, it.parkingType, it.approvalStatus) }
    }

    suspend fun approveListing(listingId: String): AdminResult<Unit> = runCatchingApi {
        adminApi.approveListing(listingId)
        Unit
    }

    suspend fun rejectListing(listingId: String, reason: String): AdminResult<Unit> = runCatchingApi {
        adminApi.rejectListing(listingId, AdminRejectRequest(reason))
        Unit
    }

    suspend fun cashSummary(): AdminResult<CashSummaryUi> = runCatchingApi {
        val r = adminApi.cashSummary()
        CashSummaryUi(
            totalCollected = Money(BigInteger(r.totalCashCollectedMinorUnits), r.currency),
            totalCommission = Money(BigInteger(r.totalCommissionMinorUnits), r.currency),
            totalOwnerNet = Money(BigInteger(r.totalOwnerNetMinorUnits), r.currency),
            completedCount = r.completedCount,
            pendingCount = r.pendingCount,
            byOwner = r.byOwner.map {
                CashByOwnerUi(
                    ownerId = it.ownerId,
                    label = it.businessName ?: it.email ?: it.phone ?: "Owner",
                    transactionCount = it.transactionCount,
                    totalCollected = Money(BigInteger(it.totalCashCollectedMinorUnits), r.currency),
                    commission = Money(BigInteger(it.commissionMinorUnits), r.currency),
                    netEarnings = Money(BigInteger(it.netEarningsMinorUnits), r.currency),
                )
            },
        )
    }

    suspend fun mapsQuotaUsage(): AdminResult<MapsQuotaSnapshotUi> = runCatchingApi {
        val r = adminApi.mapsQuotaUsage()
        MapsQuotaSnapshotUi(
            date = r.date,
            globallyTripped = r.globallyTripped,
            skus = r.skus.map { MapsQuotaSkuUi(it.sku, it.count, it.cap, it.percentUsed, it.capReached) },
        )
    }

    private inline fun <T> runCatchingApi(block: () -> T): AdminResult<T> = try {
        AdminResult.Success(block())
    } catch (e: retrofit2.HttpException) {
        AdminResult.Error(
            when (e.code()) {
                403 -> "You don't have admin access on this account."
                404 -> "We couldn't find that."
                409 -> "That action isn't possible right now."
                else -> FRIENDLY_ERROR
            },
        )
    } catch (e: Exception) {
        AdminResult.Error(FRIENDLY_ERROR)
    }
}
