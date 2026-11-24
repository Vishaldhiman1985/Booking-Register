package com.example.bookingregister.finance.dev

import android.content.Context
import com.example.bookingregister.finance.domain.FinanceV24AuditPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

/** Finance DEV local human review receipts. Preferences are NOT tamper-proof or externally signed. */
internal class FinanceV24AuditReviewStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "finance_dev_v24_audit_review_history_v1", Context.MODE_PRIVATE
    )
    data class Review(val month: String, val fingerprint: String, val reviewer: String,
                      val note: String, val blockers: Int, val reviews: Int, val recordedAt: Long)

    private fun decode(o: JSONObject): Review = Review(
        o.getString("month"), o.getString("fingerprint"), o.getString("reviewer"),
        o.getString("note"), o.getInt("blockers"), o.getInt("reviews"), o.getLong("recordedAt")
    )
    fun history(month: String): List<Review> {
        YearMonth.parse(month)
        val rows = JSONArray(prefs.getString("reviews", "[]") ?: "[]")
        return (0 until rows.length()).map { decode(rows.getJSONObject(it)) }
            .filter { it.month == month }
    }
    fun latest(month: String): Review? = history(month).lastOrNull()

    /** Allows a documented review even when exceptions remain; never implies approval/closure. */
    fun save(report: FinanceV24AuditPolicy.Report, reviewer: String, note: String) {
        require(Regex("[0-9a-f]{64}").matches(report.fingerprint)) { "Report fingerprint missing" }
        require(reviewer.trim().length in 2..80) { "Reviewer name: 2-80 characters" }
        require(note.trim().length in 12..500) { "Review note: 12-500 characters" }
        synchronized(monitor) {
            val current = JSONArray(prefs.getString("reviews", "[]") ?: "[]")
            check(current.length() < 10000) { "Review history limit reached; export records before continuing" }
            current.put(JSONObject().apply {
                put("month", report.month); put("fingerprint", report.fingerprint)
                put("reviewer", reviewer.trim()); put("note", note.trim())
                put("blockers", report.blockerCount); put("reviews", report.reviewCount)
                put("recordedAt", System.currentTimeMillis())
                put("scope", "PROVISIONAL_REVIEW_ONLY_NOT_FINAL_CLOSE")
            })
            check(prefs.edit().putString("reviews", current.toString()).commit()) {
                "Could not persist local review history"
            }
        }
    }
    private companion object { val monitor = Any() }
}
