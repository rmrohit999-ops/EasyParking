package com.parkease.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.parkease.feature.attendant.AttendantRoutes
import com.parkease.feature.attendant.attendantGraph
import com.parkease.feature.auth.AuthRoutes
import com.parkease.feature.auth.authGraph
import com.parkease.feature.booking.BookingRoutes
import com.parkease.feature.booking.bookingGraph
import com.parkease.feature.driversearch.DriverSearchRoutes
import com.parkease.feature.driversearch.driverSearchGraph
import com.parkease.feature.earnings.EarningsRoutes
import com.parkease.feature.earnings.earningsGraph
import com.parkease.feature.notifications.NotificationsRoutes
import com.parkease.feature.notifications.notificationsGraph
import com.parkease.feature.ownerparking.OwnerParkingRoutes
import com.parkease.feature.ownerparking.ownerParkingGraph
import com.parkease.feature.vehicles.VehiclesRoutes
import com.parkease.feature.vehicles.vehiclesGraph

private const val ROLE_HOME_ROUTE = "role-home"

/**
 * Root of the navigation graph. Starts at the auth graph (feature:auth,
 * Milestone 2) when there's no session, and lands on a role-appropriate
 * home once authenticated. RootNavViewModel just exposes
 * AuthRepository.isLoggedIn — full role-specific home screens are built in
 * Milestones 5/4/8 (driver/owner/attendant respectively); the interim
 * role-home screen added in Milestone 3 links into feature:vehicles (the
 * one driver-facing feature built so far) so it's reachable end-to-end
 * ahead of the real driver home. Enforcing "a driver never reaches the
 * owner graph" is a navigation-level concern for those milestones; the
 * backend's own per-endpoint role/ownership checks (Milestone 2) are the
 * real boundary either way — see Milestone 0 §4.
 *
 * `startDestination` only ever reflects the *first* composition's
 * isLoggedIn value — by design, NavHost never re-evaluates it on its own.
 * `isLoggedIn` itself starts `false` at cold start even for an already
 * signed-in user (RootNavViewModel's StateFlow needs a moment to collect
 * the real value out of EncryptedSessionStore's DataStore), so without the
 * LaunchedEffect below, a returning signed-in user would always land on
 * the login screen with nothing to carry them past it. The same effect
 * doubles as what makes Milestone 11's sign-out button (see
 * RootNavViewModel.signOut) actually navigate anywhere once the session is
 * cleared, rather than leaving the user stranded on role-home post-logout.
 */
@Composable
fun RootNavHost(modifier: Modifier = Modifier, viewModel: RootNavViewModel = hiltViewModel()) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    LaunchedEffect(isLoggedIn) {
        val onAuthGraph = navController.currentDestination?.hierarchy?.any { it.route == AuthRoutes.GRAPH } == true
        if (isLoggedIn && onAuthGraph) {
            navController.navigate(ROLE_HOME_ROUTE) { popUpTo(AuthRoutes.GRAPH) { inclusive = true } }
        } else if (!isLoggedIn && !onAuthGraph) {
            navController.navigate(AuthRoutes.GRAPH) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) ROLE_HOME_ROUTE else AuthRoutes.GRAPH,
        modifier = modifier,
    ) {
        authGraph(navController) {
            navController.navigate(ROLE_HOME_ROUTE) {
                popUpTo(AuthRoutes.GRAPH) { inclusive = true }
            }
        }
        composable(ROLE_HOME_ROUTE) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Signed in. Full role-specific home screens (driver: " +
                            "Milestone 5, owner: Milestone 4, attendant: Milestone 8) land next.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = { navController.navigate(VehiclesRoutes.GRAPH) }) {
                        Text("My Vehicles")
                    }
                    Button(onClick = { navController.navigate(OwnerParkingRoutes.GRAPH) }) {
                        Text("My Parking Listings")
                    }
                    Button(onClick = { navController.navigate(EarningsRoutes.GRAPH) }) {
                        Text("Earnings & Payouts")
                    }
                    Button(onClick = { navController.navigate(DriverSearchRoutes.GRAPH) }) {
                        Text("Find Parking")
                    }
                    Button(onClick = { navController.navigate(BookingRoutes.GRAPH) }) {
                        Text("My Bookings")
                    }
                    Button(onClick = { navController.navigate(AttendantRoutes.GRAPH) }) {
                        Text("Attendant Tools")
                    }
                    Button(onClick = { navController.navigate(NotificationsRoutes.GRAPH) }) {
                        Text("Notifications")
                    }
                    Button(onClick = { viewModel.signOut() }) {
                        Text("Sign out")
                    }
                }
            }
        }
        vehiclesGraph(navController)
        ownerParkingGraph(navController)
        driverSearchGraph(navController) { sectionId, isInstant ->
            navController.navigate(BookingRoutes.confirm(sectionId, isInstant))
        }
        bookingGraph(navController)
        attendantGraph(navController)
        earningsGraph(navController)
        notificationsGraph(navController)
    }
}
