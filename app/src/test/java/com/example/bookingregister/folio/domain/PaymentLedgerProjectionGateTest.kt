package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentLedgerProjectionGateTest {

    @Test
    fun ordinary_modern_payment_history_is_safe() {
        val result = PaymentLedgerProjectionGate.evaluate(
            listOf(
                payment(
                    id = "room_3000",
                    amount = 3_000.0,
                    stay = 3_000.0
                ),
                payment(
                    id = "extra_1000",
                    amount = 1_000.0,
                    unapplied = 1_000.0,
                    category = BookingPaymentCategory.AUTO
                )
            )
        )

        assertTrue(result.isSafe)
        assertEquals(
            PaymentLedgerProjectionReadiness.SAFE_FOR_CANONICAL_PROJECTION,
            result.readiness
        )
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun modern_linked_correction_remains_safe() {
        val wrong = payment(
            id = "wrong_3000",
            amount = 3_000.0,
            unapplied = 3_000.0,
            category = BookingPaymentCategory.AUTO
        )
        val correction = reversal(
            id = "correction_3000",
            type = BookingPaymentType.ADJUSTMENT,
            amount = 3_000.0,
            unapplied = 3_000.0,
            originalPaymentRemoteId = wrong.remoteId
        )

        val result = PaymentLedgerProjectionGate.evaluate(
            listOf(wrong, correction)
        )

        assertTrue(result.isSafe)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun old_unlinked_adjustment_requires_legacy_review() {
        val result = PaymentLedgerProjectionGate.evaluate(
            listOf(
                payment(
                    id = "genuine_10500",
                    amount = 10_500.0,
                    stay = 10_500.0
                ),
                reversal(
                    id = "old_adjustment_13000",
                    type = BookingPaymentType.ADJUSTMENT,
                    amount = 13_000.0,
                    unapplied = 13_000.0,
                    originalPaymentRemoteId = null
                )
            )
        )

        assertFalse(result.isSafe)
        assertEquals(
            PaymentLedgerProjectionReadiness.LEGACY_REVIEW_REQUIRED,
            result.readiness
        )
        assertEquals(1, result.issues.size)
        assertEquals(
            PaymentLedgerIntegrityIssueType.LEGACY_UNLINKED_ADJUSTMENT,
            result.issues.single().issueType
        )
    }

    @Test
    fun old_unlinked_refund_requires_legacy_review() {
        val result = PaymentLedgerProjectionGate.evaluate(
            listOf(
                payment(
                    id = "payment_5000",
                    amount = 5_000.0,
                    stay = 5_000.0
                ),
                reversal(
                    id = "old_refund_1000",
                    type = BookingPaymentType.REFUND,
                    amount = 1_000.0,
                    stay = 1_000.0,
                    originalPaymentRemoteId = null
                )
            )
        )

        assertFalse(result.isSafe)
        assertEquals(
            PaymentLedgerIntegrityIssueType.LEGACY_UNLINKED_REFUND,
            result.issues.single().issueType
        )
    }

    @Test
    fun broken_original_link_requires_legacy_review() {
        val result = PaymentLedgerProjectionGate.evaluate(
            listOf(
                reversal(
                    id = "correction_missing_original",
                    type = BookingPaymentType.ADJUSTMENT,
                    amount = 2_000.0,
                    unapplied = 2_000.0,
                    originalPaymentRemoteId = "missing_payment"
                )
            )
        )

        assertFalse(result.isSafe)
        assertEquals(
            PaymentLedgerIntegrityIssueType.BROKEN_ORIGINAL_LINK,
            result.issues.single().issueType
        )
        assertEquals(
            "missing_payment",
            result.issues.single().originalPaymentRemoteId
        )
    }

    @Test
    fun subham_style_history_is_blocked_only_because_of_old_unlinked_adjustment() {
        val genuine = payment(
            id = "genuine_10500",
            amount = 10_500.0,
            stay = 10_500.0,
            category = BookingPaymentCategory.STAY
        )

        val wrong3000 = payment(
            id = "wrong_3000",
            amount = 3_000.0,
            unapplied = 3_000.0,
            category = BookingPaymentCategory.STAY
        )
        val wrong2500 = payment(
            id = "wrong_2500",
            amount = 2_500.0,
            unapplied = 2_500.0,
            category = BookingPaymentCategory.AUTO
        )
        val wrong2000 = payment(
            id = "wrong_2000",
            amount = 2_000.0,
            unapplied = 2_000.0,
            category = BookingPaymentCategory.AUTO
        )
        val wrong5500 = payment(
            id = "wrong_5500",
            amount = 5_500.0,
            unapplied = 5_500.0,
            category = BookingPaymentCategory.AUTO
        )

        val oldUnlinked = reversal(
            id = "old_unlinked_13000",
            type = BookingPaymentType.ADJUSTMENT,
            amount = 13_000.0,
            unapplied = 13_000.0,
            originalPaymentRemoteId = null,
            category = BookingPaymentCategory.STAY
        )

        val linkedCorrections = listOf(
            reversal(
                id = "correction_3000",
                type = BookingPaymentType.ADJUSTMENT,
                amount = 3_000.0,
                unapplied = 3_000.0,
                originalPaymentRemoteId = wrong3000.remoteId,
                category = wrong3000.paymentCategory
            ),
            reversal(
                id = "correction_2500",
                type = BookingPaymentType.ADJUSTMENT,
                amount = 2_500.0,
                unapplied = 2_500.0,
                originalPaymentRemoteId = wrong2500.remoteId,
                category = wrong2500.paymentCategory
            ),
            reversal(
                id = "correction_2000",
                type = BookingPaymentType.ADJUSTMENT,
                amount = 2_000.0,
                unapplied = 2_000.0,
                originalPaymentRemoteId = wrong2000.remoteId,
                category = wrong2000.paymentCategory
            ),
            reversal(
                id = "correction_5500",
                type = BookingPaymentType.ADJUSTMENT,
                amount = 5_500.0,
                unapplied = 5_500.0,
                originalPaymentRemoteId = wrong5500.remoteId,
                category = wrong5500.paymentCategory
            )
        )

        val result = PaymentLedgerProjectionGate.evaluate(
            listOf(
                genuine,
                wrong3000,
                oldUnlinked,
                wrong2500,
                wrong2000,
                wrong5500
            ) + linkedCorrections
        )

        assertFalse(result.isSafe)
        assertEquals(
            PaymentLedgerProjectionReadiness.LEGACY_REVIEW_REQUIRED,
            result.readiness
        )
        assertEquals(1, result.issues.size)
        assertEquals("old_unlinked_13000", result.issues.single().paymentRemoteId)
        assertEquals(
            PaymentLedgerIntegrityIssueType.LEGACY_UNLINKED_ADJUSTMENT,
            result.issues.single().issueType
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
        category: String = BookingPaymentCategory.AUTO
    ): BookingPaymentEntity = BookingPaymentEntity(
        remoteId = id,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        paymentType = BookingPaymentType.PAYMENT,
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

    private fun reversal(
        id: String,
        type: String,
        amount: Double,
        stay: Double = 0.0,
        food: Double = 0.0,
        service: Double = 0.0,
        damage: Double = 0.0,
        unapplied: Double = 0.0,
        originalPaymentRemoteId: String?,
        category: String = BookingPaymentCategory.AUTO
    ): BookingPaymentEntity = BookingPaymentEntity(
        remoteId = id,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        originalPaymentRemoteId = originalPaymentRemoteId,
        paymentType = type,
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
}
