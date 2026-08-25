package com.parkease.partner.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** App-level Hilt module. Empty for now — see NetworkConfigModule for the real providers this app needs. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
