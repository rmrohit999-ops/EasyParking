@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.booking.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

private val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM, hh:mm a")

@Composable
fun BookingConfirmScreen(
    onBookingConfirmed: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: BookingConfirmViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var startDateTime by remember { mutableStateOf(LocalDateTime.now().plusMinutes(15)) }
    var endDateTime by remember { mutableStateOf(LocalDateTime.now().plusHours(2)) }

    LaunchedEffect(uiState.confirmedBooking) {
        uiState.confirmedBooking?.let { onBookingConfirmed(it.id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isInstant) "Instant Parking" else "Book Parking") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Cancel") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!uiState.hasVehicle) {
                Text(
                    "Add a vehicle first so we can book parking that fits it.",
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }

            if (viewModel.isInstant) {
                Text(
                    "This section supports Instant Parking — your booking starts now, and the final amount is based on how long you actually park.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = viewModel::bookInstant,
                    enabled = !uiState.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Book Now")
                    }
                }
            } else {
                Text("Choose when you'll arrive and leave.", style = MaterialTheme.typography.bodyMedium)

                DateTimePickerRow(
                    label = "Start",
                    value = startDateTime,
                    onPick = { picked -> startDateTime = picked },
                    context = context,
                )
                DateTimePickerRow(
                    label = "End",
                    value = endDateTime,
                    onPick = { picked -> endDateTime = picked },
                    context = context,
                )

                Button(
                    onClick = {
                        val zone = ZoneId.systemDefault()
                        val start = startDateTime.atZone(zone).toInstant()
                        val end = endDateTime.atZone(zone).toInstant()
                        viewModel.bookAdvance(start, end)
                    },
                    enabled = !uiState.isSubmitting && endDateTime.isAfter(startDateTime),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Reserve")
                    }
                }
                if (!endDateTime.isAfter(startDateTime)) {
                    Text("End time must be after start time.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Text(
                "Payment isn't collected in this build yet — bookings are held as Pending Payment until checkout is available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DateTimePickerRow(
    label: String,
    value: LocalDateTime,
    onPick: (LocalDateTime) -> Unit,
    context: android.content.Context,
) {
    OutlinedButton(
        onClick = {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            onPick(LocalDateTime.of(year, month + 1, day, hour, minute))
                        },
                        value.hour,
                        value.minute,
                        false,
                    ).show()
                },
                value.year,
                value.monthValue - 1,
                value.dayOfMonth,
            ).show()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("$label: ${value.format(DISPLAY_FORMAT)}")
    }
}

