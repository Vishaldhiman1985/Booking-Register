package com.example.bookingregister.accounting.integrity

import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
import com.example.bookingregister.data.entities.BookingAccountingChargeType
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.finalbill.domain.FinalBillPreviewBuilder
import com.example.bookingregister.folio.domain.FolioSummaryBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomAccountingIntegrityTest {

    @Test
    fun room_service_damage_discount_payment_have_same_balance_in_folio_and_final_bill() {
        val booking = BookingEntity(
            remoteId = "booking_1",
            bookingUuid = "BR-1",
            hotelRemoteId = "hotel_1",
            guestName = "Ram",
            sourceName = "Walk-in",
            sourceType = BookingSourceType.DIRECT,
            checkInMillis = 1_700_000_000_000L,
            checkOutMillis = 1_700_086_400_000L,
            roomRemoteIds = listOf("room_101"),
            grossCharges = 10_000.0,
            roomRevenue = 9_523.82,
            propertyTax = 476.18,
            rate = 10_000.0,
            receivable = 10_000.0
        )

        val rooms = listOf(
            RoomEntity(
                remoteId = "room_101",
                hotelRemoteId = "hotel_1",
                roomName = "101"
            )
        )

        val roomLines = listOf(
            BookingFinancialLineEntity(
                remoteId = "line_1",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_1",
                roomRemoteId = "room_101",
                businessDateMillis = 1_700_000_000_000L,
                grossAmount = 10_000.0,
                taxableAmount = 9_523.82,
                gstRatePercent = 5.0,
                gstAmount = 476.18,
                hsnSacCode = "996311",
                cgstRatePercent = 2.5,
                sgstRatePercent = 2.5,
                cgstAmount = 238.09,
                sgstAmount = 238.09
            )
        )

        val charges = listOf(
            BookingAccountingChargeEntity(
                remoteId = "service_1",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_1",
                chargeType = BookingAccountingChargeType.SERVICE_CHARGE,
                accountBucket = BookingPaymentCategory.SERVICE,
                amount = 500.0,
                description = "Bonfire"
            ),
            BookingAccountingChargeEntity(
                remoteId = "damage_1",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_1",
                chargeType = BookingAccountingChargeType.DAMAGE_CHARGE,
                accountBucket = BookingPaymentCategory.DAMAGE,
                amount = 1_000.0,
                description = "Broken glass"
            ),
            BookingAccountingChargeEntity(
                remoteId = "discount_1",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_1",
                chargeType = BookingAccountingChargeType.DISCOUNT,
                accountBucket = BookingPaymentCategory.STAY,
                amount = 1_000.0,
                description = "Manager discount"
            )
        )

        val payments = listOf(
            BookingPaymentEntity(
                remoteId = "pay_stay",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_1",
                paymentType = BookingPaymentType.PAYMENT,
                paymentCategory = BookingPaymentCategory.STAY,
                amount = 7_000.0
            ),
            BookingPaymentEntity(
                remoteId = "pay_service",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_1",
                paymentType = BookingPaymentType.PAYMENT,
                paymentCategory = BookingPaymentCategory.SERVICE,
                amount = 500.0
            ),
            BookingPaymentEntity(
                remoteId = "pay_damage",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_1",
                paymentType = BookingPaymentType.PAYMENT,
                paymentCategory = BookingPaymentCategory.DAMAGE,
                amount = 1_000.0
            )
        )

        val folio = FolioSummaryBuilder.build(
            booking = booking,
            payments = payments,
            foodOrders = emptyList(),
            accountingCharges = charges,
            bookingFinancialLines = roomLines
        )

        val preview = FinalBillPreviewBuilder().build(
            booking = booking,
            rooms = rooms,
            bookingPayments = payments,
            bookingFinancialLines = roomLines,
            accountingCharges = charges,
            foodOrders = emptyList(),
            foodOrderItems = emptyList()
        )

        assertEquals(10_000.0, folio.stayTotal, 0.01)
        assertEquals(1_000.0, folio.stayDiscount, 0.01)
        assertEquals(500.0, folio.serviceTotal, 0.01)
        assertEquals(1_000.0, folio.damageTotal, 0.01)

        assertEquals(7_000.0, folio.stayPaid, 0.01)
        assertEquals(500.0, folio.servicePaid, 0.01)
        assertEquals(1_000.0, folio.damagePaid, 0.01)

        assertEquals(2_000.0, folio.grandBalance, 0.01)

        assertEquals(folio.grandBalance, preview.folioBalance, 0.01)
        assertEquals(folio.grandBalance, preview.guestCheckoutBalance, 0.01)
    }
}