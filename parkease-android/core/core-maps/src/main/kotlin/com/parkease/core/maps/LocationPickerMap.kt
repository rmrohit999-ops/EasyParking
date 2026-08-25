package com.parkease.core.maps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

private const val DEFAULT_ZOOM_NO_PIN = 11f
private const val DEFAULT_ZOOM_WITH_PIN = 17f

/**
 * A single draggable pin on a map, for "select the exact parking entrance"
 * flows. Tapping the map or dragging the marker both call back with the
 * new coordinates — the caller (a form ViewModel) decides what to do with
 * them (update lat/lng fields, trigger reverse geocoding, etc.), matching
 * how the existing GPS button on LocationFormScreen already works, so both
 * input paths feed the same state.
 *
 * `latitude`/`longitude` being null means no location chosen yet — the map
 * centers on a wide default view instead of a specific pin. When the
 * caller later supplies real coordinates (e.g. from the GPS button), the
 * camera re-centers and zooms in to match.
 */
@Composable
fun LocationPickerMap(
    latitude: Double?,
    longitude: Double?,
    onLocationSelected: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier,
    defaultCenter: LatLng = LatLng(20.5937, 78.9629), // geographic center of India — a neutral starting view, not a claim about the user's location
) {
    val initial = if (latitude != null && longitude != null) LatLng(latitude, longitude) else defaultCenter
    val markerState = rememberMarkerState(position = initial)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initial, if (latitude != null) DEFAULT_ZOOM_WITH_PIN else DEFAULT_ZOOM_NO_PIN)
    }

    // External updates (the "use current location" GPS button) move the pin
    // and camera; this must not fire from the picker's own onLocationSelected
    // calls below, since compose would otherwise fight itself over marker
    // ownership — externally-driven and drag-driven position changes are
    // kept as separate one-way flows into/out of markerState.
    LaunchedEffect(latitude, longitude) {
        if (latitude != null && longitude != null) {
            val updated = LatLng(latitude, longitude)
            if (markerState.position != updated) {
                markerState.position = updated
                cameraPositionState.position = CameraPosition.fromLatLngZoom(updated, DEFAULT_ZOOM_WITH_PIN)
            }
        }
    }

    LaunchedEffect(markerState) {
        snapshotFlow { markerState.position }.collect { position ->
            onLocationSelected(position.latitude, position.longitude)
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        onMapClick = { latLng -> markerState.position = latLng },
    ) {
        Marker(state = markerState, draggable = true)
    }
}
