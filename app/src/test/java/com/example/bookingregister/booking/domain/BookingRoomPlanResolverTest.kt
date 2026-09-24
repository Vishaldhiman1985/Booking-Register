package com.example.bookingregister.booking.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingRoomPlanResolverTest {

    private val day1 = 1_700_000_000_000L
    private val day2 = day1 + DAY_MILLIS
    private val day3 = day2 + DAY_MILLIS
    private val day4 = day3 + DAY_MILLIS

    @Test
    fun `legacy booking remains unchanged when no explicit room plan exists`() {
        val result = BookingRoomPlanResolver.resolve(
            checkInMillis = day1,
            checkOutMillis = day4,
            legacyRoomRemoteIds = listOf("401", "402"),
            legacyPropertyRemoteId = "hotel-a",
            explicitAssignments = emptyList()
        )

        assertEquals(BookingRoomPlanSource.LEGACY_BOOKING, result.source)
        assertTrue(result.isValid)
        assertEquals(6, result.assignments.size)

        assertEquals(
            setOf("401", "402"),
            result.assignments
                .filter { it.businessDateMillis == day1 }
                .map { it.roomRemoteId }
                .toSet()
        )

        assertEquals(
            setOf("401", "402"),
            result.assignments
                .filter { it.businessDateMillis == day3 }
                .map { it.roomRemoteId }
                .toSet()
        )
    }

    @Test
    fun `explicit room plan replaces legacy whole stay room assumption`() {
        val explicit = listOf(
            BookingRoomNightAssignment(day1, "401", "hotel-a"),
            BookingRoomNightAssignment(day2, "401", "hotel-a"),
            BookingRoomNightAssignment(day3, "301", "hotel-a")
        )

        val result = BookingRoomPlanResolver.resolve(
            checkInMillis = day1,
            checkOutMillis = day4,
            legacyRoomRemoteIds = listOf("401"),
            legacyPropertyRemoteId = "hotel-a",
            explicitAssignments = explicit
        )

        assertEquals(
            BookingRoomPlanSource.EXPLICIT_ASSIGNMENTS,
            result.source
        )
        assertTrue(result.isValid)

        assertEquals(
            listOf("401"),
            result.assignments
                .filter { it.businessDateMillis == day1 }
                .map { it.roomRemoteId }
        )

        assertEquals(
            listOf("301"),
            result.assignments
                .filter { it.businessDateMillis == day3 }
                .map { it.roomRemoteId }
        )
    }

    @Test
    fun `incomplete explicit plan is invalid and never falls back to legacy rooms`() {
        val explicit = listOf(
            BookingRoomNightAssignment(day1, "301", "hotel-a"),
            BookingRoomNightAssignment(day3, "301", "hotel-a")
        )

        val result = BookingRoomPlanResolver.resolve(
            checkInMillis = day1,
            checkOutMillis = day4,
            legacyRoomRemoteIds = listOf("401"),
            legacyPropertyRemoteId = "hotel-a",
            explicitAssignments = explicit
        )

        assertEquals(
            BookingRoomPlanSource.EXPLICIT_ASSIGNMENTS,
            result.source
        )
        assertFalse(result.isValid)

        assertTrue(
            result.validation.errors.contains(
                "Every night must have at least one room."
            )
        )

        assertTrue(
            result.assignments.none { it.roomRemoteId == "401" }
        )
    }

    @Test
    fun `explicit assignments are normalized and sorted`() {
        val explicit = listOf(
            BookingRoomNightAssignment(day2, " 302 ", " hotel-b "),
            BookingRoomNightAssignment(day1, " 301 ", " hotel-b ")
        )

        val result = BookingRoomPlanResolver.resolve(
            checkInMillis = day1,
            checkOutMillis = day3,
            legacyRoomRemoteIds = listOf("401"),
            legacyPropertyRemoteId = "hotel-a",
            explicitAssignments = explicit
        )

        assertTrue(result.isValid)
        assertEquals(day1, result.assignments[0].businessDateMillis)
        assertEquals("301", result.assignments[0].roomRemoteId)
        assertEquals("hotel-b", result.assignments[0].propertyRemoteId)

        assertEquals(day2, result.assignments[1].businessDateMillis)
        assertEquals("302", result.assignments[1].roomRemoteId)
        assertEquals("hotel-b", result.assignments[1].propertyRemoteId)
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}