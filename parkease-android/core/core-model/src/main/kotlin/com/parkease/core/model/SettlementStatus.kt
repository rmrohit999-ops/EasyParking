package com.parkease.core.model

/** Mirrors the backend settlement state machine (Milestone 0 §11). */
enum class SettlementStatus {
    PENDING,
    PROCESSING,
    SETTLED,
    FAILED,
    REVERSED,
}
