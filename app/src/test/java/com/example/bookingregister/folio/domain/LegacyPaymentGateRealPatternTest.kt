package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPaymentGateRealPatternTest {

    @Test
    fun coherent_unlinked_2000_adjustment_with_explicit_buckets_can_be_projected_safely() {
        val payments = listOf(
            payment("p1", 1_500.0, stay = 1_500.0),
            payment("p2", 2_000.0, stay = 2_000.0),
            adjustment("a1", 2_000.0, stay = 1_500.0, unapplied = 500.0),
            payment("p3", 1_500.0, stay = 1_500.0),
            payment("p4", 3_500.0, stay = 1_500.0, unapplied = 2_000.0),
            payment("p5", 1_500.0, unapplied = 1_500.0)
        )

        val projection = PaymentLedgerProjector.project(payments)

        assertMoney(5_000.0, projection.stayApplied)
        assertMoney(3_000.0, projection.guestCredit)
        assertMoney(8_000.0, projection.netRecordedPayment)
        assertTrue(projection.reconciles)

        val gate = PaymentLedgerProjectionGate.evaluate(payments)
        assertTrue(
            "Explicit, coherent legacy allocation should be safe for canonical projection",
            gate.isSafe
        )
    }

    @Test
    fun coherent_unlinked_500_credit_adjustment_can_be_projected_safely() {
        val payments = listOf(
            payment(
                "advance",
                1_500.0,
                stay = 1_500.0,
                type = BookingPaymentType.ADVANCE
            ),
            payment(
                "payment_3500",
                3_500.0,
                stay = 3_000.0,
                unapplied = 500.0
            ),
            adjustment(
                "adjust_500",
                500.0,
                unapplied = 500.0
            )
        )

        val projection = PaymentLedgerProjector.project(payments)

        assertMoney(4_500.0, projection.stayApplied)
        assertMoney(0.0, projection.guestCredit)
        assertMoney(4_500.0, projection.netRecordedPayment)
        assertTrue(projection.reconciles)

        val gate = PaymentLedgerProjectionGate.evaluate(payments)
        assertTrue(
            "Explicit credit-only legacy adjustment should be safe",
            gate.isSafe
        )
    }

    @Test
    fun subham_style_old_13000_plus_new_linked_corrections_must_remain_blocked() {
        val genuine = payment(
            "genuine_10500",
            10_500.0,
            stay = 10_500.0,
            category = BookingPaymentCategory.STAY
        )

        val wrong3000 = payment(
            "wrong_3000",
            3_000.0,
            unapplied = 3_000.0,
            category = BookingPaymentCategory.STAY
        )

        val old13000 = adjustment(
            "old_13000",
            13_000.0,
            unapplied = 13_000.0,
            category = BookingPaymentCategory.STAY
        )

        val wrong2500 = payment("wrong_2500", 2_500.0, unapplied = 2_500.0)
        val wrong2000 = payment("wrong_2000", 2_000.0, unapplied = 2_000.0)
        val wrong5500 = payment("wrong_5500", 5_500.0, unapplied = 5_500.0)

        val corrections = listOf(
            linkedAdjustment("c3000", wrong3000, 3_000.0),
            linkedAdjustment("c5500", wrong5500, 5_500.0),
            linkedAdjustment("c2000", wrong2000, 2_000.0),
            linkedAdjustment("c2500", wrong2500, 2_500.0)
        )

        val payments = listOf(
            genuine,
            wrong3000,
            old13000,
            wrong2500,
            wrong2000,
            wrong5500
        ) + corrections

        val projection = PaymentLedgerProjector.project(payments)

        assertMoney(10_500.0, projection.stayApplied)
        assertMoney(-13_000.0, projection.guestCredit)
        assertMoney(-2_500.0, projection.netRecordedPayment)

        val gate = PaymentLedgerProjectionGate.evaluate(payments)
        assertFalse(
            "Over-reversed legacy history must require review",
            gate.isSafe
        )
    }

    @Test
    fun duplicate_unlinked_11000_adjustments_must_remain_blocked() {
        val payments = listOf(
            payment("payment_11000", 11_000.0, stay = 11_000.0),
            payment(
                "payment_2000",
                2_000.0,
                unapplied = 2_000.0,
                category = BookingPaymentCategory.STAY
            ),
            adjustment(
                "adjust_11000_a",
                11_000.0,
                unapplied = 11_000.0,
                category = BookingPaymentCategory.STAY
            ),
            adjustment(
                "adjust_11000_b",
                11_000.0,
                unapplied = 11_000.0,
                category = BookingPaymentCategory.STAY
            )
        )

        val projection = PaymentLedgerProjector.project(payments)

        assertMoney(11_000.0, projection.stayApplied)
        assertMoney(-20_000.0, projection.guestCredit)
        assertMoney(-9_000.0, projection.netRecordedPayment)

        val gate = PaymentLedgerProjectionGate.evaluate(payments)
        assertFalse(
            "Duplicate over-reversing legacy adjustments must require review",
            gate.isSafe
        )
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

    private fun linkedAdjustment(
        id: String,
        original: BookingPaymentEntity,
        amount: Double
    ): BookingPaymentEntity = BookingPaymentEntity(
        remoteId = id,
        hotelRemoteId = original.hotelRemoteId,
        bookingRemoteId = original.bookingRemoteId,
        originalPaymentRemoteId = original.remoteId,
        paymentType = BookingPaymentType.ADJUSTMENT,
        paymentCategory = original.paymentCategory,
        amount = amount,
        allocatedStayAmount = 0.0,
        allocatedFoodAmount = 0.0,
        allocatedServiceAmount = 0.0,
        allocatedDamageAmount = 0.0,
        unappliedAmount = amount,
        paymentMillis = 3L,
        updatedAt = 3L
    )

    private fun assertMoney(expected: Double, actual: Double) {
        org.junit.Assert.assertEquals(expected, actual, 0.001)
    }
}
