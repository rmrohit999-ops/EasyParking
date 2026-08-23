package com.parkease.feature.vehicles.data

import com.parkease.core.model.VehicleCategory
import com.parkease.core.model.VehicleSize
import com.parkease.core.model.VehicleType
import com.parkease.core.model.toEnumOrNull
import com.parkease.core.network.api.VehiclesApi
import com.parkease.core.network.model.CreateVehicleRequest
import javax.inject.Inject
import javax.inject.Singleton

data class VehicleUi(
    val id: String,
    val category: VehicleCategory?,
    val vehicleType: VehicleType?,
    val size: VehicleSize?,
    val registrationNumber: String,
    val make: String?,
    val model: String?,
    val isDefault: Boolean,
) {
    val displayName: String
        get() = listOfNotNull(make, model).joinToString(" ").ifBlank { registrationNumber }
}

sealed class VehiclesResult {
    data object Success : VehiclesResult()
    data class Error(val message: String) : VehiclesResult()
}

@Singleton
class VehiclesRepository @Inject constructor(
    private val vehiclesApi: VehiclesApi,
) {
    suspend fun list(): List<VehicleUi> = vehiclesApi.list().map {
        VehicleUi(
            id = it.id,
            category = it.category.toEnumOrNull<VehicleCategory>(),
            vehicleType = it.vehicleType.toEnumOrNull<VehicleType>(),
            size = it.size.toEnumOrNull<VehicleSize>(),
            registrationNumber = it.registrationNumber,
            make = it.make,
            model = it.model,
            isDefault = it.isDefault,
        )
    }

    suspend fun addVehicle(
        category: VehicleCategory,
        vehicleType: VehicleType,
        registrationNumber: String,
        make: String?,
        model: String?,
        setAsDefault: Boolean,
    ): VehiclesResult = try {
        vehiclesApi.create(
            CreateVehicleRequest(
                category = category.name,
                vehicleType = vehicleType.name,
                registrationNumber = registrationNumber,
                make = make,
                model = model,
                setAsDefault = setAsDefault,
            ),
        )
        VehiclesResult.Success
    } catch (e: retrofit2.HttpException) {
        VehiclesResult.Error(
            when (e.code()) {
                400 -> "That doesn't look like a valid vehicle registration number. Please check and try again."
                409 -> "You already have this vehicle registered."
                else -> "We couldn't add that vehicle right now. Please try again."
            },
        )
    } catch (e: Exception) {
        VehiclesResult.Error("We couldn't add that vehicle right now. Please try again.")
    }

    suspend fun setDefault(vehicleId: String) {
        vehiclesApi.setDefault(vehicleId)
    }

    suspend fun remove(vehicleId: String) {
        vehiclesApi.remove(vehicleId)
    }
}
