@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.booking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.core.model.BookingStatus
import com.parkease.feature.booking.data.BookingUi
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM, hh:mm a")

/**
 * Owner-facing bookings list — the same underlying data as
 * MyBookingsScreen (BookingRepository.listBookings() is already
 * role-scoped server-side), but purpose-built around the cash-collection
 * workflow: shows who to expect, what to collect, and a "Payment Received"
 * action for anything still Cash Payment Pending.
 */
@Composable
fun OwnerBookingsScreen(
    onBack: () -> Unit,
    viewModel: OwnerBookingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bookings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading && uiState.bookings.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.bookings.isEmpty() -> Text(
                    "No bookings yet.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.bookings, key = { it.id }) { booking ->
                        OwnerBookingRow(
                            booking = booking,
                            actionInProgress = uiState.actionInProgressBookingId == booking.id,
                            onPaymentReceived = { viewModel.requestConfirmDialog(booking.id) },
                        )
                    }
                }
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

    val dialogBookingId = uiState.confirmDialogBookingId
    if (dialogBookingId != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirmDialog() },
            title = { Text("Confirm cash received") },
            text = {
                if (uiState.confirmDialogLoading || uiState.confirmDialogAmount == null) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    val amount = uiState.confirmDialogAmount!!.totalPayable.toDisplayString()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Amount to Receive: $amount", style = MaterialTheme.typography.titleMedium)
                        Text("Payment Method: Cash", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "I confirm that I have received $amount in cash from the customer.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.confirmDialogLoading && uiState.confirmDialogAmount != null,
                    onClick = { viewModel.confirmPaymentReceived(dialogBookingId) },
                ) { Text("Confirm Payment Received") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConfirmDialog() }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun OwnerBookingRow(
    booking: BookingUi,
    actionInProgress: Boolean,
    onPaymentReceived: () -> Unit,
) {
    val cashPending = booking.status == BookingStatus.PENDING_PAYMENT && booking.intendedPaymentMethod == "CASH"
    val cashPaid = booking.cashAmount != null

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            booking.parkingName?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            booking.driverContact?.let {
                Text("Customer: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            booking.vehicleRegistrationNumber?.let {
                Text("Vehicle: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Booking ID: ${booking.id.take(8)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            booking.startTime?.let { start ->
                val range = booking.endTime?.let { end ->
                    "${DISPLAY_FORMAT.format(start.atZone(ZoneId.systemDefault()))} – ${DISPLAY_FORMAT.format(end.atZone(ZoneId.systemDefault()))}"
                } ?: DISPLAY_FORMAT.format(start.atZone(ZoneId.systemDefault()))
                Text(range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(4.dp))

            when {
                cashPaid -> {
                    StatusBadge("Paid", MaterialTheme.colorScheme.primary)
                    Text(
                        "Payment Method: Cash — ${booking.cashAmount!!.toDisplayString()}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                cashPending -> {
                    StatusBadge("Cash Payment Pending", MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onPaymentReceived, enabled = !actionInProgress, modifier = Modifier.fillMaxWidth()) {
                        if (actionInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Payment Received")
                        }
                    }
                }
                booking.status == BookingStatus.PENDING_PAYMENT -> {
                    StatusBadge("Awaiting Payment", MaterialTheme.colorScheme.outline)
                }
                else -> {
                    Text(booking.status?.name ?: "UNKNOWN", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
