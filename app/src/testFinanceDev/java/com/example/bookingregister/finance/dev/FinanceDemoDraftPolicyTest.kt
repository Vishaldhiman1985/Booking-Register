package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceDemoDraftPolicyTest {
    @Test fun createsOnlySyntheticScopedDrafts() {
        val row = FinanceDemoDraftPolicy.create("demo-rent", "test-1", 123L)
        FinanceDemoDraftPolicy.validate(row)
        assertEquals("DEMO_TENANT_ONLY", row.tenantId)
        assertEquals("LOCAL_DRAFT_NOT_POSTED", row.status)
        assertEquals(25_000_000L, row.amountPaise)
    }

    @Test fun rejectsUnknownFixture() {
        assertFails { FinanceDemoDraftPolicy.create("live-landlord", "test-2", 123L) }
    }

    @Test fun rejectsWrongPropertyOrCurrency() {
        val row = FinanceDemoDraftPolicy.create("demo-laundry", "test-3", 123L)
        assertFails { FinanceDemoDraftPolicy.validate(row.copy(propertyId = "REAL_PROPERTY")) }
        assertFails { FinanceDemoDraftPolicy.validate(row.copy(currencyCode = "USD")) }
    }

    @Test fun rejectsTamperedAmountOrPostedStatus() {
        val row = FinanceDemoDraftPolicy.create("demo-maintenance", "test-4", 123L)
        assertFails { FinanceDemoDraftPolicy.validate(row.copy(amountPaise = 1L)) }
        assertFails { FinanceDemoDraftPolicy.validate(row.copy(status = "POSTED")) }
    }

    @Test fun rendersAmountsWithoutFloatingPoint() {
        assertEquals("₹250000.00", FinanceDemoDraftPolicy.moneyText(25_000_000L))
        assertEquals("₹8750.50", FinanceDemoDraftPolicy.moneyText(875_050L))
    }

    private fun assertFails(block: () -> Unit) {
        var failed = false
        try { block() } catch (_: IllegalArgumentException) { failed = true }
        assertTrue("Expected validation failure", failed)
    }
}
