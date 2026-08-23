package com.parkease.app.di

import com.parkease.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Supplies the flavor-specific API base URL (BuildConfig.API_BASE_URL —
 * differs per dev/staging/prod product flavor, see app/build.gradle.kts)
 * to core-network's NetworkModule, which stays flavor-agnostic.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkConfigModule {
    @Provides
    @Singleton
    @Named("apiBaseUrl")
    fun provideApiBaseUrl(): String = BuildConfig.API_BASE_URL
}
