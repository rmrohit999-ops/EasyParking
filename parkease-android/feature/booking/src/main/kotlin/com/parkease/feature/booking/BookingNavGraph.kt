package com.parkease.feature.booking

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.parkease.feature.booking.ui.ActiveSessionScreen
import com.parkease.feature.booking.ui.BookingConfirmScreen
import com.parkease.feature.booking.ui.BookingDetailScreen
import com.parkease.feature.booking.ui.DigitalParkingPassScreen
import com.parkease.feature.booking.ui.MyBookingsScreen
import com.parkease.feature.booking.ui.OwnerBookingsScreen

object BookingRoutes {
    const val GRAPH = "booking"
    const val LIST = "booking/list"
    const val OWNER_LIST = "booking/owner-list"
    // startEpochMillis/endEpochMillis are optional query params (String,
    // nullable — Nav-Compose has no nullable LongType) so an advance-
    // booking flow that already resolved a date/time/duration elsewhere
    // (e.g. AdvanceBookingBottomSheet) can hand it straight to this
    // screen's pickers instead of making the driver re-enter it. Existing
    // callers that omit them see no change: the screen falls back to its
    // original "now+15min / now+2h" defaults.
    const val CONFIRM_PATTERN = "booking/confirm/{sectionId}/{isInstant}?startEpochMillis={startEpochMillis}&endEpochMillis={endEpochMillis}"
    const val DETAIL_PATTERN = "booking/detail/{bookingId}"
    const val PASS_PATTERN = "booking/pass/{bookingId}"
    const val ACTIVE_SESSION_PATTERN = "booking/active/{bookingId}"

    fun confirm(sectionId: String, isInstant: Boolean, startEpochMillis: Long? = null, endEpochMillis: Long? = null): String {
        val base = "booking/confirm/$sectionId/$isInstant"
        return if (startEpochMillis != null && endEpochMillis != null) {
            "$base?startEpochMillis=$startEpochMillis&endEpochMillis=$endEpochMillis"
        } else {
            base
        }
    }
    fun detail(bookingId: String) = "booking/detail/$bookingId"
    fun pass(bookingId: String) = "booking/pass/$bookingId"
    fun activeSession(bookingId: String) = "booking/active/$bookingId"
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
                navArgument("startEpochMillis") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("endEpochMillis") { type = NavType.StringType; nullable = true; defaultValue = null },
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
            BookingDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenPass = { bookingId -> navController.navigate(BookingRoutes.pass(bookingId)) },
                onOpenActiveSession = { bookingId -> navController.navigate(BookingRoutes.activeSession(bookingId)) },
            )
        }
        composable(
            route = BookingRoutes.PASS_PATTERN,
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
        ) {
            DigitalParkingPassScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = BookingRoutes.ACTIVE_SESSION_PATTERN,
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
        ) {
            ActiveSessionScreen(
                onBack = { navController.popBackStack() },
                onViewPass = { bookingId -> navController.navigate(BookingRoutes.pass(bookingId)) },
            )
        }
    }
}
