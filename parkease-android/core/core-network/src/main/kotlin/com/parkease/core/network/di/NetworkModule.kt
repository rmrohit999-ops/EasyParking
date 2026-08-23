package com.parkease.core.network.di

import com.parkease.core.network.api.AttendantApi
import com.parkease.core.network.api.AuthApi
import com.parkease.core.network.api.BookingApi
import com.parkease.core.network.api.DiscoveryApi
import com.parkease.core.network.api.EarningsApi
import com.parkease.core.network.api.NotificationsApi
import com.parkease.core.network.api.ParkingApi
import com.parkease.core.network.api.PaymentsApi
import com.parkease.core.network.api.QrApi
import com.parkease.core.network.api.RefundsApi
import com.parkease.core.network.api.UsersApi
import com.parkease.core.network.api.VehiclesApi
import com.parkease.core.network.interceptor.AuthInterceptor
import com.parkease.core.network.interceptor.TokenAuthenticator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RawClient

/**
 * Two OkHttp/Retrofit stacks on purpose:
 *  - the "raw" one carries no AuthInterceptor/TokenAuthenticator, used only
 *    for the refresh-token call itself — attaching the authenticator to the
 *    client that performs token refresh would recurse on a 401 from the
 *    refresh endpoint.
 *  - the default one is what every real feature-module API interface uses.
 *
 * Base URL comes from the app module's BuildConfig.API_BASE_URL per flavor
 * (dev/staging/prod) — provided here via a plain @Named("apiBaseUrl") String
 * that the app module's own Hilt module supplies, keeping core-network
 * flavor-agnostic.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            // BASIC only: method/URL/status/duration. Never HEADERS or BODY —
            // those would risk logging Authorization tokens or PII.
            level = HttpLoggingInterceptor.Level.BASIC
        }

    @Provides
    @Singleton
    @RawClient
    fun provideRawOkHttpClient(logging: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        logging: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .authenticator(tokenAuthenticator)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @RawClient
    fun provideRawRetrofit(
        @RawClient client: OkHttpClient,
        moshi: Moshi,
        @Named("apiBaseUrl") apiBaseUrl: String,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(apiBaseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        moshi: Moshi,
        @Named("apiBaseUrl") apiBaseUrl: String,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(apiBaseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    // @RawClient-qualified: Dagger/Hilt automatically satisfies any injection
    // point declared as `Provider<AuthApi>` (or `@RawClient Provider<AuthApi>`)
    // from this binding — that's how TokenAuthenticator gets the *raw*
    // client's AuthApi (see its @Inject constructor) without a hand-written
    // Provider<T> method here, and without colliding with the unqualified
    // provideAuthApi() binding below.
    @Provides
    @Singleton
    @RawClient
    fun provideRawAuthApi(@RawClient retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideUsersApi(retrofit: Retrofit): UsersApi = retrofit.create(UsersApi::class.java)

    @Provides
    @Singleton
    fun provideVehiclesApi(retrofit: Retrofit): VehiclesApi = retrofit.create(VehiclesApi::class.java)

    @Provides
    @Singleton
    fun provideParkingApi(retrofit: Retrofit): ParkingApi = retrofit.create(ParkingApi::class.java)

    @Provides
    @Singleton
    fun provideDiscoveryApi(retrofit: Retrofit): DiscoveryApi = retrofit.create(DiscoveryApi::class.java)

    @Provides
    @Singleton
    fun provideBookingApi(retrofit: Retrofit): BookingApi = retrofit.create(BookingApi::class.java)

    @Provides
    @Singleton
    fun providePaymentsApi(retrofit: Retrofit): PaymentsApi = retrofit.create(PaymentsApi::class.java)

    @Provides
    @Singleton
    fun provideQrApi(retrofit: Retrofit): QrApi = retrofit.create(QrApi::class.java)

    @Provides
    @Singleton
    fun provideAttendantApi(retrofit: Retrofit): AttendantApi = retrofit.create(AttendantApi::class.java)

    @Provides
    @Singleton
    fun provideEarningsApi(retrofit: Retrofit): EarningsApi = retrofit.create(EarningsApi::class.java)

    @Provides
    @Singleton
    fun provideRefundsApi(retrofit: Retrofit): RefundsApi = retrofit.create(RefundsApi::class.java)

    @Provides
    @Singleton
    fun provideNotificationsApi(retrofit: Retrofit): NotificationsApi = retrofit.create(NotificationsApi::class.java)

    // NOTE: AuthInterceptor and TokenAuthenticator are NOT provided here —
    // both have an @Inject constructor (see interceptor/), so Hilt
    // constructs them directly. Adding @Provides methods for them too would
    // create a duplicate-binding conflict.
}
