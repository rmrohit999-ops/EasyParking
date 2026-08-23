package com.parkease.feature.earnings.data

import com.parkease.core.model.Money
import com.parkease.core.network.api.EarningsApi
import com.parkease.core.network.model.CreatePayoutAccountRequest
import com.parkease.core.network.model.PayoutAccountResponse
import com.parkease.core.network.model.SettlementResponse
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

data class EarningsBucketUi(val status: String, val amount: Money, val count: Int)

data class PayoutAccountUi(
    val id: String,
    val method: String,
    val accountHolderName: String,
    val accountNumberMasked: String?,
    val ifsc: String?,
    val upiVpaMasked: String?,
    val verificationStatus: String,
    val isPrimary: Boolean,
)

data class SettlementUi(
    val id: String,
    val requestedAmount: Money,
    val status: String,
    val payoutAccountId: String,
    val gatewayPayoutId: String?,
    val requestedAt: String,
    val processedAt: String?,
)

sealed class EarningsResult<out T> {
    data class Success<T>(val value: T) : EarningsResult<T>()
    data class Error(val message: String) : EarningsResult<Nothing>()
}

/** The order a driver/owner naturally thinks in: earned but not yet payable, payable, mid-payout, paid, then the exception states. */
val EARNINGS_STATUS_ORDER = listOf("PENDING", "AVAILABLE", "PROCESSING", "SETTLED", "FAILED", "ADJUSTED", "REVERSED")

private const val FRIENDLY_ERROR = "Something went wrong. Please try again."

@Singleton
class EarningsRepository @Inject constructor(
    private val earningsApi: EarningsApi,
) {
    suspend fun summary(): EarningsResult<List<EarningsBucketUi>> = runCatchingApi {
        val response = earningsApi.earningsSummary()
        response.byStatus
            .map { (status, bucket) -> EarningsBucketUi(status, Money(BigInteger(bucket.amountMinorUnits)), bucket.count) }
            .sortedBy { EARNINGS_STATUS_ORDER.indexOf(it.status).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }

    suspend fun listPayoutAccounts(): EarningsResult<List<PayoutAccountUi>> = runCatchingApi {
        earningsApi.listPayoutAccounts().map { it.toUi() }
    }

    suspend fun addBankAccount(holderName: String, accountNumber: String, ifsc: String): EarningsResult<PayoutAccountUi> = runCatchingApi {
        earningsApi.createPayoutAccount(
            CreatePayoutAccountRequest(method = "BANK", accountHolderName = holderName, accountNumber = accountNumber, ifsc = ifsc),
        ).toUi()
    }

    suspend fun addUpiAccount(holderName: String, vpa: String): EarningsResult<PayoutAccountUi> = runCatchingApi {
        earningsApi.createPayoutAccount(CreatePayoutAccountRequest(method = "UPI", accountHolderName = holderName, upiVpa = vpa)).toUi()
    }

    suspend fun setPrimaryPayoutAccount(accountId: String): EarningsResult<PayoutAccountUi> = runCatchingApi {
        earningsApi.setPrimaryPayoutAccount(accountId).toUi()
    }

    suspend fun removePayoutAccount(accountId: String): EarningsResult<Unit> = runCatchingApi {
        earningsApi.removePayoutAccount(accountId)
    }

    suspend fun requestSettlement(): EarningsResult<SettlementUi> = runCatchingApi {
        earningsApi.requestSettlement().toUi()
    }

    suspend fun listSettlements(): EarningsResult<List<SettlementUi>> = runCatchingApi {
        earningsApi.listSettlements().map { it.toUi() }
    }

    private fun PayoutAccountResponse.toUi() = PayoutAccountUi(
        id = id,
        method = method,
        accountHolderName = accountHolderName,
        accountNumberMasked = accountNumberMasked,
        ifsc = ifsc,
        upiVpaMasked = upiVpaMasked,
        verificationStatus = verificationStatus,
        isPrimary = isPrimary,
    )

    private fun SettlementResponse.toUi() = SettlementUi(
        id = id,
        requestedAmount = Money(BigInteger(requestedAmountMinorUnits)),
        status = status,
        payoutAccountId = payoutAccountId,
        gatewayPayoutId = gatewayPayoutId,
        requestedAt = requestedAt,
        processedAt = processedAt,
    )

    private inline fun <T> runCatchingApi(block: () -> T): EarningsResult<T> = try {
        EarningsResult.Success(block())
    } catch (e: retrofit2.HttpException) {
        EarningsResult.Error(
            when (e.code()) {
                400 -> "That request wasn't valid. Please check the details and try again."
                403 -> "You don't have permission to do that."
                404 -> "We couldn't find that."
                409 -> "That action isn't possible right now — see the details below."
                503 -> "This feature isn't available yet — it needs a payout provider configured on the server."
                else -> FRIENDLY_ERROR
            },
        )
    } catch (e: Exception) {
        EarningsResult.Error(FRIENDLY_ERROR)
    }
}
