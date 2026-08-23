package com.parkease.feature.auth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Two-step phone flow: request OTP, then verify. The OTP is always entered
 * manually by the user — this app never reads SMS automatically (see
 * Milestone 0 §15: no READ_SMS/RECEIVE_SMS permission is requested).
 */
@Composable
fun OtpLoginScreen(
    onLoggedIn: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) onLoggedIn()
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Sign in with phone", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        if (uiState.otpSentTo == null) {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it; viewModel.clearError() },
                label = { Text("Phone number") },
                placeholder = { Text("+91XXXXXXXXXX") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.requestOtp(phone.trim(), "LOGIN") },
                enabled = !uiState.isLoading && phone.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState.isLoading) "Sending…" else "Send code")
            }
        } else {
            Text("Enter the code sent to ${uiState.otpSentTo}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it; viewModel.clearError() },
                label = { Text("6-digit code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.verifyOtp(uiState.otpSentTo!!, "LOGIN", code.trim()) },
                enabled = !uiState.isLoading && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState.isLoading) "Verifying…" else "Verify & continue")
            }
        }

        uiState.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack) { Text("Back") }
    }
}
