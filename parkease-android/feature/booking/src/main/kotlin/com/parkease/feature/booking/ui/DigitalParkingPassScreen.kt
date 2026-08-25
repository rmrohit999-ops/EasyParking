@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.feature.booking.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.parkease.core.model.BookingStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM, hh:mm a")

/**
 * Full-screen digital entry pass: a real scannable QR rendered from
 * PassUi.token (ZXing, on-device — no external QR-rendering service),
 * shown to a gate attendant at check-in/check-out. Reuses
 * BookingDetailViewModel's existing pass/passLoading/passMessage state
 * instead of a parallel ViewModel — BookingDetailScreen already renders
 * pass.token as a compact monospace fallback inline; this screen is the
 * dedicated, large-QR presentation of that same real data.
 *
 * Note: this screen gets its OWN BookingDetailViewModel instance
 * (nav-graph destinations don't share ViewModel instances by default in
 * this codebase's hiltViewModel() usage) — it independently loads the
 * same booking via the same bookingId, it does not carry over in-memory
 * state from a screen the driver was just on. That's a real, if slightly
 * redundant, network call, not fake/stale data.
 */
@Composable
fun DigitalParkingPassScreen(
    onBack: () -> Unit,
    viewModel: BookingDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(uiState.booking, uiState.pass, uiState.passLoading) {
        val status = uiState.booking?.status
        val canShow = status == BookingStatus.CONFIRMED || status == BookingStatus.DRIVER_ARRIVING
        if (canShow && uiState.pass == null && !uiState.passLoading) {
            viewModel.showPass()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entry Pass") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Close") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val booking = uiState.booking
            val status = booking?.status
            val canShow = status == BookingStatus.CONFIRMED || status == BookingStatus.DRIVER_ARRIVING

            when {
                uiState.isLoading && booking == null -> {
                    Spacer(Modifier.height(64.dp))
                    CircularProgressIndicator()
                }
                booking == null -> {
                    Text(
                        uiState.errorMessage ?: "Couldn't load this booking.",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                !canShow -> {
                    Spacer(Modifier.height(48.dp))
                    Text(
                        "The entry pass is available once your booking is confirmed.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                else -> {
                    Text(booking.parkingName ?: "Parking", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    booking.vehicleRegistrationNumber?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    val pass = uiState.pass
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            when {
                                uiState.passLoading -> {
                                    Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                                pass != null -> {
                                    val qrBitmap = remember(pass.token) { encodeQrBitmap(pass.token, 720) }
                                    if (qrBitmap != null) {
                                        Image(
                                            bitmap = qrBitmap.asImageBitmap(),
                                            contentDescription = "Entry pass QR code",
                                            modifier = Modifier.size(240.dp),
                                        )
                                    } else {
                                        Text("Couldn't render the QR code — use the code below instead.", style = MaterialTheme.typography.bodySmall)
                                    }
                                    SelectionContainer {
                                        Text(
                                            pass.token,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                    }
                                    pass.expiresAt?.let {
                                        Text(
                                            "Valid until ${TIME_FORMAT.format(it.atZone(ZoneId.systemDefault()))}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                else -> {
                                    Text("Preparing your pass…", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    uiState.passMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Text(
                        "Show this screen to the attendant at check-in and check-out.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )

                    booking.startTime?.let { start ->
                        booking.endTime?.let { end ->
                            Text(
                                "${TIME_FORMAT.format(start.atZone(ZoneId.systemDefault()))} — ${TIME_FORMAT.format(end.atZone(ZoneId.systemDefault()))}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Pure ZXing wrapper: real QR encoding, no network call, on-device only. Returns null on encode failure (e.g. an unexpectedly huge token) rather than crashing the screen. */
private fun encodeQrBitmap(content: String, sizePx: Int): Bitmap? {
    return try {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val black = androidx.compose.ui.graphics.Color.Black.toArgb()
        val white = androidx.compose.ui.graphics.Color.White.toArgb()
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) black else white)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
