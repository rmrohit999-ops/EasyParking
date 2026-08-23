@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.ownerparking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Manual pin-drop for Milestone 4: latitude/longitude + address fields,
 * validated client-side to the same [-90,90]/[-180,180] ranges the backend
 * enforces (UpsertLocationDto). A "use my current location" GPS shortcut
 * and a real map picker are deliberately NOT here — core-location's
 * FusedLocationProviderClient wrapper is scoped to Milestone 5 (driver
 * discovery), the first feature that actually needs a live location
 * permission flow (Milestone 0 §15's "don't request permissions ahead of
 * real use"). This screen will gain that shortcut once that wrapper exists.
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

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
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
                "Tip: open this address in your maps app, long-press the pin, and copy the coordinates shown.",
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
