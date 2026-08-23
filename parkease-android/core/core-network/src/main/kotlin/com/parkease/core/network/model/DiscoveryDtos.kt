package com.parkease.core.network.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchSectionResultResponse(
    val id: String,
    val name: String,
    val vehicleCategory: String,
    val supportedVehicleTypes: List<String>,
    val currency: String,
    val hourlyRateMinorUnits: Int,
    val isCovered: Boolean,
    val hasSecurity: Boolean,
    val hasCctv: Boolean,
    val hasEvCharging: Boolean,
    val instantModeEnabled: Boolean,
    val availableCount: Int,
)

@JsonClass(generateAdapter = true)
data class SearchListingResultResponse(
    val id: String,
    val name: String,
    val parkingType: String,
    val addressLine: String,
    val city: String,
    val distanceMeters: Double,
    val sections: List<SearchSectionResultResponse>,
)

@JsonClass(generateAdapter = true)
data class SearchResponse(
    val page: Int,
    val pageSize: Int,
    val totalListings: Int,
    val results: List<SearchListingResultResponse>,
)

@JsonClass(generateAdapter = true)
data class FavoriteListingResponse(
    val id: String,
    val name: String,
    val parkingType: String,
    val status: String,
    val city: String?,
    val favoritedAt: String,
)
