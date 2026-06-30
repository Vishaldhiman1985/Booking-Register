package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentAllocationRepairPolicyTest {
    @Test
    fun movesNewestAutoStayOverpaymentToFoodBalance() {
        val repaired = PaymentAllocationRepairPolicy.moveAutoStayOverpaymentToFood(
            stayTotal = 5_000.0,
            foodTotal = 280.0,
            payments = listOf(
                payment("advance", amount = 1_000.0, stay = 1_000.0, category = BookingPaymentCategory.STAY, type = BookingPaymentType.ADVANCE),
                payment("room", amount = 4_000.0, stay = 4_000.0),
                payment("food", amount = 280.0, stay = 280.0)
            ),
            now = 10L
        )

        assertEquals(1, repaired.size)
        assertEquals("food", repaired.single().remoteId)
        assertEquals(0.0, repaired.single().allocatedStayAmount, 0.001)
        assertEquals(280.0, repaired.single().allocatedFoodAmount, 0.001)
    }

    @Test
    fun explicitStayPaymentsAreNotMoved() {
        val repaired = PaymentAllocationRepairPolicy.moveAutoStayOverpaymentToFood(
            stayTotal = 5_000.0,
            foodTotal = 280.0,
            payments = listOf(
                payment("stay", amount = 5_280.0, stay = 5_280.0, category = BookingPaymentCategory.STAY)
            ),
            now = 10L
        )

        assertEquals(0, repaired.size)
    }

    private fun payment(
        remoteId: String,
        amount: Double,
        stay: Double,
        food: Double = 0.0,
        category: String = BookingPaymentCategory.AUTO,
        type: String = BookingPaymentType.PAYMENT
    ): BookingPaymentEntity = BookingPaymentEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        paymentType = type,
        paymentCategory = category,
        amount = amount,
        allocatedStayAmount = stay,
        allocatedFoodAmount = food,
        paymentMillis = amount.toLong()
    )
}