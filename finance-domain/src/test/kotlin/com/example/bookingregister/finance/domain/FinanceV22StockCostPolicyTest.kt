package com.example.bookingregister.finance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceV22StockCostPolicyTest {
    private val p = FinanceV22StockCostPolicy
    private fun line(o: Double, r: Double, c: Double) = FinanceV22StockCostPolicy.Quantity("rice", "Rice", "kg", o, r, c)
    private fun rates(a: Long? = 1000, b: Long? = 2000) =
        FinanceV22StockCostPolicy.Evidence(a,b,"Opening valuation sheet","Vendor invoice 246")

    @Test fun weightedAverageBalancesInPaise() {
        val v = p.value(line(10.0,10.0,4.0),rates())
        assertEquals(10000L,v.openingValuePaise)
        assertEquals(20000L,v.receivedValuePaise)
        assertEquals(6000L,v.closingValuePaise)
        assertEquals(24000L,v.consumedValuePaise)
        assertEquals(16.0,v.consumedQuantity,0.00001)
        assertEquals(24000L,p.totals(listOf(v)).consumedPaise)
    }
    @Test fun noPurchasesNeedsOnlyOpeningEvidence() {
        val v=p.value(line(10.0,0.0,6.0),FinanceV22StockCostPolicy.Evidence(1000,null,"Approved opening count",""))
        assertEquals(4000L,v.consumedValuePaise)
    }
    @Test fun zeroStockIsZeroWithoutPriceEvidence() {
        assertEquals(0L,p.value(line(0.0,0.0,0.0),FinanceV22StockCostPolicy.Evidence(null,null,"","")).consumedValuePaise)
    }
    @Test fun missingPricesNeverBecomeZeroCostOfGoods() {
        assertThrows(IllegalArgumentException::class.java) {
            p.value(line(2.0,1.0,1.0),FinanceV22StockCostPolicy.Evidence(1000,null,"Opening valuation sheet",""))
        }
    }
    @Test fun unsupportedClosingAndMissingReferencesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { p.value(line(1.0,1.0,3.0),rates()) }
        assertThrows(IllegalArgumentException::class.java) {
            p.value(line(1.0,1.0,1.0),FinanceV22StockCostPolicy.Evidence(1000,2000,"", "Vendor invoice"))
        }
    }
    @Test fun rateInputRejectsFractionsOfAPaiseAndBadValues() {
        assertEquals(1255L,p.rupeesToPaise("12.55"))
        assertThrows(ArithmeticException::class.java) { p.rupeesToPaise("12.555") }
        assertThrows(IllegalArgumentException::class.java) { p.rupeesToPaise("0") }
    }
    @Test fun fingerprintChangesWhenQuantitiesOrReceiptHistoryChange() {
        val a = p.fingerprint("2026-10",1000,listOf(line(4.0,2.0,1.0)),listOf("g1"))
        assertEquals(64,a.length)
        assertTrue(a==p.fingerprint("2026-10",1000,listOf(line(4.0,2.0,1.0)),listOf("g1")))
        assertFalse(a==p.fingerprint("2026-10",1000,listOf(line(4.0,2.0,2.0)),listOf("g1")))
        assertFalse(a==p.fingerprint("2026-10",1000,listOf(line(4.0,2.0,1.0)),listOf("g2")))
    }
    @Test fun roundingStillConservesPaise() {
        val v = p.value(line(0.333,0.333,0.111),rates(10,11))
        assertEquals(v.openingValuePaise+v.receivedValuePaise,v.closingValuePaise+v.consumedValuePaise)
    }
}
