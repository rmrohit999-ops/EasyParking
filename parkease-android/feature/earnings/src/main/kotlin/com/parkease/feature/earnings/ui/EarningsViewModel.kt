package com.parkease.feature.earnings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.feature.earnings.data.EarningsBucketUi
import com.parkease.feature.earnings.data.EarningsRepository
import com.parkease.feature.earnings.data.EarningsResult
import com.parkease.feature.earnings.data.PayoutAccountUi
import com.parkease.feature.earnings.data.SettlementUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EarningsUiState(
    val isLoading: Boolean = true,
    val buckets: List<EarningsBucketUi> = emptyList(),
    val payoutAccounts: List<PayoutAccountUi> = emptyList(),
    val settlements: List<SettlementUi> = emptyList(),
    val newAccountMethod: String = "BANK",
    val holderNameInput: String = "",
    val accountNumberInput: String = "",
    val ifscInput: String = "",
    val upiVpaInput: String = "",
    val actionInProgress: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
) {
    val availableAmountText: String
        get() = buckets.firstOrNull { it.status == "AVAILABLE" }?.amount?.toDisplayString() ?: "INR 0.00"
}

@HiltViewModel
class EarningsViewModel @Inject constructor(
    private val repository: EarningsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EarningsUiState())
    val uiState: StateFlow<EarningsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            val summary = repository.summary()
            val accounts = repository.listPayoutAccounts()
            val settlements = repository.listSettlements()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                buckets = (summary as? EarningsResult.Success)?.value ?: _uiState.value.buckets,
                payoutAccounts = (accounts as? EarningsResult.Success)?.value ?: _uiState.value.payoutAccounts,
                settlements = (settlements as? EarningsResult.Success)?.value ?: _uiState.value.settlements,
                message = (summary as? EarningsResult.Error)?.message
                    ?: (accounts as? EarningsResult.Error)?.message
                    ?: (settlements as? EarningsResult.Error)?.message,
                isError = summary is EarningsResult.Error || accounts is EarningsResult.Error || settlements is EarningsResult.Error,
            )
        }
    }

    fun onMethodChanged(method: String) {
        _uiState.value = _uiState.value.copy(newAccountMethod = method)
    }

    fun onHolderNameChanged(value: String) {
        _uiState.value = _uiState.value.copy(holderNameInput = value)
    }

    fun onAccountNumberChanged(value: String) {
        _uiState.value = _uiState.value.copy(accountNumberInput = value)
    }

    fun onIfscChanged(value: String) {
        _uiState.value = _uiState.value.copy(ifscInput = value.uppercase())
    }

    fun onUpiVpaChanged(value: String) {
        _uiState.value = _uiState.value.copy(upiVpaInput = value)
    }

    fun addPayoutAccount() {
        val state = _uiState.value
        if (state.holderNameInput.isBlank()) return
        runAction {
            if (state.newAccountMethod == "BANK") {
                repository.addBankAccount(state.holderNameInput.trim(), state.accountNumberInput.trim(), state.ifscInput.trim())
            } else {
                repository.addUpiAccount(state.holderNameInput.trim(), state.upiVpaInput.trim())
            }
        }
        _uiState.value = _uiState.value.copy(
            holderNameInput = "",
            accountNumberInput = "",
            ifscInput = "",
            upiVpaInput = "",
        )
    }

    fun setPrimary(accountId: String) {
        runAction { repository.setPrimaryPayoutAccount(accountId) }
    }

    fun removeAccount(accountId: String) {
        runAction { repository.removePayoutAccount(accountId) }
    }

    fun requestSettlement() {
        runAction { repository.requestSettlement() }
    }

    private fun runAction(block: suspend () -> EarningsResult<*>) {
        _uiState.value = _uiState.value.copy(actionInProgress = true, message = null)
        viewModelScope.launch {
            val result = block()
            _uiState.value = when (result) {
                is EarningsResult.Success -> _uiState.value.copy(actionInProgress = false, message = "Done.", isError = false)
                is EarningsResult.Error -> _uiState.value.copy(actionInProgress = false, message = result.message, isError = true)
            }
            load()
        }
    }
}
