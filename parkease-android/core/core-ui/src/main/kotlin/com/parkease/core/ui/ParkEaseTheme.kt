package com.parkease.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Placeholder theme entry point. The real design-system tokens (color
 * roles, type scale, spacing scale, component styles — Milestone 0's
 * "modern premium design based on green/white/blue/black, rounded cards,
 * clean typography") are built alongside the first real screens in
 * Milestone 5, not speculatively invented in this foundation milestone.
 */
@Composable
fun ParkEaseTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
