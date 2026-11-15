package com.example.bookingregister.finance.dev

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** Pure exact-money and recurring schedule rules. DEV-only, not a posted journal. */
internal object FinanceFixedPolicy {
    fun amount(text: String): Long {
        val value = text.trim().replace(",", "").removePrefix("₹").trim()
        val major = BigDecimal(value)
        require(major > BigDecimal.ZERO) { "Amount must be positive" }
        require(major.scale() <= 2) { "Use at most two decimal places" }
        return major.movePointRight(2).longValueExact()
    }
    fun rupees(paise: Long): String = "₹" + BigDecimal.valueOf(paise, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString()
    fun month(text: String): YearMonth = YearMonth.parse(text.trim())
    fun due(month: YearMonth, day: Int): LocalDate {
        require(day in 1..31) { "Payment day must be between 1 and 31" }
        return month.atDay(day.coerceAtMost(month.lengthOfMonth()))
    }
    fun effectiveAmount(month: YearMonth, revisions: List<Pair<YearMonth, Long>>): Long {
        val found = revisions.filter { it.first <= month }.maxByOrNull { it.first }
            ?: error("No amount configured for $month")
        require(found.second > 0) { "Invalid recurring amount" }
        return found.second
    }
    fun remaining(due: Long, paid: Long): Long {
        require(due >= 0 && paid >= 0 && paid <= due) { "Invalid bill payment balance" }
        return due - paid
    }
    fun validateRevision(effective: YearMonth, firstMonth: YearMonth, latestIssued: YearMonth?) {
        require(effective >= firstMonth) { "New amount cannot precede contract start" }
        require(latestIssued == null || effective > latestIssued) {
            "That month's bill is already issued. Choose a future month; old bills remain unchanged."
        }
    }
}
