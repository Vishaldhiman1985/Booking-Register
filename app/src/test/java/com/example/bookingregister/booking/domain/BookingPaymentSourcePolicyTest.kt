package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingPaymentSourcePolicyTest {
    @Test
    fun initialAdvanceIsEditableOnlyBeforeFirstSave() {
        assertTrue(BookingPaymentSourcePolicy.canEditInitialAdvance(existingBooking = null))
        assertFalse(BookingPaymentSourcePolicy.canEditInitialAdvance(existingBooking = booking(paid = 0.0)))
    }

    @Test
    fun paymentRowsOverrideAnyPaidValueSubmittedByBookingEditor() {
        val resolved = BookingPaymentSourcePolicy.authoritativeStayPaid(
            existingBooking = booking(paid = 2_000.0),
            requestedPaid = 9_000.0,
            hasPaymentRows = true,
            stayPaidFromRows = 5_000.0
        )

        assertEquals(5_000.0, resolved, 0.01)
    }

    @Test
    fun persistedBookingWithoutRowsPreservesItsPreviousPaidAmount() {
        val resolved = BookingPaymentSourcePolicy.authoritativeStayPaid(
            existingBooking = booking(paid = 2_000.0),
            requestedPaid = 8_000.0,
            hasPaymentRows = false,
            stayPaidFromRows = 0.0
        )

        assertEquals(2_000.0, resolved, 0.01)
    }

    @Test
    fun newBookingAcceptsInitialAdvanceForPaymentRowSeeding() {
        val resolved = BookingPaymentSourcePolicy.authoritativeStayPaid(
            existingBooking = null,
            requestedPaid = 2_000.0,
            hasPaymentRows = false,
            stayPaidFromRows = 0.0
        )

        assertEquals(2_000.0, resolved, 0.01)
    }

    private fun booking(paid: Double) = BookingEntity(
        remoteId = "booking",
        bookingUuid = "booking-uuid",
        hotelRemoteId = "hotel",
        guestName = "Guest",
        checkInMillis = 1_000L,
        checkOutMillis = 2_000L,
        roomRemoteIds = listOf("room"),
        paid = paid
    )
}
