package com.example.bookingregister.finance.dev

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bookingregister.finance.domain.FinanceDashboardTotals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Finance DEV V19: concise, source-backed dashboard.
 * No dummy revenue, company-wide P&L claim, invented property mapping, or tax expense duplication.
 * All local data loads and idempotent existing-store synchronizations run off the main thread.
 */
internal class FinanceDashboardV19(
    private val activity: AppCompatActivity,
    private val openExpenses: () -> Unit,
    private val openLease: () -> Unit,
    private val openReports: () -> Unit
) {
    private val ink = Color.rgb(29, 43, 58)
    private val muted = Color.rgb(99, 111, 125)
    private val green = Color.rgb(12, 111, 80)
    private val border = Color.rgb(224, 230, 236)
    private val pale = Color.rgb(246, 249, 250)
    private val amber = Color.rgb(150, 94, 25)
    private val good = Color.rgb(6, 106, 77)
    private val periodSelection = FinancePeriodSelectionV19(activity)
    private var generation = 0
    private lateinit var target: LinearLayout
    private var drafts: List<FinanceDemoDraftBill> = emptyList()

    private fun dp(number: Int): Int = (number * activity.resources.displayMetrics.density + 0.5f).toInt()
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun gap(size: Int) = View(activity).apply { layoutParams = LinearLayout.LayoutParams(1, dp(size)) }
    private fun shape(fill: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(13).toFloat(); setStroke(dp(1), border)
    }
    private fun text(value: String, size: Float = 14f, bold: Boolean = false, color: Int = ink) =
        TextView(activity).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setLineSpacing(dp(1).toFloat(), 1f)
        }
    private fun money(paise: Long): String = "₹" + NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = if (paise % 100L == 0L) 0 else 2
    }.format(BigDecimal.valueOf(paise, 2))
    private fun panel(): LinearLayout = column().apply {
        setPadding(dp(14), dp(13), dp(14), dp(13)); background = shape(Color.WHITE)
    }
    private fun action(title: String, callback: () -> Unit): TextView = text(title, 13f, true, green).apply {
        gravity = Gravity.CENTER
        minHeight = dp(44)
        setPadding(dp(6), dp(8), dp(6), dp(8))
        background = shape(Color.rgb(238, 246, 243))
        isClickable = true
        isFocusable = true
        contentDescription = title
        setOnClickListener { callback() }
    }
    private fun separation(parent: LinearLayout) {
        parent.addView(View(activity).apply { setBackgroundColor(border) }, LinearLayout.LayoutParams(-1, dp(1)))
    }

    fun render(parent: LinearLayout, currentDrafts: List<FinanceDemoDraftBill>) {
        target = parent
        drafts = currentDrafts.toList()
        showMonth()
    }

    private fun showMonth() {
        val request = ++generation
        val selection = periodSelection.read()
        target.removeAllViews()
        periodSelection.render(target) { showMonth() }
        target.addView(text("All properties · locally recorded bills and taxes", 11f, false, muted))
        target.addView(gap(10))
        val status = panel().apply { background = shape(pale); addView(text("Loading recorded figures…", 13f, false, muted)) }
        target.addView(status)

        activity.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { load(selection) }
            }
            if (generation != request || activity.isFinishing || activity.isDestroyed) return@launch
            target.post {
                if (generation != request || !target.isAttachedToWindow) return@post
                target.removeView(status)
                outcome.fold(
                    onSuccess = { showFigures(it) },
                    onFailure = { error ->
                        target.addView(panel().apply {
                            addView(text("Figures unavailable", 15f, true, amber))
                            addView(gap(4))
                            addView(text(error.message ?: "Unable to read Finance local records", 12f, false, muted))
                            addView(gap(10))
                            addView(action("Retry") { showMonth() })
                        })
                    }
                )
            }
        }
    }

    private fun load(selection: FinancePeriodSelectionV19.Selection): FinanceDashboardTotals.Summary {
        // The same recorded bill/paid/unpaid definitions are used by Finance Statement.
        val (bills, revenues) = FinanceActivityStore(activity.applicationContext).use { ledger ->
            ledger.synchronize(drafts)
            val localBills = ledger.bills().filter { !LocalDate.parse(it.date).isAfter(LocalDate.now()) }
                .map { b ->
                    val period = b.period.takeIf { it.matches(Regex("\\d{4}-\\d{2}")) } ?: b.date.take(7)
                    FinanceDashboardTotals.Bill(period, b.amount, b.due, b.department)
                }
            val source = if (selection.allTime) ledger.revenueRecordsAll()
                else ledger.revenueRecords(selection.month.toString())
            val localRevenue = source.map { r ->
                FinanceDashboardTotals.Revenue(r.date.take(7), r.amountPaise, r.department)
            }
            localBills to localRevenue
        }
        val rents = FinanceFixedExpenseStore(activity.applicationContext).use { it.bills() }
        val taxRecords = FinanceLeaseTaxStore(activity.applicationContext).use { store ->
            store.issueForBills(rents)
            store.bills().map { FinanceDashboardTotals.LeaseTax(it.month, it.mode, it.remainingPaise) }
        }
        return FinanceDashboardTotals.calculate(selection.month.toString(), bills, revenues, taxRecords,
            selection.allTime)
    }

    private fun infoCard(title: String, value: String, detail: String, emphasis: Int = ink): LinearLayout = panel().apply {
        addView(text(title, 12f, false, muted))
        addView(gap(6))
        addView(text(value, 21f, true, emphasis).apply { setSingleLine(false) })
        addView(gap(6))
        addView(text(detail, 11f, false, muted))
    }
    private fun pair(left: LinearLayout, right: LinearLayout) {
        val row = row()
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(View(activity), LinearLayout.LayoutParams(dp(8), 1))
        row.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        target.addView(row)
        target.addView(gap(8))
    }
    private fun amountLine(target: LinearLayout, title: String, value: String, color: Int = ink) {
        val line = row().apply { setPadding(0, dp(7), 0, dp(7)) }
        line.addView(text(title, 13f, false, muted), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        line.addView(text(value, 14f, true, color).apply { gravity = Gravity.END })
        target.addView(line)
    }

    private fun showFigures(s: FinanceDashboardTotals.Summary) {
        val noRevenue = s.importedRevenuePaise == null
        pair(
            infoCard("Revenue imported", s.importedRevenuePaise?.let(::money) ?: "—",
                if (noRevenue) "No verified imported revenue" else "Local import · not reconciled"),
            infoCard("Expense bills", money(s.billAmountPaise), "${s.billCount} recorded bills")
        )
        pair(
            infoCard("Unpaid bills", money(s.billUnpaidPaise), "Current balance · selected period",
                if (s.billUnpaidPaise > 0L) amber else good),
            infoCard("Total outstanding", money(s.totalOutstandingPaise), "Bills + separately tracked rent tax",
                if (s.totalOutstandingPaise > 0L) amber else good)
        )
        target.addView(panel().apply {
            addView(text("Kitchen · F&B", 15f, true))
            addView(gap(6))
            amountLine(this, "Revenue imported", s.kitchenRevenuePaise?.let(::money) ?: "—")
            separation(this)
            amountLine(this, "Expense bills", money(s.kitchenBillAmountPaise))
            separation(this)
            amountLine(this, "Indicative difference", s.kitchenDifferencePaise?.let(::money) ?: "—")
            addView(gap(5))
            addView(text("Actual kitchen P&L needs verified sales and opening/closing stock.", 11f, false, muted))
        })
        target.addView(gap(8))
        target.addView(panel().apply {
            val header = row()
            header.addView(text("Rent GST / RCM Due", 15f, true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            header.addView(text(money(s.taxUnpaidPaise), 17f, true,
                if (s.taxUnpaidPaise > 0L) amber else good))
            addView(header)
            addView(gap(6))
            amountLine(this, "RCM · Government", money(s.rcmUnpaidPaise))
            separation(this)
            amountLine(this, "GST · Landlord", money(s.landlordGstUnpaidPaise))
            addView(gap(5))
            addView(text("Declared unpaid tax only · excluded from expense bills", 11f, false, muted))
            addView(gap(7))
            addView(action("View lease dues") { openLease() })
        })
        target.addView(gap(9))
        target.addView(action("V24 accounting controls") {
            FinanceV24ControlCenter(activity, drafts).show()
        })
        target.addView(gap(9))
        target.addView(action("View reports") { openReports() })
        target.addView(gap(9))
        target.addView(text(
            "Amounts cover all properties in the local Finance DEV register. Bill and tax dues are current " +
                "balances, not historical as-of snapshots. This is not an audited P&L.",
            11f, false, muted
        ))
        target.addView(gap(18))
    }

}
