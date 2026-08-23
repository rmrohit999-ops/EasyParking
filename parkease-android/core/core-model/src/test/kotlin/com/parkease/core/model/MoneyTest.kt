package com.parkease.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigInteger

class MoneyTest {

    @Test
    fun `adds and subtracts within the same currency`() {
        val a = Money.of(10000) // INR 100.00
        val b = Money.of(2550) // INR 25.50
        assertThat((a + b).toDisplayString()).isEqualTo("INR 125.50")
        assertThat((a - b).toDisplayString()).isEqualTo("INR 74.50")
    }

    @Test
    fun `throws on currency mismatch`() {
        val inr = Money.of(100, "INR")
        val usd = Money.of(100, "USD")
        try {
            inr + usd
            error("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("Currency mismatch")
        }
    }

    @Test
    fun `zero and negative helpers report correctly`() {
        assertThat(Money.zero().isZero).isTrue()
        assertThat(Money.of(-1).isNegative).isTrue()
        assertThat(Money.of(1).isNegative).isFalse()
    }

    @Test
    fun `display string pads single-digit minor units`() {
        assertThat(Money.of(BigInteger.valueOf(5).toLong()).toDisplayString()).isEqualTo("INR 0.05")
    }
}
