package com.parkease.feature.auth.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Same page hosts both documents, at #privacy and #terms respectively. */
private const val LEGAL_URL = "https://claude.ai/code/artifact/f5ce0140-a291-4052-a126-482dee0b6246"

@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) onRegistered()
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Create your account", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it; viewModel.clearError() },
            label = { Text("Full name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; viewModel.clearError() },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; viewModel.clearError() },
            label = { Text("Password (min. 8 characters)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        uiState.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.register(fullName.trim(), email.trim(), null, password) },
            enabled = !uiState.isLoading && fullName.isNotBlank() && email.isNotBlank() && password.length >= 8,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (uiState.isLoading) "Creating account…" else "Create account")
        }

        Text(
            "You'll add your vehicle next, before your first booking.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                "By creating an account you agree to our",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$LEGAL_URL#privacy"))) },
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text("Privacy Policy", style = MaterialTheme.typography.bodySmall) }
            Text("&", style = MaterialTheme.typography.bodySmall)
            TextButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$LEGAL_URL#terms"))) },
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text("Terms", style = MaterialTheme.typography.bodySmall) }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) { Text("Back") }
    }
}
