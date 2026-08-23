package com.parkease.feature.ownerparking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.core.model.ApprovalStatus
import com.parkease.core.model.ListingStatus
import com.parkease.feature.ownerparking.data.ListingUi

@Composable
fun MyListingsScreen(
    onCreateListing: () -> Unit,
    onOpenListing: (String) -> Unit,
    viewModel: MyListingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateListing) {
                Icon(Icons.Default.Add, contentDescription = "Add parking listing")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.listings.isEmpty() -> EmptyListingsState(modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.listings, key = { it.id }) { listing ->
                        ListingCard(listing = listing, onClick = { onOpenListing(listing.id) })
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
private fun EmptyListingsState(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No parking listings yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add your first listing to start renting out parking spaces.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ListingCard(listing: ListingUi, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(listing.name, style = MaterialTheme.typography.titleMedium)
            listing.parkingType?.let {
                Text(it.name.lowercase().replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(label = approvalLabel(listing.approvalStatus))
                StatusChip(label = statusLabel(listing.status))
            }
        }
    }
}

@Composable
private fun StatusChip(label: String) {
    AssistChip(onClick = {}, label = { Text(label) })
}

private fun approvalLabel(status: ApprovalStatus?): String = when (status) {
    ApprovalStatus.PENDING -> "Pending review"
    ApprovalStatus.APPROVED -> "Approved"
    ApprovalStatus.REJECTED -> "Rejected"
    ApprovalStatus.NEEDS_MORE_INFORMATION -> "Needs more info"
    ApprovalStatus.SUSPENDED -> "Suspended"
    null -> "Unknown"
}

private fun statusLabel(status: ListingStatus?): String = when (status) {
    ListingStatus.ACTIVE -> "Active"
    ListingStatus.PAUSED -> "Paused"
    ListingStatus.CLOSED -> "Closed"
    null -> "Unknown"
}
