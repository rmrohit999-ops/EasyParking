package com.parkease.feature.earnings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.parkease.feature.earnings.ui.EarningsScreen

object EarningsRoutes {
    const val GRAPH = "earnings"
    const val HOME = "earnings/home"
}

/** Owner-facing earnings/payout-accounts/settlements flow (Milestone 9). */
fun NavGraphBuilder.earningsGraph(navController: NavController) {
    navigation(startDestination = EarningsRoutes.HOME, route = EarningsRoutes.GRAPH) {
        composable(EarningsRoutes.HOME) {
            EarningsScreen(onBack = { navController.popBackStack() })
        }
    }
}
