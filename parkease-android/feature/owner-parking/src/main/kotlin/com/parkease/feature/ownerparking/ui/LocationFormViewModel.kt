package com.parkease.feature.ownerparking.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.core.location.AddressGeocoder
import com.parkease.core.location.DriverLocationClient
import com.parkease.core.location.LocationPermissionState
import com.parkease.core.location.LocationResult
import com.parkease.core.location.ReverseGeocodeResult
import com.parkease.feature.ownerparking.data.ParkingRepository
import com.parkease.feature.ownerparking.data.ParkingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocationFormUiState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
    val permissionState: LocationPermissionState = LocationPermissionState.NOT_REQUESTED,
    val isFetchingLocation: Boolean = false,
    val fetchedLatitude: Double? = null,
    val fetchedLongitude: Double? = null,
    val fetchedAddressLine: String? = null,
    val fetchedCity: String? = null,
    val fetchedState: String? = null,
    val fetchedPostalCode: String? = null,
    val geocodeUnavailable: Boolean = false,
)

@HiltViewModel
class LocationFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ParkingRepository,
    private val locationClient: DriverLocationClient,
    private val addressGeocoder: AddressGeocoder,
) : ViewModel() {

    val listingId: String = checkNotNull(savedStateHandle["listingId"]) { "listingId is required" }

    private val _uiState = MutableStateFlow(LocationFormUiState())
    val uiState: StateFlow<LocationFormUiState> = _uiState.asStateFlow()

    /** Called once the screen already holds permission, or right after the runtime prompt resolves. */
    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(
            permissionState = if (granted) LocationPermissionState.GRANTED else LocationPermissionState.DENIED,
        )
        if (granted) fetchCurrentLocation()
    }

    fun requestCurrentLocation() {
        if (locationClient.hasLocationPermission()) {
            _uiState.value = _uiState.value.copy(permissionState = LocationPermissionState.GRANTED)
            fetchCurrentLocation()
        } else {
            _uiState.value = _uiState.value.copy(permissionState = LocationPermissionState.NOT_REQUESTED)
        }
    }

    private fun fetchCurrentLocation() {
        _uiState.value = _uiState.value.copy(isFetchingLocation = true, errorMessage = null, geocodeUnavailable = false)
        viewModelScope.launch {
            when (val result = locationClient.getCurrentLocation()) {
                is LocationResult.Success -> {
                    val geocode = addressGeocoder.reverseGeocode(result.point.latitude, result.point.longitude)
                    _uiState.value = when (geocode) {
                        is ReverseGeocodeResult.Success -> _uiState.value.copy(
                            isFetchingLocation = false,
                            fetchedLatitude = result.point.latitude,
                            fetchedLongitude = result.point.longitude,
                            fetchedAddressLine = geocode.address.addressLine,
                            fetchedCity = geocode.address.city,
                            fetchedState = geocode.address.state,
                            fetchedPostalCode = geocode.address.postalCode,
                        )
                        ReverseGeocodeResult.Unavailable -> _uiState.value.copy(
                            isFetchingLocation = false,
                            fetchedLatitude = result.point.latitude,
                            fetchedLongitude = result.point.longitude,
                            geocodeUnavailable = true,
                        )
                    }
                }
                LocationResult.PermissionDenied -> _uiState.value = _uiState.value.copy(
                    isFetchingLocation = false,
                    permissionState = LocationPermissionState.DENIED,
                    errorMessage = "Location permission is needed to use your current position.",
                )
                LocationResult.LocationUnavailable -> _uiState.value = _uiState.value.copy(
                    isFetchingLocation = false,
                    errorMessage = "We couldn't get your location. Please check your device's location settings and try again.",
                )
            }
        }
    }

    fun save(
        latitude: Double,
        longitude: Double,
        addressLine: String,
        city: String,
        state: String,
        postalCode: String,
        entranceNotes: String?,
    ) {
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = repository.upsertLocation(listingId, latitude, longitude, addressLine, city, state, postalCode, entranceNotes)) {
                is ParkingResult.Success -> _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
                is ParkingResult.Error -> _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = result.message)
            }
        }
    }
}
