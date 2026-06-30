package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.FoodBillingScope
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class WalkInScenarioTest {

    @Test
    fun ramScenarioCalculatesCorrectBalance() {
        val booking = BookingEntity(
            remoteId = "booking_ram",
            bookingUuid = "BK-RAM",
            hotelRemoteId = "hotel_1",
            guestName = "Ram",
            checkInMillis = 0L,
            checkOutMillis = 86_400_000L,
            roomRemoteIds = listOf("room_1"),
            grossCharges = 6_000.0,
            receivable = 6_000.0,
            rate = 6_000.0
        )

        val foodOrders = listOf(
            foodOrder(remoteId = "food_1000", total = 1_000.0),
            foodOrder(remoteId = "food_500", total = 500.0)
        )

        val payments = listOf(
            payment(remoteId = "advance_2000", amount = 2_000.0, stay = 2_000.0),
            payment(remoteId = "payment_1000", amount = 1_000.0, stay = 1_000.0)
        )

        val summary = FolioSummaryBuilder.build(
            booking = booking,
            payments = payments,
            foodOrders = foodOrders
        )

        assertEquals(6_000.0, summary.stayTotal, 0.01)
        assertEquals(1_500.0, summary.foodTotal, 0.01)
        assertEquals(7_500.0, summary.grandTotal, 0.01)

        assertEquals(3_000.0, summary.stayPaid, 0.01)
        assertEquals(0.0, summary.foodPaid, 0.01)
        assertEquals(3_000.0, summary.totalPaid, 0.01)

        assertEquals(3_000.0, summary.stayBalance, 0.01)
        assertEquals(1_500.0, summary.foodBalance, 0.01)
        assertEquals(4_500.0, summary.grandBalance, 0.01)
    }

    private fun foodOrder(remoteId: String, total: Double): FoodOrderEntity =
        FoodOrderEntity(
            remoteId = remoteId,
            hotelRemoteId = "hotel_1",
            bookingRemoteId = "booking_ram",
            guestName = "Ram",
            foodBillingScope = FoodBillingScope.IN_HOUSE_BOOKING,
            status = FoodOrderStatus.OPEN,
            orderMillis = 1_000L,
            totalAmount = total
        )

    private fun payment(
        remoteId: String,
        amount: Double,
        stay: Double = 0.0,
        food: Double = 0.0
    ): BookingPaymentEntity =
        BookingPaymentEntity(
            remoteId = remoteId,
            hotelRemoteId = "hotel_1",
            bookingRemoteId = "booking_ram",
            amount = amount,
            allocatedStayAmount = stay,
            allocatedFoodAmount = food,
            paymentMillis = 1L
        )
}