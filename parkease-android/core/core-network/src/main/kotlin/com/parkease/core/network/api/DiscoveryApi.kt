package com.parkease.core.network.api

import com.parkease.core.network.model.FavoriteListingResponse
import com.parkease.core.network.model.ListingReviewsResponse
import com.parkease.core.network.model.SearchResponse
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface DiscoveryApi {
    @GET("v1/search/parking")
    suspend fun search(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radiusMeters") radiusMeters: Int? = null,
        @Query("vehicleId") vehicleId: String? = null,
        @Query("category") category: String? = null,
        @Query("instantOnly") instantOnly: Boolean? = null,
        @Query("covered") covered: Boolean? = null,
        @Query("hasSecurity") hasSecurity: Boolean? = null,
        @Query("hasCctv") hasCctv: Boolean? = null,
        @Query("hasEvCharging") hasEvCharging: Boolean? = null,
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
    ): SearchResponse

    @POST("v1/favorites/{listingId}")
    suspend fun addFavorite(@Path("listingId") listingId: String)

    @DELETE("v1/favorites/{listingId}")
    suspend fun removeFavorite(@Path("listingId") listingId: String)

    @GET("v1/favorites")
    suspend fun listFavorites(): List<FavoriteListingResponse>

    @GET("v1/parking/{listingId}/reviews")
    suspend fun listReviews(
        @Path("listingId") listingId: String,
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
    ): ListingReviewsResponse
}
