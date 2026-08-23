package com.parkease.feature.driversearch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.feature.driversearch.data.DiscoveryRepository
import com.parkease.feature.driversearch.data.DiscoveryResult
import com.parkease.feature.driversearch.data.FavoriteUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val isLoading: Boolean = true,
    val favorites: List<FavoriteUi> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: DiscoveryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.listFavorites()) {
                is DiscoveryResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false, favorites = result.value)
                is DiscoveryResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }

    fun remove(listingId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(listingId, currentlyFavorite = true)
            refresh()
        }
    }
}
