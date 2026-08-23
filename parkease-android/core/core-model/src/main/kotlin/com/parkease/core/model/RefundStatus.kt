package com.parkease.core.model

/** Mirrors the backend refund state machine (Milestone 0 §10). */
enum class RefundStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
}
