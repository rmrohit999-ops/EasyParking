@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.attendant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.feature.attendant.data.ScanResultUi

/**
 * The attendant/owner operational screen for Milestone 8: verify a
 * driver's QR pass, then check the vehicle in or out, or report a
 * mismatch. Manual token entry rather than live camera scanning — see
 * AttendantRepository.scan's doc comment for the disclosed scope reason.
 */
@Composable
fun AttendantOpsScreen(
    onBack: () -> Unit,
    viewModel: AttendantOpsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendant") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Type or paste the driver's entry pass code, then verify it before checking a vehicle in or out.",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = uiState.tokenInput,
                onValueChange = viewModel::onTokenChanged,
                label = { Text("Entry pass code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::scan,
                enabled = !uiState.isScanning && uiState.tokenInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Verify Pass")
                }
            }

            uiState.scanResult?.let { scan -> ScanResultCard(scan) }

            if (uiState.scanResult != null) {
                OutlinedTextField(
                    value = uiState.presentedRegistrationInput,
                    onValueChange = viewModel::onPresentedRegistrationChanged,
                    label = { Text("Vehicle registration on the plate (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = viewModel::checkIn, enabled = !uiState.actionInProgress, modifier = Modifier.weight(1f)) {
                        Text("Check In")
                    }
                    Button(onClick = viewModel::checkOut, enabled = !uiState.actionInProgress, modifier = Modifier.weight(1f)) {
                        Text("Check Out")
                    }
                }

                OutlinedTextField(
                    value = uiState.mismatchRegistrationInput,
                    onValueChange = viewModel::onMismatchRegistrationChanged,
                    label = { Text("Actual registration seen (to report a mismatch)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = viewModel::reportMismatch,
                    enabled = !uiState.actionInProgress && uiState.mismatchRegistrationInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Report Mismatch")
                }

                TextButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
                    Text("Start Over")
                }
            }

            uiState.message?.let {
                Text(
                    it,
                    color = if (uiState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ScanResultCard(scan: ScanResultUi) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Outcome: ${scan.outcome}", style = MaterialTheme.typography.titleSmall)
            Text("Booking status: ${scan.bookingStatus?.name ?: "UNKNOWN"}", style = MaterialTheme.typography.bodySmall)
            Text("Section: ${scan.sectionName}", style = MaterialTheme.typography.bodySmall)
            Text("Vehicle: ${scan.vehicleRegistration} (${scan.vehicleCategory})", style = MaterialTheme.typography.bodySmall)
        }
    }
}
