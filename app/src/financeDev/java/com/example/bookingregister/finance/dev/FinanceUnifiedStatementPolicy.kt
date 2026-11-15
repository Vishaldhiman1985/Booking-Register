package com.example.bookingregister.finance.dev

import java.time.LocalDate

/** Pure read model. Never posts, duplicates or alters a source bill. */
internal object FinanceUnifiedStatementPolicy {
    const val FIXED = "FIXED"
    const val MANUAL = "MANUAL"

    data class Entry(
        val id: String,
        val source: String,
        val date: String,
        val title: String,
        val vendor: String,
        val department: String,
        val amountPaise: Long,
        val paidPaise: Long,
        val stockPending: Boolean = false
    ) {
        init {
            require(source == FIXED || source == MANUAL)
            require(id.isNotBlank() && title.isNotBlank())
            LocalDate.parse(date)
            require(amountPaise >= 0 && paidPaise >= 0 && paidPaise <= amountPaise)
        }
        val balancePaise get() = Math.subtractExact(amountPaise, paidPaise)
        val status get() = when {
            balancePaise == 0L -> "Paid"
            paidPaise == 0L -> "Not paid"
            else -> "Partially paid"
        }
    }

    data class Totals(val expenses: Long, val paid: Long, val due: Long)

    /** Fixed obligations appear in the statement on their chosen due date. */
    fun statement(entries: List<Entry>, asOf: LocalDate, filter: String = "All"): List<Entry> {
        require(filter in setOf("All", "Not paid", "Partially paid", "Paid"))
        return entries.asSequence()
            .filter { it.source != FIXED || !LocalDate.parse(it.date).isAfter(asOf) }
            .filter { filter == "All" || it.status == filter }
            .sortedWith(compareByDescending<Entry> { it.date }.thenBy { it.id })
            .toList()
    }

    /** Upcoming fixed bills are deliberately excluded: payables become due at the scheduled date. */
    fun due(entries: List<Entry>, asOf: LocalDate): List<Entry> = entries.asSequence()
        .filter { it.balancePaise > 0L }
        .filter { it.source != FIXED || !LocalDate.parse(it.date).isAfter(asOf) }
        .sortedWith(compareBy<Entry> { it.date }.thenBy { it.id }).toList()

    fun totals(entries: List<Entry>): Totals {
        var expense = 0L
        var paid = 0L
        for (e in entries) {
            expense = Math.addExact(expense, e.amountPaise)
            paid = Math.addExact(paid, e.paidPaise)
        }
        return Totals(expense, paid, Math.subtractExact(expense, paid))
    }
}
