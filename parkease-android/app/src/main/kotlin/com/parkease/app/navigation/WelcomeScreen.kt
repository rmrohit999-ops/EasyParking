package com.parkease.app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The app's true first screen — always shown, regardless of session state
 * (RootNavHost re-routes an already-logged-in user straight past login
 * when they tap a card, but Welcome itself is unconditional). Selecting a
 * card is routing only, never a permission grant — see RoleIntent's doc
 * comment.
 */
@Composable
fun WelcomeScreen(
    onSelectPark: () -> Unit,
    onSelectOwn: () -> Unit,
    onSelectAdmin: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 64.dp, bottom = 32.dp),
        ) {
            Text(
                "Welcome to ParkEase",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "What would you like to do?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.weight(1f))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RoleCard(
                    icon = Icons.Default.LocalParking,
                    title = "I Want to Park",
                    subtitle = "Find and book a parking spot nearby",
                    onClick = onSelectPark,
                )
                RoleCard(
                    icon = Icons.Default.DirectionsCar,
                    title = "I Own a Parking Space",
                    subtitle = "List your space and manage bookings",
                    onClick = onSelectOwn,
                )
                RoleCard(
                    icon = Icons.Default.AdminPanelSettings,
                    title = "I am an Admin",
                    subtitle = "Monitor and manage the platform",
                    onClick = onSelectAdmin,
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RoleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(14.dp).size(28.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
