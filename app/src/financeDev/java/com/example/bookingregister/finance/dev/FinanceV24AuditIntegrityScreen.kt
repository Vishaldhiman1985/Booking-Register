package com.example.bookingregister.finance.dev

import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bookingregister.finance.domain.FinanceV22StockCostPolicy
import com.example.bookingregister.finance.domain.FinanceV23ProfitBridgePolicy
import com.example.bookingregister.finance.domain.FinanceV24AuditPolicy
import com.example.bookingregister.finance.domain.FinanceV24Reconciliation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

/** Read-only V24 cross-source checks, local human-review history and user-initiated CA text export.
 * No balances are posted or corrected, and no statutory or final period closure is available. */
internal class FinanceV24AuditIntegrityScreen(
    private val activity: AppCompatActivity,
    private val drafts: List<FinanceDemoDraftBill>
) {
    private val policy = FinanceV24AuditPolicy
    private val dark = Color.rgb(34, 48, 65)
    private val muted = Color.rgb(93, 106, 118)
    private val warning = Color.rgb(164, 90, 22)
    private val danger = Color.rgb(171, 42, 56)
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density + 0.5f).toInt()
    private fun column() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(9), dp(15), dp(16))
    }
    private fun label(value: String, size: Float = 12f, color: Int = dark) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(4), 0, dp(4))
    }
    private fun action(value: String, handler: () -> Unit) = Button(activity).apply {
        text = value; isAllCaps = false; setOnClickListener { handler() }
    }
    private fun cash(paise: Long) = FinanceFixedPolicy.rupees(paise)
    private fun notify(error: Throwable) {
        Toast.makeText(activity, error.message ?: "Audit review failed", Toast.LENGTH_LONG).show()
    }

    /** Never imports live bookings. Uses the same V24 projection as the control centre. */
    private fun load(month: String): FinanceV24AuditPolicy.Report {
        YearMonth.parse(month)
        val app = activity.applicationContext
        val core = FinanceV24ControlCenter(activity, drafts).load(month)
        val (bills, revenues) = FinanceActivityStore(app).use { store ->
            store.bills().filter { it.period == month } to store.revenueRecords(month)
        }
        val salary = FinancePayrollStore(app).use { store ->
            store.issuedRuns().filter { it.period == month }.map {
                FinanceV24AuditPolicy.IssuedSalary(it.id, it.period, it.amountPaise)
            }
        }
        val evidence = FinanceV21TaxEvidenceStore(app).use { store ->
            store.entries(month).map {
                FinanceV24AuditPolicy.TaxEvidence(it.id, it.period, it.kind.name,
                    it.amountPaise, it.settledPaise, it.cancelled)
            }
        }
        val rentTax = FinanceLeaseTaxStore(app).use { store ->
            store.bills().filter { it.month == month }.map {
                FinanceV24AuditPolicy.RentTax(it.id, it.month, it.mode, it.taxPaise, it.paidPaise)
            }
        }
        val stockStore = HotelMonthlyStockStore(app)
        val stockMonth = stockStore.all().firstOrNull { it.month == month }
        val sourceSignatures = mutableListOf<String>()
        val valued = mutableListOf<FinanceV22StockCostPolicy.Valued>()
        val stock = stockMonth?.let { approved ->
            val lines = FinanceV22StockValuationScreen.quantityLines(approved)
            val currentHash = FinanceV22StockCostPolicy.fingerprint(month, approved.approvedAtMillis,
                lines, approved.purchaseReceiptIds)
            sourceSignatures.add("physical-stock:$currentHash")
            val evidenceStore = FinanceV22StockValuationStore(app)
            lines.forEach { line ->
                val stored = evidenceStore.get(month, line.itemId)
                sourceSignatures.add("cost:${line.itemId}:${stored?.toString() ?: "MISSING"}")
                if (stored?.fingerprint == currentHash) {
                    runCatching { FinanceV22StockCostPolicy.value(line, stored.evidence) }
                        .getOrNull()?.let { valued.add(it) }
                }
            }
            val changed = stockStore.receiptsChangedAfterClose(month)
            FinanceV24AuditPolicy.Stock(true, changed, lines.size, valued.size,
                if (changed || valued.size != lines.size) null
                else FinanceV22StockCostPolicy.totals(valued).consumedPaise)
        }
        val links = FinanceExpenseStockLinkStore(app).all().associateBy { it.draftId }
        val allocations = mutableListOf<FinanceV24AuditPolicy.Allocation>()
        val allocationsStore = FinanceV23CostCenterStore(app)
        bills.forEach { bill ->
            val link = if (bill.id.startsWith("manual:")) links[bill.id.removePrefix("manual:")] else null
            val inventoryPurchase = link?.state == FinanceExpenseStockPolicy.RECEIVED_DIRECT ||
                link?.state == FinanceExpenseStockPolicy.LINKED_PO
            if (!inventoryPurchase) {
                val key = "bill:${bill.id}"
                val recorded = allocationsStore.get(month, key)
                allocations.add(FinanceV24AuditPolicy.Allocation(key, bill.amount, recorded?.amountPaise,
                    FinanceV23ProfitBridgePolicy.center(bill.department) == null))
            }
        }
        if (stock?.consumptionPaise != null) {
            valued.filter { it.consumedValuePaise > 0L }.forEach { item ->
                val key = "stock:${item.itemId}"
                allocations.add(FinanceV24AuditPolicy.Allocation(key, item.consumedValuePaise,
                    allocationsStore.get(month, key)?.amountPaise, true))
            }
        }
        val allocationSnapshots = allocationsStore.allForMonth(month)
        allocationSnapshots.forEach { sourceSignatures.add("allocation:${it.toString()}") }
        evidence.forEach { sourceSignatures.add("tax-entry:${it.toString()}") }
        val expected = allocations.map { it.key }.toSet()
        val orphan = allocationSnapshots.map { it.key }.filter { it !in expected }
        val coreIssues = core.issues.map {
            FinanceV24AuditPolicy.Issue(if (it.severity == FinanceV24Reconciliation.Severity.BLOCKER)
                FinanceV24AuditPolicy.Severity.BLOCKER else FinanceV24AuditPolicy.Severity.REVIEW, "CORE_${it.code}", it.detail)
        }
        val typedBills = bills.map { bill ->
            val link = if (bill.id.startsWith("manual:")) links[bill.id.removePrefix("manual:")] else null
            FinanceV24AuditPolicy.Bill(bill.id, bill.period, bill.source, bill.department, bill.amount,
                bill.paid, bill.due, link?.state == FinanceExpenseStockPolicy.RECEIVED_DIRECT ||
                    link?.state == FinanceExpenseStockPolicy.LINKED_PO)
        }
        return policy.calculate(FinanceV24AuditPolicy.Input(
            month = month, coreFingerprint = core.fingerprint, coreIssues = coreIssues,
            bills = typedBills, salaryRuns = salary, evidence = evidence, rentTaxes = rentTax,
            revenue = revenues.map { FinanceV24AuditPolicy.Revenue(it.date,it.department,it.amountPaise) },
            stock = stock, allocations = allocations, orphanAllocationKeys = orphan,
            provisionalLock = FinancePeriodEntryLock(app).isLocked(month),
            sourceSignatures = sourceSignatures
        ))
    }

    fun show(month: String) {
        YearMonth.parse(month)
        val root = column()
        val heading = label("V24 | Source integrity and CA review · $month", 17f)
        root.addView(heading)
        root.addView(label("FINANCE DEV / LOCAL RECORDS ONLY. Not a final close, GST return, statutory audit, or certified hotel P&L.", 12f, warning))
        val body = column()
        body.addView(label("Rechecking source records and review evidence...", 13f, muted))
        root.addView(body)
        val dialog = AlertDialog.Builder(activity).setTitle("V24 | Audit readiness")
            .setView(ScrollView(activity).apply { addView(root) })
            .setPositiveButton("Close", null).create()
        dialog.show()
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { load(month) } }
            if (!dialog.isShowing || activity.isFinishing || activity.isDestroyed) return@launch
            body.removeAllViews()
            result.fold(onSuccess = { report ->
                render(body, report)
            }, onFailure = { error ->
                body.addView(label("Cannot prepare an audit packet: ${error.message ?: "source read failed"}", 13f, danger))
                body.addView(label("No figures have been declared verified. Resolve the source error and retry.", 12f, warning))
            })
        }
    }

    private fun render(body: LinearLayout, report: FinanceV24AuditPolicy.Report) {
        body.addView(label("FINAL MONTH CLOSE: DISABLED", 16f, danger))
        body.addView(label("${report.blockerCount} blocking exceptions  |  ${report.reviewCount} outstanding reviews", 14f,
            if (report.blockerCount > 0) danger else warning))
        body.addView(label("Bills recorded: ${cash(report.billGrossPaise)} | Outstanding: ${cash(report.billDuePaise)}", 12f))
        body.addView(label("Issued salary total (included in bills, not added twice): ${cash(report.salaryGrossPaise)}", 12f))
        body.addView(label("Declared tax evidence payable: ${cash(report.taxEvidencePayablePaise)} | Open: ${cash(report.taxEvidenceOpenPaise)}", 12f))
        body.addView(label("Potential tax credits: ${cash(report.potentialTaxCreditsPaise)} (NOT verified or netted)", 12f, warning))
        body.addView(label("Separate rent GST/RCM outstanding: ${cash(report.rentTaxOpenPaise)} (NOT combined with V21 evidence)", 12f))
        body.addView(label("Stock consumption preview: ${report.stockConsumedPaise?.let(::cash) ?: "WITHHELD — insufficient evidence"}", 12f,
            if (report.stockConsumedPaise == null) danger else dark))
        body.addView(label("Locally imported revenue evidence: ${cash(report.revenueEvidencePaise)} (coverage not certified)", 12f))
        body.addView(label("Provisional V20 entry lock: ${if (report.provisionalLock) "Active" else "Not active"}", 12f, muted))
        body.addView(View(activity).apply { minimumHeight = dp(10) })
        body.addView(label("UNRESOLVED EXCEPTIONS", 14f))
        report.issues.forEach { issue ->
            body.addView(label("${issue.severity} · ${issue.code} — ${issue.detail}", 12f,
                if (issue.severity == FinanceV24AuditPolicy.Severity.BLOCKER) danger else warning))
        }
        body.addView(View(activity).apply { minimumHeight = dp(11) })
        body.addView(label("Local source fingerprint: ${report.fingerprint}", 10f, muted))
        val history = FinanceV24AuditReviewStore(activity.applicationContext)
        val latest = history.latest(report.month)
        val historyStatus = label(when {
            latest == null -> "No V24 review note recorded for this source snapshot"
            latest.fingerprint == report.fingerprint -> "Latest V24 review matches this source snapshot (NOT an approval)"
            else -> "REVIEW STALE: source records have changed since the last V24 review"
        }, 12f, if (latest != null && latest.fingerprint != report.fingerprint) danger else muted)
        body.addView(historyStatus)
        body.addView(label("Local V24 review entries: ${history.history(report.month).size}", 11f, muted))
        body.addView(action("Record reviewer name and exception note") {
            recordReview(report) { historyStatus.text =
                "New local review recorded against current source fingerprint (NOT final close)" }
        })
        body.addView(action("Share CA review summary (user-initiated)") { verifyAndShare(report) })
        body.addView(label("CA review packet is an exception summary; it is not source invoices, bank evidence, a final ledger, or a GST filing.", 11f, warning))
    }

    private fun recordReview(report: FinanceV24AuditPolicy.Report, saved: () -> Unit) {
        val root = column()
        val reviewer = EditText(activity).apply {
            hint = "Reviewer name"; setSingleLine(true); root.addView(this)
        }
        val note = EditText(activity).apply {
            hint = "Reviewed exceptions and next actions (12+ characters)"
            minLines = 3; maxLines = 5; root.addView(this)
        }
        AlertDialog.Builder(activity).setTitle("Record review — NOT month close")
            .setMessage("This saves human review evidence only. Unresolved exceptions remain open.")
            .setView(root).setNegativeButton("Cancel",null).setPositiveButton("Save review",null)
            .create().apply {
                val entryDialog = this
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val reviewerValue = reviewer.text.toString()
                        val noteValue = note.text.toString()
                        getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                        activity.lifecycleScope.launch {
                            val outcome = withContext(Dispatchers.IO) { runCatching {
                                val fresh = load(report.month)
                                check(fresh.fingerprint == report.fingerprint) {
                                    "Sources changed while reviewing. Close and reopen V24 before saving."
                                }
                                FinanceV24AuditReviewStore(activity).save(fresh,
                                    reviewerValue, noteValue)
                            } }
                            if (!activity.isFinishing && !activity.isDestroyed) outcome.fold(
                                onSuccess = {
                                    entryDialog.dismiss(); saved()
                                    Toast.makeText(activity,"V24 review note saved locally",Toast.LENGTH_SHORT).show()
                                },
                                onFailure = { error ->
                                    getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                                    notify(error)
                                }
                            )
                        }
                    }
                }
                show()
            }
    }

    private fun verifyAndShare(report: FinanceV24AuditPolicy.Report) {
        activity.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching {
                val fresh = load(report.month)
                check(fresh.fingerprint == report.fingerprint) {
                    "Sources changed. Reopen V24 and review current exceptions before exporting."
                }
                packet(fresh, FinanceV24AuditReviewStore(activity).latest(fresh.month))
            } }
            if (activity.isFinishing || activity.isDestroyed) return@launch
            outcome.fold(onSuccess = { content ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Finance DEV V24 audit exceptions — ${report.month}")
                    putExtra(Intent.EXTRA_TEXT, content)
                }
                activity.startActivity(Intent.createChooser(intent,"Share local CA review summary"))
            }, onFailure = ::notify)
        }
    }

    private fun packet(report: FinanceV24AuditPolicy.Report, review: FinanceV24AuditReviewStore.Review?): String =
        buildString {
            appendLine("FINANCE DEV — V24 AUDIT EXCEPTION SUMMARY — ${report.month}")
            appendLine("STATUS: NOT FINAL CLOSED / NOT CERTIFIED / NOT A STATUTORY RETURN")
            appendLine("Data scope: local Finance DEV entries, not an authenticated multi-property PMS feed")
            appendLine("Fingerprint (local drift only): ${report.fingerprint}")
            appendLine("Open blockers: ${report.blockerCount}; review requirements: ${report.reviewCount}")
            appendLine("Bills: ${cash(report.billGrossPaise)}; open dues: ${cash(report.billDuePaise)}")
            appendLine("Issued payroll already included in bills: ${cash(report.salaryGrossPaise)}")
            appendLine("V21 payable tax evidence: ${cash(report.taxEvidencePayablePaise)}; unpaid ${cash(report.taxEvidenceOpenPaise)}")
            appendLine("Potential tax credits, not verified/netted: ${cash(report.potentialTaxCreditsPaise)}")
            appendLine("Separate rent GST/RCM unpaid: ${cash(report.rentTaxOpenPaise)}")
            appendLine("Unposted stock consumption preview: ${report.stockConsumedPaise?.let(::cash) ?: "UNAVAILABLE"}")
            appendLine("Local revenue evidence: ${cash(report.revenueEvidencePaise)}; PMS coverage unverified")
            appendLine("V20 provisional lock: ${if(report.provisionalLock) "Active" else "Inactive"}")
            appendLine("REVIEW EXCEPTIONS:")
            report.issues.forEach { appendLine("${it.severity}|${it.code}|${it.detail}") }
            appendLine("LATEST HUMAN REVIEW (not approval):")
            if (review == null) appendLine("No review recorded") else {
                appendLine("Reviewer: ${review.reviewer}; time epoch ms: ${review.recordedAt}")
                appendLine("Snapshot match: ${review.fingerprint == report.fingerprint}")
                appendLine("Comment: ${review.note}")
            }
            appendLine("Not a reconciliation of bank statements, invoice files, hotel-wise sales completeness or GST returns.")
        }
}
