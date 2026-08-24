package com.parkease.feature.booking

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.parkease.feature.booking.ui.BookingConfirmScreen
import com.parkease.feature.booking.ui.BookingDetailScreen
import com.parkease.feature.booking.ui.MyBookingsScreen
import com.parkease.feature.booking.ui.OwnerBookingsScreen

object BookingRoutes {
    const val GRAPH = "booking"
    const val LIST = "booking/list"
    const val OWNER_LIST = "booking/owner-list"
    const val CONFIRM_PATTERN = "booking/confirm/{sectionId}/{isInstant}"
    const val DETAIL_PATTERN = "booking/detail/{bookingId}"

    fun confirm(sectionId: String, isInstant: Boolean) = "booking/confirm/$sectionId/$isInstant"
    fun detail(bookingId: String) = "booking/detail/$bookingId"
}

/**
 * Driver-facing booking flow: confirm an advance/instant booking for a
 * section (entered from feature:driver-search's SearchScreen), list "My
 * Bookings", and view/cancel a single booking's detail. Public entry point
 * mirrors feature:auth/feature:vehicles/feature:owner-parking/
 * feature:driver-search's *Graph pattern.
 */
fun NavGraphBuilder.bookingGraph(navController: NavController) {
    navigation(startDestination = BookingRoutes.LIST, route = BookingRoutes.GRAPH) {
        composable(BookingRoutes.LIST) {
            MyBookingsScreen(
                onOpenBooking = { bookingId -> navController.navigate(BookingRoutes.detail(bookingId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(BookingRoutes.OWNER_LIST) {
            OwnerBookingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = BookingRoutes.CONFIRM_PATTERN,
            arguments = listOf(
                navArgument("sectionId") { type = NavType.StringType },
                navArgument("isInstant") { type = NavType.BoolType },
            ),
        ) {
            BookingConfirmScreen(
                onBookingConfirmed = { bookingId ->
                    navController.navigate(BookingRoutes.detail(bookingId)) {
                        popUpTo(BookingRoutes.GRAPH)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = BookingRoutes.DETAIL_PATTERN,
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
        ) {
            BookingDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
