package com.parkease.feature.admin.data

import com.parkease.core.network.api.AdminApi
import com.parkease.core.network.model.AdminRejectRequest
import com.parkease.core.network.model.SuspendUserRequest
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
