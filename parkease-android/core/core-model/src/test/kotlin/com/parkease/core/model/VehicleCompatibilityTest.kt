package com.parkease.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VehicleCompatibilityTest {

    private val fourWheelerSection = SectionCompatibilitySummary(
        sectionVehicleCategory = VehicleCategory.FOUR_WHEELER,
        supportedVehicleTypes = setOf(VehicleType.CAR, VehicleType.SUV, VehicleType.EV),
        maxVehicleSize = VehicleSize.LARGE,
        sectionActive = true,
        sectionApproved = true,
        availableCount = 3,
    )

    @Test
    fun `a two-wheeler cannot book a four-wheeler-only section`() {
        val reason = checkCompatibility(
            vehicleCategory = VehicleCategory.TWO_WHEELER,
            vehicleType = VehicleType.SCOOTER,
            vehicleSize = null,
            section = fourWheelerSection,
        )
        assertThat(reason).isEqualTo(IncompatibilityReason.CATEGORY_MISMATCH)
    }

    @Test
    fun `a four-wheeler cannot book a two-wheeler-only section`() {
        val twoWheelerSection = fourWheelerSection.copy(
            sectionVehicleCategory = VehicleCategory.TWO_WHEELER,
            supportedVehicleTypes = setOf(VehicleType.BIKE, VehicleType.SCOOTER),
        )
        val reason = checkCompatibility(
            vehicleCategory = VehicleCategory.FOUR_WHEELER,
            vehicleType = VehicleType.CAR,
            vehicleSize = null,
            section = twoWheelerSection,
        )
        assertThat(reason).isEqualTo(IncompatibilityReason.CATEGORY_MISMATCH)
    }

    @Test
    fun `matching category and type with availability is bookable`() {
        val reason = checkCompatibility(
            vehicleCategory = VehicleCategory.FOUR_WHEELER,
            vehicleType = VehicleType.CAR,
            vehicleSize = VehicleSize.MEDIUM,
            section = fourWheelerSection,
        )
        assertThat(reason).isNull()
    }

    @Test
    fun `oversized vehicle is size-incompatible`() {
        val reason = checkCompatibility(
            vehicleCategory = VehicleCategory.FOUR_WHEELER,
            vehicleType = VehicleType.SUV,
            vehicleSize = VehicleSize.EXTRA_LARGE,
            section = fourWheelerSection,
        )
        assertThat(reason).isEqualTo(IncompatibilityReason.SIZE_INCOMPATIBLE)
    }

    @Test
    fun `zero availability blocks booking even when compatible`() {
        val reason = checkCompatibility(
            vehicleCategory = VehicleCategory.FOUR_WHEELER,
            vehicleType = VehicleType.CAR,
            vehicleSize = VehicleSize.MEDIUM,
            section = fourWheelerSection.copy(availableCount = 0),
        )
        assertThat(reason).isEqualTo(IncompatibilityReason.NO_AVAILABILITY)
    }

    @Test
    fun `inactive section blocks booking even when otherwise compatible`() {
        val reason = checkCompatibility(
            vehicleCategory = VehicleCategory.FOUR_WHEELER,
            vehicleType = VehicleType.CAR,
            vehicleSize = VehicleSize.MEDIUM,
            section = fourWheelerSection.copy(sectionActive = false),
        )
        assertThat(reason).isEqualTo(IncompatibilityReason.SECTION_INACTIVE)
    }

    @Test
    fun `unsupported vehicle type within the right category is rejected`() {
        val reason = checkCompatibility(
            vehicleCategory = VehicleCategory.FOUR_WHEELER,
            vehicleType = VehicleType.OTHER,
            vehicleSize = null,
            section = fourWheelerSection,
        )
        assertThat(reason).isEqualTo(IncompatibilityReason.VEHICLE_TYPE_UNSUPPORTED)
    }

    @Test
    fun `booking status terminal classification matches backend allow-list`() {
        assertThat(BookingStatus.COMPLETED.isTerminal).isTrue()
        assertThat(BookingStatus.REJECTED.isTerminal).isTrue()
        assertThat(BookingStatus.PARKING_ACTIVE.isTerminal).isFalse()
        assertThat(BookingStatus.CONFIRMED.isTerminal).isFalse()
    }
}
