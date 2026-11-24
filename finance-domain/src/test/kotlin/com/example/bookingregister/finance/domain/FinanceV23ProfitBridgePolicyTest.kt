package com.example.bookingregister.finance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class FinanceV23ProfitBridgePolicyTest {
    private val p = FinanceV23ProfitBridgePolicy
    private val month = "2026-10"
    private fun income(id: String = "sale-1", dept: String = "Rooms", amount: Long = 10000L) =
        FinanceV23ProfitBridgePolicy.Income(id, month, dept, amount)
    private fun bill(id: String = "bill-1", dept: String = "Rooms", amount: Long = 4000L, stock: Boolean = false) =
        FinanceV23ProfitBridgePolicy.Expense(id, month, dept, amount, stock)

    @Test fun directRoomCostAndImportedRevenueAreKeptSeparate() {
        val r = p.calculate(month, listOf(income()), listOf(bill()), emptyList(), emptyList())
        val rooms = r.rows.single { it.center == FinanceV23ProfitBridgePolicy.Center.ROOMS }
        assertEquals(10000L, rooms.revenuePaise)
        assertEquals(4000L, rooms.costPaise)
        assertEquals(6000L, rooms.recordedDifferencePaise)
        assertEquals(0L, r.excludedInventoryPurchasesPaise)
    }
    @Test fun boughtInventoryIsNotRecountedAlongsideConsumption() {
        val r = p.calculate(month, listOf(income(dept="Kitchen")),
            listOf(bill(amount=7000,stock=true)), listOf(FinanceV23ProfitBridgePolicy.StockCost("rice",month,2000)),
            listOf(FinanceV23ProfitBridgePolicy.Assignment("stock:rice",2000,p.single(FinanceV23ProfitBridgePolicy.Center.KITCHEN))))
        assertEquals(7000L,r.excludedInventoryPurchasesPaise)
        assertEquals(1,r.excludedInventoryPurchaseCount)
        assertEquals(2000L,r.allocatedCostPaise)
    }
    @Test fun sharedBillsRequireExplicitSplitRatherThanAssumedRoomCosts() {
        val r = p.calculate(month, listOf(income()), listOf(bill(dept="Shared")), emptyList(), emptyList())
        assertEquals(4000L,r.unallocatedPaise)
        assertTrue(r.issues.any { it.contains("allocation") })
    }
    @Test fun costSplitsPreservePaise() {
        val s = FinanceV23ProfitBridgePolicy.Split(3333,3333,3334)
        for (amount in 0L..100L) assertEquals(amount,s.parts(amount).values.sum())
    }
    @Test fun percentagesMustSumExactly() {
        assertThrows(IllegalArgumentException::class.java) { FinanceV23ProfitBridgePolicy.Split(5000,4000,0) }
        assertThrows(IllegalArgumentException::class.java) { FinanceV23ProfitBridgePolicy.Split(10001,0,-1) }
    }
    @Test fun storedAllocationInvalidatedByLaterBillChange() {
        val r = p.calculate(month,listOf(income()),listOf(bill(dept="Shared",amount=6000)),emptyList(),
            listOf(FinanceV23ProfitBridgePolicy.Assignment("bill:bill-1",5000,FinanceV23ProfitBridgePolicy.Split(5000,5000,0))))
        assertEquals(6000L,r.unallocatedPaise)
        assertTrue(r.issues.any { it.contains("stale") })
    }
    @Test fun unknownRevenueDepartmentIsNotInvented() {
        val r=p.calculate(month,listOf(income(dept="Misc")),emptyList(),emptyList(),emptyList())
        assertEquals(0L,r.rows.sumOf { it.revenuePaise })
        assertTrue(r.issues.any { it.contains("needs classification") })
    }
    @Test fun duplicateBillsCannotInflateExpense() {
        assertThrows(IllegalArgumentException::class.java) {
            p.calculate(month,listOf(income()),listOf(bill(),bill()),emptyList(),emptyList())
        }
    }
    @Test fun payrollBillOnlyAppearsOnceInSourceRows() {
        val r=p.calculate(month,listOf(income()),listOf(bill("salary", "Kitchen",3000)),emptyList(),emptyList())
        assertEquals(3000L,r.rows.single { it.center == FinanceV23ProfitBridgePolicy.Center.KITCHEN }.costPaise)
    }
    @Test fun negativeIncomeCorrectionsAreRetained() {
        val r=p.calculate(month,listOf(income("A",amount=20000),income("reverse",amount=-5000)),
            emptyList(),emptyList(),emptyList())
        assertEquals(15000L,r.importedRevenuePaise)
    }
    @Test fun malformedSourceMonthIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            p.calculate(month,listOf(FinanceV23ProfitBridgePolicy.Income("1","2026-09","Rooms",100)),emptyList(),emptyList(),emptyList())
        }
    }
    @Test fun zeroRevenueIsNotPresentedAsCertified() {
        val r=p.calculate(month,emptyList(),emptyList(),emptyList(),emptyList())
        assertFalse(r.possibleToInterpret)
        assertTrue(r.issues.isNotEmpty())
    }
    @Test fun unallocatedStockCannotBeSilentlyChargedToKitchen() {
        val r=p.calculate(month,listOf(income()),emptyList(),listOf(FinanceV23ProfitBridgePolicy.StockCost("soap",month,1000)),emptyList())
        assertEquals(1000L,r.unallocatedPaise)
    }
    @Test fun externalStockBlockerFlowsIntoReport() {
        val r=p.calculate(month,listOf(income()),emptyList(),emptyList(),emptyList(),listOf("Stock evidence missing"))
        assertTrue(r.issues.contains("Stock evidence missing"))
        assertFalse(r.possibleToInterpret)
    }
}
