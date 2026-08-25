@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.booking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parkease.core.maps.MapPin
import com.parkease.core.maps.MapRoute
import com.parkease.core.maps.OsmMap
import com.parkease.core.maps.PinColor
import com.parkease.core.maps.RouteStyle
import com.parkease.core.maps.launchNavigation
import org.osmdroid.util.GeoPoint

@Composable
fun ActiveSessionScreen(
    onBack: () -> Unit,
    onViewPass: (bookingId: String) -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("You're Parked") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        val booking = uiState.booking
        when {
            uiState.isLoading && booking == null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            booking == null -> Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(uiState.errorMessage ?: "Couldn't load this booking.", color = MaterialTheme.colorScheme.error)
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(booking.parkingName ?: "Parking", style = MaterialTheme.typography.titleMedium)
                        Text(
                            formatElapsed(uiState.elapsedSeconds),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("Time parked", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                val car = uiState.carLocation
                val driverLat = uiState.driverLatitude
                val driverLng = uiState.driverLongitude
                if (car != null) {
                    Text("Walk back to my car", style = MaterialTheme.typography.titleSmall)
                    OsmMap(
                        cameraCenter = GeoPoint(driverLat ?: car.latitude, driverLng ?: car.longitude),
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        initialZoom = 17.0,
                        myLocationEnabled = true,
                        pins = listOf(MapPin(id = "car", position = GeoPoint(car.latitude, car.longitude), title = "Your car", color = PinColor.BLUE)),
                        routes = uiState.walkBackRoute?.let { listOf(MapRoute(points = it, style = RouteStyle.WALK_BACK)) } ?: emptyList(),
                    )
                    uiState.walkBackDistanceMeters?.let { distance ->
                        val distanceLabel = if (distance < 1000) "${distance.toInt()} m" else "${"%.1f".format(distance / 1000)} km"
                        val approxNote = if (uiState.walkBackIsApproximate) " (approximate)" else ""
                        Text(
                            "$distanceLabel away · ~${viewModel.walkingEtaMinutes(distance)} min walk$approxNote",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    OutlinedButton(
                        onClick = { launchNavigation(context, car.latitude, car.longitude, "Your car") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Directions to My Car")
                    }
                } else {
                    Text(
                        "We couldn't record your car's location yet — enable location access to use walk-back navigation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(onClick = { onViewPass(booking.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("View Entry Pass")
                }
            }
        }
    }
}

internal fun formatElapsed(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
