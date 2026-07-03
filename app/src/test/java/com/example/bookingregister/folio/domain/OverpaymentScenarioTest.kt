package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingPaymentEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.bookingregister.authoritativeRoomLines

class OverpaymentScenarioTest {

    @Test
    fun overpaymentDoesNotCreateNegativeBalance() {
        val booking = BookingEntity(
            remoteId = "booking_overpaid",
            bookingUuid = "BK-OVER",
            hotelRemoteId = "hotel_1",
            guestName = "Overpaid Guest",
            checkInMillis = 0L,
            checkOutMillis = 86_400_000L,
            roomRemoteIds = listOf("room_1"),
            grossCharges = 7_500.0,
            receivable = 7_500.0,
            rate = 7_500.0
        )

        val payments = listOf(
            BookingPaymentEntity(
                remoteId = "payment_8000",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_overpaid",
                amount = 8_000.0,
                allocatedStayAmount = 8_000.0,
                paymentMillis = 1L
            )
        )

        val summary = FolioSummaryBuilder.build(
            booking = booking,
            payments = payments,
            foodOrders = emptyList(),
            bookingFinancialLines = authoritativeRoomLines(booking)
        )

        assertEquals(7_500.0, summary.grandTotal, 0.01)
        assertEquals(8_000.0, summary.totalPaid, 0.01)
        assertEquals(0.0, summary.grandBalance, 0.01)

        // Future improvement:
        // when unapplied/refundable credit is implemented,
        // this should become 500.0 instead of 0.0.
        assertEquals(0.0, summary.unappliedPaid, 0.01)
    }
}
