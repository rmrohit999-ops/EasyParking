@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.feature.admin.data.MapsQuotaSkuUi
import com.parkease.feature.admin.data.MapsQuotaSnapshotUi

/**
 * Live daily usage for the three billable Google Maps Platform SKUs this
 * codebase is built to guard (see backend MapsQuotaService's doc comment
 * for why every bar here reads 0% today: nothing calls Directions,
 * Places, or server-side Geocoding yet — this screen is real and correct
 * ahead of that, not simulated).
 */
@Composable
fun AdminMapsQuotaScreen(
    onBack: () -> Unit,
    viewModel: AdminMapsQuotaViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Maps API Quota") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val snapshot = uiState.snapshot
            when {
                uiState.isLoading && snapshot == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                snapshot != null -> QuotaContent(snapshot)
            }
            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }
}

@Composable
private fun QuotaContent(snapshot: MapsQuotaSnapshotUi) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { OverallStatusBadge(snapshot) }
        item {
            Text(
                "Daily usage — ${snapshot.date}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(snapshot.skus, key = { it.sku }) { SkuUsageCard(it, snapshot.globallyTripped) }
        item {
            Text(
                "No billable Maps API calls exist in ParkEase yet — these bars will stay at 0% " +
                    "until Directions, Places, or server-side Geocoding features are built. Map " +
                    "rendering and on-device address lookup are free and aren't tracked here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OverallStatusBadge(snapshot: MapsQuotaSnapshotUi) {
    val capped = snapshot.globallyTripped || snapshot.skus.any { it.capReached }
    val (label, color) = if (capped) {
        "80% CAP REACHED — INTENT FALLBACK ACTIVE" to MaterialTheme.colorScheme.error
    } else {
        "ACTIVE" to MaterialTheme.colorScheme.primary
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, shape = CircleShape),
            )
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun SkuUsageCard(sku: MapsQuotaSkuUi, globallyTripped: Boolean) {
    val effectivelyCapped = sku.capReached || globallyTripped
    val barColor = if (effectivelyCapped) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(sku.sku.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                Text("${sku.count} / ${sku.cap} (${sku.percentUsed}%)", style = MaterialTheme.typography.bodySmall)
            }
            LinearProgressIndicator(
                progress = { (sku.percentUsed / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = barColor,
            )
            if (effectivelyCapped) {
                Text(
                    "Blocked for the rest of today — falling back to free native-intent navigation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
