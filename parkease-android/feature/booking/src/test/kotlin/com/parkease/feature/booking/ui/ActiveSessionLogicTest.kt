package com.parkease.feature.booking.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ActiveSessionLogicTest {

    @Test
    fun `formats elapsed time under an hour as mm colon ss`() {
        assertThat(formatElapsed(0)).isEqualTo("00:00")
        assertThat(formatElapsed(65)).isEqualTo("01:05")
        assertThat(formatElapsed(3599)).isEqualTo("59:59")
    }

    @Test
    fun `formats elapsed time at or over an hour as h colon mm colon ss`() {
        assertThat(formatElapsed(3600)).isEqualTo("1:00:00")
        assertThat(formatElapsed(3725)).isEqualTo("1:02:05")
    }

    @Test
    fun `haversine distance between identical points is zero`() {
        assertThat(haversineMeters(12.9, 77.6, 12.9, 77.6)).isEqualTo(0.0)
    }

    @Test
    fun `haversine distance matches a known real-world reference within 1 percent`() {
        // Bengaluru MG Road to Cubbon Park entrance — roughly 900m apart in reality.
        val distance = haversineMeters(12.9757, 77.6069, 12.9789, 77.5993)
        assertThat(distance).isGreaterThan(700.0)
        assertThat(distance).isLessThan(1200.0)
    }
}
