package com.parkease.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TokenPairResponse(
    @Json(name = "accessToken") val accessToken: String,
    @Json(name = "refreshToken") val refreshToken: String,
    @Json(name = "expiresInSeconds") val expiresInSeconds: Long,
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val fullName: String,
    val email: String? = null,
    val phone: String? = null,
    val password: String? = null,
)

@JsonClass(generateAdapter = true)
data class LoginRequest(val email: String, val password: String)

@JsonClass(generateAdapter = true)
data class OtpRequestRequest(val phone: String, val purpose: String)

@JsonClass(generateAdapter = true)
data class OtpRequestResponse(val expiresInSeconds: Long)

@JsonClass(generateAdapter = true)
data class OtpVerifyRequest(val phone: String, val purpose: String, val code: String)

@JsonClass(generateAdapter = true)
data class GoogleSignInRequest(val idToken: String)

@JsonClass(generateAdapter = true)
data class RefreshRequest(val refreshToken: String)

@JsonClass(generateAdapter = true)
data class ForgotPasswordRequest(val email: String)

@JsonClass(generateAdapter = true)
data class ResetPasswordRequest(val resetToken: String, val newPassword: String)

@JsonClass(generateAdapter = true)
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@JsonClass(generateAdapter = true)
data class SessionSummaryResponse(
    val id: String,
    val deviceInfo: String?,
    val ipAddress: String?,
    val issuedAt: String,
    val expiresAt: String,
)

@JsonClass(generateAdapter = true)
data class UserProfileResponse(
    val id: String,
    val email: String?,
    val phone: String?,
    val profilePhotoUrl: String?,
    val status: String,
    val roles: List<String>,
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class ApiErrorBody(
    val error: ApiErrorDetail,
)

@JsonClass(generateAdapter = true)
data class ApiErrorDetail(
    val code: String,
    val message: String,
    val correlationId: String?,
)
