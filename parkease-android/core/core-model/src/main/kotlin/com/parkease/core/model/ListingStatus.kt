package com.parkease.core.model

/** Mirrors the backend's `ListingStatus` Prisma enum — used for both parking listings and sections. */
enum class ListingStatus {
    ACTIVE,
    PAUSED,
    CLOSED,
}
