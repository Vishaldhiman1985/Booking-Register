package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingChartVisibilityPolicyTest {
    @Test
    fun `reserved booking is visible on chart`() {
        assertTrue(BookingChartVisibilityPolicy.isVisible(booking(BookingStatus.RESERVED)))
    }

    @Test
    fun `cancelled booking is retained but hidden from chart`() {
        val cancelled = booking(BookingStatus.CANCELLED).copy(
            cancellationReason = "Guest cancelled",
            cancelledAt = 1_500L
        )

        assertFalse(cancelled.isDeleted)
        assertFalse(BookingChartVisibilityPolicy.isVisible(cancelled))
    }

    @Test
    fun `checked out booking remains visible on historical chart`() {
        assertTrue(BookingChartVisibilityPolicy.isVisible(booking(BookingStatus.CHECKED_OUT)))
    }

    @Test
    fun `deleted record is never visible on chart`() {
        assertFalse(
            BookingChartVisibilityPolicy.isVisible(
                booking(BookingStatus.RESERVED).copy(isDeleted = true)
            )
        )
    }

    private fun booking(status: String) = BookingEntity(
        remoteId = "booking-1",
        bookingUuid = "booking-uuid-1",
        hotelRemoteId = "hotel-1",
        guestName = "Guest",
        checkInMillis = 1_000L,
        checkOutMillis = 2_000L,
        roomRemoteIds = listOf("room-1"),
        bookingStatus = status
    )
}
