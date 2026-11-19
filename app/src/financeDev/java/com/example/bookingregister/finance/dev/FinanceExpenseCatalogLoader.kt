package com.example.bookingregister.finance.dev

import android.content.Context
import org.json.JSONObject

/** Reads the immutable Finance DEV seed master. No network or production database access. */
internal object FinanceExpenseCatalogLoader {
    fun load(context: Context): SmartExpenseCatalog {
        val source = context.assets.open("finance_expense_master_v1.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(source)
        val categories = root.getJSONArray("categories")
        val mapped = (0 until categories.length()).map { categoryIndex ->
            val category = categories.getJSONObject(categoryIndex)
            val types = category.getJSONArray("types")
            SmartExpenseCategory(
                code = category.getString("code"),
                name = category.getString("name"),
                visibleByDefault = category.getBoolean("visible_in_small_hotel_preset"),
                types = (0 until types.length()).map { typeIndex ->
                    val type = types.getJSONObject(typeIndex)
                    val aliases = type.getJSONArray("aliases")
                    SmartExpenseType(
                        code = type.getString("code"),
                        name = type.getString("name"),
                        aliases = (0 until aliases.length()).map(aliases::getString),
                        enabledByDefault = type.getBoolean("enabled_by_default"),
                        requiresAccountantReview = type.getBoolean("requires_accountant_review"),
                        accountingHint = type.getString("statutory_mapping_hint")
                    )
                }
            )
        }
        val clarifications = root.getJSONArray("search_disambiguation")
        return SmartExpenseCatalog(mapped, (0 until clarifications.length()).map { i ->
            val obj = clarifications.getJSONObject(i)
            val choices = obj.getJSONArray("choices")
            SmartExpenseClarification(obj.getString("query"), (0 until choices.length()).map { j ->
                val item = choices.getJSONObject(j)
                SmartExpenseChoice(item.getString("label"), item.getString("type_code"))
            })
        }).also { it.validate() }
    }
}

/** Display preferences only; accounting codes and historical entries are never changed. */
internal class FinanceExpenseVisibility(
    context: Context,
    private val propertyId: String = FinanceDemoDraftPolicy.PROPERTY
) {
    private val prefs = context.getSharedPreferences("finance_dev_expense_visibility_v1", Context.MODE_PRIVATE)
    private fun scopeKey(name: String) = "property:${propertyId.length}:$propertyId:$name"

    fun categoryVisible(category: SmartExpenseCategory): Boolean =
        prefs.getBoolean(scopeKey("category_${category.code}"),
            prefs.getBoolean("category_${category.code}", category.visibleByDefault))

    fun typeVisible(category: SmartExpenseCategory, type: SmartExpenseType): Boolean =
        categoryVisible(category) && prefs.getBoolean(scopeKey("type_${type.code}"),
            prefs.getBoolean("type_${type.code}", type.enabledByDefault))

    fun setCategoryVisible(code: String, visible: Boolean) {
        check(prefs.edit().putBoolean(scopeKey("category_$code"), visible).commit()) {
            "Could not save property category visibility"
        }
    }

    fun setTypeVisible(code: String, visible: Boolean) {
        check(prefs.edit().putBoolean(scopeKey("type_$code"), visible).commit()) {
            "Could not save property type visibility"
        }
    }
}
