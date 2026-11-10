package com.example.bookingregister.finance.dev

/**
 * A supplementary receipt only records quantities that were physically counted today.
 * Original purchase orders and all older goods receipts are left unchanged.
 * Selected lines are assumed fully received for their currently outstanding quantity.
 */
object HotelPurchaseRemainingGoodsPolicy {
    private const val EPSILON = 0.000001

    fun outstanding(
        order: HotelPurchaseOrder,
        prior: List<HotelPurchaseGoodsReceipt>
    ): List<HotelPurchaseOrderLine> {
        HotelPurchaseGoodsReceiptPolicy.validateHistory(order, prior)
        val balances = HotelPurchaseGoodsReceiptPolicy.remaining(order, prior)
        return order.lines.filter { (balances[it.itemId] ?: 0.0) > EPSILON }
    }

    fun createSupplementaryReceipt(
        order: HotelPurchaseOrder,
        prior: List<HotelPurchaseGoodsReceipt>,
        selectedItemIds: Set<String>,
        receiptId: String,
        atMillis: Long,
        note: String
    ): HotelPurchaseGoodsReceipt {
        require(order.status == "PLACED") { "Only placed orders can receive goods" }
        require(receiptId.isNotBlank() && atMillis > 0) { "A valid receipt identity and time are required" }
        require(selectedItemIds.isNotEmpty()) { "Tick at least one item actually received" }
        val balances = HotelPurchaseGoodsReceiptPolicy.remaining(order, prior)
        val lines = outstanding(order, prior)
        require(selectedItemIds.all { id -> lines.any { it.itemId == id } }) {
            "The selected item is no longer outstanding"
        }
        val receivedLines = lines.filter { it.itemId in selectedItemIds }.map { item ->
            HotelPurchaseGoodsReceiptLine(
                item.itemId, item.itemName, item.unit, balances.getValue(item.itemId)
            )
        }
        val receipt = HotelPurchaseGoodsReceipt(receiptId, order.id, atMillis, receivedLines, note.trim())
        HotelPurchaseGoodsReceiptPolicy.validate(order, prior, receipt)
        return receipt
    }

    fun isFullyDelivered(order: HotelPurchaseOrder, receipts: List<HotelPurchaseGoodsReceipt>): Boolean =
        HotelPurchaseGoodsReceiptPolicy.remaining(order, receipts).values.all { it <= EPSILON }
}
