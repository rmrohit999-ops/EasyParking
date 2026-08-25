package com.parkease.partner.navigation

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
import com.parkease.feature.attendant.AttendantRoutes
import com.parkease.feature.attendant.attendantGraph
import com.parkease.feature.auth.AuthRoutes
import com.parkease.feature.auth.authGraph
import com.parkease.feature.booking.BookingRoutes
import com.parkease.feature.booking.bookingGraph
import com.parkease.feature.earnings.EarningsRoutes
import com.parkease.feature.earnings.earningsGraph
import com.parkease.feature.notifications.NotificationsRoutes
import com.parkease.feature.notifications.notificationsGraph
import com.parkease.feature.ownerparking.OwnerParkingRoutes
import com.parkease.feature.ownerparking.ownerParkingGraph

private const val LOADING_ROUTE = "loading"
private const val OWNER_HOME_ROUTE = "owner-home"

/** Same page feature:auth's RegisterScreen links to, at #privacy / #terms. */
private const val LEGAL_URL = "https://claude.ai/code/artifact/f5ce0140-a291-4052-a126-482dee0b6246"

/**
 * Root of the Partner app's navigation graph. Serves two audiences —
 * owners and attendants — but unlike the old single-app shell there's no
 * Welcome-screen card to pick between them: which one a signed-in account
 * lands on comes from its real roles (RootNavViewModel.resolveLanding),
 * never a client-side choice. Admin is still reachable (spec:
 * rohitrreddy@gmail.com gets an optional quick-switch from either app) via
 * a link in the owner home's account footer — AdminHomeScreen's own
 * dashboardSummary() call is what actually gates access.
 */
@Composable
fun RootNavHost(modifier: Modifier = Modifier, viewModel: RootNavViewModel = hiltViewModel()) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val landing by viewModel.landing.collectAsStateWithLifecycle()
    val becomeOwnerState by viewModel.becomeOwnerState.collectAsStateWithLifecycle()
    val deleteAccountState by viewModel.deleteAccountState.collectAsStateWithLifecycle()
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    val navController = rememberNavController()
    val context = LocalContext.current

    fun openPrivacyPolicy() {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$LEGAL_URL#privacy")))
    }

    // Single effect for every transition: initial resolve once both
    // isLoggedIn and (when true) landing have real values, sign-out, and a
    // session expiring mid-use.
    LaunchedEffect(isLoggedIn, landing) {
        val target = when (isLoggedIn) {
            null -> return@LaunchedEffect
            false -> AuthRoutes.GRAPH
            true -> when (landing) {
                null -> return@LaunchedEffect
                is PartnerLanding.Owner -> OWNER_HOME_ROUTE
                is PartnerLanding.AttendantOnly -> AttendantRoutes.GRAPH
            }
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
            // Which home this lands on is re-resolved from isLoggedIn
            // flipping true (see RootNavViewModel) — nothing further to do here.
        }
        composable(OWNER_HOME_ROUTE) {
            OwnerHomeScreen(
                becomeOwnerError = (becomeOwnerState as? BecomeOwnerState.Error)?.message,
                onMyListings = { navController.navigate(OwnerParkingRoutes.GRAPH) },
                onBookings = { navController.navigate(BookingRoutes.OWNER_LIST) },
                onEarnings = { navController.navigate(EarningsRoutes.GRAPH) },
                onAttendantTools = { navController.navigate(AttendantRoutes.GRAPH) },
                onAdmin = { navController.navigate(AdminRoutes.GRAPH) },
                onSignOut = { viewModel.signOut() },
                onPrivacyPolicy = { openPrivacyPolicy() },
                onDeleteAccount = { showDeleteAccountConfirm = true },
            )
        }
        // AttendantOnly accounts land here directly (no owner dashboard
        // shell around it) — onSignOut is what makes that work as a real
        // root screen rather than a dead end with no way back to auth.
        attendantGraph(navController, onSignOut = { viewModel.signOut() })
        adminGraph(navController, onSignOut = { viewModel.signOut() })
        ownerParkingGraph(navController)
        bookingGraph(navController)
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
        AlertDialog(
            onDismissRequest = { viewModel.clearDeleteAccountError() },
            confirmButton = { TextButton(onClick = { viewModel.clearDeleteAccountError() }) { Text("OK") } },
            title = { Text("Couldn't delete account") },
            text = { Text(it.message) },
        )
    }
}
