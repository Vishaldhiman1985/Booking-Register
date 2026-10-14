package com.example.bookingregister.booking.domain

/** Local availability rule; server-side room-night locks still protect concurrent devices. */
object BookingRoomNightAvailabilityPolicy {
    data class Occupant(
        val bookingId: String,
        val checkInMillis: Long,
        val checkOutMillis: Long,
        val legacyRoomIds: List<String>,
        val legacyPropertyId: String?,
        val explicitAssignments: List<BookingRoomNightAssignment> = emptyList(),
        val cancelled: Boolean = false,
        val deleted: Boolean = false
    )

    fun isOccupied(roomId: String, nightMillis: Long, occupants: List<Occupant>, excludeBookingId: String? = null): Boolean =
        occupants.any { booking ->
            if (booking.deleted || booking.cancelled || booking.bookingId == excludeBookingId ||
                nightMillis < booking.checkInMillis || nightMillis >= booking.checkOutMillis) return@any false
            val plan = BookingRoomPlanResolver.resolve(
                booking.checkInMillis, booking.checkOutMillis,
                booking.legacyRoomIds, booking.legacyPropertyId, booking.explicitAssignments
            )
            val occupiedInPlan = plan.assignments.any {
                it.businessDateMillis == nightMillis && it.roomRemoteId == roomId
            }
            // Invalid plans fail closed: neither legacy nor explicitly named rooms are released.
            if (plan.isValid) occupiedInPlan else occupiedInPlan || roomId in booking.legacyRoomIds
        }

    fun conflicts(requestedBookingId: String, targetAssignments: List<BookingRoomNightAssignment>, occupants: List<Occupant>): Boolean =
        targetAssignments.any { isOccupied(it.roomRemoteId, it.businessDateMillis, occupants, requestedBookingId) }
}
