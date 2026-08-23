package com.parkease.feature.auth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    var email by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Reset your password", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        if (submitted) {
            Text(
                "If an account exists for that email, we've sent instructions to reset the password.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    isLoading = true
                    scope.launch {
                        viewModel.forgotPasswordDirect(email.trim())
                        isLoading = false
                        submitted = true
                    }
                },
                enabled = !isLoading && email.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isLoading) "Sending…" else "Send reset instructions")
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack) { Text("Back to sign in") }
    }
}
