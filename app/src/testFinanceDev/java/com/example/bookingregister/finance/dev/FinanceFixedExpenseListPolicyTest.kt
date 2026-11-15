package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FinanceFixedExpenseListPolicyTest {
    private fun bill(id: String, amount: Long, paid: Long) = FinanceFixedExpenseStore.Bill(
        id = id, ruleId = "lease", type = "Building Lease", vendor = "Landlord",
        department = "Rooms", month = "2026-11", dueDate = "2026-11-05",
        amount = amount, paid = paid
    )

    @Test fun partialRentAppearsOnceWithTheCorrectBalances() {
        val rent = bill("fixed:lease:2026-11", 10_500_000L, 5_000_000L)
        val all = listOf(rent)
        assertEquals("Partially paid", FinanceFixedExpenseListPolicy.status(rent))
        assertEquals(1, FinanceFixedExpenseListPolicy.visible(all, "All").size)
        assertEquals(1, FinanceFixedExpenseListPolicy.visible(all, "Partially paid").size)
        assertEquals(0, FinanceFixedExpenseListPolicy.visible(all, "Paid").size)
        val totals = FinanceFixedExpenseListPolicy.summary(all)
        assertEquals(10_500_000L, totals.billedPaise)
        assertEquals(5_000_000L, totals.paidPaise)
        assertEquals(5_500_000L, totals.duePaise)
    }

    @Test fun paidAndUnpaidAreDistinctAndFiltered() {
        val list = listOf(bill("a", 10_000, 0), bill("b", 20_000, 20_000), bill("c", 30_000, 10_000))
        assertEquals(1, FinanceFixedExpenseListPolicy.visible(list, "Not paid").size)
        assertEquals(1, FinanceFixedExpenseListPolicy.visible(list, "Paid").size)
        assertEquals(1, FinanceFixedExpenseListPolicy.visible(list, "Partially paid").size)
        assertEquals(30_000L, FinanceFixedExpenseListPolicy.summary(list).duePaise)
    }

    @Test fun invalidPaymentBalancesNeverEnterTotals() {
        assertThrows(IllegalArgumentException::class.java) {
            FinanceFixedExpenseListPolicy.summary(listOf(bill("bad", 10_000, 12_000)))
        }
    }
}
