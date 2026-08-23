package com.parkease.feature.ownerparking.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.feature.ownerparking.data.ParkingRepository
import com.parkease.feature.ownerparking.data.ParkingResult
import com.parkease.feature.ownerparking.data.PhotoUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhotosUiState(
    val isLoading: Boolean = true,
    val photos: List<PhotoUi> = emptyList(),
    val isUploading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class PhotosViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ParkingRepository,
) : ViewModel() {

    val listingId: String = checkNotNull(savedStateHandle["listingId"]) { "listingId is required" }

    private val _uiState = MutableStateFlow(PhotosUiState())
    val uiState: StateFlow<PhotosUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val photos = repository.listPhotos(listingId)
                _uiState.value = _uiState.value.copy(isLoading = false, photos = photos)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "We couldn't load photos right now.")
            }
        }
    }

    fun upload(photoType: String, contentType: String, bytes: ByteArray) {
        _uiState.value = _uiState.value.copy(isUploading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.uploadPhoto(listingId, photoType, sectionId = null, contentType = contentType, bytes = bytes)) {
                is ParkingResult.Success -> {
                    _uiState.value = _uiState.value.copy(isUploading = false)
                    refresh()
                }
                is ParkingResult.Error -> _uiState.value = _uiState.value.copy(isUploading = false, errorMessage = result.message)
            }
        }
    }

    fun remove(photoId: String) {
        viewModelScope.launch {
            when (val result = repository.removePhoto(listingId, photoId)) {
                is ParkingResult.Success -> refresh()
                is ParkingResult.Error -> _uiState.value = _uiState.value.copy(errorMessage = result.message)
            }
        }
    }
}
