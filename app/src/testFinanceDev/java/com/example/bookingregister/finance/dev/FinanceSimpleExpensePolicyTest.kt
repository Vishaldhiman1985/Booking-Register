package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Confirm V14's new draft fields don't post or affect legacy classification. */
class FinanceSimpleExpensePolicyTest {
    @Test fun oneTimePayeeAndDepartmentPersistOnDraftAndEdit() {
        val input = FinanceDemoDraftPolicy.DraftInput(
            vendorCode = "one_time:Kasauli street shop",
            category = "Rooms & Guest Supplies",
            invoiceDate = "2026-11-12",
            amountPaise = 25_000L,
            paymentStatus = FinanceDemoDraftPolicy.PaymentStatus.PAID,
            amountPaidPaise = 25_000L,
            expenseCategoryCode = "HC04",
            expenseTypeCode = "HE0403",
            expenseTypeLabel = "Room Cleaning Supplies",
            department = "Housekeeping",
            notes = "Mop bought for housekeeping"
        )
        val draft = FinanceDemoDraftPolicy.create(input, "v14-one-time", 1000L)
        assertEquals("Housekeeping", draft.department)
        assertEquals("Mop bought for housekeeping", draft.notes)
        assertEquals("one_time:Kasauli street shop", draft.vendorCode)
        assertEquals(FinanceDemoDraftPolicy.STATUS_ACTIVE, draft.status)
        val edited = FinanceDemoDraftPolicy.edit(draft, input.copy(notes = "Corrected purchase note"), "Updated note", 2000L)
        assertEquals("Corrected purchase note", edited.notes)
        assertEquals(2L, edited.revision)
    }

    @Test fun noPayeeIsAllowedButBlankOneTimePayeeIsNot() {
        val old = FinanceDemoDraftPolicy.create("demo-rent", "v14-old", 1000L)
        val updated = FinanceDemoDraftPolicy.edit(old,
            FinanceDemoDraftPolicy.DraftInput(
                vendorCode = "no_payee", category = old.category, invoiceDate = old.invoiceDate,
                amountPaise = 20_000L, paymentStatus = FinanceDemoDraftPolicy.PaymentStatus.NOT_PAID,
                amountPaidPaise = 0L, department = "Rooms"
            ), "New payee rule", 2000L)
        assertEquals("no_payee", updated.vendorCode)
        assertEquals("Rooms", updated.department)
        var rejected = false
        try { FinanceDemoDraftPolicy.validate(updated.copy(vendorCode = "one_time:")) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun migratedLegacyDefaultsStayValid() {
        val old = FinanceDemoDraftPolicy.create("demo-laundry", "old-v14", 1000L)
        assertEquals("Unassigned", old.department)
        assertEquals("", old.notes)
        FinanceDemoDraftPolicy.validate(old)
    }
}
