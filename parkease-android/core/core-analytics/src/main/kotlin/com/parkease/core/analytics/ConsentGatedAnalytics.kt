package com.parkease.core.analytics

/**
 * Placeholder for the consent-gated analytics facade (Milestone 0: "analytics
 * configuration with consent" / never collect before the user has opted in).
 * Real implementation lands in Milestone 11 alongside notification
 * history/analytics, reading its gate from the same consent-preferences
 * store the settings screen writes to.
 */
interface ConsentGatedAnalytics {
    fun setAnalyticsConsent(granted: Boolean)
    fun isAnalyticsConsentGranted(): Boolean
}
