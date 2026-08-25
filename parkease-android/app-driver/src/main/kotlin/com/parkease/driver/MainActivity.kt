package com.parkease.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.parkease.driver.navigation.RootNavHost
import com.parkease.core.network.payments.RazorpayCheckoutResult
import com.parkease.core.network.payments.RazorpayResultBus
import com.parkease.core.ui.ParkEaseTheme
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity host for the Driver app. RootNavHost owns the real
 * graph: auth when signed out, the driver home once authenticated.
 *
 * Implements Razorpay's PaymentResultWithDataListener — its classic
 * Android Checkout SDK requires the launching Activity itself (not a
 * Fragment or a plain callback) to implement this. Results are forwarded
 * into RazorpayResultBus for feature:booking's BookingDetailViewModel.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    @Inject lateinit var razorpayResultBus: RazorpayResultBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParkEaseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Scaffold { innerPadding ->
                        RootNavHost(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        razorpayResultBus.emit(
            RazorpayCheckoutResult.Success(
                razorpayPaymentId = razorpayPaymentId ?: paymentData?.paymentId.orEmpty(),
                razorpayOrderId = paymentData?.orderId,
                razorpaySignature = paymentData?.signature,
            ),
        )
    }

    override fun onPaymentError(code: Int, description: String?, paymentData: PaymentData?) {
        razorpayResultBus.emit(RazorpayCheckoutResult.Failure(code, description))
    }
}
