package com.parkease.core.network.interceptor

import com.parkease.core.datastore.SessionStore
import com.parkease.core.network.ApiConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID
import javax.inject.Inject

/** Attaches the current access token (if any) and a correlation ID to every request. */
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header(ApiConfig.CORRELATION_ID_HEADER, UUID.randomUUID().toString())

        sessionStore.currentAccessTokenBlocking()?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(builder.build())
    }
}
