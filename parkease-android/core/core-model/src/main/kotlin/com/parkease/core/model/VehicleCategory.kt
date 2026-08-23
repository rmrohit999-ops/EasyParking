package com.parkease.core.model

/**
 * Mirrors the backend's `VehicleCategory` Prisma enum (schema.prisma) and the
 * category model in the Milestone 0 architecture doc §6. This is a fixed,
 * backend-owned enum — the Android client never infers a category from free
 * text, and every search/booking/QR screen keys off this type rather than a
 * display label.
 */
enum class VehicleCategory {
    TWO_WHEELER,
    FOUR_WHEELER,
    OTHER_SUPPORTED,
    UNSUPPORTED_PENDING_REVIEW,
}
