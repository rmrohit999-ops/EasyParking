@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.feature.admin.data.PendingListingUi

@Composable
fun AdminPendingListingsScreen(
    onBack: () -> Unit,
    viewModel: AdminPendingListingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingRejectId by remember { mutableStateOf<String?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pending listings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading && uiState.listings.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.listings.isEmpty() -> Text(
                    "Nothing waiting on review — new listings that meet the requirements are approved automatically.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.listings, key = { it.id }) { listing ->
                        PendingListingRow(
                            listing = listing,
                            actionInProgress = uiState.actionInProgressId == listing.id,
                            onApprove = { viewModel.approve(listing.id) },
                            onReject = { pendingRejectId = listing.id },
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

    if (pendingRejectId != null) {
        AlertDialog(
            onDismissRequest = { pendingRejectId = null; rejectReason = "" },
            title = { Text("Reject this listing?") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = { Text("Reason (shown to the owner)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRejectId?.let { viewModel.reject(it, rejectReason.ifBlank { "Does not meet listing requirements." }) }
                        pendingRejectId = null
                        rejectReason = ""
                    },
                ) { Text("Reject", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRejectId = null; rejectReason = "" }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PendingListingRow(
    listing: PendingListingUi,
    actionInProgress: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(listing.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${listing.parkingType} · ${listing.approvalStatus}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (actionInProgress) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onApprove) { Text("Approve") }
                    OutlinedButton(onClick = onReject) { Text("Reject", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
