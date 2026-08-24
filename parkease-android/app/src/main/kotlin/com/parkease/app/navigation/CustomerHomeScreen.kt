@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Driver-facing home — lands here for RoleIntent.PARK. "Find Parking" is
 * the primary action (feature:driver-search's SearchScreen); a real
 * map-first experience there is a separate, later piece of work that needs
 * a Google Maps API key — this entry point doesn't change once that lands.
 */
@Composable
fun CustomerHomeScreen(
    onFindParking: () -> Unit,
    onMyBookings: () -> Unit,
    onMyVehicles: () -> Unit,
    onNotifications: () -> Unit,
    onSignOut: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ParkEase") },
                actions = { TextButton(onClick = onSignOut) { Text("Sign out") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ElevatedCard(onClick = onFindParking, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column {
                        Text("Where do you want to park?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Search nearby parking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text("Quick access", style = MaterialTheme.typography.titleSmall)

            HomeActionCard(Icons.Default.EventNote, "My Bookings", "See your upcoming and past bookings", onMyBookings)
            HomeActionCard(Icons.Default.DirectionsCar, "My Vehicles", "Manage your registered vehicles", onMyVehicles)
            HomeActionCard(Icons.Default.Notifications, "Notifications", "Booking updates and alerts", onNotifications)

            Spacer(Modifier.weight(1f))
            AccountFooter(onPrivacyPolicy = onPrivacyPolicy, onDeleteAccount = onDeleteAccount)
        }
    }
}

@Composable
private fun HomeActionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
