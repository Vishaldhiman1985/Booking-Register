package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.folio.domain.MiniFolioBuilder
import com.example.bookingregister.folio.domain.MiniFolioStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class BookingPricingStatusTest {
    @Test
    fun otaBookingRemainsReservedWhilePricingIsPending() {
        val booking = pendingOtaBooking()

        assertEquals(BookingSourceType.OTA, booking.sourceType)
        assertEquals(BookingStatus.RESERVED, booking.bookingStatus)
        assertEquals(BookingPricingStatus.PENDING, booking.pricingStatus)
        assertTrue(booking.roomRemoteIds.isNotEmpty())
    }

    @Test
    fun pendingPricingWithoutRoomLinesIsNotAnIntegrityFailureOrSettledFolio() {
        val folio = requireNotNull(
            MiniFolioBuilder().buildForBooking(
                booking = pendingOtaBooking(),
                activeRoomIds = setOf("room_1"),
                bookingFinancialLines = emptyList()
            )
        )

        assertTrue(folio.integrityErrors.isEmpty())
        assertEquals(MiniFolioStatus.PRICING_PENDING, folio.status)
        assertEquals(0.0, folio.totalCharges, 0.001)
    }

    @Test
    fun pendingPricingBlocksOnlyRoomFinancialCompletion() {
        assertFalse(BookingPricingStatus.canTakeStayPayment(BookingPricingStatus.PENDING))
        assertFalse(BookingPricingStatus.canGenerateRoomBill(BookingPricingStatus.PENDING))
        assertTrue(BookingPricingStatus.canTakeStayPayment(BookingPricingStatus.CONFIRMED))
        assertTrue(BookingPricingStatus.canGenerateRoomBill(BookingPricingStatus.COMPLIMENTARY))
    }

    private fun pendingOtaBooking() = BookingEntity(
        remoteId = "ota_pending_1",
        bookingUuid = "OTA-PENDING-1",
        hotelRemoteId = "hotel_1",
        guestName = "OTA Guest",
        sourceType = BookingSourceType.OTA,
        checkInMillis = 0L,
        checkOutMillis = 86_400_000L,
        roomRemoteIds = listOf("room_1"),
        bookingStatus = BookingStatus.RESERVED,
        pricingStatus = BookingPricingStatus.PENDING
    )
}
