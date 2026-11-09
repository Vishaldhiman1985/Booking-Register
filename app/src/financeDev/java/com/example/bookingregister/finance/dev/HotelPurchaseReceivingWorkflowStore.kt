package com.example.bookingregister.finance.dev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Separate Finance DEV-only audit records. Never modifies original PO/receipt preferences. */
data class HotelPurchaseIssueEvent(
    val atMillis: Long,
    val action: String,
    val note: String
)

data class HotelPurchaseReceivingWorkflow(
    val orderId: String,
    val resolution: String = "OPEN",
    val resolutionNote: String = "",
    val approvedAtMillis: Long = 0L,
    val events: List<HotelPurchaseIssueEvent> = emptyList()
) {
    val archived: Boolean get() = approvedAtMillis > 0L
}

class HotelPurchaseReceivingWorkflowStore(context: Context) {
    private val prefs = context.getSharedPreferences("finance_dev_po_workflow_v1", Context.MODE_PRIVATE)
    private val invoiceEvidence = HotelPurchaseInvoiceStore(context)

    fun all(): List<HotelPurchaseReceivingWorkflow> {
        val data = JSONArray(prefs.getString("records", "[]") ?: "[]")
        return (0 until data.length()).map { i ->
            val obj = data.getJSONObject(i)
            val history = obj.optJSONArray("events") ?: JSONArray()
            HotelPurchaseReceivingWorkflow(
                orderId = obj.getString("orderId"),
                resolution = obj.optString("resolution", "OPEN"),
                resolutionNote = obj.optString("resolutionNote", ""),
                approvedAtMillis = obj.optLong("approvedAtMillis", 0L),
                events = (0 until history.length()).map { j ->
                    val event = history.getJSONObject(j)
                    HotelPurchaseIssueEvent(event.getLong("atMillis"), event.getString("action"), event.optString("note", ""))
                }
            )
        }
    }

    fun get(orderId: String): HotelPurchaseReceivingWorkflow =
        all().firstOrNull { it.orderId == orderId } ?: HotelPurchaseReceivingWorkflow(orderId)

    fun append(orderId: String, action: String, note: String, resolution: String? = null) {
        val current = get(orderId)
        require(!current.archived) { "This order is already archived" }
        require(action.isNotBlank())
        save(current.copy(
            resolution = resolution ?: current.resolution,
            resolutionNote = if (resolution != null) note else current.resolutionNote,
            events = current.events + HotelPurchaseIssueEvent(System.currentTimeMillis(), action, note)
        ))
    }

    fun approve(order: HotelPurchaseOrder, receipts: List<HotelPurchaseGoodsReceipt>) {
        val current = get(order.id)
        require(!current.archived) { "Order already approved" }
        require(receipts.isNotEmpty()) { "Record the delivery before approving" }
        HotelPurchaseGoodsReceiptPolicy.validateHistory(order, receipts)
        require(order.status == "PLACED") { "Only placed orders can be approved" }
        require(invoiceEvidence.isVerified(order.id)) { "Verify the supplier invoice before approving" }
        val remaining = HotelPurchaseGoodsReceiptPolicy.remaining(order, receipts)
        val shortage = remaining.values.any { it > 0.000001 }
        if (shortage) {
            require(current.resolution == "CANCELLED_BALANCE" && current.resolutionNote.isNotBlank()) {
                "Shortages remain. Record the corrected invoice or cancellation with a note before approval."
            }
        }
        save(current.copy(
            approvedAtMillis = System.currentTimeMillis(),
            events = current.events + HotelPurchaseIssueEvent(System.currentTimeMillis(), "APPROVED", "Goods receipt and supplier invoice verified; accounting remains manual")
        ))
    }

    private fun save(record: HotelPurchaseReceivingWorkflow) {
        val updated = all().filterNot { it.orderId == record.orderId } + record
        val array = JSONArray()
        updated.forEach { value ->
            array.put(JSONObject().apply {
                put("orderId", value.orderId)
                put("resolution", value.resolution)
                put("resolutionNote", value.resolutionNote)
                put("approvedAtMillis", value.approvedAtMillis)
                put("events", JSONArray().apply {
                    value.events.forEach { e ->
                        put(JSONObject().apply {
                            put("atMillis", e.atMillis)
                            put("action", e.action)
                            put("note", e.note)
                        })
                    }
                })
            })
        }
        check(prefs.edit().putString("records", array.toString()).commit()) { "Could not save order workflow" }
    }
}
