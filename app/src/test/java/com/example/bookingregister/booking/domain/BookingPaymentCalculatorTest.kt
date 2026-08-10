package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingPaymentCalculatorTest {
    @Test
    fun complimentaryPricingOverridesStaleUnpaidPaymentState() {
        val normalized = BookingPaymentCalculator.normalize(
            booking(
                pricingStatus = BookingPricingStatus.COMPLIMENTARY,
                paymentStatus = BookingPaymentStatus.NOT_PAID,
                rate = 5_000.0,
                receivable = 5_000.0,
                paid = 0.0,
                balance = 5_000.0
            )
        )

        assertEquals(BookingPaymentStatus.COMPLIMENTARY, normalized.paymentStatus)
        assertEquals(0.0, normalized.rate, 0.001)
        assertEquals(0.0, normalized.receivable, 0.001)
        assertEquals(0.0, normalized.paid, 0.001)
        assertEquals(0.0, normalized.balance, 0.001)
    }

    @Test
    fun confirmedZeroValueBookingRemainsNotPaid() {
        val normalized = BookingPaymentCalculator.normalize(
            booking(
                pricingStatus = BookingPricingStatus.CONFIRMED,
                paymentStatus = BookingPaymentStatus.NOT_PAID
            )
        )

        assertEquals(BookingPaymentStatus.NOT_PAID, normalized.paymentStatus)
    }

    private fun booking(
        pricingStatus: String,
        paymentStatus: String,
        rate: Double = 0.0,
        receivable: Double = 0.0,
        paid: Double = 0.0,
        balance: Double = 0.0
    ) = BookingEntity(
        remoteId = "booking_1",
        bookingUuid = "BOOKING-1",
        hotelRemoteId = "hotel_1",
        guestName = "Guest",
        checkInMillis = 0L,
        checkOutMillis = 86_400_000L,
        roomRemoteIds = listOf("room_1"),
        rate = rate,
        receivable = receivable,
        paid = paid,
        balance = balance,
        paymentStatus = paymentStatus,
        pricingStatus = pricingStatus
    )
}
