package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FinanceDemoPaymentPolicyTest {
    private val bills = listOf(
        FinanceDemoPaymentPolicy.OpenBill("nov", "2026-11-03", 18_000_00),
        FinanceDemoPaymentPolicy.OpenBill("oct", "2026-10-12", 50_000_00)
    )

    @Test fun oldestFirstAllocatesOctoberBeforeNovember() {
        val result = FinanceDemoPaymentPolicy.oldestFirst(bills, 60_000_00)
        assertEquals(listOf("oct", "nov"), result.allocations.map { it.billId })
        assertEquals(listOf(50_000_00L, 10_000_00L), result.allocations.map { it.appliedPaise })
        assertEquals(0L, result.unallocatedPaise)
    }

    @Test fun partialPaymentLeavesOldBillOutstanding() {
        val result = FinanceDemoPaymentPolicy.oldestFirst(bills, 20_000_00)
        assertEquals(listOf(FinanceDemoPaymentPolicy.Allocation("oct", 20_000_00)), result.allocations)
    }

    @Test fun overpaymentRemainsUnallocated() {
        val result = FinanceDemoPaymentPolicy.oldestFirst(bills, 70_000_00)
        assertEquals(2_000_00L, result.unallocatedPaise)
    }

    @Test fun noBillsMeansAdvance() {
        assertEquals(5_000_00L, FinanceDemoPaymentPolicy.oldestFirst(emptyList(), 5_000_00).unallocatedPaise)
    }

    @Test fun invalidAmountsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            FinanceDemoPaymentPolicy.oldestFirst(bills, 0)
        }
    }
}
