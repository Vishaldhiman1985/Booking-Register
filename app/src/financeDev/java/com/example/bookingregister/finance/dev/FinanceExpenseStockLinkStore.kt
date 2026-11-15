package com.example.bookingregister.finance.dev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** A physical receipt line. Item identity and stock unit are immutable snapshots. */
internal data class FinanceExpenseStockLine(
    val itemId: String,
    val itemName: String,
    val unit: String,
    val quantity: Double
)

/**
 * Finance DEV ONLY. One stock link per local draft. This is not an accounting journal.
 * RECEIVED_DIRECT feeds Monthly Stock once; LINKED_PO only references existing PO receipts.
 */
internal data class FinanceExpenseStockLink(
    val draftId: String,
    val expenseTypeCode: String,
    val invoiceMonth: String,
    val groupIds: Set<String>,
    val state: String,
    val receivedOn: String? = null,
    val poOrderId: String? = null,
    val lines: List<FinanceExpenseStockLine> = emptyList(),
    val updatedAtMillis: Long
) {
    val receiptId: String get() = "direct:$draftId"
}

/** Pure guard rules; no writes to Purchase, inventory, or posted hotel accounts. */
internal object FinanceExpenseStockPolicy {
    const val PENDING = "PENDING"
    const val RECEIVED_DIRECT = "RECEIVED_DIRECT"
    const val LINKED_PO = "LINKED_PO"
    const val CANCELLED = "CANCELLED"

    fun validateLines(
        lines: List<FinanceExpenseStockLine>,
        catalog: List<HotelPurchaseItem>,
        allowedGroups: Set<String>
    ) {
        require(lines.isNotEmpty()) { "Add at least one item and received quantity" }
        require(lines.map { it.itemId }.distinct().size == lines.size) {
            "Each item can appear only once; combine its quantities"
        }
        val known = catalog.associateBy { it.id }
        lines.forEach { line ->
            val item = known[line.itemId] ?: error("Catalog item not found: ${line.itemName}")
            require(item.active && item.categoryId in allowedGroups) { "Item is not available for this expense type: ${line.itemName}" }
            require(item.name == line.itemName && item.unit == line.unit) { "Item or stock unit has changed: ${line.itemName}" }
            require(line.quantity.isFinite() && line.quantity > 0.0 && line.quantity <= 1_000_000.0) {
                "Enter a positive received quantity for ${line.itemName}"
            }
        }
    }

    fun requireOpenReceiptMonth(receivedOn: String, closedMonths: List<String>) {
        val date = LocalDate.parse(receivedOn)
        require(!date.isAfter(LocalDate.now())) { "Received date cannot be in the future" }
        val target = YearMonth.from(date)
        require(closedMonths.none { YearMonth.parse(it) >= target }) {
            "Stock for $target is already closed or a later month is approved"
        }
    }

    fun requireEditable(old: FinanceDemoDraftBill, revised: FinanceDemoDraftBill, link: FinanceExpenseStockLink?) {
        if (link == null || link.state == PENDING || link.state == CANCELLED) return
        require(old.vendorCode == revised.vendorCode &&
            old.invoiceDate == revised.invoiceDate &&
            old.expenseTypeCode == revised.expenseTypeCode &&
            old.department == revised.department &&
            old.amountPaise == revised.amountPaise) {
            "Received/linked stock is locked to this payee, expense type, department, invoice date and amount. Correct stock through a supervised adjustment first."
        }
    }

    fun requireDiscardable(link: FinanceExpenseStockLink?) {
        require(link == null || link.state == PENDING || link.state == CANCELLED) {
            "Cannot discard a draft with confirmed stock or a linked Purchase Order. A supervised reversal is required."
        }
    }

    fun poAmountInPaise(value: Double): Long {
        require(value.isFinite() && value > 0.0) { "Invoice amount must be positive" }
        return BigDecimal.valueOf(value).movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP).longValueExact()
    }
}

