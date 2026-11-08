package com.example.bookingregister.finance.dev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class HotelPurchaseVendorStore(context: Context) {

    private val prefs = context.getSharedPreferences(
        "finance_dev_purchase_vendors_v1",
        Context.MODE_PRIVATE
    )

    fun all(): List<HotelPurchaseVendor> {
        val raw = prefs.getString("vendors", "[]") ?: "[]"
        val array = JSONArray(raw)

        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val categories = obj.getJSONArray("categories")

            HotelPurchaseVendor(
                id = obj.getString("id"),
                name = obj.getString("name"),
                phone = obj.optString("phone"),
                suppliedCategoryIds = (0 until categories.length())
                    .map { categories.getString(it) }
                    .toSet()
            )
        }
    }

    fun save(vendor: HotelPurchaseVendor) {
        require(vendor.id.isNotBlank())
        require(vendor.name.isNotBlank())

        val vendors = all().filterNot { it.id == vendor.id } + vendor
        val array = JSONArray()

        vendors.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("phone", item.phone)
                put(
                    "categories",
                    JSONArray(item.suppliedCategoryIds.sorted())
                )
            })
        }

        check(prefs.edit().putString("vendors", array.toString()).commit()) {
            "Unable to save purchasing vendor"
        }
    }
}
