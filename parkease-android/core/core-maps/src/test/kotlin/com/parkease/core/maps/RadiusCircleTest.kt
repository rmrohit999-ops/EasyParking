package com.parkease.core.maps

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.osmdroid.util.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RadiusCircleTest {

    private fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2) * sin(dLon / 2)
        return earthRadiusMeters * 2 * atan2(sqrt(h), sqrt(1 - h))
    }

    @Test
    fun `every point on the circle is approximately the requested radius from center`() {
        val center = GeoPoint(12.9716, 77.5946)
        val radius = 1000.0

        val circle = radiusCircle(center, radius)

        assertThat(circle.points).hasSize(49) // 0..48 inclusive
        circle.points.forEach { point ->
            assertThat(haversineMeters(center, point)).isWithin(2.0).of(radius)
        }
    }

    @Test
    fun `the circle closes back on itself`() {
        val circle = radiusCircle(GeoPoint(0.0, 0.0), 500.0)

        val first = circle.points.first()
        val last = circle.points.last()
        assertThat(first.latitude).isWithin(1e-9).of(last.latitude)
        assertThat(first.longitude).isWithin(1e-9).of(last.longitude)
    }
}
