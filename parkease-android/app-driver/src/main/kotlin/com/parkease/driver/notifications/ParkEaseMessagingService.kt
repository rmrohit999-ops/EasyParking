package com.parkease.driver.notifications

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
import com.parkease.driver.MainActivity
import com.parkease.driver.R
import com.parkease.core.datastore.SessionStore
import com.parkease.feature.notifications.data.NotificationsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/** Real FCM client-side counterpart to FcmPushProviderService (backend). */
@AndroidEntryPoint
class ParkEaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationsRepository: NotificationsRepository

    @Inject
    lateinit var sessionStore: SessionStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
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
