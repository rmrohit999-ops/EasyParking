package com.parkease.core.model

/** Mirrors the backend payment order state machine (Milestone 0 §9). */
enum class PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCESSFUL,
    FAILED,
}

/** Mirrors `transactions.payment_status`, which additionally tracks refund states. */
enum class TransactionPaymentStatus {
    PENDING,
    PROCESSING,
    SUCCESSFUL,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED,
}
