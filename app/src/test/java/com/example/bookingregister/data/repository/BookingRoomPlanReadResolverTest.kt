package com.example.bookingregister.data.repository

import com.example.bookingregister.booking.domain.BookingRoomPlanSource
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingRoomNightAssignmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingRoomPlanReadResolverTest {

    private val day1 = 1_700_000_000_000L
    private val day2 = day1 + DAY_MILLIS
    private val day3 = day2 + DAY_MILLIS

    @Test
    fun `legacy booking is used when there are no assignment rows`() {
        val booking = booking(
            roomRemoteIds = listOf("401"),
            propertyRemoteId = "property-a"
        )

        val result = BookingRoomPlanReadResolver.resolve(
            booking = booking,
            assignmentEntities = emptyList()
        )

        assertEquals(BookingRoomPlanSource.LEGACY_BOOKING, result.source)
        assertTrue(result.isValid)
        assertEquals(
            listOf("401", "401"),
            result.assignments.map { it.roomRemoteId }
        )
    }

    @Test
    fun `matching active assignment rows override legacy rooms`() {
        val booking = booking(
            roomRemoteIds = listOf("401"),
            propertyRemoteId = "property-a"
        )

        val assignments = listOf(
            assignment(
                remoteId = "assignment-1",
                businessDateMillis = day1,
                roomRemoteId = "301",
                propertyRemoteId = "property-a"
            ),
            assignment(
                remoteId = "assignment-2",
                businessDateMillis = day2,
                roomRemoteId = "302",
                propertyRemoteId = "property-a"
            )
        )

        val result = BookingRoomPlanReadResolver.resolve(
            booking = booking,
            assignmentEntities = assignments
        )

        assertEquals(
            BookingRoomPlanSource.EXPLICIT_ASSIGNMENTS,
            result.source
        )
        assertTrue(result.isValid)
        assertEquals(
            listOf("301", "302"),
            result.assignments.map { it.roomRemoteId }
        )
    }

    @Test
    fun `deleted and unrelated assignment rows are ignored`() {
        val booking = booking(
            roomRemoteIds = listOf("401"),
            propertyRemoteId = "property-a"
        )

        val assignments = listOf(
            assignment(
                remoteId = "deleted",
                businessDateMillis = day1,
                roomRemoteId = "999",
                propertyRemoteId = "property-a",
                isDeleted = true
            ),
            assignment(
                remoteId = "other-booking",
                businessDateMillis = day1,
                roomRemoteId = "888",
                propertyRemoteId = "property-a",
                bookingRemoteId = "different-booking"
            ),
            assignment(
                remoteId = "other-hotel",
                businessDateMillis = day1,
                roomRemoteId = "777",
                propertyRemoteId = "property-a",
                hotelRemoteId = "different-hotel"
            )
        )

        val result = BookingRoomPlanReadResolver.resolve(
            booking = booking,
            assignmentEntities = assignments
        )

        assertEquals(BookingRoomPlanSource.LEGACY_BOOKING, result.source)
        assertTrue(result.isValid)
        assertEquals(
            listOf("401", "401"),
            result.assignments.map { it.roomRemoteId }
        )
    }

    private fun booking(
        roomRemoteIds: List<String>,
        propertyRemoteId: String?
    ) = BookingEntity(
        remoteId = BOOKING_ID,
        bookingUuid = "uuid-$BOOKING_ID",
        hotelRemoteId = HOTEL_ID,
        propertyRemoteId = propertyRemoteId,
        guestName = "Test Guest",
        checkInMillis = day1,
        checkOutMillis = day3,
        roomRemoteIds = roomRemoteIds
    )

    private fun assignment(
        remoteId: String,
        businessDateMillis: Long,
        roomRemoteId: String,
        propertyRemoteId: String?,
        bookingRemoteId: String = BOOKING_ID,
        hotelRemoteId: String = HOTEL_ID,
        isDeleted: Boolean = false
    ) = BookingRoomNightAssignmentEntity(
        remoteId = remoteId,
        hotelRemoteId = hotelRemoteId,
        bookingRemoteId = bookingRemoteId,
        roomRemoteId = roomRemoteId,
        propertyRemoteId = propertyRemoteId,
        businessDateMillis = businessDateMillis,
        isDeleted = isDeleted
    )

    companion object {
        private const val HOTEL_ID = "hotel-1"
        private const val BOOKING_ID = "booking-1"
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}