package com.parkease.core.location

import android.content.Context
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ReverseGeocodedAddress(
    val addressLine: String,
    val city: String,
    val state: String,
    val postalCode: String,
)

sealed class ReverseGeocodeResult {
    data class Success(val address: ReverseGeocodedAddress) : ReverseGeocodeResult()
    data object Unavailable : ReverseGeocodeResult()
}

/**
 * Turns a GPS fix into an editable street address using the platform
 * Geocoder — no Google Maps API key needed, since this is backed by the
 * device's own geocoding service rather than a Maps REST call. Some AOSP
 * builds ship without a geocoder backend at all (Geocoder.isPresent() ==
 * false), so callers must handle Unavailable and let the fields stay
 * manually editable, matching how getCurrentLocation() already degrades.
 */
@Singleton
class AddressGeocoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseGeocodeResult =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext ReverseGeocodeResult.Unavailable

            val geocoder = Geocoder(context, Locale.getDefault())
            val place = try {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            } catch (e: Exception) {
                null
            } ?: return@withContext ReverseGeocodeResult.Unavailable

            val addressLine = place.getAddressLine(0)
                ?: listOfNotNull(place.subThoroughfare, place.thoroughfare).joinToString(" ").trim()

            ReverseGeocodeResult.Success(
                ReverseGeocodedAddress(
                    addressLine = addressLine,
                    city = place.locality ?: place.subAdminArea.orEmpty(),
                    state = place.adminArea.orEmpty(),
                    postalCode = place.postalCode.orEmpty(),
                ),
            )
        }
}
