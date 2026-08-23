package com.parkease.core.network.api

import com.parkease.core.network.model.*
import retrofit2.http.*

/**
 * Device registration, in-app inbox, and channel preferences (Milestone
 * 11). Every method here is scoped to the caller's own user server-side —
 * there is no admin/other-user variant, matching NotificationsController's
 * shape (it's entirely `@CurrentUser`-driven, no role guard needed).
 */
interface NotificationsApi {
    @POST("v1/notifications/devices")
    suspend fun registerDevice(@Body body: RegisterDeviceRequest)

    @POST("v1/notifications/devices/unregister")
    suspend fun unregisterDevice(@Body body: UnregisterDeviceRequest)

    @GET("v1/notifications")
    suspend fun listMine(@Query("unreadOnly") unreadOnly: Boolean? = null): List<NotificationResponse>

    @POST("v1/notifications/{notificationId}/read")
    suspend fun markRead(@Path("notificationId") notificationId: String)

    @POST("v1/notifications/read-all")
    suspend fun markAllRead(): MarkAllReadResponse

    @GET("v1/notifications/preferences")
    suspend fun getPreferences(): List<NotificationPreferenceResponse>

    @POST("v1/notifications/preferences")
    suspend fun updatePreference(@Body body: UpdateNotificationPreferenceRequest): NotificationPreferenceResponse
}
