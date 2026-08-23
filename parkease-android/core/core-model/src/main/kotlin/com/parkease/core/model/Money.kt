package com.parkease.core.model

import java.math.BigInteger

/**
 * Mirrors the backend's Money type (parkease-backend/src/common/money/money.ts):
 * always an integer count of minor units (paise) plus a currency code, never
 * a Kotlin Float/Double. The Android client only ever *displays* Money the
 * backend computed — this type exists so display code has a single,
 * consistent formatter, not so the client can compute prices itself.
 */
data class Money(val minorUnits: BigInteger, val currency: String = "INR") {

    init {
        require(currency.length == 3) { "currency must be an ISO 4217 code, got '$currency'" }
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(minorUnits + other.minorUnits, currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(minorUnits - other.minorUnits, currency)
    }

    val isNegative: Boolean get() = minorUnits < BigInteger.ZERO
    val isZero: Boolean get() = minorUnits == BigInteger.ZERO

    /** Human-readable display string only — never re-parsed for arithmetic. */
    fun toDisplayString(): String {
        val negative = minorUnits < BigInteger.ZERO
        val abs = minorUnits.abs()
        val major = abs.divide(BigInteger.valueOf(100))
        val minor = abs.mod(BigInteger.valueOf(100))
        val sign = if (negative) "-" else ""
        return "$sign$currency ${major}.${minor.toString().padStart(2, '0')}"
    }

    private fun requireSameCurrency(other: Money) {
        require(other.currency == currency) { "Currency mismatch: $currency vs ${other.currency}" }
    }

    companion object {
        fun of(minorUnits: Long, currency: String = "INR"): Money =
            Money(BigInteger.valueOf(minorUnits), currency)

        fun zero(currency: String = "INR"): Money = of(0, currency)
    }
}
