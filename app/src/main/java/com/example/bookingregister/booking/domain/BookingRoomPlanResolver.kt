package com.example.bookingregister.booking.domain

enum class BookingRoomPlanSource {
    LEGACY_BOOKING,
    EXPLICIT_ASSIGNMENTS
}

data class EffectiveBookingRoomPlan(
    val source: BookingRoomPlanSource,
    val assignments: List<BookingRoomNightAssignment>,
    val validation: BookingRoomPlanValidation
) {
    val isValid: Boolean
        get() = validation.isValid
}

object BookingRoomPlanResolver {

    fun resolve(
        checkInMillis: Long,
        checkOutMillis: Long,
        legacyRoomRemoteIds: List<String>,
        legacyPropertyRemoteId: String?,
        explicitAssignments: List<BookingRoomNightAssignment>
    ): EffectiveBookingRoomPlan {
        val hasExplicitPlan = explicitAssignments.isNotEmpty()

        val effectiveAssignments = if (hasExplicitPlan) {
            explicitAssignments
                .map { assignment ->
                    assignment.copy(
                        roomRemoteId = assignment.roomRemoteId.trim(),
                        propertyRemoteId = cleanPropertyId(assignment.propertyRemoteId)
                    )
                }
                .sortedWith(
                    compareBy<BookingRoomNightAssignment> { it.businessDateMillis }
                        .thenBy { it.roomRemoteId }
                )
        } else {
            BookingRoomPlanPolicy.legacyAssignments(
                checkInMillis = checkInMillis,
                checkOutMillis = checkOutMillis,
                roomRemoteIds = legacyRoomRemoteIds,
                propertyRemoteId = legacyPropertyRemoteId
            )
        }

        return EffectiveBookingRoomPlan(
            source = if (hasExplicitPlan) {
                BookingRoomPlanSource.EXPLICIT_ASSIGNMENTS
            } else {
                BookingRoomPlanSource.LEGACY_BOOKING
            },
            assignments = effectiveAssignments,
            validation = BookingRoomPlanPolicy.validate(
                checkInMillis = checkInMillis,
                checkOutMillis = checkOutMillis,
                assignments = effectiveAssignments
            )
        )
    }

    private fun cleanPropertyId(propertyRemoteId: String?): String? =
        propertyRemoteId?.trim()?.takeIf { it.isNotBlank() }
}