@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.booking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.core.model.BookingStatus
import com.parkease.feature.booking.data.BookingUi
import com.parkease.feature.booking.data.PassUi
import com.parkease.feature.booking.data.RefundUi
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BookingDetailScreen(
    onBack: () -> Unit,
    viewModel: BookingDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
    pass: PassUi?,
    passLoading: Boolean,
    passMessage: String?,
    onShowPass: () -> Unit,
    refunds: List<RefundUi>,
) {
    val canCancel = booking.status == BookingStatus.PENDING_PAYMENT || booking.status == BookingStatus.CONFIRMED
    val canPay = booking.status == BookingStatus.PENDING_PAYMENT
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

        if (canPay) {
            Button(onClick = onPayNow, enabled = !paymentInProgress, modifier = Modifier.fillMaxWidth()) {
                if (paymentInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Pay Now")
                }
            }
            paymentMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
