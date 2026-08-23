package com.parkease.core.network

/**
 * Placeholder for the Retrofit/OkHttp client setup. Real implementation
 * (auth-header interceptor with token refresh, correlation-ID header,
 * certificate pinning, base URL from BuildConfig.API_BASE_URL) lands in
 * Milestone 2 alongside the auth module, since it needs the session token
 * holder that milestone introduces. Declared now only as the module's
 * anchor package.
 */
object ApiConfig {
    const val CORRELATION_ID_HEADER = "x-correlation-id"
}
