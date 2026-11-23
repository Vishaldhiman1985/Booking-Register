package com.example.bookingregister.finance.dev

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
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
import com.example.bookingregister.finance.domain.FinanceGstBillNote
import com.example.bookingregister.finance.domain.FinanceV24Reconciliation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/**
 * A single Finance DEV-only V19-V24 read-only review surface.
 * Reuses the V17 payable store; never creates duplicate journal entries.
 * The review fingerprint does NOT lock any existing writer or certify statutory reporting.
 */
internal class FinanceV24ControlCenter(
    private val activity: AppCompatActivity,
    private val drafts: List<FinanceDemoDraftBill>
) {
    private val ink = Color.rgb(27, 42, 58)
    private val muted = Color.rgb(96, 111, 125)
    private val green = Color.rgb(13, 111, 80)
    private val red = Color.rgb(178, 45, 63)
    private val amber = Color.rgb(156, 96, 25)
    private val pale = Color.rgb(246, 249, 250)
    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density + .5f).toInt()
    private fun col() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }
    private fun gap(height: Int) = View(activity).apply { minimumHeight = dp(height) }
    private fun text(value: String, size: Float = 13f, strong: Boolean = false, color: Int = ink) =
        TextView(activity).apply {
            this.text = value; textSize = size; setTextColor(color)
            if (strong) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(3), 0, dp(3))
        }
    private fun panel() = col().apply {
        setPadding(dp(13), dp(11), dp(13), dp(11))
        background = GradientDrawable().apply { setColor(pale); cornerRadius = dp(12).toFloat() }
    }
    private fun money(paise: Long): String = FinanceFixedPolicy.rupees(paise)
    private fun line(parent: LinearLayout, title: String, value: String, emphasize: Boolean = false) {
        val r = row()
        r.addView(text(title, 12f, false, muted), LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(text(value, 13f, emphasize, ink).apply { gravity = Gravity.END })
        parent.addView(r)
    }

    private fun load(month: String): FinanceV24Reconciliation.Report {
        val (bills, payments, revenues) = FinanceActivityStore(activity.applicationContext).use { store ->
            store.synchronize(drafts)
            Triple(store.bills().filter { !LocalDate.parse(it.date).isAfter(LocalDate.now()) }.map {
                FinanceV24Reconciliation.Bill(it.id,it.period,it.vendorId,it.source,it.department,
                    it.amount,it.paid,it.due,it.stockPending)
            }, store.payments().map {
                FinanceV24Reconciliation.Payment(it.id,it.vendorId,it.amount,it.allocated,it.kind,it.date,it.reference)
            }, store.revenueRecords(month).filter { !LocalDate.parse(it.date).isAfter(LocalDate.now()) }.map {
                FinanceV24Reconciliation.Revenue(it.date,it.department,it.amountPaise)
            })
        }
        val fixedBills = FinanceFixedExpenseStore(activity.applicationContext).use { it.bills() }
        val taxes = FinanceLeaseTaxStore(activity.applicationContext).use { store ->
            store.issueForBills(fixedBills)
            store.bills().map {
                FinanceV24Reconciliation.LeaseTax(it.id,it.month,it.mode,it.taxPaise,it.paidPaise)
            }
        }
        val payroll = FinancePayrollStore(activity.applicationContext).use { store ->
            store.issuedRuns().map {
                FinanceV24Reconciliation.PayrollRun(it.id,it.period,it.amountPaise)
            }
        }
        val stock = HotelMonthlyStockStore(activity.applicationContext).let { store ->
            store.all().firstOrNull { it.month == month }?.let { s ->
                FinanceV24Reconciliation.StockState(month, true,
                    store.receiptsChangedAfterClose(month), s.closing.size, false)
            }
        }
        val gst = drafts.filter { it.accountingPeriod == month }.map { FinanceGstBillNote.read(it.notes).status }
        return FinanceV24Reconciliation.calculate(
            month, bills, payments, revenues, taxes, payroll, stock,
            gst.count { it == FinanceGstBillNote.Status.YES },
            gst.count { it == FinanceGstBillNote.Status.NO },
            gst.count { it == FinanceGstBillNote.Status.NOT_RECORDED }
        )
    }

    /** Local human review evidence: a snapshot, not a ledger close or cryptographic proof. */
    private class Reviews(context: Context) {
        private val prefs = context.getSharedPreferences("finance_dev_v24_review_snapshots", Context.MODE_PRIVATE)
        fun latest(month: String): String? {
            val list = JSONArray(prefs.getString("reviews", "[]") ?: "[]")
            for (i in list.length() - 1 downTo 0) {
                val o = list.getJSONObject(i)
                if (o.getString("month") == month) return o.getString("fingerprint")
            }
            return null
        }
        fun save(month: String, fingerprint: String, reason: String) {
            require(reason.trim().length in 5..240) { "Enter a review note of 5-240 characters" }
            val list = JSONArray(prefs.getString("reviews", "[]") ?: "[]")
            list.put(JSONObject().apply {
                put("month", month); put("fingerprint", fingerprint)
                put("reviewNote", reason.trim()); put("recordedAt", System.currentTimeMillis())
            })
            check(prefs.edit().putString("reviews", list.toString()).commit()) {
                "Could not save local review snapshot"
            }
        }
    }

    fun show() {
        var selected = FinancePeriodSelectionV19(activity).read().month
        val wrapper = col().apply { setPadding(dp(15), dp(8), dp(15), dp(15)) }
        val controls = row()
        val before = Button(activity).apply { text = "<"; contentDescription = "Previous month" }
        val label = text("", 16f, true).apply { gravity = Gravity.CENTER }
        val after = Button(activity).apply { text = ">"; contentDescription = "Next month" }
        controls.addView(before, LinearLayout.LayoutParams(dp(52),dp(50)))
        controls.addView(label, LinearLayout.LayoutParams(0,dp(50),1f))
        controls.addView(after, LinearLayout.LayoutParams(dp(52),dp(50)))
        wrapper.addView(controls)
        wrapper.addView(text("Locally recorded data | Not an audited statement", 11f, false, muted))
        wrapper.addView(gap(9))
        val body = col()
        wrapper.addView(body)
        val alert = AlertDialog.Builder(activity)
            .setTitle("Finance V24 | Control Centre")
            .setView(ScrollView(activity).apply { addView(wrapper) })
            .setPositiveButton("Close", null).create()
        var token = 0
        fun refresh() {
            val month = selected.toString()
            val request = ++token
            label.text = selected.toString()
            after.isEnabled = selected.isBefore(YearMonth.now())
            body.removeAllViews()
            body.addView(text("Loading financial checks...", 13f, false, muted))
            activity.lifecycleScope.launch {
                val outcome = withContext(Dispatchers.IO) { runCatching { load(month) } }
                if (request != token || activity.isFinishing || activity.isDestroyed || !alert.isShowing) return@launch
                body.removeAllViews()
                outcome.fold(onSuccess = { report -> render(body, report) }, onFailure = { error ->
                    body.addView(text("Unable to reconcile: ${error.message ?: "Local ledger error"}", 13f, true, red))
                })
            }
        }
        before.setOnClickListener { selected = selected.minusMonths(1); refresh() }
        after.setOnClickListener {
            if (selected.isBefore(YearMonth.now())) { selected = selected.plusMonths(1); refresh() }
        }
        alert.show()
        refresh()
    }

    private fun render(body: LinearLayout, report: FinanceV24Reconciliation.Report) {
        val source = panel()
        source.addView(text("V20 | Payables and advances", 15f, true))
        line(source, "Expense bills", money(report.billsPaise))
        line(source, "Settled on bills", money(report.paidPaise))
        line(source, "Bills unpaid", money(report.duePaise), true)
        line(source, "Unallocated vendor advances (all periods)", money(report.unusedAdvancesPaise))
        source.addView(text("Advances are not additional expenses. Opening-paid amounts may lack dated payment entries.", 11f, false, muted))
        body.addView(source); body.addView(gap(9))

        val tax = panel()
        tax.addView(text("V21 | Declared GST / RCM", 15f, true))
        line(tax, "Tracked rent tax", money(report.trackedTaxPaise))
        line(tax, "Rent tax paid", money(report.taxPaidPaise))
        line(tax, "Unpaid rent tax", money(report.taxDuePaise), true)
        line(tax, "RCM payable to government", money(report.rcmDuePaise))
        line(tax, "GST payable to landlord", money(report.landlordGstDuePaise))
        tax.addView(text("GST invoices: Yes ${report.gstBillsYes} | No ${report.gstBillsNo} | Unrecorded ${report.gstBillsUnrecorded}", 12f))
        tax.addView(text("TDS/TCS, ITC, GST filing and legal applicability are not calculated in this register.", 11f, false, amber))
        body.addView(tax); body.addView(gap(9))

        val labour = panel()
        labour.addView(text("V22 | Payroll and stock", 15f, true))
        line(labour, "Issued payroll", money(report.issuedPayrollPaise))
        line(labour, "Unpaid payroll bills", money(report.payrollDuePaise))
        labour.addView(text(if (report.stock?.approved == true)
            "Stock month approved | ${report.stock?.itemCount} closing item lines"
            else "Stock month not approved or not recorded", 12f))
        labour.addView(text("Stock consumption quantities may be available, but monetary valuation is not yet certified.", 11f, false, muted))
        body.addView(labour); body.addView(gap(9))

        val profit = panel()
        profit.addView(text("V23 | Department differences", 15f, true))
        report.departments.forEach { dept ->
            profit.addView(text(dept.name, 13f, true))
            line(profit, "Imported revenue", dept.revenuePaise?.let(::money) ?: "Not verified")
            line(profit, "Expense bills", money(dept.billedExpensePaise))
            line(profit, "Indicative difference", dept.indicativeDifferencePaise?.let(::money) ?: "Unavailable", true)
            profit.addView(gap(3))
        }
        profit.addView(text("NOT final P&L: stock valuation, complete sales import, allocation and CA treatment are unverified.", 11f, false, amber))
        body.addView(profit); body.addView(gap(9))

        val controls = panel()
        controls.addView(text("V24 | Reconciliation & close readiness", 15f, true))
        val blockers = report.issues.count { it.severity == FinanceV24Reconciliation.Severity.BLOCKER }
        val reviews = report.issues.size - blockers
        controls.addView(text("$blockers blocking inconsistencies | $reviews review requirements", 13f, true,
            if (blockers > 0) red else amber))
        report.issues.forEach { issue ->
            controls.addView(text("${issue.severity}: ${issue.detail}", 11f, false,
                if (issue.severity == FinanceV24Reconciliation.Severity.BLOCKER) red else muted))
        }
        controls.addView(gap(7))
        controls.addView(text("Final period close: NOT ENABLED until the source and statutory checks are complete.", 12f, true, amber))
        controls.addView(text("Review fingerprint: ${report.fingerprint.take(16)}...", 11f, false, muted))
        val reviewsStore = Reviews(activity.applicationContext)
        val prior = reviewsStore.latest(report.month)
        controls.addView(text(when {
            prior == null -> "No previous review snapshot for ${report.month}"
            prior == report.fingerprint -> "Local review snapshot matches current records"
            else -> "REVIEW REQUIRED: Records changed since the previous review snapshot"
        }, 11f, true, if (prior != null && prior != report.fingerprint) red else muted))
        val save = Button(activity).apply {
            text = "Save review snapshot (not month close)"
            isAllCaps = false
            setOnClickListener {
                val reason = EditText(activity).apply {
                    hint = "Reviewer note (required)"; setSingleLine(false)
                    minLines = 2; maxLines = 3
                }
                AlertDialog.Builder(activity).setTitle("Record local review only")
                    .setMessage("This saves a fingerprint and note. It does not freeze, close or post any month.")
                    .setView(reason)
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Save", null).create().apply {
                        setOnShowListener {
                            getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                try {
                                    reviewsStore.save(report.month,report.fingerprint,reason.text.toString())
                                    dismiss()
                                    Toast.makeText(activity,"Local review snapshot saved",Toast.LENGTH_LONG).show()
                                } catch (error:Exception) { reason.error = error.message ?: "Enter review note" }
                            }
                        }
                        show()
                    }
            }
        }
        controls.addView(save)
        body.addView(controls)
        body.addView(gap(12))
    }
}
