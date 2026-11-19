package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinanceLeaveBankPolicyTest {
    private val joined=LocalDate.of(2026,1,1)
    private val nov=YearMonth.of(2026,11)
    @Test fun noAbsenceAccruesFourCompanyCredits() {
        val m=FinanceLeaveBankPolicy.month(nov,joined,null,4,0,0)
        assertEquals(4,m.unusedCredits);assertEquals(0,m.unresolvedExtra)
    }
    @Test fun sixNonworkingWithOneManagerApprovedExtraLeavesOneUnresolved() {
        val m=FinanceLeaveBankPolicy.month(nov,joined,null,4,6,1)
        assertEquals(4,m.usedCredits);assertEquals(1,m.managerPaidExtra)
        assertEquals(1,m.unresolvedExtra);assertEquals(-1,m.netChange)
    }
    @Test fun negativeCompanyDaysNeverBecomeAutomaticRecovery() {
        val m=FinanceLeaveBankPolicy.month(nov,joined,null,4,6,0)
        val b=FinanceLeaveBankPolicy.balance(listOf(m))
        val payout=FinanceLeaveBankPolicy.exitPreview(b,true,1800000,nov.atEndOfMonth())
        assertEquals(-2,b.net); assertEquals(0L,payout.companyBenefitPaise)
        assertEquals(2,payout.unresolvedExtraDays)
    }
    @Test fun benefitsLapseOnNoNoticeButStatutoryLeaveOutsideThisPolicy() {
        val m=FinanceLeaveBankPolicy.month(nov,joined,null,4,0,0)
        val estimate=FinanceLeaveBankPolicy.exitPreview(
            FinanceLeaveBankPolicy.balance(listOf(m)),false,1800000,nov.atEndOfMonth())
        assertEquals(0,estimate.companyBenefitDays)
        assertEquals(0L,estimate.companyBenefitPaise)
    }
    @Test fun noticeCompletedUsesExitMonthActualCalendarDays() {
        val m=FinanceLeaveBankPolicy.month(nov,joined,null,4,0,0)
        val estimate=FinanceLeaveBankPolicy.exitPreview(
            FinanceLeaveBankPolicy.balance(listOf(m)),true,1800000,nov.atEndOfMonth())
        assertEquals(240000L,estimate.companyBenefitPaise)
    }
    @Test fun februaryCreditIsProratedForJoiningMidMonth() {
        val feb=YearMonth.of(2028,2) // leap year
        val m=FinanceLeaveBankPolicy.month(feb,LocalDate.of(2028,2,15),null,4,0,0)
        assertEquals(29,m.calendarDays); assertEquals(15,m.coveredDays)
        assertEquals(2,m.credits)
    }
    @Test(expected=IllegalArgumentException::class)
    fun cannotApproveMoreThanExtraNonWorking() {
        FinanceLeaveBankPolicy.month(nov,joined,null,4,2,1)
    }
}
