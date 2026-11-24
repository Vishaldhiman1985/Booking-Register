package com.example.bookingregister.finance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class FinanceV21TaxEvidencePolicyTest {
    private val policy = FinanceV21TaxEvidencePolicy
    private val today = LocalDate.of(2026, 11, 23)
    private fun input(kind: FinanceV21TaxEvidencePolicy.Kind = FinanceV21TaxEvidencePolicy.Kind.INPUT_GST) =
        FinanceV21TaxEvidencePolicy.Input(kind, "Hotel A", "2026-11-20", "INV-51", "Vendor B", 12500, "", "194-I")

    @Test fun identifiesPeriodAndChecksDocument() {
        assertEquals("2026-11", policy.validate(input(), today))
        assertThrows(IllegalArgumentException::class.java) {
            policy.validate(input().copy(date = "2026-11-24"), today)
        }
        assertThrows(Exception::class.java) {
            policy.validate(input().copy(date = "2026-11-31"), today)
        }
    }

    @Test fun parsesPaiseExactlyWithoutFloatingPoint() {
        assertEquals(1245L, policy.rupeesToPaise("12.45"))
        assertThrows(Exception::class.java) { policy.rupeesToPaise("12.455") }
        assertThrows(Exception::class.java) { policy.rupeesToPaise("-1") }
    }

    @Test fun tdsAndTcsRequireSection() {
        assertThrows(IllegalArgumentException::class.java) {
            policy.validate(input(FinanceV21TaxEvidencePolicy.Kind.TDS_WITHHELD).copy(section = ""), today)
        }
        policy.validate(input(FinanceV21TaxEvidencePolicy.Kind.TCS_CREDIT), today)
    }

    @Test fun creditsCannotBecomePayableSettlements() {
        for (kind in listOf(FinanceV21TaxEvidencePolicy.Kind.INPUT_GST, FinanceV21TaxEvidencePolicy.Kind.TCS_CREDIT)) {
            assertEquals(0L, policy.remaining(kind, 100L, 0L))
            assertThrows(IllegalArgumentException::class.java) { policy.requireSettlement(kind, 100L, 0L, 50L) }
        }
    }

    @Test fun preventsOverpaymentButAllowsExactSettlement() {
        val kind = FinanceV21TaxEvidencePolicy.Kind.OUTPUT_GST
        policy.requireSettlement(kind, 100L, 25L, 75L)
        assertEquals(75L, policy.remaining(kind, 100L, 25L))
        assertThrows(IllegalArgumentException::class.java) { policy.requireSettlement(kind, 100L, 25L, 76L) }
        assertThrows(IllegalArgumentException::class.java) { policy.requireSettlement(kind, 100L, 25L, 0L) }
    }

    @Test fun validatesGstinAndCorrectionReasons() {
        policy.validate(input().copy(gstin = "02ABCDE1234F1Z5"), today)
        assertThrows(IllegalArgumentException::class.java) { policy.validate(input().copy(gstin = "BADGSTIN"), today) }
        policy.requireCorrectionReason("Wrong invoice")
        assertThrows(IllegalArgumentException::class.java) { policy.requireCorrectionReason("wrong") }
    }
}
