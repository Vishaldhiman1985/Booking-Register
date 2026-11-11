package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceGlobalCatalogDefaultsTest {
    @Test fun foodIngredientsHasExpectedHierarchyAndDepartment() {
        val config = FinanceGlobalCatalogDefaults.forType("HE0201")
        assertTrue(config.stockable)
        assertEquals("Kitchen", config.department)
        assertTrue(config.groupIds.containsAll(setOf("grocery", "vegetables", "dairy", "meat", "bakery", "beverages", "spices")))
    }

    @Test fun beverageGuestAndBarTypesRemainDistinct() {
        assertEquals("F&B", FinanceGlobalCatalogDefaults.forType("HE0202").department)
        assertEquals("Bar", FinanceGlobalCatalogDefaults.forType("HE0203").department)
        assertEquals(setOf("alcoholic"), FinanceGlobalCatalogDefaults.forType("HE0203").groupIds)
        assertEquals("Housekeeping", FinanceGlobalCatalogDefaults.forType("HE0404").department)
        assertTrue("beverages" in FinanceGlobalCatalogDefaults.forType("HE0404").groupIds)
    }

    @Test fun untrackedServiceDoesNotRequireInventedStockItems() {
        val config = FinanceGlobalCatalogDefaults.forType("HE0501")
        assertFalse(config.stockable)
        assertTrue(config.groupIds.isEmpty())
        assertEquals("Rooms", config.department)
    }

    @Test fun allSeededGroupsHaveAtLeastOneAccountingTypeLink() {
        val known = HotelPurchaseCatalog.categories.map { it.id }.toSet()
        val mapped = FinanceGlobalCatalogDefaults.groupsByType.values.flatMap { it }.toSet()
        assertEquals(known, mapped)
        assertTrue(FinanceGlobalCatalogDefaults.groupsByType.keys.all { Regex("HE[0-9]{4}").matches(it) })
    }

    @Test fun enabledStockDefaultsDoNotDuplicateItemMasterRecords() {
        val groups = FinanceGlobalCatalogDefaults.groupsByType
        assertTrue("beverages" in groups.getValue("HE0201"))
        assertTrue("beverages" in groups.getValue("HE0202"))
        // Mapping the same group to two accounting types does not create new item IDs.
        assertTrue("HE0201" in FinanceGlobalCatalogDefaults.initiallyStockable)
    }
}
