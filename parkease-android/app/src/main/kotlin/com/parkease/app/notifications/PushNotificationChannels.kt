package com.parkease.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat

const val DEFAULT_NOTIFICATION_CHANNEL_ID = "parkease_default"

/**
 * Created once at app startup (ParkEaseApplication.onCreate) — required on
 * API 26+ (this app's minSdk) before any notification can be posted; a
 * single channel is enough for Milestone 11's scope (one category of
 * "important app updates"), matching NotificationsService's own single
 * PUSH channel per user rather than per-category OS channels.
 */
fun ensureDefaultNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
        DEFAULT_NOTIFICATION_CHANNEL_ID,
        "ParkEase notifications",
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = "Booking, payment, refund, payout, support, and dispute updates."
    }
    manager.createNotificationChannel(channel)
}
