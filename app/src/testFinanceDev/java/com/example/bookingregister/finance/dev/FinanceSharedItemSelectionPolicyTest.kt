package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceSharedItemSelectionPolicyTest {
    private val categories = listOf(
        HotelPurchaseCategory("grocery", "Grocery & Dry Rations"),
        HotelPurchaseCategory("dairy", "Dairy & Eggs"),
        HotelPurchaseCategory("empty", "No stock")
    )
    private val items = listOf(
        HotelPurchaseItem("rice", "grocery", "Basmati Rice", "kg", true, listOf("chawal")),
        HotelPurchaseItem("milk", "dairy", "Milk", "litre"),
        HotelPurchaseItem("archived", "grocery", "Old Rice", "kg", false)
    )

    @Test fun groupRailSkipsEmptyGroupsAndArchivedOnlyGroups() {
        assertEquals(listOf("grocery", "dairy"), FinanceSharedItemSelectionPolicy.groupsWithItems(categories, items).map { it.id })
    }

    @Test fun categoryRailAndSearchMatchPurchaseOrderBehavior() {
        assertEquals(listOf("rice"), FinanceSharedItemSelectionPolicy.displayedItems(items, "grocery", "").map { it.id })
        assertEquals(listOf("milk"), FinanceSharedItemSelectionPolicy.displayedItems(items, "grocery", "milk").map { it.id })
        assertEquals(listOf("rice"), FinanceSharedItemSelectionPolicy.displayedItems(items, "dairy", "chawal").map { it.id })
    }

    @Test fun selectedQuantitiesUseSharedStableItemIdsAndStockUnits() {
        val selection = FinanceSharedItemSelectionPolicy.selectedLines(items, mapOf("rice" to 2.5, "milk" to 1.0))
        assertEquals(2, selection.size)
        assertEquals("kg", selection.first { it.first.id == "rice" }.first.unit)
        assertEquals(2.5, selection.first { it.first.id == "rice" }.second, 0.0001)
    }

    @Test fun zeroQuantitiesAreOmittedAndUnknownItemsAreRejected() {
        assertEquals(listOf("rice"), FinanceSharedItemSelectionPolicy.selectedLines(items, mapOf("rice" to 1.0, "milk" to 0.0)).map { it.first.id })
        assertRejects { FinanceSharedItemSelectionPolicy.selectedLines(items, mapOf("archived" to 1.0)) }
        assertRejects { FinanceSharedItemSelectionPolicy.selectedLines(items, mapOf("rice" to Double.NaN)) }
        assertRejects { FinanceSharedItemSelectionPolicy.selectedLines(items, mapOf("unknown" to 2.0)) }
        assertRejects { FinanceSharedItemSelectionPolicy.selectedLines(items, emptyMap()) }
    }

    private fun assertRejects(block: () -> Unit) {
        var rejected = false
        try { block() } catch (_: IllegalArgumentException) { rejected = true } catch (_: IllegalStateException) { rejected = true }
        assertTrue("Expected to reject selection", rejected)
    }
}
