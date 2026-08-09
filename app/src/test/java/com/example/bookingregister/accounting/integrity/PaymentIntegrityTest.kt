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
    fun overpayment_correction_reduces_unapplied_credit_without_reopening_room_balance() {
        val booking = BookingEntity(
            remoteId = "vinay_booking",
            bookingUuid = "VINAY-1",
            hotelRemoteId = "hotel_1",
            guestName = "Vinay Kansal",
            sourceName = "Direct",
            sourceType = BookingSourceType.DIRECT,
            checkInMillis = 1_700_000_000_000L,
            checkOutMillis = 1_700_086_400_000L,
            roomRemoteIds = listOf("M101"),
            grossCharges = 4_500.0,
            rate = 4_500.0,
            receivable = 4_500.0
        )
        val roomLines = listOf(
            BookingFinancialLineEntity(
                remoteId = "vinay_line",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = booking.remoteId,
                roomRemoteId = "M101",
                businessDateMillis = booking.checkInMillis,
                grossAmount = 4_500.0,
                taxableAmount = 4_285.71,
                gstRatePercent = 5.0,
                gstAmount = 214.29
            )
        )
        val payments = listOf(
            BookingPaymentEntity(
                remoteId = "advance_1500",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = booking.remoteId,
                paymentType = BookingPaymentType.ADVANCE,
                paymentCategory = BookingPaymentCategory.STAY,
                amount = 1_500.0,
                allocatedStayAmount = 1_500.0
            ),
            BookingPaymentEntity(
                remoteId = "payment_3500",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = booking.remoteId,
                paymentType = BookingPaymentType.PAYMENT,
                paymentCategory = BookingPaymentCategory.STAY,
                amount = 3_500.0,
                allocatedStayAmount = 3_000.0,
                unappliedAmount = 500.0
            ),
            BookingPaymentEntity(
                remoteId = "correction_500",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = booking.remoteId,
                paymentType = BookingPaymentType.ADJUSTMENT,
                paymentCategory = BookingPaymentCategory.STAY,
                amount = 500.0,
                unappliedAmount = 500.0
            )
        )

        val folio = FolioSummaryBuilder.build(
            booking = booking,
            payments = payments,
            foodOrders = emptyList(),
            bookingFinancialLines = roomLines
        )

        assertEquals(4_500.0, folio.stayPaid, 0.01)
        assertEquals(0.0, folio.stayBalance, 0.01)
        assertEquals(4_500.0, folio.totalPaid, 0.01)
        assertEquals(0.0, folio.grandBalance, 0.01)
    }

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
