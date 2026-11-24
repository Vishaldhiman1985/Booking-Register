package com.example.bookingregister.finance.domain

/** Read-only permissions derived from the existing vendor master; no second master is created. */
object FinanceV25VendorSelectionPolicy {
    const val STAFF_CATEGORY = "HC01"

    /** Vendor master type codes are authoritative. Old category preferences are read-only fallback. */
    fun permitted(
        categoryCode: String,
        typeCode: String,
        vendorMasterTypeCodes: Set<String>,
        legacyAllowedCategoryCodes: Set<String>?
    ): Boolean {
        if (categoryCode == STAFF_CATEGORY || categoryCode.isBlank() || typeCode.isBlank()) return false
        return if (vendorMasterTypeCodes.isNotEmpty()) {
            typeCode in vendorMasterTypeCodes
        } else {
            legacyAllowedCategoryCodes?.contains(categoryCode) == true
        }
    }

    fun canSelectVendor(vendorId: String): Boolean = vendorId.isNotBlank() &&
        vendorId != "no_payee" && !vendorId.startsWith("employee:")
}
