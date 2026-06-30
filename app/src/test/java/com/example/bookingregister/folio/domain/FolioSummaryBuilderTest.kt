package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
import com.example.bookingregister.data.entities.BookingAccountingChargeType
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.FoodBillingScope
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class FolioSummaryBuilderTest {
    private val miniFolioBuilder = MiniFolioBuilder()

    @Test
    fun keepsStayAndFoodTotalsSeparate() {
        val booking = booking(receivable = 5_000.0)
        val foodOrders = listOf(foodOrder(total = 2_000.0))
        val payments = listOf(
            payment(amount = 6_000.0, stay = 5_000.0, food = 1_000.0)
        )

        val summary = FolioSummaryBuilder.build(booking, payments, foodOrders)

        assertEquals(5_000.0, summary.stayTotal, 0.01)
        assertEquals(2_000.0, summary.foodTotal, 0.01)
        assertEquals(5_000.0, summary.stayPaid, 0.01)
        assertEquals(1_000.0, summary.foodPaid, 0.01)
        assertEquals(0.0, summary.stayBalance, 0.01)
        assertEquals(1_000.0, summary.foodBalance, 0.01)
        assertEquals(1_000.0, summary.grandBalance, 0.01)
    }

    @Test
    fun legacyFoodCategoryPaymentDoesNotInflateRoomPaid() {
        val booking = booking(receivable = 5_000.0)
        val foodOrders = listOf(foodOrder(total = 280.0))
        val payments = listOf(
            payment(
                amount = 280.0,
                stay = 0.0,
                food = 0.0,
                category = BookingPaymentCategory.FOOD
            )
        )

        val summary = FolioSummaryBuilder.build(booking, payments, foodOrders)

        assertEquals(0.0, summary.stayPaid, 0.01)
        assertEquals(280.0, summary.foodPaid, 0.01)
        assertEquals(5_000.0, summary.stayBalance, 0.01)
        assertEquals(0.0, summary.foodBalance, 0.01)
    }

    @Test
    fun billedFoodAndNewFoodBothRemainInCompleteLedger() {
        val booking = booking(receivable = 2_600.0)
        val foodOrders = listOf(
            foodOrder(remoteId = "old_food", total = 500.0, status = FoodOrderStatus.BILLED_IN_FOLIO, archivedAt = 2_000L),
            foodOrder(remoteId = "new_food", total = 280.0, orderMillis = 3_000L)
        )
        val payments = listOf(
            payment(remoteId = "advance", amount = 600.0, stay = 600.0, paymentMillis = 500L),
            payment(remoteId = "room", amount = 2_000.0, stay = 2_000.0, paymentMillis = 1_000L),
            payment(remoteId = "old_food_payment", amount = 500.0, food = 500.0, paymentMillis = 1_500L)
        )

        val summary = FolioSummaryBuilder.build(booking, payments, foodOrders)

        assertEquals(2_600.0, summary.stayPaid, 0.01)
        assertEquals(780.0, summary.foodTotal, 0.01)
        assertEquals(500.0, summary.foodPaid, 0.01)
        assertEquals(280.0, summary.foodBalance, 0.01)
        assertEquals(280.0, summary.grandBalance, 0.01)
    }

    @Test
    fun cancelledFoodDoesNotAffectSummary() {
        val booking = booking(receivable = 2_600.0)
        val foodOrders = listOf(
            foodOrder(remoteId = "active_food", total = 80.0),
            foodOrder(remoteId = "cancelled_food", total = 500.0, status = FoodOrderStatus.CANCELLED)
        )

        val summary = FolioSummaryBuilder.build(booking, emptyList(), foodOrders)

        assertEquals(80.0, summary.foodTotal, 0.01)
        assertEquals(2_680.0, summary.grandTotal, 0.01)
        assertEquals(2_680.0, summary.grandBalance, 0.01)
    }

    @Test
    fun summaryTotalsComeFromSameLedgerLinesAsMiniFolio() {
        val booking = booking(receivable = 2_600.0)
        val foodOrders = listOf(
            foodOrder(remoteId = "billed_food", total = 500.0, status = FoodOrderStatus.BILLED_IN_FOLIO),
            foodOrder(remoteId = "new_food", total = 80.0)
        )
        val payments = listOf(
            payment(remoteId = "advance", amount = 600.0, stay = 600.0),
            payment(remoteId = "room", amount = 2_000.0, stay = 2_000.0),
            payment(remoteId = "food", amount = 500.0, food = 500.0)
        )

        val summary = FolioSummaryBuilder.build(booking, payments, foodOrders)
        val folio = requireNotNull(
            miniFolioBuilder.buildForBooking(
                booking = booking,
                activeRoomIds = setOf("room_1"),
                bookingPayments = payments,
                foodOrders = foodOrders
            )
        )

        assertEquals(folio.totalCharges, summary.grandTotal, 0.01)
        assertEquals(folio.totalPayments, summary.totalPaid, 0.01)
        assertEquals(folio.balance, summary.grandBalance, 0.01)
    }

    @Test
    fun bucketDiscountsReduceBalancesAndPaymentAllocationBuckets() {
        val booking = booking(receivable = 5_000.0)
        val foodOrders = listOf(foodOrder(total = 1_000.0))
        val charges = listOf(
            accountingCharge(
                remoteId = "room_discount",
                type = BookingAccountingChargeType.DISCOUNT,
                amount = 500.0,
                bucket = BookingPaymentCategory.STAY
            ),
            accountingCharge(
                remoteId = "food_discount",
                type = BookingAccountingChargeType.DISCOUNT,
                amount = 100.0,
                bucket = BookingPaymentCategory.FOOD
            ),
            accountingCharge(
                remoteId = "service",
                type = BookingAccountingChargeType.SERVICE_CHARGE,
                amount = 800.0,
                bucket = BookingPaymentCategory.SERVICE
            ),
            accountingCharge(
                remoteId = "service_discount",
                type = BookingAccountingChargeType.DISCOUNT,
                amount = 80.0,
                bucket = BookingPaymentCategory.SERVICE
            )
        )

        val summary = FolioSummaryBuilder.build(
            booking = booking,
            payments = emptyList(),
            foodOrders = foodOrders,
            accountingCharges = charges
        )

        assertEquals(4_500.0, summary.stayBalance, 0.01)
        assertEquals(900.0, summary.foodBalance, 0.01)
        assertEquals(720.0, summary.serviceBalance, 0.01)
        assertEquals(4_500.0, summary.chargeBuckets.stay, 0.01)
        assertEquals(900.0, summary.chargeBuckets.food, 0.01)
        assertEquals(720.0, summary.chargeBuckets.service, 0.01)
        assertEquals(6_120.0, summary.grandBalance, 0.01)
    }

    @Test
    fun ledgerShowsOneGuestPaymentWhileAllocationStaysInternal() {
        val booking = booking(receivable = 5_000.0)
        val payment = payment(
            amount = 1_000.0,
            stay = 600.0,
            food = 300.0,
            service = 100.0
        )

        val folio = requireNotNull(
            miniFolioBuilder.buildForBooking(
                booking = booking,
                activeRoomIds = setOf("room_1"),
                bookingPayments = listOf(payment)
            )
        )
        val paymentLines = folio.lines.filter { it.kind == MiniFolioLineKind.PAYMENT }

        assertEquals(1, paymentLines.size)
        assertEquals(1_000.0, paymentLines.single().amount, 0.01)
        assertEquals("Payment received", paymentLines.single().description)
        assertEquals(1_000.0, folio.totalPayments, 0.01)
    }

    private fun booking(receivable: Double): BookingEntity = BookingEntity(
        remoteId = "booking_1",
        bookingUuid = "BK-1",
        hotelRemoteId = "hotel_1",
        guestName = "Guest",
        checkInMillis = 0L,
        checkOutMillis = 86_400_000L,
        roomRemoteIds = listOf("room_1"),
        receivable = receivable,
        rate = receivable
    )

    private fun foodOrder(
        remoteId: String = "food_1",
        total: Double,
        status: String = FoodOrderStatus.OPEN,
        archivedAt: Long? = null,
        orderMillis: Long = 1_000L
    ): FoodOrderEntity = FoodOrderEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        linkedFinalBillId = if (status == FoodOrderStatus.BILLED_IN_FOLIO) "final_bill_1" else null,
        archivedAt = archivedAt,
        guestName = "Guest",
        foodBillingScope = FoodBillingScope.IN_HOUSE_BOOKING,
        status = status,
        orderMillis = orderMillis,
        totalAmount = total
    )

    private fun payment(
        remoteId: String = "payment_1",
        amount: Double,
        stay: Double = 0.0,
        food: Double = 0.0,
        service: Double = 0.0,
        category: String = BookingPaymentCategory.AUTO,
        paymentMillis: Long = 1L
    ): BookingPaymentEntity = BookingPaymentEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        paymentCategory = category,
        amount = amount,
        allocatedStayAmount = stay,
        allocatedFoodAmount = food,
        allocatedServiceAmount = service,
        paymentMillis = paymentMillis
    )

    private fun accountingCharge(
        remoteId: String,
        type: String,
        amount: Double,
        bucket: String
    ): BookingAccountingChargeEntity = BookingAccountingChargeEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        chargeType = type,
        accountBucket = bucket,
        amount = amount,
        description = remoteId,
        chargeMillis = 1_000L
    )
}


