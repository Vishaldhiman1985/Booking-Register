package com.example.bookingregister.finance.dev

import android.content.Context
import com.example.bookingregister.finance.domain.FinanceV23ProfitBridgePolicy
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

/** User-reviewed local allocation evidence. Separate from posted bills, tax and inventory stores. */
internal class FinanceV23CostCenterStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("finance_dev_v23_cost_centers_v1", Context.MODE_PRIVATE)
    data class Stored(val month: String, val key: String, val amountPaise: Long,
                      val split: FinanceV23ProfitBridgePolicy.Split, val updatedAt: Long)
    private fun encode(row: Stored) = JSONObject().apply {
        put("month", row.month); put("key", row.key); put("amountPaise", row.amountPaise)
        put("roomsBps", row.split.roomsBps); put("kitchenBps", row.split.kitchenBps)
        put("servicesBps", row.split.servicesBps); put("updatedAt", row.updatedAt)
    }
    private fun decode(json: JSONObject) = Stored(
        json.getString("month"), json.getString("key"), json.getLong("amountPaise"),
        FinanceV23ProfitBridgePolicy.Split(json.getInt("roomsBps"), json.getInt("kitchenBps"),
            json.getInt("servicesBps")), json.getLong("updatedAt")
    )
    private fun key(month: String, source: String): String {
        YearMonth.parse(month)
        require(source.startsWith("bill:") || source.startsWith("stock:")) { "Invalid allocation source" }
        require(source.substringAfter(':').isNotBlank()) { "Missing allocation source identity" }
        return "$month|$source"
    }
    fun get(month: String, source: String): Stored? = JSONObject(prefs.getString("allocations", "{}") ?: "{}")
        .optJSONObject(key(month, source))?.let(::decode)

    /** Changing a split requires a reason; amount+ID ties it to the specific source version. */
    fun save(month: String, source: String, amountPaise: Long,
             split: FinanceV23ProfitBridgePolicy.Split, reason: String) {
        val k = key(month, source)
        require(amountPaise > 0L) { "Only positive source costs can be allocated" }
        require(reason.trim().length in 8..240) { "Reason or allocation basis must contain 8-240 characters" }
        FinancePeriodEntryLock(app).requireDateOpen("$month-01")
        synchronized(monitor) {
            val all = JSONObject(prefs.getString("allocations", "{}") ?: "{}")
            val existing = all.optJSONObject(k)?.toString()
            val updatedAt = System.currentTimeMillis()
            val next = encode(Stored(month, source, amountPaise, split, updatedAt))
            val history = JSONArray(prefs.getString("history", "[]") ?: "[]")
            history.put(JSONObject().apply {
                put("month", month); put("source", source); put("previous", existing ?: JSONObject.NULL)
                put("next", next); put("reason", reason.trim()); put("at", updatedAt)
            })
            all.put(k, next)
            check(prefs.edit().putString("allocations", all.toString())
                .putString("history", history.toString()).commit()) { "Allocation evidence could not be saved" }
        }
    }
    private companion object { val monitor = Any() }
}
