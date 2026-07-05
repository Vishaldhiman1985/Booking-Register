package com.example.bookingregister.room.domain

import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.RoomEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomLifecyclePolicyTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `cannot delete room with any historical booking`() {
        val error = RoomLifecyclePolicy.deleteError(RoomHistoryFacts(bookingCount = 1))
        assertTrue(error?.contains("cannot be deleted") == true)
    }

    @Test
    fun `unused room can be deleted`() {
        assertNull(RoomLifecyclePolicy.deleteError(RoomHistoryFacts()))
    }

    @Test
    fun `cannot disable or retire room with current or future booking`() {
        val future = booking(checkIn = now + 1_000, checkOut = now + 10_000)
        val blocking = RoomLifecyclePolicy.blockingBookings("room_1", listOf(future), now)
        assertEquals(listOf(future), blocking)
        assertTrue(
            RoomLifecyclePolicy.inactiveTransitionError(
                RoomLifecycleStatus.RETIRED,
                "Renovation",
                blocking
            )?.contains("Move, cancel, or check out") == true
        )
    }

    @Test
    fun `past checked out history permits retirement with reason`() {
        val past = booking(
            checkIn = now - 20_000,
            checkOut = now - 10_000,
            status = BookingStatus.CHECKED_OUT
        )
        val blocking = RoomLifecyclePolicy.blockingBookings("room_1", listOf(past), now)
        assertTrue(blocking.isEmpty())
        assertNull(
            RoomLifecyclePolicy.inactiveTransitionError(
                RoomLifecycleStatus.RETIRED,
                "Property renovation",
                blocking
            )
        )
        assertNull(RoomLifecyclePolicy.retirementBillingError(hasUnbilledPastBooking = false))
    }

    @Test
    fun `past unbilled booking blocks retirement`() {
        assertTrue(
            RoomLifecyclePolicy.retirementBillingError(hasUnbilledPastBooking = true)
                ?.contains("final bill") == true
        )
    }

    @Test
    fun `retirement requires reason`() {
        assertEquals(
            "A reason is required.",
            RoomLifecyclePolicy.inactiveTransitionError(RoomLifecycleStatus.RETIRED, "  ", emptyList())
        )
    }

    @Test
    fun `retired room is hidden from current and future chart`() {
        val room = room(RoomLifecycleStatus.RETIRED)
        val future = booking(checkIn = now + 1_000, checkOut = now + 10_000)
        assertTrue(
            !RoomLifecyclePolicy.isVisibleInChartWindow(
                room, listOf(future), now, now + 20_000, now
            )
        )
        assertTrue(!RoomLifecyclePolicy.isBookable(room))
    }

    @Test
    fun `retired room remains visible in past chart containing its booking`() {
        val room = room(RoomLifecycleStatus.RETIRED)
        val past = booking(
            checkIn = now - 20_000,
            checkOut = now - 10_000,
            status = BookingStatus.CHECKED_OUT
        )
        assertTrue(
            RoomLifecyclePolicy.isVisibleInChartWindow(
                room, listOf(past), now - 30_000, now - 1_000, now
            )
        )
    }

    @Test
    fun `only active room is bookable`() {
        assertTrue(RoomLifecyclePolicy.isBookable(room(RoomLifecycleStatus.ACTIVE)))
        assertTrue(!RoomLifecyclePolicy.isBookable(room(RoomLifecycleStatus.DISABLED)))
        assertTrue(!RoomLifecyclePolicy.isBookable(room(RoomLifecycleStatus.RETIRED)))
    }

    private fun room(status: String) = RoomEntity(
        remoteId = "room_1",
        hotelRemoteId = "hotel_1",
        roomName = "101",
        lifecycleStatus = status
    )

    private fun booking(
        checkIn: Long,
        checkOut: Long,
        status: String = BookingStatus.RESERVED
    ) = BookingEntity(
        remoteId = "booking_1",
        bookingUuid = "uuid_1",
        hotelRemoteId = "hotel_1",
        guestName = "Guest",
        checkInMillis = checkIn,
        checkOutMillis = checkOut,
        roomRemoteIds = listOf("room_1"),
        bookingStatus = status
    )
}
