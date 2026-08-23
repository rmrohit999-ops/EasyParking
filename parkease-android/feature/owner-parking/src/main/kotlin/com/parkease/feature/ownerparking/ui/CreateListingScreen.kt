@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.ownerparking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.core.model.ParkingType

private fun parkingTypeLabel(type: ParkingType): String = when (type) {
    ParkingType.INDIVIDUAL -> "Individual"
    ParkingType.RESIDENTIAL -> "Residential"
    ParkingType.APARTMENT -> "Apartment complex"
    ParkingType.COMMERCIAL -> "Commercial"
    ParkingType.OFFICE -> "Office"
    ParkingType.MALL -> "Mall"
    ParkingType.OTHER -> "Other"
}

@Composable
fun CreateListingScreen(
    onCreated: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CreateListingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var parkingType by remember { mutableStateOf(ParkingType.INDIVIDUAL) }
    var description by remember { mutableStateOf("") }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.createdListingId) {
        uiState.createdListingId?.let(onCreated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New parking listing") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Cancel") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Listing name") },
                placeholder = { Text("e.g. Green Park Residency") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(
                expanded = typeMenuExpanded,
                onExpandedChange = { typeMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = parkingTypeLabel(parkingType),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Premises type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                    ParkingType.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(parkingTypeLabel(option)) },
                            onClick = { parkingType = option; typeMenuExpanded = false },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { viewModel.createListing(name.trim(), parkingType, description.trim()) },
                enabled = !uiState.isSaving && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create listing")
                }
            }

            Text(
                "You'll add a location, at least one section, and a photo before submitting this listing for review.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
