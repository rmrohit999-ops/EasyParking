package com.parkease.feature.vehicles.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.core.model.VehicleCategory
import com.parkease.feature.vehicles.data.VehicleUi

@Composable
fun MyVehiclesScreen(
    onAddVehicle: () -> Unit,
    viewModel: VehiclesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddVehicle) {
                Icon(Icons.Default.Add, contentDescription = "Add vehicle")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.vehicles.isEmpty() -> EmptyVehiclesState(modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.vehicles, key = { it.id }) { vehicle ->
                        VehicleCard(
                            vehicle = vehicle,
                            onSetDefault = { viewModel.setDefault(vehicle.id) },
                            onRemove = { viewModel.remove(vehicle.id) },
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

@Composable
private fun EmptyVehiclesState(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No vehicles yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add a two-wheeler or four-wheeler to start booking parking.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun VehicleCard(vehicle: VehicleUi, onSetDefault: () -> Unit, onRemove: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Category label is always shown as text, never color-only,
                // per Milestone 0's vehicle-category accessibility requirement.
                AssistChip(
                    onClick = {},
                    label = { Text(categoryLabel(vehicle.category)) },
                )
                Spacer(Modifier.height(6.dp))
                Text(vehicle.displayName, style = MaterialTheme.typography.titleMedium)
                Text(vehicle.registrationNumber, style = MaterialTheme.typography.bodySmall)
                if (vehicle.isDefault) {
                    Spacer(Modifier.height(4.dp))
                    Text("Default vehicle", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (!vehicle.isDefault) {
                    TextButton(onClick = onSetDefault) { Text("Set default") }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove vehicle")
                }
            }
        }
    }
}

private fun categoryLabel(category: VehicleCategory?): String = when (category) {
    VehicleCategory.TWO_WHEELER -> "Two-Wheeler"
    VehicleCategory.FOUR_WHEELER -> "Four-Wheeler"
    VehicleCategory.OTHER_SUPPORTED -> "Other"
    VehicleCategory.UNSUPPORTED_PENDING_REVIEW -> "Pending review"
    null -> "Unknown"
}
