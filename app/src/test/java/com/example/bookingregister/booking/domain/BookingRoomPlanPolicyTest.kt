package com.example.bookingregister.booking.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingRoomPlanPolicyTest {

    private val day1 = 1_700_000_000_000L
    private val day2 = day1 + DAY_MILLIS
    private val day3 = day2 + DAY_MILLIS
    private val day4 = day3 + DAY_MILLIS

    @Test
    fun `ordinary booking keeps the same rooms for every night`() {
        val assignments = BookingRoomPlanPolicy.legacyAssignments(
            checkInMillis = day1,
            checkOutMillis = day4,
            roomRemoteIds = listOf("401", "402"),
            propertyRemoteId = "hotel-a"
        )

        assertEquals(6, assignments.size)

        assertEquals(
            setOf("401", "402"),
            assignments.filter { it.businessDateMillis == day1 }
                .map { it.roomRemoteId }
                .toSet()
        )
        assertEquals(
            setOf("401", "402"),
            assignments.filter { it.businessDateMillis == day2 }
                .map { it.roomRemoteId }
                .toSet()
        )
        assertEquals(
            setOf("401", "402"),
            assignments.filter { it.businessDateMillis == day3 }
                .map { it.roomRemoteId }
                .toSet()
        )
    }

    @Test
    fun `room change preserves past nights and replaces only future nights`() {
        val original = BookingRoomPlanPolicy.legacyAssignments(
            checkInMillis = day1,
            checkOutMillis = day4,
            roomRemoteIds = listOf("401", "402"),
            propertyRemoteId = "hotel-a"
        )

        val changed = BookingRoomPlanPolicy.replaceFromDate(
            checkInMillis = day1,
            checkOutMillis = day4,
            existingAssignments = original,
            effectiveDateMillis = day3,
            replacementRoomRemoteIds = listOf("301", "302"),
            replacementPropertyRemoteId = "hotel-a"
        )

        assertEquals(
            setOf("401", "402"),
            changed.filter { it.businessDateMillis == day1 }
                .map { it.roomRemoteId }
                .toSet()
        )
        assertEquals(
            setOf("401", "402"),
            changed.filter { it.businessDateMillis == day2 }
                .map { it.roomRemoteId }
                .toSet()
        )
        assertEquals(
            setOf("301", "302"),
            changed.filter { it.businessDateMillis == day3 }
                .map { it.roomRemoteId }
                .toSet()
        )
    }

    @Test
    fun `future room change may move the guest to another property`() {
        val original = BookingRoomPlanPolicy.legacyAssignments(
            checkInMillis = day1,
            checkOutMillis = day4,
            roomRemoteIds = listOf("401"),
            propertyRemoteId = "hotel-a"
        )

        val changed = BookingRoomPlanPolicy.replaceFromDate(
            checkInMillis = day1,
            checkOutMillis = day4,
            existingAssignments = original,
            effectiveDateMillis = day3,
            replacementRoomRemoteIds = listOf("201"),
            replacementPropertyRemoteId = "hotel-b"
        )

        assertEquals(
            "hotel-a",
            changed.first { it.businessDateMillis == day2 }.propertyRemoteId
        )
        assertEquals(
            "hotel-b",
            changed.first { it.businessDateMillis == day3 }.propertyRemoteId
        )
    }

    @Test
    fun `every night must have at least one room`() {
        val assignments = listOf(
            BookingRoomNightAssignment(
                businessDateMillis = day1,
                roomRemoteId = "401",
                propertyRemoteId = "hotel-a"
            ),
            BookingRoomNightAssignment(
                businessDateMillis = day3,
                roomRemoteId = "301",
                propertyRemoteId = "hotel-a"
            )
        )

        val result = BookingRoomPlanPolicy.validate(
            checkInMillis = day1,
            checkOutMillis = day4,
            assignments = assignments
        )

        assertFalse(result.isValid)
        assertTrue(
            result.errors.any {
                it == "Every night must have at least one room."
            }
        )
    }

    @Test
    fun `rooms used on one night cannot belong to different properties`() {
        val assignments = listOf(
            BookingRoomNightAssignment(day1, "401", "hotel-a"),
            BookingRoomNightAssignment(day1, "201", "hotel-b")
        )

        val result = BookingRoomPlanPolicy.validate(
            checkInMillis = day1,
            checkOutMillis = day2,
            assignments = assignments
        )

        assertFalse(result.isValid)
        assertTrue(
            result.errors.contains(
                "Rooms used on the same night must belong to the same property."
            )
        )
    }

    @Test
    fun `valid planned stay passes validation`() {
        val original = BookingRoomPlanPolicy.legacyAssignments(
            checkInMillis = day1,
            checkOutMillis = day4,
            roomRemoteIds = listOf("401", "402"),
            propertyRemoteId = "hotel-a"
        )

        val changed = BookingRoomPlanPolicy.replaceFromDate(
            checkInMillis = day1,
            checkOutMillis = day4,
            existingAssignments = original,
            effectiveDateMillis = day3,
            replacementRoomRemoteIds = listOf("501", "502"),
            replacementPropertyRemoteId = "hotel-a"
        )

        val result = BookingRoomPlanPolicy.validate(
            checkInMillis = day1,
            checkOutMillis = day4,
            assignments = changed
        )

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `room assignments are grouped into simple stay segments`() {
        val original = BookingRoomPlanPolicy.legacyAssignments(
            checkInMillis = day1,
            checkOutMillis = day4,
            roomRemoteIds = listOf("401", "402"),
            propertyRemoteId = "hotel-a"
        )

        val changed = BookingRoomPlanPolicy.replaceFromDate(
            checkInMillis = day1,
            checkOutMillis = day4,
            existingAssignments = original,
            effectiveDateMillis = day3,
            replacementRoomRemoteIds = listOf("501", "502"),
            replacementPropertyRemoteId = "hotel-b"
        )

        val segments = BookingRoomPlanPolicy.toSegments(changed)

        assertEquals(2, segments.size)

        assertEquals(day1, segments[0].startDateMillis)
        assertEquals(day3, segments[0].endDateMillis)
        assertEquals("hotel-a", segments[0].propertyRemoteId)
        assertEquals(listOf("401", "402"), segments[0].roomRemoteIds)

        assertEquals(day3, segments[1].startDateMillis)
        assertEquals(day4, segments[1].endDateMillis)
        assertEquals("hotel-b", segments[1].propertyRemoteId)
        assertEquals(listOf("501", "502"), segments[1].roomRemoteIds)
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}