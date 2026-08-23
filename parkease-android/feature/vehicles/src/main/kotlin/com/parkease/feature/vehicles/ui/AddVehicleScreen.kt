@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.vehicles.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.core.model.VehicleCategory
import com.parkease.core.model.VehicleType

/**
 * Client-side grouping of VehicleType by the category a driver is most
 * likely to pick it under. This is a UX convenience only — it narrows the
 * dropdown so a driver who picked TWO_WHEELER isn't offered "SUV" — and is
 * never treated as authoritative. The backend's CreateVehicleDto accepts
 * any VehicleType with any driver-selectable category (Milestone 3 backend),
 * and OTHER_SUPPORTED categories can reasonably contain any type.
 */
private fun vehicleTypesFor(category: VehicleCategory): List<VehicleType> = when (category) {
    VehicleCategory.TWO_WHEELER -> listOf(VehicleType.BIKE, VehicleType.SCOOTER, VehicleType.EV, VehicleType.OTHER)
    VehicleCategory.FOUR_WHEELER -> listOf(VehicleType.CAR, VehicleType.SUV, VehicleType.EV, VehicleType.OTHER)
    else -> VehicleType.entries
}

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

// Driver-selectable categories only — UNSUPPORTED_PENDING_REVIEW is a
// backend-assigned state (Milestone 10 admin review), never client-set,
// matching the backend's DRIVER_SELECTABLE_CATEGORIES restriction.
private val DRIVER_SELECTABLE_CATEGORIES = listOf(
    VehicleCategory.TWO_WHEELER,
    VehicleCategory.FOUR_WHEELER,
    VehicleCategory.OTHER_SUPPORTED,
)

@Composable
fun AddVehicleScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: VehiclesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var category by remember { mutableStateOf(VehicleCategory.TWO_WHEELER) }
    var vehicleType by remember { mutableStateOf(VehicleType.BIKE) }
    var registrationNumber by remember { mutableStateOf("") }
    var make by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var setAsDefault by remember { mutableStateOf(true) }

    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    // Whenever the category changes, snap vehicleType to a valid option for
    // it so the dropdown never silently submits a mismatched pairing.
    LaunchedEffect(category) {
        val validTypes = vehicleTypesFor(category)
        if (vehicleType !in validTypes) {
            vehicleType = validTypes.first()
        }
    }

    LaunchedEffect(uiState.justAdded) {
        if (uiState.justAdded) {
            viewModel.consumeJustAdded()
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add vehicle") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Cancel") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Vehicle category",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "This decides which parking sections your vehicle can book — " +
                    "choose carefully, it can't be changed later without admin review.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ExposedDropdownMenuBox(
                expanded = categoryMenuExpanded,
                onExpandedChange = { categoryMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = categoryLabel(category),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false },
                ) {
                    DRIVER_SELECTABLE_CATEGORIES.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(categoryLabel(option)) },
                            onClick = {
                                category = option
                                categoryMenuExpanded = false
                            },
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = typeMenuExpanded,
                onExpandedChange = { typeMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = vehicleTypeLabel(vehicleType),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Vehicle type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false },
                ) {
                    vehicleTypesFor(category).forEach { option ->
                        DropdownMenuItem(
                            text = { Text(vehicleTypeLabel(option)) },
                            onClick = {
                                vehicleType = option
                                typeMenuExpanded = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = registrationNumber,
                onValueChange = { registrationNumber = it },
                label = { Text("Registration number") },
                placeholder = { Text("e.g. KA01AB1234") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = make,
                onValueChange = { make = it },
                label = { Text("Make (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(checked = setAsDefault, onCheckedChange = { setAsDefault = it })
                Spacer(Modifier.width(4.dp))
                Text("Set as default vehicle")
            }

            uiState.errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start,
                )
            }

            Button(
                onClick = {
                    viewModel.addVehicle(
                        category = category,
                        vehicleType = vehicleType,
                        registrationNumber = registrationNumber.trim(),
                        make = make.trim().ifBlank { null },
                        model = model.trim().ifBlank { null },
                        setAsDefault = setAsDefault,
                    )
                },
                enabled = !uiState.isSaving && registrationNumber.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save vehicle")
                }
            }
        }
    }
}
