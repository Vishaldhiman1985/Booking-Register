package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.room.domain.RoomLifecycleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomConflictResolutionPolicyTest {
    private val start = 1_700_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun `only active same-property rooms not occupied by another booking are offered`() {
        val rooms = listOf(
            room("H101", "property-a"),
            room("H102", "property-a"),
            room("H103", "property-a", lifecycle = RoomLifecycleStatus.DISABLED),
            room("M201", "property-b")
        )
        val bookings = listOf(
            booking("other", listOf("H101")),
            booking("booking-being-fixed", listOf("H102"))
        )

        val result = RoomConflictResolutionPolicy.availableRooms(
            hotelRemoteId = "hotel-a",
            propertyRemoteId = "property-a",
            bookingRemoteId = "booking-being-fixed",
            checkInMillis = start,
            checkOutMillis = start + day,
            serverRooms = rooms,
            serverBookings = bookings
        )

        assertEquals(listOf("H102"), result.map { it.remoteId })
    }

    @Test
    fun `overlap outside selected dates does not hide the room`() {
        val room = room("H101", "property-a")
        val oldBooking = booking(
            remoteId = "old",
            rooms = listOf("H101"),
            checkIn = start - (3 * day),
            checkOut = start - day
        )

        val result = RoomConflictResolutionPolicy.availableRooms(
            hotelRemoteId = "hotel-a",
            propertyRemoteId = "property-a",
            bookingRemoteId = "booking-being-fixed",
            checkInMillis = start,
            checkOutMillis = start + day,
            serverRooms = listOf(room),
            serverBookings = listOf(oldBooking)
        )

        assertEquals(listOf("H101"), result.map { it.remoteId })
    }

    @Test
    fun `room count must stay unchanged during conflict repair`() {
        val current = booking("booking-a", listOf("H101", "H102"))
        assertTrue(RoomConflictResolutionPolicy.preservesRoomCount(current, listOf("H103", "H104")))
        assertFalse(RoomConflictResolutionPolicy.preservesRoomCount(current, listOf("H103")))
        assertFalse(RoomConflictResolutionPolicy.preservesRoomCount(current, listOf("H103", "H103")))
    }

    @Test
    fun `equal room-night pricing is safe for room-only rebuilding`() {
        val booking = booking("booking-a", listOf("H101")).copy(
            grossCharges = 3000.0,
            pricingStatus = BookingPricingStatus.CONFIRMED
        )
        val lines = listOf(
            line("line-1", "H101", start, 1500.0),
            line("line-2", "H101", start + day, 1500.0)
        )
        val twoNightBooking = booking.copy(checkOutMillis = start + (2 * day))

        assertTrue(
            RoomConflictResolutionPolicy.financialLinesCanBeRebuiltWithoutChangingMoney(
                twoNightBooking,
                lines
            )
        )
    }

    @Test
    fun `special unequal room-night pricing is refused rather than silently changed`() {
        val booking = booking("booking-a", listOf("H101")).copy(
            checkOutMillis = start + (2 * day),
            grossCharges = 3000.0,
            pricingStatus = BookingPricingStatus.CONFIRMED
        )
        val lines = listOf(
            line("line-1", "H101", start, 1000.0),
            line("line-2", "H101", start + day, 2000.0)
        )

        assertFalse(
            RoomConflictResolutionPolicy.financialLinesCanBeRebuiltWithoutChangingMoney(
                booking,
                lines
            )
        )
    }

    private fun room(
        remoteId: String,
        propertyRemoteId: String?,
        lifecycle: String = RoomLifecycleStatus.ACTIVE
    ) = RoomEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel-a",
        roomName = remoteId,
        propertyRemoteId = propertyRemoteId,
        lifecycleStatus = lifecycle
    )

    private fun booking(
        remoteId: String,
        rooms: List<String>,
        checkIn: Long = start,
        checkOut: Long = start + day
    ) = BookingEntity(
        remoteId = remoteId,
        bookingUuid = remoteId,
        hotelRemoteId = "hotel-a",
        propertyRemoteId = "property-a",
        guestName = "Guest",
        checkInMillis = checkIn,
        checkOutMillis = checkOut,
        roomRemoteIds = rooms
    )

    private fun line(
        remoteId: String,
        roomRemoteId: String,
        businessDateMillis: Long,
        grossAmount: Double
    ) = BookingFinancialLineEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel-a",
        bookingRemoteId = "booking-a",
        roomRemoteId = roomRemoteId,
        propertyRemoteId = "property-a",
        businessDateMillis = businessDateMillis,
        grossAmount = grossAmount,
        taxableAmount = grossAmount
    )
}