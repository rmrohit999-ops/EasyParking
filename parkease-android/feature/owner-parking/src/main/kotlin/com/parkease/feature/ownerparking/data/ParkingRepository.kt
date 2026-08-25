package com.parkease.feature.ownerparking.data

import com.parkease.core.model.ApprovalStatus
import com.parkease.core.model.ListingStatus
import com.parkease.core.model.Money
import com.parkease.core.model.ParkingType
import com.parkease.core.model.VehicleCategory
import com.parkease.core.model.VehicleType
import com.parkease.core.model.toEnumOrNull
import com.parkease.core.network.api.ParkingApi
import com.parkease.core.network.di.RawClient
import com.parkease.core.network.model.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

data class ListingUi(
    val id: String,
    val name: String,
    val parkingType: ParkingType?,
    val description: String?,
    val approvalStatus: ApprovalStatus?,
    val status: ListingStatus?,
)

data class LocationUi(
    val latitude: Double,
    val longitude: Double,
    val addressLine: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val entranceNotes: String?,
)

data class SectionUi(
    val id: String,
    val name: String,
    val vehicleCategory: VehicleCategory?,
    val supportedVehicleTypes: List<VehicleType>,
    val capacity: Int,
    val hourlyRate: Money,
    val isCovered: Boolean,
    val hasSecurity: Boolean,
    val hasCctv: Boolean,
    val hasEvCharging: Boolean,
    val instantModeEnabled: Boolean,
    val status: ListingStatus?,
    val approvalStatus: ApprovalStatus?,
)

data class ListingDetailUi(
    val listing: ListingUi,
    val location: LocationUi?,
    val sections: List<SectionUi>,
    val photoCount: Int,
)

data class PhotoUi(
    val id: String,
    val photoType: String,
    val sectionId: String?,
    val viewUrl: String,
)

sealed class ParkingResult<out T> {
    data class Success<T>(val value: T) : ParkingResult<T>()
    data class Error(val message: String) : ParkingResult<Nothing>()
}

private const val FRIENDLY_ERROR = "Something went wrong. Please try again."

