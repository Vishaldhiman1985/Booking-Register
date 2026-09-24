package com.example.bookingregister.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingCreateConflictResolutionPolicyTest {

    @Test
    fun `non terminal server outcome is not applicable`() {
        val decision = BookingCreateConflictResolutionPolicy.decide(
            safeEvidence(serverOutcome = "APPLIED")
        )

        assertEquals(
            BookingCreateConflictResolutionAction.NOT_APPLICABLE,
            decision.action
        )
        assertTrue(decision.guardFailures.isEmpty())
    }

    @Test
    fun `strictly proven rejected create can auto resolve`() {
        val decision = BookingCreateConflictResolutionPolicy.decide(
            safeEvidence()
        )

        assertEquals(
            BookingCreateConflictResolutionAction.AUTO_RESOLVE,
            decision.action
        )
        assertTrue(decision.guardFailures.isEmpty())
    }

    @Test
    fun `existing booking edit can never auto resolve as a ghost`() {
        val decision = BookingCreateConflictResolutionPolicy.decide(
            safeEvidence(originalCreate = false)
        )

        assertEquals(
            BookingCreateConflictResolutionAction.MANUAL_REVIEW,
            decision.action
        )
        assertEquals(
            listOf(BookingCreateConflictGuardFailure.NOT_ORIGINAL_CREATE),
            decision.guardFailures
        )
    }

    @Test
    fun `missing authoritative blocker requires manual review`() {
        val decision = BookingCreateConflictResolutionPolicy.decide(
            safeEvidence(blockingBookingRemoteIds = listOf("", "   "))
        )

        assertEquals(
            BookingCreateConflictResolutionAction.MANUAL_REVIEW,
            decision.action
        )
        assertEquals(
            listOf(BookingCreateConflictGuardFailure.NO_AUTHORITATIVE_BLOCKER),
            decision.guardFailures
        )
    }

    @Test
    fun `any evidence of prior server acceptance requires manual review`() {
        val cases = listOf(
            safeEvidence(bookingRevision = 1L),
            safeEvidence(bookingBaseRevision = 1L),
            safeEvidence(bookingLastSyncedAt = 123L)
        )

        cases.forEach { evidence ->
            val decision = BookingCreateConflictResolutionPolicy.decide(evidence)

            assertEquals(
                BookingCreateConflictResolutionAction.MANUAL_REVIEW,
                decision.action
            )
            assertTrue(
                BookingCreateConflictGuardFailure.BOOKING_MAY_HAVE_BEEN_SERVER_ACCEPTED in
                    decision.guardFailures
            )
        }
    }

    @Test
    fun `business activity or uncertain financial history blocks auto resolution`() {
        val decision = BookingCreateConflictResolutionPolicy.decide(
            safeEvidence(
                hasLegitimatePayment = true,
                hasFoodOrders = true,
                hasAccountingCharges = true,
                hasFinalBill = true,
                allFinancialLinesLocalOnly = false,
                hasAmbiguousLaterBookingIntent = true
            )
        )

        assertEquals(
            BookingCreateConflictResolutionAction.MANUAL_REVIEW,
            decision.action
        )

        assertEquals(
            listOf(
                BookingCreateConflictGuardFailure.HAS_LEGITIMATE_PAYMENT,
                BookingCreateConflictGuardFailure.HAS_FOOD_ORDERS,
                BookingCreateConflictGuardFailure.HAS_ACCOUNTING_CHARGES,
                BookingCreateConflictGuardFailure.HAS_FINAL_BILL,
                BookingCreateConflictGuardFailure.FINANCIAL_LINES_NOT_PROVEN_LOCAL_ONLY,
                BookingCreateConflictGuardFailure.AMBIGUOUS_LATER_BOOKING_INTENT
            ),
            decision.guardFailures
        )
    }

    @Test
    fun `multiple failed guards are all retained for manual review evidence`() {
        val decision = BookingCreateConflictResolutionPolicy.decide(
            safeEvidence(
                originalCreate = false,
                blockingBookingRemoteIds = emptyList(),
                bookingRevision = 5L,
                hasLegitimatePayment = true,
                allFinancialLinesLocalOnly = false
            )
        )

        assertEquals(
            BookingCreateConflictResolutionAction.MANUAL_REVIEW,
            decision.action
        )

        assertEquals(
            listOf(
                BookingCreateConflictGuardFailure.NOT_ORIGINAL_CREATE,
                BookingCreateConflictGuardFailure.NO_AUTHORITATIVE_BLOCKER,
                BookingCreateConflictGuardFailure.BOOKING_MAY_HAVE_BEEN_SERVER_ACCEPTED,
                BookingCreateConflictGuardFailure.HAS_LEGITIMATE_PAYMENT,
                BookingCreateConflictGuardFailure.FINANCIAL_LINES_NOT_PROVEN_LOCAL_ONLY
            ),
            decision.guardFailures
        )
    }

    private fun safeEvidence(
        serverOutcome: String = BookingCreateConflictResolutionPolicy.REJECTED_ROOM_CONFLICT,
        originalCreate: Boolean = true,
        blockingBookingRemoteIds: List<String> = listOf("canonical_booking_1"),
        bookingRevision: Long = 0L,
        bookingBaseRevision: Long = 0L,
        bookingLastSyncedAt: Long? = null,
        hasLegitimatePayment: Boolean = false,
        hasFoodOrders: Boolean = false,
        hasAccountingCharges: Boolean = false,
        hasFinalBill: Boolean = false,
        allFinancialLinesLocalOnly: Boolean = true,
        hasAmbiguousLaterBookingIntent: Boolean = false
    ) = BookingCreateConflictEvidence(
        serverOutcome = serverOutcome,
        originalCreate = originalCreate,
        blockingBookingRemoteIds = blockingBookingRemoteIds,
        bookingRevision = bookingRevision,
        bookingBaseRevision = bookingBaseRevision,
        bookingLastSyncedAt = bookingLastSyncedAt,
        hasLegitimatePayment = hasLegitimatePayment,
        hasFoodOrders = hasFoodOrders,
        hasAccountingCharges = hasAccountingCharges,
        hasFinalBill = hasFinalBill,
        allFinancialLinesLocalOnly = allFinancialLinesLocalOnly,
        hasAmbiguousLaterBookingIntent = hasAmbiguousLaterBookingIntent
    )
}