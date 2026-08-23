package com.parkease.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * App-level Hilt module. Empty in Milestone 1 — the real providers
 * (Retrofit client, session token holder, Room database, DataStore) are
 * added as core-network/core-database/core-datastore gain real
 * implementations starting Milestone 2.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
