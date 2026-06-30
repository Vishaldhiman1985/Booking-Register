package com.example.bookingregister.finalbill.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
import com.example.bookingregister.data.entities.BookingAccountingChargeType
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.data.entities.FoodBillingScope
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderItemEntity
import com.example.bookingregister.data.entities.FoodOrderStatus
import com.example.bookingregister.data.entities.RoomEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalBillPreviewBuilderTest {
    private val builder = FinalBillPreviewBuilder()

    @Test
    fun consolidatedBillKeepsRoomAndFoodSeparateWithGstInclusiveFood() {
        val preview = builder.build(
            booking = booking(),
            rooms = listOf(room()),
            bookingPayments = listOf(
                payment("advance", amount = 1_000.0, stay = 1_000.0, type = BookingPaymentType.ADVANCE, category = BookingPaymentCategory.STAY),
                payment("room", amount = 4_000.0, stay = 4_000.0),
                payment("food", amount = 280.0, food = 280.0)
            ),
            foodOrders = listOf(foodOrder()),
            foodOrderItems = listOf(
                foodItem("item_1", "Prantha", quantity = 2.0, unitPrice = 100.0, taxable = 190.48, gst = 9.52, total = 200.0),
                foodItem("item_2", "Omlate", quantity = 1.0, unitPrice = 80.0, taxable = 76.19, gst = 3.81, total = 80.0)
            )
        )

        assertEquals(5_000.0, preview.roomCharges, 0.01)
        assertEquals(5_000.0, preview.roomPaid, 0.01)
        assertEquals(0.0, preview.roomBalance, 0.01)
        assertEquals(280.0, preview.foodTotal, 0.01)
        assertEquals(280.0, preview.foodPaid, 0.01)
        assertEquals(0.0, preview.foodBalance, 0.01)
        assertEquals(266.67, preview.foodSubtotal, 0.01)
        assertEquals(13.33, preview.foodGst, 0.01)
        assertEquals(5_280.0, preview.totalCharges, 0.01)
        assertEquals(5_280.0, preview.totalPaid, 0.01)
        assertEquals(0.0, preview.folioBalance, 0.01)
        assertEquals(1, preview.gstSummaries.size)
        assertEquals(5.0, preview.gstSummaries.single().gstRatePercent, 0.01)
        assertEquals(13.33, preview.gstSummaries.single().totalGstAmount, 0.01)

        val ledgerText = FinalBillTextFormatter.formatBalanceLedger(preview)
        assertTrue(ledgerText.contains("Room Final Price: Rs 5000"))
        assertTrue(ledgerText.contains("Food Total: Rs 280"))
        assertTrue(ledgerText.contains("Food Paid: Rs 280"))
        assertTrue(ledgerText.contains("Balance: Rs 0"))
    }

    @Test
    fun finalBillPreviewKeepsAlreadyBilledFoodInCompleteLedger() {
        val preview = builder.build(
            booking = booking(),
            rooms = listOf(room()),
            bookingPayments = emptyList(),
            foodOrders = listOf(foodOrder(status = FoodOrderStatus.BILLED, total = 280.0)),
            foodOrderItems = listOf(foodItem("item_1", "Prantha", quantity = 2.0, unitPrice = 100.0, taxable = 190.48, gst = 9.52, total = 200.0))
        )

        assertEquals(280.0, preview.foodTotal, 0.01)
        assertEquals(1, preview.foodOrders.size)
        assertEquals(1, preview.foodItems.size)
    }

    @Test
    fun previewShowsCompleteLedgerBalanceAfterPreviousFinalBill() {
        val preview = builder.build(
            booking = booking(receivable = 2_600.0),
            rooms = listOf(room()),
            bookingPayments = listOf(
                payment("advance", amount = 600.0, stay = 600.0, paymentMillis = 500L),
                payment("room", amount = 2_000.0, stay = 2_000.0, paymentMillis = 1_000L),
                payment("old_food_payment", amount = 500.0, food = 500.0, paymentMillis = 1_500L)
            ),
            foodOrders = listOf(
                foodOrder(remoteId = "old_order", status = FoodOrderStatus.BILLED_IN_FOLIO, total = 500.0, archivedAt = 2_000L),
                foodOrder(remoteId = "new_order", total = 280.0, orderMillis = 3_000L)
            ),
            foodOrderItems = listOf(
                foodItem("item_1", "Prantha", quantity = 2.0, unitPrice = 140.0, taxable = 266.67, gst = 13.33, total = 280.0, orderRemoteId = "new_order")
            )
        )

        assertEquals(2_600.0, preview.roomPaid, 0.01)
        assertEquals(780.0, preview.foodTotal, 0.01)
        assertEquals(500.0, preview.foodPaid, 0.01)
        assertEquals(280.0, preview.foodBalance, 0.01)
        assertEquals(280.0, preview.folioBalance, 0.01)
        assertEquals(2, preview.foodOrders.size)
    }

    @Test
    fun otaReceivableUsesExpectedPayoutInsteadOfGrossRoomPrice() {
        val preview = builder.build(
            booking = booking(sourceType = BookingSourceType.OTA, expectedPayout = 3_847.62),
            rooms = listOf(room()),
            bookingPayments = emptyList(),
            foodOrders = emptyList(),
            foodOrderItems = emptyList()
        )

        assertEquals(5_000.0, preview.roomCharges, 0.01)
        assertEquals(0.0, preview.roomPaid, 0.01)
        assertEquals(3_847.62, preview.roomBalance, 0.01)
        assertEquals(0.0, preview.guestCheckoutBalance, 0.01)
    }

    @Test
    fun bucketedDiscountsReduceTheMatchingBillBucketAndNetTotal() {
        val preview = builder.build(
            booking = booking(),
            rooms = listOf(room()),
            bookingPayments = emptyList(),
            accountingCharges = listOf(
                discount("room_discount", BookingPaymentCategory.STAY, 500.0),
                discount("food_discount", BookingPaymentCategory.FOOD, 40.0)
            ),
            foodOrders = listOf(foodOrder(total = 280.0)),
            foodOrderItems = listOf(
                foodItem("item_1", "Prantha", quantity = 2.0, unitPrice = 140.0, taxable = 266.67, gst = 13.33, total = 280.0)
            )
        )

        assertEquals(500.0, preview.roomDiscount, 0.01)
        assertEquals(40.0, preview.foodDiscount, 0.01)
        assertEquals(4_500.0, preview.roomBalance, 0.01)
        assertEquals(240.0, preview.foodBalance, 0.01)
        assertEquals(4_740.0, preview.totalCharges, 0.01)
        assertEquals(4_740.0, preview.folioBalance, 0.01)
    }

    private fun booking(
        sourceType: String = BookingSourceType.DIRECT,
        expectedPayout: Double = 0.0,
        receivable: Double = 5_000.0
    ): BookingEntity = BookingEntity(
        remoteId = "booking_1",
        bookingUuid = "BK-1",
        hotelRemoteId = "hotel_1",
        guestName = "Guest",
        sourceType = sourceType,
        checkInMillis = 0L,
        checkOutMillis = 86_400_000L,
        roomRemoteIds = listOf("room_1"),
        receivable = receivable,
        rate = receivable,
        grossCharges = receivable,
        expectedPayout = expectedPayout
    )

    private fun room(): RoomEntity = RoomEntity(
        remoteId = "room_1",
        hotelRemoteId = "hotel_1",
        roomName = "M101"
    )

    private fun foodOrder(
        remoteId: String = "order_1",
        status: String = FoodOrderStatus.OPEN,
        total: Double = 280.0,
        archivedAt: Long? = null,
        orderMillis: Long = 1L
    ): FoodOrderEntity = FoodOrderEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        orderNumber = "ORD-1",
        foodBillingScope = FoodBillingScope.IN_HOUSE_BOOKING,
        linkedFinalBillId = if (status == FoodOrderStatus.BILLED_IN_FOLIO) "final_bill_1" else null,
        archivedAt = archivedAt,
        roomRemoteId = "room_1",
        roomName = "M101",
        guestName = "Guest",
        orderMillis = orderMillis,
        status = status,
        subtotal = total,
        taxableAmount = 266.67,
        gstAmount = 13.33,
        totalAmount = total
    )

    private fun foodItem(
        remoteId: String,
        name: String,
        quantity: Double,
        unitPrice: Double,
        taxable: Double,
        gst: Double,
        total: Double,
        orderRemoteId: String = "order_1"
    ): FoodOrderItemEntity = FoodOrderItemEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        orderRemoteId = orderRemoteId,
        itemName = name,
        quantity = quantity,
        unitPrice = unitPrice,
        gstRatePercent = 5.0,
        lineSubtotal = taxable,
        lineGst = gst,
        lineTotal = total
    )

    private fun payment(
        remoteId: String,
        amount: Double,
        stay: Double = 0.0,
        food: Double = 0.0,
        type: String = BookingPaymentType.PAYMENT,
        category: String = BookingPaymentCategory.AUTO,
        paymentMillis: Long = amount.toLong()
    ): BookingPaymentEntity = BookingPaymentEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        paymentType = type,
        paymentCategory = category,
        amount = amount,
        allocatedStayAmount = stay,
        allocatedFoodAmount = food,
        paymentMillis = paymentMillis
    )

    private fun discount(
        remoteId: String,
        bucket: String,
        amount: Double
    ): BookingAccountingChargeEntity = BookingAccountingChargeEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        chargeType = BookingAccountingChargeType.DISCOUNT,
        accountBucket = bucket,
        amount = amount,
        description = "Discount"
    )
}
