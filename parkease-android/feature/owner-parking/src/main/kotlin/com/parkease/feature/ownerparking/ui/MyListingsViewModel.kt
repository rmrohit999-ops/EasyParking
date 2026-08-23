package com.parkease.feature.ownerparking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.feature.ownerparking.data.ListingUi
import com.parkease.feature.ownerparking.data.ParkingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyListingsUiState(
    val isLoading: Boolean = true,
    val listings: List<ListingUi> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class MyListingsViewModel @Inject constructor(
    private val repository: ParkingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyListingsUiState())
    val uiState: StateFlow<MyListingsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val listings = repository.listMine()
                _uiState.value = _uiState.value.copy(isLoading = false, listings = listings)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "We couldn't load your listings right now. Please try again.",
                )
            }
        }
    }
}
