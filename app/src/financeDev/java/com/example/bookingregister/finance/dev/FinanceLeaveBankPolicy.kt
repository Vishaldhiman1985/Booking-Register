package com.example.bookingregister.finance.dev

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Company DISCRETIONARY leave-credit register, not a statutory weekly-rest, earned-leave
 * or overtime calculator. Confirm the applicable employment policy before using it for pay.
 * Historic months without a manager-confirmed record never silently earn credits.
 */
internal object FinanceLeaveBankPolicy {
    data class Month(
        val period:String, val calendarDays:Int, val coveredDays:Int,
        val credits:Int, val nonWorking:Int, val usedCredits:Int,
        val managerPaidExtra:Int, val unresolvedExtra:Int, val unusedCredits:Int
    ) {
        val netChange:Int get() = unusedCredits - unresolvedExtra
    }
    data class Balance(
        val months:Int, val earned:Int, val used:Int, val extraPaid:Int,
        val unresolvedExtra:Int, val unused:Int, val net:Int
    )
    data class ExitEstimate(
        val companyBenefitDays:Int, val companyBenefitPaise:Long,
        val unresolvedExtraDays:Int, val noticeCompleted:Boolean
    )

    fun month(period:YearMonth, joined:LocalDate, departed:LocalDate?,
              allowance:Int, nonWorking:Int, approvedExtra:Int):Month {
        require(allowance in 0..10) { "Monthly company leave credits must be 0–10" }
        val first=maxOf(joined,period.atDay(1))
        val last=minOf(departed ?: period.atEndOfMonth(),period.atEndOfMonth())
        require(!first.isAfter(last)) { "Employee was not employed in this month" }
        val covered=(last.toEpochDay()-first.toEpochDay()+1).toInt()
        require(nonWorking in 0..covered) { "Non-working days exceed days employed" }
        val credit=(allowance.toLong()*covered/period.lengthOfMonth()).toInt()
        val used=minOf(nonWorking,credit)
        val excess=nonWorking-used
        require(approvedExtra in 0..excess) { "Approved extra paid days exceed excess non-working days" }
        return Month(period.toString(),period.lengthOfMonth(),covered,credit,nonWorking,
            used,approvedExtra,excess-approvedExtra,credit-used)
    }
    fun balance(months:List<Month>):Balance {
        var earned=0;var used=0;var extraPaid=0;var unresolved=0;var unused=0
        months.forEach {
            earned=Math.addExact(earned,it.credits)
            used=Math.addExact(used,it.usedCredits)
            extraPaid=Math.addExact(extraPaid,it.managerPaidExtra)
            unresolved=Math.addExact(unresolved,it.unresolvedExtra)
            unused=Math.addExact(unused,it.unusedCredits)
        }
        return Balance(months.size,earned,used,extraPaid,unresolved,unused,unused-unresolved)
    }
    /** A proposal ONLY. Statutory leave is independent, never forfeited by this policy. */
    fun exitPreview(balance:Balance,noticeCompleted:Boolean,monthlySalaryPaise:Long,exit:LocalDate):ExitEstimate {
        require(monthlySalaryPaise>0)
        val eligible=if(noticeCompleted) maxOf(0,balance.net) else 0
        val value=BigDecimal.valueOf(monthlySalaryPaise)
            .multiply(BigDecimal.valueOf(eligible.toLong()))
            .divide(BigDecimal.valueOf(YearMonth.from(exit).lengthOfMonth().toLong()),0,RoundingMode.HALF_UP)
            .longValueExact()
        return ExitEstimate(eligible,value,balance.unresolvedExtra,noticeCompleted)
    }
}
