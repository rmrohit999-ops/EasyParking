package com.parkease.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.parkease.app.navigation.RootNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. RootNavHost (navigation/RootNavHost.kt) owns the
 * real graph: the auth graph (feature:auth, Milestone 2) when signed out,
 * a role-appropriate home once authenticated.
 *
 * Disclosed Milestone 11 scope limit: ParkEaseMessagingService attaches a
 * `deep_link` extra (the notification's server-supplied deep link, e.g.
 * "parkease://support/tickets/{id}") to the intent that opens this
 * Activity when a user taps a push notification, but nothing here reads
 * that extra yet — there is no deep-link-to-NavController-route resolver
 * built for it, so tapping a push notification opens the app to its normal
 * start screen rather than the specific booking/ticket/dispute it's about.
 * The in-app notification inbox (feature:notifications) has the same gap
 * on tap. Wiring a real resolver (parsing the URI, mapping each path shape
 * to the owning feature module's NavController route) is real, scoped
 * work of its own, not something to half-build here.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Scaffold { innerPadding ->
                        RootNavHost(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}
