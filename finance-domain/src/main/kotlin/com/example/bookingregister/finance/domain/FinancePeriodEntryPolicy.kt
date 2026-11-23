package com.example.bookingregister.finance.domain

import java.time.LocalDate
import java.time.YearMonth

/** V20: date-based provisional entry protection. NOT a final accounting-period close. */
object FinancePeriodEntryPolicy {
    fun isLockable(month: String, today: LocalDate): Boolean =
        YearMonth.parse(month).isBefore(YearMonth.from(today))

    fun monthOf(date: String): String = YearMonth.from(LocalDate.parse(date)).toString()

    fun requireDateOpen(date: String, isLocked: Boolean) {
        LocalDate.parse(date)
        require(!isLocked) { "This month is provisionally locked for manual expenses and dated payments. Reopen it in V24 Controls with a reason." }
    }

    fun requireEditable(oldDate: String, newDate: String, lockedMonths: Set<String>) {
        val oldMonth = monthOf(oldDate)
        val newMonth = monthOf(newDate)
        requireDateOpen(oldDate, oldMonth in lockedMonths)
        requireDateOpen(newDate, newMonth in lockedMonths)
    }

    fun requireReviewEvidence(fingerprint: String, note: String) {
        require(Regex("[a-fA-F0-9]{64}").matches(fingerprint)) { "A matching reconciliation fingerprint is required" }
        require(note.trim().length in 8..240) { "Enter a reason of 8 to 240 characters" }
    }
}
