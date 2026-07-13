package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefundAllocationPolicyTest {
    private val original = BookingPaymentEntity(
        remoteId = "payment_1",
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        paymentType = BookingPaymentType.PAYMENT,
        paymentCategory = BookingPaymentCategory.AUTO,
        amount = 1_000.0,
        allocatedStayAmount = 400.0,
        allocatedFoodAmount = 300.0,
        allocatedServiceAmount = 200.0,
        allocatedDamageAmount = 100.0
    )

    @Test
    fun `partial refund reverses original allocations proportionally`() {
        val refund = RefundAllocationPolicy.reverse(original, 250.0, 0.0)!!
        assertEquals(100.0, refund.stayAmount, 0.001)
        assertEquals(75.0, refund.foodAmount, 0.001)
        assertEquals(50.0, refund.serviceAmount, 0.001)
        assertEquals(25.0, refund.damageAmount, 0.001)
    }

    @Test
    fun `multiple refunds cannot exceed original payment`() {
        assertNull(RefundAllocationPolicy.reverse(original, 301.0, 700.0))
        assertEquals(300.0, RefundAllocationPolicy.remainingRefundable(original, 700.0), 0.001)
    }
}
