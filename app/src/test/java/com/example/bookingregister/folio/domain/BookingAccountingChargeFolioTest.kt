package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
import com.example.bookingregister.data.entities.BookingAccountingChargeType
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.bookingregister.authoritativeRoomLines

class BookingAccountingChargeFolioTest {
    private val builder = MiniFolioBuilder()

    @Test
    fun serviceDamageAndDiscountRemainVisibleInFolio() {
        val folio = requireNotNull(
            builder.buildForBooking(
                booking = booking(),
                activeRoomIds = setOf("room_1"),
                accountingCharges = listOf(
                    charge("service_1", BookingAccountingChargeType.SERVICE_CHARGE, 800.0, "Bonfire"),
                    charge("damage_1", BookingAccountingChargeType.DAMAGE_CHARGE, 1_000.0, "Broken glass"),
                    charge("discount_1", BookingAccountingChargeType.DISCOUNT, 500.0, "Discount - Approved by Manager")
                ),
                bookingFinancialLines = authoritativeRoomLines(booking())
            )
        )

        assertTrue(folio.lines.any { it.type == MiniFolioLineType.ROOM_CHARGE && it.amount == 3_000.0 })
        assertTrue(folio.lines.any { it.type == MiniFolioLineType.SERVICE_CHARGE && it.amount == 800.0 })
        assertTrue(folio.lines.any { it.type == MiniFolioLineType.DAMAGE_CHARGE && it.amount == 1_000.0 })
        assertTrue(folio.lines.any { it.type == MiniFolioLineType.DISCOUNT && it.amount == 500.0 })
        assertEquals(4_800.0, folio.totalCharges, 0.01)
        assertEquals(500.0, folio.totalDiscounts, 0.01)
        assertEquals(4_300.0, folio.balance, 0.01)
    }

    @Test
    fun summarySeparatesServiceDamageDiscountAndGuestCredit() {
        val summary = FolioSummaryBuilder.build(
            booking = booking(),
            payments = listOf(
                payment("room_payment", 3_000.0, stay = 3_000.0),
                payment("service_payment", 800.0, service = 800.0),
                payment("damage_payment", 1_200.0, category = BookingPaymentCategory.DAMAGE)
            ),
            foodOrders = emptyList(),
            accountingCharges = listOf(
                charge("service_1", BookingAccountingChargeType.SERVICE_CHARGE, 800.0, "Bonfire"),
                charge("damage_1", BookingAccountingChargeType.DAMAGE_CHARGE, 1_000.0, "Broken glass"),
                charge("discount_1", BookingAccountingChargeType.DISCOUNT, 500.0, "Discount - Approved by Manager")
            ),
            bookingFinancialLines = authoritativeRoomLines(booking())
        )

        assertEquals(3_000.0, summary.stayTotal, 0.01)
        assertEquals(800.0, summary.serviceTotal, 0.01)
        assertEquals(1_000.0, summary.damageTotal, 0.01)
        assertEquals(500.0, summary.discountTotal, 0.01)
        assertEquals(4_800.0, summary.grandCharges, 0.01)
        assertEquals(4_300.0, summary.grandTotal, 0.01)
        assertEquals(3_000.0, summary.stayPaid, 0.01)
        assertEquals(800.0, summary.servicePaid, 0.01)
        assertEquals(1_200.0, summary.damagePaid, 0.01)
        assertEquals(700.0, summary.guestCredit, 0.01)
    }

    private fun booking(): BookingEntity = BookingEntity(
        remoteId = "booking_1",
        bookingUuid = "BK-1",
        hotelRemoteId = "hotel_1",
        guestName = "Guest",
        checkInMillis = 0L,
        checkOutMillis = 86_400_000L,
        roomRemoteIds = listOf("room_1"),
        grossCharges = 3_000.0,
        receivable = 3_000.0,
        rate = 3_000.0
    )

    private fun charge(
        remoteId: String,
        type: String,
        amount: Double,
        description: String
    ): BookingAccountingChargeEntity = BookingAccountingChargeEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        chargeType = type,
        amount = amount,
        description = description,
        chargeMillis = amount.toLong()
    )

    private fun payment(
        remoteId: String,
        amount: Double,
        stay: Double = 0.0,
        service: Double = 0.0,
        category: String = BookingPaymentCategory.AUTO
    ): BookingPaymentEntity = BookingPaymentEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        paymentCategory = category,
        amount = amount,
        allocatedStayAmount = stay,
        allocatedServiceAmount = service,
        paymentMillis = amount.toLong()
    )
}
