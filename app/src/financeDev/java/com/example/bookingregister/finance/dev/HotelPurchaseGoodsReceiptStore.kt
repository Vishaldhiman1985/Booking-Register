package com.example.bookingregister.finance.dev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A recorded delivery. Original purchase order quantities are never edited. */
data class HotelPurchaseGoodsReceiptLine(
    val itemId: String,
    val itemName: String,
    val unit: String,
    val receivedQuantity: Double
)

data class HotelPurchaseGoodsReceipt(
    val id: String,
    val orderId: String,
    val receivedAtMillis: Long,
    val lines: List<HotelPurchaseGoodsReceiptLine>,
    val note: String = ""
)

/** Quantity checks are separate from accounting; saving a receipt never creates an expense. */
object HotelPurchaseGoodsReceiptPolicy {
    private const val EPSILON = 0.000001

    /** Re-check persisted receipt history before approving an order. No writes occur here. */
    fun validateHistory(order: HotelPurchaseOrder, receipts: List<HotelPurchaseGoodsReceipt>) {
        HotelPurchaseOrderPolicy.validate(order)
        require(order.status == "PLACED") { "Only placed orders may be received" }
        require(receipts.map { it.id }.distinct().size == receipts.size) { "Duplicate goods receipt ID" }
        val accepted = mutableListOf<HotelPurchaseGoodsReceipt>()
        receipts.forEach { receipt ->
            validate(order, accepted, receipt)
            accepted.add(receipt)
        }
    }

    fun remaining(
        order: HotelPurchaseOrder,
        receipts: List<HotelPurchaseGoodsReceipt>
    ): Map<String, Double> {
        require(receipts.all { it.orderId == order.id }) { "Receipt belongs to a different order" }
        return order.lines.associate { ordered ->
            val received = receipts.sumOf { receipt ->
                receipt.lines.filter { it.itemId == ordered.itemId }
                    .sumOf { it.receivedQuantity }
            }
            ordered.itemId to (ordered.quantity - received).coerceAtLeast(0.0)
        }
    }

    fun validate(
        order: HotelPurchaseOrder,
        existing: List<HotelPurchaseGoodsReceipt>,
        receipt: HotelPurchaseGoodsReceipt
    ) {
        require(order.status == "PLACED") { "Only placed orders may be received" }
        HotelPurchaseOrderPolicy.validate(order)
        require(existing.none { it.id == receipt.id }) { "Duplicate goods receipt ID" }
        require(receipt.id.isNotBlank()) { "Receipt ID is required" }
        require(receipt.orderId == order.id) { "Receipt belongs to a different order" }
        require(receipt.receivedAtMillis > 0) { "Invalid receipt date" }
        require(receipt.lines.isNotEmpty()) { "Add received quantities" }
        require(receipt.lines.map { it.itemId }.distinct().size == receipt.lines.size) {
            "Duplicate items in receipt"
        }

        val orderedById = order.lines.associateBy { it.itemId }
        val outstanding = remaining(order, existing)
        require(receipt.lines.any { it.receivedQuantity > 0.0 }) {
            "Enter at least one received quantity"
        }
        receipt.lines.forEach { line ->
            val ordered = orderedById[line.itemId]
                ?: throw IllegalArgumentException("Item not found in the purchase order")
            require(line.itemName == ordered.itemName && line.unit == ordered.unit) {
                "Item details do not match the original order"
            }
            require(line.receivedQuantity.isFinite() && line.receivedQuantity >= 0.0) {
                "Invalid received quantity for ${line.itemName}"
            }
            require(line.receivedQuantity <= (outstanding[line.itemId] ?: 0.0) + EPSILON) {
                "${line.itemName}: received quantity exceeds the remaining order"
            }
        }
    }
}

/** Finance DEV-only, append-only local storage. No changes to existing PO preferences. */
class HotelPurchaseGoodsReceiptStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        "finance_dev_purchase_goods_receipts_v1",
        Context.MODE_PRIVATE
    )

    fun all(): List<HotelPurchaseGoodsReceipt> {
        val array = JSONArray(prefs.getString("receipts", "[]") ?: "[]")
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val linesArray = obj.getJSONArray("lines")
            HotelPurchaseGoodsReceipt(
                id = obj.getString("id"),
                orderId = obj.getString("orderId"),
                receivedAtMillis = obj.getLong("receivedAtMillis"),
                note = obj.optString("note", ""),
                lines = (0 until linesArray.length()).map { lineIndex ->
                    val line = linesArray.getJSONObject(lineIndex)
                    HotelPurchaseGoodsReceiptLine(
                        itemId = line.getString("itemId"),
                        itemName = line.getString("itemName"),
                        unit = line.getString("unit"),
                        receivedQuantity = line.getDouble("receivedQuantity")
                    )
                }
            )
        }.sortedBy { it.receivedAtMillis }
    }

    fun forOrder(orderId: String): List<HotelPurchaseGoodsReceipt> =
        all().filter { it.orderId == orderId }

    fun add(order: HotelPurchaseOrder, receipt: HotelPurchaseGoodsReceipt) {
        val previous = all()
        require(previous.none { it.id == receipt.id }) { "Receipt is already recorded" }
        HotelPurchaseGoodsReceiptPolicy.validate(
            order,
            previous.filter { it.orderId == order.id },
            receipt
        )
        val array = JSONArray()
        (previous + receipt).forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("orderId", entry.orderId)
                put("receivedAtMillis", entry.receivedAtMillis)
                put("note", entry.note)
                put("lines", JSONArray().apply {
                    entry.lines.forEach { line ->
                        put(JSONObject().apply {
                            put("itemId", line.itemId)
                            put("itemName", line.itemName)
                            put("unit", line.unit)
                            put("receivedQuantity", line.receivedQuantity)
                        })
                    }
                })
            })
        }
        check(prefs.edit().putString("receipts", array.toString()).commit()) {
            "Unable to save goods receipt"
        }
    }
}
