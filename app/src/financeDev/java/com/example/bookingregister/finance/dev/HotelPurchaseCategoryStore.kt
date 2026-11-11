package com.example.bookingregister.finance.dev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Keeps the factory catalog immutable; only user edits are persisted. */
class HotelPurchaseCategoryStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("finance_dev_purchase_categories_v1", Context.MODE_PRIVATE)

    private fun customRaw(): JSONArray = JSONArray(prefs.getString("categories", "[]") ?: "[]")
    private fun overrides(): JSONObject = JSONObject(prefs.getString("overrides_v2", "{}") ?: "{}")

    fun customCategories(): List<HotelPurchaseCategory> = customWithStates().map { it.first }

    private fun customWithStates(): List<Pair<HotelPurchaseCategory, Boolean>> {
        val a = customRaw()
        return (0 until a.length()).mapNotNull { index ->
            runCatching {
                val obj = a.getJSONObject(index)
                HotelPurchaseCategory(obj.getString("id"), obj.getString("name")) to obj.optBoolean("active", true)
            }.getOrNull()
        }
    }

    fun allIncludingArchived(): List<Pair<HotelPurchaseCategory, Boolean>> {
        val edits = overrides()
        val builtIn = HotelPurchaseCatalog.categories.map { category ->
            val edit = edits.optJSONObject(category.id)
            category.copy(name = edit?.optString("name", category.name) ?: category.name) to
                (edit?.optBoolean("active", true) ?: true)
        }
        return builtIn + customWithStates().filter { custom -> builtIn.none { it.first.id == custom.first.id } }
    }

    fun all(): List<HotelPurchaseCategory> = allIncludingArchived().filter { it.second }.map { it.first }

    fun add(name: String): HotelPurchaseCategory {
        val clean = validName(name, null)
        val entry = HotelPurchaseCategory("custom_${UUID.randomUUID()}", clean)
        val a = customRaw()
        a.put(JSONObject().put("id", entry.id).put("name", entry.name).put("active", true))
        saveCustom(a)
        return entry
    }

    fun rename(id: String, newName: String) {
        val clean = validName(newName, id)
        require(allIncludingArchived().any { it.first.id == id }) { "Category not found" }
        if (HotelPurchaseCatalog.categories.any { it.id == id }) {
            val all = overrides()
            val edit = all.optJSONObject(id) ?: JSONObject()
            edit.put("name", clean)
            all.put(id, edit)
            saveOverrides(all)
        } else {
            val a = customRaw()
            val result = JSONArray()
            for (index in 0 until a.length()) {
                val obj = a.getJSONObject(index)
                if (obj.getString("id") == id) obj.put("name", clean)
                result.put(obj)
            }
            saveCustom(result)
        }
    }

    fun archive(id: String) = setActive(id, false)
    fun restore(id: String) = setActive(id, true)

    private fun setActive(id: String, active: Boolean) {
        val entry = allIncludingArchived().firstOrNull { it.first.id == id } ?: error("Category not found")
        if (!active) {
            require(HotelPurchaseCustomItemStore(context).catalogItems().none { it.categoryId == id }) {
                "Move or archive active items in '${entry.first.name}' before archiving this category"
            }
        } else {
            validName(entry.first.name, id)
        }
        if (HotelPurchaseCatalog.categories.any { it.id == id }) {
            val edits = overrides()
            val obj = edits.optJSONObject(id) ?: JSONObject()
            obj.put("active", active)
            edits.put(id, obj)
            saveOverrides(edits)
        } else {
            val old = customRaw()
            val next = JSONArray()
            for (i in 0 until old.length()) {
                val obj = old.getJSONObject(i)
                if (obj.getString("id") == id) obj.put("active", active)
                next.put(obj)
            }
            saveCustom(next)
        }
    }

    private fun validName(name: String, exceptId: String?): String {
        val value = name.trim()
        require(value.isNotEmpty() && value.length <= 80) { "Enter a category name up to 80 characters" }
        require(allIncludingArchived().none { it.first.id != exceptId && it.first.name.equals(value, true) }) {
            "Category name already exists"
        }
        return value
    }

    private fun saveCustom(a: JSONArray) {
        check(prefs.edit().putString("categories", a.toString()).commit()) { "Could not save categories" }
    }
    private fun saveOverrides(o: JSONObject) {
        check(prefs.edit().putString("overrides_v2", o.toString()).commit()) { "Could not save category edit" }
    }
}
