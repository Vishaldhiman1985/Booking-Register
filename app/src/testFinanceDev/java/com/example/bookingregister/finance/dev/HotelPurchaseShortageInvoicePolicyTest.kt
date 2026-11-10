package com.example.bookingregister.finance.dev

import org.junit.Test

class HotelPurchaseShortageInvoicePolicyTest {
    private fun doc(at: Long, kind: String = "CREDIT_NOTE") = HotelPurchaseInvoiceDocument(
        "id-$at", "po", "vendor", "D-$at", "2026-11-09", 20.0, kind, at
    )
    @Test fun noRevisionRequiredAllowsExistingDocuments() {
        HotelPurchaseShortageInvoicePolicy.requireCurrentDocuments(HotelPurchaseReceivingWorkflow("po"), listOf(doc(10)))
    }
    @Test fun originalFullInvoiceNeedsNewEvidence() {
        val flow = HotelPurchaseReceivingWorkflow("po", events = listOf(HotelPurchaseIssueEvent(100, "REVISED_DOCUMENT_REQUIRED", "missing")))
        try {
            HotelPurchaseShortageInvoicePolicy.requireCurrentDocuments(flow, listOf(doc(50, "INVOICE")))
            throw AssertionError("Expected missing correction to be rejected")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun correctedCreditNoteAfterResolutionAccepted() {
        val flow = HotelPurchaseReceivingWorkflow("po", events = listOf(HotelPurchaseIssueEvent(100, "REVISED_DOCUMENT_REQUIRED", "missing")))
        HotelPurchaseShortageInvoicePolicy.requireCurrentDocuments(flow, listOf(doc(50, "INVOICE"), doc(101)))
    }
}
