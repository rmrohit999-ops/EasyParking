@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.partner.navigation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.core.location.LocationPermissionState

/**
 * Owner-facing home — this app's default landing for any account that
 * holds (or has just self-service-onboarded into) OWNER. "My Parking
 * Listings" is where adding a new space, pinning its location on the map,
 * and managing sections/photos all live (feature:owner-parking).
 */
@Composable
fun OwnerHomeScreen(
    becomeOwnerError: String?,
    onMyListings: () -> Unit,
    onBookings: () -> Unit,
    onEarnings: () -> Unit,
    onAttendantTools: () -> Unit,
    onAdmin: () -> Unit,
    onSignOut: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onDeleteAccount: () -> Unit,
    locationViewModel: OwnerHomeViewModel = hiltViewModel(),
) {
    val locationState by locationViewModel.locationState.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationViewModel.onPermissionResult(granted)
    }
    LaunchedEffect(Unit) { locationViewModel.checkPermissionAlreadyGranted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Owner Dashboard") },
                actions = { TextButton(onClick = onSignOut) { Text("Sign out") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            becomeOwnerError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            OwnerLocationCard(
                state = locationState,
                onEnableLocation = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
            )

            OwnerActionCard(
                icon = Icons.Default.LocalParking,
                title = "My Parking Listings",
                subtitle = "Add a space, pin its location, manage sections & photos",
                onClick = onMyListings,
            )
            OwnerActionCard(
                icon = Icons.Default.EventNote,
                title = "Bookings",
                subtitle = "See bookings and confirm cash payments received",
                onClick = onBookings,
            )
            OwnerActionCard(
                icon = Icons.Default.AccountBalanceWallet,
                title = "Earnings & Payouts",
                subtitle = "See what you've earned and request a payout",
                onClick = onEarnings,
            )
            OwnerActionCard(
                icon = Icons.Default.Badge,
                title = "Attendant Tools",
                subtitle = "Scan entry passes and check vehicles in/out",
                onClick = onAttendantTools,
            )

            Spacer(Modifier.weight(1f))
            AccountFooter(onAdmin = onAdmin, onPrivacyPolicy = onPrivacyPolicy, onDeleteAccount = onDeleteAccount)
        }
    }
}

/**
 * "My Current Location" — deliberately just the owner's own live GPS
 * position, never a specific listing's location (see this file's own doc
 * comment, and OwnerHomeViewModel's). Never left blank: shows a reverse-
 * geocoded address when available, raw coordinates when geocoding fails,
 * and an honest "Enable Location" prompt when permission hasn't been
 * granted — never a fabricated location.
 */
@Composable
private fun OwnerLocationCard(state: OwnerLocationUiState, onEnableLocation: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text("My Current Location", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val label = state.addressLabel
                    ?: state.latitude?.let { lat -> state.longitude?.let { lng -> "%.4f, %.4f".format(lat, lng) } }
                when {
                    state.permissionState == LocationPermissionState.DENIED -> Text("Location access is off.", style = MaterialTheme.typography.bodyMedium)
                    state.isLoading && label == null -> Text("Detecting location…", style = MaterialTheme.typography.bodyMedium)
                    label != null -> Text(label, style = MaterialTheme.typography.bodyMedium)
                    else -> Text("Location unavailable", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.permissionState != LocationPermissionState.GRANTED) {
                TextButton(onClick = onEnableLocation) { Text("Enable") }
            }
        }
    }
}

@Composable
private fun OwnerActionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
