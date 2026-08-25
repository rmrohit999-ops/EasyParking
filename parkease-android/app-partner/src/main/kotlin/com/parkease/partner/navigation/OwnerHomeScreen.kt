@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.partner.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Owner-facing home — this app's default landing for any account that
 * holds (or has just self-service-onboarded into) OWNER. "My Parking
 * Listings" is where adding a new space, pinning its location on the map,
 * and managing sections/photos all live (feature:owner-parking).
 */
@Composable
fun OwnerHomeScreen(
    becomeOwnerError: String?,
    onMyListings: () -> Unit,
    onBookings: () -> Unit,
    onEarnings: () -> Unit,
    onAttendantTools: () -> Unit,
    onAdmin: () -> Unit,
    onSignOut: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Owner Dashboard") },
                actions = { TextButton(onClick = onSignOut) { Text("Sign out") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            becomeOwnerError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            OwnerActionCard(
                icon = Icons.Default.LocalParking,
                title = "My Parking Listings",
                subtitle = "Add a space, pin its location, manage sections & photos",
                onClick = onMyListings,
            )
            OwnerActionCard(
                icon = Icons.Default.EventNote,
                title = "Bookings",
                subtitle = "See bookings and confirm cash payments received",
                onClick = onBookings,
            )
            OwnerActionCard(
                icon = Icons.Default.AccountBalanceWallet,
                title = "Earnings & Payouts",
                subtitle = "See what you've earned and request a payout",
                onClick = onEarnings,
            )
            OwnerActionCard(
                icon = Icons.Default.Badge,
                title = "Attendant Tools",
                subtitle = "Scan entry passes and check vehicles in/out",
                onClick = onAttendantTools,
            )

            Spacer(Modifier.weight(1f))
            AccountFooter(onAdmin = onAdmin, onPrivacyPolicy = onPrivacyPolicy, onDeleteAccount = onDeleteAccount)
        }
    }
}

@Composable
private fun OwnerActionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
