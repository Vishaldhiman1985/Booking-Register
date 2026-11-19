package com.example.bookingregister.finance.dev

import java.time.LocalDate
import java.time.YearMonth

/**
 * A salary advance belongs to the employee and the month of its actual payment date.
 * Auto-allocate that month's active advances, plus *partially allocated* earlier
 * advances (the demonstrable remaining carry forward). Never silently move a wholly
 * unused previous-month advance into a different salary period.
 *
 * Values are paise. This policy does not post ledger entries or move money.
 */
internal object FinanceSalaryAdvancePolicy {
    data class Advance(val id:String,val date:String,val amount:Long,val available:Long)
    data class Allocation(val paymentId:String,val amount:Long)
    fun eligible(period:YearMonth,advances:List<Advance>):List<Advance> = advances.filter { a ->
        require(a.amount>0 && a.available in 0..a.amount)
        val month=YearMonth.from(LocalDate.parse(a.date))
        a.available>0L && (month==period || (month.isBefore(period) && a.available<a.amount))
    }.sortedWith(compareBy<Advance> { it.date }.thenBy { it.id })

    fun plan(period:YearMonth,due:Long,advances:List<Advance>):List<Allocation> {
        require(due>=0L)
        var remainder=due
        val result=mutableListOf<Allocation>()
        eligible(period,advances).forEach { a ->
            val alloc=minOf(remainder,a.available)
            if(alloc>0L) {
                result.add(Allocation(a.id,alloc))
                remainder-=alloc
            }
        }
        return result
    }
    fun projectedBalance(period:YearMonth,gross:Long,advances:List<Advance>):Long {
        require(gross>=0L)
        val allocations=plan(period,gross,advances)
        return gross-allocations.fold(0L) { acc,a -> Math.addExact(acc,a.amount) }
    }
}
