package com.example.bookingregister.finance.dev

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Quantities only. This store never writes accounting, payment, inventory values or Purchase data. */
data class HotelStockQuantity(val itemId: String, val name: String, val unit: String, val quantity: Double)

data class HotelStockMonth(
    val month: String,
    val opening: List<HotelStockQuantity>,
    val purchased: List<HotelStockQuantity>,
    val closing: List<HotelStockQuantity>,
    val approvedAtMillis: Long,
    val approvedBy: String,
    val purchaseReceiptIds: List<String>
)

object HotelMonthlyStockPolicy {
    private const val EPS = 0.000001
    fun validMonth(month: String): YearMonth = YearMonth.parse(month)

    fun merge(items: List<HotelStockQuantity>): List<HotelStockQuantity> {
        val result = linkedMapOf<String, HotelStockQuantity>()
        for (item in items) {
            require(item.itemId.isNotBlank() && item.name.isNotBlank() && item.unit.isNotBlank())
            require(item.quantity.isFinite() && item.quantity >= 0) { "Negative or invalid stock is not permitted" }
            val previous = result[item.itemId]
            require(previous == null || (previous.unit == item.unit && previous.name == item.name)) {
                "Item has mismatched units: ${item.name}. Resolve units before closing stock."
            }
            result[item.itemId] = item.copy(quantity = (previous?.quantity ?: 0.0) + item.quantity)
        }
        return result.values.toList()
    }

    fun balance(opening: List<HotelStockQuantity>, purchases: List<HotelStockQuantity>, closing: List<HotelStockQuantity>): List<HotelStockQuantity> {
        val starts = merge(opening).associateBy { it.itemId }
        val bought = merge(purchases).associateBy { it.itemId }
        val ends = merge(closing).associateBy { it.itemId }
        val ids = (starts.keys + bought.keys + ends.keys).distinct()
        return ids.map { id ->
            val example = starts[id] ?: bought[id] ?: ends.getValue(id)
            for (item in listOfNotNull(starts[id], bought[id], ends[id])) {
                require(item.unit == example.unit && item.name == example.name) { "Item/unit changed: ${example.name}" }
            }
            val available = (starts[id]?.quantity ?: 0.0) + (bought[id]?.quantity ?: 0.0)
            val counted = ends[id]?.quantity ?: throw IllegalArgumentException("Count remaining stock for ${example.name}, including 0")
            require(counted <= available + EPS) { "${example.name}: closing stock exceeds opening + receipts; check missing purchases or stock transfers" }
            example.copy(quantity = (available - counted).coerceAtLeast(0.0))
        }
    }
}

/** Uses a new, Finance DEV-only preferences file. Existing PO and receipt stores are untouched. */
class HotelMonthlyStockStore(context: Context) {
    private val prefs = context.getSharedPreferences("finance_dev_monthly_stock_v1", Context.MODE_PRIVATE)
    private val receipts = HotelPurchaseGoodsReceiptStore(context)

