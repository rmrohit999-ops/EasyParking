package com.parkease.partner.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.core.location.AddressGeocoder
import com.parkease.core.location.DriverLocationClient
import com.parkease.core.location.LocationPermissionState
import com.parkease.core.location.LocationResult
import com.parkease.core.location.ReverseGeocodeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OwnerLocationUiState(
    val permissionState: LocationPermissionState = LocationPermissionState.NOT_REQUESTED,
    val isLoading: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val addressLabel: String? = null,
)

/**
 * Real GPS + reverse-geocode for the owner dashboard's "My Current
 * Location" card — reuses the same DriverLocationClient/AddressGeocoder
 * the driver app's home screen and the location-editing screen already
 * use (one-shot fix, on-device geocoding, no new integration). Distinct
 * from a listing's registered "My Parking Location" (feature:owner-
 * parking's LocationFormScreen) — an owner may well be opening the app
 * away from any of their properties, so this is deliberately just "where
 * I am right now," not tied to any specific listing.
 */
@HiltViewModel
class OwnerHomeViewModel @Inject constructor(
    private val locationClient: DriverLocationClient,
    private val geocoder: AddressGeocoder,
) : ViewModel() {

    private val _locationState = MutableStateFlow(OwnerLocationUiState())
    val locationState: StateFlow<OwnerLocationUiState> = _locationState.asStateFlow()

    fun checkPermissionAlreadyGranted() {
        if (locationClient.hasLocationPermission()) {
            _locationState.value = _locationState.value.copy(permissionState = LocationPermissionState.GRANTED)
            fetchLocation()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _locationState.value = _locationState.value.copy(
            permissionState = if (granted) LocationPermissionState.GRANTED else LocationPermissionState.DENIED,
        )
        if (granted) fetchLocation()
    }

    private fun fetchLocation() {
        _locationState.value = _locationState.value.copy(isLoading = true)
        viewModelScope.launch {
            when (val result = locationClient.getCurrentLocation()) {
                is LocationResult.Success -> {
                    val lat = result.point.latitude
                    val lng = result.point.longitude
                    _locationState.value = _locationState.value.copy(isLoading = false, latitude = lat, longitude = lng)
                    when (val geocode = geocoder.reverseGeocode(lat, lng)) {
                        is ReverseGeocodeResult.Success -> _locationState.value = _locationState.value.copy(addressLabel = geocode.address.addressLine)
                        // Left null on purpose — the screen falls back to raw
                        // coordinates rather than an empty field.
                        ReverseGeocodeResult.Unavailable -> Unit
                    }
                }
                LocationResult.PermissionDenied -> _locationState.value = _locationState.value.copy(isLoading = false, permissionState = LocationPermissionState.DENIED)
                LocationResult.LocationUnavailable -> _locationState.value = _locationState.value.copy(isLoading = false)
            }
        }
    }
}