@Singleton
class ParkingRepository @Inject constructor(
    private val parkingApi: ParkingApi,
    @RawClient private val uploadHttpClient: OkHttpClient,
) {
    suspend fun listMine(): List<ListingUi> = parkingApi.listMine().map { it.toUi() }

    suspend fun createListing(name: String, parkingType: ParkingType, description: String?): ParkingResult<ListingUi> =
        runCatchingApi {
            parkingApi.createListing(CreateListingRequest(name, parkingType.name, description?.ifBlank { null })).toUi()
        }

    suspend fun getListing(listingId: String): ParkingResult<ListingDetailUi> = runCatchingApi {
        val detail = parkingApi.getListing(listingId)
        ListingDetailUi(
            listing = ListingResponse(
                detail.id, detail.name, detail.parkingType, detail.description, detail.approvalStatus, detail.status, detail.timezone,
            ).toUi(),
            location = detail.location?.toUi(),
            sections = detail.sections.map { it.toUi() },
            photoCount = detail.photoCount,
        )
    }

    suspend fun updateListingStatus(listingId: String, status: ListingStatus): ParkingResult<ListingUi> = runCatchingApi {
        parkingApi.updateListingStatus(listingId, UpdateListingStatusRequest(status.name)).toUi()
    }

    suspend fun upsertLocation(
        listingId: String,
        latitude: Double,
        longitude: Double,
        addressLine: String,
        city: String,
        state: String,
        postalCode: String,
        entranceNotes: String?,
    ): ParkingResult<LocationUi> = runCatchingApi {
        parkingApi.upsertLocation(
            listingId,
            UpsertLocationRequest(latitude, longitude, addressLine, city, state, postalCode, entranceNotes?.ifBlank { null }),
        ).toUi()
    }

    suspend fun submitForApproval(listingId: String): ParkingResult<ListingUi> = runCatchingApi {
        parkingApi.submitForApproval(listingId).toUi()
    }

    suspend fun createSection(
        listingId: String,
        name: String,
        vehicleCategory: VehicleCategory,
        supportedVehicleTypes: List<VehicleType>,
        capacity: Int,
        hourlyRateMinorUnits: Int,
        isCovered: Boolean,
        hasSecurity: Boolean,
        hasCctv: Boolean,
        hasEvCharging: Boolean,
        instantModeEnabled: Boolean,
    ): ParkingResult<SectionUi> = runCatchingApi {
        parkingApi.createSection(
            listingId,
            CreateSectionRequest(
                name = name,
                vehicleCategory = vehicleCategory.name,
                supportedVehicleTypes = supportedVehicleTypes.map { it.name },
                capacity = capacity,
                hourlyRateMinorUnits = hourlyRateMinorUnits,
                isCovered = isCovered,
                hasSecurity = hasSecurity,
                hasCctv = hasCctv,
                hasEvCharging = hasEvCharging,
                instantModeEnabled = instantModeEnabled,
            ),
        ).toUi()
    }

    suspend fun removeSection(listingId: String, sectionId: String): ParkingResult<Unit> = runCatchingApi {
        parkingApi.removeSection(listingId, sectionId)
    }

    /**
     * Full upload flow: ask the backend for a presigned PUT URL, upload the
     * raw bytes directly to storage (never through our own API compute —
     * see StorageService on the backend), then register the resulting
     * storage key as a ParkingPhoto row. The raw (unauthenticated) OkHttp
     * client is used for the PUT step since a presigned URL carries its own
     * signature in the query string — attaching our API bearer token to a
     * request bound for the storage host would be both wrong and a token
     * leak to a third-party host.
     */
    suspend fun uploadPhoto(
        listingId: String,
        photoType: String,
        sectionId: String?,
        contentType: String,
        bytes: ByteArray,
    ): ParkingResult<PhotoUi> = runCatchingApi {
        val presigned = parkingApi.createPhotoUploadUrl(listingId, CreatePhotoUploadUrlRequest(photoType, contentType, sectionId))

        val request = Request.Builder()
            .url(presigned.uploadUrl)
            .put(bytes.toRequestBody(contentType.toMediaType()))
            .build()
        // uploadHttpClient's shared timeouts (15s connect / 20s read, OkHttp's
        // default 10s write) are tuned for small JSON API calls — a real
        // phone-camera JPEG is commonly several MB, which can easily exceed a
        // 10s write timeout on an ordinary mobile connection, failing the
        // upload with nothing more specific than "something went wrong."
        // Widened here, per-call, rather than raising the shared client's
        // timeouts globally (which also backs the token-refresh call, where a
        // short timeout is the right behavior).
        val uploadClient = uploadHttpClient.newBuilder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        // `execute()` is a BLOCKING call. uploadPhoto() is invoked from
        // viewModelScope.launch {} (Dispatchers.Main.immediate by default),
        // so without an explicit switch here this ran synchronous network
        // I/O directly on the main thread — Android throws
        // NetworkOnMainThreadException for that unconditionally, on every
        // device, before a single byte reaches the network. That exception
        // isn't an IOException, so it also skipped every specific catch
        // below and fell straight to runCatchingApi's generic handler —
        // which is why this always showed the same generic error message
        // regardless of file size or connection speed; those weren't the
        // actual cause.
        withContext(Dispatchers.IO) {
            try {
                uploadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw UploadFailedException("The photo upload didn't complete (server responded ${response.code}). Please try again.")
                    }
                }
            } catch (e: SocketTimeoutException) {
                throw UploadFailedException("The upload timed out. Please try again on a stronger connection.")
            } catch (e: UnknownHostException) {
                throw UploadFailedException("Couldn't reach the photo server — please check your internet connection.")
            } catch (e: SSLException) {
                throw UploadFailedException("A secure connection to the photo server couldn't be established. Please try again.")
            } catch (e: IOException) {
                throw UploadFailedException("The photo upload failed (${e.javaClass.simpleName}). Please try again.")
            }
        }

        val registered = parkingApi.registerPhoto(listingId, RegisterPhotoRequest(presigned.storageKey, photoType, sectionId))
        PhotoUi(id = registered.id, photoType = registered.photoType, sectionId = registered.sectionId, viewUrl = "")
    }

    suspend fun listPhotos(listingId: String): List<PhotoUi> =
        parkingApi.listPhotos(listingId).map { PhotoUi(it.id, it.photoType, it.sectionId, it.viewUrl) }

    suspend fun removePhoto(listingId: String, photoId: String): ParkingResult<Unit> = runCatchingApi {
        parkingApi.removePhoto(listingId, photoId)
    }

    private inline fun <T> runCatchingApi(block: () -> T): ParkingResult<T> = try {
        ParkingResult.Success(block())
    } catch (e: retrofit2.HttpException) {
        ParkingResult.Error(
            when (e.code()) {
                400 -> e.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: FRIENDLY_ERROR
                403 -> "You don't have permission to do that."
                404 -> "We couldn't find that."
                409 -> "That can't be done right now — please check the current status and try again."
                else -> FRIENDLY_ERROR
            },
        )
    } catch (e: UploadFailedException) {
        ParkingResult.Error(e.message ?: FRIENDLY_ERROR)
    } catch (e: Exception) {
        ParkingResult.Error(FRIENDLY_ERROR)
    }
}

private class UploadFailedException(message: String) : Exception(message)

private fun ListingResponse.toUi() = ListingUi(
    id = id,
    name = name,
    parkingType = parkingType.toEnumOrNull<ParkingType>(),
    description = description,
    approvalStatus = approvalStatus.toEnumOrNull<ApprovalStatus>(),
    status = status.toEnumOrNull<ListingStatus>(),
)

private fun LocationResponse.toUi() = LocationUi(
    latitude = latitude,
    longitude = longitude,
    addressLine = addressLine,
    city = city,
    state = state,
    postalCode = postalCode,
    entranceNotes = entranceNotes,
)

private fun SectionResponse.toUi() = SectionUi(
    id = id,
    name = name,
    vehicleCategory = vehicleCategory.toEnumOrNull<VehicleCategory>(),
    supportedVehicleTypes = supportedVehicleTypes.mapNotNull { it.toEnumOrNull<VehicleType>() },
    capacity = capacity,
    hourlyRate = Money.of(hourlyRateMinorUnits.toLong(), currency),
    isCovered = isCovered,
    hasSecurity = hasSecurity,
    hasCctv = hasCctv,
    hasEvCharging = hasEvCharging,
    instantModeEnabled = instantModeEnabled,
    status = status.toEnumOrNull<ListingStatus>(),
    approvalStatus = approvalStatus.toEnumOrNull<ApprovalStatus>(),
)
