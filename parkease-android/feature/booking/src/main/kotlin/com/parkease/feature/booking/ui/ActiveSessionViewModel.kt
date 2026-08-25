package com.parkease.feature.booking.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parkease.core.datastore.CarLocation
import com.parkease.core.datastore.CarLocationStore
import com.parkease.core.location.DriverLocationClient
import com.parkease.core.location.LocationResult
import com.parkease.core.maps.RouteResult
import com.parkease.core.maps.RoutingProfile
import com.parkease.core.maps.RoutingRepository
import com.parkease.core.model.BookingStatus
import com.parkease.core.model.BookingType
import com.parkease.feature.booking.data.BookingActionResult
import com.parkease.feature.booking.data.BookingRepository
import com.parkease.feature.booking.data.BookingUi
import com.parkease.feature.booking.reminder.ParkingReminders
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class ActiveSessionUiState(
    val isLoading: Boolean = true,
    val booking: BookingUi? = null,
    val elapsedSeconds: Long = 0,
    val carLocation: CarLocation? = null,
    val driverLatitude: Double? = null,
    val driverLongitude: Double? = null,
    val walkBackRoute: List<GeoPoint>? = null,
    val walkBackIsApproximate: Boolean = false,
    val walkBackDistanceMeters: Double? = null,
    val errorMessage: String? = null,
)

private const val REROUTE_THRESHOLD_METERS = 25.0

/**
 * Backs the "you're parked" screen: a live elapsed-time counter anchored
 * to the now-real `booking.actualCheckInAt` (backend change alongside this
 * feature — see QrService/BookingService), and "walk back to my car" —
 * captured once, client-side, the moment this booking is first observed
 * PARKING_ACTIVE (no such concept exists server-side), then a live routed
 * walking line from the continuous location Flow to that point. Also
 * schedules the one local WorkManager reminder near an advance booking's
 * end time, cancelling it (and the saved car location) once the session
 * ends.
 */
@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookingRepository,
    private val locationClient: DriverLocationClient,
    private val carLocationStore: CarLocationStore,
    private val routingRepository: RoutingRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val bookingId: String = checkNotNull(savedStateHandle["bookingId"]) { "bookingId is required" }

    private val _uiState = MutableStateFlow(ActiveSessionUiState())
    val uiState: StateFlow<ActiveSessionUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var locationJob: Job? = null
    private var lastRoutedFrom: GeoPoint? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = repository.getBooking(bookingId)) {
                is BookingActionResult.Success -> onBookingLoaded(result.value)
                is BookingActionResult.Error -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.message)
            }
        }
    }

    private fun onBookingLoaded(booking: BookingUi) {
        _uiState.value = _uiState.value.copy(isLoading = false, booking = booking, errorMessage = null)

        if (booking.status == BookingStatus.PARKING_ACTIVE) {
            ensureCarLocationCaptured()
            startElapsedTimer(booking.actualCheckInAt)
            startLocationTracking()
            if (booking.bookingType == BookingType.ADVANCE) {
                booking.endTime?.let { ParkingReminders.schedule(appContext, bookingId, it) }
            }
        } else {
            // Session ended (or hasn't started) — nothing left to track or remind about.
            timerJob?.cancel()
            locationJob?.cancel()
            ParkingReminders.cancel(appContext, bookingId)
            carLocationStore.clear(bookingId)
        }
    }

    private fun ensureCarLocationCaptured() {
        val existing = carLocationStore.get(bookingId)
        if (existing != null) {
            _uiState.value = _uiState.value.copy(carLocation = existing)
            return
        }
        viewModelScope.launch {
            when (val result = locationClient.getCurrentLocation()) {
                is LocationResult.Success -> {
                    carLocationStore.set(bookingId, result.point.latitude, result.point.longitude)
                    _uiState.value = _uiState.value.copy(carLocation = CarLocation(result.point.latitude, result.point.longitude))
                }
                else -> Unit // No fix available yet — walk-back simply stays unavailable until location succeeds on a later attempt.
            }
        }
    }

    private fun startElapsedTimer(actualCheckInAt: Instant?) {
        if (timerJob?.isActive == true) return
        val anchor = actualCheckInAt ?: return
        timerJob = viewModelScope.launch {
            while (true) {
                _uiState.value = _uiState.value.copy(elapsedSeconds = Duration.between(anchor, Instant.now()).seconds.coerceAtLeast(0))
                delay(1000)
            }
        }
    }

    private fun startLocationTracking() {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            locationClient.observeLocation().collect { result ->
                if (result !is LocationResult.Success) return@collect
                val here = GeoPoint(result.point.latitude, result.point.longitude)
                _uiState.value = _uiState.value.copy(driverLatitude = here.latitude, driverLongitude = here.longitude)

                val car = _uiState.value.carLocation ?: return@collect
                val distance = haversineMeters(here.latitude, here.longitude, car.latitude, car.longitude)
                _uiState.value = _uiState.value.copy(walkBackDistanceMeters = distance)

                val from = lastRoutedFrom
                if (from != null && haversineMeters(from.latitude, from.longitude, here.latitude, here.longitude) < REROUTE_THRESHOLD_METERS) {
                    return@collect // Hasn't moved enough to justify another OSRM call.
                }
                lastRoutedFrom = here
                when (val route = routingRepository.route(RoutingProfile.WALKING, from = here, to = GeoPoint(car.latitude, car.longitude))) {
                    is RouteResult.Routed -> _uiState.value = _uiState.value.copy(walkBackRoute = route.points, walkBackIsApproximate = false)
                    is RouteResult.Fallback -> _uiState.value = _uiState.value.copy(walkBackRoute = route.points, walkBackIsApproximate = true)
                }
            }
        }
    }

    /** ~80 m/min average walking pace — same honest estimate used elsewhere in this app (DriverHomeScreen's "X min walk"), not a real routed-duration ETA. */
    fun walkingEtaMinutes(distanceMeters: Double): Int = (distanceMeters / 80).roundToInt().coerceAtLeast(1)

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        locationJob?.cancel()
    }
}

internal fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
}
