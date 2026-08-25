package com.parkease.partner.di

import com.parkease.partner.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Supplies the flavor-specific API base URL to core-network's
 * NetworkModule. Also supplies the Razorpay key_id binding as an empty
 * string — feature:booking's BookingDetailViewModel (compiled into this
 * app for OwnerBookingsScreen's sake, though its own detail screen is
 * never navigated to here) still needs this named binding to exist for
 * Hilt's graph to resolve at all.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkConfigModule {
    @Provides
    @Singleton
    @Named("apiBaseUrl")
    fun provideApiBaseUrl(): String = BuildConfig.API_BASE_URL

    @Provides
    @Singleton
    @Named("razorpayKeyId")
    fun provideRazorpayKeyId(): String = BuildConfig.RAZORPAY_KEY_ID
}
