package com.example.bookingregister.finance.dev

data class HotelPurchaseCategory(
    val id: String,
    val name: String
)

data class HotelPurchaseItem(
    val id: String,
    val categoryId: String,
    val name: String,
    val unit: String,
    val active: Boolean = true,
    val synonyms: List<String> = emptyList()
)

data class HotelPurchaseVendor(
    val id: String,
    val name: String,
    val phone: String,
    val suppliedCategoryIds: Set<String>
)

object HotelPurchasePolicy {

    fun itemsForVendor(
        vendor: HotelPurchaseVendor,
        items: List<HotelPurchaseItem>
    ): List<HotelPurchaseItem> {
        return items.filter {
            it.active &&
            it.categoryId in vendor.suppliedCategoryIds
        }
    }

    fun canOrder(
        vendor: HotelPurchaseVendor,
        item: HotelPurchaseItem
    ): Boolean {
        return item.active &&
            item.categoryId in vendor.suppliedCategoryIds
    }
}
