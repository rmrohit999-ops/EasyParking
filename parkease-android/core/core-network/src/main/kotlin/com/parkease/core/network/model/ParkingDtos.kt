package com.parkease.core.network.model

import com.squareup.moshi.JsonClass

// As with VehicleDtos.kt, enum-shaped fields (parkingType/vehicleCategory/
// status/approvalStatus/...) are plain String here — never a Moshi-parsed
// Kotlin enum directly — so a value this app build doesn't yet recognize
// fails soft (mapped to null/UNKNOWN in the UI layer) instead of crashing
// JSON parsing for the whole response.

@JsonClass(generateAdapter = true)
data class CreateListingRequest(
    val name: String,
    val parkingType: String,
    val description: String? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateListingRequest(
    val name: String? = null,
    val description: String? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateListingStatusRequest(val status: String)

@JsonClass(generateAdapter = true)
data class ListingResponse(
    val id: String,
    val name: String,
    val parkingType: String,
    val description: String?,
    val approvalStatus: String,
    val status: String,
    val timezone: String,
)

@JsonClass(generateAdapter = true)
data class ListingDetailResponse(
    val id: String,
    val name: String,
    val parkingType: String,
    val description: String?,
    val approvalStatus: String,
    val status: String,
    val timezone: String,
    val location: LocationResponse?,
    val sections: List<SectionResponse>,
    val photoCount: Int,
)

@JsonClass(generateAdapter = true)
data class UpsertLocationRequest(
    val latitude: Double,
    val longitude: Double,
    val addressLine: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val entranceNotes: String? = null,
    val locationAccuracyMeters: Double? = null,
)

@JsonClass(generateAdapter = true)
data class LocationResponse(
    val latitude: Double,
    val longitude: Double,
    val addressLine: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val entranceNotes: String?,
    val locationAccuracyMeters: Double?,
)

@JsonClass(generateAdapter = true)
data class CreateSectionRequest(
    val name: String,
    val vehicleCategory: String,
    val supportedVehicleTypes: List<String>,
    val capacity: Int,
    val hourlyRateMinorUnits: Int,
    val isCovered: Boolean? = null,
    val hasSecurity: Boolean? = null,
    val hasCctv: Boolean? = null,
    val hasEvCharging: Boolean? = null,
    val instantModeEnabled: Boolean? = null,
    val locationNotes: String? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateSectionRequest(
    val name: String? = null,
    val supportedVehicleTypes: List<String>? = null,
    val capacity: Int? = null,
    val hourlyRateMinorUnits: Int? = null,
    val isCovered: Boolean? = null,
    val hasSecurity: Boolean? = null,
    val hasCctv: Boolean? = null,
    val hasEvCharging: Boolean? = null,
    val instantModeEnabled: Boolean? = null,
    val locationNotes: String? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateSectionStatusRequest(val status: String)

@JsonClass(generateAdapter = true)
data class SectionResponse(
    val id: String,
    val name: String,
    val vehicleCategory: String,
    val supportedVehicleTypes: List<String>,
    val capacity: Int,
    val currency: String,
    val hourlyRateMinorUnits: Int,
    val isCovered: Boolean,
    val hasSecurity: Boolean,
    val hasCctv: Boolean,
    val hasEvCharging: Boolean,
    val instantModeEnabled: Boolean,
    val status: String,
    val approvalStatus: String,
    val locationNotes: String?,
)

@JsonClass(generateAdapter = true)
data class CreatePhotoUploadUrlRequest(
    val photoType: String,
    val contentType: String,
    val sectionId: String? = null,
)

@JsonClass(generateAdapter = true)
data class PresignedUploadResponse(
    val uploadUrl: String,
    val storageKey: String,
    val expiresInSeconds: Int,
)

@JsonClass(generateAdapter = true)
data class RegisterPhotoRequest(
    val storageKey: String,
    val photoType: String,
    val sectionId: String? = null,
)

@JsonClass(generateAdapter = true)
data class RegisterPhotoResponse(
    val id: String,
    val photoType: String,
    val sectionId: String?,
    val uploadedAt: String,
)

@JsonClass(generateAdapter = true)
data class PhotoResponse(
    val id: String,
    val photoType: String,
    val sectionId: String?,
    val uploadedAt: String,
    val viewUrl: String,
)
