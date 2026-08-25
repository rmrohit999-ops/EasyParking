package com.parkease.driver.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.core.datastore.DriverPreferences
import com.parkease.core.location.AddressGeocoder
import com.parkease.core.location.DriverLocationClient
import com.parkease.core.location.ForwardGeocodeResult
import com.parkease.core.location.GeocodedPlace
import com.parkease.core.location.LocationPermissionState
import com.parkease.core.location.LocationResult
import com.parkease.core.maps.RouteResult
import com.parkease.core.maps.RoutingProfile
import com.parkease.core.maps.RoutingRepository
import com.parkease.core.model.VehicleCategory
import com.parkease.core.model.toEnumOrNull
import com.parkease.feature.driversearch.data.DiscoveryRepository
import com.parkease.feature.driversearch.data.DiscoveryResult
import com.parkease.feature.driversearch.data.ListingResultUi
import com.parkease.feature.driversearch.data.SearchFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import javax.inject.Inject

/** [DriverHomeUiState.radiusMeters] choices — 1 km is the spec-mandated default, not feature:driver-search's plain-list-screen default of 3 km. */
val RADIUS_CHOICES_METERS = listOf(500, 1000, 3000, 5000)
private const val DEFAULT_RADIUS_METERS = 1000

data class DriverHomeUiState(
    val permissionState: LocationPermissionState = LocationPermissionState.NOT_REQUESTED,
    val isLoading: Boolean = false,
    val results: List<ListingResultUi> = emptyList(),
    val selectedCategory: VehicleCategory = VehicleCategory.FOUR_WHEELER,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    val filters: SearchFilters = SearchFilters(),
    val favoritesOnly: Boolean = false,
    val selectedListingId: String? = null,
    /** The driver's real live GPS fix — used for the my-location overlay and as the walking/driving route origin, independent of where the map is currently centered/searching. */
    val driverLatitude: Double? = null,
    val driverLongitude: Double? = null,
    /** Where the current search is actually centered — the driver's GPS fix by default, or a searched address once one is picked. */
    val searchCenterLabel: String? = null,
    val addressQuery: String = "",
    val isSearchingAddress: Boolean = false,
    val addressMatches: List<GeocodedPlace> = emptyList(),
    /** Real routed line (or its straight-line fallback) from the driver's current position to the selected listing's entrance, drawn under the preview sheet. Null until a listing is selected and the route call resolves. */
    val routeToSelectedListing: List<GeoPoint>? = null,
    val routeToSelectedListingIsApproximate: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Orchestrates the driver home screen: the 2W/4W category switcher (with
 * "adaptive memory" — the last choice persists via DriverPreferences,
 * independent of which vehicle is registered as default), the 500m/1km/
 * 3km/5km radius chips (1km default per spec, not driver-search's plain
 * SearchScreen's 3km), search-by-address (re-centers the search away from
 * live GPS), a real routed line to whichever listing is selected, and the
 * map + nearby-carousel results feeding off all of that. Deliberately
 * composes the SAME DiscoveryRepository feature:driver-search's own
 * SearchScreen uses — this is a second, richer UI over identical real
 * search/favorites logic, not a parallel data layer that could drift from it.
 */
@HiltViewModel
class DriverHomeViewModel @Inject constructor(
    private val locationClient: DriverLocationClient,
    private val repository: DiscoveryRepository,
    private val driverPreferences: DriverPreferences,
    private val geocoder: AddressGeocoder,
    private val routingRepository: RoutingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DriverHomeUiState(selectedCategory = driverPreferences.lastVehicleCategory()?.toEnumOrNull<VehicleCategory>() ?: VehicleCategory.FOUR_WHEELER),
    )
    val uiState: StateFlow<DriverHomeUiState> = _uiState.asStateFlow()

    /** The point search runs against — the driver's GPS fix unless an address search overrides it. Distinct from driverLatitude/Longitude in the UI state, which always tracks the driver's real position. */
    private var searchLat: Double? = null
    private var searchLng: Double? = null

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
                    searchLat = locationResult.point.latitude
                    searchLng = locationResult.point.longitude
                    _uiState.value = _uiState.value.copy(
                        driverLatitude = searchLat,
                        driverLongitude = searchLng,
                        searchCenterLabel = null,
                    )
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

    /** The spec's "Interactive Category Switcher" — independent of any specific registered vehicle, so a driver can browse 2W spots even if their default vehicle is a car. Persists as "adaptive memory" for next time. */
    fun setCategory(category: VehicleCategory) {
        if (category == _uiState.value.selectedCategory) return
        driverPreferences.setLastVehicleCategory(category.name)
        _uiState.value = _uiState.value.copy(selectedCategory = category, selectedListingId = null)
        runSearch()
    }

    fun setRadius(radiusMeters: Int) {
        if (radiusMeters == _uiState.value.radiusMeters) return
        _uiState.value = _uiState.value.copy(radiusMeters = radiusMeters)
        runSearch()
    }

    fun setFilters(filters: SearchFilters) {
        _uiState.value = _uiState.value.copy(filters = filters)
        runSearch()
    }

    fun setFavoritesOnly(favoritesOnly: Boolean) {
        _uiState.value = _uiState.value.copy(favoritesOnly = favoritesOnly)
    }

    fun setAddressQuery(query: String) {
        _uiState.value = _uiState.value.copy(addressQuery = query, addressMatches = emptyList())
    }

    fun searchAddress() {
        val query = _uiState.value.addressQuery.trim()
        if (query.isBlank()) return
        _uiState.value = _uiState.value.copy(isSearchingAddress = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = geocoder.forwardGeocode(query)) {
                is ForwardGeocodeResult.Success -> _uiState.value = _uiState.value.copy(isSearchingAddress = false, addressMatches = result.places)
                ForwardGeocodeResult.Unavailable -> _uiState.value = _uiState.value.copy(
                    isSearchingAddress = false,
                    errorMessage = "We couldn't find that place. Try a more specific address.",
                )
            }
        }
    }

    /** Re-centers the search on a picked address rather than live GPS — driverLatitude/Longitude (the my-location dot) stay untouched. */
    fun selectAddressMatch(place: GeocodedPlace) {
        searchLat = place.latitude
        searchLng = place.longitude
        _uiState.value = _uiState.value.copy(
            addressMatches = emptyList(),
            addressQuery = place.label,
            searchCenterLabel = place.label,
            selectedListingId = null,
        )
        runSearch()
    }

    /** Drops a searched-address override and goes back to searching around the driver's live position. */
    fun clearSearchedAddress() {
        val lat = _uiState.value.driverLatitude
        val lng = _uiState.value.driverLongitude
        if (lat == null || lng == null) return
        searchLat = lat
        searchLng = lng
        _uiState.value = _uiState.value.copy(addressQuery = "", searchCenterLabel = null, selectedListingId = null)
        runSearch()
    }

    fun selectListing(listingId: String?) {
        _uiState.value = _uiState.value.copy(selectedListingId = listingId, routeToSelectedListing = null, routeToSelectedListingIsApproximate = false)
        if (listingId == null) return

        val listing = _uiState.value.results.firstOrNull { it.id == listingId } ?: return
        val driverLat = _uiState.value.driverLatitude
        val driverLng = _uiState.value.driverLongitude
        if (driverLat == null || driverLng == null) return

        viewModelScope.launch {
            val result = routingRepository.route(
                RoutingProfile.DRIVING,
                from = GeoPoint(driverLat, driverLng),
                to = GeoPoint(listing.latitude, listing.longitude),
            )
            // The driver may have deselected (or selected something else)
            // while this call was in flight — only apply it if still relevant.
            if (_uiState.value.selectedListingId != listingId) return@launch
            when (result) {
                is RouteResult.Routed -> _uiState.value = _uiState.value.copy(routeToSelectedListing = result.points, routeToSelectedListingIsApproximate = false)
                is RouteResult.Fallback -> _uiState.value = _uiState.value.copy(routeToSelectedListing = result.points, routeToSelectedListingIsApproximate = true)
            }
        }
    }

    fun toggleFavorite(listingId: String) {
        val current = _uiState.value.results.firstOrNull { it.id == listingId } ?: return
        val optimistic = _uiState.value.results.map { if (it.id == listingId) it.copy(isFavorite = !it.isFavorite) else it }
        _uiState.value = _uiState.value.copy(results = optimistic)
        viewModelScope.launch {
            val succeeded = repository.toggleFavorite(listingId, current.isFavorite)
            if (!succeeded) {
                val reverted = _uiState.value.results.map { if (it.id == listingId) it.copy(isFavorite = current.isFavorite) else it }
                _uiState.value = _uiState.value.copy(results = reverted)
            }
        }
    }

    private fun runSearch() {
        val lat = searchLat
        val lng = searchLng
        if (lat == null || lng == null) return

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val favoriteIds = repository.listFavoriteIds()
            when (
                val result = repository.search(
                    lat = lat,
                    lng = lng,
                    vehicleId = null,
                    category = _uiState.value.selectedCategory,
                    radiusMeters = _uiState.value.radiusMeters,
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
