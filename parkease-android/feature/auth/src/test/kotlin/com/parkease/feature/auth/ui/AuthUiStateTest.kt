package com.parkease.feature.auth.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AuthUiStateTest {
    @Test
    fun `default state is not loading, no error, not authenticated`() {
        val state = AuthUiState()
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
        assertThat(state.otpSentTo).isNull()
        assertThat(state.isAuthenticated).isFalse()
    }

    @Test
    fun `copy preserves unrelated fields`() {
        val state = AuthUiState(isLoading = true, otpSentTo = "+911234567890")
        val next = state.copy(isLoading = false)
        assertThat(next.otpSentTo).isEqualTo("+911234567890")
        assertThat(next.isLoading).isFalse()
    }
}
