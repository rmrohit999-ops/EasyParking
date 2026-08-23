package com.parkease.app

import android.app.Application
import com.parkease.app.notifications.ensureDefaultNotificationChannel
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt's generated component graph roots here.
 * The one thing done unconditionally at process start is registering the
 * notification channel (Milestone 11) — required before API 26+ can post
 * any notification at all, and idempotent (createNotificationChannel is a
 * no-op if the channel already exists), so it's safe to call on every
 * cold start rather than gating it behind a "first run" check. Crash
 * reporting / analytics initialization stays consent-gated and is not
 * wired up here.
 */
@HiltAndroidApp
class ParkEaseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureDefaultNotificationChannel(this)
    }
}
