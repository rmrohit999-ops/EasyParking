@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.feature.admin.data.AdminUserUi

@Composable
fun AdminUsersScreen(
    onBack: () -> Unit,
    viewModel: AdminUsersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingSuspendUserId by remember { mutableStateOf<String?>(null) }
    var suspendReason by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Users") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Search by email or phone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { viewModel.search() }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                trailingIcon = { TextButton(onClick = { viewModel.search() }) { Text("Search") } },
            )

            uiState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }

            if (uiState.isLoading && uiState.users.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (uiState.users.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No users found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.users, key = { it.id }) { user ->
                        UserRow(
                            user = user,
                            actionInProgress = uiState.actionInProgressUserId == user.id,
                            onSuspend = { pendingSuspendUserId = user.id },
                            onReinstate = { viewModel.reinstateUser(user.id) },
                        )
                    }
                }
            }
        }
    }

    if (pendingSuspendUserId != null) {
        AlertDialog(
            onDismissRequest = { pendingSuspendUserId = null; suspendReason = "" },
            title = { Text("Suspend this user?") },
            text = {
                Column {
                    Text("This blocks sign-in immediately and revokes all their sessions.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = suspendReason,
                        onValueChange = { suspendReason = it },
                        label = { Text("Reason") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSuspendUserId?.let { viewModel.suspendUser(it, suspendReason.ifBlank { "Suspended by admin" }) }
                        pendingSuspendUserId = null
                        suspendReason = ""
                    },
                ) { Text("Suspend", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSuspendUserId = null; suspendReason = "" }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun UserRow(
    user: AdminUserUi,
    actionInProgress: Boolean,
    onSuspend: () -> Unit,
    onReinstate: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(user.email ?: user.phone ?: "(no contact)", style = MaterialTheme.typography.titleSmall)
            Text(
                "${user.roles.joinToString(", ")} · ${user.status}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (actionInProgress) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (user.status == "SUSPENDED") {
                OutlinedButton(onClick = onReinstate) { Text("Reinstate") }
            } else {
                OutlinedButton(onClick = onSuspend) { Text("Suspend", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
