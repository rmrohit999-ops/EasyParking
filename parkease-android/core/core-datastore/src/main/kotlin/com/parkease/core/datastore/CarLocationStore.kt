package com.parkease.core.datastore

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CAR_LOCATION_PREFS_FILE_NAME = "parkease_car_location_prefs"

data class CarLocation(val latitude: Double, val longitude: Double)

/**
 * Remembers "where I parked" per booking — captured once, client-side, the
 * first time a booking is observed reaching PARKING_ACTIVE (see
 * ActiveSessionViewModel in feature:booking), since the backend has no such
 * concept today (no entrance/exit-relative "you parked here" data at all).
 * Keyed by bookingId so multiple bookings never collide; stored as strings
 * rather than SharedPreferences' lossy putFloat, to keep full double
 * precision — same convention as DriverPreferences in this file's package.
 */
@Singleton
class CarLocationStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(CAR_LOCATION_PREFS_FILE_NAME, Context.MODE_PRIVATE)

    fun get(bookingId: String): CarLocation? {
        val lat = prefs.getString(latKey(bookingId), null)?.toDoubleOrNull() ?: return null
        val lng = prefs.getString(lngKey(bookingId), null)?.toDoubleOrNull() ?: return null
        return CarLocation(lat, lng)
    }

    fun set(bookingId: String, latitude: Double, longitude: Double) {
        prefs.edit {
            putString(latKey(bookingId), latitude.toString())
            putString(lngKey(bookingId), longitude.toString())
        }
    }

    /** Called once a booking reaches a terminal status, so stale car locations don't accumulate forever. */
    fun clear(bookingId: String) {
        prefs.edit {
            remove(latKey(bookingId))
            remove(lngKey(bookingId))
        }
    }

    private fun latKey(bookingId: String) = "car_lat_$bookingId"
    private fun lngKey(bookingId: String) = "car_lng_$bookingId"
}
