package com.example.bookingregister.accounting.integrity

import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodBillingLifecycleTest {

    @Test
    fun only_unbilled_food_orders_should_be_selected_for_next_final_bill() {

        val firstBillId = "final_bill_1"

        val orders = listOf(

            // Already billed in previous final bill
            FoodOrderEntity(
                remoteId = "food_old",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_1",
                guestName = "Ram",
                linkedFinalBillId = firstBillId,
                archivedAt = 1_700_000_000_000L,
                status = FoodOrderStatus.BILLED_IN_FOLIO,
                totalAmount = 500.0
            ),

            // Newly ordered food
            FoodOrderEntity(
                remoteId = "food_new",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_1",
                guestName = "Ram",
                status = FoodOrderStatus.OPEN,
                totalAmount = 300.0
            )
        )

        val unbilledOrders = orders.filter {
            !it.isDeleted &&
                    it.status != FoodOrderStatus.CANCELLED &&
                    it.status != FoodOrderStatus.BILLED &&
                    it.status != FoodOrderStatus.BILLED_IN_FOLIO &&
                    it.linkedFinalBillId.isNullOrBlank()
        }

        assertEquals(1, unbilledOrders.size)
        assertEquals("food_new", unbilledOrders.first().remoteId)
        assertEquals(300.0, unbilledOrders.first().totalAmount, 0.01)
    }
}