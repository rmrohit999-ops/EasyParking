package com.parkease.core.network.model

import com.squareup.moshi.JsonClass

/** category/vehicleType/size values are the same enum strings as core-model's
 * VehicleCategory/VehicleType/VehicleSize and the backend's Prisma enums —
 * kept as plain String here (rather than importing core-model's enum types
 * directly into Moshi-parsed DTOs) so a value the client doesn't yet
 * recognize (future category added server-side) fails soft instead of
 * crashing JSON parsing; UI code maps String -> core-model enum with a
 * safe fallback. */
@JsonClass(generateAdapter = true)
data class VehicleResponse(
    val id: String,
    val category: String,
    val vehicleType: String,
    val size: String?,
    val registrationNumber: String,
    val make: String?,
    val model: String?,
    val isDefault: Boolean,
)

@JsonClass(generateAdapter = true)
data class CreateVehicleRequest(
    val category: String,
    val vehicleType: String,
    val size: String? = null,
    val registrationNumber: String,
    val make: String? = null,
    val model: String? = null,
    val setAsDefault: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateVehicleRequest(
    val vehicleType: String? = null,
    val size: String? = null,
    val make: String? = null,
    val model: String? = null,
)
