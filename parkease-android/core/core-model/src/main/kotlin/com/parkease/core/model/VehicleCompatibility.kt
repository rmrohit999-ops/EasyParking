package com.parkease.core.model

/**
 * Client-side mirror of the backend's `is_bookable(vehicle, section)`
 * predicate (Milestone 0 §6). IMPORTANT: this is UI-only — it decides
 * whether to *show* a section as bookable and which reason to display when
 * it isn't. It is never the authority: the backend re-validates the same
 * predicate server-side on search, hold-creation, and booking-confirm, and
 * the server's answer always wins if the two ever disagree (e.g. because a
 * cached search result went stale).
 */
data class SectionCompatibilitySummary(
    val sectionVehicleCategory: VehicleCategory,
    val supportedVehicleTypes: Set<VehicleType>,
    val maxVehicleSize: VehicleSize?,
    val sectionActive: Boolean,
    val sectionApproved: Boolean,
    val availableCount: Int,
)

enum class IncompatibilityReason {
    CATEGORY_MISMATCH,
    VEHICLE_TYPE_UNSUPPORTED,
    SIZE_INCOMPATIBLE,
    SECTION_INACTIVE,
    NO_AVAILABILITY,
}

/**
 * Returns null when bookable, or the specific reason it isn't — so the UI
 * can show *why*, per the Milestone 0 requirement that an incompatible
 * vehicle's screen explains the mismatch rather than just hiding the option.
 */
fun checkCompatibility(
    vehicleCategory: VehicleCategory,
    vehicleType: VehicleType,
    vehicleSize: VehicleSize?,
    section: SectionCompatibilitySummary,
): IncompatibilityReason? {
    if (vehicleCategory != section.sectionVehicleCategory) return IncompatibilityReason.CATEGORY_MISMATCH
    if (vehicleType !in section.supportedVehicleTypes) return IncompatibilityReason.VEHICLE_TYPE_UNSUPPORTED
    if (section.maxVehicleSize != null && vehicleSize != null && vehicleSize.ordinal > section.maxVehicleSize.ordinal) {
        return IncompatibilityReason.SIZE_INCOMPATIBLE
    }
    if (!section.sectionActive || !section.sectionApproved) return IncompatibilityReason.SECTION_INACTIVE
    if (section.availableCount <= 0) return IncompatibilityReason.NO_AVAILABILITY
    return null
}
