package com.parkease.core.maps

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.osmdroid.util.GeoPoint

private const val DEFAULT_ZOOM_NO_PIN = 5.0
private const val DEFAULT_ZOOM_WITH_PIN = 17.0

/**
 * A single draggable pin on an osmdroid map, for "select the exact parking
 * entrance" flows. Tapping the map or dragging the marker both call back
 * with the new coordinates — the caller (a form ViewModel) decides what to
 * do with them (update lat/lng fields, trigger reverse geocoding, etc.),
 * matching how the existing GPS button on LocationFormScreen already
 * works, so both input paths feed the same state. Same public signature as
 * before this module's Google Maps -> osmdroid migration, so callers
 * needed no changes beyond that removed `mapsConfigured()` gate.
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
    defaultCenter: GeoPoint = GeoPoint(20.5937, 78.9629), // geographic center of India — a neutral starting view, not a claim about the user's location
    /** Shows the owner's own live GPS position (blue dot) alongside the red draggable parking pin — the owner may be physically away from the property they're registering, so the two are deliberately distinct: blue = where I am, red = where the parking is. */
    myLocationEnabled: Boolean = false,
) {
    val hasPin = latitude != null && longitude != null
    val position = if (hasPin) GeoPoint(latitude!!, longitude!!) else defaultCenter

    OsmMap(
        cameraCenter = position,
        modifier = modifier,
        initialZoom = if (hasPin) DEFAULT_ZOOM_WITH_PIN else DEFAULT_ZOOM_NO_PIN,
        myLocationEnabled = myLocationEnabled,
        draggablePin = position,
        onDraggablePinMoved = onLocationSelected,
        onMapClick = { point -> onLocationSelected(point.latitude, point.longitude) },
    )
}
