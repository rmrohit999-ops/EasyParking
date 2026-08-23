package com.parkease.app.notifications

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Thin suspend wrapper over FirebaseMessaging's Task<String> token fetch —
 * mirrors DriverLocationClient's GMS-Task-to-suspend-function pattern
 * (core-location, Milestone 5) rather than pulling in a
 * kotlinx-coroutines-play-services dependency for one call site. Lives in
 * the app module, not core-network: firebase-messaging is only a
 * dependency of :app (see app/build.gradle.kts) — adding it to
 * core-network would pull Play Services into every feature module that has
 * no need for it.
 */
@Singleton
class PushTokenProvider @Inject constructor() {
    /**
     * Null on any failure — no Play Services, no network, no default
     * FirebaseApp (a checkout with no real app/google-services.json yet,
     * see the Milestone 12 note in app/build.gradle.kts) — callers treat
     * that as "skip for now," never as a hard error. The explicit
     * try/catch around `FirebaseMessaging.getInstance()` itself matters
     * here specifically: unlike a failed Task (routed through
     * addOnFailureListener below), a missing default FirebaseApp makes
     * `getInstance()` throw `IllegalStateException` synchronously, before
     * any Task even exists to fail.
     */
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
