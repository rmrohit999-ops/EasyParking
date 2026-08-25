package com.parkease.core.maps

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.osmdroid.util.GeoPoint

class RoutingRepositoryTest {

    private val straightLine = listOf(GeoPoint(12.9, 77.6), GeoPoint(12.95, 77.65))

    @Test
    fun `decodes a well-formed OSRM response into routed points`() {
        val response = OsrmRouteResponse(
            code = "Ok",
            routes = listOf(
                OsrmRoute(
                    geometry = OsrmGeometry(type = "LineString", coordinates = listOf(listOf(77.6, 12.9), listOf(77.62, 12.92), listOf(77.65, 12.95))),
                    distance = 1234.5,
                    duration = 300.0,
                ),
            ),
        )

        val result = decodeOrFallback(response, straightLine)

        assertThat(result).isInstanceOf(RouteResult.Routed::class.java)
        val routed = result as RouteResult.Routed
        assertThat(routed.points).hasSize(3)
        // GeoJSON is [lon, lat] — decoding must flip the order for GeoPoint(lat, lon).
        assertThat(routed.points.first().latitude).isEqualTo(12.9)
        assertThat(routed.points.first().longitude).isEqualTo(77.6)
        assertThat(routed.distanceMeters).isEqualTo(1234.5)
    }

    @Test
    fun `falls back to a straight line when OSRM reports a non-Ok code`() {
        val response = OsrmRouteResponse(code = "NoRoute", routes = emptyList())

        val result = decodeOrFallback(response, straightLine)

        assertThat(result).isEqualTo(RouteResult.Fallback(straightLine))
    }

    @Test
    fun `falls back to a straight line when the response has no routes`() {
        val response = OsrmRouteResponse(code = "Ok", routes = null)

        val result = decodeOrFallback(response, straightLine)

        assertThat(result).isEqualTo(RouteResult.Fallback(straightLine))
    }

    @Test
    fun `falls back to a straight line when the geometry has no coordinates`() {
        val response = OsrmRouteResponse(
            code = "Ok",
            routes = listOf(OsrmRoute(geometry = OsrmGeometry(type = "LineString", coordinates = emptyList()), distance = 0.0, duration = 0.0)),
        )

        val result = decodeOrFallback(response, straightLine)

        assertThat(result).isEqualTo(RouteResult.Fallback(straightLine))
    }
}
