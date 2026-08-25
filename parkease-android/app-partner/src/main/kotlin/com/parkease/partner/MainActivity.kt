package com.parkease.partner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.parkease.partner.navigation.RootNavHost
import com.parkease.core.ui.ParkEaseTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the Partner app. RootNavHost owns the real
 * graph: auth when signed out, an owner or attendant landing once
 * authenticated depending on the account's actual roles.
 *
 * Unlike app-driver's MainActivity, this one does NOT implement Razorpay's
 * PaymentResultWithDataListener — owners never open a checkout sheet
 * (cash confirmation and payouts don't go through Razorpay Checkout), so
 * there's nothing for that interface to receive here.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParkEaseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Scaffold { innerPadding ->
                        RootNavHost(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}
