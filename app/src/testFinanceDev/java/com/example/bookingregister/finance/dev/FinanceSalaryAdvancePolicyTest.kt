package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class FinanceSalaryAdvancePolicyTest {
    private val nov=YearMonth.of(2026,11)
    @Test fun novemberAdvanceAutomaticallyReducesNovemberSalary() {
        val a=FinanceSalaryAdvancePolicy.Advance("a1","2026-11-10",500_000L,500_000L)
        assertEquals(1_350_000L,
            FinanceSalaryAdvancePolicy.projectedBalance(nov,1_850_000L,listOf(a)))
    }
    @Test fun threeAdvancesAreAllocatedOldestFirstAndNeverOverpay() {
        val a=listOf(
            FinanceSalaryAdvancePolicy.Advance("a","2026-11-10",1_000_000L,1_000_000L),
            FinanceSalaryAdvancePolicy.Advance("b","2026-11-16",900_000L,900_000L)
        )
        assertEquals(listOf(1_000_000L,800_000L),FinanceSalaryAdvancePolicy.plan(nov,1_800_000L,a).map { it.amount })
        assertEquals(0L,FinanceSalaryAdvancePolicy.projectedBalance(nov,1_800_000L,a))
    }
    @Test fun januaryAdvanceNotMovedToNovemberUnlessPartiallyAllocatedEarlier() {
        val older=FinanceSalaryAdvancePolicy.Advance("old","2026-10-10",700_000L,700_000L)
        assertEquals(emptyList<FinanceSalaryAdvancePolicy.Allocation>(), FinanceSalaryAdvancePolicy.plan(nov,1_800_000L,listOf(older)))
        val remainder=older.copy(available=200_000L)
        assertEquals(200_000L,FinanceSalaryAdvancePolicy.plan(nov,1_800_000L,listOf(remainder)).single().amount)
    }
    @Test fun laterMonthsCannotSettleEarlierMonth() {
        val later=FinanceSalaryAdvancePolicy.Advance("dec","2026-12-01",500_000L,500_000L)
        assertEquals(emptyList<FinanceSalaryAdvancePolicy.Allocation>(),FinanceSalaryAdvancePolicy.plan(nov,1_800_000L,listOf(later)))
    }
    @Test fun rerunningAfterAllocationIsIdempotent() {
        val a=FinanceSalaryAdvancePolicy.Advance("a","2026-11-10",500_000L,0L)
        assertEquals(emptyList<FinanceSalaryAdvancePolicy.Allocation>(),FinanceSalaryAdvancePolicy.plan(nov,1_300_000L,listOf(a)))
    }
}
