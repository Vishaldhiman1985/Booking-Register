package com.example.bookingregister.finalbill.domain

import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.folio.domain.MiniFolioLine
import com.example.bookingregister.folio.domain.MiniFolioLineKind
import com.example.bookingregister.folio.domain.MiniFolioLineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalBillTextFormatterTest {
    @Test
    fun balanceLedgerSeparatesRoomAndFood() {
        val text = FinalBillTextFormatter.formatBalanceLedger(preview())

        assertTrue(text.contains("Room Deal"))
        assertTrue(text.contains("Room Final Price: Rs 5000"))
        assertTrue(text.contains("Food Orders"))
        assertTrue(text.contains("ORD-1: Rs 280"))
        assertTrue(text.contains("Food Paid: Rs 280"))
        assertTrue(text.contains("Balance: Rs 0"))
    }

    @Test
    fun finalSettlementIncludesLedgerSigns() {
        val text = FinalBillTextFormatter.formatFinalSettlement(preview())

        assertTrue(text.contains("Final Settlement"))
        assertTrue(text.contains("+ Rs 5000  Room charge"))
        assertTrue(text.contains("- Rs 5280  Payment received"))
    }

    @Test
    fun otaBalanceSummarySeparatesGuestBalanceFromOtaReceivable() {
        val otaPreview = preview().copy(
            sourceName = "Booking.com",
            sourceType = BookingSourceType.OTA,
            roomPaid = 0.0,
            roomBalance = 5_000.0,
            foodPaid = 0.0,
            foodBalance = 280.0,
            totalPaid = 0.0,
            folioBalance = 5_280.0
        )
        val text = FinalBillTextFormatter.formatBalanceSummary(otaPreview)

        assertTrue(text.contains("OTA Room Settlement"))
        assertTrue(text.contains("OTA Receivable: Rs 5000"))
        assertTrue(text.contains("Guest Checkout Balance: Rs 280"))
        assertEquals(280.0, otaPreview.guestCheckoutBalance, 0.001)
    }

    @Test
    fun balanceSummaryShowsServiceChargesBesideLedger() {
        val servicePreview = preview().copy(
            roomCharges = 5_200.0,
            roomPaid = 1_200.0,
            roomBalance = 4_000.0,
            foodOrders = emptyList(),
            foodTotal = 0.0,
            foodPaid = 0.0,
            foodBalance = 0.0,
            extraTotal = 1_200.0,
            serviceBalance = 1_200.0,
            totalCharges = 6_400.0,
            totalPaid = 1_200.0,
            folioBalance = 5_200.0,
            folioLines = listOf(
                MiniFolioLine(
                    bookingRemoteId = "booking_1",
                    businessDateMillis = 1L,
                    type = MiniFolioLineType.ROOM_CHARGE,
                    kind = MiniFolioLineKind.CHARGE,
                    amount = 5_200.0,
                    description = "Room charge",
                    accountBucket = BookingPaymentCategory.STAY
                ),
                MiniFolioLine(
                    bookingRemoteId = "booking_1",
                    businessDateMillis = 1L,
                    type = MiniFolioLineType.SERVICE_CHARGE,
                    kind = MiniFolioLineKind.CHARGE,
                    amount = 1_200.0,
                    description = "Bonfire",
                    accountBucket = BookingPaymentCategory.SERVICE
                ),
                MiniFolioLine(
                    bookingRemoteId = "booking_1",
                    businessDateMillis = 1L,
                    type = MiniFolioLineType.PAYMENT_RECEIVED,
                    kind = MiniFolioLineKind.PAYMENT,
                    amount = 1_200.0,
                    description = "Advance received",
                    accountBucket = BookingPaymentCategory.STAY
                )
            )
        )

        val text = FinalBillTextFormatter.formatBalanceSummary(servicePreview)

        assertTrue(text.contains("Other Services"))
        assertTrue(text.contains("Bonfire: Rs 1200"))
        assertTrue(text.contains("Service Total: Rs 1200"))
        assertTrue(text.contains("Service Balance: Rs 1200"))
        assertTrue(text.contains("Grand Total: Rs 6400"))
        assertTrue(text.contains("Balance: Rs 5200"))
    }

    private fun preview(): FinalBillPreview = FinalBillPreview(
        bookingRemoteId = "booking_1",
        guestName = "Guest",
        roomNames = "101",
        sourceName = "Walk-in",
        roomCharges = 5_000.0,
        roomPaid = 5_000.0,
        roomBalance = 0.0,
        foodOrders = listOf(
            FinalBillFoodOrderPreview(
                orderRemoteId = "order_1",
                orderNumber = "ORD-1",
                roomName = "101",
                orderMillis = 1L,
                totalAmount = 280.0,
                itemCount = 2
            )
        ),
        foodItems = emptyList(),
        gstSummaries = emptyList(),
        foodSubtotal = 266.67,
        foodGst = 13.33,
        foodTotal = 280.0,
        foodPaid = 280.0,
        foodBalance = 0.0,
        totalCharges = 5_280.0,
        totalPaid = 5_280.0,
        folioBalance = 0.0,
        folioLines = listOf(
            MiniFolioLine(
                bookingRemoteId = "booking_1",
                businessDateMillis = 1L,
                type = MiniFolioLineType.ROOM_CHARGE,
                kind = MiniFolioLineKind.CHARGE,
                amount = 5_000.0,
                description = "Room charge"
            ),
            MiniFolioLine(
                bookingRemoteId = "booking_1",
                businessDateMillis = 1L,
                type = MiniFolioLineType.PAYMENT_RECEIVED,
                kind = MiniFolioLineKind.PAYMENT,
                amount = 5_280.0,
                description = "Payment received"
            )
        )
    )
}
