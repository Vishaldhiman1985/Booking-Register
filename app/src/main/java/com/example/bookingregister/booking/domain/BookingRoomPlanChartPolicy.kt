package com.example.bookingregister.booking.domain

data class BookingRoomChartSpan(
    val roomRemoteId: String,
    val propertyRemoteId: String?,
    val startDateMillis: Long,
    val endDateMillis: Long
)

object BookingRoomPlanChartPolicy {

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun toRoomSpans(
        assignments: List<BookingRoomNightAssignment>
    ): List<BookingRoomChartSpan> {
        if (assignments.isEmpty()) return emptyList()

        val normalized = assignments
            .map {
                it.copy(
                    roomRemoteId = it.roomRemoteId.trim(),
                    propertyRemoteId = cleanPropertyId(it.propertyRemoteId)
                )
            }
            .filter { it.roomRemoteId.isNotBlank() }
            .distinctBy { it.businessDateMillis to it.roomRemoteId }
            .sortedWith(
                compareBy<BookingRoomNightAssignment> { it.roomRemoteId }
                    .thenBy { it.businessDateMillis }
            )

        val result = mutableListOf<BookingRoomChartSpan>()

        normalized
            .groupBy { it.roomRemoteId }
            .toSortedMap()
            .forEach { (roomRemoteId, roomAssignments) ->
                roomAssignments.forEach { assignment ->
                    val previous = result.lastOrNull()

                    val canExtend = previous != null &&
                        previous.roomRemoteId == roomRemoteId &&
                        previous.endDateMillis == assignment.businessDateMillis &&
                        propertyKey(previous.propertyRemoteId) ==
                            propertyKey(assignment.propertyRemoteId)

                    if (canExtend) {
                        result[result.lastIndex] = previous!!.copy(
                            endDateMillis = assignment.businessDateMillis + DAY_MILLIS
                        )
                    } else {
                        result += BookingRoomChartSpan(
                            roomRemoteId = roomRemoteId,
                            propertyRemoteId = cleanPropertyId(assignment.propertyRemoteId),
                            startDateMillis = assignment.businessDateMillis,
                            endDateMillis = assignment.businessDateMillis + DAY_MILLIS
                        )
                    }
                }
            }

        return result.sortedWith(
            compareBy<BookingRoomChartSpan> { it.startDateMillis }
                .thenBy { it.roomRemoteId }
        )
    }

    private fun cleanPropertyId(propertyRemoteId: String?): String? =
        propertyRemoteId?.trim()?.takeIf { it.isNotBlank() }

    private fun propertyKey(propertyRemoteId: String?): String =
        cleanPropertyId(propertyRemoteId).orEmpty()
}