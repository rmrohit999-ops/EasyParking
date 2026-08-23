package com.parkease.feature.auth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Email+password login. Phone/OTP is a separate screen (OtpLoginScreen) —
 * kept apart rather than tabbed together so each flow's validation and
 * copy stays simple, per the "no training required" principle.
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onNavigateToOtpLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) onLoggedIn()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("ParkEase", style = MaterialTheme.typography.headlineMedium)
        Text("Find Parking. Park Easy.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))

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
            label = { Text("Password") },
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
            onClick = { viewModel.login(email.trim(), password) },
            enabled = !uiState.isLoading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Sign in")
            }
        }

        TextButton(onClick = onNavigateToForgotPassword, modifier = Modifier.fillMaxWidth()) {
            Text("Forgot password?")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        OutlinedButton(onClick = onNavigateToOtpLogin, modifier = Modifier.fillMaxWidth()) {
            Text("Continue with phone number")
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onNavigateToRegister, modifier = Modifier.fillMaxWidth()) {
            Text("New to ParkEase? Create an account")
        }
    }
}
