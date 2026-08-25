package com.parkease.partner.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat

const val DEFAULT_NOTIFICATION_CHANNEL_ID = "parkease_default"

/** Created once at app startup — required on API 26+ before any notification can be posted; idempotent. */
fun ensureDefaultNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
        DEFAULT_NOTIFICATION_CHANNEL_ID,
        "ParkEase notifications",
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = "New bookings, cash collection, earnings, and payout updates."
    }
    manager.createNotificationChannel(channel)
}
