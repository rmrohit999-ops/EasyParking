@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.notifications.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Per-category push toggle (Milestone 11). Only the PUSH channel is shown —
 * see NOTIFICATION_CATEGORIES' doc comment for why SMS/EMAIL aren't
 * offered here even though the backend schema has room for them.
 */
@Composable
fun NotificationPreferencesScreen(onBack: () -> Unit, viewModel: NotificationPreferencesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Notification preferences") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    "Choose which push notifications you'd like to receive. You'll always see everything in your in-app inbox regardless of these settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            items(uiState.rows, key = { it.category }) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(row.label, style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = row.enabled, onCheckedChange = { viewModel.toggle(row.category, it) })
                }
                HorizontalDivider()
            }
            if (uiState.message != null) {
                item {
                    Text(
                        uiState.message ?: "",
                        color = if (uiState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}
