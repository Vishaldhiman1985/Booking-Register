package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinanceRecurringPolicyTest {
    private fun rule(first: String = "2026-01-31", end: String? = null) =
        FinanceRecurringPolicy.newRule(
            categoryCode = "HC05",
            categoryLabel = "Property Rent & Occupancy",
            typeCode = "HE0501",
            typeLabel = "Hotel Building Rent",
            amountPaise = 50_000_00L,
            firstDueDate = first,
            endDate = end,
            nowMillis = 1L,
            id = "rent-rule"
        )

    @Test fun monthlyDueDayUsesLastDayWhenMonthIsShorter() {
        val bills = FinanceRecurringPolicy.billsThrough(rule(), LocalDate.parse("2026-03-31"), 2L)
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31"), bills.map { it.dueDate })
        assertTrue(bills.all { it.amountPaise == 50_000_00L })
    }

    @Test fun noBillExistsBeforeFirstDueDate() {
        assertTrue(FinanceRecurringPolicy.billsThrough(rule(), LocalDate.parse("2026-01-30"), 2L).isEmpty())
    }

    @Test fun endingDateStopsFutureBills() {
        val bills = FinanceRecurringPolicy.billsThrough(
            rule(first = "2026-01-02", end = "2026-03-02"),
            LocalDate.parse("2026-06-30"),
            2L
        )
        assertEquals(listOf("2026-01-02", "2026-02-02", "2026-03-02"), bills.map { it.dueDate })
    }

    @Test fun displayStatusSeparatesDueOverduePartialAndPaid() {
        val bill = FinanceRecurringPolicy.billsThrough(rule("2026-01-02"), LocalDate.parse("2026-01-02"), 2L).single()
        assertEquals(FinanceRecurringPolicy.DisplayStatus.UPCOMING,
            FinanceRecurringPolicy.displayStatus(bill, LocalDate.parse("2026-01-01")))
        assertEquals(FinanceRecurringPolicy.DisplayStatus.DUE,
            FinanceRecurringPolicy.displayStatus(bill, LocalDate.parse("2026-01-02")))
        assertEquals(FinanceRecurringPolicy.DisplayStatus.OVERDUE,
            FinanceRecurringPolicy.displayStatus(bill, LocalDate.parse("2026-01-03")))
        assertEquals(FinanceRecurringPolicy.DisplayStatus.PARTLY_PAID,
            FinanceRecurringPolicy.displayStatus(bill.copy(amountPaidPaise = 1L), LocalDate.parse("2026-01-03")))
        assertEquals(FinanceRecurringPolicy.DisplayStatus.PAID,
            FinanceRecurringPolicy.displayStatus(bill.copy(amountPaidPaise = bill.amountPaise), LocalDate.parse("2026-01-03")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun paymentCannotExceedOutstandingAmount() {
        val bill = FinanceRecurringPolicy.billsThrough(rule("2026-01-02"), LocalDate.parse("2026-01-02"), 2L).single()
        FinanceRecurringPolicy.validatePayment(
            bill,
            FinanceRecurringPayment(
                paymentId = "p1",
                billId = bill.billId,
                amountPaise = bill.amountPaise + 1L,
                paymentDate = "2026-01-02",
                method = "CASH",
                reference = "",
                createdAtMillis = 3L
            )
        )
    }
}
