@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.earnings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.feature.earnings.data.EarningsBucketUi
import com.parkease.feature.earnings.data.PayoutAccountUi
import com.parkease.feature.earnings.data.SettlementUi

/**
 * Owner-facing earnings/payout-accounts/settlements screen for Milestone
 * 9. One scrolling screen rather than three separate ones — the three
 * concerns (what you've earned, where it goes, when you've been paid) are
 * small enough individually that a tabbed/multi-screen flow would add
 * navigation overhead without real benefit, matching feature:attendant's
 * "single screen flow" scope call in Milestone 8.
 */
@Composable
fun EarningsScreen(onBack: () -> Unit, viewModel: EarningsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Earnings & payouts") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { SummarySection(uiState.buckets, uiState.actionInProgress, onRequestSettlement = viewModel::requestSettlement) }
            item { PayoutAccountsSection(uiState, viewModel) }
            item { SettlementsSection(uiState.settlements) }
            if (uiState.message != null) {
                item {
                    Text(
                        uiState.message ?: "",
                        color = if (uiState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummarySection(buckets: List<EarningsBucketUi>, actionInProgress: Boolean, onRequestSettlement: () -> Unit) {
    val available = buckets.firstOrNull { it.status == "AVAILABLE" }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Earnings", style = MaterialTheme.typography.titleMedium)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                buckets.forEach { bucket ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(statusLabel(bucket.status), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${bucket.amount.toDisplayString()} (${bucket.count})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (bucket.status == "AVAILABLE") FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        Button(
            onClick = onRequestSettlement,
            enabled = !actionInProgress && available != null && !available.amount.isZero,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Request settlement of available earnings")
        }
    }
}

@Composable
private fun PayoutAccountsSection(uiState: EarningsUiState, viewModel: EarningsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Payout accounts", style = MaterialTheme.typography.titleMedium)
        uiState.payoutAccounts.forEach { account -> PayoutAccountRow(account, uiState.actionInProgress, viewModel) }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add a payout account", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.newAccountMethod == "BANK",
                        onClick = { viewModel.onMethodChanged("BANK") },
                        label = { Text("Bank account") },
                    )
                    FilterChip(
                        selected = uiState.newAccountMethod == "UPI",
                        onClick = { viewModel.onMethodChanged("UPI") },
                        label = { Text("UPI") },
                    )
                }
                OutlinedTextField(
                    value = uiState.holderNameInput,
                    onValueChange = viewModel::onHolderNameChanged,
                    label = { Text("Account holder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (uiState.newAccountMethod == "BANK") {
                    OutlinedTextField(
                        value = uiState.accountNumberInput,
                        onValueChange = viewModel::onAccountNumberChanged,
                        label = { Text("Account number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.ifscInput,
                        onValueChange = viewModel::onIfscChanged,
                        label = { Text("IFSC") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = uiState.upiVpaInput,
                        onValueChange = viewModel::onUpiVpaChanged,
                        label = { Text("UPI VPA (e.g. name@bank)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(
                    onClick = viewModel::addPayoutAccount,
                    enabled = !uiState.actionInProgress && uiState.holderNameInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Add account")
                }
            }
        }
    }
}

@Composable
private fun PayoutAccountRow(account: PayoutAccountUi, actionInProgress: Boolean, viewModel: EarningsViewModel) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(account.accountHolderName, fontWeight = FontWeight.Bold)
                if (account.isPrimary) Text("PRIMARY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                if (account.method == "BANK") "Bank •••• ${account.accountNumberMasked ?: ""} (${account.ifsc ?: ""})" else "UPI ${account.upiVpaMasked ?: ""}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Verification: ${account.verificationStatus}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!account.isPrimary) {
                    TextButton(onClick = { viewModel.setPrimary(account.id) }, enabled = !actionInProgress) {
                        Text("Make primary")
                    }
                }
                TextButton(onClick = { viewModel.removeAccount(account.id) }, enabled = !actionInProgress) {
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun SettlementsSection(settlements: List<SettlementUi>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settlement history", style = MaterialTheme.typography.titleMedium)
        if (settlements.isEmpty()) {
            Text("No settlements yet.", style = MaterialTheme.typography.bodyMedium)
        }
        settlements.forEach { settlement ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(settlement.requestedAmount.toDisplayString(), fontWeight = FontWeight.Bold)
                        Text(settlement.status, style = MaterialTheme.typography.labelMedium)
                    }
                    Text("Requested: ${settlement.requestedAt}", style = MaterialTheme.typography.bodySmall)
                    settlement.processedAt?.let { Text("Processed: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

private fun statusLabel(status: String) = when (status) {
    "PENDING" -> "Pending (not yet checked out)"
    "AVAILABLE" -> "Available to settle"
    "PROCESSING" -> "In a settlement"
    "SETTLED" -> "Settled"
    "FAILED" -> "Failed"
    "ADJUSTED" -> "Adjusted (partial refund)"
    "REVERSED" -> "Reversed (refunded)"
    else -> status
}
