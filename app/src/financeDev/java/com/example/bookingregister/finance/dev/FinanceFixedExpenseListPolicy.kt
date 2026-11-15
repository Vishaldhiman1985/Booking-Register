package com.example.bookingregister.finance.dev

/** Read-only projection of fixed-expense bills into the Expenses screen.
 * Never writes an expense draft, bank payment, stock movement, or accounting journal.
 */
internal object FinanceFixedExpenseListPolicy {
    data class Summary(val billedPaise: Long, val paidPaise: Long, val duePaise: Long)

    fun status(bill: FinanceFixedExpenseStore.Bill): String = when {
        bill.paid == 0L -> "Not paid"
        bill.paid >= bill.amount -> "Paid"
        else -> "Partially paid"
    }

    fun visible(bills: List<FinanceFixedExpenseStore.Bill>, filter: String): List<FinanceFixedExpenseStore.Bill> {
        require(filter in setOf("All", "Not paid", "Partially paid", "Paid")) { "Unknown expense filter" }
        return bills.filter { filter == "All" || status(it) == filter }
    }

    fun summary(bills: List<FinanceFixedExpenseStore.Bill>): Summary {
        var billed = 0L
        var paid = 0L
        for (bill in bills) {
            require(bill.amount > 0 && bill.paid >= 0 && bill.paid <= bill.amount) { "Invalid fixed bill balance" }
            billed = Math.addExact(billed, bill.amount)
            paid = Math.addExact(paid, bill.paid)
        }
        return Summary(billed, paid, Math.subtractExact(billed, paid))
    }
}
