package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class InitialPaymentAllocationTest {
    @Test
    fun `initial advance is allocated to stay using normal policy`() {
        val allocation = PaymentAllocationPolicy.allocate(
            amount = 500.0,
            selectedCategory = BookingPaymentCategory.AUTO,
            charges = ChargeBuckets(stay = 2_800.0)
        )
        assertEquals(500.0, allocation.stayAmount, 0.001)
        assertEquals(0.0, allocation.foodAmount, 0.001)
        assertEquals(0.0, allocation.unappliedAmount, 0.001)
    }

    @Test
    fun `initial overpayment leaves exact excess unapplied`() {
        val allocation = PaymentAllocationPolicy.allocate(
            amount = 3_000.0,
            selectedCategory = BookingPaymentCategory.AUTO,
            charges = ChargeBuckets(stay = 2_800.0)
        )
        assertEquals(2_800.0, allocation.stayAmount, 0.001)
        assertEquals(200.0, allocation.unappliedAmount, 0.001)
        assertEquals(3_000.0, allocation.appliedTotal + allocation.unappliedAmount, 0.001)
    }

    @Test
    fun `remote hydration cannot seed a second initial payment identity`() {
        val booking = booking(paid = 500.0)
        val first = InitialPaymentFactory.create(booking)!!
        val repeated = InitialPaymentFactory.create(booking)!!
        assertEquals("booking-a_payment_initial_paid", first.remoteId)
        assertEquals(first.remoteId, repeated.remoteId)
    }

    @Test
    fun `legacy zero allocation is repaired with the normal allocation policy`() {
        val allocation = PaymentAllocationPolicy.allocate(
            amount = 500.0,
            selectedCategory = BookingPaymentCategory.AUTO,
            charges = ChargeBuckets(stay = 2_800.0)
        )
        assertEquals(500.0, allocation.stayAmount, 0.001)
        assertEquals(500.0, allocation.appliedTotal + allocation.unappliedAmount, 0.001)
    }

    private fun booking(paid: Double) = BookingEntity(
        remoteId = "booking-a",
        bookingUuid = "booking-a",
        hotelRemoteId = "hotel-a",
        guestName = "Guest",
        checkInMillis = 1,
        checkOutMillis = 2,
        roomRemoteIds = listOf("room-a"),
        grossCharges = 2_800.0,
        paid = paid,
        updatedAt = 100
    )
}
