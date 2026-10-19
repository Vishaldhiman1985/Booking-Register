package com.example.bookingregister.booking.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingRoomNightAvailabilityPolicyTest {
    private val day = 86_400_000L
    private fun room(night: Int, id: String, property: String = "DIMPY") =
        BookingRoomNightAssignment(day * night, id, property)
    private val lalita = BookingRoomNightAvailabilityPolicy.Occupant(
        "lalita", day * 10, day * 14, listOf("D1"), "DIMPY",
        listOf(room(10, "D1"), room(11, "D2"), room(12, "P1", "PUSHPA"), room(13, "D1"))
    )

    @Test fun movedRoomFreesOriginalNights() {
        val o = listOf(lalita)
        assertTrue(BookingRoomNightAvailabilityPolicy.isOccupied("D1", day * 10, o))
        assertFalse(BookingRoomNightAvailabilityPolicy.isOccupied("D1", day * 11, o))
        assertFalse(BookingRoomNightAvailabilityPolicy.isOccupied("D1", day * 12, o))
        assertTrue(BookingRoomNightAvailabilityPolicy.isOccupied("D1", day * 13, o))
        assertTrue(BookingRoomNightAvailabilityPolicy.isOccupied("D2", day * 11, o))
        assertTrue(BookingRoomNightAvailabilityPolicy.isOccupied("P1", day * 12, o))
    }
    @Test fun normalBookingAndDatewiseBookingUseSameConflictRule() {
        assertFalse(BookingRoomNightAvailabilityPolicy.conflicts("evie", listOf(room(11, "D1")), listOf(lalita)))
        assertTrue(BookingRoomNightAvailabilityPolicy.conflicts("evie", listOf(room(11, "D2")), listOf(lalita)))
    }
    @Test fun legacyBookingStillOccupiesEntireStay() {
        assertTrue(BookingRoomNightAvailabilityPolicy.isOccupied("D1", day * 11, listOf(lalita.copy(explicitAssignments = emptyList()))))
    }
    @Test fun existingBookingDoesNotConflictWithItself() {
        assertFalse(BookingRoomNightAvailabilityPolicy.isOccupied("D2", day * 11, listOf(lalita), "lalita"))
    }
    @Test fun cancelledAndDeletedDoNotBlock() {
        assertFalse(BookingRoomNightAvailabilityPolicy.isOccupied("D1", day * 10, listOf(lalita.copy(cancelled = true), lalita.copy(deleted = true))))
    }
    @Test fun incompletePlanFailsClosed() {
        val bad = lalita.copy(explicitAssignments = listOf(room(11, "D2")))
        assertTrue(BookingRoomNightAvailabilityPolicy.isOccupied("D1", day * 11, listOf(bad)))
        assertTrue(BookingRoomNightAvailabilityPolicy.isOccupied("D2", day * 11, listOf(bad)))
    }
    @Test fun checkoutDayIsFree() {
        assertFalse(BookingRoomNightAvailabilityPolicy.isOccupied("D1", day * 14, listOf(lalita)))
    }

    @Test fun simpleStayRejectsRoomOccupiedOnOnlyOneNightAndKeepsOwnRoomSelectable() {
        val nisha = BookingRoomNightAvailabilityPolicy.Occupant(
            "nisha", day * 17, day * 20, listOf("P1"), "PUSHPA"
        )
        val mutti = BookingRoomNightAvailabilityPolicy.Occupant(
            "mutti", day * 17, day * 20, listOf("D2"), "DIMPY",
            listOf(room(17, "D4"), room(18, "D2"), room(19, "D2"))
        )

        val occupied = BookingRoomNightAvailabilityPolicy.occupiedRoomIdsForStay(
            listOf("P1", "P2", "D4"), day * 17, day * 20, listOf(nisha, mutti), "nisha"
        )

        assertEquals(setOf("D4"), occupied)
        assertFalse("The booking's own P1 must remain checked and selectable", "P1" in occupied)
    }

    @Test fun simpleStayRejectsD4ForLaliEvenThoughItIsFreeOnSecondNight() {
        val lali = BookingRoomNightAvailabilityPolicy.Occupant(
            "lali", day * 17, day * 19, listOf("P3"), "PUSHPA"
        )
        val mutti = BookingRoomNightAvailabilityPolicy.Occupant(
            "mutti", day * 17, day * 19, listOf("D4"), "DIMPY",
            listOf(room(17, "D4"), room(18, "D2"))
        )

        val occupied = BookingRoomNightAvailabilityPolicy.occupiedRoomIdsForStay(
            listOf("P3", "P4", "D4"), day * 17, day * 19, listOf(lali, mutti), "lali"
        )

        assertEquals(setOf("D4"), occupied)
        assertTrue(BookingRoomNightAvailabilityPolicy.isOccupied("D4", day * 17, listOf(mutti)))
        assertFalse(BookingRoomNightAvailabilityPolicy.isOccupied("D4", day * 18, listOf(mutti)))
    }

    @Test fun simpleStayUsesActualNightPlanRatherThanLegacyRooms() {
        val moved = BookingRoomNightAvailabilityPolicy.Occupant(
            "moved", day * 17, day * 19, listOf("D1"), "DIMPY",
            listOf(room(17, "D2"), room(18, "D2"))
        )

        assertEquals(
            setOf("D2"),
            BookingRoomNightAvailabilityPolicy.occupiedRoomIdsForStay(
                listOf("D1", "D2"), day * 17, day * 19, listOf(moved)
            )
        )
    }

    @Test fun simpleStayFailsClosedForIncompletePlansButNotAfterCheckout() {
        val incomplete = lalita.copy(explicitAssignments = listOf(room(11, "D2")))

        assertEquals(
            setOf("D1", "D2"),
            BookingRoomNightAvailabilityPolicy.occupiedRoomIdsForStay(
                listOf("D1", "D2"), day * 11, day * 12, listOf(incomplete)
            )
        )
        assertEquals(
            emptySet<String>(),
            BookingRoomNightAvailabilityPolicy.occupiedRoomIdsForStay(
                listOf("D1", "D2"), day * 14, day * 15, listOf(incomplete)
            )
        )
    }
}
