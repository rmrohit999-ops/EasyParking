package com.parkease.core.network.api

import com.parkease.core.network.model.*
import retrofit2.http.*

interface ParkingApi {
    @POST("v1/parking-listings")
    suspend fun createListing(@Body body: CreateListingRequest): ListingResponse

    @GET("v1/parking-listings")
    suspend fun listMine(): List<ListingResponse>

    @GET("v1/parking-listings/{listingId}")
    suspend fun getListing(@Path("listingId") listingId: String): ListingDetailResponse

    @PATCH("v1/parking-listings/{listingId}")
    suspend fun updateListing(@Path("listingId") listingId: String, @Body body: UpdateListingRequest): ListingResponse

    @PATCH("v1/parking-listings/{listingId}/status")
    suspend fun updateListingStatus(
        @Path("listingId") listingId: String,
        @Body body: UpdateListingStatusRequest,
    ): ListingResponse

    @PUT("v1/parking-listings/{listingId}/location")
    suspend fun upsertLocation(
        @Path("listingId") listingId: String,
        @Body body: UpsertLocationRequest,
    ): LocationResponse

    @POST("v1/parking-listings/{listingId}/submit-for-approval")
    suspend fun submitForApproval(@Path("listingId") listingId: String): ListingResponse

    @POST("v1/parking-listings/{listingId}/sections")
    suspend fun createSection(
        @Path("listingId") listingId: String,
        @Body body: CreateSectionRequest,
    ): SectionResponse

    @GET("v1/parking-listings/{listingId}/sections")
    suspend fun listSections(@Path("listingId") listingId: String): List<SectionResponse>

    @PATCH("v1/parking-listings/{listingId}/sections/{sectionId}")
    suspend fun updateSection(
        @Path("listingId") listingId: String,
        @Path("sectionId") sectionId: String,
        @Body body: UpdateSectionRequest,
    ): SectionResponse

    @PATCH("v1/parking-listings/{listingId}/sections/{sectionId}/status")
    suspend fun updateSectionStatus(
        @Path("listingId") listingId: String,
        @Path("sectionId") sectionId: String,
        @Body body: UpdateSectionStatusRequest,
    ): SectionResponse

    @DELETE("v1/parking-listings/{listingId}/sections/{sectionId}")
    suspend fun removeSection(@Path("listingId") listingId: String, @Path("sectionId") sectionId: String)

    @POST("v1/parking-listings/{listingId}/photos/upload-url")
    suspend fun createPhotoUploadUrl(
        @Path("listingId") listingId: String,
        @Body body: CreatePhotoUploadUrlRequest,
    ): PresignedUploadResponse

    @POST("v1/parking-listings/{listingId}/photos")
    suspend fun registerPhoto(
        @Path("listingId") listingId: String,
        @Body body: RegisterPhotoRequest,
    ): RegisterPhotoResponse

    @GET("v1/parking-listings/{listingId}/photos")
    suspend fun listPhotos(@Path("listingId") listingId: String): List<PhotoResponse>

    @DELETE("v1/parking-listings/{listingId}/photos/{photoId}")
    suspend fun removePhoto(@Path("listingId") listingId: String, @Path("photoId") photoId: String)
}
