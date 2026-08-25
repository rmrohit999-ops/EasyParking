package com.parkease.driver

import android.app.Application
import com.parkease.driver.notifications.ensureDefaultNotificationChannel
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point for the Driver app. Registers the notification
 * channel unconditionally at process start — required on API 26+ before
 * any notification can be posted, idempotent, so safe on every cold start.
 */
@HiltAndroidApp
class ParkEaseDriverApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureDefaultNotificationChannel(this)
    }
}
