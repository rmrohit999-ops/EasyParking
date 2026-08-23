package com.parkease.feature.notifications

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.parkease.feature.notifications.ui.NotificationPreferencesScreen
import com.parkease.feature.notifications.ui.NotificationsScreen

object NotificationsRoutes {
    const val GRAPH = "notifications"
    const val INBOX = "notifications/inbox"
    const val PREFERENCES = "notifications/preferences"
}

/** In-app notification inbox and channel preferences (Milestone 11). */
fun NavGraphBuilder.notificationsGraph(navController: NavController) {
    navigation(startDestination = NotificationsRoutes.INBOX, route = NotificationsRoutes.GRAPH) {
        composable(NotificationsRoutes.INBOX) {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                onOpenPreferences = { navController.navigate(NotificationsRoutes.PREFERENCES) },
            )
        }
        composable(NotificationsRoutes.PREFERENCES) {
            NotificationPreferencesScreen(onBack = { navController.popBackStack() })
        }
    }
}
