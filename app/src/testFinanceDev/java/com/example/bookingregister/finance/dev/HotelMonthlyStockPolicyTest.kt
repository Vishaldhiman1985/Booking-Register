package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class HotelMonthlyStockPolicyTest {
    private fun q(id: String, qty: Double, unit: String = "Kg") = HotelStockQuantity(id, id, unit, qty)

    @Test fun carriesClosingAndCalculatesConsumption() {
        val result = HotelMonthlyStockPolicy.balance(listOf(q("rice", 10.0)), listOf(q("rice", 100.0)), listOf(q("rice", 25.6)))
        assertEquals(84.4, result.single().quantity, 0.000001)
    }
    @Test fun zeroClosingAllowed() {
        assertEquals(3.0, HotelMonthlyStockPolicy.balance(emptyList(), listOf(q("dal", 3.0)), listOf(q("dal", 0.0))).single().quantity, 0.000001)
    }
    @Test fun missingCountBlocked() {
        try { HotelMonthlyStockPolicy.balance(listOf(q("rice", 1.0)), emptyList(), emptyList()); fail("expected failure") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun overcountBlocked() {
        try { HotelMonthlyStockPolicy.balance(listOf(q("rice", 2.0)), emptyList(), listOf(q("rice", 3.0))); fail("expected failure") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun unitMismatchBlocked() {
        try { HotelMonthlyStockPolicy.balance(listOf(q("milk", 2.0, "Packet")), listOf(q("milk", 1.0, "Litre")), listOf(q("milk", 1.0, "Packet"))); fail("expected failure") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun mergesMultipleReceipts() {
        assertEquals(11.5, HotelMonthlyStockPolicy.merge(listOf(q("rice", 10.0), q("rice", 1.5))).single().quantity, 0.000001)
    }
}
