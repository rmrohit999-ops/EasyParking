package com.parkease.core.network.payments

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class RazorpayCheckoutResult {
    data class Success(val razorpayPaymentId: String, val razorpayOrderId: String?, val razorpaySignature: String?) :
        RazorpayCheckoutResult()

    data class Failure(val code: Int, val description: String?) : RazorpayCheckoutResult()
}

/**
 * Razorpay's classic Android SDK requires the launching Activity itself to
 * implement PaymentResultWithDataListener — MainActivity (app module) is
 * that Activity, since this is a single-Activity Compose app. This bus is
 * how the result crosses back from MainActivity into whichever
 * feature:booking ViewModel actually started the checkout, without
 * core-network depending on the Razorpay SDK or on any Android UI type.
 */
@Singleton
class RazorpayResultBus @Inject constructor() {
    private val _results = MutableSharedFlow<RazorpayCheckoutResult>(extraBufferCapacity = 1)
    val results: SharedFlow<RazorpayCheckoutResult> = _results.asSharedFlow()

    fun emit(result: RazorpayCheckoutResult) {
        _results.tryEmit(result)
    }
}
