package com.parkease.core.maps

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavigationLauncherTest {

    @Test
    fun `google maps navigation uri uses driving mode`() {
        val uri = googleMapsNavigationUri(12.9716, 77.5946)
        assertThat(uri).isEqualTo("google.navigation:q=12.9716,77.5946&mode=d")
    }

    @Test
    fun `generic geo uri includes coordinates twice for maximum compatibility`() {
        val uri = genericGeoUri(12.9716, 77.5946, label = null)
        assertThat(uri).isEqualTo("geo:12.9716,77.5946?q=12.9716,77.5946")
    }

    @Test
    fun `generic geo uri appends an encoded label when present`() {
        val uri = genericGeoUri(12.9716, 77.5946, label = "MG Road Parking")
        assertThat(uri).isEqualTo("geo:12.9716,77.5946?q=12.9716,77.5946(MG+Road+Parking)")
    }

    @Test
    fun `generic geo uri omits the label suffix for a blank label`() {
        val uri = genericGeoUri(12.9716, 77.5946, label = "   ")
        assertThat(uri).isEqualTo("geo:12.9716,77.5946?q=12.9716,77.5946")
    }

    @Test
    fun `negative coordinates are formatted correctly`() {
        val uri = googleMapsNavigationUri(-33.8688, 151.2093)
        assertThat(uri).isEqualTo("google.navigation:q=-33.8688,151.2093&mode=d")
    }
}
