package com.parkease.driver.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.parkease.feature.auth.AuthRoutes
import com.parkease.feature.auth.authGraph
import com.parkease.driver.home.DriverHomeScreen
import com.parkease.feature.booking.BookingRoutes
import com.parkease.feature.booking.bookingGraph
import com.parkease.feature.driversearch.DriverSearchRoutes
import com.parkease.feature.driversearch.driverSearchGraph
import com.parkease.feature.notifications.NotificationsRoutes
import com.parkease.feature.notifications.notificationsGraph
import com.parkease.feature.vehicles.VehiclesRoutes
import com.parkease.feature.vehicles.vehiclesGraph

private const val LOADING_ROUTE = "loading"
private const val CUSTOMER_HOME_ROUTE = "customer-home"

/** Same page feature:auth's RegisterScreen links to, at #privacy / #terms. */
private const val LEGAL_URL = "https://claude.ai/code/artifact/f5ce0140-a291-4052-a126-482dee0b6246"

/**
 * Root of the Driver app's navigation graph. This app serves exactly one
 * audience, so there's no Welcome-screen role chooser (that only made
 * sense when one app served three roles) — signed-out opens straight into
 * auth, and a successful sign-in always lands on the driver home. Admin is
 * still reachable (spec: rohitrreddy@gmail.com gets an optional quick-
 * switch from either app) via a link in the account footer, exactly as
 * unprivileged as before: AdminHomeScreen's own dashboardSummary() call is
 * what actually gates access, never this navigation shell.
 */
@Composable
fun RootNavHost(modifier: Modifier = Modifier, viewModel: RootNavViewModel = hiltViewModel()) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val deleteAccountState by viewModel.deleteAccountState.collectAsStateWithLifecycle()
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    val navController = rememberNavController()
    val context = LocalContext.current

    fun openPrivacyPolicy() {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$LEGAL_URL#privacy")))
    }

    // One effect handles every transition: the initial resolve once
    // DataStore's async read completes (isLoggedIn starts null — see
    // RootNavViewModel's doc comment on why that matters here, unlike the
    // old single-app shell which always had a neutral Welcome screen to
    // sit on), sign-out, and a session expiring mid-use alike.
    LaunchedEffect(isLoggedIn) {
        val target = when (isLoggedIn) {
            null -> return@LaunchedEffect
            true -> CUSTOMER_HOME_ROUTE
            false -> AuthRoutes.GRAPH
        }
        val onTarget = navController.currentDestination?.hierarchy?.any { it.route == target } == true
        if (!onTarget) {
            navController.navigate(target) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(
        navController = navController,
        startDestination = LOADING_ROUTE,
        modifier = modifier,
    ) {
        composable(LOADING_ROUTE) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        authGraph(navController) {
            navController.navigate(CUSTOMER_HOME_ROUTE) { popUpTo(0) { inclusive = true } }
        }
        composable(CUSTOMER_HOME_ROUTE) {
            DriverHomeScreen(
                onBookSection = { sectionId, isInstant ->
                    navController.navigate(BookingRoutes.confirm(sectionId, isInstant))
                },
                onBookAdvance = { sectionId, startEpochMillis, endEpochMillis ->
                    navController.navigate(BookingRoutes.confirm(sectionId, isInstant = false, startEpochMillis = startEpochMillis, endEpochMillis = endEpochMillis))
                },
                onMyBookings = { navController.navigate(BookingRoutes.GRAPH) },
                onMyVehicles = { navController.navigate(VehiclesRoutes.GRAPH) },
                onNotifications = { navController.navigate(NotificationsRoutes.GRAPH) },
                onAdmin = { navController.navigate(AdminRoutes.GRAPH) },
                onSignOut = { viewModel.signOut() },
                onPrivacyPolicy = { openPrivacyPolicy() },
                onDeleteAccount = { showDeleteAccountConfirm = true },
            )
        }
        adminGraph(navController, onSignOut = { viewModel.signOut() })
        vehiclesGraph(navController)
        driverSearchGraph(navController) { sectionId, isInstant ->
            navController.navigate(BookingRoutes.confirm(sectionId, isInstant))
        }
        bookingGraph(navController)
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
        AlertDialog(
            onDismissRequest = { viewModel.clearDeleteAccountError() },
            confirmButton = { TextButton(onClick = { viewModel.clearDeleteAccountError() }) { Text("OK") } },
            title = { Text("Couldn't delete account") },
            text = { Text(it.message) },
        )
    }
}
