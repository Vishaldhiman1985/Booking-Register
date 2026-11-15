package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.YearMonth

class FinanceFixedPolicyTest {
    @Test fun moneyIsExact() {
        assertEquals(5000000L, FinanceFixedPolicy.amount("50,000"))
        assertEquals(6000001L, FinanceFixedPolicy.amount("60000.01"))
        assertThrows(Exception::class.java) { FinanceFixedPolicy.amount("200.111") }
    }
    @Test fun previousBillIsNeverRepriced() {
        val rates = listOf(YearMonth.parse("2027-01") to 5_000_000L, YearMonth.parse("2027-04") to 6_000_000L)
        assertEquals(5_000_000L, FinanceFixedPolicy.effectiveAmount(YearMonth.parse("2027-03"), rates))
        assertEquals(6_000_000L, FinanceFixedPolicy.effectiveAmount(YearMonth.parse("2027-04"), rates))
        assertThrows(IllegalArgumentException::class.java) {
            FinanceFixedPolicy.validateRevision(YearMonth.parse("2027-03"), YearMonth.parse("2027-01"), YearMonth.parse("2027-03"))
        }
    }
    @Test fun dueDateClampsMonthEnd() {
        assertEquals("2028-02-29", FinanceFixedPolicy.due(YearMonth.parse("2028-02"), 31).toString())
        assertEquals(400L, FinanceFixedPolicy.remaining(500L,100L))
    }
}
