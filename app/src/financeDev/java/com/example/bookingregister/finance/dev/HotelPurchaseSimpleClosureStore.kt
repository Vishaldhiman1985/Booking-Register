package com.example.bookingregister.finance.dev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Procurement-only reference values. Does not create a journal entry or alter legacy stores. */
data class HotelPurchaseDeliveryUpdate(
    val atMillis: Long,
    val billAmount: Double,
    val receiptId: String,
    val note: String
)

data class HotelPurchaseSimpleClosure(
    val orderId: String,
    val finalAmount: Double?,
    val closedAtMillis: Long,
    val unavailable: Map<String, Double>,
    val updates: List<HotelPurchaseDeliveryUpdate>
) {
    val closed: Boolean get() = closedAtMillis > 0L
}

class HotelPurchaseSimpleClosureStore(context: Context) {
    private val preferences = context.getSharedPreferences("finance_dev_purchase_simple_closure_v1", Context.MODE_PRIVATE)

    fun get(orderId: String): HotelPurchaseSimpleClosure {
        val entries = JSONArray(preferences.getString("records", "[]") ?: "[]")
        for (i in 0 until entries.length()) {
            val json = entries.getJSONObject(i)
            if (json.getString("orderId") != orderId) continue
            val missing = json.optJSONObject("unavailable") ?: JSONObject()
            val quantities = mutableMapOf<String, Double>()
            val keys = missing.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                quantities[key] = missing.getDouble(key)
            }
            val history = json.optJSONArray("updates") ?: JSONArray()
            return HotelPurchaseSimpleClosure(
                orderId,
                if (json.isNull("finalAmount")) null else json.optDouble("finalAmount"),
                json.optLong("closedAtMillis", 0L),
                quantities,
                (0 until history.length()).map { index ->
                    val entry = history.getJSONObject(index)
                    HotelPurchaseDeliveryUpdate(
                        entry.getLong("atMillis"), entry.getDouble("billAmount"),
                        entry.optString("receiptId", ""), entry.optString("note", "")
                    )
                }
            )
        }
        return HotelPurchaseSimpleClosure(orderId, null, 0L, emptyMap(), emptyList())
    }

    fun saveUpdate(orderId: String, amount: Double, receiptId: String, note: String,
                   unavailableNow: Map<String, Double> = emptyMap()) {
        require(amount.isFinite() && amount >= 0.0) { "Enter a valid bill amount" }
        val old = get(orderId)
        require(!old.closed) { "Order already closed" }
        require(receiptId.isNotBlank() || note.isNotBlank()) { "Delivery update reference required" }
        if (old.updates.any { it.receiptId == receiptId && receiptId.isNotBlank() }) return
        require(unavailableNow.values.all { it.isFinite() && it >= 0.0 }) { "Invalid unavailable quantity" }
        val totalUnavailable = old.unavailable.toMutableMap()
        unavailableNow.forEach { (id, qty) ->
            totalUnavailable[id] = (totalUnavailable[id] ?: 0.0) + qty
        }
        persist(old.copy(
            unavailable = totalUnavailable,
            updates = old.updates + HotelPurchaseDeliveryUpdate(System.currentTimeMillis(), amount, receiptId, note)
        ))
    }

    fun close(order: HotelPurchaseOrder, receipts: List<HotelPurchaseGoodsReceipt>,
              unavailable: Map<String, Double>, amount: Double) {
        require(amount.isFinite() && amount >= 0) { "Final bill amount is required" }
        val record = get(order.id)
        require(!record.closed) { "Order already closed" }
        require(order.status == "PLACED") { "Only placed orders can be delivered" }
        val remaining = HotelPurchaseGoodsReceiptPolicy.remaining(order, receipts)
        require(unavailable.keys.all { it in remaining.keys }) { "Unknown item" }
        require(unavailable.values.all { it.isFinite() && it >= 0 }) { "Invalid unavailable quantity" }
        require(remaining.all { (id, qty) -> kotlin.math.abs(qty - (unavailable[id] ?: 0.0)) < 0.000001 }) {
            "Some quantities remain outstanding"
        }
        require(receipts.isNotEmpty() || unavailable.isNotEmpty()) { "Delivery not recorded" }
        require(record.unavailable == unavailable) { "Unavailable item history changed" }
        persist(record.copy(finalAmount = amount, closedAtMillis = System.currentTimeMillis(), unavailable = unavailable))
    }

    private fun persist(record: HotelPurchaseSimpleClosure) {
        val current = JSONArray(preferences.getString("records", "[]") ?: "[]")
        val next = JSONArray()
        for (i in 0 until current.length()) {
            val value = current.getJSONObject(i)
            if (value.optString("orderId") != record.orderId) next.put(value)
        }
        val unavailable = JSONObject()
        record.unavailable.forEach { (id, qty) -> unavailable.put(id, qty) }
        next.put(JSONObject().apply {
            put("orderId", record.orderId)
            put("finalAmount", record.finalAmount ?: JSONObject.NULL)
            put("closedAtMillis", record.closedAtMillis)
            put("unavailable", unavailable)
            put("updates", JSONArray().apply {
                record.updates.forEach { update ->
                    put(JSONObject().apply {
                        put("atMillis", update.atMillis)
                        put("billAmount", update.billAmount)
                        put("receiptId", update.receiptId)
                        put("note", update.note)
                    })
                }
            })
        })
        check(preferences.edit().putString("records", next.toString()).commit()) { "Could not save purchase closure" }
    }
}
