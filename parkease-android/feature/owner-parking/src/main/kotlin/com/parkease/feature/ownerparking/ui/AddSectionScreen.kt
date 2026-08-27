@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.ownerparking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.core.model.VehicleCategory
import com.parkease.core.model.VehicleType

// Owner-selectable categories only — UNSUPPORTED_PENDING_REVIEW never
// applies to a section (it's a vehicle-level admin-review state), matching
// OWNER_SELECTABLE_SECTION_CATEGORIES on the backend.
private val OWNER_SELECTABLE_CATEGORIES = listOf(VehicleCategory.TWO_WHEELER, VehicleCategory.FOUR_WHEELER, VehicleCategory.OTHER_SUPPORTED)

private fun categoryLabel(category: VehicleCategory): String = when (category) {
    VehicleCategory.TWO_WHEELER -> "Two-Wheeler"
    VehicleCategory.FOUR_WHEELER -> "Four-Wheeler"
    VehicleCategory.OTHER_SUPPORTED -> "Other"
    VehicleCategory.UNSUPPORTED_PENDING_REVIEW -> "Pending review"
}

private fun vehicleTypeLabel(type: VehicleType): String = when (type) {
    VehicleType.BIKE -> "Bike"
    VehicleType.SCOOTER -> "Scooter"
    VehicleType.CAR -> "Car"
    VehicleType.SUV -> "SUV"
    VehicleType.EV -> "Electric"
    VehicleType.OTHER -> "Other"
}

@Composable
fun AddSectionScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddSectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(VehicleCategory.TWO_WHEELER) }
    var selectedTypes by remember { mutableStateOf(setOf(VehicleType.BIKE, VehicleType.SCOOTER)) }
    var capacityText by remember { mutableStateOf("") }
    var hourlyRateText by remember { mutableStateOf("") }
    var isCovered by remember { mutableStateOf(false) }
    var hasSecurity by remember { mutableStateOf(false) }
    var hasCctv by remember { mutableStateOf(false) }
    var hasEvCharging by remember { mutableStateOf(false) }
    var instantModeEnabled by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    // Prefill from the real existing section once it loads (edit mode only
    // — uiState.existing stays null for a plain "add" screen).
    LaunchedEffect(uiState.existing) {
        uiState.existing?.let { section ->
            name = section.name
            category = section.vehicleCategory ?: category
            selectedTypes = section.supportedVehicleTypes.toSet()
            capacityText = section.capacity.toString()
            hourlyRateText = (section.hourlyRate.minorUnits.toDouble() / 100).let {
                if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
            }
            isCovered = section.isCovered
            hasSecurity = section.hasSecurity
            hasCctv = section.hasCctv
            hasEvCharging = section.hasEvCharging
            instantModeEnabled = section.instantModeEnabled
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Edit section" else "Add section") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Cancel") } },
            )
        },
    ) { padding ->
        if (uiState.isLoadingExisting) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Section name") },
                placeholder = { Text("e.g. Basement Level 1") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (viewModel.isEditing) {
                // Backend rule (UpdateSectionDto's doc comment): category is
                // close-and-recreate only, never an in-place edit — capacity/
                // booking history are keyed off it.
                OutlinedTextField(
                    value = categoryLabel(category),
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Vehicle category (can't be changed)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                    OutlinedTextField(
                        value = categoryLabel(category),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Vehicle category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        OWNER_SELECTABLE_CATEGORIES.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(categoryLabel(option)) },
                                onClick = { category = option; categoryMenuExpanded = false },
                            )
                        }
                    }
                }
            }

            Text("Supported vehicle types", style = MaterialTheme.typography.labelLarge)
            FlowRowOfChips(
                selected = selectedTypes,
                onToggle = { type ->
                    selectedTypes = if (type in selectedTypes) selectedTypes - type else selectedTypes + type
                },
            )

            OutlinedTextField(
                value = capacityText,
                onValueChange = { capacityText = it },
                label = { Text("Capacity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = hourlyRateText,
                onValueChange = { hourlyRateText = it },
                label = { Text("Hourly rate (₹)") },
                placeholder = { Text("e.g. 30") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            AmenitySwitch("Covered", isCovered) { isCovered = it }
            AmenitySwitch("Security guard", hasSecurity) { hasSecurity = it }
            AmenitySwitch("CCTV", hasCctv) { hasCctv = it }
            AmenitySwitch("EV charging", hasEvCharging) { hasEvCharging = it }
            AmenitySwitch("Instant Mode (auto-confirm bookings)", instantModeEnabled) { instantModeEnabled = it }

            (validationError ?: uiState.errorMessage)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    val capacity = capacityText.toIntOrNull()
                    val rateRupees = hourlyRateText.toDoubleOrNull()
                    val rateMinorUnits = rateRupees?.let { Math.round(it * 100) }?.toInt()
                    validationError = when {
                        name.isBlank() -> "Enter a section name."
                        selectedTypes.isEmpty() -> "Select at least one supported vehicle type."
                        capacity == null || capacity < 1 -> "Enter a valid capacity of at least 1."
                        rateMinorUnits == null || rateMinorUnits < 100 -> "Enter a valid hourly rate of at least ₹1."
                        else -> null
                    }
                    if (validationError == null && capacity != null && rateMinorUnits != null) {
                        if (viewModel.isEditing) {
                            viewModel.saveEdits(
                                name.trim(), selectedTypes.toList(), capacity, rateMinorUnits,
                                isCovered, hasSecurity, hasCctv, hasEvCharging, instantModeEnabled,
                            )
                        } else {
                            viewModel.createSection(
                                name.trim(), category, selectedTypes.toList(), capacity, rateMinorUnits,
                                isCovered, hasSecurity, hasCctv, hasEvCharging, instantModeEnabled,
                            )
                        }
                    }
                },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (viewModel.isEditing) "Save changes" else "Save section")
                }
            }
        }
    }
}

@Composable
private fun FlowRowOfChips(selected: Set<VehicleType>, onToggle: (VehicleType) -> Unit) {
    // Wraps manually with Row+wrap-like grouping via a simple two-column
    // flow substitute (androidx.compose.foundation.layout.FlowRow requires
    // a newer Compose foundation than pinned here) — three chips per row.
    VehicleType.entries.chunked(3).forEach { rowTypes ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            rowTypes.forEach { type ->
                FilterChip(
                    selected = type in selected,
                    onClick = { onToggle(type) },
                    label = { Text(vehicleTypeLabel(type)) },
                )
            }
        }
    }
}

@Composable
private fun AmenitySwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
