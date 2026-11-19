package com.example.bookingregister.finance.dev

import org.junit.Assert.*
import org.junit.Test

class FinanceMonthlyExpenseReportPolicyTest {
    private fun row(id:String,month:String,dept:String,category:String,gross:Long,paid:Long)=
        FinanceMonthlyExpenseReportPolicy.Expense(id,month,dept,category,category,id,"X",gross,paid,gross-paid,"MANUAL")
    @Test fun fullSalaryExpenseRemainsDespitePriorAdvance() {
        val inputs=listOf(row("salary","2026-11","Kitchen","Salaries & Wages",1850000,500000),
            row("grocery","2026-11","Kitchen","Kitchen Grocery",1200000,1200000),
            row("old","2026-10","Kitchen","Kitchen Grocery",500,0))
        val monthly=FinanceMonthlyExpenseReportPolicy.monthly(inputs,"2026-11")
        val totals=FinanceMonthlyExpenseReportPolicy.totals(monthly)
        assertEquals(3050000L,totals.gross)
        assertEquals(1700000L,totals.settled)
        assertEquals(1350000L,totals.outstanding)
        assertEquals(2,FinanceMonthlyExpenseReportPolicy.categoryTotals(monthly).size)
    }
    @Test fun duplicateBillIdIsRejected() {
        val x=row("same","2026-11","Rooms","Laundry",1000,0)
        try { FinanceMonthlyExpenseReportPolicy.monthly(listOf(x,x),"2026-11");fail("Must reject duplicates") }
        catch(_:IllegalArgumentException) {}
    }
}
