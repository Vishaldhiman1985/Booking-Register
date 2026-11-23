package com.example.bookingregister.finance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinancePeriodEntryPolicyTest {
    private val today = LocalDate.of(2026, 11, 23)

    @Test fun onlyPastMonthsMayBeProvisionallyLocked() {
        assertTrue(FinancePeriodEntryPolicy.isLockable("2026-10", today))
        assertFalse(FinancePeriodEntryPolicy.isLockable("2026-11", today))
        assertFalse(FinancePeriodEntryPolicy.isLockable("2026-12", today))
    }

    @Test fun dateMappingDoesNotDependOnSystemClock() {
        assertEquals("2026-02", FinancePeriodEntryPolicy.monthOf("2026-02-28"))
        assertThrows(Exception::class.java) { FinancePeriodEntryPolicy.monthOf("2026-02-30") }
    }

    @Test fun editsCannotMoveExistingLockedInvoiceIntoOpenMonth() {
        assertThrows(IllegalArgumentException::class.java) {
            FinancePeriodEntryPolicy.requireEditable("2026-10-15", "2026-11-01", setOf("2026-10"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FinancePeriodEntryPolicy.requireEditable("2026-11-01", "2026-10-15", setOf("2026-10"))
        }
        FinancePeriodEntryPolicy.requireEditable("2026-11-02", "2026-11-03", setOf("2026-10"))
    }

    @Test fun laterPaymentsOfOldBillsRemainPermitted() {
        FinancePeriodEntryPolicy.requireDateOpen("2026-11-23", false)
        assertThrows(IllegalArgumentException::class.java) {
            FinancePeriodEntryPolicy.requireDateOpen("2026-10-23", true)
        }
    }

    @Test fun auditEvidenceNeedsFingerprintAndMeaningfulReason() {
        val hash = "a".repeat(64)
        FinancePeriodEntryPolicy.requireReviewEvidence(hash, "Reviewed and reconciled")
        assertThrows(IllegalArgumentException::class.java) {
            FinancePeriodEntryPolicy.requireReviewEvidence(hash, "ok")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FinancePeriodEntryPolicy.requireReviewEvidence("invalid", "Reviewed and reconciled")
        }
    }
}
