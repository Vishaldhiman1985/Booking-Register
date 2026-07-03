package com.example.bookingregister.accounting.integrity

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.folio.domain.FolioSummaryBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentIntegrityTest {

    @Test
    fun payment_refund_and_adjustment_change_balance_consistently() {
        val booking = BookingEntity(
            remoteId = "booking_1",
            bookingUuid = "BR-1",
            hotelRemoteId = "hotel_1",
            guestName = "Ram",
            sourceName = "Walk-in",
            sourceType = BookingSourceType.DIRECT,
            checkInMillis = 1_700_000_000_000L,
            checkOutMillis = 1_700_086_400_000L,
            roomRemoteIds = listOf("room_101"),
            grossCharges = 10_000.0,
            roomRevenue = 9_523.82,
            propertyTax = 476.18,
            rate = 10_000.0,
            receivable = 10_000.0
        )

        val roomLines = listOf(
            BookingFinancialLineEntity(
                remoteId = "line_1",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_1",
                roomRemoteId = "room_101",
                businessDateMillis = 1_700_000_000_000L,
                grossAmount = 10_000.0,
                taxableAmount = 9_523.82,
                gstRatePercent = 5.0,
                gstAmount = 476.18,
                hsnSacCode = "996311",
                cgstRatePercent = 2.5,
                sgstRatePercent = 2.5,
                cgstAmount = 238.09,
                sgstAmount = 238.09
            )
        )

        val payments = listOf(
            BookingPaymentEntity(
                remoteId = "payment_1",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_1",
                paymentType = BookingPaymentType.PAYMENT,
                paymentCategory = BookingPaymentCategory.STAY,
                amount = 7_000.0
            ),
            BookingPaymentEntity(
                remoteId = "refund_1",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_1",
                paymentType = BookingPaymentType.REFUND,
                paymentCategory = BookingPaymentCategory.STAY,
                amount = 1_000.0
            ),
            BookingPaymentEntity(
                remoteId = "adjustment_1",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_1",
                paymentType = BookingPaymentType.ADJUSTMENT,
                paymentCategory = BookingPaymentCategory.STAY,
                amount = 500.0
            )
        )

        val folio = FolioSummaryBuilder.build(
            booking = booking,
            payments = payments,
            foodOrders = emptyList(),
            bookingFinancialLines = roomLines
        )

        assertEquals(10_000.0, folio.stayTotal, 0.01)

        // 7000 payment - 1000 refund - 500 adjustment = 5500 net paid
        assertEquals(5_500.0, folio.stayPaid, 0.01)

        // 10000 charge - 5500 paid = 4500 balance
        assertEquals(4_500.0, folio.stayBalance, 0.01)
        assertEquals(4_500.0, folio.grandBalance, 0.01)
    }
}