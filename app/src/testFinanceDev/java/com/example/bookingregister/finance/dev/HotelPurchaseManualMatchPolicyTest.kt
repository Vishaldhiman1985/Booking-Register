package com.example.bookingregister.finance.dev

import org.junit.Assert.assertTrue
import org.junit.Test

class HotelPurchaseManualMatchPolicyTest {
    private val order = HotelPurchaseOrder("po-1", "vendor-1", "Supplier", 1000L,
        listOf(HotelPurchaseOrderLine("rice", "Rice", 5.0, "kg")), "PLACED")
    private fun receipt(q: Double) = HotelPurchaseGoodsReceipt("r-1", "po-1", 2000L,
        listOf(HotelPurchaseGoodsReceiptLine("rice", "Rice", "kg", q)))
    private fun document(vendor: String = "vendor-1", kind: String = "INVOICE") = HotelPurchaseInvoiceDocument(
        "d-1", "po-1", vendor, "INV-1", "2026-11-09", 500.0, kind, 2000L)

    private fun check(receipts: List<HotelPurchaseGoodsReceipt>, documents: List<HotelPurchaseInvoiceDocument>,
                      goods: Boolean = true, amounts: Boolean = true, documentsChecked: Boolean = true,
                      resolved: Boolean = false) = HotelPurchaseManualMatchPolicy.validate(
        order, receipts, documents, "Checked supplier bill", goods, amounts, documentsChecked, resolved)

    @Test fun fullReceiptAndAllChecksCanBeVerified() {
        check(listOf(receipt(5.0)), listOf(document()))
        assertTrue(true)
    }

    @Test fun noGoodsReceiptCannotVerify() {
        rejects { check(emptyList(), listOf(document())) }
    }

    @Test fun outstandingGoodsRequireResolution() {
        rejects { check(listOf(receipt(3.0)), listOf(document())) }
        check(listOf(receipt(3.0)), listOf(document()), resolved = true)
    }

    @Test fun everyManualCheckIsRequired() {
        rejects { check(listOf(receipt(5.0)), listOf(document()), goods = false) }
        rejects { check(listOf(receipt(5.0)), listOf(document()), amounts = false) }
        rejects { check(listOf(receipt(5.0)), listOf(document()), documentsChecked = false) }
    }

    @Test fun documentMustBelongToOriginalVendorAndOrder() {
        rejects { check(listOf(receipt(5.0)), listOf(document("other"))) }
        rejects { check(listOf(receipt(5.0)), listOf(document().copy(orderId = "other"))) }
        rejects { check(listOf(receipt(5.0)), listOf(document(kind = "CREDIT_NOTE"))) }
    }

    @Test fun malformedReceiptHistoryCannotVerify() {
        rejects { check(listOf(receipt(6.0)), listOf(document())) }
    }

    private fun rejects(block: () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { return }
        throw AssertionError("Expected invalid reconciliation to be rejected")
    }
}
