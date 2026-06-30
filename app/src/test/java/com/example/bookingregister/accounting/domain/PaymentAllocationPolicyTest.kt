package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.BookingPaymentCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentAllocationPolicyTest {
    @Test
    fun autoAllocatesStayBeforeFood() {
        val allocation = PaymentAllocationPolicy.allocate(
            amount = 3_000.0,
            selectedCategory = BookingPaymentCategory.AUTO,
            charges = ChargeBuckets(stay = 5_000.0, food = 2_000.0),
            alreadyPaid = ChargeBuckets(stay = 3_000.0)
        )

        assertEquals(2_000.0, allocation.stayAmount, 0.01)
        assertEquals(1_000.0, allocation.foodAmount, 0.01)
        assertEquals(0.0, allocation.serviceAmount, 0.01)
        assertEquals(0.0, allocation.unappliedAmount, 0.01)
    }

    @Test
    fun manualFoodPaymentDoesNotTouchStayBalance() {
        val allocation = PaymentAllocationPolicy.allocate(
            amount = 1_000.0,
            selectedCategory = BookingPaymentCategory.FOOD,
            charges = ChargeBuckets(stay = 5_000.0, food = 2_000.0),
            alreadyPaid = ChargeBuckets()
        )

        assertEquals(0.0, allocation.stayAmount, 0.01)
        assertEquals(1_000.0, allocation.foodAmount, 0.01)
        assertEquals(0.0, allocation.serviceAmount, 0.01)
    }

    @Test
    fun overPaymentIsKeptUnapplied() {
        val allocation = PaymentAllocationPolicy.allocate(
            amount = 8_000.0,
            selectedCategory = BookingPaymentCategory.AUTO,
            charges = ChargeBuckets(stay = 5_000.0, food = 2_000.0),
            alreadyPaid = ChargeBuckets()
        )

        assertEquals(5_000.0, allocation.stayAmount, 0.01)
        assertEquals(2_000.0, allocation.foodAmount, 0.01)
        assertEquals(1_000.0, allocation.unappliedAmount, 0.01)
    }
    @Test
    fun autoAllocatesToServiceOnlyAfterStayAndFoodAreSettled() {
        val allocation = PaymentAllocationPolicy.allocate(
            amount = 150.0,
            selectedCategory = BookingPaymentCategory.AUTO,
            charges = ChargeBuckets(stay = 5_000.0, food = 280.0, service = 100.0),
            alreadyPaid = ChargeBuckets(stay = 5_000.0, food = 280.0)
        )

        assertEquals(0.0, allocation.stayAmount, 0.01)
        assertEquals(0.0, allocation.foodAmount, 0.01)
        assertEquals(100.0, allocation.serviceAmount, 0.01)
        assertEquals(50.0, allocation.unappliedAmount, 0.01)
    }

    @Test
    fun explicitStayPaymentDoesNotLeakIntoFoodWhenStayIsSettled() {
        val allocation = PaymentAllocationPolicy.allocate(
            amount = 500.0,
            selectedCategory = BookingPaymentCategory.STAY,
            charges = ChargeBuckets(stay = 5_000.0, food = 280.0),
            alreadyPaid = ChargeBuckets(stay = 5_000.0)
        )

        assertEquals(0.0, allocation.stayAmount, 0.01)
        assertEquals(0.0, allocation.foodAmount, 0.01)
        assertEquals(0.0, allocation.serviceAmount, 0.01)
        assertEquals(500.0, allocation.unappliedAmount, 0.01)
    }
}

