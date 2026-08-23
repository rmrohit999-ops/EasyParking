package com.parkease.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.parkease.app.navigation.RootNavHost
import com.parkease.core.network.payments.RazorpayCheckoutResult
import com.parkease.core.network.payments.RazorpayResultBus
import com.parkease.core.ui.ParkEaseTheme
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity host. RootNavHost (navigation/RootNavHost.kt) owns the
 * real graph: the auth graph (feature:auth, Milestone 2) when signed out,
 * a role-appropriate home once authenticated.
 *
 * Disclosed Milestone 11 scope limit: ParkEaseMessagingService attaches a
 * `deep_link` extra (the notification's server-supplied deep link, e.g.
 * "parkease://support/tickets/{id}") to the intent that opens this
 * Activity when a user taps a push notification, but nothing here reads
 * that extra yet — there is no deep-link-to-NavController-route resolver
 * built for it, so tapping a push notification opens the app to its normal
 * start screen rather than the specific booking/ticket/dispute it's about.
 * The in-app notification inbox (feature:notifications) has the same gap
 * on tap. Wiring a real resolver (parsing the URI, mapping each path shape
 * to the owning feature module's NavController route) is real, scoped
 * work of its own, not something to half-build here.
 *
 * Also implements Razorpay's PaymentResultWithDataListener — its classic
 * Android Checkout SDK requires the launching Activity itself (not a
 * Fragment or a plain callback) to implement this, so results are forwarded
 * into RazorpayResultBus for feature:booking's BookingDetailViewModel to
 * pick up (see that module for why: it can't implement the interface
 * itself, since it never holds an Activity reference).
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
