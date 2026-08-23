package com.parkease.feature.notifications.data

import com.parkease.core.network.api.NotificationsApi
import com.parkease.core.network.model.RegisterDeviceRequest
import com.parkease.core.network.model.UnregisterDeviceRequest
import com.parkease.core.network.model.UpdateNotificationPreferenceRequest
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationUi(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val deepLink: String?,
    val isRead: Boolean,
    val createdAt: String,
)

data class NotificationPreferenceUi(val category: String, val channel: String, val enabled: Boolean)

sealed class NotificationsResult<out T> {
    data class Success<T>(val value: T) : NotificationsResult<T>()
    data class Error(val message: String) : NotificationsResult<Nothing>()
}

private const val FRIENDLY_ERROR = "Something went wrong. Please try again."

/**
 * Notification inbox, channel preferences, and device registration
 * (Milestone 11). `registerDevice`/`unregisterDevice` deliberately return a
 * plain Boolean rather than a NotificationsResult — a push token failing to
 * register must never surface an error to the user or block sign-in/
 * sign-out, mirroring the backend's own "a notification-side failure never
 * propagates to the triggering action" rule (NotificationsService.send,
 * RefundsService.notifyRefund, etc.) on the client side too.
 */
@Singleton
class NotificationsRepository @Inject constructor(
    private val notificationsApi: NotificationsApi,
) {
    suspend fun registerDevice(fcmToken: String): Boolean = try {
        notificationsApi.registerDevice(RegisterDeviceRequest(fcmToken))
        true
    } catch (e: Exception) {
        false
    }

    suspend fun unregisterDevice(fcmToken: String): Boolean = try {
        notificationsApi.unregisterDevice(UnregisterDeviceRequest(fcmToken))
        true
    } catch (e: Exception) {
        false
    }

    suspend fun listMine(unreadOnly: Boolean = false): NotificationsResult<List<NotificationUi>> = runCatchingApi {
        notificationsApi.listMine(unreadOnly).map {
            NotificationUi(
                id = it.id,
                type = it.type,
                title = it.title,
                body = it.body,
                deepLink = it.deepLink,
                isRead = it.readAt != null,
                createdAt = it.createdAt,
            )
        }
    }

    suspend fun markRead(notificationId: String): NotificationsResult<Unit> = runCatchingApi {
        notificationsApi.markRead(notificationId)
    }

    suspend fun markAllRead(): NotificationsResult<Int> = runCatchingApi {
        notificationsApi.markAllRead().markedRead
    }

    suspend fun getPreferences(): NotificationsResult<List<NotificationPreferenceUi>> = runCatchingApi {
        notificationsApi.getPreferences().map { NotificationPreferenceUi(it.category, it.channel, it.enabled) }
    }

    suspend fun updatePreference(category: String, channel: String, enabled: Boolean): NotificationsResult<NotificationPreferenceUi> =
        runCatchingApi {
            val response = notificationsApi.updatePreference(UpdateNotificationPreferenceRequest(category, channel, enabled))
            NotificationPreferenceUi(response.category, response.channel, response.enabled)
        }

    private inline fun <T> runCatchingApi(block: () -> T): NotificationsResult<T> = try {
        NotificationsResult.Success(block())
    } catch (e: retrofit2.HttpException) {
        NotificationsResult.Error(
            when (e.code()) {
                400 -> "That request wasn't valid. Please check the details and try again."
                403 -> "You don't have permission to do that."
                404 -> "We couldn't find that."
                else -> FRIENDLY_ERROR
            },
        )
    } catch (e: Exception) {
        NotificationsResult.Error(FRIENDLY_ERROR)
    }
}

/**
 * The categories the backend actually sends push notifications for today
 * (BookingService.notifyOnTransition, RefundsService.notifyRefund,
 * SettlementsService.notifySettlementOutcome, SupportService.addMessage,
 * DisputesService.resolveDispute — all Milestone 9-11). Only the PUSH
 * channel is exposed here: NotificationPreference has SMS/EMAIL channel
 * values in its schema, but nothing in this build actually sends an SMS or
 * email notification yet, so surfacing toggles for channels that don't do
 * anything would be misleading rather than a real setting.
 */
val NOTIFICATION_CATEGORIES: List<Pair<String, String>> = listOf(
    "booking_status" to "Booking updates",
    "refund" to "Refunds",
    "settlement" to "Payouts",
    "support" to "Support replies",
    "dispute" to "Disputes",
)
