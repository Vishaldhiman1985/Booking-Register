package com.example.bookingregister.finance.dev

import android.graphics.Color
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.bookingregister.finance.domain.FinanceV22PayrollAuditPolicy
import com.example.bookingregister.finance.domain.FinanceV22StockCostPolicy
import com.example.bookingregister.finance.domain.FinanceV23ProfitBridgePolicy
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

/** Local departmental profitability BRIDGE, not final hotel accounts, tax accounts, or a live PMS sync. */
internal class FinanceV23ProfitBridgeScreen(private val activity: AppCompatActivity) {
    private val policy = FinanceV23ProfitBridgePolicy
    private val ink = Color.rgb(32, 47, 63)
    private val muted = Color.rgb(96, 110, 123)
    private val caution = Color.rgb(151, 91, 21)
    private val positive = Color.rgb(15, 113, 79)
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density + 0.5f).toInt()
    private fun column() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(15),dp(8),dp(15),dp(16))
    }
    private fun label(message: String, font: Float = 13f, color: Int = ink) = TextView(activity).apply {
        text = message; textSize = font; setTextColor(color); setPadding(0,dp(4),0,dp(4))
    }
    private fun money(paise: Long) = FinanceFixedPolicy.rupees(paise)
    private fun button(caption: String, click: () -> Unit) = Button(activity).apply {
        text = caption; isAllCaps = false; setOnClickListener { click() }
    }
    private fun warn(error: Throwable) {
        Toast.makeText(activity,error.message ?: "P&L review could not be loaded",Toast.LENGTH_LONG).show()
    }
    private data class Source(val key: String,val title: String,val amountPaise: Long,
                              val suggested: FinanceV23ProfitBridgePolicy.Center?)
    private data class Snapshot(val result: FinanceV23ProfitBridgePolicy.Result,
                                val sources: List<Source>, val stockReview: String, val payrollReview: String)

    /** Reads the V17 bill projection; does not issue invoices, payroll, inventory or payments. */
    private fun load(month: String): Snapshot {
        YearMonth.parse(month)
        val context = activity.applicationContext
        val (bills, revenues) = FinanceActivityStore(context).use { store ->
            store.bills().filter { it.period == month } to store.revenueRecords(month)
        }
        val links = FinanceExpenseStockLinkStore(context).all().associateBy { it.draftId }
        val issues = mutableListOf<String>()
        val expenses = bills.map { bill ->
            val link = bill.id.removePrefix("manual:").takeIf { bill.id.startsWith("manual:") }
                ?.let { links[it] }
            if (link?.state == FinanceExpenseStockPolicy.PENDING) {
                issues.add("Pending stock receipt for bill ${bill.id}; inventory classification remains uncertain")
            }
            val purchased = link?.state == FinanceExpenseStockPolicy.RECEIVED_DIRECT ||
                link?.state == FinanceExpenseStockPolicy.LINKED_PO
            FinanceV23ProfitBridgePolicy.Expense(bill.id,month,bill.department,bill.amount,purchased)
        }
        val payroll = FinancePayrollStore(context).use { store ->
            store.issuedRuns().filter { it.period == month }.map {
                FinanceV22PayrollAuditPolicy.Issued(it.id,it.amountPaise)
            }
        }
        val payrollBills = bills.filter { it.source == "PAYROLL" }.map {
            FinanceV22PayrollAuditPolicy.Payable(it.id,it.amount,it.paid,it.due)
        }
        val payrollCheck = FinanceV22PayrollAuditPolicy.reconcile(payroll,payrollBills)
        if (!payrollCheck.reconciled) issues.addAll(payrollCheck.issues.map { "Payroll: $it" })
        val payrollReview = "Issued salary: ${money(payrollCheck.issuedPaise)}; " +
            "ledger outstanding: ${money(payrollCheck.duePaise)}; " +
            if (payrollCheck.reconciled) "matched" else "REVIEW REQUIRED"

        val stockStore = HotelMonthlyStockStore(context)
        val stockMonth = stockStore.all().firstOrNull { it.month == month }
        val consumptions = mutableListOf<FinanceV23ProfitBridgePolicy.StockCost>()
        var stockReview = "No approved stock month; inventory valuation unavailable"
        if (stockMonth == null) {
            issues.add("Monthly physical stock has not been approved")
        } else if (stockStore.receiptsChangedAfterClose(month)) {
            issues.add("Goods receipts changed after stock approval; valuation is stale")
            stockReview = "Stock receipts changed after approval"
        } else {
            val lines = FinanceV22StockValuationScreen.quantityLines(stockMonth)
            val fingerprint = FinanceV22StockCostPolicy.fingerprint(month,stockMonth.approvedAtMillis,
                lines,stockMonth.purchaseReceiptIds)
            val evidenceStore = FinanceV22StockValuationStore(context)
            val valued = mutableListOf<FinanceV22StockCostPolicy.Valued>()
            val incomplete = mutableListOf<String>()
            lines.forEach { line ->
                val stored = evidenceStore.get(month,line.itemId)
                if (stored == null || stored.fingerprint != fingerprint) {
                    incomplete.add(line.name)
                } else {
                    val value = runCatching { FinanceV22StockCostPolicy.value(line,stored.evidence) }.getOrNull()
                    if (value == null) incomplete.add(line.name) else valued.add(value)
                }
            }
            if (incomplete.isNotEmpty()) {
                issues.add("Stock cost evidence missing or stale for ${incomplete.size} item(s): ${incomplete.take(4).joinToString()}")
                stockReview = "Cost evidence incomplete: ${incomplete.size} item(s)"
            } else {
                val totals = FinanceV22StockCostPolicy.totals(valued)
                consumptions.addAll(valued.filter { it.consumedValuePaise > 0L }.map {
                    FinanceV23ProfitBridgePolicy.StockCost(it.itemId,month,it.consumedValuePaise)
                })
                stockReview = "Approved quantities + documented costs: ${money(totals.consumedPaise)} consumed (not posted)"
            }
        }
        val sources = expenses.filter { !it.inventoryPurchase }.map { bill ->
            Source("bill:${bill.id}","Bill ${bill.id} · ${bill.department}",bill.amountPaise,policy.center(bill.department))
        } + consumptions.map { stock ->
            Source("stock:${stock.itemId}","Stock consumption · ${stock.itemId}",stock.consumedPaise,null)
        }
        val splitStore = FinanceV23CostCenterStore(context)
        val overrides = sources.mapNotNull { source ->
            splitStore.get(month,source.key)?.let {
                FinanceV23ProfitBridgePolicy.Assignment(source.key,it.amountPaise,it.split)
            }
        }
        val incomeRows = revenues.mapIndexed { idx, r ->
            FinanceV23ProfitBridgePolicy.Income("local-revenue:$idx",month,r.department,r.amountPaise)
        }
        val result = policy.calculate(month,incomeRows,expenses,consumptions,overrides,issues)
        return Snapshot(result,sources,stockReview,payrollReview)
    }

    fun show(month: String) {
        try { YearMonth.parse(month) } catch (e: Exception) { warn(e); return }
        val root = column()
        root.addView(label("V23 | Departmental profitability bridge · $month",17f))
        root.addView(label("LOCAL DEMO-PROPERTY PREVIEW ONLY. Not a final P&L, GST-adjusted result, " +
            "verified booking-revenue feed, or multi-property statement.",12f,caution))
        val snapshot = runCatching { load(month) }.getOrElse { error ->
            root.addView(label("Unable to build report: ${error.message}",13f,caution))
            AlertDialog.Builder(activity).setTitle("V23 | P&L review")
                .setView(ScrollView(activity).apply { addView(root) })
                .setPositiveButton("Close",null).show()
            return
        }
        val report = snapshot.result
        root.addView(label("Imported revenue in local ledger: ${money(report.importedRevenuePaise)}",13f))
        root.addView(label("Source expense + stock consumption allocated: ${money(report.allocatedCostPaise)}",13f))
        root.addView(label("Excluded linked inventory purchase bills: ${money(report.excludedInventoryPurchasesPaise)} " +
            "(${report.excludedInventoryPurchaseCount} bills), so purchases are not also treated as consumption",12f,muted))
        root.addView(label("Unallocated source cost: ${money(report.unallocatedPaise)}",12f,
            if(report.unallocatedPaise>0) caution else muted))
        root.addView(label(snapshot.stockReview,12f,muted))
        root.addView(label(snapshot.payrollReview,12f,muted))
        root.addView(View(activity).apply { minimumHeight = dp(10) })
        report.rows.forEach { row ->
            root.addView(label(row.center.label.uppercase(),15f,ink))
            root.addView(label("Locally imported sales: ${money(row.revenuePaise)}",13f))
            root.addView(label("Known allocated costs: ${money(row.costPaise)}",13f))
            root.addView(label("Known-record DIFFERENCE (NOT profit): ${money(row.recordedDifferencePaise)}",13f,
                if (report.issues.isEmpty()) positive else caution))
        }
        root.addView(View(activity).apply { minimumHeight = dp(10) })
        root.addView(label("READINESS: ${report.issues.size} unresolved source / allocation issue(s)",14f,
            if(report.issues.isEmpty()) positive else caution))
        if(report.issues.isEmpty()) root.addView(label(
            "No detected internal blockers, but source completeness, ITC, taxes, property mapping and CA approval remain unverified.",
            12f,caution))
        report.issues.forEach { root.addView(label("• $it",12f,caution)) }
        root.addView(View(activity).apply { minimumHeight = dp(10) })
        root.addView(label("ALLOCATE SOURCES TO ROOMS / KITCHEN / SERVICES",14f))
        root.addView(label("Shared bills and stock consumption need explicit allocations. " +
            "Assignments are documented locally and do not post expenses.",11f,muted))
        val store = FinanceV23CostCenterStore(activity.applicationContext)
        val locked = FinancePeriodEntryLock(activity.applicationContext).isLocked(month)
        var currentDialog: AlertDialog? = null
        snapshot.sources.forEach { source ->
            val saved = store.get(month,source.key)
            val summary = if(saved==null) "Source department: ${source.suggested?.label ?: "Unassigned"}"
                else if(saved.amountPaise!=source.amountPaise) "STALE ALLOCATION — source amount changed"
                else "Allocated: R ${saved.split.roomsBps/100.0}% · K ${saved.split.kitchenBps/100.0}% · S ${saved.split.servicesBps/100.0}%"
            root.addView(label("${source.title} | ${money(source.amountPaise)}",12f))
            root.addView(label(summary,11f,if(summary.startsWith("STALE"))caution else muted))
            root.addView(button("Review / allocate cost") {
                openAllocation(month,source,saved?.split) {
                    currentDialog?.dismiss()
                    show(month)
                }
            }.apply { isEnabled = !locked })
        }
        if (locked) root.addView(label("V20 provisional lock active. Reopen with a reason before editing allocations.",12f,caution))
        root.addView(View(activity).apply { minimumHeight = dp(12) })
        root.addView(label("LIMITATIONS: No authenticated revenue import; operating expenses may include recoverable " +
            "GST; shared expenses require documented splits; payroll completeness and stock transfers require " +
            "review. This screen creates no books, tax filings, journals or cash entries.",11f,caution))
        currentDialog = AlertDialog.Builder(activity).setTitle("V23 | Departmental review")
            .setView(ScrollView(activity).apply { addView(root) })
            .setPositiveButton("Close",null).create()
        currentDialog.show()
    }

    private fun openAllocation(month: String,source: Source,
                               existing: FinanceV23ProfitBridgePolicy.Split?,onSaved: () -> Unit) {
        val root = column()
        root.addView(label("${source.title} | ${money(source.amountPaise)}",13f))
        root.addView(label("Enter percentages. Total must equal 100.00%. Provide a defensible allocation basis.",12f,caution))
        val defaults = existing ?: source.suggested?.let(policy::single)
        fun edit(title: String,bps: Int) = EditText(activity).apply {
            hint=title;inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(BigDecimal.valueOf(bps.toLong(),2).toPlainString())
            setSingleLine(true);root.addView(this)
        }
        val rooms=edit("Rooms %",defaults?.roomsBps ?: 0)
        val kitchen=edit("Kitchen %",defaults?.kitchenBps ?: 0)
        val services=edit("Services %",defaults?.servicesBps ?: 0)
        val note=EditText(activity).apply {
            hint="Allocation method / reason (8+ characters)"
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(false);minLines=2;maxLines=4;root.addView(this)
        }
        fun percent(raw: String): Int {
            val p=raw.trim().toBigDecimalOrNull() ?: error("Enter a valid percentage")
            return p.multiply(BigDecimal.valueOf(100)).setScale(0,RoundingMode.UNNECESSARY).intValueExact()
        }
        AlertDialog.Builder(activity).setTitle("Allocate V23 source")
            .setView(ScrollView(activity).apply { addView(root) })
            .setNegativeButton("Cancel",null).setPositiveButton("Save",null)
            .create().apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        try {
                            val split=FinanceV23ProfitBridgePolicy.Split(percent(rooms.text.toString()),percent(kitchen.text.toString()),percent(services.text.toString()))
                            // The source amount is pinned; changed upstream values invalidate the saved split on next read.
                            FinanceV23CostCenterStore(activity).save(month,source.key,source.amountPaise,split,note.text.toString())
                            dismiss()
                            onSaved()
                        } catch(e:Exception) { warn(e) }
                    }
                }
                show()
            }
    }
}
