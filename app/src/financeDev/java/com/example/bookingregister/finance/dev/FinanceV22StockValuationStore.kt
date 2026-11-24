package com.example.bookingregister.finance.dev

import android.content.Context
import com.example.bookingregister.finance.domain.FinanceV22StockCostPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

/** Separate Finance DEV-only COST EVIDENCE; never changes quantities, PO, invoices, payables or payroll. */
internal class FinanceV22StockValuationStore(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "finance_dev_v22_stock_cost_evidence_v1", Context.MODE_PRIVATE
    )
    data class Stored(val month: String, val itemId: String, val fingerprint: String,
                      val evidence: FinanceV22StockCostPolicy.Evidence, val updatedAt: Long)

    private fun encode(x: Stored) = JSONObject().apply {
        put("month", x.month); put("itemId", x.itemId); put("fingerprint", x.fingerprint)
        put("openingUnitPaise", x.evidence.openingUnitPaise ?: JSONObject.NULL)
        put("receivedUnitPaise", x.evidence.receivedUnitPaise ?: JSONObject.NULL)
        put("openingReference", x.evidence.openingReference)
        put("receivedReference", x.evidence.receivedReference)
        put("updatedAt", x.updatedAt)
    }
    private fun decode(o: JSONObject) = Stored(
        o.getString("month"), o.getString("itemId"), o.getString("fingerprint"),
        FinanceV22StockCostPolicy.Evidence(
            if (o.isNull("openingUnitPaise")) null else o.getLong("openingUnitPaise"),
            if (o.isNull("receivedUnitPaise")) null else o.getLong("receivedUnitPaise"),
            o.getString("openingReference"), o.getString("receivedReference")
        ), o.getLong("updatedAt")
    )
    fun get(month: String, itemId: String): Stored? {
        YearMonth.parse(month)
        return JSONObject(prefs.getString("items", "{}") ?: "{}")
            .optJSONObject("$month:$itemId")?.let(::decode)
    }

    fun save(line: FinanceV22StockCostPolicy.Quantity, month: String, fingerprint: String,
             evidence: FinanceV22StockCostPolicy.Evidence, reason: String) {
        YearMonth.parse(month)
        require(Regex("[a-fA-F0-9]{64}").matches(fingerprint)) { "Stock approval fingerprint missing" }
        require(reason.trim().length in 8..240) { "Enter evidence/correction reason of 8-240 characters" }
        FinanceV22StockCostPolicy.value(line, evidence) // Detect invalid/overflowing valuation before storing it.
        // Revalidate from the current authoritative stock snapshot immediately before saving.
        val store = HotelMonthlyStockStore(context)
        val stock = store.all().firstOrNull { it.month == month } ?: error("Stock month is not approved")
        require(!store.receiptsChangedAfterClose(month)) { "Receipts changed after stock approval. Reconcile stock first" }
        val currentLines = FinanceV22StockValuationScreen.quantityLines(stock)
        val current = FinanceV22StockCostPolicy.fingerprint(month, stock.approvedAtMillis,
            currentLines, stock.purchaseReceiptIds)
        require(current == fingerprint && currentLines.any { it == line }) {
            "Stock quantities changed during valuation. Reopen and review the month."
        }
        FinancePeriodEntryLock(context).requireDateOpen("$month-01")
        synchronized(monitor) {
            val items = JSONObject(prefs.getString("items", "{}") ?: "{}")
            val history = JSONArray(prefs.getString("history", "[]") ?: "[]")
            val key = "$month:${line.itemId}"
            val previous = items.optJSONObject(key)?.toString()
            val now = System.currentTimeMillis()
            val next = Stored(month, line.itemId, fingerprint, evidence, now)
            items.put(key, encode(next))
            history.put(JSONObject().apply {
                put("month",month);put("itemId",line.itemId)
                put("action",if(previous==null)"PRICE_EVIDENCE_ADDED" else "PRICE_EVIDENCE_REVISED")
                put("before", previous ?: JSONObject.NULL);put("after",encode(next))
                put("reason",reason.trim());put("at",now)
            })
            check(prefs.edit().putString("items",items.toString())
                .putString("history",history.toString()).commit()) { "Could not save stock cost evidence" }
        }
    }
    private companion object { val monitor = Any() }
}
