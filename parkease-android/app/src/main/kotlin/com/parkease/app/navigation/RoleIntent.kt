package com.parkease.app.navigation

/**
 * What the user tapped on the Welcome screen — routing only, never a grant
 * of access. The real permission check always happens server-side (a
 * DRIVER role is auto-granted at registration; OWNER is self-service and
 * idempotent; ADMIN is never grantable client-side at all — AdminHomeScreen's
 * own API calls are the actual gate, this just decides which screen to send
 * an already-authenticated user to, or which experience to land on right
 * after login).
 */
enum class RoleIntent {
    PARK,
    OWN,
    ADMIN,
}
