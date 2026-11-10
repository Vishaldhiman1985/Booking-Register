package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotelPurchaseRemainingGoodsPolicyTest {
    private val order = HotelPurchaseOrder("po-1", "v-1", "Harsh", 1000L,
        listOf(HotelPurchaseOrderLine("maida", "Maida", 1.0, "kg"),
            HotelPurchaseOrderLine("rice", "Rice", 2.0, "kg")), "PLACED")
    private val first = HotelPurchaseGoodsReceipt("r1", "po-1", 2000L,
        listOf(HotelPurchaseGoodsReceiptLine("maida", "Maida", "kg", 0.5),
            HotelPurchaseGoodsReceiptLine("rice", "Rice", "kg", 2.0)))

    @Test fun supplementaryReceiptUsesOnlySelectedOutstandingLine() {
        val extra = HotelPurchaseRemainingGoodsPolicy.createSupplementaryReceipt(
            order, listOf(first), setOf("maida"), "r2", 3000L, "late delivery")
        assertEquals(1, extra.lines.size)
        assertEquals("maida", extra.lines.single().itemId)
        assertEquals(0.5, extra.lines.single().receivedQuantity, 0.0000001)
        assertTrue(HotelPurchaseRemainingGoodsPolicy.isFullyDelivered(order, listOf(first, extra)))
        assertEquals(0.5, first.lines.first().receivedQuantity, 0.0)
    }

    @Test fun noSelectionIsRejected() {
        expectFailure { HotelPurchaseRemainingGoodsPolicy.createSupplementaryReceipt(
            order, listOf(first), emptySet(), "r2", 3000L, "") }
    }

    @Test fun alreadyReceivedItemCannotBeSelected() {
        expectFailure { HotelPurchaseRemainingGoodsPolicy.createSupplementaryReceipt(
            order, listOf(first), setOf("rice"), "r2", 3000L, "") }
    }

    @Test fun staleSelectionRejectedAfterAnotherReceipt() {
        val extra = HotelPurchaseRemainingGoodsPolicy.createSupplementaryReceipt(
            order, listOf(first), setOf("maida"), "r2", 3000L, "")
        expectFailure { HotelPurchaseRemainingGoodsPolicy.createSupplementaryReceipt(
            order, listOf(first, extra), setOf("maida"), "r3", 4000L, "") }
    }

    @Test fun draftCannotReceiveRemainingGoods() {
        expectFailure { HotelPurchaseRemainingGoodsPolicy.createSupplementaryReceipt(
            order.copy(status="DRAFT"), listOf(first), setOf("maida"), "r2", 3000L, "") }
    }

    @Test fun orderStillPendingIfOneOutstandingItemNotSelected() {
        val both = order.copy(lines = order.lines + HotelPurchaseOrderLine("salt", "Salt", 1.0, "kg"))
        val extra = HotelPurchaseRemainingGoodsPolicy.createSupplementaryReceipt(
            both, listOf(first), setOf("maida"), "r2", 3000L, "")
        assertFalse(HotelPurchaseRemainingGoodsPolicy.isFullyDelivered(both, listOf(first, extra)))
    }

    private fun expectFailure(action: () -> Unit) {
        try { action() } catch (_: IllegalArgumentException) { return }
        throw AssertionError("Expected validation failure")
    }
}
