package com.example.bookingregister.finance.dev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class HotelPurchaseCustomItemStore(context: Context) {

    private val prefs = context.getSharedPreferences(
        "finance_dev_custom_purchase_items_v1",
        Context.MODE_PRIVATE
    )

    fun all(): List<HotelPurchaseItem> {
        val array = JSONArray(prefs.getString("items", "[]"))
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val obj = array.getJSONObject(index)
                val aliases = obj.optJSONArray("synonyms") ?: JSONArray()

                HotelPurchaseItem(
                    id = obj.getString("id"),
                    categoryId = obj.getString("categoryId"),
                    name = obj.getString("name"),
                    unit = obj.getString("unit"),
                    active = obj.optBoolean("active", true),
                    synonyms = (0 until aliases.length()).map {
                        aliases.getString(it)
                    }
                )
            }.getOrNull()
        }
    }

    fun add(
        name: String,
        categoryId: String,
        unit: String,
        synonyms: List<String>
    ): HotelPurchaseItem {
        require(name.isNotBlank())
        require(categoryId.isNotBlank())
        require(unit.isNotBlank())

        val item = HotelPurchaseItem(
            id = UUID.randomUUID().toString(),
            categoryId = categoryId,
            name = name.trim(),
            unit = unit.trim(),
            synonyms = synonyms.map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        )

        val array = JSONArray(prefs.getString("items", "[]"))
        array.put(JSONObject().apply {
            put("id", item.id)
            put("categoryId", item.categoryId)
            put("name", item.name)
            put("unit", item.unit)
            put("active", item.active)
            put("synonyms", JSONArray(item.synonyms))
        })

        check(prefs.edit().putString("items", array.toString()).commit()) {
            "Could not save custom purchase item"
        }

        return item
    }
}