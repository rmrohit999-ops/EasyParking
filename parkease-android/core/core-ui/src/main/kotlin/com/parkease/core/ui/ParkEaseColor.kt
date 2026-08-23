package com.parkease.core.ui

import androidx.compose.ui.graphics.Color

/**
 * Brand tokens, ported from the Base44 reference build's `src/index.css`
 * (green/white/blue/black, rounded cards — matching the Milestone 0
 * blueprint's own design brief) into real Compose values. That build's
 * *business logic* was mock/fake throughout (see its own PROJECT_AUDIT.md);
 * only these visual tokens are carried over.
 */
object ParkEaseColor {
    // Light
    val LightPrimary = Color(0xFF1C7D5A)
    val LightPrimaryContainer = Color(0xFF145C42)
    val LightAccent = Color(0xFF2474F5)
    val LightBackground = Color(0xFFFFFFFF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightOnSurface = Color(0xFF0F1729)
    val LightSecondaryContainer = Color(0xFFF1F5F9)
    val LightOnSecondaryContainer = Color(0xFF0F1729)
    val LightMutedForeground = Color(0xFF65758B)
    val LightDestructive = Color(0xFFEF4343)
    val LightBorder = Color(0xFFE1E7EF)
    val LightSuccess = Color(0xFF21C45D)
    val LightWarning = Color(0xFFF59F0A)

    // Dark
    val DarkPrimary = Color(0xFF27B07D)
    val DarkAccent = Color(0xFF3C83F6)
    val DarkBackground = Color(0xFF090E1A)
    val DarkSurface = Color(0xFF0E1525)
    val DarkOnSurface = Color(0xFFF8FAFC)
    val DarkSecondaryContainer = Color(0xFF1D283A)
    val DarkOnSecondaryContainer = Color(0xFFF8FAFC)
    val DarkMutedForeground = Color(0xFF94A3B8)
    val DarkDestructive = Color(0xFF811D1D)
    val DarkBorder = Color(0xFF222F44)
    val DarkSuccess = Color(0xFF2FCF6C)
    val DarkWarning = Color(0xFFF7B03D)

    // Role-nav / dark chrome — sidebar in the reference build, used here for
    // dark app-bars / nav surfaces that stay dark in both themes (e.g. the
    // role-home screen's masthead) rather than every dark-toned surface.
    val NavSurface = Color(0xFF0F1729)
    val NavOnSurface = Color(0xFFF1F5F9)
    val NavAccent = Color(0xFF25A777)
}
