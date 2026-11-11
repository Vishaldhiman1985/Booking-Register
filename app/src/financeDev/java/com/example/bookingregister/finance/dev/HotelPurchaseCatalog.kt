package com.example.bookingregister.finance.dev

object HotelPurchaseCatalog {

    val categories = listOf(
        HotelPurchaseCategory("grocery", "Grocery & Dry Rations"),
        HotelPurchaseCategory("vegetables", "Vegetables & Fruits"),
        HotelPurchaseCategory("dairy", "Dairy & Eggs"),
        HotelPurchaseCategory("meat", "Meat, Poultry & Seafood"),
        HotelPurchaseCategory("bakery", "Bakery & Breads"),
        HotelPurchaseCategory("beverages", "Tea, Coffee & Beverages"),
        HotelPurchaseCategory("spices", "Spices, Oils & Condiments"),
        HotelPurchaseCategory("frozen", "Frozen & Ready-to-Cook"),
        HotelPurchaseCategory("kitchen", "Kitchen Equipment & Utensils"),
        HotelPurchaseCategory("disposables", "Packaging & Disposables"),
        HotelPurchaseCategory("cleaning", "Cleaning Chemicals"),
        HotelPurchaseCategory("housekeeping", "Housekeeping Supplies"),
        HotelPurchaseCategory("guest", "Guest Amenities & Toiletries"),
        HotelPurchaseCategory("linen", "Linen, Towels & Bedding"),
        HotelPurchaseCategory("laundry", "Laundry Supplies & Services"),
        HotelPurchaseCategory("uniforms", "Staff Uniforms & Safety"),
        HotelPurchaseCategory("electrical", "Electrical & Lighting"),
        HotelPurchaseCategory("plumbing", "Plumbing & Sanitary"),
        HotelPurchaseCategory("hardware", "Hardware & Maintenance"),
        HotelPurchaseCategory("furniture", "Furniture & Fixtures"),
        HotelPurchaseCategory("decor", "Decor & Soft Furnishings"),
        HotelPurchaseCategory("pool", "Swimming Pool Supplies"),
        HotelPurchaseCategory("garden", "Gardening & Landscaping"),
        HotelPurchaseCategory("office", "Office & Stationery"),
        HotelPurchaseCategory("it", "IT & Electronics"),
        HotelPurchaseCategory("services", "External Services & Events"),
        HotelPurchaseCategory("alcoholic", "Alcoholic Beverages")
    )

    fun categoryById(id: String): HotelPurchaseCategory? {
        return categories.firstOrNull { it.id == id }
    }
}
