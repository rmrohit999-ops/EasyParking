package com.parkease.driver.notifications

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Thin suspend wrapper over FirebaseMessaging's Task<String> token fetch. */
@Singleton
class PushTokenProvider @Inject constructor() {
    /** Null on any failure (no Play Services, no network, no default FirebaseApp) — callers treat that as "skip for now." */
    suspend fun currentToken(): String? = try {
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> if (continuation.isActive) continuation.resume(token) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        }
    } catch (e: IllegalStateException) {
        null
    }
}
