package com.parkease.feature.driversearch

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.parkease.feature.driversearch.ui.FavoritesScreen
import com.parkease.feature.driversearch.ui.SearchScreen

object DriverSearchRoutes {
    const val GRAPH = "driver-search"
    const val SEARCH = "driver-search/search"
    const val FAVORITES = "driver-search/favorites"
}

/**
 * Driver-facing discovery: GPS-based nearby search filtered to the
 * driver's default vehicle, category filters, and favorites. Public entry
 * point mirrors feature:auth/feature:vehicles/feature:owner-parking's
 * *Graph pattern.
 *
 * [onBookSection] lets the caller (RootNavHost) route into feature:booking
 * without this module depending on it directly — same "public entry point
 * takes a callback, doesn't know about other modules' routes" pattern used
 * for onAddVehicle/onCreateListing elsewhere.
 */
fun NavGraphBuilder.driverSearchGraph(navController: NavController, onBookSection: (String, Boolean) -> Unit) {
    navigation(startDestination = DriverSearchRoutes.SEARCH, route = DriverSearchRoutes.GRAPH) {
        composable(DriverSearchRoutes.SEARCH) {
            SearchScreen(
                onOpenFavorites = { navController.navigate(DriverSearchRoutes.FAVORITES) },
                onBookSection = onBookSection,
            )
        }
        composable(DriverSearchRoutes.FAVORITES) {
            FavoritesScreen(onBack = { navController.popBackStack() })
        }
    }
}
