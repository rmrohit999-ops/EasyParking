@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.booking.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.core.model.BookingStatus
import com.parkease.feature.booking.data.BookingUi
import com.parkease.feature.booking.data.PassUi
import com.parkease.feature.booking.data.QuoteUi
import com.parkease.feature.booking.data.RefundUi
import com.razorpay.Checkout
import org.json.JSONObject
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BookingDetailScreen(
    onBack: () -> Unit,
    viewModel: BookingDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Fires once per created order (readyToLaunchCheckoutForOrderId is
    // cleared by onCheckoutLaunched right after), opening Razorpay's
    // Checkout activity. Requires `context` to actually be the launching
    // Activity — true here since this is a single-Activity app and
    // MainActivity is the one that implements PaymentResultWithDataListener
    // (see MainActivity's doc comment for why that has to be an Activity,
    // not this Composable, that implements the callback).
    LaunchedEffect(uiState.readyToLaunchCheckoutForOrderId) {
        val order = uiState.paymentOrder ?: return@LaunchedEffect
        if (uiState.readyToLaunchCheckoutForOrderId != order.id) return@LaunchedEffect
        val activity = context as? Activity ?: return@LaunchedEffect
        val checkout = Checkout()
        checkout.setKeyID(viewModel.razorpayKeyId)
        val options = JSONObject().apply {
            put("name", "ParkEase")
            put("description", "Parking booking payment")
            put("order_id", order.gatewayOrderId)
            put("currency", order.currency)
            put("amount", order.amountMinorUnits.toLongOrNull() ?: 0L)
        }
        viewModel.onCheckoutLaunched()
        checkout.open(activity, options)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Booking") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val booking = uiState.booking
            when {
                uiState.isLoading && booking == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                booking != null -> BookingDetailContent(
                    booking = booking,
                    actionInProgress = uiState.actionInProgress,
                    onCancel = viewModel::cancel,
                    paymentInProgress = uiState.paymentInProgress,
                    paymentMessage = uiState.paymentMessage,
                    onPayNow = viewModel::payNow,
                    quote = uiState.quote,
                    cashInProgress = uiState.cashInProgress,
                    onPayWithCash = viewModel::payWithCash,
                    pass = uiState.pass,
                    passLoading = uiState.passLoading,
                    passMessage = uiState.passMessage,
                    onShowPass = viewModel::showPass,
                    refunds = uiState.refunds,
                )
            }

            uiState.errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun BookingDetailContent(
    booking: BookingUi,
    actionInProgress: Boolean,
    onCancel: () -> Unit,
    paymentInProgress: Boolean,
    paymentMessage: String?,
    onPayNow: () -> Unit,
    quote: QuoteUi?,
    cashInProgress: Boolean,
    onPayWithCash: () -> Unit,
    pass: PassUi?,
    passLoading: Boolean,
    passMessage: String?,
    onShowPass: () -> Unit,
    refunds: List<RefundUi>,
) {
    val canCancel = booking.status == BookingStatus.PENDING_PAYMENT || booking.status == BookingStatus.CONFIRMED
    val canPay = booking.status == BookingStatus.PENDING_PAYMENT
    val cashPending = booking.status == BookingStatus.PENDING_PAYMENT && booking.intendedPaymentMethod == "CASH"
    val cashPaid = booking.cashAmount != null
    val canShowPass = booking.status == BookingStatus.CONFIRMED || booking.status == BookingStatus.DRIVER_ARRIVING
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(booking.status?.name ?: "UNKNOWN", style = MaterialTheme.typography.headlineSmall)
        Text("Type: ${booking.bookingType?.name ?: "UNKNOWN"}", style = MaterialTheme.typography.bodyMedium)

        booking.startTime?.let {
            Text("Start: ${formatter.format(it.atZone(ZoneId.systemDefault()))}", style = MaterialTheme.typography.bodyMedium)
        }
        booking.endTime?.let {
            Text("End: ${formatter.format(it.atZone(ZoneId.systemDefault()))}", style = MaterialTheme.typography.bodyMedium)
        }

        booking.priceSnapshot?.let { snapshot ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Price (estimate)", style = MaterialTheme.typography.titleSmall)
                    snapshot.forEach { (key, value) ->
                        if (value != null) {
                            Text("$key: $value", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (canPay && !cashPending) {
            quote?.let {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Parking Fee: ${it.parkingAmount.toDisplayString()}", style = MaterialTheme.typography.bodyMedium)
                        if (it.taxAmount.minorUnits.signum() != 0) {
                            Text("Tax: ${it.taxAmount.toDisplayString()}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            "Total Payable: ${it.totalPayable.toDisplayString()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                    }
                }
            }
            Button(onClick = onPayNow, enabled = !paymentInProgress && !cashInProgress, modifier = Modifier.fillMaxWidth()) {
                if (paymentInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Pay Now")
                }
            }
            OutlinedButton(onClick = onPayWithCash, enabled = !paymentInProgress && !cashInProgress, modifier = Modifier.fillMaxWidth()) {
                if (cashInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Pay with Cash")
                }
            }
            paymentMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (cashPending) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge("Cash Payment Pending", MaterialTheme.colorScheme.tertiary)
                    Text(
                        "Payment Method: Cash",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    quote?.let {
                        Text(
                            "Please pay ${it.totalPayable.toDisplayString()} in cash to the parking owner.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        if (cashPaid) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge("Paid", MaterialTheme.colorScheme.primary)
                    Text("Amount Paid: ${booking.cashAmount!!.toDisplayString()}", style = MaterialTheme.typography.bodyMedium)
                    Text("Payment Method: Cash", style = MaterialTheme.typography.bodyMedium)
                    Text("Booking ID: ${booking.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    booking.cashConfirmedAt?.let {
                        Text(
                            "Paid on ${formatter.format(it.atZone(ZoneId.systemDefault()))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (canShowPass) {
            if (pass == null) {
                OutlinedButton(onClick = onShowPass, enabled = !passLoading, modifier = Modifier.fillMaxWidth()) {
                    if (passLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Show Entry Pass")
                    }
                }
            } else {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Entry Pass", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Show this code to the attendant at check-in and check-out.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        SelectionContainer {
                            Text(
                                pass.token,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                    }
                }
            }
            passMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        if (canCancel) {
            OutlinedButton(onClick = onCancel, enabled = !actionInProgress, modifier = Modifier.fillMaxWidth()) {
                if (actionInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Cancel Booking")
                }
            }
        }

        if (refunds.isNotEmpty()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Refunds", style = MaterialTheme.typography.titleSmall)
                    refunds.forEach { refund ->
                        Column {
                            Text(
                                "${refund.amount.toDisplayString()} — ${refund.status}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                refundExplanation(refund),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun refundExplanation(refund: RefundUi): String {
    val reason = when (refund.reasonCode) {
        "CATEGORY_MISMATCH" -> "vehicle/category mismatch at check-in"
        "PARKING_UNAVAILABLE" -> "parking marked unavailable"
        "ADMIN_APPROVED" -> "approved by support"
        "CANCELLATION_POLICY" -> "cancellation policy"
        else -> "other"
    }
    return when (refund.status) {
        "COMPLETED" -> "Refunded ($reason)."
        "PENDING" -> "Awaiting cash refund confirmation ($reason)."
        "FAILED" -> "Refund attempt failed ($reason) — support will follow up."
        else -> "$reason — ${refund.status.lowercase()}"
    }
}
