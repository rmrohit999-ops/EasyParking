package com.parkease.feature.attendant

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.parkease.feature.attendant.ui.AttendantOpsScreen

object AttendantRoutes {
    const val GRAPH = "attendant"
    const val OPS = "attendant/ops"
}

/**
 * Attendant/owner-self-service operational flow for Milestone 8: verify a
 * driver's QR entry pass and check the vehicle in or out, or report a
 * mismatch. A single screen today (AttendantOpsScreen) — mirrors the
 * *Graph pattern used by feature:booking/feature:vehicles/
 * feature:owner-parking/feature:driver-search.
 */
fun NavGraphBuilder.attendantGraph(navController: NavController, onSignOut: (() -> Unit)? = null) {
    navigation(startDestination = AttendantRoutes.OPS, route = AttendantRoutes.GRAPH) {
        composable(AttendantRoutes.OPS) {
            AttendantOpsScreen(
                onBack = { navController.popBackStack() },
                onSignOut = onSignOut,
            )
        }
    }
}
