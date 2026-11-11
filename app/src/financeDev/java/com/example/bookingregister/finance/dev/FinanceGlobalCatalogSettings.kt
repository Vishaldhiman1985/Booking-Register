package com.example.bookingregister.finance.dev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A non-destructive overlay on the existing immutable CA master and Purchase item catalog.
 * Codes / item IDs are never renamed or migrated by this class.
 */
internal data class FinanceCatalogTypeConfig(
    val stockable: Boolean,
    val department: String,
    val groupIds: Set<String>
)

internal object FinanceGlobalCatalogDefaults {
    val departments = listOf(
        "Kitchen", "F&B", "Bar", "Housekeeping", "Rooms",
        "Maintenance", "Administration", "Sales & Marketing", "Services", "Shared Operations"
    )

    // These are contextual links, not duplicate physical item records. A group may serve
    // multiple accounting types; the chosen expense type and department remain on the bill.
    val groupsByType: Map<String, Set<String>> = mapOf(
        "HE0201" to setOf("grocery", "vegetables", "dairy", "meat", "bakery", "beverages", "spices", "frozen"),
        "HE0202" to setOf("beverages"),
        "HE0203" to setOf("alcoholic"),
        "HE0204" to setOf("grocery", "bakery", "beverages", "frozen"),
        "HE0301" to setOf("kitchen"),
        "HE0302" to setOf("cleaning"),
        "HE0303" to setOf("disposables"),
        "HE0304" to setOf("kitchen"),
        "HE0305" to setOf("laundry", "linen"),
        "HE0310" to setOf("services"),
        "HE0401" to setOf("laundry", "linen"),
        "HE0402" to setOf("guest"),
        "HE0403" to setOf("cleaning", "housekeeping"),
        "HE0404" to setOf("beverages", "dairy", "guest"),
        "HE0405" to setOf("linen"),
        "HE0407" to setOf("guest"),
        "HE0411" to setOf("decor", "garden"),
        "HE0110" to setOf("uniforms"),
        "HE0702" to setOf("hardware"),
        "HE0703" to setOf("plumbing"),
        "HE0704" to setOf("electrical"),
        "HE0707" to setOf("kitchen"),
        "HE0708" to setOf("pool"),
        "HE0709" to setOf("garden"),
        "HE0712" to setOf("hardware", "electrical", "plumbing"),
        "HE0713" to setOf("furniture"),
        "HE0716" to setOf("hardware"),
        "HE1011" to setOf("it"),
        "HE1106" to setOf("office"),
        "HE1109" to setOf("office"),
        "HE1307" to setOf("housekeeping"),
        "HE1701" to setOf("guest"),
        "HE1703" to setOf("disposables", "decor"),
        "HE1708" to setOf("beverages", "grocery")
    )

    // Limited to the six stock types agreed for the first implementation. Other types can
    // be enabled explicitly from the central catalog, after choosing their groups.
    val initiallyStockable = setOf("HE0201", "HE0202", "HE0203", "HE0402", "HE0403", "HE0404")

    fun defaultDepartment(typeCode: String): String = when (typeCode) {
        "HE0201", "HE0204" -> "Kitchen"
        "HE0202" -> "F&B"
        "HE0203" -> "Bar"
        "HE0402", "HE0403", "HE0404", "HE0405", "HE0401", "HE0407" -> "Housekeeping"
        else -> when {
            typeCode.startsWith("HE03") -> "F&B"
            typeCode.startsWith("HE04") -> "Rooms"
            typeCode.startsWith("HE05") -> "Rooms"
            typeCode.startsWith("HE07") -> "Maintenance"
            typeCode.startsWith("HE08") -> "Rooms"
            typeCode.startsWith("HE09") -> "Sales & Marketing"
            typeCode.startsWith("HE17") -> "Services"
            typeCode.startsWith("HE01") || typeCode.startsWith("HE10") ||
                typeCode.startsWith("HE11") || typeCode.startsWith("HE12") ||
                typeCode.startsWith("HE15") || typeCode.startsWith("HE16") -> "Administration"
            else -> "Shared Operations"
        }
    }

    fun forType(typeCode: String): FinanceCatalogTypeConfig = FinanceCatalogTypeConfig(
        stockable = typeCode in initiallyStockable,
        department = defaultDepartment(typeCode),
        groupIds = groupsByType[typeCode].orEmpty()
    )
}

/** Only saves optional catalog classifications. No stock, receipt, or accounting writes. */
internal class FinanceGlobalCatalogSettings(context: Context) {
    private val prefs = context.getSharedPreferences("finance_dev_global_catalog_settings_v1", Context.MODE_PRIVATE)

    fun forType(typeCode: String): FinanceCatalogTypeConfig {
        val fallback = FinanceGlobalCatalogDefaults.forType(typeCode)
        val obj = runCatching {
            JSONObject(prefs.getString("type_overrides", "{}") ?: "{}")
                .optJSONObject(typeCode)
        }.getOrNull() ?: return fallback
        val groups = obj.optJSONArray("groupIds")?.let { a ->
            (0 until a.length()).mapNotNull { i -> a.optString(i).takeIf(String::isNotBlank) }.toSet()
        } ?: fallback.groupIds
        return FinanceCatalogTypeConfig(
            stockable = obj.optBoolean("stockable", fallback.stockable),
            department = obj.optString("department", fallback.department).takeIf { it in FinanceGlobalCatalogDefaults.departments }
                ?: fallback.department,
            groupIds = groups
        )
    }

    fun save(typeCode: String, stockable: Boolean, department: String, groupIds: Set<String>) {
        require(Regex("HE[0-9]{4}").matches(typeCode)) { "Invalid expense type code" }
        require(department in FinanceGlobalCatalogDefaults.departments) { "Choose a valid department" }
        require(!stockable || groupIds.isNotEmpty()) { "Select at least one item group for stock tracking" }
        val root = JSONObject(prefs.getString("type_overrides", "{}") ?: "{}")
        root.put(typeCode, JSONObject().apply {
            put("stockable", stockable)
            put("department", department)
            put("groupIds", JSONArray(groupIds.sorted()))
        })
        check(prefs.edit().putString("type_overrides", root.toString()).commit()) {
            "Unable to save catalog settings"
        }
    }
}
