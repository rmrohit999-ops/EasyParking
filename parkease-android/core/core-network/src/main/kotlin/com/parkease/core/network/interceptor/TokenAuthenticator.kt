package com.parkease.core.network.interceptor

import com.parkease.core.datastore.SessionStore
import com.parkease.core.datastore.SessionTokens
import com.parkease.core.network.api.AuthApi
import com.parkease.core.network.di.RawClient
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider

/**
 * Runs when a request comes back 401. Uses a raw AuthApi (built on a plain
 * client with no AuthInterceptor/Authenticator of its own — injected as a
 * Provider to avoid a circular Retrofit/OkHttp dependency graph) to rotate
 * the refresh token exactly once, then retries the original request with
 * the new access token. If refresh itself fails (expired/stolen/revoked
 * refresh token), the session is cleared — the app's root nav observes
 * SessionStore.isLoggedIn and routes back to the auth graph.
 */
class TokenAuthenticator @Inject constructor(
    private val sessionStore: SessionStore,
    @RawClient private val rawAuthApiProvider: Provider<AuthApi>,
) : Authenticator {

    override fun authenticate(route: Route?, response: okhttp3.Response): Request? {
        // Never retry more than once for the same request.
        if (responseCount(response) >= 2) return null

        val refreshToken = sessionStore.currentRefreshTokenBlocking() ?: return null

        return runBlocking {
            try {
                val newTokens = rawAuthApiProvider.get().refresh(
                    com.parkease.core.network.model.RefreshRequest(refreshToken),
                )
                sessionStore.saveTokens(
                    SessionTokens(
                        accessToken = newTokens.accessToken,
                        refreshToken = newTokens.refreshToken,
                        accessTokenExpiresAtEpochMillis =
                            System.currentTimeMillis() + newTokens.expiresInSeconds * 1000,
                    ),
                )
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${newTokens.accessToken}")
                    .build()
            } catch (e: Exception) {
                sessionStore.clear()
                null
            }
        }
    }

    private fun responseCount(response: okhttp3.Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
