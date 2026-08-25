package com.parkease.feature.booking.reminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Real local scheduling for "your reservation ends soon" — not a fake
 * toast, an actual `WorkManager` one-shot job that fires even if the app
 * is backgrounded or the process dies in the meantime. Posts to the same
 * notification channel app-driver's `PushNotificationChannels.kt` already
 * creates at app startup (`DEFAULT_NOTIFICATION_CHANNEL_ID = "parkease_default"`)
 * — duplicated here as a plain string rather than a cross-module reference,
 * since a feature module can't depend on the app module that owns it.
 */
class ParkingReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }
        val bookingId = inputData.getString(KEY_BOOKING_ID) ?: return Result.failure()

        val notification = NotificationCompat.Builder(applicationContext, PARKEASE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Your parking reservation ends soon")
            .setContentText("Head back to your vehicle to avoid an overstay charge.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(bookingId.hashCode(), notification)
        return Result.success()
    }

    companion object {
        const val KEY_BOOKING_ID = "booking_id"
        const val PARKEASE_NOTIFICATION_CHANNEL_ID = "parkease_default"
    }
}

/** Schedule/cancel helpers, keyed by booking so re-scheduling (e.g. re-opening the active-session screen) replaces rather than duplicates a pending reminder. */
object ParkingReminders {
    private const val WORK_NAME_PREFIX = "parking_reminder_"

    fun schedule(context: Context, bookingId: String, endTime: Instant, leadTime: Duration = Duration.ofMinutes(10)) {
        val delayMillis = endTime.minus(leadTime).toEpochMilli() - System.currentTimeMillis()
        if (delayMillis <= 0) return

        val request = OneTimeWorkRequestBuilder<ParkingReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(ParkingReminderWorker.KEY_BOOKING_ID, bookingId).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME_PREFIX + bookingId, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, bookingId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PREFIX + bookingId)
    }
}
