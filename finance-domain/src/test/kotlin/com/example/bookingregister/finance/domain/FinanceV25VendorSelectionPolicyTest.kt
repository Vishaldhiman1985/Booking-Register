package com.example.bookingregister.finance.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceV25VendorSelectionPolicyTest {
    private val p = FinanceV25VendorSelectionPolicy

    @Test fun savedVendorTypesAreAuthoritative() {
        assertTrue(p.permitted("GROCERY", "RICE", setOf("RICE"), setOf("OTHER")))
        assertFalse(p.permitted("GROCERY", "DAL", setOf("RICE"), setOf("GROCERY")))
    }

    @Test fun legacyCategoriesOnlyWhenMasterNotConfigured() {
        assertTrue(p.permitted("GROCERY", "RICE", emptySet(), setOf("GROCERY")))
        assertFalse(p.permitted("ROOMS", "RICE", emptySet(), setOf("GROCERY")))
    }

    @Test fun vendorWithoutAssignmentsCannotSilentlyUseAnyType() {
        assertFalse(p.permitted("GROCERY", "RICE", emptySet(), null))
    }

    @Test fun staffOnlyCategoryCannotBeAddedThroughVendors() {
        assertFalse(p.permitted(p.STAFF_CATEGORY, "SALARY", setOf("SALARY"), setOf(p.STAFF_CATEGORY)))
    }

    @Test fun blankValuesCannotBypassClassification() {
        assertFalse(p.permitted("", "RICE", setOf("RICE"), null))
        assertFalse(p.permitted("GROCERY", "", setOf("RICE"), null))
    }

    @Test fun employeeVendorIdsCannotBeOpened() {
        assertTrue(p.canSelectVendor("supplier-9"))
        assertFalse(p.canSelectVendor("employee:abc"))
        assertFalse(p.canSelectVendor("no_payee"))
        assertFalse(p.canSelectVendor(""))
    }
}
