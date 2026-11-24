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
import java.time.YearMonth

/** V22 priced stock review (manual documentary evidence) + issued-payroll payable cross-check. */
internal class FinanceV22StockValuationScreen(private val activity: AppCompatActivity) {
    private val p = FinanceV22StockCostPolicy
    private val dark = Color.rgb(32, 47, 63)
    private val muted = Color.rgb(93, 106, 120)
    private val amber = Color.rgb(151, 91, 21)
    private val green = Color.rgb(15, 113, 79)
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density + .5f).toInt()
    private fun col() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15),dp(8),dp(15),dp(14))
    }
    private fun text(value: String, size: Float = 13f, color: Int = dark): TextView = TextView(activity).apply {
        text=value; textSize=size; setTextColor(color); setPadding(0,dp(5),0,dp(5))
    }
    private fun button(label: String, action: () -> Unit) = Button(activity).apply {
        text=label;isAllCaps=false;setOnClickListener { action() }
    }
    private fun rupees(paise: Long) = FinanceFixedPolicy.rupees(paise)
    private fun warn(error: Throwable) {
        Toast.makeText(activity, error.message ?: "Valuation not available", Toast.LENGTH_LONG).show()
    }

    companion object {
        /** The approved monthly stock is the sole physical-quantity source. No phantom PO receipts. */
        fun quantityLines(month: HotelStockMonth): List<FinanceV22StockCostPolicy.Quantity> {
            val opening = HotelMonthlyStockPolicy.merge(month.opening).associateBy { it.itemId }
            val received = HotelMonthlyStockPolicy.merge(month.purchased).associateBy { it.itemId }
            val closing = HotelMonthlyStockPolicy.merge(month.closing).associateBy { it.itemId }
            return (opening.keys + received.keys + closing.keys).distinct().sorted().map { id ->
                val example = opening[id] ?: received[id] ?: closing.getValue(id)
                listOfNotNull(opening[id],received[id],closing[id]).forEach {
                    require(it.name == example.name && it.unit == example.unit) { "Item name/unit mismatch: ${example.name}" }
                }
                FinanceV22StockCostPolicy.Quantity(id,example.name,example.unit,
                    opening[id]?.quantity ?: 0.0, received[id]?.quantity ?: 0.0,
                    closing[id]?.quantity ?: 0.0)
            }
        }
    }

    fun show(month: String) {
        try { YearMonth.parse(month) } catch (e: Exception) { warn(e); return }
        val root = col()
        root.addView(text("V22 | $month Stock cost & payroll",18f))
        root.addView(text("REVIEW ONLY. Rates are manually supported cost evidence, not posted journal entries or certified COGS.",12f,amber))
        val scroll = ScrollView(activity).apply { addView(root) }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("V22 | Cost & salary checks")
            .setView(scroll).setPositiveButton("Close",null).create()
        val stockStore = HotelMonthlyStockStore(activity.applicationContext)
        val snapshot = runCatching { stockStore.all().firstOrNull { it.month == month } }
            .getOrElse { root.addView(text("Stock unavailable: ${it.message}",12f,amber)); null }
        if (snapshot == null) {
            root.addView(text("No approved stock close for $month. Approve physical counts before entering unit costs.",13f,amber))
        } else {
            runCatching { showStock(root, dialog, month, snapshot, stockStore) }
                .onFailure { root.addView(text("Stock valuation needs review: ${it.message}",12f,amber)) }
        }
        runCatching { showPayroll(root, month) }
            .onFailure { root.addView(text("Payroll reconciliation unavailable: ${it.message}",12f,amber)) }
        dialog.show()
    }

    private fun showStock(root: LinearLayout, dialog: AlertDialog, month: String,
                          stock: HotelStockMonth, stockStore: HotelMonthlyStockStore) {
        val lines = quantityLines(stock)
        val fingerprint = p.fingerprint(month,stock.approvedAtMillis,lines,stock.purchaseReceiptIds)
        val lateReceipts = stockStore.receiptsChangedAfterClose(month)
        root.addView(text("Approved stock: ${stock.approvedBy} | ${lines.size} items",15f))
        if (lateReceipts) root.addView(text("BLOCKED: goods receipts changed after closing. Reconcile stock before valuation.",12f,amber))
        val evidenceStore = FinanceV22StockValuationStore(activity.applicationContext)
        val valued = mutableListOf<FinanceV22StockCostPolicy.Valued>()
        val missing = mutableListOf<String>()
        lines.forEach { line ->
            val stored = evidenceStore.get(month,line.itemId)
            val fresh = stored != null && stored.fingerprint == fingerprint
            val value = if (fresh) runCatching { p.value(line,stored!!.evidence) }.getOrNull() else null
            if (value != null) valued.add(value) else missing.add(line.name)
            val title = "${line.name} (${line.unit}) | O ${line.opening} + R ${line.received} − C ${line.closing}"
            root.addView(text(title,12f,dark))
            root.addView(text(when {
                value != null -> "Consumed ${line.opening + line.received - line.closing} ${line.unit} | ${rupees(value.consumedValuePaise)}"
                stored != null && !fresh -> "STALE: stock quantities/receipt snapshot changed — recheck prices"
                else -> "MISSING: cost and invoice evidence required"
            },12f,if(value != null)green else amber))
            root.addView(button(if(stored == null) "Enter cost for ${line.name}" else "Review cost for ${line.name}") {
                openCostDialog(dialog,month,fingerprint,line,stored?.evidence,lateReceipts)
            }.apply { isEnabled = !lateReceipts && !FinancePeriodEntryLock(activity).isLocked(month) })
            root.addView(View(activity).apply { minimumHeight = dp(6) })
        }
        if (!lateReceipts && missing.isEmpty()) {
            val t=p.totals(valued)
            root.addView(text("PRICED STOCK SUMMARY",15f,green))
            root.addView(text("Opening: ${rupees(t.openingPaise)} | Receipts: ${rupees(t.receivedPaise)}",13f))
            root.addView(text("Closing: ${rupees(t.closingPaise)} | Indicative consumption: ${rupees(t.consumedPaise)}",13f,green))
            root.addView(text("Periodic weighted-average method. Manual costs require CA verification; do not add consumption again to expense bills.",11f,amber))
        } else {
            root.addView(text("MONETARY TOTAL WITHHELD: ${missing.size} unpriced/stale item(s)${if(lateReceipts)"; changed receipts" else ""}.",12f,amber))
        }
    }

    private fun openCostDialog(parent: AlertDialog,month: String,fingerprint: String,
                               line: FinanceV22StockCostPolicy.Quantity,
                               existing: FinanceV22StockCostPolicy.Evidence?, blocked: Boolean) {
        if (blocked) return
        val view=col()
        view.addView(text("${line.name} • ${line.unit} | Opening ${line.opening}, received ${line.received}, closing ${line.closing}",13f))
        view.addView(text("Supply the actual per-unit cost in INR, using a documented basis. Exclude recoverable ITC only where verified by your accountant.",11f,amber))
        fun input(label: String, value: String, amount: Boolean = false): EditText = EditText(activity).apply {
            hint=label;setText(value)
            inputType= if (amount) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            view.addView(this)
        }
        fun price(v: Long?)=v?.let { java.math.BigDecimal.valueOf(it,2).stripTrailingZeros().toPlainString() } ?: ""
        val openPrice=if(line.opening>0.0)input("Opening rate ₹ per ${line.unit}",price(existing?.openingUnitPaise),true) else null
        val openReference=if(line.opening>0.0)input("Opening stock valuation reference",existing?.openingReference ?: "") else null
        val receivedPrice=if(line.received>0.0)input("Received rate ₹ per ${line.unit}",price(existing?.receivedUnitPaise),true) else null
        val receivedReference=if(line.received>0.0)input("Purchase invoice / allocation reference",existing?.receivedReference ?: "") else null
        val reason=input("Why is this cost supported/updated? (8+ characters)","")
        AlertDialog.Builder(activity).setTitle("V22 | Document unit costs")
            .setView(ScrollView(activity).apply { addView(view) })
            .setNegativeButton("Cancel",null).setPositiveButton("Save evidence",null)
            .create().apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        try {
                            val evidence = FinanceV22StockCostPolicy.Evidence(
                                openPrice?.text?.toString()?.let(p::rupeesToPaise),
                                receivedPrice?.text?.toString()?.let(p::rupeesToPaise),
                                openReference?.text?.toString()?.trim() ?: "",
                                receivedReference?.text?.toString()?.trim() ?: ""
                            )
                            FinanceV22StockValuationStore(activity).save(line,month,fingerprint,evidence,reason.text.toString())
                            dismiss()
                            parent.dismiss()
                            show(month)
                        } catch(e:Exception) { warn(e) }
                    }
                }
                show()
            }
    }

    private fun showPayroll(root: LinearLayout,month: String) {
        root.addView(text("PAYROLL | Issued run versus existing payable",15f))
        val payroll = FinancePayrollStore(activity.applicationContext).use { store ->
            store.issuedRuns().filter { it.period == month }.map {
                FinanceV22PayrollAuditPolicy.Issued(it.id,it.amountPaise)
            }
        }
        // No synchronise() call here: avoid accidental changes to manual draft projections.
        val payable = FinanceActivityStore(activity.applicationContext).use { store ->
            store.bills().filter { it.source == "PAYROLL" && it.period == month }.map {
                FinanceV22PayrollAuditPolicy.Payable(it.id,it.amount,it.paid,it.due)
            }
        }
        val result = FinanceV22PayrollAuditPolicy.reconcile(payroll,payable)
        root.addView(text("Issued salary: ${rupees(result.issuedPaise)} | Runs: ${result.issuedCount}",13f))
        root.addView(text("Allocated/settled: ${rupees(result.paidPaise)} | Outstanding: ${rupees(result.duePaise)}",13f))
        if(result.reconciled) root.addView(text("Salary payable projection reconciles for this period.",12f,green))
        else {
            root.addView(text("PAYROLL REVIEW REQUIRED (${result.issues.size})",12f,amber))
            result.issues.forEach { root.addView(text("• $it",11f,amber)) }
        }
        root.addView(text("Salary expense is already represented by issued payroll bills. No extra salary expense or bank payment is created here.",11f,muted))
    }
}
