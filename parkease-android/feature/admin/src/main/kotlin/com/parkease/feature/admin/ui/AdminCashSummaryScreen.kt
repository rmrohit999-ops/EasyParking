@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.feature.admin.data.CashByOwnerUi
import com.parkease.feature.admin.data.CashSummaryUi

@Composable
fun AdminCashSummaryScreen(
    onBack: () -> Unit,
    viewModel: AdminCashSummaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cash Payments") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val summary = uiState.summary
            when {
                uiState.isLoading && summary == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                summary != null -> CashSummaryContent(summary)
            }
            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }
}

@Composable
private fun CashSummaryContent(summary: CashSummaryUi) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Total cash collected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(summary.totalCollected.toDisplayString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("ParkEase commission: ${summary.totalCommission.toDisplayString()}", style = MaterialTheme.typography.bodySmall)
                    Text("Owner net payable: ${summary.totalOwnerNet.toDisplayString()}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CountTile("Completed", summary.completedCount, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                CountTile("Pending", summary.pendingCount, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            }
        }
        item { Text("By owner", style = MaterialTheme.typography.titleSmall) }
        items(summary.byOwner, key = { it.ownerId }) { OwnerCashRow(it) }
        if (summary.byOwner.isEmpty()) {
            item {
                Text(
                    "No completed cash transactions in this period.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CountTile(label: String, value: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OwnerCashRow(owner: CashByOwnerUi) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(owner.label, style = MaterialTheme.typography.titleSmall)
            Text("${owner.transactionCount} transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Collected: ${owner.totalCollected.toDisplayString()}", style = MaterialTheme.typography.bodySmall)
            Text("Commission: ${owner.commission.toDisplayString()}", style = MaterialTheme.typography.bodySmall)
            Text("Net earnings: ${owner.netEarnings.toDisplayString()}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
