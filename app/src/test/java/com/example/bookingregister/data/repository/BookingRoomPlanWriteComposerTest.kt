package com.example.bookingregister.data.repository

import com.example.bookingregister.booking.domain.BookingRoomNightAssignment
import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingRoomNightAssignmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingRoomPlanWriteComposerTest {

    private val day1 = 1_700_000_000_000L
    private val day2 = day1 + DAY_MILLIS
    private val day3 = day2 + DAY_MILLIS

    @Test
    fun `target plan becomes deterministic command assignments and pending local rows`() {
        val booking = booking()
        val target = listOf(
            BookingRoomNightAssignment(day1, "401", "property-a"),
            BookingRoomNightAssignment(day2, "301", "property-a")
        )

        val result = BookingRoomPlanWriteComposer.prepare(
            booking = booking,
            targetAssignments = target,
            existingRows = emptyList(),
            now = 9_000L
        )

        assertEquals(2, result.commandAssignments.size)
        assertEquals(2, result.localRows.size)

        assertEquals(
            "room_plan_booking-1_${day1}_401",
            result.localRows[0].remoteId
        )
        assertEquals(SyncState.PENDING, result.localRows[0].syncState)
        assertFalse(result.localRows[0].isDeleted)
        assertEquals(9_000L, result.localRows[0].updatedAt)
    }

    @Test
    fun `existing deterministic row preserves local identity and revision baseline`() {
        val booking = booking()
        val existing = assignmentEntity(
            localId = 77,
            remoteId = "room_plan_booking-1_${day1}_401",
            businessDateMillis = day1,
            roomRemoteId = "401",
            revision = 5,
            baseRevision = 5
        )

        val result = BookingRoomPlanWriteComposer.prepare(
            booking = booking,
            targetAssignments = listOf(
                BookingRoomNightAssignment(day1, "401", "property-a"),
                BookingRoomNightAssignment(day2, "401", "property-a")
            ),
            existingRows = listOf(existing),
            now = 10_000L
        )

        val preserved = result.localRows.first { it.remoteId == existing.remoteId }

        assertEquals(77, preserved.localId)
        assertEquals(5, preserved.revision)
        assertEquals(5, preserved.baseRevision)
        assertEquals(SyncState.PENDING, preserved.syncState)
        assertFalse(preserved.isDeleted)
    }

    @Test
    fun `room removed from target plan becomes local tombstone`() {
        val booking = booking()
        val oldRow = assignmentEntity(
            localId = 88,
            remoteId = "room_plan_booking-1_${day2}_401",
            businessDateMillis = day2,
            roomRemoteId = "401",
            revision = 3,
            baseRevision = 3
        )

        val result = BookingRoomPlanWriteComposer.prepare(
            booking = booking,
            targetAssignments = listOf(
                BookingRoomNightAssignment(day1, "401", "property-a"),
                BookingRoomNightAssignment(day2, "301", "property-a")
            ),
            existingRows = listOf(oldRow),
            now = 11_000L
        )

        val tombstone = result.localRows.first { it.remoteId == oldRow.remoteId }

        assertEquals(88, tombstone.localId)
        assertTrue(tombstone.isDeleted)
        assertEquals(SyncState.PENDING, tombstone.syncState)
        assertEquals(3, tombstone.revision)
        assertEquals(3, tombstone.baseRevision)

        assertTrue(
            result.commandAssignments.none {
                it.businessDateMillis == day2 && it.roomRemoteId == "401"
            }
        )
    }

    @Test
    fun `invalid incomplete target plan is rejected before any write preparation`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BookingRoomPlanWriteComposer.prepare(
                booking = booking(),
                targetAssignments = listOf(
                    BookingRoomNightAssignment(day1, "401", "property-a")
                ),
                existingRows = emptyList(),
                now = 12_000L
            )
        }

        assertTrue(
            error.message.orEmpty().contains(
                "Every night must have at least one room."
            )
        )
    }

    private fun booking() = BookingEntity(
        remoteId = "booking-1",
        bookingUuid = "uuid-booking-1",
        hotelRemoteId = "hotel-1",
        propertyRemoteId = "property-a",
        guestName = "Guest",
        checkInMillis = day1,
        checkOutMillis = day3,
        roomRemoteIds = listOf("401")
    )

    private fun assignmentEntity(
        localId: Long,
        remoteId: String,
        businessDateMillis: Long,
        roomRemoteId: String,
        revision: Long,
        baseRevision: Long
    ) = BookingRoomNightAssignmentEntity(
        localId = localId,
        remoteId = remoteId,
        hotelRemoteId = "hotel-1",
        bookingRemoteId = "booking-1",
        roomRemoteId = roomRemoteId,
        propertyRemoteId = "property-a",
        businessDateMillis = businessDateMillis,
        revision = revision,
        baseRevision = baseRevision
    )

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}