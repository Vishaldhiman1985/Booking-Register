package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceCatalogQualityRulesTest {
    @Test fun missingStockGroupAndEmptyStockGroupAreReported() {
        val issues = FinanceCatalogQualityRules.inspect(
            listOf(FinanceQualityGroup("food", "Food")),
            emptyList(),
            listOf(FinanceQualityType("HE0201", "Food Ingredients", true, setOf("food", "missing")))
        )
        assertTrue(issues.any { it.kind == "Missing link" && it.typeCode == "HE0201" })
        assertTrue(issues.any { it.kind == "Missing items" && it.groupId == "food" })
    }

    @Test fun duplicateWithinGroupDoesNotMarkSameItemInDifferentGroup() {
        val issues = FinanceCatalogQualityRules.inspect(
            listOf(FinanceQualityGroup("a", "A"), FinanceQualityGroup("b", "B")),
            listOf(
                FinanceQualityItem("1", "a", "Milk", "litre", true),
                FinanceQualityItem("2", "a", " milk  ", "litre", true),
                FinanceQualityItem("3", "b", "Milk", "litre", true)
            ), emptyList())
        assertEquals(1, issues.count { it.kind == "Duplicate" })
    }

    @Test fun suspectedUnitsAreReviewOnlyAndNotUniversal() {
        assertTrue(FinanceCatalogQualityRules.reviewUnit("Milk Full Cream", "kg"))
        assertTrue(FinanceCatalogQualityRules.reviewUnit("Floor Cleaner", "piece"))
        assertFalse(FinanceCatalogQualityRules.reviewUnit("Milk Powder", "kg"))
        assertFalse(FinanceCatalogQualityRules.reviewUnit("Floor Cleaner", "litre"))
        assertFalse(FinanceCatalogQualityRules.reviewUnit("Soap Bar", "piece"))
    }

    @Test fun archivedItemIsNotFlagged() {
        val issues = FinanceCatalogQualityRules.inspect(
            listOf(FinanceQualityGroup("a", "A")),
            listOf(FinanceQualityItem("old", "a", "Floor Cleaner", "piece", false)),
            emptyList())
        assertEquals(0, issues.size)
    }
}
