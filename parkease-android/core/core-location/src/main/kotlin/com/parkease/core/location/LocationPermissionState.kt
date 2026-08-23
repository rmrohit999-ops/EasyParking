package com.parkease.core.location

/**
 * Tracks the driver-search screen's own permission-request flow (Milestone
 * 0 §15: just-in-time request with rationale, graceful degradation to
 * manual search on denial). The actual runtime request/rationale dialog is
 * a UI concern owned by feature:driver-search's
 * `rememberLauncherForActivityResult(RequestMultiplePermissions())` call —
 * this enum is just the state machine that flow drives, so screens don't
 * each invent their own. See [DriverLocationClient] for the one-shot fix
 * itself, used once this state is GRANTED.
 */
enum class LocationPermissionState {
    NOT_REQUESTED,
    GRANTED,
    DENIED,
    DENIED_PERMANENTLY,
}
