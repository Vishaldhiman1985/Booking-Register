package com.example.bookingregister.finance.dev

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** Finance DEV payroll calculations. Basic monthly pay is separate from leave banking.
 * Partial joining/leaving months use actual calendar-day proration by company policy;
 * do not treat this as a universal statutory wage deduction formula.
 */
internal object FinancePayrollPolicy {
    data class Calculation(
        val period: String,
        val daysInMonth: Int,
        val coveredDays: Int,
        val nonWorkingDays: Int,
        val paidRestUsed: Int,
        val additionalApprovedPaidDays: Int,
        val unpaidDays: Int,
        val payableDays: Int,
        val basePayPaise: Long,
        val adjustmentPaise: Long,
        val grossPayPaise: Long
    )

    fun calculate(
        period: YearMonth,
        monthlySalaryPaise: Long,
        joinDate: LocalDate,
        lastWorkingDate: LocalDate?,
        nonWorkingDays: Int,
        monthlyPaidRestDays: Int,
        additionalApprovedPaidDays: Int,
        adjustmentPaise: Long
    ): Calculation {
        require(monthlySalaryPaise > 0L) { "Monthly salary must be positive" }
        require(monthlyPaidRestDays in 0..10) { "Paid rest allowance must be 0 to 10 days" }
        require(nonWorkingDays >= 0 && additionalApprovedPaidDays >= 0)
        val first = maxOf(joinDate, period.atDay(1))
        val last = minOf(lastWorkingDate ?: period.atEndOfMonth(), period.atEndOfMonth())
        require(!last.isBefore(first)) { "Employee was not employed in selected month" }
        val days = period.lengthOfMonth()
        val covered = last.toEpochDay().minus(first.toEpochDay()).toInt() + 1
        require(nonWorkingDays <= covered) { "Non-working days cannot exceed employment days" }

        // Company policy preview: rest allowance is prorated for employees joining/leaving mid-month.
        // Local labour rules / contract entitlements require independent approval before production.
        val eligibleRest = (monthlyPaidRestDays.toLong() * covered / days).toInt()
        val paidRest = minOf(nonWorkingDays, eligibleRest)
        require(additionalApprovedPaidDays <= nonWorkingDays - paidRest) {
            "Extra paid days exceed uncovered non-working days"
        }
        val unpaid = nonWorkingDays - paidRest - additionalApprovedPaidDays
        // V18 leave-bank policy: do not reduce the regular monthly salary for leave-bank
        // entries. Absence and discretionary company credits are reviewed separately
        // at exit. A genuine statutory wage deduction requires a separate approved path.
        val payable = covered
        val basePay = BigDecimal.valueOf(monthlySalaryPaise)
            .multiply(BigDecimal.valueOf(covered.toLong()))
            .divide(BigDecimal.valueOf(days.toLong()), 0, RoundingMode.HALF_UP)
            .longValueExact()
        val gross = Math.addExact(basePay, adjustmentPaise)
        require(gross >= 0L) { "Net adjustment cannot make salary negative" }
        return Calculation(period.toString(), days, covered, nonWorkingDays, paidRest,
            additionalApprovedPaidDays, unpaid, payable, basePay, adjustmentPaise, gross)
    }

    fun nextPayDate(period: YearMonth, day: Int): LocalDate {
        require(day in 1..28) { "Salary disbursement day must be 1–28" }
        val next = period.plusMonths(1)
        return next.atDay(minOf(day, next.lengthOfMonth()))
    }

    fun billId(employeeId: String, period: String): String {
        require(employeeId.isNotBlank())
        YearMonth.parse(period)
        return "payroll:$employeeId:$period"
    }
}
