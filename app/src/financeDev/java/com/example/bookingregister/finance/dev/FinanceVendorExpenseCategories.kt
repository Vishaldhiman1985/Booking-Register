package com.example.bookingregister.finance.dev

import android.content.Context

/** Property-scoped choices about vendor classification; does not edit old bills or purchasing suppliers. */
internal class FinanceVendorExpenseCategories(context: Context, private val propertyId: String) {
    private val prefs = context.getSharedPreferences("finance_dev_vendor_expense_categories_v1", Context.MODE_PRIVATE)
    private fun key(vendorId: String) = "property:${propertyId.length}:$propertyId:vendor:${vendorId.length}:$vendorId"

    /** Null = not configured yet; empty is not a valid saved assignment. */
    fun allowed(vendorId: String): Set<String>? {
        val k = key(vendorId)
        return if (prefs.contains(k)) (prefs.getStringSet(k, null) ?: emptySet()).toSet() else null
    }

    fun assign(vendorId: String, categoryCodes: Set<String>) {
        require(vendorId.isNotBlank()) { "Choose a registered vendor" }
        require(categoryCodes.isNotEmpty()) { "Select at least one category for this vendor" }
        require(categoryCodes.none { it == FinanceExpenseCategoryRules.STAFF_ONLY }) {
            "Staff & Employee Benefits belongs to Manpower"
        }
        check(prefs.edit().putStringSet(key(vendorId), categoryCodes.toMutableSet()).commit()) {
            "Could not save vendor categories"
        }
    }
}

/** App-side policy used before the form can record any new manual expense. */
internal object FinanceExpenseCategoryRules {
    const val STAFF_ONLY = "HC01"
    fun permitted(categoryCode: String, registeredVendorCategories: Set<String>?): Boolean =
        categoryCode != STAFF_ONLY &&
            (registeredVendorCategories == null || categoryCode in registeredVendorCategories)
}
