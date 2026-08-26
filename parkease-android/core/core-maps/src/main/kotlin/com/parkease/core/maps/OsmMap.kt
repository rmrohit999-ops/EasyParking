package com.parkease.core.maps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.sin
import org.osmdroid.views.overlay.Polygon as OsmPolygon
import org.osmdroid.views.overlay.Polyline as OsmPolyline

/**
 * osmdroid (OpenStreetMap) is this app's only in-app map engine — replaces
 * the earlier Google Maps Compose integration, which needed a billed API
 * key that was never actually configured in this dev environment (so its
 * map screens rendered nothing locally). osmdroid fetches tiles from OSM's
 * public Mapnik servers over plain HTTPS, no key, no billing, and this
 * primitive is the one place that sets it up correctly (distinct
 * User-Agent per OSM's tile usage policy, app-scoped tile cache dir so no
 * storage permission is ever needed).
 *
 * Deliberately generic — markers/routes/polygons/my-location/draggable-pin
 * are all optional so one composable serves the driver app's read-only
 * discovery map, the owner location-picker's single draggable pin, and
 * (Phase 2) the partner app's entrance/exit/slot-polygon editor, without
 * each screen re-implementing MapView's lifecycle/tile-cache setup.
 */

enum class PinColor(internal val argb: Int) {
    RED(0xFFE53935.toInt()),
    ORANGE(0xFFFB8C00.toInt()),
    GREEN(0xFF43A047.toInt()),
    BLUE(0xFF1E88E5.toInt()),
    PURPLE(0xFF8E24AA.toInt()),
}

/** One read-only pin — a search result, not something the user can move (see [OsmMap]'s `draggablePin` for that). */
data class MapPin(
    val id: String,
    val position: GeoPoint,
    val title: String,
    val snippet: String? = null,
    val color: PinColor = PinColor.RED,
)

enum class RouteStyle { ROUTE_TO_ENTRANCE, WALK_BACK }

/** A polyline overlay — real routed points from [RoutingRepository], or its straight-line fallback; either way just a list of points to draw. */
data class MapRoute(
    val points: List<GeoPoint>,
    val style: RouteStyle,
)

/** Generic polygon overlay capability — no Phase-1 caller yet; Phase 2's partner-app slot boundaries will use this without further changes here. */
data class MapPolygon(
    val points: List<GeoPoint>,
    val strokeColorArgb: Int = 0xFF1E88E5.toInt(),
    val fillColorArgb: Int = 0x331E88E5,
)

/**
 * A circular [MapPolygon] approximated as a 48-point ring around [center] —
 * osmdroid has no native circle overlay, so "show the current search
 * radius on the map" is drawn this way. Real geometry (proper great-circle
 * offsets), not a decorative fixed-pixel circle that would look wrong at
 * different zoom levels.
 */
fun radiusCircle(center: GeoPoint, radiusMeters: Double, strokeColorArgb: Int = 0xFF1E88E5.toInt(), fillColorArgb: Int = 0x1A1E88E5): MapPolygon {
    val earthRadiusMeters = 6_371_000.0
    val points = (0..48).map { i ->
        val bearing = Math.toRadians(i * 360.0 / 48)
        val angularDistance = radiusMeters / earthRadiusMeters
        val lat1 = Math.toRadians(center.latitude)
        val lon1 = Math.toRadians(center.longitude)
        val lat2 = kotlin.math.asin(sin(lat1) * cos(angularDistance) + cos(lat1) * sin(angularDistance) * cos(bearing))
        val lon2 = lon1 + kotlin.math.atan2(sin(bearing) * sin(angularDistance) * cos(lat1), cos(angularDistance) - sin(lat1) * sin(lat2))
        GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }
    return MapPolygon(points = points, strokeColorArgb = strokeColorArgb, fillColorArgb = fillColorArgb)
}

private val osmConfigured = AtomicBoolean(false)

private fun ensureOsmdroidConfigured(context: Context) {
    if (osmConfigured.compareAndSet(false, true)) {
        val config = Configuration.getInstance()
        config.load(context, context.getSharedPreferences("osmdroid_config", Context.MODE_PRIVATE))
        // OSM's tile usage policy requires a distinct User-Agent per app —
        // the package name (already unique per app/flavor, e.g.
        // com.parkease.driver.dev) satisfies that without hardcoding one.
        config.userAgentValue = context.packageName
        config.osmdroidTileCache = File(context.cacheDir, "osmdroid_tiles").apply { mkdirs() }
    }
}

private val pinDrawableCache = mutableMapOf<Int, BitmapDrawable>()

/** A simple filled-circle-with-white-ring pin, drawn on-device — no bundled marker assets needed, and trivially recolorable per [PinColor]/availability. */
private fun coloredPinDrawable(context: Context, colorArgb: Int): BitmapDrawable =
    pinDrawableCache.getOrPut(colorArgb) {
        val sizePx = (36 * context.resources.displayMetrics.density).toInt().coerceAtLeast(24)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val radius = sizePx / 2f - sizePx * 0.14f
        canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb; style = Paint.Style.FILL })
        canvas.drawCircle(
            cx,
            cy,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = sizePx * 0.1f },
        )
        BitmapDrawable(context.resources, bitmap)
    }

