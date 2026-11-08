package com.example.bookingregister.finance.dev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class HotelPurchaseCategoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(
        "finance_dev_purchase_categories_v1",
        Context.MODE_PRIVATE
    )

    fun all(): List<HotelPurchaseCategory> {
        val builtIn = HotelPurchaseCatalog.categories
        val custom = customCategories()
        return builtIn + custom.filter { category ->
            builtIn.none { it.id == category.id }
        }
    }

    fun customCategories(): List<HotelPurchaseCategory> {
        val array = JSONArray(prefs.getString("categories", "[]") ?: "[]")

        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val obj = array.getJSONObject(index)
                HotelPurchaseCategory(
                    id = obj.getString("id"),
                    name = obj.getString("name")
                )
            }.getOrNull()
        }
    }

    fun add(name: String): HotelPurchaseCategory {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) {
            "Enter category name"
        }

        require(all().none {
            it.name.equals(cleanName, ignoreCase = true)
        }) {
            "Category already exists"
        }

        val category = HotelPurchaseCategory(
            id = "custom_" + UUID.randomUUID().toString(),
            name = cleanName
        )

        val array = JSONArray(
            prefs.getString("categories", "[]") ?: "[]"
        )

        array.put(JSONObject().apply {
            put("id", category.id)
            put("name", category.name)
        })

        check(
            prefs.edit()
                .putString("categories", array.toString())
                .commit()
        ) {
            "Could not save category"
        }

        return category
    }

    fun rename(id: String, newName: String) {
        val cleanName = newName.trim()

        require(cleanName.isNotEmpty()) {
            "Enter category name"
        }

        require(all().none {
            it.id != id &&
                it.name.equals(cleanName, ignoreCase = true)
        }) {
            "Category already exists"
        }

        val categories = customCategories()
        require(categories.any { it.id == id }) {
            "Only custom categories can be renamed"
        }

        val array = JSONArray()

        categories.forEach { category ->
            array.put(JSONObject().apply {
                put("id", category.id)
                put(
                    "name",
                    if (category.id == id) cleanName else category.name
                )
            })
        }

        check(
            prefs.edit()
                .putString("categories", array.toString())
                .commit()
        ) {
            "Could not rename category"
        }
    }
}