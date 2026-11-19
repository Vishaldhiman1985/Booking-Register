package com.example.bookingregister.finance.dev

import org.junit.Assert.*
import org.junit.Test

class FinanceExpenseCategoryRulesTest {
    @Test fun staffOnlyCannotBeEnteredManually() {
        assertFalse(FinanceExpenseCategoryRules.permitted("HC01", null))
        assertFalse(FinanceExpenseCategoryRules.permitted("HC01", setOf("HC01", "HC02")))
    }
    @Test fun registeredVendorAllowsOnlyAssignedCategory() {
        assertTrue(FinanceExpenseCategoryRules.permitted("HC02", setOf("HC02")))
        assertFalse(FinanceExpenseCategoryRules.permitted("HC03", setOf("HC02")))
    }
    @Test fun unregisteredVendorMayChooseOtherNonPayrollCategories() {
        assertTrue(FinanceExpenseCategoryRules.permitted("HC18", null))
    }
}
