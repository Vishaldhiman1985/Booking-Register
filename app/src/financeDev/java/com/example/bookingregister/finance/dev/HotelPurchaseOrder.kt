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
        require(order.lines.all {
            it.itemId.isNotBlank() &&
                it.quantity.isFinite() &&
                it.quantity > 0
        }) {
            "Every item must have a valid quantity"
        }
    }
}