    fun all(): List<HotelStockMonth> {
        val a = JSONArray(prefs.getString("months", "[]") ?: "[]")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            HotelStockMonth(
                month = o.getString("month"),
                opening = readLines(o.getJSONArray("opening")),
                purchased = readLines(o.getJSONArray("purchased")),
                closing = readLines(o.getJSONArray("closing")),
                approvedAtMillis = o.getLong("approvedAtMillis"),
                approvedBy = o.optString("approvedBy", ""),
                purchaseReceiptIds = (o.optJSONArray("purchaseReceiptIds") ?: JSONArray()).let { ids ->
                    (0 until ids.length()).map { ids.getString(it) }
                }
            )
        }.sortedBy { it.month }
    }

    private fun readLines(a: JSONArray) = (0 until a.length()).map { i ->
        a.getJSONObject(i).let { o -> HotelStockQuantity(o.getString("itemId"), o.getString("name"), o.getString("unit"), o.getDouble("quantity")) }
    }

    private fun encodeLines(lines: List<HotelStockQuantity>) = JSONArray().apply {
        lines.forEach { q -> put(JSONObject().apply {
            put("itemId", q.itemId); put("name", q.name); put("unit", q.unit); put("quantity", q.quantity)
        }) }
    }

    private fun encode(m: HotelStockMonth) = JSONObject().apply {
        put("month", m.month); put("opening", encodeLines(m.opening))
        put("purchased", encodeLines(m.purchased)); put("closing", encodeLines(m.closing))
        put("approvedAtMillis", m.approvedAtMillis); put("approvedBy", m.approvedBy)
        put("purchaseReceiptIds", JSONArray(m.purchaseReceiptIds))
    }

    fun purchased(month: String): Pair<List<HotelStockQuantity>, List<String>> {
        val period = HotelMonthlyStockPolicy.validMonth(month)
        val matching = receipts.all().filter {
            val date = Instant.ofEpochMilli(it.receivedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            YearMonth.from(date) == period
        }
        val quantities = matching.flatMap { receipt -> receipt.lines.filter { it.receivedQuantity > 0 }.map {
            HotelStockQuantity(it.itemId, it.itemName, it.unit, it.receivedQuantity)
        } }
        return HotelMonthlyStockPolicy.merge(quantities) to matching.map { it.id }.sorted()
    }

    /** Initial stock is entered once before the first monthly close; later months inherit the preceding approved close. */
    fun opening(month: String): List<HotelStockQuantity>? {
        val previous = HotelMonthlyStockPolicy.validMonth(month).minusMonths(1).toString()
        return all().find { it.month == previous }?.closing
            ?: initialOpening(month)
    }

    fun savedInitialMonths(): List<String> {
        val root = JSONObject(prefs.getString("initialOpenings", "{}") ?: "{}")
        return root.keys().asSequence().toList().sorted()
    }

    fun initialOpening(month: String): List<HotelStockQuantity>? {
        val root = JSONObject(prefs.getString("initialOpenings", "{}") ?: "{}")
        return root.optJSONArray(month)?.let(::readLines)
    }

    fun setInitialOpening(month: String, quantities: List<HotelStockQuantity>) {
        HotelMonthlyStockPolicy.validMonth(month)
        require(all().none { it.month == month }) { "Month is already closed" }
        require(all().none { it.month < month }) { "Cannot introduce a new initial stock after stock closing has begun" }
        val merged = HotelMonthlyStockPolicy.merge(quantities)
        val root = JSONObject(prefs.getString("initialOpenings", "{}") ?: "{}")
        require(!root.has(month)) { "Initial opening stock is already saved; use Edit Opening with a reason" }
        root.put(month, encodeLines(merged))
        check(prefs.edit().putString("initialOpenings", root.toString()).commit()) { "Opening stock was not saved" }
    }

    fun approve(month: String, counts: List<HotelStockQuantity>, managerName: String): HotelStockMonth {
        val period = HotelMonthlyStockPolicy.validMonth(month)
        require(period < YearMonth.now()) { "Only completed months may be closed" }
        require(managerName.isNotBlank()) { "Manager name is required" }
        val history = all()
        require(history.none { it.month == month }) { "This month is already closed" }
        require(history.none { it.month > month }) { "Close stock months in chronological order" }
        val beginning = opening(month) ?: throw IllegalStateException("Set initial opening stock or close the previous month first")
        val previous = history.lastOrNull()
        if (previous != null) require(YearMonth.parse(previous.month).plusMonths(1) == period) {
            "Close the intervening month before proceeding"
        }
        val (received, ids) = purchased(month)
        val mergedCounts = completeClosing(beginning, received, counts)
        HotelMonthlyStockPolicy.balance(beginning, received, mergedCounts)
        val result = HotelStockMonth(month, HotelMonthlyStockPolicy.merge(beginning), received, mergedCounts,
            System.currentTimeMillis(), managerName.trim(), ids)
        val next = JSONArray().apply { (history + result).forEach { put(encode(it)) } }
        check(prefs.edit().putString("months", next.toString()).commit()) { "Stock closing was not saved" }
        return result
    }

    /** Missing count entries mean physical zero at approval, not uncounted. */
    private fun completeClosing(
        opening: List<HotelStockQuantity>,
        received: List<HotelStockQuantity>,
        entered: List<HotelStockQuantity>
    ): List<HotelStockQuantity> {
        val enteredById = HotelMonthlyStockPolicy.merge(entered).associateBy { it.itemId }
        val baseline = HotelMonthlyStockPolicy.merge(opening + received).associateBy { it.itemId }
        val merged = linkedMapOf<String, HotelStockQuantity>()
        baseline.forEach { (id, item) ->
            val chosen = enteredById[id]
            if (chosen != null) require(chosen.unit == item.unit && chosen.name == item.name) {
                "The name or unit changed for ${item.name}; reconcile the catalog first"
            }
            merged[id] = item.copy(quantity = chosen?.quantity ?: 0.0)
        }
        enteredById.forEach { (id, item) -> merged.putIfAbsent(id, item) }
        return merged.values.toList()
    }

    /** Supervised amendments retain an append-only audit of before/after quantities. */
    private fun updatedAmendAudit(month: String, kind: String, reason: String, before: List<HotelStockQuantity>, after: List<HotelStockQuantity>): String {
        val history = JSONArray(prefs.getString("stockAmendments", "[]") ?: "[]")
        history.put(JSONObject().apply {
            put("month", month); put("kind", kind); put("reason", reason.trim())
            put("timestampMillis", System.currentTimeMillis())
            put("before", encodeLines(before)); put("after", encodeLines(after))
        })
        return history.toString()
    }

    fun corrections(month: String): List<String> {
        val history = JSONArray(prefs.getString("stockAmendments", "[]") ?: "[]")
        return (0 until history.length()).mapNotNull { idx ->
            val entry = history.getJSONObject(idx)
            if (entry.optString("month") != month) null
            else "${entry.optString("kind")} · ${entry.optString("reason")}"
        }
    }

    fun amendOpening(month: String, entered: List<HotelStockQuantity>, reason: String) {
        require(reason.trim().length >= 5) { "Enter a meaningful correction reason (at least 5 characters)" }
        val before = initialOpening(month) ?: throw IllegalStateException("There is no saved initial opening for this month")
        require(all().none { it.month >= month }) { "Opening cannot be changed after this or a later month was approved" }
        val after = HotelMonthlyStockPolicy.merge(entered)
        val root = JSONObject(prefs.getString("initialOpenings", "{}") ?: "{}")
        val audit = updatedAmendAudit(month, "OPENING", reason, before, after)
        root.put(month, encodeLines(after))
        check(prefs.edit().putString("initialOpenings", root.toString())
            .putString("stockAmendments", audit).commit()) { "Opening correction was not saved" }
    }

    fun amendClosing(month: String, entered: List<HotelStockQuantity>, reason: String, managerName: String) {
        require(reason.trim().length >= 5) { "Enter a meaningful correction reason (at least 5 characters)" }
        require(managerName.isNotBlank()) { "Approving manager is required" }
        val history = all()
        val current = history.find { it.month == month } ?: throw IllegalStateException("Month has not been approved")
        require(history.none { it.month > month }) { "Later months are approved. Reconcile later months before changing this closing" }
        require(!receiptsChangedAfterClose(month)) { "Receipts changed after approval; reconcile purchases before correcting stock" }
        val after = completeClosing(current.opening, current.purchased, entered)
        HotelMonthlyStockPolicy.balance(current.opening, current.purchased, after)
        val audit = updatedAmendAudit(month, "CLOSING by ${managerName.trim()}", reason, current.closing, after)
        val updated = current.copy(closing = after, approvedAtMillis = System.currentTimeMillis(), approvedBy = managerName.trim())
        val next = JSONArray().apply { history.map { if (it.month == month) updated else it }.forEach { put(encode(it)) } }
        check(prefs.edit().putString("months", next.toString())
            .putString("stockAmendments", audit).commit()) { "Closing correction was not saved" }
    }

    /** A later backdated receipt must be investigated; approved stock is never silently recomputed. */
    fun receiptsChangedAfterClose(month: String): Boolean {
        val closed = all().find { it.month == month } ?: return false
        return purchased(month).second != closed.purchaseReceiptIds
    }
}
