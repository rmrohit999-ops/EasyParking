package com.parkease.core.network.api

import com.parkease.core.network.model.UserProfileResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateProfileRequest(val fullName: String? = null, val profilePhotoUrl: String? = null)

@JsonClass(generateAdapter = true)
data class BecomeOwnerRequest(val businessName: String? = null)

interface UsersApi {
    @GET("v1/users/me")
    suspend fun getMe(): UserProfileResponse

    @PATCH("v1/users/me")
    suspend fun updateMe(@Body body: UpdateProfileRequest): UserProfileResponse

    @POST("v1/users/me/roles/owner")
    suspend fun becomeOwner(@Body body: BecomeOwnerRequest): UserProfileResponse
}
