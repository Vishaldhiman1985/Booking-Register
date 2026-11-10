package com.example.bookingregister.finance.dev

/** Require new supplier documentation when the original invoice included missing goods.
 * This does not automatically validate amounts; manual matching is still required.
 */
object HotelPurchaseShortageInvoicePolicy {
    fun requireCurrentDocuments(
        workflow: HotelPurchaseReceivingWorkflow,
        documents: List<HotelPurchaseInvoiceDocument>
    ) {
        val flagged = workflow.events.lastOrNull { it.action == "REVISED_DOCUMENT_REQUIRED" }
            ?: return
        require(documents.any {
            it.orderId == workflow.orderId &&
                it.addedAtMillis >= flagged.atMillis &&
                (it.kind == "CREDIT_NOTE" || it.kind == "INVOICE")
        }) { "Add the corrected supplier invoice or credit note before verification" }
    }
}
