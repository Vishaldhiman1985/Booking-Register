package com.example.bookingregister.finance.dev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One catalog for Create Order, Manage Items and Monthly Stock. Built-in lists are read-only defaults. */
class HotelPurchaseCustomItemStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("finance_dev_custom_purchase_items_v1", Context.MODE_PRIVATE)
    private fun customRaw() = JSONArray(prefs.getString("items", "[]") ?: "[]")
    private fun overrides() = JSONObject(prefs.getString("overrides_v2", "{}") ?: "{}")

    fun all(): List<HotelPurchaseItem> {
        val array = customRaw()
        return (0 until array.length()).mapNotNull { i ->
            runCatching { fromJson(array.getJSONObject(i)) }.getOrNull()
        }
    }

    private fun defaults(): List<HotelPurchaseItem> {
        val originals = HotelPurchaseItems.items.map { original ->
            val master = HotelPurchaseMasterCatalog.items.firstOrNull {
                it.categoryId == original.categoryId && it.name.equals(original.name, ignoreCase = true)
            }
            original.copy(synonyms = (original.synonyms + (master?.synonyms ?: emptyList())).distinct())
        }
        val others = HotelPurchaseMasterCatalog.items.filter { master ->
            HotelPurchaseItems.items.none {
                it.categoryId == master.categoryId && it.name.equals(master.name, ignoreCase = true)
            }
        }
        return (originals + others).distinctBy { it.id }
    }

    fun catalogItems(includeArchived: Boolean = false): List<HotelPurchaseItem> {
        val changes = overrides()
        return (defaults().map { original ->
            changes.optJSONObject(original.id)?.let { edit ->
                original.copy(
                    categoryId = edit.optString("categoryId", original.categoryId),
                    name = edit.optString("name", original.name),
                    unit = edit.optString("unit", original.unit),
                    active = edit.optBoolean("active", original.active),
                    synonyms = edit.optJSONArray("synonyms")?.let(::aliases) ?: original.synonyms
                )
            } ?: original
        } + all()).distinctBy { it.id }.filter { includeArchived || it.active }
    }

    fun add(name: String, categoryId: String, unit: String, synonyms: List<String>): HotelPurchaseItem {
        validate(name, categoryId, unit, null)
        val item = HotelPurchaseItem(
            id = UUID.randomUUID().toString(), categoryId = categoryId,
            name = name.trim(), unit = unit.trim(), synonyms = cleanAliases(synonyms)
        )
        val next = customRaw().put(toJson(item))
        saveCustom(next)
        return item
    }

    fun edit(id: String, name: String, categoryId: String, unit: String, synonyms: List<String>) {
        val old = catalogItems(true).firstOrNull { it.id == id } ?: error("Item not found")
        validate(name, categoryId, unit, id)
        // Stock values and purchase receipts keep their original item ID and unit.
        if (hasHistory(id) && (!old.unit.equals(unit.trim(), ignoreCase = true) || old.name != name.trim())) {
            error("Name or unit cannot change after an item is used in orders, receipts or stock. Archive it and create a corrected item; category and aliases remain editable.")
        }
        val newer = old.copy(name = name.trim(), categoryId = categoryId, unit = unit.trim(), synonyms = cleanAliases(synonyms))
        persistEdit(newer)
    }

    fun archive(id: String) = setActive(id, false)
    fun restore(id: String) = setActive(id, true)

    private fun setActive(id: String, active: Boolean) {
        val old = catalogItems(true).firstOrNull { it.id == id } ?: error("Item not found")
        if (active) validate(old.name, old.categoryId, old.unit, id)
        persistEdit(old.copy(active = active))
    }

    private fun persistEdit(item: HotelPurchaseItem) {
        if (defaults().any { it.id == item.id }) {
            val edit = overrides()
            edit.put(item.id, toJson(item))
            check(prefs.edit().putString("overrides_v2", edit.toString()).commit()) { "Could not save item edit" }
        } else {
            val old = customRaw()
            val next = JSONArray()
            var found = false
            for (i in 0 until old.length()) {
                val obj = old.getJSONObject(i)
                if (obj.getString("id") == item.id) { next.put(toJson(item)); found = true }
                else next.put(obj)
            }
            require(found) { "Custom item missing" }
            saveCustom(next)
        }
    }

    private fun validate(name: String, categoryId: String, unit: String, exceptId: String?) {
        val n = name.trim()
        require(n.isNotEmpty() && n.length <= 120) { "Enter an item name up to 120 characters" }
        require(unit.isNotBlank()) { "Select unit" }
        require(HotelPurchaseCategoryStore(context).all().any { it.id == categoryId }) { "Select an active category" }
        require(catalogItems(true).none {
            it.id != exceptId && it.active && it.categoryId == categoryId && it.name.equals(n, true)
        }) { "Item already exists in this category" }
    }

    private fun hasHistory(id: String): Boolean {
        if (HotelPurchaseOrderStore(context).all().any { order -> order.lines.any { it.itemId == id } }) return true
        if (HotelPurchaseGoodsReceiptStore(context).all().any { receipt -> receipt.lines.any { it.itemId == id } }) return true
        if (HotelMonthlyStockStore(context).all().any { month ->
                (month.opening + month.purchased + month.closing).any { it.itemId == id }
            }) return true
        // Opening stock may exist before any approved month.
        val raw = context.getSharedPreferences("finance_dev_monthly_stock_v1", Context.MODE_PRIVATE)
            .getString("initialOpenings", "{}") ?: "{}"
        if (raw.contains("\"itemId\":\"$id\"")) return true
        return false
    }

    private fun fromJson(o: JSONObject): HotelPurchaseItem = HotelPurchaseItem(
        id = o.getString("id"), categoryId = o.getString("categoryId"),
        name = o.getString("name"), unit = o.getString("unit"),
        active = o.optBoolean("active", true),
        synonyms = aliases(o.optJSONArray("synonyms") ?: JSONArray())
    )
    private fun toJson(x: HotelPurchaseItem) = JSONObject().apply {
        put("id", x.id); put("categoryId", x.categoryId)
        put("name", x.name); put("unit", x.unit); put("active", x.active)
        put("synonyms", JSONArray(x.synonyms))
    }
    private fun aliases(a: JSONArray): List<String> = (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
    private fun cleanAliases(a: List<String>) = a.map(String::trim).filter(String::isNotEmpty).distinct()
    private fun saveCustom(a: JSONArray) {
        check(prefs.edit().putString("items", a.toString()).commit()) { "Could not save custom item" }
    }
}
