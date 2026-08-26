package com.parkease.feature.driversearch.data

import com.parkease.core.model.Money
import com.parkease.core.model.VehicleCategory
import com.parkease.core.model.toEnumOrNull
import com.parkease.core.network.api.DiscoveryApi
import com.parkease.core.network.api.VehiclesApi
import javax.inject.Inject
import javax.inject.Singleton

data class SearchFilters(
    val instantOnly: Boolean = false,
    val covered: Boolean = false,
    val hasSecurity: Boolean = false,
    val hasCctv: Boolean = false,
    val hasEvCharging: Boolean = false,
)

data class SectionResultUi(
    val id: String,
    val name: String,
    val vehicleCategory: VehicleCategory?,
    val hourlyRate: Money,
    val isCovered: Boolean,
    val hasSecurity: Boolean,
    val hasCctv: Boolean,
    val hasEvCharging: Boolean,
    val instantModeEnabled: Boolean,
    val availableCount: Int,
)

data class ListingResultUi(
    val id: String,
    val name: String,
    val parkingType: String,
    val addressLine: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double,
    val sections: List<SectionResultUi>,
    val primaryPhotoUrl: String?,
    val isFavorite: Boolean = false,
    /** Real average of driver-submitted reviews — null when ratingCount is 0 (never a fabricated default rating). */
    val averageRating: Double? = null,
    val ratingCount: Int = 0,
)

data class FavoriteUi(
    val id: String,
    val name: String,
    val city: String?,
    val status: String,
)

sealed class DiscoveryResult<out T> {
    data class Success<T>(val value: T) : DiscoveryResult<T>()
    data class Error(val message: String) : DiscoveryResult<Nothing>()
}

private const val FRIENDLY_ERROR = "Something went wrong. Please try again."

@Singleton
class DiscoveryRepository @Inject constructor(
    private val discoveryApi: DiscoveryApi,
    private val vehiclesApi: VehiclesApi,
) {
    /**
     * Null if the driver has no vehicles yet, or the lookup itself failed
     * (network error, or an account with no DRIVER role) — either way the
     * caller falls back to its own "no vehicle" empty state rather than
     * letting an unhandled exception crash the search coroutine.
     */
    suspend fun getDefaultVehicle(): Pair<String, VehicleCategory>? = try {
        val vehicles = vehiclesApi.list()
        val default = vehicles.firstOrNull { it.isDefault } ?: vehicles.firstOrNull()
        val category = default?.category?.toEnumOrNull<VehicleCategory>()
        if (default != null && category != null) default.id to category else null
    } catch (e: Exception) {
        null
    }

    suspend fun search(
        lat: Double,
        lng: Double,
        vehicleId: String?,
        category: VehicleCategory?,
        radiusMeters: Int,
        filters: SearchFilters,
        favoriteIds: Set<String>,
    ): DiscoveryResult<List<ListingResultUi>> = try {
        val response = discoveryApi.search(
            lat = lat,
            lng = lng,
            radiusMeters = radiusMeters,
            vehicleId = vehicleId,
            category = category?.name,
            instantOnly = filters.instantOnly.takeIf { it },
            covered = filters.covered.takeIf { it },
            hasSecurity = filters.hasSecurity.takeIf { it },
            hasCctv = filters.hasCctv.takeIf { it },
            hasEvCharging = filters.hasEvCharging.takeIf { it },
        )
        DiscoveryResult.Success(
            response.results.map { listing ->
                ListingResultUi(
                    id = listing.id,
                    name = listing.name,
                    parkingType = listing.parkingType,
                    addressLine = listing.addressLine,
                    city = listing.city,
                    latitude = listing.latitude,
                    longitude = listing.longitude,
                    distanceMeters = listing.distanceMeters,
                    primaryPhotoUrl = listing.primaryPhotoUrl,
                    isFavorite = listing.id in favoriteIds,
                    averageRating = listing.averageRating,
                    ratingCount = listing.ratingCount,
                    sections = listing.sections.map { s ->
                        SectionResultUi(
                            id = s.id,
                            name = s.name,
                            vehicleCategory = s.vehicleCategory.toEnumOrNull<VehicleCategory>(),
                            hourlyRate = Money.of(s.hourlyRateMinorUnits.toLong(), s.currency),
                            isCovered = s.isCovered,
                            hasSecurity = s.hasSecurity,
                            hasCctv = s.hasCctv,
                            hasEvCharging = s.hasEvCharging,
                            instantModeEnabled = s.instantModeEnabled,
                            availableCount = s.availableCount,
                        )
                    },
                )
            },
        )
    } catch (e: retrofit2.HttpException) {
        DiscoveryResult.Error(if (e.code() == 403) "That vehicle doesn't belong to you." else FRIENDLY_ERROR)
    } catch (e: Exception) {
        DiscoveryResult.Error(FRIENDLY_ERROR)
    }

    suspend fun listFavoriteIds(): Set<String> = try {
        discoveryApi.listFavorites().map { it.id }.toSet()
    } catch (e: Exception) {
        emptySet()
    }

    suspend fun listFavorites(): DiscoveryResult<List<FavoriteUi>> = try {
        DiscoveryResult.Success(
            discoveryApi.listFavorites().map { FavoriteUi(id = it.id, name = it.name, city = it.city, status = it.status) },
        )
    } catch (e: Exception) {
        DiscoveryResult.Error(FRIENDLY_ERROR)
    }

    suspend fun toggleFavorite(listingId: String, currentlyFavorite: Boolean): Boolean = try {
        if (currentlyFavorite) discoveryApi.removeFavorite(listingId) else discoveryApi.addFavorite(listingId)
        true
    } catch (e: Exception) {
        false
    }
}