/** Single-device Finance DEV stock linkage. No destructive migrations of any older store. */
internal class FinanceExpenseStockLinkStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("finance_dev_expense_stock_links_v15", Context.MODE_PRIVATE)
    private val settings = FinanceGlobalCatalogSettings(context)

    private fun encode(link: FinanceExpenseStockLink) = JSONObject().apply {
        put("draftId", link.draftId)
        put("expenseTypeCode", link.expenseTypeCode)
        put("invoiceMonth", link.invoiceMonth)
        put("groupIds", JSONArray(link.groupIds.sorted()))
        put("state", link.state)
        put("receivedOn", link.receivedOn ?: JSONObject.NULL)
        put("poOrderId", link.poOrderId ?: JSONObject.NULL)
        put("updatedAtMillis", link.updatedAtMillis)
        put("lines", JSONArray().apply {
            link.lines.forEach { item -> put(JSONObject().apply {
                put("itemId", item.itemId)
                put("itemName", item.itemName)
                put("unit", item.unit)
                put("quantity", item.quantity)
            }) }
        })
    }

    private fun decode(o: JSONObject): FinanceExpenseStockLink = FinanceExpenseStockLink(
        draftId = o.getString("draftId"),
        expenseTypeCode = o.getString("expenseTypeCode"),
        invoiceMonth = o.getString("invoiceMonth"),
        groupIds = (o.optJSONArray("groupIds") ?: JSONArray()).let { a ->
            (0 until a.length()).map { i -> a.getString(i) }.toSet()
        },
        state = o.getString("state"),
        receivedOn = o.optString("receivedOn").takeUnless { it.isBlank() || it == "null" },
        poOrderId = o.optString("poOrderId").takeUnless { it.isBlank() || it == "null" },
        updatedAtMillis = o.getLong("updatedAtMillis"),
        lines = (o.optJSONArray("lines") ?: JSONArray()).let { a ->
            (0 until a.length()).map { i -> a.getJSONObject(i).let { line ->
                FinanceExpenseStockLine(
                    line.getString("itemId"), line.getString("itemName"),
                    line.getString("unit"), line.getDouble("quantity")
                )
            } }
        }
    )

    fun all(): List<FinanceExpenseStockLink> {
        val raw = JSONObject(prefs.getString("links", "{}") ?: "{}")
        return raw.keys().asSequence().map { key -> decode(raw.getJSONObject(key)) }.toList()
    }

    fun get(draftId: String): FinanceExpenseStockLink? =
        JSONObject(prefs.getString("links", "{}") ?: "{}").optJSONObject(draftId)?.let(::decode)

    private fun persist(link: FinanceExpenseStockLink, action: String) {
        val all = JSONObject(prefs.getString("links", "{}") ?: "{}")
        val events = JSONArray(prefs.getString("events", "[]") ?: "[]")
        all.put(link.draftId, encode(link))
        events.put(JSONObject().apply {
            put("draftId", link.draftId)
            put("action", action)
            put("state", link.state)
            put("atMillis", link.updatedAtMillis)
            put("directReceiptId", if (link.state == FinanceExpenseStockPolicy.RECEIVED_DIRECT) link.receiptId else JSONObject.NULL)
            put("linkedPoId", link.poOrderId ?: JSONObject.NULL)
        })
        check(prefs.edit().putString("links", all.toString())
            .putString("events", events.toString()).commit()) { "Could not save stock task and audit event" }
    }

    /** Called only after a new local draft has been committed. No retroactive demo drafts are enrolled. */
    fun ensurePending(draft: FinanceDemoDraftBill): FinanceExpenseStockLink? {
        get(draft.draftId)?.let { return it }
        val type = draft.expenseTypeCode ?: return null
        val config = settings.forType(type)
        if (!config.stockable) return null
        require(draft.status == FinanceDemoDraftPolicy.STATUS_ACTIVE)
        val created = FinanceExpenseStockLink(
            draftId = draft.draftId,
            expenseTypeCode = type,
            invoiceMonth = draft.accountingPeriod,
            groupIds = config.groupIds,
            state = FinanceExpenseStockPolicy.PENDING,
            updatedAtMillis = System.currentTimeMillis()
        )
        persist(created, "PENDING_CREATED")
        return created
    }

    /** The bookkeeping draft is saved first; only this link is completed by stock confirmation. */
    fun receiveDirect(draft: FinanceDemoDraftBill, receivedOn: String, lines: List<FinanceExpenseStockLine>) {
        val before = get(draft.draftId) ?: error("Stock task not found; reopen the expense")
        require(before.state == FinanceExpenseStockPolicy.PENDING) { "This expense stock has already been completed" }
        require(before.expenseTypeCode == draft.expenseTypeCode) { "Expense type changed; reopen the stock task" }
        FinanceExpenseStockPolicy.requireOpenReceiptMonth(receivedOn, HotelMonthlyStockStore(context).all().map { it.month })
        FinanceExpenseStockPolicy.validateLines(lines, HotelPurchaseCustomItemStore(context).catalogItems(), before.groupIds)
        persist(before.copy(
            state = FinanceExpenseStockPolicy.RECEIVED_DIRECT,
            receivedOn = receivedOn,
            lines = lines.toList(),
            updatedAtMillis = System.currentTimeMillis()
        ), "DIRECT_RECEIPT_CONFIRMED")
    }

    /** PO receipt quantities already appear in stock. Linking never writes another goods receipt. */
    fun attachVerifiedOrder(draft: FinanceDemoDraftBill, order: HotelPurchaseOrder) {
        val before = get(draft.draftId) ?: error("Stock task not found")
        require(before.state == FinanceExpenseStockPolicy.PENDING) { "This stock task is no longer pending" }
        require(draft.vendorCode == order.vendorId) { "Select the same registered vendor as this Purchase Order" }
        require(order.status == "PLACED") { "Only placed Purchase Orders may be attached" }
        require(all().none { it.draftId != draft.draftId && it.poOrderId == order.id && it.state == FinanceExpenseStockPolicy.LINKED_PO }) {
            "Purchase Order is already linked to another expense"
        }
        val receipts = HotelPurchaseGoodsReceiptStore(context).forOrder(order.id)
        require(receipts.isNotEmpty() && receipts.any { receipt -> receipt.lines.any { it.receivedQuantity > 0.0 } }) {
            "Receive goods against the Purchase Order first"
        }
        val invoices = HotelPurchaseInvoiceStore(context)
        require(invoices.isVerifiedFor(order.id, receipts)) { "Verify the invoice against the latest goods receipt first" }
        require(FinanceExpenseStockPolicy.poAmountInPaise(invoices.netAmount(order.id)) == draft.amountPaise) {
            "Expense total does not match the verified PO invoice. Reconcile amounts before linking."
        }
        persist(before.copy(
            state = FinanceExpenseStockPolicy.LINKED_PO,
            poOrderId = order.id,
            updatedAtMillis = System.currentTimeMillis()
        ), "VERIFIED_PO_ATTACHED")
    }

    fun synchronizeAfterChange(draft: FinanceDemoDraftBill) {
        val old = get(draft.draftId)
        if (draft.status == FinanceDemoDraftPolicy.STATUS_DISCARDED) {
            if (old != null) {
                FinanceExpenseStockPolicy.requireDiscardable(old)
                persist(old.copy(state = FinanceExpenseStockPolicy.CANCELLED, updatedAtMillis = System.currentTimeMillis()), "PENDING_CANCELLED")
            }
            return
        }
        if (old == null) {
            ensurePending(draft)
        } else if (old.state == FinanceExpenseStockPolicy.PENDING) {
            val config = settings.forType(draft.expenseTypeCode ?: "")
            persist(old.copy(
                expenseTypeCode = draft.expenseTypeCode ?: old.expenseTypeCode,
                invoiceMonth = draft.accountingPeriod,
                groupIds = config.groupIds,
                state = if (config.stockable) FinanceExpenseStockPolicy.PENDING else FinanceExpenseStockPolicy.CANCELLED,
                updatedAtMillis = System.currentTimeMillis()
            ), "PENDING_UPDATED_AFTER_EDIT")
        } else if (old.state == FinanceExpenseStockPolicy.CANCELLED) {
            // Reopening an already discarded draft is unsupported. Do not resurrect links.
            Unit
        }
    }

    fun pendingThroughMonth(month: String): List<FinanceExpenseStockLink> = all().filter {
        it.state == FinanceExpenseStockPolicy.PENDING && it.invoiceMonth <= month
    }

    fun directReceiptsForMonth(month: String): List<FinanceExpenseStockLink> = all().filter {
        it.state == FinanceExpenseStockPolicy.RECEIVED_DIRECT &&
            it.receivedOn?.let { date -> YearMonth.from(LocalDate.parse(date)).toString() == month } == true
    }

    fun hasDirectItemHistory(itemId: String): Boolean = all().any { link ->
        link.state == FinanceExpenseStockPolicy.RECEIVED_DIRECT && link.lines.any { it.itemId == itemId }
    }
}
