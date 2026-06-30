package com.example.bookingregister.finalbill.domain

import com.example.bookingregister.accounting.domain.PaymentAllocationPolicy
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.folio.domain.FolioSummaryBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingFolioScenarioTest {
    @Test
    fun autoPaymentsSettleRoomFirstThenFoodWithoutRoomOverpay() {
        val booking = booking(receivable = 5_000.0)
        val foodOrders = listOf(
            foodOrder("order_1", 200.0),
            foodOrder("order_2", 80.0)
        )

        val advance = payment("advance", amount = 1_000.0, stay = 1_000.0, category = BookingPaymentCategory.STAY, type = BookingPaymentType.ADVANCE)
        val roomPayment = allocatePayment("room_payment", booking, listOf(advance), foodOrders, amount = 4_000.0)
        val foodPayment = allocatePayment("food_payment", booking, listOf(advance, roomPayment), foodOrders, amount = 280.0)

        val summary = FolioSummaryBuilder.build(booking, listOf(advance, roomPayment, foodPayment), foodOrders)

        assertEquals(5_000.0, summary.stayTotal, 0.001)
        assertEquals(280.0, summary.foodTotal, 0.001)
        assertEquals(5_000.0, summary.stayPaid, 0.001)
        assertEquals(280.0, summary.foodPaid, 0.001)
        assertEquals(0.0, summary.stayBalance, 0.001)
        assertEquals(0.0, summary.foodBalance, 0.001)
        assertEquals(0.0, summary.grandBalance, 0.001)
    }


    @Test
    fun folioSettlementCanClearStaleBookingBalanceForCheckout() {
        val booking = booking(receivable = 2_000.0).copy(
            paid = 500.0,
            balance = 1_500.0
        )
        val advance = payment(
            remoteId = "advance",
            amount = 500.0,
            stay = 500.0,
            category = BookingPaymentCategory.STAY,
            type = BookingPaymentType.ADVANCE
        )
        val roomPayment = payment(
            remoteId = "room_payment",
            amount = 1_500.0,
            stay = 1_500.0,
            category = BookingPaymentCategory.STAY
        )

        val summary = FolioSummaryBuilder.build(booking, listOf(advance, roomPayment), emptyList())

        assertEquals(2_000.0, summary.stayTotal, 0.001)
        assertEquals(2_000.0, summary.stayPaid, 0.001)
        assertEquals(0.0, summary.stayBalance, 0.001)
        assertEquals(0.0, summary.grandBalance, 0.001)
    }
    private fun allocatePayment(
        remoteId: String,
        booking: BookingEntity,
        existingPayments: List<BookingPaymentEntity>,
        foodOrders: List<FoodOrderEntity>,
        amount: Double
    ): BookingPaymentEntity {
        val summary = FolioSummaryBuilder.build(booking, existingPayments, foodOrders)
        val allocation = PaymentAllocationPolicy.allocate(
            amount = amount,
            selectedCategory = BookingPaymentCategory.AUTO,
            charges = summary.chargeBuckets,
            alreadyPaid = summary.paidBuckets
        )
        return payment(
            remoteId = remoteId,
            amount = amount,
            stay = allocation.stayAmount,
            food = allocation.foodAmount,
            category = allocation.selectedCategory
        )
    }

    private fun booking(receivable: Double): BookingEntity = BookingEntity(
        remoteId = "booking_1",
        bookingUuid = "BK-1",
        hotelRemoteId = "hotel_1",
        guestName = "Guest",
        checkInMillis = 1L,
        checkOutMillis = 2L,
        roomRemoteIds = listOf("room_1"),
        receivable = receivable,
        paid = 1_000.0,
        balance = receivable - 1_000.0
    )

    private fun foodOrder(remoteId: String, total: Double): FoodOrderEntity = FoodOrderEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        roomRemoteId = "room_1",
        roomName = "101",
        guestName = "Guest",
        totalAmount = total,
        subtotal = total
    )

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
        allocatedFoodAmount = food
    )
}
