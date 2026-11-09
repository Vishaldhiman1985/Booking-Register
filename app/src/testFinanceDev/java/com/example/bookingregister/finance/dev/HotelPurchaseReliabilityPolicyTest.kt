package com.example.bookingregister.finance.dev

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotelPurchaseReliabilityPolicyTest {
    private fun order(status: String = "PLACED") = HotelPurchaseOrder(
        "po-1", "vendor-1", "Supplier", 1000L,
        listOf(HotelPurchaseOrderLine("rice", "Rice", 5.0, "kg")), status
    )
    private fun receipt(id: String, quantity: Double, poId: String = "po-1") =
        HotelPurchaseGoodsReceipt(id, poId, 2000L,
            listOf(HotelPurchaseGoodsReceiptLine("rice", "Rice", "kg", quantity)))
    private fun document(kind: String, amount: Double) = HotelPurchaseInvoiceDocument(
        "doc-$kind", "po-1", "vendor-1", "INV-1", "2026-11-09", amount, kind, 2000L
    )

    @Test fun duplicateOrderLinesAreRejected() {
        val o = order().copy(lines = order().lines + order().lines.first())
        assertThrows<IllegalArgumentException> { HotelPurchaseOrderPolicy.validate(o) }
    }

    @Test fun placedOrdersCannotBeOverwritten() {
        assertThrows<IllegalArgumentException> {
            HotelPurchaseOrderPolicy.validateUpdate(order(), order().copy(status = "DRAFT"))
        }
    }

    @Test fun draftCanBePlacedWithoutChangingIdentity() {
        HotelPurchaseOrderPolicy.validateUpdate(order("DRAFT"), order("PLACED"))
    }

    @Test fun receiptHistoryRejectsDuplicateIdsAndOverReceiving() {
        assertThrows<IllegalArgumentException> {
            HotelPurchaseGoodsReceiptPolicy.validateHistory(order(), listOf(receipt("r1", 1.0), receipt("r1", 1.0)))
        }
        assertThrows<IllegalArgumentException> {
            HotelPurchaseGoodsReceiptPolicy.validateHistory(order(), listOf(receipt("r1", 4.0), receipt("r2", 2.0)))
        }
    }

    @Test fun receiptHistoryRejectsWrongOrderAndDraft() {
        assertThrows<IllegalArgumentException> {
            HotelPurchaseGoodsReceiptPolicy.validateHistory(order(), listOf(receipt("r1", 1.0, "po-2")))
        }
        assertThrows<IllegalArgumentException> {
            HotelPurchaseGoodsReceiptPolicy.validateHistory(order("DRAFT"), listOf(receipt("r1", 1.0)))
        }
    }

    @Test fun validPartialReceiptsRemainSupported() {
        HotelPurchaseGoodsReceiptPolicy.validateHistory(order(), listOf(receipt("r1", 2.0), receipt("r2", 3.0)))
    }

    @Test fun invoiceVerificationNeedsRealPositiveNetInvoice() {
        assertFalse(HotelPurchaseInvoicePolicy.canVerify(listOf(document("CREDIT_NOTE", 100.0))))
        assertFalse(HotelPurchaseInvoicePolicy.canVerify(listOf(document("INVOICE", 100.0), document("CREDIT_NOTE", 100.0))))
        assertFalse(HotelPurchaseInvoicePolicy.canVerify(listOf(document("INVOICE", Double.NaN))))
        assertTrue(HotelPurchaseInvoicePolicy.canVerify(listOf(document("INVOICE", 100.0), document("CREDIT_NOTE", 20.0))))
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try { block() } catch (t: Throwable) {
            if (t is T) return
            throw AssertionError("Expected ${T::class.java.simpleName}, got ${t::class.java.simpleName}", t)
        }
        throw AssertionError("Expected ${T::class.java.simpleName} but nothing was thrown")
    }
}
