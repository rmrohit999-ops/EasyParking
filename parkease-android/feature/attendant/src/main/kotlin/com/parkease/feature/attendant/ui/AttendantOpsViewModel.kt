package com.parkease.feature.attendant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.feature.attendant.data.AttendantActionResult
import com.parkease.feature.attendant.data.AttendantRepository
import com.parkease.feature.attendant.data.ScanResultUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AttendantOpsUiState(
    val tokenInput: String = "",
    val isScanning: Boolean = false,
    val scanResult: ScanResultUi? = null,
    val presentedRegistrationInput: String = "",
    val mismatchRegistrationInput: String = "",
    val actionInProgress: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class AttendantOpsViewModel @Inject constructor(
    private val repository: AttendantRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AttendantOpsUiState())
    val uiState: StateFlow<AttendantOpsUiState> = _uiState.asStateFlow()

    fun onTokenChanged(value: String) {
        _uiState.value = _uiState.value.copy(tokenInput = value)
    }

    fun onPresentedRegistrationChanged(value: String) {
        _uiState.value = _uiState.value.copy(presentedRegistrationInput = value)
    }

    fun onMismatchRegistrationChanged(value: String) {
        _uiState.value = _uiState.value.copy(mismatchRegistrationInput = value)
    }

    fun scan() {
        val token = _uiState.value.tokenInput.trim()
        if (token.isEmpty()) return
        _uiState.value = _uiState.value.copy(isScanning = true, message = null, scanResult = null)
        viewModelScope.launch {
            when (val result = repository.scan(token)) {
                is AttendantActionResult.Success -> _uiState.value = _uiState.value.copy(isScanning = false, scanResult = result.value)
                is AttendantActionResult.Error -> _uiState.value = _uiState.value.copy(isScanning = false, message = result.message, isError = true)
            }
        }
    }

    fun checkIn() {
        val bookingId = _uiState.value.scanResult?.bookingId ?: return
        val token = _uiState.value.tokenInput.trim()
        runAction {
            repository.checkIn(bookingId, token, _uiState.value.presentedRegistrationInput)
        }
    }

    fun checkOut() {
        val bookingId = _uiState.value.scanResult?.bookingId ?: return
        val token = _uiState.value.tokenInput.trim()
        runAction { repository.checkOut(bookingId, token) }
    }

    fun reportMismatch() {
        val bookingId = _uiState.value.scanResult?.bookingId ?: return
        val registration = _uiState.value.mismatchRegistrationInput.trim()
        if (registration.isEmpty()) return
        runAction { repository.reportMismatch(bookingId, registration, note = null) }
    }

    fun reset() {
        _uiState.value = AttendantOpsUiState()
    }

    private fun runAction(block: suspend () -> AttendantActionResult<*>) {
        _uiState.value = _uiState.value.copy(actionInProgress = true, message = null)
        viewModelScope.launch {
            when (val result = block()) {
                is AttendantActionResult.Success -> _uiState.value = _uiState.value.copy(
                    actionInProgress = false,
                    message = "Done: ${result.value}",
                    isError = false,
                )
                is AttendantActionResult.Error -> _uiState.value = _uiState.value.copy(
                    actionInProgress = false,
                    message = result.message,
                    isError = true,
                )
            }
        }
    }
}
