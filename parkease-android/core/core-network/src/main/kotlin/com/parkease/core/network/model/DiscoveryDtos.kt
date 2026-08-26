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
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double,
    val sections: List<SearchSectionResultResponse>,
    /** Presigned read URL for the listing's first active photo — null if it has none, or if storage isn't configured on this deployment. */
    val primaryPhotoUrl: String?,
    /** Real average of driver-submitted reviews' `overall` rating, rounded to 1 decimal — null when ratingCount is 0, never a fabricated default. */
    val averageRating: Double?,
    val ratingCount: Int,
)

@JsonClass(generateAdapter = true)
data class ReviewResponse(
    val id: String,
    val ratings: Map<String, Double?>,
    val comment: String?,
    val createdAt: String,
    val reviewerLabel: String,
)

@JsonClass(generateAdapter = true)
data class ListingReviewsResponse(
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val results: List<ReviewResponse>,
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
