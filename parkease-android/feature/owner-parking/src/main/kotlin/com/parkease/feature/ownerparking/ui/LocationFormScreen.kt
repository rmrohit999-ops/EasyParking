@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.ownerparking.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.core.location.LocationPermissionState

/**
 * Manual pin-drop, plus a "Use my current location" GPS shortcut backed by
 * core-location's DriverLocationClient (the same one-shot fix feature:
 * driver-search uses) and AddressGeocoder (platform Geocoder, no Maps API
 * key needed) — GPS fills the coordinates and reverse-geocoding fills the
 * address/city/state/postal code fields in one tap. All fields stay
 * editable either way, so GPS or geocoding failing/being denied never
 * blocks entering everything by hand. A full embedded map picker with a
 * draggable pin is a separate, bigger piece of work (needs a Google Maps
 * API key) not attempted here.
 */
@Composable
fun LocationFormScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: LocationFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var addressLine by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var entranceNotes by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    LaunchedEffect(uiState.fetchedLatitude, uiState.fetchedLongitude) {
        val lat = uiState.fetchedLatitude
        val lng = uiState.fetchedLongitude
        if (lat != null && lng != null) {
            latitude = lat.toString()
            longitude = lng.toString()
        }
        uiState.fetchedAddressLine?.let { if (it.isNotBlank()) addressLine = it }
        uiState.fetchedCity?.let { if (it.isNotBlank()) city = it }
        uiState.fetchedState?.let { if (it.isNotBlank()) state = it }
        uiState.fetchedPostalCode?.let { if (it.isNotBlank()) postalCode = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Location") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Cancel") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(
                onClick = {
                    if (uiState.permissionState == LocationPermissionState.GRANTED) {
                        viewModel.requestCurrentLocation()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    }
                },
                enabled = !uiState.isFetchingLocation,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isFetchingLocation) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use my current location & fill address")
                }
            }
            if (uiState.permissionState == LocationPermissionState.DENIED) {
                Text(
                    "Location access was denied. You can still enter coordinates manually below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (uiState.geocodeUnavailable) {
                Text(
                    "We got your coordinates, but couldn't look up the street address automatically. Please fill in the address fields below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    label = { Text("Latitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = { Text("Longitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "Or open this address in your maps app, long-press the pin, and copy the coordinates shown.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = addressLine,
                onValueChange = { addressLine = it },
                label = { Text("Address line") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = state, onValueChange = { state = it }, label = { Text("State") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                value = postalCode,
                onValueChange = { postalCode = it },
                label = { Text("Postal code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = entranceNotes,
                onValueChange = { entranceNotes = it },
                label = { Text("Entrance notes (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            (validationError ?: uiState.errorMessage)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    val lat = latitude.toDoubleOrNull()
                    val lng = longitude.toDoubleOrNull()
                    validationError = when {
                        lat == null || lat < -90.0 || lat > 90.0 -> "Enter a valid latitude between -90 and 90."
                        lng == null || lng < -180.0 || lng > 180.0 -> "Enter a valid longitude between -180 and 180."
                        addressLine.isBlank() || city.isBlank() || state.isBlank() || postalCode.isBlank() ->
                            "Please fill in address, city, state, and postal code."
                        else -> null
                    }
                    if (validationError == null && lat != null && lng != null) {
                        viewModel.save(lat, lng, addressLine.trim(), city.trim(), state.trim(), postalCode.trim(), entranceNotes.trim())
                    }
                },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save location")
                }
            }
        }
    }
}
