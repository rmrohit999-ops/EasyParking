package com.parkease.feature.auth.data

import com.parkease.core.datastore.SessionStore
import com.parkease.core.datastore.SessionTokens
import com.parkease.core.network.api.AuthApi
import com.parkease.core.network.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    data object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Thin wrapper over AuthApi that also persists the resulting session. Every
 * method here maps a thrown exception to a user-facing AuthResult.Error
 * with a plain-language message — screens never show a raw HTTP status or
 * exception message, per Milestone 0's error-handling requirement.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val sessionStore: SessionStore,
) {
    val isLoggedIn: Flow<Boolean> get() = sessionStore.isLoggedIn

    suspend fun requestOtp(phone: String, purpose: String): AuthResult = runCatchingAuth {
        authApi.requestOtp(OtpRequestRequest(phone, purpose))
    }

    suspend fun verifyOtp(phone: String, purpose: String, code: String): AuthResult = runCatchingAuth {
        val tokens = authApi.verifyOtp(OtpVerifyRequest(phone, purpose, code))
        persist(tokens)
    }

    suspend fun login(email: String, password: String): AuthResult = runCatchingAuth {
        val tokens = authApi.login(LoginRequest(email, password))
        persist(tokens)
    }

    suspend fun register(fullName: String, email: String?, phone: String?, password: String?): AuthResult =
        runCatchingAuth {
            val tokens = authApi.register(RegisterRequest(fullName, email, phone, password))
            persist(tokens)
        }

    suspend fun googleSignIn(idToken: String): AuthResult = runCatchingAuth {
        val tokens = authApi.googleSignIn(GoogleSignInRequest(idToken))
        persist(tokens)
    }

    suspend fun forgotPassword(email: String): AuthResult = runCatchingAuth {
        authApi.forgotPassword(ForgotPasswordRequest(email))
    }

    suspend fun logout() {
        runCatching { authApi.logout() }
        sessionStore.clear()
    }

    private suspend fun persist(tokens: TokenPairResponse) {
        sessionStore.saveTokens(
            SessionTokens(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                accessTokenExpiresAtEpochMillis = System.currentTimeMillis() + tokens.expiresInSeconds * 1000,
            ),
        )
    }

    private suspend fun runCatchingAuth(block: suspend () -> Unit): AuthResult = try {
        block()
        AuthResult.Success
    } catch (e: retrofit2.HttpException) {
        AuthResult.Error(friendlyMessageFor(e))
    } catch (e: java.io.IOException) {
        AuthResult.Error("We couldn't reach ParkEase. Please check your connection and try again.")
    } catch (e: Exception) {
        AuthResult.Error("We couldn't complete that right now. Please try again.")
    }

    private fun friendlyMessageFor(e: retrofit2.HttpException): String = when (e.code()) {
        401 -> "Incorrect email or password."
        403 -> "You do not have permission to perform this action."
        409 -> "An account with those details already exists."
        429 -> "Too many attempts. Please wait a moment and try again."
        else -> "We couldn't complete that right now. Please try again."
    }
}
