package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FinanceLeaseTaxPolicyTest {
    private val p = FinanceLeaseTaxPolicy

    @Test fun landlordGstCreatesLandlordDueNotGovernmentDue() {
        val x = p.split(10_000_000L, p.LANDLORD_GST, 1800)
        assertEquals(1_800_000L, x.taxPaise)
        assertEquals(11_800_000L, x.landlordPaise)
        assertEquals(0L, x.governmentPaise)
    }

    @Test fun rcmCreatesGovernmentDueSeparateFromRent() {
        val x = p.split(10_000_000L, p.RCM, 1800)
        assertEquals(10_000_000L, x.landlordPaise)
        assertEquals(1_800_000L, x.governmentPaise)
        assertEquals(11_800_000L, x.totalPaise)
    }

    @Test fun noneCreatesNoTaxDue() {
        val x = p.split(10_000_000L, p.NONE, 1800)
        assertEquals(0L, x.taxPaise)
        assertEquals(10_000_000L, x.totalPaise)
    }

    @Test fun exactPaiseAndRateParser() {
        assertEquals(1800, p.rateBasisPoints("18"))
        assertEquals(1250, p.rateBasisPoints("12.5"))
        assertEquals(1L, p.taxPaise(3L, 1800))
    }

    @Test fun rejectsUnsupportedDeclaration() {
        assertThrows(IllegalArgumentException::class.java) {
            p.validateDeclaration(p.UNREGISTERED, "02AAAAA0000A1Z5", p.RCM, 1800, "2026-11")
        }
    }
}
