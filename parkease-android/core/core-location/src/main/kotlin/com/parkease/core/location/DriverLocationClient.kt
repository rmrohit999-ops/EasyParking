package com.parkease.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class GeoPoint(val latitude: Double, val longitude: Double, val accuracyMeters: Float?)

sealed class LocationResult {
    data class Success(val point: GeoPoint) : LocationResult()
    data object PermissionDenied : LocationResult()
    data object LocationUnavailable : LocationResult()
}

/**
 * Thin wrapper over FusedLocationProviderClient for a one-shot "where is
 * the driver right now" fix — the real implementation deferred from
 * Milestone 4 to here (Milestone 5), the first feature that actually needs
 * live location per Milestone 0 §15's "don't request permissions ahead of
 * real use." Callers are responsible for having already obtained runtime
 * permission (see [hasLocationPermission]) — this class never triggers a
 * permission prompt itself, since that's a UI concern owned by the
 * requesting screen.
 */
@Singleton
class DriverLocationClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission") // guarded by hasLocationPermission() below
    suspend fun getCurrentLocation(): LocationResult {
        if (!hasLocationPermission()) return LocationResult.PermissionDenied

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(60_000L)
            .build()

        return suspendCancellableCoroutine { continuation: CancellableContinuation<LocationResult> ->
            val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()
            continuation.invokeOnCancellation { cancellationTokenSource.cancel() }

            fusedClient.getCurrentLocation(request, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    val result = if (location == null) {
                        LocationResult.LocationUnavailable
                    } else {
                        LocationResult.Success(
                            GeoPoint(location.latitude, location.longitude, location.accuracy.takeIf { it > 0f }),
                        )
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(LocationResult.LocationUnavailable)
                }
        }
    }
}
