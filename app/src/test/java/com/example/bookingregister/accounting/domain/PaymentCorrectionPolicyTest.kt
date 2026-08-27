package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class PaymentCorrectionPolicyTest {
    private val original = BookingPaymentEntity(
        remoteId = "wrong_payment_2000",
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "danish_booking",
        paymentType = BookingPaymentType.PAYMENT,
        paymentCategory = BookingPaymentCategory.STAY,
        amount = 2_000.0,
        allocatedStayAmount = 2_000.0
    )

    @Test
    fun `full correction reverses the exact original payment allocation`() {
        val allocation = PaymentCorrectionPolicy.reverseRemaining(
            original = original,
            payments = listOf(original),
            correctionAmount = 2_000.0
        )

        assertNotNull(allocation)
        assertEquals(2_000.0, allocation!!.stayAmount, 0.001)
        assertEquals(0.0, allocation.unappliedAmount, 0.001)
    }

    @Test
    fun `partial correction is rejected so the original payment cannot be ambiguously edited`() {
        assertNull(
            PaymentCorrectionPolicy.reverseRemaining(
                original = original,
                payments = listOf(original),
                correctionAmount = 1_000.0
            )
        )
    }

    @Test
    fun `refund already linked to original reduces the remaining correctable amount`() {
        val refund = BookingPaymentEntity(
            remoteId = "refund_500",
            hotelRemoteId = "hotel_1",
            bookingRemoteId = "danish_booking",
            originalPaymentRemoteId = original.remoteId,
            paymentType = BookingPaymentType.REFUND,
            paymentCategory = BookingPaymentCategory.STAY,
            amount = 500.0,
            allocatedStayAmount = 500.0
        )

        assertEquals(
            1_500.0,
            PaymentCorrectionPolicy.remainingCorrectable(original, listOf(original, refund)),
            0.001
        )
    }
    @Test
    fun `legacy payment without explicit allocations still reverses its folio bucket`() {
        val legacy = BookingPaymentEntity(
            remoteId = "legacy_payment",
            hotelRemoteId = "hotel_1",
            bookingRemoteId = "booking_legacy",
            paymentType = BookingPaymentType.PAYMENT,
            paymentCategory = BookingPaymentCategory.STAY,
            amount = 2_000.0
        )

        val allocation = PaymentCorrectionPolicy.reverseRemaining(
            original = legacy,
            payments = listOf(legacy),
            correctionAmount = 2_000.0
        )

        assertNotNull(allocation)
        assertEquals(2_000.0, allocation!!.stayAmount, 0.001)
        assertEquals(0.0, allocation.unappliedAmount, 0.001)
    }

}
