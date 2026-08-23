package com.parkease.feature.vehicles

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.parkease.feature.vehicles.ui.AddVehicleScreen
import com.parkease.feature.vehicles.ui.MyVehiclesScreen

object VehiclesRoutes {
    const val GRAPH = "vehicles"
    const val LIST = "vehicles/list"
    const val ADD = "vehicles/add"
}

/**
 * Public entry point into this module, mirroring feature:auth's
 * AuthNavGraph pattern — the app-level RootNavHost (or a driver-role home
 * graph, Milestone 5) calls `vehiclesGraph(navController)` without knowing
 * this module's internal route names or screens.
 */
fun NavGraphBuilder.vehiclesGraph(navController: NavController) {
    navigation(startDestination = VehiclesRoutes.LIST, route = VehiclesRoutes.GRAPH) {
        composable(VehiclesRoutes.LIST) {
            MyVehiclesScreen(
                onAddVehicle = { navController.navigate(VehiclesRoutes.ADD) },
            )
        }
        composable(VehiclesRoutes.ADD) {
            AddVehicleScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
