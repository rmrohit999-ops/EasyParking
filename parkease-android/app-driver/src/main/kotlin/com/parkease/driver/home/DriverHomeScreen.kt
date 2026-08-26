@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.parkease.driver.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.parkease.core.location.GeocodedPlace
import com.parkease.core.location.LocationPermissionState
import com.parkease.core.maps.MapPin
import com.parkease.core.maps.MapRoute
import com.parkease.core.maps.OsmMap
import com.parkease.core.maps.PinColor
import com.parkease.core.maps.RouteStyle
import com.parkease.core.maps.launchNavigation
import com.parkease.core.maps.radiusCircle
import com.parkease.core.model.VehicleCategory
import com.parkease.feature.driversearch.data.ListingResultUi
import com.parkease.feature.driversearch.data.SectionResultUi
import kotlin.math.roundToInt
import org.osmdroid.util.GeoPoint

/**
 * The driver app's real landing screen (wired in place of the old plain
 * CustomerHomeScreen — see app-driver's RootNavHost). Interactive 2W/4W
 * category switcher, 1km-default-radius map with occupancy-colored pins,
 * and a nearby carousel beneath it, all backed by the exact same
 * DiscoveryRepository feature:driver-search's own SearchScreen uses.
 *
 * Known, disclosed scope limits (not attempted here):
 *  - Map pins are colored by availableCount thresholds, not a true
 *    capacity-based percentage — SectionResultUi's search response
 *    doesn't carry each section's total capacity, only how many are free.
 *  - "X min walk" is a straight-line estimate at an average walking pace,
 *    not a real routed walking-directions duration (that needs the
 *    Directions API, which per MapsQuotaService's own doc comment isn't
 *    wired into this app yet).
 */