@Composable
fun OsmMap(
    cameraCenter: GeoPoint,
    modifier: Modifier = Modifier,
    initialZoom: Double = 15.0,
    pins: List<MapPin> = emptyList(),
    routes: List<MapRoute> = emptyList(),
    polygons: List<MapPolygon> = emptyList(),
    myLocationEnabled: Boolean = false,
    showZoomControls: Boolean = true,
    onPinClick: (String) -> Unit = {},
    onMapClick: (GeoPoint) -> Unit = {},
    /** A single draggable pin (the location-picker use case) — null means none shown. */
    draggablePin: GeoPoint? = null,
    onDraggablePinMoved: (latitude: Double, longitude: Double) -> Unit = { _, _ -> },
    /** Fires (with no debounce — osmdroid has no distinct "idle" event) as the user pans/zooms, so a caller can offer "Search this area." */
    onCameraMoved: (GeoPoint) -> Unit = {},
    /** Shows a small recenter FAB in the bottom-end corner; tapping it calls back so the caller can reset its own search/camera state to the driver's real GPS position. */
    onRecenterClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    ensureOsmdroidConfigured(context)
    val currentOnCameraMoved = rememberUpdatedState(onCameraMoved)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(showZoomControls)
            controller.setZoom(initialZoom)
            controller.setCenter(cameraCenter)
            addMapListener(
                object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        currentOnCameraMoved.value(GeoPoint(mapCenter.latitude, mapCenter.longitude))
                        return false
                    }
                    override fun onZoom(event: ZoomEvent?): Boolean {
                        currentOnCameraMoved.value(GeoPoint(mapCenter.latitude, mapCenter.longitude))
                        return false
                    }
                },
            )
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    val lastCameraCenter = remember { mutableStateOf(cameraCenter) }
    LaunchedEffect(cameraCenter, initialZoom) {
        if (cameraCenter.latitude != lastCameraCenter.value.latitude || cameraCenter.longitude != lastCameraCenter.value.longitude) {
            mapView.controller.animateTo(cameraCenter)
            lastCameraCenter.value = cameraCenter
        }
        mapView.controller.setZoom(initialZoom)
    }

    Box(modifier = modifier) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView },
        update = { view ->
            view.setBuiltInZoomControls(showZoomControls)
            view.overlays.clear()

            // Added first so it sits at the bottom of the hit-test order —
            // osmdroid dispatches touch events to overlays in reverse
            // (last-added-first), so markers added after this still get
            // first refusal on a tap before it falls through as a map click.
            view.overlays.add(
                MapEventsOverlay(
                    object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            onMapClick(p)
                            return true
                        }
                        override fun longPressHelper(p: GeoPoint): Boolean = false
                    },
                ),
            )

            if (myLocationEnabled) {
                val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), view)
                myLocationOverlay.enableMyLocation()
                view.overlays.add(myLocationOverlay)
            }

            routes.forEach { route ->
                if (route.points.size < 2) return@forEach
                val line = OsmPolyline(view).apply {
                    setPoints(route.points)
                    outlinePaint.isAntiAlias = true
                    if (route.style == RouteStyle.WALK_BACK) {
                        outlinePaint.color = 0xFF1E88E5.toInt()
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(28f, 18f), 0f)
                    } else {
                        outlinePaint.color = 0xFF43A047.toInt()
                        outlinePaint.strokeWidth = 12f
                    }
                }
                view.overlays.add(line)
            }

            polygons.forEach { polygon ->
                if (polygon.points.size < 3) return@forEach
                val overlay = OsmPolygon(view).apply {
                    points = polygon.points
                    fillPaint.color = polygon.fillColorArgb
                    outlinePaint.color = polygon.strokeColorArgb
                    outlinePaint.strokeWidth = 4f
                }
                view.overlays.add(overlay)
            }

            pins.forEach { pin ->
                val marker = Marker(view).apply {
                    position = pin.position
                    title = pin.title
                    snippet = pin.snippet
                    icon = coloredPinDrawable(context, pin.color.argb)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ ->
                        onPinClick(pin.id)
                        true
                    }
                }
                view.overlays.add(marker)
            }

            if (draggablePin != null) {
                val marker = Marker(view).apply {
                    position = draggablePin
                    isDraggable = true
                    icon = coloredPinDrawable(context, PinColor.RED.argb)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerDragListener(
                        object : Marker.OnMarkerDragListener {
                            override fun onMarkerDrag(marker: Marker) = Unit
                            override fun onMarkerDragEnd(marker: Marker) {
                                onDraggablePinMoved(marker.position.latitude, marker.position.longitude)
                            }
                            override fun onMarkerDragStart(marker: Marker) = Unit
                        },
                    )
                }
                view.overlays.add(marker)
            }

            view.invalidate()
        },
    )
    if (onRecenterClick != null) {
        FloatingActionButton(
            onClick = onRecenterClick,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(44.dp),
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Recenter on my location")
        }
    }
    }
}
