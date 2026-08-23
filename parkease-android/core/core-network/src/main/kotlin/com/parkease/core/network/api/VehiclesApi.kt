package com.parkease.core.network.api

import com.parkease.core.network.model.CreateVehicleRequest
import com.parkease.core.network.model.UpdateVehicleRequest
import com.parkease.core.network.model.VehicleResponse
import retrofit2.http.*

interface VehiclesApi {
    @GET("v1/vehicles")
    suspend fun list(): List<VehicleResponse>

    @POST("v1/vehicles")
    suspend fun create(@Body body: CreateVehicleRequest): VehicleResponse

    @PATCH("v1/vehicles/{vehicleId}")
    suspend fun update(@Path("vehicleId") vehicleId: String, @Body body: UpdateVehicleRequest): VehicleResponse

    @DELETE("v1/vehicles/{vehicleId}")
    suspend fun remove(@Path("vehicleId") vehicleId: String)

    @POST("v1/vehicles/{vehicleId}/default")
    suspend fun setDefault(@Path("vehicleId") vehicleId: String)
}
