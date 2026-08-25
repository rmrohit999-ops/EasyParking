package com.parkease.driver.di

import com.parkease.driver.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Supplies the flavor-specific API base URL and the Razorpay Checkout
 * key_id to core-network's NetworkModule, which stays flavor-agnostic.
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
