package com.parkease.core.model

/** Mirrors the backend's `ParkingType` Prisma enum — what kind of premises the listing is. */
enum class ParkingType {
    INDIVIDUAL,
    RESIDENTIAL,
    APARTMENT,
    COMMERCIAL,
    OFFICE,
    MALL,
    OTHER,
}
