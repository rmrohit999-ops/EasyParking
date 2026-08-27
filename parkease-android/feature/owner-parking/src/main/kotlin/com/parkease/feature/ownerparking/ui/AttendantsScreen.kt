@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.ownerparking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import com.parkease.feature.ownerparking.data.AttendantAssignmentUi

@Composable
fun AttendantsScreen(
    onBack: () -> Unit,
    viewModel: AttendantsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendants") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "An attendant needs an existing ParkEase account (they sign up in the app first). Give their email and which vehicle categories they can check in/out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = uiState.emailInput,
                onValueChange = viewModel::setEmailInput,
                label = { Text("Attendant's email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(VehicleCategory.TWO_WHEELER, VehicleCategory.FOUR_WHEELER, VehicleCategory.OTHER_SUPPORTED).forEach { category ->
                    FilterChip(
                        selected = category in uiState.selectedCategories,
                        onClick = { viewModel.toggleCategory(category) },
                        label = { Text(categoryChipLabel(category)) },
                    )
                }
            }

            Button(
                onClick = viewModel::assign,
                enabled = !uiState.isAssigning && uiState.emailInput.isNotBlank() && uiState.selectedCategories.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isAssigning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Add Attendant")
                }
            }

            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider()

            Text("Assigned (${uiState.attendants.size})", style = MaterialTheme.typography.titleSmall)
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    uiState.attendants.isEmpty() -> Text(
                        "No attendants assigned yet.",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.attendants, key = { it.id }) { attendant ->
                            AttendantRow(attendant, onRevoke = { viewModel.revoke(attendant.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendantRow(attendant: AttendantAssignmentUi, onRevoke: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(attendant.attendantEmail ?: attendant.attendantPhone ?: "Unknown", style = MaterialTheme.typography.bodyLarge)
                Text(
                    attendant.authorizedCategories.joinToString(", ") { categoryChipLabel(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRevoke) {
                Icon(Icons.Default.Delete, contentDescription = "Revoke access")
            }
        }
    }
}

private fun categoryChipLabel(category: VehicleCategory): String = when (category) {
    VehicleCategory.TWO_WHEELER -> "2-Wheeler"
    VehicleCategory.FOUR_WHEELER -> "4-Wheeler"
    VehicleCategory.OTHER_SUPPORTED -> "Other"
    VehicleCategory.UNSUPPORTED_PENDING_REVIEW -> "Pending review"
}
