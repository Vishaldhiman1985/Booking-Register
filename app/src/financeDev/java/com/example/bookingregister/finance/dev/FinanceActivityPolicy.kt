package com.example.bookingregister.finance.dev

import java.time.LocalDate

/** Small, pure rules behind the local Finance DEV payable projection. All values are paise. */
internal object FinanceActivityPolicy {
    data class Totals(val billed: Long, val paid: Long, val due: Long)
    fun totals(bills: List<Pair<Long,Long>>): Totals {
        var billed=0L; var paid=0L
        bills.forEach { (amount, settled) ->
            require(amount>=0 && settled>=0 && settled<=amount) { "Invalid bill settlement" }
            billed=Math.addExact(billed,amount); paid=Math.addExact(paid,settled)
        }
        return Totals(billed,paid,Math.subtractExact(billed,paid))
    }
    fun due(amount: Long, openingPaid: Long, allocated: Long): Long {
        require(amount>0 && openingPaid>=0 && allocated>=0)
        val settled=Math.addExact(openingPaid,allocated)
        require(settled<=amount) { "Bill over-allocated" }
        return amount-settled
    }
    fun remainingAdvance(amount: Long, allocated: Long): Long {
        require(amount>0 && allocated>=0 && allocated<=amount)
        return amount-allocated
    }
    fun status(amount: Long, due: Long): String = when {
        due==0L -> "Paid"
        due==amount -> "Not paid"
        else -> "Partially paid"
    }
    data class Event(val id: String, val date: String, val sortOrder: Int)
    /** Deterministic oldest-first order; bill occurs before payments on the same date. */
    fun chronological(events: List<Event>): List<Event> = events.sortedWith(
        compareBy<Event> { LocalDate.parse(it.date) }.thenBy { it.sortOrder }.thenBy { it.id }
    )
    fun validateSettlement(amount: Long, currentDue: Long) {
        require(amount>0L) { "Payment must be positive" }
        require(amount<=currentDue) { "Amount exceeds outstanding balance" }
    }
}
