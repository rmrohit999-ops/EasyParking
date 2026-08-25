package com.parkease.core.maps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState

/** One read-only pin on a [MarkersMap] — a search result, not something the user can move. */
data class MapMarker(
    val id: String,
    val position: LatLng,
    val title: String,
    val snippet: String? = null,
)

/**
 * Read-only markers for search-results-style screens (as opposed to
 * [LocationPickerMap]'s single draggable pin). Centers on [cameraCenter]
 * (typically the driver's own position) at a fixed zoom sized for the
 * same ~3km default search radius feature/driver-search already uses —
 * deliberately not an auto-fit-bounds camera, which needs the Maps SDK to
 * have finished initializing before CameraUpdateFactory can be called
 * safely; a fixed zoom around the search center has no such ordering
 * requirement and is a reasonable result for a first version.
 */
@Composable
fun MarkersMap(
    markers: List<MapMarker>,
    cameraCenter: LatLng,
    modifier: Modifier = Modifier,
    initialZoom: Float = 14f,
    onMarkerClick: (String) -> Unit = {},
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(cameraCenter, initialZoom)
    }

    LaunchedEffect(cameraCenter) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(cameraCenter, initialZoom)
    }

    GoogleMap(modifier = modifier, cameraPositionState = cameraPositionState) {
        Marker(
            state = rememberMarkerState(position = cameraCenter),
            title = "You",
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
        )
        markers.forEach { marker ->
            Marker(
                state = rememberMarkerState(position = marker.position),
                title = marker.title,
                snippet = marker.snippet,
                onClick = {
                    onMarkerClick(marker.id)
                    true // consumed: jump straight to the listing rather than showing the info window first
                },
            )
        }
    }
}
