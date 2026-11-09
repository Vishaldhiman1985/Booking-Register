package com.example.bookingregister.finance.dev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** Finance DEV only. Invoice evidence belongs to a PO and its original vendor ID.
 * This register does not post expenses, GST, ITC or payments.
 */
data class HotelPurchaseInvoiceDocument(
    val id: String,
    val orderId: String,
    val vendorId: String,
    val invoiceNumber: String,
    val invoiceDate: String,
    val amount: Double,
    val kind: String,
    val addedAtMillis: Long
)

data class HotelPurchaseInvoiceVerification(
    val orderId: String,
    val verifiedAtMillis: Long,
    val note: String
)

/** Procurement document policy; not an accounting or GST eligibility decision. */
object HotelPurchaseInvoicePolicy {
    fun canVerify(documents: List<HotelPurchaseInvoiceDocument>): Boolean =
        documents.any { it.kind == "INVOICE" && it.amount.isFinite() && it.amount > 0.0 } &&
            documents.all { (it.kind == "INVOICE" || it.kind == "CREDIT_NOTE") && it.amount.isFinite() && it.amount > 0.0 } &&
            documents.sumOf { if (it.kind == "CREDIT_NOTE") -it.amount else it.amount } > 0.0
}

class HotelPurchaseInvoiceStore(context: Context) {
    private val prefs = context.getSharedPreferences("finance_dev_po_invoice_evidence_v1", Context.MODE_PRIVATE)

    fun all(): List<HotelPurchaseInvoiceDocument> {
        val array = JSONArray(prefs.getString("documents", "[]") ?: "[]")
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            HotelPurchaseInvoiceDocument(
                id = o.getString("id"), orderId = o.getString("orderId"),
                vendorId = o.getString("vendorId"), invoiceNumber = o.getString("invoiceNumber"),
                invoiceDate = o.getString("invoiceDate"), amount = o.getDouble("amount"),
                kind = o.getString("kind"), addedAtMillis = o.getLong("addedAtMillis")
            )
        }.sortedBy { it.addedAtMillis }
    }

    fun forOrder(orderId: String): List<HotelPurchaseInvoiceDocument> = all().filter { it.orderId == orderId }

    fun verification(orderId: String): HotelPurchaseInvoiceVerification? {
        val obj = JSONObject(prefs.getString("verifications", "{}") ?: "{}")
        val match = obj.optJSONObject(orderId) ?: return null
        return HotelPurchaseInvoiceVerification(orderId, match.getLong("verifiedAtMillis"), match.getString("note"))
    }

    fun isVerified(orderId: String): Boolean =
        verification(orderId) != null && HotelPurchaseInvoicePolicy.canVerify(forOrder(orderId))

    fun netAmount(orderId: String): Double = forOrder(orderId).sumOf {
        if (it.kind == "CREDIT_NOTE") -it.amount else it.amount
    }

    fun add(order: HotelPurchaseOrder, number: String, date: String, amount: Double, kind: String) {
        val cleanNumber = number.trim()
        require(order.status == "PLACED") { "Invoices can be registered only for placed orders" }
        require(cleanNumber.isNotBlank()) { "Invoice / credit note number is required" }
        require(kind == "INVOICE" || kind == "CREDIT_NOTE") { "Unknown document type" }
        require(amount.isFinite() && amount > 0.0) { "Enter a positive document amount" }
        try { LocalDate.parse(date) } catch (e: Exception) { throw IllegalArgumentException("Enter a valid date as YYYY-MM-DD") }
        val existing = all()
        require(existing.none {
            it.vendorId == order.vendorId && it.kind == kind && it.invoiceNumber.equals(cleanNumber, ignoreCase = true)
        }) { "This vendor document number is already recorded" }
        val document = HotelPurchaseInvoiceDocument(
            UUID.randomUUID().toString(), order.id, order.vendorId, cleanNumber,
            date, amount, kind, System.currentTimeMillis()
        )
        val array = JSONArray()
        (existing + document).forEach { d ->
            array.put(JSONObject().apply {
                put("id", d.id); put("orderId", d.orderId); put("vendorId", d.vendorId)
                put("invoiceNumber", d.invoiceNumber); put("invoiceDate", d.invoiceDate)
                put("amount", d.amount); put("kind", d.kind); put("addedAtMillis", d.addedAtMillis)
            })
        }
        // Any new invoice/credit note invalidates a previous verification for this PO.
        val verifications = JSONObject(prefs.getString("verifications", "{}") ?: "{}")
        verifications.remove(order.id)
        check(prefs.edit().putString("documents", array.toString())
            .putString("verifications", verifications.toString()).commit()) { "Unable to save invoice evidence" }
    }

    fun verify(order: HotelPurchaseOrder, note: String) {
        require(order.status == "PLACED") { "Only placed orders can be verified" }
        require(HotelPurchaseInvoicePolicy.canVerify(forOrder(order.id))) {
            "Record a valid supplier invoice with a positive net amount before verification"
        }
        require(note.trim().length >= 5) { "Enter the verification note or invoice reference" }
        val verifications = JSONObject(prefs.getString("verifications", "{}") ?: "{}")
        verifications.put(order.id, JSONObject().apply {
            put("verifiedAtMillis", System.currentTimeMillis())
            put("note", note.trim())
        })
        check(prefs.edit().putString("verifications", verifications.toString()).commit()) {
            "Unable to save invoice verification"
        }
    }
}
