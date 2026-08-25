package com.parkease.driver.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.core.location.AddressGeocoder
import com.parkease.core.location.ForwardGeocodeResult
import com.parkease.core.location.GeocodedPlace
import com.parkease.core.model.VehicleCategory
import com.parkease.feature.driversearch.data.DiscoveryRepository
import com.parkease.feature.driversearch.data.DiscoveryResult
import com.parkease.feature.driversearch.data.ListingResultUi
import com.parkease.feature.driversearch.data.SearchFilters
import com.parkease.feature.vehicles.data.VehicleUi
import com.parkease.feature.vehicles.data.VehiclesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class AdvanceBookingUiState(
    val destinationQuery: String = "",
    val isSearchingDestination: Boolean = false,
    val destinationMatches: List<GeocodedPlace> = emptyList(),
    val selectedDestination: GeocodedPlace? = null,
    val vehicles: List<VehicleUi> = emptyList(),
    val selectedVehicleId: String? = null,
    val isFindingParking: Boolean = false,
    val results: List<ListingResultUi> = emptyList(),
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Backs AdvanceBookingBottomSheet: resolve a typed destination to real
 * coordinates (on-device Geocoder — see AddressGeocoder.forwardGeocode's
 * doc comment on why this is a real, if less capable than Places
 * Autocomplete, "no fakes" choice), then search near it with the same
 * DiscoveryRepository the home screen and plain SearchScreen both use.
 *
 * Disclosed scope limit: this searches CURRENT availability near the
 * destination, not availability specifically AT the driver's chosen
 * future date/time — the backend has no time-aware availability query
 * yet (only "available right now"). The date/time/duration picked here
 * is still real and does reach the booking (BookingConfirmScreen, via
 * the prefilled start/end nav args), it just isn't used to pre-filter
 * which sections are shown as options in this sheet.
 */
@HiltViewModel
class AdvanceBookingViewModel @Inject constructor(
    private val geocoder: AddressGeocoder,
    private val discoveryRepository: DiscoveryRepository,
    private val vehiclesRepository: VehiclesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdvanceBookingUiState())
    val uiState: StateFlow<AdvanceBookingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val vehicles = vehiclesRepository.list()
            _uiState.value = _uiState.value.copy(
                vehicles = vehicles,
                selectedVehicleId = vehicles.firstOrNull { it.isDefault }?.id ?: vehicles.firstOrNull()?.id,
            )
        }
    }

    fun setDestinationQuery(query: String) {
        _uiState.value = _uiState.value.copy(destinationQuery = query, selectedDestination = null, destinationMatches = emptyList())
    }

    fun searchDestination() {
        val query = _uiState.value.destinationQuery.trim()
        if (query.isBlank()) return
        _uiState.value = _uiState.value.copy(isSearchingDestination = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = geocoder.forwardGeocode(query)) {
                is ForwardGeocodeResult.Success -> _uiState.value = _uiState.value.copy(isSearchingDestination = false, destinationMatches = result.places)
                ForwardGeocodeResult.Unavailable -> _uiState.value = _uiState.value.copy(
                    isSearchingDestination = false,
                    errorMessage = "We couldn't find that place. Try a more specific address.",
                )
            }
        }
    }

    fun selectDestination(place: GeocodedPlace) {
        _uiState.value = _uiState.value.copy(selectedDestination = place, destinationMatches = emptyList(), destinationQuery = place.label)
    }

    fun selectVehicle(vehicleId: String) {
        _uiState.value = _uiState.value.copy(selectedVehicleId = vehicleId)
    }

    fun findParking(category: VehicleCategory) {
        val destination = _uiState.value.selectedDestination ?: return
        _uiState.value = _uiState.value.copy(isFindingParking = true, hasSearched = true, errorMessage = null)
        viewModelScope.launch {
            when (
                val result = discoveryRepository.search(
                    lat = destination.latitude,
                    lng = destination.longitude,
                    vehicleId = null,
                    category = category,
                    radiusMeters = 2000,
                    filters = SearchFilters(),
                    favoriteIds = emptySet(),
                )
            ) {
                is DiscoveryResult.Success -> _uiState.value = _uiState.value.copy(isFindingParking = false, results = result.value)
                is DiscoveryResult.Error -> _uiState.value = _uiState.value.copy(isFindingParking = false, errorMessage = result.message)
            }
        }
    }

    fun estimatedTotalMinorUnits(hourlyRateMinorUnits: Long, startTime: Instant, endTime: Instant): Long {
        val hours = (endTime.epochSecond - startTime.epochSecond) / 3600.0
        return (hourlyRateMinorUnits * hours).toLong()
    }
}
