package com.parkease.driver.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared footer for the home screen — kept out of the primary action list
 * per the "avoid clutter" UI requirement, but still one tap away. The
 * Admin link is unconditional here (not hidden based on any client-side
 * role check) — AdminHomeScreen's own dashboardSummary() call is the real
 * gate, same as it's always been; tapping it as a non-admin just shows
 * that screen's existing "you don't have admin access" state.
 */
@Composable
fun AccountFooter(onAdmin: () -> Unit, onPrivacyPolicy: () -> Unit, onDeleteAccount: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onPrivacyPolicy) {
            Text("Privacy & Terms", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onAdmin) {
            Text("Admin", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onDeleteAccount) {
            Text("Delete account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
