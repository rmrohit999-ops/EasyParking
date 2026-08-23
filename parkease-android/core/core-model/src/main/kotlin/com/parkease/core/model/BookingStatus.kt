package com.parkease.core.model

/**
 * Mirrors the backend booking state machine (Milestone 0 §8). The Android
 * client renders UI off this status but never computes or mutates it
 * locally — every transition is server-authoritative.
 */
enum class BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    DRIVER_ARRIVING,
    CHECKED_IN,
    PARKING_ACTIVE,
    CHECKED_OUT,
    COMPLETED,
    REJECTED,
    EXPIRED,
    CANCELLED,
    NO_SHOW,
    VEHICLE_MISMATCH,
    PARKING_UNAVAILABLE,
    ADMIN_CANCELLED;

    /** Terminal states never transition further — mirrors the backend allow-list. */
    val isTerminal: Boolean
        get() = this in setOf(
            COMPLETED, REJECTED, EXPIRED, CANCELLED, NO_SHOW, ADMIN_CANCELLED, PARKING_UNAVAILABLE,
        )
}
