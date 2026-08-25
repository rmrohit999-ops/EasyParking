package com.parkease.core.maps

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.osmdroid.util.GeoPoint
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class RoutingProfile(internal val osrmName: String) {
    WALKING("foot"),
    DRIVING("driving"),
}

sealed class RouteResult {
    /** A real road/path-following route from OSRM. */
    data class Routed(val points: List<GeoPoint>, val distanceMeters: Double, val durationSeconds: Double) : RouteResult()

    /** OSRM was unreachable, timed out, or returned no route — a direct line between the two points, so callers always have something to draw. Kept as a distinct case (not silently merged into Routed) so UI can label it "approximate" rather than claiming a real route. */
    data class Fallback(val points: List<GeoPoint>) : RouteResult()
}

@JsonClass(generateAdapter = true)
internal data class OsrmRouteResponse(val code: String?, val routes: List<OsrmRoute>?)

@JsonClass(generateAdapter = true)
internal data class OsrmRoute(val geometry: OsrmGeometry?, val distance: Double?, val duration: Double?)

@JsonClass(generateAdapter = true)
internal data class OsrmGeometry(val type: String?, val coordinates: List<List<Double>>?)

internal interface OsrmApi {
    @GET("route/v1/{profile}/{coordinates}")
    suspend fun route(
        @Path("profile") profile: String,
        @Path("coordinates") coordinates: String,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson",
    ): OsrmRouteResponse
}

/**
 * Real road/path-following polylines via OSRM's public routing API
 * (router.project-osrm.org) — not straight lines. That demo server is
 * explicitly documented by the OSRM project as unsuitable for production
 * load, so every call degrades to a straight-line [RouteResult.Fallback]
 * on any failure (timeout, non-200, malformed body) rather than crashing
 * or leaving the caller with nothing to draw — the same "degrade
 * gracefully, disclose the fallback" pattern already used for
 * storage/email elsewhere in this codebase. Deliberately its own small
 * Retrofit/OkHttp stack — OSRM is a public third-party host, not this
 * app's own authenticated backend.
 */
@Singleton
class RoutingRepository @Inject constructor() {
    private val api: OsrmApi = Retrofit.Builder()
        .baseUrl("https://router.project-osrm.org/")
        .client(
            OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .build(),
        )
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
        .build()
        .create(OsrmApi::class.java)

    suspend fun route(profile: RoutingProfile, from: GeoPoint, to: GeoPoint): RouteResult {
        val straightLine = listOf(from, to)
        return try {
            withContext(Dispatchers.IO) {
                val coordinates = "${from.longitude},${from.latitude};${to.longitude},${to.latitude}"
                val response = api.route(profile.osrmName, coordinates)
                decodeOrFallback(response, straightLine)
            }
        } catch (e: Exception) {
            RouteResult.Fallback(straightLine)
        }
    }
}

internal fun decodeOrFallback(response: OsrmRouteResponse, straightLine: List<GeoPoint>): RouteResult {
    val route = response.routes?.firstOrNull()
    val coordinates = route?.geometry?.coordinates
    if (response.code != "Ok" || coordinates.isNullOrEmpty()) {
        return RouteResult.Fallback(straightLine)
    }
    return RouteResult.Routed(
        // GeoJSON coordinates are [lon, lat] — the reverse of GeoPoint's (lat, lon) constructor order.
        points = coordinates.map { GeoPoint(it[1], it[0]) },
        distanceMeters = route.distance ?: 0.0,
        durationSeconds = route.duration ?: 0.0,
    )
}
