package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceActivityPolicyTest {
    @Test fun rentIsNotExpensedTwice() {
        val sum=FinanceActivityPolicy.totals(listOf(11000000L to 5000000L,11000000L to 0L))
        assertEquals(22000000L,sum.billed)
        assertEquals(5000000L,sum.paid)
        assertEquals(17000000L,sum.due)
    }
    @Test fun installmentSettlesOneBill() {
        assertEquals(6000000L,FinanceActivityPolicy.due(11000000,0,5000000))
        assertEquals(3000000L,FinanceActivityPolicy.due(11000000,0,8000000))
        assertEquals("Partially paid",FinanceActivityPolicy.status(11000000,3000000))
    }
    @Test fun advanceAllocationNeverCreatesCashTwice() {
        assertEquals(300000L,FinanceActivityPolicy.remainingAdvance(1000000,700000))
        assertEquals(0L,FinanceActivityPolicy.due(700000,0,700000))
    }
    @Test fun paymentsFollowActualDates() {
        val events=FinanceActivityPolicy.chronological(listOf(
            FinanceActivityPolicy.Event("pay-20","2026-11-20",2),
            FinanceActivityPolicy.Event("bill-5","2026-11-05",0),
            FinanceActivityPolicy.Event("pay-10","2026-11-10",2)
        ))
        assertEquals(listOf("bill-5","pay-10","pay-20"),events.map { it.id })
    }
    @Test(expected=IllegalArgumentException::class) fun rejectOverpayment() {
        FinanceActivityPolicy.validateSettlement(6000000,3000000)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectOverAllocatedAdvance() {
        FinanceActivityPolicy.remainingAdvance(1000000,1100000)
    }
    @Test fun paidOnlyAtZeroDue() {
        assertTrue(FinanceActivityPolicy.status(100,0)=="Paid")
    }
}
