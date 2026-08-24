package com.parkease.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.parkease.feature.admin.AdminRoutes
import com.parkease.feature.admin.adminGraph
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

private const val WELCOME_ROUTE = "welcome"
private const val CUSTOMER_HOME_ROUTE = "customer-home"
private const val OWNER_HOME_ROUTE = "owner-home"

/** Same page as feature:auth's RegisterScreen links to, at #privacy / #terms. */
private const val LEGAL_URL = "https://claude.ai/code/artifact/f5ce0140-a291-4052-a126-482dee0b6246"

/**
 * Root of the navigation graph. Welcome is unconditionally the first
 * screen on every cold start — "Open App -> Welcome -> choose what I want
 * to do -> land in that experience" — not gated by session state, since
 * the three cards there are routing only (see RoleIntent's doc comment),
 * never a permission grant. An already-logged-in user tapping a card skips
 * straight past the auth graph into that role's home; a logged-out user is
 * sent to login/register first, and authGraph's onAuthenticated callback
 * routes onward using whichever RoleIntent was last selected.
 *
 * Real authorization is still enforced exactly where it always was: the
 * backend re-checks roles from the DB on every request (JwtAuthGuard), and
 * AdminHomeScreen's own dashboardSummary() call is what actually gates
 * admin access — client-side routing here can never grant it.
 */
@Composable
fun RootNavHost(modifier: Modifier = Modifier, viewModel: RootNavViewModel = hiltViewModel()) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val roleIntent by viewModel.roleIntent.collectAsStateWithLifecycle()
    val becomeOwnerState by viewModel.becomeOwnerState.collectAsStateWithLifecycle()
    val deleteAccountState by viewModel.deleteAccountState.collectAsStateWithLifecycle()
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    val navController = rememberNavController()
    val context = LocalContext.current

    fun openPrivacyPolicy() {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$LEGAL_URL#privacy")))
    }

    fun destinationForIntent(intent: RoleIntent?): String = when (intent) {
        RoleIntent.OWN -> OWNER_HOME_ROUTE
        RoleIntent.ADMIN -> AdminRoutes.GRAPH
        RoleIntent.PARK, null -> CUSTOMER_HOME_ROUTE
    }

    // Signing out (or a session expiring) always returns all the way to
    // Welcome, not just the login screen — matches "Open App -> Welcome"
    // being the one true reset point rather than two separate entry states.
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            val onWelcomeOrAuth = navController.currentDestination?.hierarchy?.any {
                it.route == WELCOME_ROUTE || it.route == AuthRoutes.GRAPH
            } == true
            if (!onWelcomeOrAuth) {
                navController.navigate(WELCOME_ROUTE) { popUpTo(0) { inclusive = true } }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = WELCOME_ROUTE,
        modifier = modifier,
    ) {
        composable(WELCOME_ROUTE) {
            WelcomeScreen(
                onSelectPark = {
                    viewModel.setRoleIntent(RoleIntent.PARK)
                    if (isLoggedIn) {
                        navController.navigate(CUSTOMER_HOME_ROUTE) { popUpTo(WELCOME_ROUTE) { inclusive = true } }
                    } else {
                        navController.navigate(AuthRoutes.GRAPH)
                    }
                },
                onSelectOwn = {
                    viewModel.setRoleIntent(RoleIntent.OWN)
                    if (isLoggedIn) {
                        viewModel.becomeOwner()
                        navController.navigate(OWNER_HOME_ROUTE) { popUpTo(WELCOME_ROUTE) { inclusive = true } }
                    } else {
                        navController.navigate(AuthRoutes.GRAPH)
                    }
                },
                onSelectAdmin = {
                    viewModel.setRoleIntent(RoleIntent.ADMIN)
                    if (isLoggedIn) {
                        navController.navigate(AdminRoutes.GRAPH) { popUpTo(WELCOME_ROUTE) { inclusive = true } }
                    } else {
                        navController.navigate(AuthRoutes.GRAPH)
                    }
                },
            )
        }
        authGraph(navController) {
            if (roleIntent == RoleIntent.OWN) viewModel.becomeOwner()
            navController.navigate(destinationForIntent(roleIntent)) {
                popUpTo(WELCOME_ROUTE) { inclusive = true }
            }
        }
        composable(CUSTOMER_HOME_ROUTE) {
            CustomerHomeScreen(
                onFindParking = { navController.navigate(DriverSearchRoutes.GRAPH) },
                onMyBookings = { navController.navigate(BookingRoutes.GRAPH) },
                onMyVehicles = { navController.navigate(VehiclesRoutes.GRAPH) },
                onNotifications = { navController.navigate(NotificationsRoutes.GRAPH) },
                onSignOut = { viewModel.signOut() },
                onPrivacyPolicy = { openPrivacyPolicy() },
                onDeleteAccount = { showDeleteAccountConfirm = true },
            )
        }
        composable(OWNER_HOME_ROUTE) {
            OwnerHomeScreen(
                becomeOwnerError = (becomeOwnerState as? BecomeOwnerState.Error)?.message,
                onMyListings = { navController.navigate(OwnerParkingRoutes.GRAPH) },
                onEarnings = { navController.navigate(EarningsRoutes.GRAPH) },
                onAttendantTools = { navController.navigate(AttendantRoutes.GRAPH) },
                onSignOut = { viewModel.signOut() },
                onPrivacyPolicy = { openPrivacyPolicy() },
                onDeleteAccount = { showDeleteAccountConfirm = true },
            )
        }
        adminGraph(navController, onSignOut = { viewModel.signOut() })
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

    if (showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirm = false },
            title = { Text("Delete your account?") },
            text = {
                Text(
                    "This permanently removes your profile, email, and phone number. " +
                        "It can't be undone. Bookings and payment records are kept for " +
                        "legal/financial record-keeping but are no longer linked to you.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAccountConfirm = false
                    viewModel.deleteAccount()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
    (deleteAccountState as? DeleteAccountState.Error)?.let {
        // Surfaced as a dialog rather than inline text since it can happen from any of the three home screens now.
        AlertDialog(
            onDismissRequest = { viewModel.clearDeleteAccountError() },
            confirmButton = { TextButton(onClick = { viewModel.clearDeleteAccountError() }) { Text("OK") } },
            title = { Text("Couldn't delete account") },
            text = { Text(it.message) },
        )
    }
}
