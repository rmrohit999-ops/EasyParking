package com.parkease.feature.ownerparking

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.parkease.feature.ownerparking.ui.AddSectionScreen
import com.parkease.feature.ownerparking.ui.AttendantsScreen
import com.parkease.feature.ownerparking.ui.CreateListingScreen
import com.parkease.feature.ownerparking.ui.ListingDetailScreen
import com.parkease.feature.ownerparking.ui.LocationFormScreen
import com.parkease.feature.ownerparking.ui.MyListingsScreen
import com.parkease.feature.ownerparking.ui.PhotosScreen

object OwnerParkingRoutes {
    const val GRAPH = "owner-parking"
    const val LISTINGS = "owner-parking/listings"
    const val CREATE_LISTING = "owner-parking/create"
    const val LISTING_DETAIL_PATTERN = "owner-parking/listing/{listingId}"
    const val LOCATION_FORM_PATTERN = "owner-parking/listing/{listingId}/location"
    // sectionId is an optional query param (String, nullable — Nav-Compose
    // has no nullable path segment) so this one route serves both "add" (no
    // sectionId) and "edit" (sectionId present) — see AddSectionViewModel's
    // doc comment.
    const val ADD_SECTION_PATTERN = "owner-parking/listing/{listingId}/add-section?sectionId={sectionId}"
    const val PHOTOS_PATTERN = "owner-parking/listing/{listingId}/photos"
    const val ATTENDANTS_PATTERN = "owner-parking/listing/{listingId}/attendants"

    fun listingDetail(listingId: String) = "owner-parking/listing/$listingId"
    fun locationForm(listingId: String) = "owner-parking/listing/$listingId/location"
    fun addSection(listingId: String) = "owner-parking/listing/$listingId/add-section"
    fun editSection(listingId: String, sectionId: String) = "owner-parking/listing/$listingId/add-section?sectionId=$sectionId"
    fun photos(listingId: String) = "owner-parking/listing/$listingId/photos"
    fun attendants(listingId: String) = "owner-parking/listing/$listingId/attendants"
}

/**
 * Owner-facing parking management: create a listing, pin its location, add
 * sections, upload photos, submit for admin review, and toggle
 * active/paused/closed once approved. Public entry point mirrors
 * feature:auth/feature:vehicles' *Graph pattern.
 */
fun NavGraphBuilder.ownerParkingGraph(navController: NavController) {
    navigation(startDestination = OwnerParkingRoutes.LISTINGS, route = OwnerParkingRoutes.GRAPH) {
        composable(OwnerParkingRoutes.LISTINGS) {
            MyListingsScreen(
                onCreateListing = { navController.navigate(OwnerParkingRoutes.CREATE_LISTING) },
                onOpenListing = { listingId -> navController.navigate(OwnerParkingRoutes.listingDetail(listingId)) },
            )
        }
        composable(OwnerParkingRoutes.CREATE_LISTING) {
            CreateListingScreen(
                onCreated = { listingId ->
                    navController.navigate(OwnerParkingRoutes.listingDetail(listingId)) {
                        popUpTo(OwnerParkingRoutes.LISTINGS)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = OwnerParkingRoutes.LISTING_DETAIL_PATTERN,
            arguments = listOf(navArgument("listingId") { type = NavType.StringType }),
        ) {
            ListingDetailScreen(
                onEditLocation = { listingId -> navController.navigate(OwnerParkingRoutes.locationForm(listingId)) },
                onAddSection = { listingId -> navController.navigate(OwnerParkingRoutes.addSection(listingId)) },
                onEditSection = { listingId, sectionId -> navController.navigate(OwnerParkingRoutes.editSection(listingId, sectionId)) },
                onManagePhotos = { listingId -> navController.navigate(OwnerParkingRoutes.photos(listingId)) },
                onManageAttendants = { listingId -> navController.navigate(OwnerParkingRoutes.attendants(listingId)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = OwnerParkingRoutes.LOCATION_FORM_PATTERN,
            arguments = listOf(navArgument("listingId") { type = NavType.StringType }),
        ) {
            LocationFormScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = OwnerParkingRoutes.ADD_SECTION_PATTERN,
            arguments = listOf(
                navArgument("listingId") { type = NavType.StringType },
                navArgument("sectionId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            AddSectionScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = OwnerParkingRoutes.PHOTOS_PATTERN,
            arguments = listOf(navArgument("listingId") { type = NavType.StringType }),
        ) {
            PhotosScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = OwnerParkingRoutes.ATTENDANTS_PATTERN,
            arguments = listOf(navArgument("listingId") { type = NavType.StringType }),
        ) {
            AttendantsScreen(onBack = { navController.popBackStack() })
        }
    }
}
