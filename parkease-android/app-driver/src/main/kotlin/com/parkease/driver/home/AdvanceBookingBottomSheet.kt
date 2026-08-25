@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.driver.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.core.location.GeocodedPlace
import com.parkease.core.model.Money
import com.parkease.core.model.VehicleCategory
import com.parkease.feature.driversearch.data.ListingResultUi
import com.parkease.feature.driversearch.data.SectionResultUi
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

private val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM, hh:mm a")

/**
 * Advance-booking flow: pick a destination (on-device geocoded — see
 * AddressGeocoder's doc comment on why this isn't Places Autocomplete),
 * pick a date/time/vehicle, search real availability near that
 * destination, then hand the chosen section + resolved times off to the
 * existing BookingConfirmScreen (via onBookAdvance -> the nav graph's
 * prefilled startEpochMillis/endEpochMillis args) rather than duplicating
 * booking-creation logic here.
 */
@Composable
fun AdvanceBookingBottomSheet(
    defaultCategory: VehicleCategory,
    onDismiss: () -> Unit,
    onBookAdvance: (sectionId: String, startEpochMillis: Long, endEpochMillis: Long) -> Unit,
    viewModel: AdvanceBookingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var startDateTime by remember { mutableStateOf(LocalDateTime.now().plusHours(1)) }
    var endDateTime by remember { mutableStateOf(LocalDateTime.now().plusHours(3)) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Text("Book in Advance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.destinationQuery,
                        onValueChange = viewModel::setDestinationQuery,
                        label = { Text("Where are you headed?") },
                        placeholder = { Text("e.g. Phoenix Mall, Whitefield") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = viewModel::searchDestination, enabled = uiState.destinationQuery.isNotBlank()) {
                                Text("Search")
                            }
                        },
                    )
                    if (uiState.isSearchingDestination) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    uiState.destinationMatches.forEach { place ->
                        DestinationMatchRow(place = place, onClick = { viewModel.selectDestination(place) })
                    }
                    uiState.selectedDestination?.let {
                        Text("Selected: ${it.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("When", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateTimePickerButton(
                            label = "Start",
                            value = startDateTime,
                            onPick = { startDateTime = it },
                            context = context,
                            modifier = Modifier.weight(1f),
                        )
                        DateTimePickerButton(
                            label = "End",
                            value = endDateTime,
                            onPick = { endDateTime = it },
                            context = context,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (!endDateTime.isAfter(startDateTime)) {
                        Text("End time must be after start time.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (uiState.vehicles.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Vehicle", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.vehicles.forEach { vehicle ->
                                FilterChip(
                                    selected = vehicle.id == uiState.selectedVehicleId,
                                    onClick = { viewModel.selectVehicle(vehicle.id) },
                                    label = { Text(vehicle.displayName) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { viewModel.findParking(defaultCategory) },
                    enabled = uiState.selectedDestination != null && endDateTime.isAfter(startDateTime) && !uiState.isFindingParking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.isFindingParking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Find Parking")
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }

            if (uiState.hasSearched && !uiState.isFindingParking) {
                if (uiState.results.isEmpty()) {
                    item { Text("No parking found near that destination.", style = MaterialTheme.typography.bodyMedium) }
                } else {
                    item { Text("Available near your destination", style = MaterialTheme.typography.titleSmall) }
                    items(uiState.results, key = { it.id }) { listing ->
                        listing.sections.firstOrNull()?.let { section ->
                            AdvanceResultCard(
                                listing = listing,
                                section = section,
                                startTime = startDateTime.atZone(ZoneId.systemDefault()).toInstant(),
                                endTime = endDateTime.atZone(ZoneId.systemDefault()).toInstant(),
                                estimateMinorUnits = viewModel.estimatedTotalMinorUnits(
                                    section.hourlyRate.minorUnits.toLong(),
                                    startDateTime.atZone(ZoneId.systemDefault()).toInstant(),
                                    endDateTime.atZone(ZoneId.systemDefault()).toInstant(),
                                ),
                                currency = section.hourlyRate.currency,
                                onReserve = {
                                    onBookAdvance(
                                        section.id,
                                        startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                        endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationMatchRow(place: GeocodedPlace, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(place.label, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DateTimePickerButton(
    label: String,
    value: LocalDateTime,
    onPick: (LocalDateTime) -> Unit,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> onPick(LocalDateTime.of(year, month + 1, day, hour, minute)) },
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
        modifier = modifier,
    ) {
        Text("$label\n${value.format(DISPLAY_FORMAT)}", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun AdvanceResultCard(
    listing: ListingResultUi,
    section: SectionResultUi,
    startTime: Instant,
    endTime: Instant,
    estimateMinorUnits: Long,
    currency: String,
    onReserve: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(listing.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(listing.addressLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("${section.hourlyRate.toDisplayString()}/hr", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Est. total: ${Money.of(estimateMinorUnits, currency).toDisplayString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (section.availableCount > 0) "${section.availableCount} left" else "Full",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (section.availableCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Button(onClick = onReserve, enabled = section.availableCount > 0, modifier = Modifier.fillMaxWidth()) {
                Text("Reserve")
            }
        }
    }
}
