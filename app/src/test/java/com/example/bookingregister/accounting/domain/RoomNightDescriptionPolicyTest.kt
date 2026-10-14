package com.example.bookingregister.accounting.domain

import com.example.bookingregister.booking.domain.BookingRoomNightAssignment
import com.example.bookingregister.booking.domain.BookingRoomPlanResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomNightDescriptionPolicyTest {
    private val nightOne = 1_760_659_200_000L
    private val nightTwo = nightOne + 86_400_000L
    private val names = mapOf("p2" to "P2", "p3" to "P3")

    private fun plan(assignments: List<BookingRoomNightAssignment>) =
        BookingRoomPlanResolver.resolve(
            checkInMillis = nightOne,
            checkOutMillis = nightTwo + 86_400_000L,
            legacyRoomRemoteIds = listOf("p3"),
            legacyPropertyRemoteId = "property_1",
            explicitAssignments = assignments
        )

    private fun assignment(date: Long, room: String, property: String = "property_1") =
        BookingRoomNightAssignment(
            businessDateMillis = date,
            roomRemoteId = room,
            propertyRemoteId = property
        )

    @Test
    fun roomShiftUsesOccupiedRoomForEachNight() {
        val result = plan(listOf(assignment(nightOne, "p2"), assignment(nightTwo, "p3")))
        assertEquals("P2", RoomNightDescriptionPolicy.occupiedRoomNameForCharge(
            result, nightOne, "property_1", 1, names
        ))
        assertEquals("P3", RoomNightDescriptionPolicy.occupiedRoomNameForCharge(
            result, nightTwo, "property_1", 1, names
        ))
    }

    @Test
    fun legacyBookingPreservesBilledRoom() {
        assertNull(RoomNightDescriptionPolicy.occupiedRoomNameForCharge(
            plan(emptyList()), nightOne, "property_1", 1, names
        ))
    }

    @Test
    fun ambiguousMultiRoomNightDoesNotGuess() {
        val result = plan(listOf(
            assignment(nightOne, "p2"),
            assignment(nightOne, "p3"),
            assignment(nightTwo, "p3")
        ))
        assertNull(RoomNightDescriptionPolicy.occupiedRoomNameForCharge(
            result, nightOne, "property_1", 2, names
        ))
    }

    @Test
    fun crossPropertyMovePreservesFinancialAttribution() {
        val result = plan(listOf(
            assignment(nightOne, "p2", "property_2"),
            assignment(nightTwo, "p3")
        ))
        assertNull(RoomNightDescriptionPolicy.occupiedRoomNameForCharge(
            result, nightOne, "property_1", 1, names
        ))
    }

    @Test
    fun invalidPlanDoesNotSupplyRoomDescription() {
        val result = plan(listOf(assignment(nightOne, "p2")))
        assertNull(RoomNightDescriptionPolicy.occupiedRoomNameForCharge(
            result, nightOne, "property_1", 1, names
        ))
    }
}