@Composable
fun DriverHomeScreen(
    onBookSection: (sectionId: String, isInstant: Boolean) -> Unit,
    onBookAdvance: (sectionId: String, startEpochMillis: Long, endEpochMillis: Long) -> Unit,
    onMyBookings: () -> Unit,
    onMyVehicles: () -> Unit,
    onNotifications: () -> Unit,
    onAdmin: () -> Unit,
    onSignOut: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onDeleteAccount: () -> Unit,
    viewModel: DriverHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showAdvanceBooking by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) { viewModel.checkPermissionAlreadyGranted() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ParkEase") },
                actions = {
                    IconButton(onClick = { viewModel.refreshLocationAndSearch() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    TextButton(onClick = { showAdvanceBooking = true }) { Text("Book in Advance") }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(text = { Text("My Bookings") }, onClick = { showOverflowMenu = false; onMyBookings() })
                            DropdownMenuItem(text = { Text("My Vehicles") }, onClick = { showOverflowMenu = false; onMyVehicles() })
                            DropdownMenuItem(text = { Text("Notifications") }, onClick = { showOverflowMenu = false; onNotifications() })
                            DropdownMenuItem(text = { Text("Admin") }, onClick = { showOverflowMenu = false; onAdmin() })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Privacy & Terms") }, onClick = { showOverflowMenu = false; onPrivacyPolicy() })
                            DropdownMenuItem(text = { Text("Delete account") }, onClick = { showOverflowMenu = false; onDeleteAccount() })
                            DropdownMenuItem(text = { Text("Sign out") }, onClick = { showOverflowMenu = false; onSignOut() })
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (uiState.permissionState) {
            LocationPermissionState.GRANTED -> Unit
            LocationPermissionState.NOT_REQUESTED, LocationPermissionState.DENIED -> {
                PermissionRationale(
                    modifier = Modifier.padding(padding),
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    },
                )
                return@Scaffold
            }
            LocationPermissionState.DENIED_PERMANENTLY -> {
                Text(
                    "Location access is off. Enable it in system settings to see nearby parking.",
                    modifier = Modifier.padding(padding).padding(24.dp),
                )
                return@Scaffold
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            CategorySwitcher(selected = uiState.selectedCategory, onSelect = viewModel::setCategory)
            AddressSearchRow(
                query = uiState.addressQuery,
                isSearching = uiState.isSearchingAddress,
                matches = uiState.addressMatches,
                searchCenterLabel = uiState.searchCenterLabel,
                currentLocationAddress = uiState.currentLocationAddress,
                driverLatitude = uiState.driverLatitude,
                driverLongitude = uiState.driverLongitude,
                onQueryChange = viewModel::setAddressQuery,
                onSearch = viewModel::searchAddress,
                onSelectMatch = viewModel::selectAddressMatch,
                onClear = viewModel::clearSearchedAddress,
            )
            RadiusChips(
                selected = uiState.radiusMeters,
                favoritesOnly = uiState.favoritesOnly,
                onSelect = viewModel::setRadius,
                onFavoritesOnlyChanged = viewModel::setFavoritesOnly,
            )

            val visibleResults = if (uiState.favoritesOnly) uiState.results.filter { it.isFavorite } else uiState.results
            val driverLat = uiState.driverLatitude
            val driverLng = uiState.driverLongitude
            // Real spec requirement: the map is the primary surface even
            // with zero results or mid-search — the empty/loading states
            // overlay it as cards rather than replacing it with blank text,
            // so "Move the map to search another area" always has a map to
            // move.
            val showSearchThisArea = uiState.pannedCenter?.let { panned ->
                val centerLat = uiState.searchCenterLatitude
                val centerLng = uiState.searchCenterLongitude
                centerLat != null && centerLng != null &&
                    haversineMetersLocal(panned.latitude, panned.longitude, centerLat, centerLng) > 300.0
            } ?: false

            Box(modifier = Modifier.weight(1f)) {
                if (driverLat == null || driverLng == null) {
                    if (uiState.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        LocationUnavailableState(
                            message = uiState.errorMessage,
                            onRetry = { viewModel.refreshLocationAndSearch() },
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        OsmMap(
                            pins = visibleResults.map { it.toMapPin() },
                            cameraCenter = GeoPoint(uiState.searchCenterLatitude ?: driverLat, uiState.searchCenterLongitude ?: driverLng),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            initialZoom = zoomForRadius(uiState.radiusMeters),
                            myLocationEnabled = true,
                            polygons = listOfNotNull(
                                uiState.searchCenterLatitude?.let { lat ->
                                    uiState.searchCenterLongitude?.let { lng -> radiusCircle(GeoPoint(lat, lng), uiState.radiusMeters.toDouble()) }
                                },
                            ),
                            routes = uiState.routeToSelectedListing?.let {
                                listOf(MapRoute(points = it, style = RouteStyle.ROUTE_TO_ENTRANCE))
                            } ?: emptyList(),
                            onPinClick = { listingId -> viewModel.selectListing(listingId) },
                            onCameraMoved = viewModel::onMapPanned,
                            onRecenterClick = { viewModel.refreshLocationAndSearch() },
                        )

                        if (uiState.isLoading && uiState.results.isEmpty()) {
                            LoadingCarouselSkeleton()
                        } else if (visibleResults.isEmpty() && uiState.favoritesOnly) {
                            OverlayCard("No favorites in this area yet. Tap the heart on a listing to save it.")
                        } else if (uiState.results.isEmpty() && !uiState.isLoading) {
                            EmptyResultsCard(
                                category = uiState.selectedCategory,
                                radiusMeters = uiState.radiusMeters,
                                onExpandRadius = {
                                    val next = RADIUS_CHOICES_METERS.firstOrNull { it > uiState.radiusMeters }
                                    if (next != null) viewModel.setRadius(next)
                                },
                            )
                        } else {
                            Text(
                                "Nearby",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(bottom = 16.dp),
                            ) {
                                items(visibleResults, key = { it.id }) { listing ->
                                    NearbyCard(
                                        listing = listing,
                                        onClick = { viewModel.selectListing(listing.id) },
                                        onToggleFavorite = { viewModel.toggleFavorite(listing.id) },
                                    )
                                }
                            }
                        }
                    }

                    if (showSearchThisArea) {
                        ElevatedButton(
                            onClick = { viewModel.searchThisArea() },
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Search this area")
                        }
                    }

                    uiState.errorMessage?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        )
                    }
                }
            }
        }
    }

    val selectedListing = uiState.results.firstOrNull { it.id == uiState.selectedListingId }
    if (selectedListing != null) {
        ListingPreviewSheet(
            listing = selectedListing,
            drivingDistanceMeters = uiState.routeToSelectedListingDistanceMeters,
            drivingDurationSeconds = uiState.routeToSelectedListingDurationSeconds,
            onDismiss = { viewModel.selectListing(null) },
            onToggleFavorite = { viewModel.toggleFavorite(selectedListing.id) },
            onBookSection = { sectionId, isInstant ->
                viewModel.selectListing(null)
                onBookSection(sectionId, isInstant)
            },
        )
    }

    if (showAdvanceBooking) {
        AdvanceBookingBottomSheet(
            defaultCategory = uiState.selectedCategory,
            onDismiss = { showAdvanceBooking = false },
            onBookAdvance = { sectionId, start, end ->
                showAdvanceBooking = false
                onBookAdvance(sectionId, start, end)
            },
        )
    }
}

private fun zoomForRadius(radiusMeters: Int): Double = when {
    radiusMeters <= 500 -> 16.0
    radiusMeters <= 1000 -> 15.0
    radiusMeters <= 3000 -> 13.0
    else -> 12.0
}

private fun ListingResultUi.toMapPin(): MapPin {
    val section = sections.firstOrNull()
    val color = section?.let { colorForAvailability(it.availableCount) } ?: PinColor.RED
    val priceLabel = section?.let { "${it.hourlyRate.toDisplayString()}/hr" }
    return MapPin(
        id = id,
        position = GeoPoint(latitude, longitude),
        title = "$name${priceLabel?.let { " · $it" } ?: ""}",
        snippet = section?.let { "${it.availableCount} slot${if (it.availableCount == 1) "" else "s"} left" },
        color = color,
    )
}

private fun colorForAvailability(availableCount: Int): PinColor = when {
    availableCount <= 0 -> PinColor.RED
    availableCount <= 2 -> PinColor.ORANGE
    else -> PinColor.GREEN
}

@Composable
private fun CategorySwitcher(selected: VehicleCategory, onSelect: (VehicleCategory) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryCard(
            label = "4-Wheeler",
            subtitle = "Car · SUV · EV",
            icon = Icons.Default.DirectionsCar,
            selected = selected == VehicleCategory.FOUR_WHEELER,
            onClick = { onSelect(VehicleCategory.FOUR_WHEELER) },
            modifier = Modifier.weight(1f),
        )
        CategoryCard(
            label = "2-Wheeler",
            subtitle = "Bike · Scooter · EV",
            icon = Icons.Default.TwoWheeler,
            selected = selected == VehicleCategory.TWO_WHEELER,
            onClick = { onSelect(VehicleCategory.TWO_WHEELER) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CategoryCard(
    label: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsStateCompat(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Thin wrapper so CategoryCard's tint animates smoothly on switch without importing animateColorAsState's experimental variance across compose versions directly into the public API. */
@Composable
private fun animateColorAsStateCompat(target: Color) =
    androidx.compose.animation.animateColorAsState(target, label = "categoryCardColor")

@Composable
private fun RadiusChips(
    selected: Int,
    favoritesOnly: Boolean,
    onSelect: (Int) -> Unit,
    onFavoritesOnlyChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RADIUS_CHOICES_METERS.forEach { radius ->
            FilterChip(
                selected = selected == radius,
                onClick = { onSelect(radius) },
                label = { Text(if (radius < 1000) "${radius}m" else "${radius / 1000}km") },
            )
        }
        FilterChip(
            selected = favoritesOnly,
            onClick = { onFavoritesOnlyChanged(!favoritesOnly) },
            leadingIcon = {
                Icon(
                    if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            label = { Text("Favorites") },
        )
    }
}

@Composable
private fun AddressSearchRow(
    query: String,
    isSearching: Boolean,
    matches: List<GeocodedPlace>,
    searchCenterLabel: String?,
    currentLocationAddress: String?,
    driverLatitude: Double?,
    driverLongitude: Double?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectMatch: (GeocodedPlace) -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search an address or area") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchCenterLabel != null) {
                    IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "Back to my location") }
                } else {
                    TextButton(onClick = onSearch, enabled = query.isNotBlank()) { Text("Go") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        matches.forEach { place ->
            TextButton(onClick = { onSelectMatch(place) }, modifier = Modifier.fillMaxWidth()) {
                Text(place.label, modifier = Modifier.fillMaxWidth())
            }
        }
        // Never leave the location field blank: an explicit search shows
        // where it's centered; otherwise fall back to the reverse-geocoded
        // current position, and if even that failed, the raw coordinates —
        // "Current Location: 12.9716, 77.5946" is still better than nothing.
        val label = searchCenterLabel?.let { "Showing results near \"$it\"" }
            ?: currentLocationAddress?.let { "Current Location: $it" }
            ?: driverLatitude?.let { lat -> driverLongitude?.let { lng -> "Current Location: %.4f, %.4f".format(lat, lng) } }
        label?.let {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PermissionRationale(modifier: Modifier = Modifier, onRequestPermission: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("ParkEase needs your location to find nearby parking.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequestPermission) { Text("Enable Location") }
    }
}

/** Shown only when we have never obtained a GPS fix at all (a prior successful fix is preserved and never wiped, so this never appears just because ONE refresh failed). */
@Composable
private fun LocationUnavailableState(message: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message ?: "We're finding your location…",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Retry")
        }
    }
}

/** Overlaid at the bottom of the (still-visible) map — "no results" never hides the map, since the driver still needs it to see nearby context and use "Search this area." */
@Composable
private fun EmptyResultsCard(category: VehicleCategory, radiusMeters: Int, onExpandRadius: () -> Unit) {
    val categoryLabel = if (category == VehicleCategory.TWO_WHEELER) "2-wheeler" else "4-wheeler"
    val radiusLabel = if (radiusMeters < 1000) "${radiusMeters}m" else "${radiusMeters / 1000}km"
    val nextRadius = RADIUS_CHOICES_METERS.firstOrNull { it > radiusMeters }
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "No $categoryLabel spots found within $radiusLabel.",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (nextRadius != null) {
                    val nextLabel = if (nextRadius < 1000) "${nextRadius}m" else "${nextRadius / 1000}km"
                    Button(onClick = onExpandRadius) { Text("Expand to $nextLabel") }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Or move the map to search another area.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OverlayCard(message: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), shape = RoundedCornerShape(16.dp)) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** A light shimmer row overlaid below the (already-visible) map while the first search for this position/radius/category is in flight — the map itself never disappears while loading. */
@Composable
private fun LoadingCarouselSkeleton() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(700), repeatMode = RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) {
            Box(
                modifier = Modifier.width(160.dp).height(100.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun NearbyCard(listing: ListingResultUi, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    val section = listing.sections.firstOrNull()
    ElevatedCard(onClick = onClick, modifier = Modifier.width(220.dp), shape = RoundedCornerShape(16.dp)) {
        Box {
            AsyncImage(
                model = listing.primaryPhotoUrl,
                contentDescription = listing.name,
                modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                contentScale = ContentScale.Crop,
            )
            IconButton(onClick = onToggleFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    if (listing.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (listing.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (listing.isFavorite) MaterialTheme.colorScheme.error else Color.White,
                )
            }
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(listing.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                RatingLabel(listing.averageRating, listing.ratingCount)
            }
            Text(formatDistanceAndWalk(listing.distanceMeters), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (section != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${section.hourlyRate.toDisplayString()}/hr", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    AvailabilityBadge(section.availableCount)
                }
            }
        }
    }
}

/** Real average of driver-submitted reviews only — renders nothing at all (not a fake "New" badge or 0-star icon) when ratingCount is 0, since "no reviews yet" isn't a rating. */
@Composable
private fun RatingLabel(averageRating: Double?, ratingCount: Int) {
    if (averageRating == null || ratingCount == 0) return
    Row(modifier = Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFA000), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(2.dp))
        Text("$averageRating ($ratingCount)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AvailabilityBadge(availableCount: Int) {
    val (label, color) = when {
        availableCount <= 0 -> "Full" to MaterialTheme.colorScheme.error
        availableCount <= 2 -> "$availableCount left" to Color(0xFFB26A00)
        else -> "$availableCount left" to MaterialTheme.colorScheme.primary
    }
    Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
}

private fun formatDistanceAndWalk(meters: Double): String {
    val distanceLabel = if (meters < 1000) "${meters.roundToInt()} m" else "${"%.1f".format(meters / 1000)} km"
    // Straight-line estimate at ~80m/min average walking pace — disclosed
    // in the file doc comment as not a real routed Directions ETA.
    val walkMinutes = (meters / 80).roundToInt().coerceAtLeast(1)
    return "$distanceLabel away · ~$walkMinutes min walk"
}

private fun formatKm(meters: Double): String = if (meters < 1000) "${meters.roundToInt()} m" else "${"%.1f".format(meters / 1000)} km"

/** Raw backend enum values (INDIVIDUAL/RESIDENTIAL/APARTMENT/COMMERCIAL/OFFICE/MALL/OTHER) formatted for display — no new data, just presentation. */
private fun parkingTypeLabel(parkingType: String): String = parkingType.lowercase().replaceFirstChar { it.uppercase() } + " Parking"

private fun vehicleCategoryLabel(category: VehicleCategory?): String = when (category) {
    VehicleCategory.TWO_WHEELER -> "2-Wheeler"
    VehicleCategory.FOUR_WHEELER -> "4-Wheeler"
    else -> "All Vehicles"
}

@Composable
private fun ListingPreviewSheet(
    listing: ListingResultUi,
    drivingDistanceMeters: Double?,
    drivingDurationSeconds: Double?,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onBookSection: (sectionId: String, isInstant: Boolean) -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (listing.primaryPhotoUrl != null) {
                AsyncImage(
                    model = listing.primaryPhotoUrl,
                    contentDescription = listing.name,
                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(listing.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    RatingLabel(listing.averageRating, listing.ratingCount)
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (listing.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (listing.isFavorite) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    )
                }
            }
            Text(listing.addressLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDistanceAndWalk(listing.distanceMeters), style = MaterialTheme.typography.bodyMedium)
            // Only shown once a real OSRM-routed result exists (never for
            // the straight-line fallback) — an honest, real driving figure
            // or nothing, never a guessed one.
            if (drivingDistanceMeters != null && drivingDurationSeconds != null) {
                Text(
                    "${formatKm(drivingDistanceMeters)} by road · ~${(drivingDurationSeconds / 60).roundToInt().coerceAtLeast(1)} min drive",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(parkingTypeLabel(listing.parkingType), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            listing.sections.firstOrNull()?.let { section ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BadgeChip(if (section.vehicleCategory == VehicleCategory.TWO_WHEELER) Icons.Default.TwoWheeler else Icons.Default.DirectionsCar, vehicleCategoryLabel(section.vehicleCategory))
                    if (section.hasSecurity) BadgeChip(Icons.Default.Security, "Security")
                    if (section.hasCctv) BadgeChip(Icons.Default.Videocam, "CCTV")
                }

                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("${section.hourlyRate.toDisplayString()}/hr", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(section.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AvailabilityBadge(section.availableCount)
                    }
                }

                OutlinedButton(
                    onClick = { launchNavigation(context, listing.latitude, listing.longitude, listing.name) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Navigate to Entrance")
                }

                Button(
                    onClick = { onBookSection(section.id, section.instantModeEnabled) },
                    enabled = section.availableCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (section.availableCount > 0) (if (section.instantModeEnabled) "Park Now" else "Reserve") else "Full")
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(icon: ImageVector, label: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        label = { Text(label) },
    )
}
