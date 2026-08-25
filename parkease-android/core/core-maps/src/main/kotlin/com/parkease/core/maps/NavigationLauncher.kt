package com.parkease.core.maps

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/**
 * ₹0-cost turn-by-turn navigation: hands off to the real Google Maps app
 * (or, if it's not installed, whatever the device's default maps app is)
 * via a plain Android Intent — no Directions API call, no Maps Platform
 * billing at all. This is what the backend's MapsQuotaService circuit
 * breaker is designed to fall back to once a daily safety cap trips —
 * called unconditionally here, not gated on quota state itself, since it
 * never touches a billable API in the first place.
 */
fun launchNavigation(context: Context, latitude: Double, longitude: Double, label: String? = null) {
    val googleMapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(googleMapsNavigationUri(latitude, longitude))).apply {
        setPackage("com.google.android.apps.maps")
    }
    if (googleMapsIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(googleMapsIntent)
        return
    }

    // Google Maps isn't installed — geo: is a standard Android URI scheme
    // any maps app can register for, still zero API cost.
    val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse(genericGeoUri(latitude, longitude, label)))
    if (genericIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(genericIntent)
    }
    // No maps app at all on the device: nothing we can do. The caller's UI
    // already shows the button as a normal action, not a promise it always
    // succeeds — silently no-op rather than surfacing a dead-end error for
    // what should be an extremely rare device state.
}

/** Extracted from [launchNavigation] so the actual URI format is covered by a plain JVM unit test, without needing Robolectric for android.net.Uri/Intent. */
internal fun googleMapsNavigationUri(latitude: Double, longitude: Double): String =
    "google.navigation:q=$latitude,$longitude&mode=d"

internal fun genericGeoUri(latitude: Double, longitude: Double, label: String?): String {
    val query = "$latitude,$longitude"
    val labelSuffix = label?.takeIf { it.isNotBlank() }?.let { "(${URLEncoder.encode(it, "UTF-8")})" }.orEmpty()
    return "geo:$query?q=$query$labelSuffix"
}
