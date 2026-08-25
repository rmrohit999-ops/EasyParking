package com.parkease.feature.admin

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.parkease.feature.admin.ui.AdminCashSummaryScreen
import com.parkease.feature.admin.ui.AdminHomeScreen
import com.parkease.feature.admin.ui.AdminMapsQuotaScreen
import com.parkease.feature.admin.ui.AdminPendingListingsScreen
import com.parkease.feature.admin.ui.AdminUsersScreen

object AdminRoutes {
    const val GRAPH = "admin"
    const val HOME = "admin/home"
    const val USERS = "admin/users"
    const val PENDING_LISTINGS = "admin/pending-listings"
    const val CASH_SUMMARY = "admin/cash-summary"
    const val MAPS_QUOTA = "admin/maps-quota"
}

/** Admin-only monitoring/management flow — every route here is backed by an ADMIN-role-gated backend endpoint, re-checked server-side on every request. */
fun NavGraphBuilder.adminGraph(navController: NavController, onSignOut: () -> Unit) {
    navigation(startDestination = AdminRoutes.HOME, route = AdminRoutes.GRAPH) {
        composable(AdminRoutes.HOME) {
            AdminHomeScreen(
                onOpenUsers = { navController.navigate(AdminRoutes.USERS) },
                onOpenPendingListings = { navController.navigate(AdminRoutes.PENDING_LISTINGS) },
                onOpenCashSummary = { navController.navigate(AdminRoutes.CASH_SUMMARY) },
                onOpenMapsQuota = { navController.navigate(AdminRoutes.MAPS_QUOTA) },
                onSignOut = onSignOut,
            )
        }
        composable(AdminRoutes.USERS) {
            AdminUsersScreen(onBack = { navController.popBackStack() })
        }
        composable(AdminRoutes.PENDING_LISTINGS) {
            AdminPendingListingsScreen(onBack = { navController.popBackStack() })
        }
        composable(AdminRoutes.CASH_SUMMARY) {
            AdminCashSummaryScreen(onBack = { navController.popBackStack() })
        }
        composable(AdminRoutes.MAPS_QUOTA) {
            AdminMapsQuotaScreen(onBack = { navController.popBackStack() })
        }
    }
}
