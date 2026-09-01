package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentLedgerProjectionTest {

    @Test
    fun fully_unapplied_auto_payment_remains_guest_credit() {
        val projection = PaymentLedgerProjector.project(
            listOf(
                payment(
                    id = "room_payment",
                    amount = 3_000.0,
                    stay = 3_000.0,
                    category = BookingPaymentCategory.AUTO
                ),
                payment(
                    id = "extra_payment",
                    amount = 3_000.0,
                    unapplied = 3_000.0,
                    category = BookingPaymentCategory.AUTO
                )
            )
        )

        assertMoney(3_000.0, projection.stayApplied)
        assertMoney(3_000.0, projection.guestCredit)
        assertMoney(6_000.0, projection.netRecordedPayment)
        assertTrue(projection.reconciles)
    }

    @Test
    fun linked_correction_of_fully_unapplied_payment_reverses_guest_credit() {
        val original = payment(
            id = "extra_payment",
            amount = 3_000.0,
            unapplied = 3_000.0,
            category = BookingPaymentCategory.AUTO
        )

        val correction = adjustment(
            id = "correction_extra_payment",
            originalPaymentRemoteId = original.remoteId,
            amount = 3_000.0,
            unapplied = 3_000.0,
            category = BookingPaymentCategory.AUTO
        )

        val projection = PaymentLedgerProjector.project(
            listOf(
                payment(
                    id = "room_payment",
                    amount = 3_000.0,
                    stay = 3_000.0,
                    category = BookingPaymentCategory.AUTO
                ),
                original,
                correction
            )
        )

        assertMoney(3_000.0, projection.stayApplied)
        assertMoney(0.0, projection.guestCredit)
        assertMoney(3_000.0, projection.netRecordedPayment)
        assertTrue(projection.reconciles)
    }

    @Test
    fun correction_of_wrong_30000_reverses_room_food_and_credit_before_correct_3000() {
        val wrong = payment(
            id = "wrong_30000",
            amount = 30_000.0,
            stay = 3_000.0,
            food = 4_000.0,
            unapplied = 23_000.0,
            category = BookingPaymentCategory.AUTO
        )

        val correction = adjustment(
            id = "correction_wrong_30000",
            originalPaymentRemoteId = wrong.remoteId,
            amount = 30_000.0,
            stay = 3_000.0,
            food = 4_000.0,
            unapplied = 23_000.0,
            category = BookingPaymentCategory.AUTO
        )

        val correctPayment = payment(
            id = "correct_3000",
            amount = 3_000.0,
            stay = 3_000.0,
            category = BookingPaymentCategory.AUTO
        )

        val projection = PaymentLedgerProjector.project(
            listOf(wrong, correction, correctPayment)
        )

        assertMoney(3_000.0, projection.stayApplied)
        assertMoney(0.0, projection.foodApplied)
        assertMoney(0.0, projection.guestCredit)
        assertMoney(3_000.0, projection.netRecordedPayment)
        assertTrue(projection.reconciles)
    }

    @Test
    fun august_partial_overpayment_guard_remains_correct() {
        val advance = payment(
            id = "advance_1500",
            amount = 1_500.0,
            stay = 1_500.0,
            category = BookingPaymentCategory.STAY,
            type = BookingPaymentType.ADVANCE
        )

        val payment = payment(
            id = "payment_3500",
            amount = 3_500.0,
            stay = 3_000.0,
            unapplied = 500.0,
            category = BookingPaymentCategory.AUTO
        )

        val correction = adjustment(
            id = "correction_500",
            originalPaymentRemoteId = payment.remoteId,
            amount = 500.0,
            unapplied = 500.0,
            category = BookingPaymentCategory.AUTO
        )

        val projection = PaymentLedgerProjector.project(
            listOf(advance, payment, correction)
        )

        assertMoney(4_500.0, projection.stayApplied)
        assertMoney(0.0, projection.guestCredit)
        assertMoney(4_500.0, projection.netRecordedPayment)
        assertTrue(projection.reconciles)
    }

    @Test
    fun legacy_row_without_any_allocation_metadata_uses_compatibility_fallback_only() {
        val projection = PaymentLedgerProjector.project(
            listOf(
                payment(
                    id = "legacy_auto",
                    amount = 1_000.0,
                    category = BookingPaymentCategory.AUTO
                )
            )
        )

        assertMoney(1_000.0, projection.stayApplied)
        assertMoney(0.0, projection.guestCredit)
        assertEquals(listOf("legacy_auto"), projection.legacyFallbackPaymentIds)
        assertTrue(projection.reconciles)
    }

    private fun payment(
        id: String,
        amount: Double,
        stay: Double = 0.0,
        food: Double = 0.0,
        service: Double = 0.0,
        damage: Double = 0.0,
        unapplied: Double = 0.0,
        category: String = BookingPaymentCategory.AUTO,
        type: String = BookingPaymentType.PAYMENT
    ): BookingPaymentEntity = BookingPaymentEntity(
        remoteId = id,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        paymentType = type,
        paymentCategory = category,
        amount = amount,
        allocatedStayAmount = stay,
        allocatedFoodAmount = food,
        allocatedServiceAmount = service,
        allocatedDamageAmount = damage,
        unappliedAmount = unapplied,
        paymentMillis = 1L,
        updatedAt = 1L
    )

    private fun adjustment(
        id: String,
        originalPaymentRemoteId: String,
        amount: Double,
        stay: Double = 0.0,
        food: Double = 0.0,
        service: Double = 0.0,
        damage: Double = 0.0,
        unapplied: Double = 0.0,
        category: String = BookingPaymentCategory.AUTO
    ): BookingPaymentEntity = BookingPaymentEntity(
        remoteId = id,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        originalPaymentRemoteId = originalPaymentRemoteId,
        paymentType = BookingPaymentType.ADJUSTMENT,
        paymentCategory = category,
        amount = amount,
        allocatedStayAmount = stay,
        allocatedFoodAmount = food,
        allocatedServiceAmount = service,
        allocatedDamageAmount = damage,
        unappliedAmount = unapplied,
        paymentMillis = 2L,
        updatedAt = 2L
    )

    private fun assertMoney(expected: Double, actual: Double) {
        assertEquals(expected, actual, 0.001)
    }
}
