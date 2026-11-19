package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinancePayrollPolicyTest {
    private val nov=YearMonth.of(2026,11)
    private val joined=LocalDate.of(2026,1,1)

    @Test fun sixNonWorkingTrackedButBasicSalaryUnchanged() {
        val result=FinancePayrollPolicy.calculate(nov,1800000,joined,null,6,4,0,0)
        assertEquals(4,result.paidRestUsed)
        assertEquals(2,result.unpaidDays)
        assertEquals(1800000L,result.grossPayPaise)
    }
    @Test fun managerCanApproveOneExtraDayWithoutAttendanceRegister() {
        val result=FinancePayrollPolicy.calculate(nov,1800000,joined,null,6,4,1,0)
        assertEquals(1,result.unpaidDays)
        assertEquals(1800000L,result.grossPayPaise)
    }
    @Test fun employeeStartingMidMonthIsProrated() {
        val result=FinancePayrollPolicy.calculate(nov,1800000,
            LocalDate.of(2026,11,16),null,0,4,0,0)
        assertEquals(15,result.coveredDays)
        assertEquals(900000L,result.grossPayPaise)
        assertEquals(0,result.paidRestUsed) // no non-working days: no leave allowance consumed
    }
    @Test fun employeeLeavingBeforeEndOfMonthIsProrated() {
        val result=FinancePayrollPolicy.calculate(nov,1800000,joined,LocalDate.of(2026,11,15),0,4,0,0)
        assertEquals(15,result.coveredDays)
        assertEquals(900000L,result.grossPayPaise)
    }
    @Test fun salaryDueNextMonthOnConfiguredDate() {
        assertEquals(LocalDate.of(2026,12,2),FinancePayrollPolicy.nextPayDate(nov,2))
    }
    @Test fun uniquePayrollIdentityAcrossMonths() {
        val id=FinancePayrollPolicy.billId("staff-1","2026-11")
        assertEquals(id,FinancePayrollPolicy.billId("staff-1","2026-11"))
        assertEquals(false,id==FinancePayrollPolicy.billId("staff-1","2026-12"))
    }
    @Test(expected=IllegalArgumentException::class)
    fun cannotMarkMoreNonWorkingThanEmployedDays() {
        FinancePayrollPolicy.calculate(nov,1800000,LocalDate.of(2026,11,16),null,16,4,0,0)
    }
    @Test(expected=IllegalArgumentException::class)
    fun managementCannotApproveExtraDaysBeyondUncoveredAbsences() {
        FinancePayrollPolicy.calculate(nov,1800000,joined,null,3,4,1,0)
    }
}
