package com.example.bookingregister.finance.dev

import android.content.Context
import com.example.bookingregister.finance.domain.FinancePeriodEntryPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/**
 * Finance DEV-only, local PROVISIONAL guard for manual expense drafts and dated payments.
 * It deliberately does not claim to close payroll, PO, stock, revenue or statutory books.
 * Review snapshots and this setting are local preferences, not an audited journal.
 */
internal class FinancePeriodEntryLock(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "finance_dev_provisional_entry_lock_v20", Context.MODE_PRIVATE
    )

    fun isLocked(month: String): Boolean {
        YearMonth.parse(month)
        return prefs.getBoolean("locked:$month", false)
    }

    fun requireDateOpen(date: String) {
        val month = FinancePeriodEntryPolicy.monthOf(date)
        FinancePeriodEntryPolicy.requireDateOpen(date, isLocked(month))
    }

    fun requireEditOpen(oldDate: String, newDate: String) {
        FinancePeriodEntryPolicy.requireEditable(oldDate, newDate,
            setOf(FinancePeriodEntryPolicy.monthOf(oldDate), FinancePeriodEntryPolicy.monthOf(newDate))
                .filter(::isLocked).toSet())
    }

    /** Record a reasoned, reversible provisional status change; it does NOT post or close books. */
    fun setLocked(month: String, desired: Boolean, fingerprint: String, note: String) {
        FinancePeriodEntryPolicy.requireReviewEvidence(fingerprint, note)
        val period = YearMonth.parse(month)
        if (desired) require(FinancePeriodEntryPolicy.isLockable(month, LocalDate.now())) {
            "Only completed past months can be provisionally locked"
        }
        synchronized(monitor) {
            val previous = isLocked(month)
            require(previous != desired) { "Month already has this provisional lock status" }
            val history = JSONArray(prefs.getString("history", "[]") ?: "[]")
            history.put(JSONObject().apply {
                put("month", period.toString())
                put("action", if (desired) "LOCK" else "REOPEN")
                put("fingerprint", fingerprint)
                put("reason", note.trim())
                put("recordedAt", System.currentTimeMillis())
            })
            check(prefs.edit().putBoolean("locked:$month", desired)
                .putString("history", history.toString()).commit()) {
                "Could not save provisional period lock or audit history"
            }
        }
    }

    private companion object { val monitor = Any() }
}
