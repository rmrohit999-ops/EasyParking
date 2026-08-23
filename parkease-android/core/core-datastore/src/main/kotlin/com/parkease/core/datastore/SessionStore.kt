package com.parkease.core.datastore

import kotlinx.coroutines.flow.Flow

data class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAtEpochMillis: Long,
)

/**
 * Session persistence contract. Real implementation (EncryptedSessionStore)
 * uses androidx.security-crypto's EncryptedSharedPreferences (AES-256-GCM,
 * Android Keystore-backed master key) — access/refresh tokens are the most
 * sensitive thing this app stores locally and are never written to plain
 * SharedPreferences, Room, or logs.
 */
interface SessionStore {
    /** Synchronous read for OkHttp's Interceptor/Authenticator, which run on non-coroutine threads. */
    fun currentAccessTokenBlocking(): String?
    fun currentRefreshTokenBlocking(): String?

    suspend fun saveTokens(tokens: SessionTokens)
    suspend fun clear()

    val isLoggedIn: Flow<Boolean>
}
