package com.parkease.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE_NAME = "parkease_session_encrypted"
private const val KEY_ACCESS_TOKEN = "access_token"
private const val KEY_REFRESH_TOKEN = "refresh_token"
private const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"

@Singleton
class EncryptedSessionStore @Inject constructor(
    @ApplicationContext context: Context,
) : SessionStore {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _isLoggedIn = MutableStateFlow(false)
    override val isLoggedIn: StateFlow<Boolean> get() = _isLoggedIn

    init {
        _isLoggedIn.value = currentRefreshTokenBlocking() != null
    }

    override fun currentAccessTokenBlocking(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    override fun currentRefreshTokenBlocking(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    override suspend fun saveTokens(tokens: SessionTokens) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
            .putLong(KEY_ACCESS_EXPIRES_AT, tokens.accessTokenExpiresAtEpochMillis)
            .apply()
        _isLoggedIn.value = true
    }

    override suspend fun clear() {
        prefs.edit().clear().apply()
        _isLoggedIn.value = false
    }
}
