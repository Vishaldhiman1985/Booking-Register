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

    /** A Simple Stay room must be free on every night, not merely on one night. */
    fun occupiedRoomIdsForStay(
        roomIds: Collection<String>,
        checkInMillis: Long,
        checkOutMillis: Long,
        occupants: List<Occupant>,
        excludeBookingId: String? = null
    ): Set<String> {
        val eligibleRoomIds = roomIds.toSet()
        val occupiedRoomIds = mutableSetOf<String>()
        occupants.forEach { booking ->
            if (booking.deleted || booking.cancelled || booking.bookingId == excludeBookingId ||
                booking.checkInMillis >= checkOutMillis || booking.checkOutMillis <= checkInMillis) return@forEach

            val plan = BookingRoomPlanResolver.resolve(
                booking.checkInMillis, booking.checkOutMillis,
                booking.legacyRoomIds, booking.legacyPropertyId, booking.explicitAssignments
            )
            plan.assignments.forEach { assignment ->
                if (assignment.businessDateMillis >= checkInMillis &&
                    assignment.businessDateMillis < checkOutMillis &&
                    assignment.businessDateMillis >= booking.checkInMillis &&
                    assignment.businessDateMillis < booking.checkOutMillis) {
                    occupiedRoomIds += assignment.roomRemoteId
                }
            }
            // Match the single-night rule when a stored plan is incomplete or invalid.
            if (!plan.isValid) occupiedRoomIds += booking.legacyRoomIds
        }
        return occupiedRoomIds.intersect(eligibleRoomIds)
    }

    fun conflicts(requestedBookingId: String, targetAssignments: List<BookingRoomNightAssignment>, occupants: List<Occupant>): Boolean =
        targetAssignments.any { isOccupied(it.roomRemoteId, it.businessDateMillis, occupants, requestedBookingId) }
}
