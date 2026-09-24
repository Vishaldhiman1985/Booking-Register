package com.example.bookingregister.booking.domain

data class BookingRoomNightAssignment(
    val businessDateMillis: Long,
    val roomRemoteId: String,
    val propertyRemoteId: String?
)

data class BookingRoomPlanSegment(
    val startDateMillis: Long,
    val endDateMillis: Long,
    val propertyRemoteId: String?,
    val roomRemoteIds: List<String>
)

data class BookingRoomPlanValidation(
    val isValid: Boolean,
    val errors: List<String>
)

object BookingRoomPlanPolicy {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    private const val MAIN_PROPERTY_KEY = "__MAIN_PROPERTY__"

    fun legacyAssignments(
        checkInMillis: Long,
        checkOutMillis: Long,
        roomRemoteIds: List<String>,
        propertyRemoteId: String?
    ): List<BookingRoomNightAssignment> {
        if (checkOutMillis <= checkInMillis) return emptyList()

        val rooms = roomRemoteIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (rooms.isEmpty()) return emptyList()

        return stayDates(checkInMillis, checkOutMillis).flatMap { dateMillis ->
            rooms.map { roomRemoteId ->
                BookingRoomNightAssignment(
                    businessDateMillis = dateMillis,
                    roomRemoteId = roomRemoteId,
                    propertyRemoteId = cleanPropertyId(propertyRemoteId)
                )
            }
        }
    }

    fun replaceFromDate(
        checkInMillis: Long,
        checkOutMillis: Long,
        existingAssignments: List<BookingRoomNightAssignment>,
        effectiveDateMillis: Long,
        replacementRoomRemoteIds: List<String>,
        replacementPropertyRemoteId: String?
    ): List<BookingRoomNightAssignment> {
        require(checkOutMillis > checkInMillis) {
            "Check-out must be after check-in."
        }
        require(effectiveDateMillis >= checkInMillis && effectiveDateMillis < checkOutMillis) {
            "Room change date must be inside the booking stay."
        }

        val replacementRooms = replacementRoomRemoteIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        require(replacementRooms.isNotEmpty()) {
            "Select at least one replacement room."
        }

        val preserved = existingAssignments
            .filter { it.businessDateMillis < effectiveDateMillis }
            .map {
                it.copy(
                    roomRemoteId = it.roomRemoteId.trim(),
                    propertyRemoteId = cleanPropertyId(it.propertyRemoteId)
                )
            }

        val replacement = stayDates(effectiveDateMillis, checkOutMillis)
            .flatMap { dateMillis ->
                replacementRooms.map { roomRemoteId ->
                    BookingRoomNightAssignment(
                        businessDateMillis = dateMillis,
                        roomRemoteId = roomRemoteId,
                        propertyRemoteId = cleanPropertyId(replacementPropertyRemoteId)
                    )
                }
            }

        return (preserved + replacement)
            .distinctBy { it.businessDateMillis to it.roomRemoteId }
            .sortedWith(
                compareBy<BookingRoomNightAssignment> { it.businessDateMillis }
                    .thenBy { it.roomRemoteId }
            )
    }

    fun validate(
        checkInMillis: Long,
        checkOutMillis: Long,
        assignments: List<BookingRoomNightAssignment>
    ): BookingRoomPlanValidation {
        val errors = mutableListOf<String>()

        if (checkOutMillis <= checkInMillis) {
            errors += "Check-out must be after check-in."
            return BookingRoomPlanValidation(false, errors)
        }

        val expectedDates = stayDates(checkInMillis, checkOutMillis)
        val expectedDateSet = expectedDates.toSet()

        if (assignments.isEmpty()) {
            errors += "Every night must have at least one room."
            return BookingRoomPlanValidation(false, errors)
        }

        assignments.forEach { assignment ->
            if (assignment.roomRemoteId.isBlank()) {
                errors += "Room assignment contains an empty room."
            }
            if (assignment.businessDateMillis !in expectedDateSet) {
                errors += "Room assignment contains a date outside the booking stay."
            }
        }

        val duplicateRoomNights = assignments
            .groupBy { it.businessDateMillis to it.roomRemoteId.trim() }
            .filterValues { it.size > 1 }

        if (duplicateRoomNights.isNotEmpty()) {
            errors += "The same room cannot be assigned twice for the same night."
        }

        val assignmentsByDate = assignments
            .filter { it.businessDateMillis in expectedDateSet }
            .groupBy { it.businessDateMillis }

        expectedDates.forEach { dateMillis ->
            val nightlyAssignments = assignmentsByDate[dateMillis].orEmpty()
            if (nightlyAssignments.isEmpty()) {
                errors += "Every night must have at least one room."
            } else {
                val propertyKeys = nightlyAssignments
                    .map { propertyKey(it.propertyRemoteId) }
                    .toSet()

                if (propertyKeys.size > 1) {
                    errors += "Rooms used on the same night must belong to the same property."
                }
            }
        }

        return BookingRoomPlanValidation(
            isValid = errors.isEmpty(),
            errors = errors.distinct()
        )
    }

    fun toSegments(
        assignments: List<BookingRoomNightAssignment>
    ): List<BookingRoomPlanSegment> {
        if (assignments.isEmpty()) return emptyList()

        data class Night(
            val dateMillis: Long,
            val propertyRemoteId: String?,
            val roomRemoteIds: List<String>
        )

        val nights = assignments
            .groupBy { it.businessDateMillis }
            .toSortedMap()
            .map { (dateMillis, nightlyAssignments) ->
                Night(
                    dateMillis = dateMillis,
                    propertyRemoteId = nightlyAssignments
                        .map { cleanPropertyId(it.propertyRemoteId) }
                        .distinct()
                        .singleOrNull(),
                    roomRemoteIds = nightlyAssignments
                        .map { it.roomRemoteId.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()
                )
            }

        val result = mutableListOf<BookingRoomPlanSegment>()

        nights.forEach { night ->
            val previous = result.lastOrNull()
            val canExtend = previous != null &&
                previous.endDateMillis == night.dateMillis &&
                propertyKey(previous.propertyRemoteId) == propertyKey(night.propertyRemoteId) &&
                previous.roomRemoteIds == night.roomRemoteIds

            if (canExtend) {
                result[result.lastIndex] = previous!!.copy(
                    endDateMillis = night.dateMillis + DAY_MILLIS
                )
            } else {
                result += BookingRoomPlanSegment(
                    startDateMillis = night.dateMillis,
                    endDateMillis = night.dateMillis + DAY_MILLIS,
                    propertyRemoteId = night.propertyRemoteId,
                    roomRemoteIds = night.roomRemoteIds
                )
            }
        }

        return result
    }

    private fun stayDates(
        checkInMillis: Long,
        checkOutMillis: Long
    ): List<Long> {
        val dates = mutableListOf<Long>()
        var dateMillis = checkInMillis
        while (dateMillis < checkOutMillis) {
            dates += dateMillis
            dateMillis += DAY_MILLIS
        }
        return dates
    }

    private fun cleanPropertyId(propertyRemoteId: String?): String? =
        propertyRemoteId?.trim()?.takeIf { it.isNotBlank() }

    private fun propertyKey(propertyRemoteId: String?): String =
        cleanPropertyId(propertyRemoteId) ?: MAIN_PROPERTY_KEY
}