package com.example.bookingregister.finance.dev

data class HotelPurchaseOrderLine(
    val itemId: String,
    val itemName: String,
    val quantity: Double,
    val unit: String
)

data class HotelPurchaseOrder(
    val id: String,
    val vendorId: String,
    val vendorName: String,
    val createdAtMillis: Long,
    val lines: List<HotelPurchaseOrderLine>,
    val status: String = "DRAFT"
)

object HotelPurchaseOrderPolicy {

    /** Allowed transitions for the Finance DEV purchase draft store. */
    fun validateUpdate(previous: HotelPurchaseOrder?, next: HotelPurchaseOrder) {
        validate(next)
        if (previous == null) return
        require(previous.id == next.id) { "Purchase order ID cannot change" }
        require(previous.status == "DRAFT") { "Placed orders cannot be edited" }
        require(next.createdAtMillis == previous.createdAtMillis) { "Creation date cannot change" }
    }

    fun validate(order: HotelPurchaseOrder) {
        require(order.id.isNotBlank()) {
            "Purchase order ID is required"
        }
        require(order.vendorId.isNotBlank()) {
            "Vendor is required"
        }
        require(order.lines.isNotEmpty()) {
            "Select at least one item"
        }
        require(order.status == "DRAFT" || order.status == "PLACED") {
            "Unknown purchase order status"
        }
        require(order.lines.map { it.itemId }.distinct().size == order.lines.size) {
            "Duplicate item in purchase order"
        }
        require(order.lines.all {
            it.itemId.isNotBlank() &&
                it.itemName.isNotBlank() &&
                it.unit.isNotBlank() &&
                it.quantity.isFinite() &&
                it.quantity > 0
        }) {
            "Every item must have a valid quantity"
        }
    }
}
