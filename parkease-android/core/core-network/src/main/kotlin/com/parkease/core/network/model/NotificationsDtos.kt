package com.parkease.core.network.model

import com.squareup.moshi.JsonClass

// ---------------------------------------------------------------------
// Devices (Milestone 11)
// ---------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class RegisterDeviceRequest(val fcmToken: String, val platform: String? = "ANDROID")

@JsonClass(generateAdapter = true)
data class UnregisterDeviceRequest(val fcmToken: String)

// ---------------------------------------------------------------------
// Inbox (Milestone 11)
// ---------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class NotificationResponse(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val deepLink: String?,
    val readAt: String?,
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class MarkAllReadResponse(val markedRead: Int)

// ---------------------------------------------------------------------
// Preferences (Milestone 11)
// ---------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class NotificationPreferenceResponse(val category: String, val channel: String, val enabled: Boolean)

@JsonClass(generateAdapter = true)
data class UpdateNotificationPreferenceRequest(val category: String, val channel: String, val enabled: Boolean)
