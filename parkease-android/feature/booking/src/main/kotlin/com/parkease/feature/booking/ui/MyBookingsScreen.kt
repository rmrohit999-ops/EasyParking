@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.booking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@Composable
fun MyBookingsScreen(
    onOpenBooking: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MyBookingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Client-side split only — BookingStatus.isTerminal is already real
    // (COMPLETED/CANCELLED/etc.), no backend change needed for "history".
    var showActive by remember { mutableStateOf(true) }
    val visibleBookings = uiState.bookings.filter { (it.status?.isTerminal ?: false) != showActive }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Bookings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = showActive, onClick = { showActive = true }, label = { Text("Active") })
                FilterChip(selected = !showActive, onClick = { showActive = false }, label = { Text("History") })
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    visibleBookings.isEmpty() -> Text(
                        if (showActive) "No active bookings right now." else "No past bookings yet.",
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(visibleBookings, key = { it.id }) { booking ->
                            BookingRow(booking = booking, onClick = { onOpenBooking(booking.id) })
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
    }
}

@Composable
private fun BookingRow(booking: BookingUi, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(statusLabel(booking.status), style = MaterialTheme.typography.titleMedium)
            booking.startTime?.let {
                Text(
                    DateTimeFormatter.ofPattern("dd MMM, hh:mm a").format(it.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(booking.bookingType?.name ?: "UNKNOWN", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun statusLabel(status: BookingStatus?): String = when (status) {
    BookingStatus.PENDING_PAYMENT -> "Pending Payment"
    BookingStatus.CONFIRMED -> "Confirmed"
    BookingStatus.DRIVER_ARRIVING -> "Arriving"
    BookingStatus.CHECKED_IN -> "Checked In"
    BookingStatus.PARKING_ACTIVE -> "Parked"
    BookingStatus.CHECKED_OUT -> "Checked Out"
    BookingStatus.COMPLETED -> "Completed"
    BookingStatus.REJECTED -> "Rejected"
    BookingStatus.EXPIRED -> "Expired"
    BookingStatus.CANCELLED -> "Cancelled"
    BookingStatus.NO_SHOW -> "No Show"
    BookingStatus.VEHICLE_MISMATCH -> "Vehicle Mismatch"
    BookingStatus.PARKING_UNAVAILABLE -> "Parking Unavailable"
    BookingStatus.ADMIN_CANCELLED -> "Cancelled by Admin"
    null -> "Unknown"
}
