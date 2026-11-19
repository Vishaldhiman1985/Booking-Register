package com.example.bookingregister.finance.dev

/** Read-only summarisation of existing dated liabilities. No journal, payroll or payments writes. */
internal object FinanceMonthlyExpenseReportPolicy {
    data class Expense(
        val id: String, val month: String, val department: String, val category: String,
        val type: String, val title: String, val payee: String,
        val grossPaise: Long, val settledPaise: Long, val outstandingPaise: Long,
        val source: String
    )
    data class Totals(val gross: Long, val settled: Long, val outstanding: Long, val count: Int)
    data class CategoryTotal(val department: String, val category: String, val totals: Totals)

    fun monthly(lines: List<Expense>, month: String): List<Expense> {
        require(Regex("\\d{4}-(0[1-9]|1[0-2])").matches(month)) { "Invalid report month" }
        val active=lines.filter { it.month==month }
        require(active.map { it.id }.distinct().size==active.size) { "Duplicate bill IDs in report" }
        active.forEach {
            require(it.grossPaise>0 && it.settledPaise>=0 && it.outstandingPaise>=0) { "Invalid bill amounts" }
            require(Math.addExact(it.settledPaise,it.outstandingPaise)==it.grossPaise) { "Unreconciled bill ${it.id}" }
        }
        return active.sortedWith(compareBy<Expense>({it.department.lowercase()}, {it.category.lowercase()}, {it.title.lowercase()}, {it.id}))
    }
    fun totals(lines: List<Expense>): Totals = Totals(
        lines.fold(0L) { a,b->Math.addExact(a,b.grossPaise) },
        lines.fold(0L) { a,b->Math.addExact(a,b.settledPaise) },
        lines.fold(0L) { a,b->Math.addExact(a,b.outstandingPaise) },lines.size
    )
    fun categoryTotals(lines: List<Expense>): List<CategoryTotal> =
        lines.groupBy { it.department.trim().ifEmpty { "Unassigned" } to it.category.trim().ifEmpty { "Needs classification" } }
            .map { (key,group) -> CategoryTotal(key.first,key.second,totals(group)) }
            .sortedWith(compareBy<CategoryTotal>({it.department.lowercase()},{it.category.lowercase()}))
}
