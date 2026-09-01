package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentLedgerIntegrityAnalyzerTest {

    @Test
    fun old_unlinked_adjustment_is_flagged_as_legacy_ambiguity() {
        val payments = listOf(
            payment(
                id = "genuine_10500",
                amount = 10_500.0,
                stay = 10_500.0
            ),
            adjustment(
                id = "old_unlinked_13000",
                amount = 13_000.0,
                unapplied = 13_000.0,
                originalPaymentRemoteId = null
            )
        )

        val issues = PaymentLedgerIntegrityAnalyzer.analyze(payments)

        assertEquals(1, issues.size)
        assertEquals(
            PaymentLedgerIntegrityIssueType.LEGACY_UNLINKED_ADJUSTMENT,
            issues.single().issueType
        )
        assertEquals("old_unlinked_13000", issues.single().paymentRemoteId)
    }

    @Test
    fun modern_linked_correction_is_not_flagged() {
        val original = payment(
            id = "wrong_3000",
            amount = 3_000.0,
            unapplied = 3_000.0
        )
        val correction = adjustment(
            id = "correction_3000",
            amount = 3_000.0,
            unapplied = 3_000.0,
            originalPaymentRemoteId = original.remoteId
        )

        val issues = PaymentLedgerIntegrityAnalyzer.analyze(
            listOf(original, correction)
        )

        assertTrue(issues.isEmpty())
    }

    @Test
    fun reversal_pointing_to_missing_original_is_flagged_as_broken_link() {
        val correction = adjustment(
            id = "correction_missing_original",
            amount = 2_000.0,
            unapplied = 2_000.0,
            originalPaymentRemoteId = "missing_payment"
        )

        val issues = PaymentLedgerIntegrityAnalyzer.analyze(
            listOf(correction)
        )

        assertEquals(1, issues.size)
        assertEquals(
            PaymentLedgerIntegrityIssueType.BROKEN_ORIGINAL_LINK,
            issues.single().issueType
        )
        assertEquals("missing_payment", issues.single().originalPaymentRemoteId)
    }

    @Test
    fun subham_style_history_flags_only_the_old_unlinked_13000_adjustment() {
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

        val oldUnlinked = adjustment(
            id = "old_unlinked_13000",
            amount = 13_000.0,
            unapplied = 13_000.0,
            originalPaymentRemoteId = null,
            category = BookingPaymentCategory.STAY
        )

        val modernCorrections = listOf(
            adjustment(
                id = "correction_3000",
                amount = 3_000.0,
                unapplied = 3_000.0,
                originalPaymentRemoteId = wrong3000.remoteId,
                category = wrong3000.paymentCategory
            ),
            adjustment(
                id = "correction_2500",
                amount = 2_500.0,
                unapplied = 2_500.0,
                originalPaymentRemoteId = wrong2500.remoteId,
                category = wrong2500.paymentCategory
            ),
            adjustment(
                id = "correction_2000",
                amount = 2_000.0,
                unapplied = 2_000.0,
                originalPaymentRemoteId = wrong2000.remoteId,
                category = wrong2000.paymentCategory
            ),
            adjustment(
                id = "correction_5500",
                amount = 5_500.0,
                unapplied = 5_500.0,
                originalPaymentRemoteId = wrong5500.remoteId,
                category = wrong5500.paymentCategory
            )
        )

        val issues = PaymentLedgerIntegrityAnalyzer.analyze(
            listOf(
                genuine,
                wrong3000,
                oldUnlinked,
                wrong2500,
                wrong2000,
                wrong5500
            ) + modernCorrections
        )

        assertEquals(1, issues.size)
        assertEquals(
            PaymentLedgerIntegrityIssueType.LEGACY_UNLINKED_ADJUSTMENT,
            issues.single().issueType
        )
        assertEquals("old_unlinked_13000", issues.single().paymentRemoteId)
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

    private fun adjustment(
        id: String,
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
}
