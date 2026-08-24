@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.feature.admin.data.DashboardSummaryUi

@Composable
fun AdminHomeScreen(
    onOpenUsers: () -> Unit,
    onOpenPendingListings: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: AdminHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin") },
                actions = { TextButton(onClick = onSignOut) { Text("Sign out") } },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.accessDenied -> AccessDeniedContent()
                uiState.isLoading && uiState.summary == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                else -> AdminHomeContent(
                    summary = uiState.summary,
                    onOpenUsers = onOpenUsers,
                    onOpenPendingListings = onOpenPendingListings,
                )
            }
        }
    }
}

@Composable
private fun AccessDeniedContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("You don't have admin access", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "This account isn't authorized as an admin. If you believe this is a mistake, contact support.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AdminHomeContent(
    summary: DashboardSummaryUi?,
    onOpenUsers: () -> Unit,
    onOpenPendingListings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Platform overview", style = MaterialTheme.typography.titleMedium)

        if (summary != null) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(220.dp),
            ) {
                items(
                    listOf(
                        "Total users" to summary.totalUsers,
                        "Suspended" to summary.suspendedUsers,
                        "Pending listings" to summary.pendingListings,
                        "Fraud alerts" to summary.openFraudAlerts,
                        "Support tickets" to summary.openSupportTickets,
                        "Open disputes" to summary.openDisputes,
                    ),
                ) { (label, value) -> StatTile(label, value) }
            }
        }

        Text("Manage", style = MaterialTheme.typography.titleMedium)

        AdminActionCard(
            icon = Icons.Default.PendingActions,
            title = "Pending listings",
            subtitle = if (summary != null && summary.pendingListings > 0) "${summary.pendingListings} awaiting review" else "Review and approve new parking listings",
            onClick = onOpenPendingListings,
        )
        AdminActionCard(
            icon = Icons.Default.PeopleAlt,
            title = "Users",
            subtitle = "Search accounts, suspend or reinstate",
            onClick = onOpenUsers,
        )
    }
}

@Composable
private fun StatTile(label: String, value: Int) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdminActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
