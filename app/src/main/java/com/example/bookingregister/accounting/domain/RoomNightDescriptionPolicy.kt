package com.example.bookingregister.accounting.domain

import com.example.bookingregister.booking.domain.BookingRoomPlanSource
import com.example.bookingregister.booking.domain.EffectiveBookingRoomPlan

/**
 * Presentation only. Never changes financial attribution or amounts.
 *
 * Returns an occupied-room label only when the allocation is explicit,
 * valid, and unambiguous for the financial line being described.
 *
 * Null means callers must preserve the existing billed-room description.
 */
object RoomNightDescriptionPolicy {

    fun occupiedRoomNameForCharge(
        plan: EffectiveBookingRoomPlan,
        businessDateMillis: Long,
        billedPropertyRemoteId: String?,
        financialLineCountForNight: Int,
        roomNamesById: Map<String, String>
    ): String? {
        if (!plan.isValid) return null
        if (plan.source != BookingRoomPlanSource.EXPLICIT_ASSIGNMENTS) return null
        if (financialLineCountForNight != 1) return null

        val occupied = plan.assignments.filter {
            it.businessDateMillis == businessDateMillis
        }

        if (occupied.size != 1) return null

        val assignment = occupied.single()

        // Cross-property occupancy must not silently change billing attribution.
        if (assignment.propertyRemoteId != billedPropertyRemoteId) return null

        return roomNamesById[assignment.roomRemoteId]
            ?.takeIf { it.isNotBlank() }
    }
}