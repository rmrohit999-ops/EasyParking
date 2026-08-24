package com.parkease.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shared footer for the role home screens — kept out of the primary action list per the "avoid clutter" UI requirement, but still one tap away everywhere. */
@Composable
fun AccountFooter(onPrivacyPolicy: () -> Unit, onDeleteAccount: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onPrivacyPolicy) {
            Text("Privacy & Terms", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onDeleteAccount) {
            Text("Delete account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
