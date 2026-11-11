package com.example.bookingregister.finance.dev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local append-only audit journal for catalog archival. Not an accounting ledger. */
class HotelCatalogChangeLog(context: Context) {
    private val prefs = context.getSharedPreferences("finance_dev_catalog_audit_v1", Context.MODE_PRIVATE)
    fun record(action: String, targetId: String, reason: String) {
        require(reason.isNotBlank()) { "A reason is required" }
        val array = JSONArray(prefs.getString("events", "[]") ?: "[]")
        array.put(JSONObject().put("action", action).put("targetId", targetId)
            .put("reason", reason.trim()).put("atMillis", System.currentTimeMillis()))
        check(prefs.edit().putString("events", array.toString()).commit()) { "Could not save audit event" }
    }
}
