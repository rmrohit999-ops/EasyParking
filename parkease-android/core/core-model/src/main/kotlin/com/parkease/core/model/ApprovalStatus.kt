package com.parkease.core.model

/** Mirrors the backend's `ApprovalStatus` Prisma enum — the admin review state of a listing or section. */
enum class ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    NEEDS_MORE_INFORMATION,
    SUSPENDED,
}
