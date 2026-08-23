package com.parkease.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.parkease.app.MainActivity
import com.parkease.app.R
import com.parkease.core.datastore.SessionStore
import com.parkease.feature.notifications.data.NotificationsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Real FCM client-side counterpart to FcmPushProviderService (backend,
 * Milestone 11). Two responsibilities: (1) forward a fresh/rotated token to
 * the backend the instant FCM hands one to us, so the `NotificationDevice`
 * row RegisterDeviceDto writes is never stale; (2) render a local
 * notification for messages FCM delivers to this callback rather than the
 * OS's own tray (a data-only payload, or a notification payload that
 * arrives while the app process is already running in the foreground —
 * see FcmPushProviderService's message shape, which always sends both a
 * `notification` block and a `data.deepLink`).
 *
 * @AndroidEntryPoint on a Service works the same way it does on an
 * Activity — Hilt generates the injection at Service#onCreate, this class
 * doesn't need its own onCreate override to make that happen.
 */
@AndroidEntryPoint
class ParkEaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationsRepository: NotificationsRepository

    @Inject
    lateinit var sessionStore: SessionStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Registering while signed out would just 401 against an
        // authenticated endpoint — RootNavViewModel.syncPushToken() (app
        // module) covers "a token already existed before the user signed
        // in" once sign-in actually happens.
        if (sessionStore.currentAccessTokenBlocking() == null) return
        serviceScope.launch { notificationsRepository.registerDevice(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        showNotification(title, body, message.data["deepLink"])
    }

    private fun showNotification(title: String, body: String, deepLink: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            deepLink?.let { putExtra(EXTRA_DEEP_LINK, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationRequestCode.incrementAndGet(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, DEFAULT_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(this).notify(notificationRequestCode.get(), notification)
    }

    companion object {
        const val EXTRA_DEEP_LINK = "deep_link"
        private val notificationRequestCode = AtomicInteger(1000)
    }
}
