package com.parkease.core.network.api

import com.parkease.core.network.model.*
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Mirrors parkease-backend's AuthController (src/modules/auth/auth.controller.ts) 1:1. */
interface AuthApi {
    @POST("v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): TokenPairResponse

    @POST("v1/auth/login")
    suspend fun login(@Body body: LoginRequest): TokenPairResponse

    @POST("v1/auth/otp/request")
    suspend fun requestOtp(@Body body: OtpRequestRequest): OtpRequestResponse

    @POST("v1/auth/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyRequest): TokenPairResponse

    @POST("v1/auth/google")
    suspend fun googleSignIn(@Body body: GoogleSignInRequest): TokenPairResponse

    @POST("v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenPairResponse

    @POST("v1/auth/logout")
    suspend fun logout()

    @POST("v1/auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest)

    @POST("v1/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest)

    @POST("v1/auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    @GET("v1/auth/sessions")
    suspend fun listSessions(): List<SessionSummaryResponse>

    @DELETE("v1/auth/sessions/{id}")
    suspend fun revokeSession(@Path("id") sessionId: String)

    @DELETE("v1/auth/account")
    suspend fun deleteAccount()
}
