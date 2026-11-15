package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinanceUnifiedStatementPolicyTest {
    private val today = LocalDate.parse("2026-11-15")
    private val paidRent = FinanceUnifiedStatementPolicy.Entry("fixed:1:2026-11", "FIXED", "2026-11-05", "Building Rent", "Landlord", "Rooms", 10500000L, 5000000L)
    private val futureRent = FinanceUnifiedStatementPolicy.Entry("fixed:1:2026-12", "FIXED", "2026-12-05", "Building Rent", "Landlord", "Rooms", 10500000L, 0)
    private val groceries = FinanceUnifiedStatementPolicy.Entry("draft:a", "MANUAL", "2026-11-15", "Food Ingredients", "Supplier", "Kitchen", 520000L, 150000L)

    @Test fun dueDateControlsFixedVisibilityAndDoesNotHideManual() {
        val visible = FinanceUnifiedStatementPolicy.statement(listOf(paidRent,futureRent,groceries), today)
        assertEquals(2, visible.size)
        assertEquals(listOf("draft:a", "fixed:1:2026-11"), visible.map { it.id })
        assertEquals(2, FinanceUnifiedStatementPolicy.due(listOf(paidRent,futureRent,groceries),today).size)
    }
    @Test fun partialPaymentAndTotals() {
        assertEquals("Partially paid", paidRent.status)
        assertEquals(5500000L, paidRent.balancePaise)
        val t=FinanceUnifiedStatementPolicy.totals(listOf(paidRent, groceries))
        assertEquals(11020000L, t.expenses)
        assertEquals(5150000L, t.paid)
        assertEquals(5870000L, t.due)
    }
    @Test fun filtersAndFullyPaidBills() {
        assertEquals(1, FinanceUnifiedStatementPolicy.statement(listOf(paidRent, groceries.copy(paidPaise=groceries.amountPaise)),today,"Paid").size)
        assertTrue(FinanceUnifiedStatementPolicy.statement(listOf(paidRent),today,"Not paid").isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun preventOverpayment() {
        groceries.copy(paidPaise=groceries.amountPaise+1L)
    }
}
