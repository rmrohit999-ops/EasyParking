@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.ownerparking.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.core.model.ListingStatus
import com.parkease.feature.ownerparking.data.ListingDetailUi
import com.parkease.feature.ownerparking.data.SectionUi

@Composable
fun ListingDetailScreen(
    onEditLocation: (String) -> Unit,
    onAddSection: (String) -> Unit,
    onManagePhotos: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ListingDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Location/section/photo screens are separate back-stack entries with
    // their own ViewModel instance, so this screen re-fetches on resume
    // rather than sharing state, to pick up whatever they just saved.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.detail?.listing?.name ?: "Listing") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val detail = uiState.detail
            when {
                uiState.isLoading && detail == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                detail != null -> ListingDetailContent(
                    detail = detail,
                    actionInProgress = uiState.actionInProgress,
                    onEditLocation = { onEditLocation(viewModel.listingId) },
                    onAddSection = { onAddSection(viewModel.listingId) },
                    onManagePhotos = { onManagePhotos(viewModel.listingId) },
                    onRemoveSection = viewModel::removeSection,
                    onSubmitForApproval = viewModel::submitForApproval,
                    onSetStatus = viewModel::setStatus,
                )
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
private fun ListingDetailContent(
    detail: ListingDetailUi,
    actionInProgress: Boolean,
    onEditLocation: () -> Unit,
    onAddSection: () -> Unit,
    onManagePhotos: () -> Unit,
    onRemoveSection: (String) -> Unit,
    onSubmitForApproval: () -> Unit,
    onSetStatus: (ListingStatus) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text("Review: ${detail.listing.approvalStatus?.name ?: "UNKNOWN"}") })
                AssistChip(onClick = {}, label = { Text("Status: ${detail.listing.status?.name ?: "UNKNOWN"}") })
            }
        }

        item {
            SectionHeader("Location")
            if (detail.location == null) {
                OutlinedButton(onClick = onEditLocation, enabled = !actionInProgress) { Text("Add location") }
            } else {
                Column {
                    Text(detail.location.addressLine, style = MaterialTheme.typography.bodyMedium)
                    Text("${detail.location.city}, ${detail.location.state} ${detail.location.postalCode}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onEditLocation, enabled = !actionInProgress) { Text("Edit location") }
                }
            }
        }

        item {
            SectionHeader("Sections (${detail.sections.size})")
        }
        items(detail.sections, key = { it.id }) { section ->
            SectionRow(section = section, onRemove = { onRemoveSection(section.id) }, enabled = !actionInProgress)
        }
        item {
            OutlinedButton(onClick = onAddSection, enabled = !actionInProgress) { Text("Add section") }
        }

        item {
            SectionHeader("Photos (${detail.photoCount})")
            OutlinedButton(onClick = onManagePhotos, enabled = !actionInProgress) { Text("Manage photos") }
        }

        item {
            SectionHeader("Actions")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (detail.listing.approvalStatus?.name != "APPROVED") {
                    Button(onClick = onSubmitForApproval, enabled = !actionInProgress, modifier = Modifier.fillMaxWidth()) {
                        Text("Submit for review")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onSetStatus(ListingStatus.ACTIVE) }, enabled = !actionInProgress) { Text("Activate") }
                    OutlinedButton(onClick = { onSetStatus(ListingStatus.PAUSED) }, enabled = !actionInProgress) { Text("Pause") }
                    OutlinedButton(onClick = { onSetStatus(ListingStatus.CLOSED) }, enabled = !actionInProgress) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun SectionRow(section: SectionUi, onRemove: () -> Unit, enabled: Boolean) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(section.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${section.vehicleCategory?.name ?: "UNKNOWN"} · capacity ${section.capacity} · " +
                        "${section.hourlyRate.toDisplayString()}/hr · ${section.approvalStatus?.name ?: "UNKNOWN"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(Icons.Default.Delete, contentDescription = "Remove section")
            }
        }
    }
}
