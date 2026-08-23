@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.driversearch.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.core.location.LocationPermissionState
import com.parkease.feature.driversearch.data.ListingResultUi
import com.parkease.feature.driversearch.data.SearchFilters
import com.parkease.feature.driversearch.data.SectionResultUi
import kotlin.math.roundToInt

@Composable
fun SearchScreen(
    onOpenFavorites: () -> Unit,
    onBookSection: (sectionId: String, isInstant: Boolean) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) { viewModel.checkPermissionAlreadyGranted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Parking") },
                actions = { TextButton(onClick = onOpenFavorites) { Text("Favorites") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (uiState.permissionState) {
                LocationPermissionState.GRANTED -> Unit
                LocationPermissionState.NOT_REQUESTED, LocationPermissionState.DENIED -> {
                    PermissionRationale(
                        onRequestPermission = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            )
                        },
                    )
                    return@Column
                }
                LocationPermissionState.DENIED_PERMANENTLY -> {
                    Text(
                        "Location access is off. Enable it in system settings to see nearby parking.",
                        modifier = Modifier.padding(24.dp),
                    )
                    return@Column
                }
            }

            FilterRow(filters = uiState.filters, onFiltersChanged = viewModel::setFilters)

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    !uiState.hasVehicle -> Text(
                        "Add a vehicle first so we can show parking that fits it.",
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                    uiState.results.isEmpty() -> Text(
                        "No compatible parking found nearby. Try widening your filters.",
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.results, key = { it.id }) { listing ->
                            ListingResultCard(
                                listing = listing,
                                onToggleFavorite = { viewModel.toggleFavorite(listing.id) },
                                onBookSection = onBookSection,
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
    }
}

@Composable
private fun PermissionRationale(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("ParkEase needs your location to find nearby parking and give directions.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequestPermission) { Text("Enable Location") }
    }
}

@Composable
private fun FilterRow(filters: SearchFilters, onFiltersChanged: (SearchFilters) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filters.instantOnly,
            onClick = { onFiltersChanged(filters.copy(instantOnly = !filters.instantOnly)) },
            label = { Text("Instant") },
        )
        FilterChip(
            selected = filters.covered,
            onClick = { onFiltersChanged(filters.copy(covered = !filters.covered)) },
            label = { Text("Covered") },
        )
        FilterChip(
            selected = filters.hasSecurity,
            onClick = { onFiltersChanged(filters.copy(hasSecurity = !filters.hasSecurity)) },
            label = { Text("Security") },
        )
        FilterChip(
            selected = filters.hasEvCharging,
            onClick = { onFiltersChanged(filters.copy(hasEvCharging = !filters.hasEvCharging)) },
            label = { Text("EV") },
        )
    }
}

@Composable
private fun ListingResultCard(
    listing: ListingResultUi,
    onToggleFavorite: () -> Unit,
    onBookSection: (sectionId: String, isInstant: Boolean) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(listing.name, style = MaterialTheme.typography.titleMedium)
                    Text("${listing.addressLine}, ${listing.city}", style = MaterialTheme.typography.bodySmall)
                    Text(formatDistance(listing.distanceMeters), style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (listing.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (listing.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (listing.isFavorite) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            listing.sections.forEach { section ->
                SectionRow(section, onBook = { onBookSection(section.id, section.instantModeEnabled) })
            }
        }
    }
}

@Composable
private fun SectionRow(section: SectionResultUi, onBook: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(section.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${section.hourlyRate.toDisplayString()}/hr · ${section.availableCount} available",
                style = MaterialTheme.typography.bodySmall,
                color = if (section.availableCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        TextButton(onClick = onBook, enabled = section.availableCount > 0) {
            Text(if (section.instantModeEnabled) "Book Now" else "Reserve")
        }
    }
}

private fun formatDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m away" else "${"%.1f".format(meters / 1000)} km away"
