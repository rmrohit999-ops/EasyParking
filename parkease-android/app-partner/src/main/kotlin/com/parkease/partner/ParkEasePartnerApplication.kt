package com.parkease.partner

import android.app.Application
import com.parkease.partner.notifications.ensureDefaultNotificationChannel
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point for the Partner app. Registers the notification
 * channel unconditionally at process start — required on API 26+ before
 * any notification can be posted, idempotent, so safe on every cold start.
 */
@HiltAndroidApp
class ParkEasePartnerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureDefaultNotificationChannel(this)
    }
}
