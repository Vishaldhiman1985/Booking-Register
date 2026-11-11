package com.example.bookingregister.finance.dev

import java.util.Locale

/** Read-only catalog diagnostics. Never mutates catalog IDs, inventory, or accounting entries. */
internal data class FinanceQualityGroup(val id: String, val name: String)
internal data class FinanceQualityItem(
    val id: String, val groupId: String, val name: String, val unit: String, val active: Boolean
)
internal data class FinanceQualityType(
    val code: String, val name: String, val stockable: Boolean, val groupIds: Set<String>
)
internal data class FinanceQualityIssue(
    val kind: String,
    val title: String,
    val detail: String,
    val priority: Int,
    val itemId: String? = null,
    val groupId: String? = null,
    val typeCode: String? = null
)

/**
 * Read-only checks: these are REVIEW PROMPTS, not proof a recorded unit is wrong.
 * Piece can mean a bottle/pack; mass can sometimes be right for dairy. Never auto-correct.
 */
internal object FinanceCatalogQualityRules {
    fun inspect(
        groups: List<FinanceQualityGroup>,
        items: List<FinanceQualityItem>,
        types: List<FinanceQualityType>
    ): List<FinanceQualityIssue> {
        val results = mutableListOf<FinanceQualityIssue>()
        val validGroups = groups.map { it.id }.toSet()
        val activeItems = items.filter { it.active }
        val activeByGroup = activeItems.groupBy { it.groupId }

        types.filter { it.stockable }.forEach { type ->
            if (type.groupIds.isEmpty()) {
                results += FinanceQualityIssue("Missing link", "No item groups linked to ${type.name}",
                    "Stock tracking is enabled for ${type.code}, but no operational item group is linked.",
                    0, typeCode = type.code)
            }
            type.groupIds.filter { it !in validGroups }.forEach { unknown ->
                results += FinanceQualityIssue("Missing link", "Unknown group in ${type.name}",
                    "The saved group ID '$unknown' is not active or has been archived. Review the expense type settings.",
                    0, typeCode = type.code)
            }
            type.groupIds.filter { it in validGroups && activeByGroup[it].isNullOrEmpty() }.forEach { empty ->
                val name = groups.first { it.id == empty }.name
                results += FinanceQualityIssue("Missing items", "No active items in $name",
                    "${type.name} is stockable and links this empty group. Add suitable items or unlink it.",
                    1, groupId = empty, typeCode = type.code)
            }
        }

        activeItems.filter { it.groupId !in validGroups }.forEach { item ->
            results += FinanceQualityIssue("Uncategorized", "Item has an inactive or missing group",
                "${item.name} (${item.id}) has group '${item.groupId}'. Move it into an active operational group.",
                0, itemId = item.id, groupId = item.groupId)
        }

        activeItems.groupBy { it.groupId to normalize(it.name) }
            .filter { (key, duplicates) -> key.second.isNotBlank() && duplicates.map { it.id }.distinct().size > 1 }
            .forEach { (key, duplicates) ->
                val categoryName = groups.firstOrNull { it.id == key.first }?.name ?: key.first
                results += FinanceQualityIssue("Duplicate", "Possible duplicate: ${duplicates.first().name}",
                    "${duplicates.size} active catalog IDs under $categoryName have the same normalized name. Review; do not merge IDs with history.",
                    1, itemId = duplicates.first().id, groupId = key.first)
            }

        activeItems.filter { reviewUnit(it.name, it.unit) }.forEach { item ->
            results += FinanceQualityIssue("Unit review", "Check unit: ${item.name}",
                "Current stock unit '${item.unit}'. Check whether stock is counted by volume, weight or sealed pack; no change is applied.",
                2, itemId = item.id, groupId = item.groupId)
        }
        return results.sortedWith(compareBy<FinanceQualityIssue> { it.priority }
            .thenBy { it.kind }.thenBy { it.title.lowercase(Locale.ROOT) })
    }

    internal fun reviewUnit(name: String, unit: String): Boolean {
        val n = name.trim().lowercase(Locale.ROOT)
        val u = unit.trim().lowercase(Locale.ROOT)
        val liquidMilk = n in setOf("milk full cream", "milk toned", "milk double toned", "cow milk", "buffalo milk", "milk skimmed")
        if (liquidMilk && u == "kg") return true
        val liquids = listOf(
            "floor cleaner", "bathroom cleaner", "toilet cleaner", "glass cleaner",
            "dishwashing liquid", "laundry detergent liquid", "fabric softener", "liquid soap",
            "handwash refill", "sanitizer refill", "carpet shampoo", "phenyl",
            "bleach", "pool algaecide", "pool ph minus", "pool ph plus"
        )
        return u == "piece" && liquids.any { n == it || n.startsWith("$it ") }
    }

    private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
}
