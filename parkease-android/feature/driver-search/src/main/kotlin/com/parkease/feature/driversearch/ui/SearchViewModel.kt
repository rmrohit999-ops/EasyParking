package com.parkease.feature.driversearch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.core.location.DriverLocationClient
import com.parkease.core.location.LocationPermissionState
import com.parkease.core.location.LocationResult
import com.parkease.core.model.VehicleCategory
import com.parkease.feature.driversearch.data.DiscoveryRepository
import com.parkease.feature.driversearch.data.DiscoveryResult
import com.parkease.feature.driversearch.data.ListingResultUi
import com.parkease.feature.driversearch.data.SearchFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_RADIUS_METERS = 3000

data class SearchUiState(
    val permissionState: LocationPermissionState = LocationPermissionState.NOT_REQUESTED,
    val isLoading: Boolean = false,
    val results: List<ListingResultUi> = emptyList(),
    val filters: SearchFilters = SearchFilters(),
    val activeCategory: VehicleCategory? = null,
    val hasVehicle: Boolean = true,
    val errorMessage: String? = null,
    val driverLatitude: Double? = null,
    val driverLongitude: Double? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val locationClient: DriverLocationClient,
    private val repository: DiscoveryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var vehicleId: String? = null

    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(
            permissionState = if (granted) LocationPermissionState.GRANTED else LocationPermissionState.DENIED,
        )
        if (granted) refreshLocationAndSearch()
    }

    fun checkPermissionAlreadyGranted() {
        if (locationClient.hasLocationPermission()) {
            _uiState.value = _uiState.value.copy(permissionState = LocationPermissionState.GRANTED)
            refreshLocationAndSearch()
        }
    }

    fun refreshLocationAndSearch() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val locationResult = locationClient.getCurrentLocation()) {
                is LocationResult.Success -> {
                    lastLat = locationResult.point.latitude
                    lastLng = locationResult.point.longitude
                    _uiState.value = _uiState.value.copy(driverLatitude = lastLat, driverLongitude = lastLng)
                    runSearch()
                }
                LocationResult.PermissionDenied -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    permissionState = LocationPermissionState.DENIED,
                    errorMessage = "Location permission is needed to find nearby parking.",
                )
                LocationResult.LocationUnavailable -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "We couldn't get your location. Please check your device's location settings and try again.",
                )
            }
        }
    }

    fun setFilters(filters: SearchFilters) {
        _uiState.value = _uiState.value.copy(filters = filters)
        runSearch()
    }

    fun toggleFavorite(listingId: String) {
        val current = _uiState.value.results.firstOrNull { it.id == listingId } ?: return
        val optimistic = _uiState.value.results.map {
            if (it.id == listingId) it.copy(isFavorite = !it.isFavorite) else it
        }
        _uiState.value = _uiState.value.copy(results = optimistic)
        viewModelScope.launch {
            val succeeded = repository.toggleFavorite(listingId, current.isFavorite)
            if (!succeeded) {
                // Revert on failure.
                val reverted = _uiState.value.results.map {
                    if (it.id == listingId) it.copy(isFavorite = current.isFavorite) else it
                }
                _uiState.value = _uiState.value.copy(results = reverted)
            }
        }
    }

    private fun runSearch() {
        val lat = lastLat
        val lng = lastLng
        if (lat == null || lng == null) return

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val defaultVehicle = repository.getDefaultVehicle()
            vehicleId = defaultVehicle?.first
            val category = defaultVehicle?.second ?: _uiState.value.activeCategory
            _uiState.value = _uiState.value.copy(hasVehicle = defaultVehicle != null, activeCategory = category)

            if (category == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, results = emptyList())
                return@launch
            }

            val favoriteIds = repository.listFavoriteIds()
            when (
                val result = repository.search(
                    lat = lat,
                    lng = lng,
                    vehicleId = vehicleId,
                    category = if (vehicleId == null) category else null,
                    radiusMeters = DEFAULT_RADIUS_METERS,
                    filters = _uiState.value.filters,
                    favoriteIds = favoriteIds,
                )
            ) {
                is DiscoveryResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false, results = result.value)
                is DiscoveryResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }
}
