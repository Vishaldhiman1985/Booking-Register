package com.example.bookingregister.booking.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BookingRoomPlanChartPolicyTest {

    private val day1 = 1_700_000_000_000L
    private val day2 = day1 + DAY_MILLIS
    private val day3 = day2 + DAY_MILLIS
    private val day4 = day3 + DAY_MILLIS

    @Test
    fun `same room on consecutive nights becomes one chart span`() {
        val assignments = listOf(
            BookingRoomNightAssignment(day1, "401", "property-a"),
            BookingRoomNightAssignment(day2, "401", "property-a"),
            BookingRoomNightAssignment(day3, "401", "property-a")
        )

        val spans = BookingRoomPlanChartPolicy.toRoomSpans(assignments)

        assertEquals(1, spans.size)
        assertEquals("401", spans[0].roomRemoteId)
        assertEquals("property-a", spans[0].propertyRemoteId)
        assertEquals(day1, spans[0].startDateMillis)
        assertEquals(day4, spans[0].endDateMillis)
    }

    @Test
    fun `mid stay room change creates separate chart spans`() {
        val assignments = listOf(
            BookingRoomNightAssignment(day1, "401", "property-a"),
            BookingRoomNightAssignment(day2, "401", "property-a"),
            BookingRoomNightAssignment(day3, "301", "property-a")
        )

        val spans = BookingRoomPlanChartPolicy.toRoomSpans(assignments)

        assertEquals(2, spans.size)

        assertEquals("401", spans[0].roomRemoteId)
        assertEquals(day1, spans[0].startDateMillis)
        assertEquals(day3, spans[0].endDateMillis)

        assertEquals("301", spans[1].roomRemoteId)
        assertEquals(day3, spans[1].startDateMillis)
        assertEquals(day4, spans[1].endDateMillis)
    }

    @Test
    fun `same room id in different properties is not merged`() {
        val assignments = listOf(
            BookingRoomNightAssignment(day1, "201", "property-a"),
            BookingRoomNightAssignment(day2, "201", "property-b")
        )

        val spans = BookingRoomPlanChartPolicy.toRoomSpans(assignments)

        assertEquals(2, spans.size)

        assertEquals("property-a", spans[0].propertyRemoteId)
        assertEquals(day1, spans[0].startDateMillis)
        assertEquals(day2, spans[0].endDateMillis)

        assertEquals("property-b", spans[1].propertyRemoteId)
        assertEquals(day2, spans[1].startDateMillis)
        assertEquals(day3, spans[1].endDateMillis)
    }

    @Test
    fun `duplicate room night does not create duplicate chart span`() {
        val assignments = listOf(
            BookingRoomNightAssignment(day1, "401", "property-a"),
            BookingRoomNightAssignment(day1, "401", "property-a"),
            BookingRoomNightAssignment(day2, "401", "property-a")
        )

        val spans = BookingRoomPlanChartPolicy.toRoomSpans(assignments)

        assertEquals(1, spans.size)
        assertEquals(day1, spans[0].startDateMillis)
        assertEquals(day3, spans[0].endDateMillis)
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}