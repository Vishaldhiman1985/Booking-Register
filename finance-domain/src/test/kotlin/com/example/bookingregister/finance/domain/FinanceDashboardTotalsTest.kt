package com.example.bookingregister.finance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FinanceDashboardTotalsTest {
    private val month = "2026-11"
    @Test fun noRevenueDoesNotBecomeFalseZeroProfit() {
        val s = FinanceDashboardTotals.calculate(month,
            listOf(FinanceDashboardTotals.Bill(month, 10_000_000, 0, "Rooms")), emptyList(), emptyList())
        assertEquals(10_000_000L, s.billAmountPaise)
        assertNull(s.importedRevenuePaise)
        assertNull(s.indicativeDifferencePaise)
    }
    @Test fun leaseTaxIsNeverAddedToRentExpense() {
        val s = FinanceDashboardTotals.calculate(month,
            listOf(FinanceDashboardTotals.Bill(month, 10_000_000, 0, "Rooms")), emptyList(),
            listOf(FinanceDashboardTotals.LeaseTax(month, "RCM", 1_800_000)))
        assertEquals(10_000_000L, s.billAmountPaise)
        assertEquals(1_800_000L, s.taxUnpaidPaise)
        assertEquals(0L, s.billUnpaidPaise)
    }
    @Test fun monthlySelectionAndKitchenTotalsAreIndependent() {
        val s = FinanceDashboardTotals.calculate(month,
            listOf(FinanceDashboardTotals.Bill(month, 5_000_000, 1_000_000, "Rooms"),
                   FinanceDashboardTotals.Bill(month, 2_000_000, 500_000, "Kitchen"),
                   FinanceDashboardTotals.Bill("2026-10", 9_000_000, 9_000_000, "Kitchen")),
            listOf(FinanceDashboardTotals.Revenue(month, 12_000_000, "Rooms"),
                   FinanceDashboardTotals.Revenue(month, 3_000_000, "Kitchen (F&B)"),
                   FinanceDashboardTotals.Revenue("2026-10", 9_000_000, "Kitchen")),
            listOf(FinanceDashboardTotals.LeaseTax(month, "RCM", 1_800_000),
                   FinanceDashboardTotals.LeaseTax(month, "LANDLORD_GST", 500_000)))
        assertEquals(7_000_000L, s.billAmountPaise)
        assertEquals(1_500_000L, s.billUnpaidPaise)
        assertEquals(2_000_000L, s.kitchenBillAmountPaise)
        assertEquals(15_000_000L, s.importedRevenuePaise)
        assertEquals(8_000_000L, s.indicativeDifferencePaise)
        assertEquals(3_000_000L, s.kitchenRevenuePaise)
        assertEquals(1_000_000L, s.kitchenDifferencePaise)
        assertEquals(2_300_000L, s.taxUnpaidPaise)
    }
    @Test fun invalidOutstandingIsNotSilentlyReported() {
        assertThrows(IllegalArgumentException::class.java) {
            FinanceDashboardTotals.calculate(month,
                listOf(FinanceDashboardTotals.Bill(month, 100, 101, "Rooms")), emptyList(), emptyList())
        }
    }
    @Test fun allTimeAndMonthUseDifferentSetsButSameDefinitions() {
        val bills = listOf(
            FinanceDashboardTotals.Bill("2026-11", 4_019_0000, 2_00000, "Rooms"),
            FinanceDashboardTotals.Bill("2026-10", 1_650_0000, 60_00000, "Rooms")
        )
        val taxes = listOf(
            FinanceDashboardTotals.LeaseTax("2026-11", "RCM", 63_90000),
            FinanceDashboardTotals.LeaseTax("2026-10", "RCM", 10_00000)
        )
        val monthly = FinanceDashboardTotals.calculate(month,bills,emptyList(),taxes)
        val all = FinanceDashboardTotals.calculate(month,bills,emptyList(),taxes,allTime=true)
        assertEquals(4_019_0000L,monthly.billAmountPaise)
        assertEquals(2_00000L,monthly.billUnpaidPaise)
        assertEquals(65_90000L,monthly.totalOutstandingPaise)
        assertEquals(5_669_0000L,all.billAmountPaise)
        assertEquals(62_00000L,all.billUnpaidPaise)
        assertEquals(73_90000L,all.taxUnpaidPaise)
        assertEquals(135_90000L,all.totalOutstandingPaise)
    }
    @Test fun noRevenueIsStillUnknownAllTime() {
        val all = FinanceDashboardTotals.calculate(month,
            listOf(FinanceDashboardTotals.Bill("2026-10", 100L, 60L, "Kitchen")),
            emptyList(),emptyList(),allTime=true)
        assertNull(all.importedRevenuePaise)
        assertNull(all.indicativeDifferencePaise)
    }

}
