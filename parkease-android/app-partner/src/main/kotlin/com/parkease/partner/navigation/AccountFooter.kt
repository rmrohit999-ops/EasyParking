package com.parkease.partner.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared footer for the owner home screen. The Admin link is unconditional
 * (not hidden based on any client-side role check) — AdminHomeScreen's own
 * dashboardSummary() call is the real gate, same as it's always been.
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
