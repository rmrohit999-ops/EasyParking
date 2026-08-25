package com.parkease.core.datastore

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE_NAME = "parkease_driver_prefs"
private const val KEY_LAST_VEHICLE_CATEGORY = "last_vehicle_category"

/**
 * Plain, unencrypted UI-preference storage — deliberately separate from
 * EncryptedSessionStore, which exists specifically for tokens. Nothing
 * stored here is sensitive (currently just "which vehicle category did
 * the driver last browse"), so it doesn't need the encryption overhead.
 */
@Singleton
class DriverPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    /** e.g. "TWO_WHEELER" / "FOUR_WHEELER" — stored as the raw enum name so this class stays independent of core-model. */
    fun lastVehicleCategory(): String? = prefs.getString(KEY_LAST_VEHICLE_CATEGORY, null)

    fun setLastVehicleCategory(category: String) {
        prefs.edit { putString(KEY_LAST_VEHICLE_CATEGORY, category) }
    }
}
