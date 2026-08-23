package com.parkease.core.database

/**
 * Placeholder anchor for the Room database. IMPORTANT constraint carried
 * over from Milestone 0: this local database is a read-mostly CACHE
 * (favorites, recent bookings, vehicle list for offline display) — it is
 * never the source of truth for availability, price, or booking/payment
 * state. The real @Database/@Entity/@Dao definitions are added per-feature
 * starting Milestone 3 (vehicles) as each domain needs local caching.
 */
internal const val PARKEASE_DB_NAME = "parkease.db"
