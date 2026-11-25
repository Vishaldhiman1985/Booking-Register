package com.example.bookingregister.finance.dev

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Finance V25: expense presentation only. All financial values come from the existing
 * V17 payable, fixed-rent tax, stock-link and employee-advance stores. No new ledger,
 * journal, stock record or payroll record is written by this class.
 */
internal class FinanceV25ExpensesScreen(
    private val activity: AppCompatActivity,
    private val onChanged: () -> Unit,
    private val onPendingStock: () -> Unit,
    private val onManualActions: (FinanceDemoDraftBill) -> Unit,
    private val onOpenVendorProfile: (String) -> Unit
) {
    private val green = Color.rgb(0, 110, 60)
    private val ink = Color.rgb(18, 35, 55)
    private val muted = Color.rgb(100, 112, 126)
    private val red = Color.rgb(191, 38, 48)
    private val amber = Color.rgb(164, 104, 0)
    private val blue = Color.rgb(22, 110, 195)
    private val mint = Color.rgb(232, 247, 236)
    private val paper = Color.WHITE
    private val edge = Color.rgb(222, 231, 228)
    private val smallDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val monthText = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    private val prefs = activity.getSharedPreferences("finance_dev_v25_expense_period_ui", Context.MODE_PRIVATE)
    private var activityFilter = "All"
    private var showAllActivity = false

    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density + .5f).toInt()
    private fun col() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }
    private fun gap(n: Int) = View(activity).apply { layoutParams = LinearLayout.LayoutParams(1, dp(n)) }
    private fun shape(fill: Int, border: Int = fill, radius: Int = 15) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), border)
    }
    private fun text(value: String, size: Float = 13f, color: Int = ink, bold: Boolean = false): TextView =
        TextView(activity).apply {
            this.text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            includeFontPadding = true
        }
    private fun rupees(paise: Long): String {
        val abs = if (paise == Long.MIN_VALUE) java.math.BigDecimal.valueOf(paise, 2).abs()
            else java.math.BigDecimal.valueOf(kotlin.math.abs(paise), 2)
        val s = abs.toPlainString()
        val digits = s.substringBefore('.')
        val prefix = digits.dropLast(3)
        val indian = if (prefix.isEmpty()) digits else
            prefix.reversed().chunked(2).map { it.reversed() }.asReversed().joinToString(",") + "," + digits.takeLast(3)
        val decimals = if (paise % 100L == 0L) "" else "." + s.substringAfter('.').padEnd(2, '0')
        return (if (paise < 0) "−" else "") + "₹" + indian + decimals
    }
    private fun add(a: Long, b: Long) = Math.addExact(a, b)
    private fun total(values: Iterable<Long>) = values.fold(0L) { a, b -> Math.addExact(a, b) }
    private fun button(value: String, selected: Boolean = false, click: () -> Unit) =
        text(value, 12f, if (selected) Color.WHITE else green, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(40)
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = shape(if (selected) green else mint, if (selected) green else mint, 18)
            isClickable = true; isFocusable = true; setOnClickListener { click() }
        }
    private fun pageCard() = col().apply {
        background = shape(paper, edge, 18)
        setPadding(dp(12), dp(15), dp(12), dp(15))
        elevation = dp(2).toFloat()
    }
    private fun separator() = View(activity).apply {
        setBackgroundColor(Color.rgb(235, 238, 241))
        layoutParams = LinearLayout.LayoutParams(-1, dp(1))
    }

    private data class Period(
        val mode: String, val month: YearMonth, val financialStart: Int,
        val from: LocalDate, val to: LocalDate
    ) {
        private val start: LocalDate get() = when (mode) {
            "Y" -> LocalDate.of(financialStart, 4, 1)
            "C" -> from
            else -> month.atDay(1)
        }
        private val end: LocalDate get() = when (mode) {
            "Y" -> LocalDate.of(financialStart + 1, 3, 31)
            "C" -> to
            else -> month.atEndOfMonth()
        }
        val label: String get() = when (mode) {
            "Y" -> "FY $financialStart-${(financialStart + 1).toString().takeLast(2)}"
            "C" -> from.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) +
                " – " + to.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
            else -> month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
        }
        fun hasBill(b: FinanceActivityStore.Bill): Boolean = when (mode) {
            "C" -> LocalDate.parse(b.date) in start..end
            else -> YearMonth.parse(b.period) in YearMonth.from(start)..YearMonth.from(end)
        }
        /** Historical advances are evaluated at the end of the selected reporting period. */
        fun asOfDate(): LocalDate = minOf(end, LocalDate.now())
        fun hasEvent(e: FinanceActivityStore.Event): Boolean = LocalDate.parse(e.date) in start..end
        fun hasTax(t: FinanceLeaseTaxStore.TaxBill): Boolean {
            val monthOfTax = YearMonth.parse(t.month)
            return !monthOfTax.atEndOfMonth().isBefore(start) && !monthOfTax.atDay(1).isAfter(end)
        }
    }
    private fun currentFy(): Int = LocalDate.now().let { if (it.monthValue >= 4) it.year else it.year - 1 }
    private fun period(): Period {
        val now = LocalDate.now()
        val month = runCatching { YearMonth.parse(prefs.getString("month", YearMonth.now().toString())) }
            .getOrNull()?.coerceAtMost(YearMonth.now()) ?: YearMonth.now()
        val fy = prefs.getInt("fy_start", currentFy()).coerceAtMost(currentFy()).coerceAtLeast(2000)
        val from = runCatching { LocalDate.parse(prefs.getString("from", now.withDayOfMonth(1).toString())) }
            .getOrDefault(now.withDayOfMonth(1))
        val to = runCatching { LocalDate.parse(prefs.getString("to", now.toString())) }
            .getOrDefault(now)
        val saneFrom = from.coerceAtMost(now)
        val saneTo = to.coerceAtMost(now).coerceAtLeast(saneFrom)
        val mode = prefs.getString("mode", "M").takeIf { it in listOf("M", "Y", "C") } ?: "M"
        return Period(mode, month, fy, saneFrom, saneTo)
    }
    private fun save(mode: String, month: YearMonth, fy: Int, from: LocalDate, to: LocalDate) {
        check(prefs.edit().putString("mode", mode).putString("month", month.toString())
            .putInt("fy_start", fy).putString("from", from.toString()).putString("to", to.toString()).commit()) {
            "Unable to remember reporting period"
        }
    }

    private data class Snapshot(
        val bills: List<FinanceActivityStore.Bill>, val events: List<FinanceActivityStore.Event>,
        val tax: List<FinanceLeaseTaxStore.TaxBill>, val availableVendor: Long,
        val availableStaff: Long, val vendorAdvances: List<FinanceActivityStore.Payment>,
        val staffAdvances: List<FinanceActivityStore.Payment>, val pendingStock: Int
    )
    private fun snapshot(drafts: List<FinanceDemoDraftBill>, advanceAsOf: LocalDate): Snapshot = FinanceActivityStore(activity).use { ledger ->
        // Retain the original V17 idempotent synchronization, not a second accounting ledger.
        ledger.synchronize(drafts)
        val today = LocalDate.now()
        val bills = ledger.bills().filter { !LocalDate.parse(it.date).isAfter(today) }
        val allEvents = ledger.events().filter { !LocalDate.parse(it.date).isAfter(today) }
        // V17 stores one allocation event per bill. The V25 activity view must show
        // a single cash movement for a payment applied to multiple invoices.
        // The payment ID and exact cash amount are read from the same V17 ledger.
        val recordedPayments = ledger.payments(includeReversed = true)
            .filter { it.kind == "PAYMENT" }.associateBy { it.id }
        val groupedPayments = allEvents.filter { it.type == "PAYMENT" }
            .groupBy { ledger.paymentIdForEvent(it.id) ?: it.id }
            .map { (paymentId, allocations) ->
                val first = allocations.first()
                val payment = recordedPayments[paymentId]
                if (payment != null && allocations.size > 1) first.copy(
                    title = "Payment · ${payment.vendor}",
                    detail = "${payment.vendor} · ${allocations.size} bills · ${payment.method}",
                    amount = payment.amount
                ) else first
            }
        val events = allEvents.filter { it.type != "PAYMENT" } + groupedPayments
        // One historical balance projection over the ORIGINAL payment/allocation tables.
        // Never use today's Payment.available for an earlier month.
        val advances = ledger.availableAdvancesAsOf(advanceAsOf)
        val vendors = advances.filterNot { it.vendorId.startsWith("employee:") }
        val staff = advances.filter { it.vendorId.startsWith("employee:") }
        val tax = FinanceFixedExpenseStore(activity).use { fixed ->
            val rents = fixed.bills()
            FinanceLeaseTaxStore(activity).use { lease ->
                lease.issueForBills(rents)
                lease.bills()
            }
        }
        Snapshot(bills, events, tax, total(vendors.map { it.available }),
            total(staff.map { it.available }), vendors, staff,
            FinanceExpenseStockLinkStore(activity).all().count { it.state == FinanceExpenseStockPolicy.PENDING })
    }

    fun render(parent: LinearLayout, drafts: List<FinanceDemoDraftBill>, statusFilter: String) {
        parent.removeAllViews()
        val selected = period()
        periodBar(parent, selected) { render(parent, drafts, statusFilter) }
        try {
            val data = snapshot(drafts, selected.asOfDate())
            val selectedBills = data.bills.filter(selected::hasBill)
            val vendorBills = selectedBills.filter { !it.vendorId.startsWith("employee:") && it.due > 0L }
            val payrollBills = selectedBills.filter { it.vendorId.startsWith("employee:") && it.due > 0L }
            val unpaidVendors = total(vendorBills.map { it.due })
            val unpaidPayroll = total(payrollBills.map { it.due })
            val selectedTax = data.tax.filter { selected.hasTax(it) && it.remainingPaise > 0L }
            val unpaidTax = total(selectedTax.map { it.remainingPaise })
            val due = add(add(unpaidVendors, unpaidPayroll), unpaidTax)
            overview(parent, data, selected, vendorBills + payrollBills, selectedTax,
                total(selectedBills.map { it.amount }), total(selectedBills.map { it.paid }),
                due, unpaidVendors, unpaidPayroll, unpaidTax)
            activityList(parent, data, selected, drafts, statusFilter) { render(parent, drafts, statusFilter) }
        } catch (error: Exception) {
            parent.addView(pageCard().apply {
                addView(text("Unable to display expense overview", 16f, red, true))
                addView(gap(8))
                addView(text(error.message ?: "Review the existing finance records", 12f, muted))
            })
        }
        parent.addView(gap(78)) // Keep final transaction clear of the Add Expense floating button.
    }

    private fun periodBar(parent: LinearLayout, p: Period, refresh: () -> Unit) {
        val card = col().apply {
            background = shape(mint, mint, 17)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val first = row()
        first.addView(text("▦", 22f, green, true), LinearLayout.LayoutParams(dp(29), -2))
        val toggle = row().apply {
            background = shape(Color.WHITE, edge, 18)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        listOf("M", "Y").forEach { m ->
            toggle.addView(button(m, p.mode == m) {
                save(m, p.month, if (m == "Y") currentFy() else p.financialStart, p.from, p.to)
                refresh()
            }, LinearLayout.LayoutParams(dp(42), dp(37)))
        }
        first.addView(toggle)
        first.addView(gap(8))
        val nav = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = shape(Color.WHITE, edge, 19)
        }
        val prev = button("‹") {
            when (p.mode) {
                "M" -> save("M", p.month.minusMonths(1), p.financialStart, p.from, p.to)
                "Y" -> save("Y", p.month, p.financialStart - 1, p.from, p.to)
                else -> return@button
            }
            refresh()
        }
        nav.addView(prev, LinearLayout.LayoutParams(dp(34), dp(40)))
        nav.addView(text(p.label, if (p.mode == "C") 12f else 14f, green, true).apply {
            gravity = Gravity.CENTER; maxLines = 1
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        val nextAllowed = when (p.mode) {
            "M" -> p.month.isBefore(YearMonth.now())
            "Y" -> p.financialStart < currentFy()
            else -> false
        }
        val next = button("›") {
            if (!nextAllowed) return@button
            when (p.mode) {
                "M" -> save("M", p.month.plusMonths(1), p.financialStart, p.from, p.to)
                "Y" -> save("Y", p.month, p.financialStart + 1, p.from, p.to)
            }
            refresh()
        }
        next.isEnabled = nextAllowed
        next.alpha = if (nextAllowed) 1f else .35f
        nav.addView(next, LinearLayout.LayoutParams(dp(34), dp(40)))
        val compactWidth = activity.resources.configuration.screenWidthDp >= 380
        first.addView(nav, LinearLayout.LayoutParams(0, -2, 1f))
        if (compactWidth) {
            first.addView(gap(6))
            first.addView(button("▦ Custom", p.mode == "C") { customRange(p, refresh) }.apply {
                contentDescription = "Select custom dates"
            }, LinearLayout.LayoutParams(dp(87), dp(40)))
        }
        card.addView(first)
        if (!compactWidth) {
            card.addView(gap(6))
            card.addView(button("▦  Custom dates", p.mode == "C") { customRange(p, refresh) }.apply {
                contentDescription = "Select custom dates"
            })
        }
        if (p.mode == "C") {
            card.addView(gap(5))
            card.addView(text("Custom range: ${p.label}", 10f, muted))
        }
        parent.addView(card)
        parent.addView(gap(12))
    }

    private fun customRange(p: Period, refresh: () -> Unit) {
        var fromDate = if (p.mode == "C") p.from else LocalDate.now().withDayOfMonth(1)
        var toDate = if (p.mode == "C") p.to else LocalDate.now()
        val layout = col().apply { setPadding(dp(16), dp(8), dp(16), dp(7)) }
        layout.addView(text("Search bills and activity between two dates.", 12f, muted))
        layout.addView(gap(12))
        val fromButton = button("From: ${fromDate.format(smallDate)}") {}
        val toButton = button("To: ${toDate.format(smallDate)}") {}
        fun datePicker(date: LocalDate, onDate: (LocalDate) -> Unit) {
            val picker = DatePickerDialog(activity, { _, year, month, day ->
                onDate(LocalDate.of(year, month + 1, day))
            }, date.year, date.monthValue - 1, date.dayOfMonth)
            picker.datePicker.maxDate = System.currentTimeMillis()
            picker.show()
        }
        fromButton.setOnClickListener {
            datePicker(fromDate) { fromDate = it; fromButton.text = "From: ${it.format(smallDate)}" }
        }
        toButton.setOnClickListener {
            datePicker(toDate) { toDate = it; toButton.text = "To: ${it.format(smallDate)}" }
        }
        layout.addView(text("START DATE", 11f, muted, true))
        layout.addView(fromButton)
        layout.addView(gap(13))
        layout.addView(text("END DATE", 11f, muted, true))
        layout.addView(toButton)
        layout.addView(gap(8))
        val dialog = AlertDialog.Builder(activity).setTitle("Custom dates")
            .setView(layout).setNegativeButton("Cancel", null)
            .setNeutralButton("Current month", null).setPositiveButton("Apply", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (toDate.isBefore(fromDate) || toDate.isAfter(LocalDate.now())) {
                    android.widget.Toast.makeText(activity, "Choose a valid date range up to today", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    save("C", p.month, p.financialStart, fromDate, toDate)
                    dialog.dismiss(); refresh()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                save("M", YearMonth.now(), currentFy(), p.from, p.to)
                dialog.dismiss(); refresh()
            }
        }
        dialog.show()
    }

    private fun overview(
        parent: LinearLayout, data: Snapshot, p: Period,
        payeeBills: List<FinanceActivityStore.Bill>,
        taxBills: List<FinanceLeaseTaxStore.TaxBill>, billed: Long, settled: Long, due: Long,
        vendorDue: Long, payrollDue: Long, taxDue: Long
    ) {
        val card = pageCard()
        val stats = row().apply { gravity = Gravity.CENTER_VERTICAL }
        fun stat(label: String, amount: Long, tint: Int, icon: String, active: Boolean, action: () -> Unit) =
            col().apply {
                setPadding(dp(5), dp(11), dp(4), dp(10))
                if (active) background = shape(Color.rgb(255, 237, 237), Color.rgb(255, 207, 207), 13)
                addView(text(icon, 17f, tint, true).apply { gravity = Gravity.CENTER })
                addView(gap(4))
                addView(text(label + if (active) "  ›" else "", 11f, if (active) tint else muted, true).apply {
                    gravity = Gravity.CENTER; maxLines = 2
                })
                addView(text(rupees(amount), if (activity.resources.configuration.screenWidthDp < 380) 13f else 15f,
                    tint, true).apply { gravity = Gravity.CENTER; maxLines = 2 })
                if (active) { isClickable = true; isFocusable = true; setOnClickListener { action() } }
            }
        stats.addView(stat("Total Bills", billed, ink, "▤", false) {}, LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(separator().apply { layoutParams = LinearLayout.LayoutParams(dp(1), dp(64)) })
        stats.addView(stat("Settled", settled, green, "✓", false) {}, LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(separator().apply { layoutParams = LinearLayout.LayoutParams(dp(1), dp(64)) })
        stats.addView(stat("Dues", due, red, "!", true) {
            (activity as? FinanceDevSafetyActivity)?.openV25Payments()
                ?: duesDetail(p, payeeBills, taxBills, vendorDue, payrollDue, taxDue)
        }, LinearLayout.LayoutParams(0, -2, 1.1f))
        card.addView(stats)
        card.addView(gap(12))
        card.addView(text("${rupees(vendorDue)} vendor  +  ${rupees(payrollDue)} salary  +  ${rupees(taxDue)} rent GST / RCM",
            11f, muted).apply { gravity = Gravity.CENTER; maxLines = 3 })
        card.addView(gap(14))
        card.addView(separator())
        card.addView(gap(13))
        val needs = row()
        fun attention(title: String, detail: String, color: Int, paleColor: Int, symbol: String, onClick: () -> Unit) =
            row().apply {
                background = shape(paleColor, paleColor, 13)
                setPadding(dp(9), dp(12), dp(8), dp(12))
                addView(text(symbol, 22f, color, true).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(dp(27), -2))
                addView(col().apply {
                    addView(text(title, 12f, ink, true).apply { maxLines = 2 })
                    addView(gap(3))
                    addView(text(detail, 11f, muted).apply { maxLines = 2 })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(text("›", 21f, color), LinearLayout.LayoutParams(dp(13), -2))
                isClickable = true; isFocusable = true; setOnClickListener { onClick() }
            }
        needs.addView(attention("Pending Quantities", "${data.pendingStock} pending - All periods", blue,
            Color.rgb(232, 244, 255), "▣", onPendingStock), LinearLayout.LayoutParams(0, -2, 1f))
        needs.addView(View(activity), LinearLayout.LayoutParams(dp(8), 1))
        needs.addView(attention("Advance paid", rupees(add(data.availableVendor, data.availableStaff)) +
            " available", amber, Color.rgb(255, 247, 230), "◉") {
            advancesDetail(data, p.asOfDate())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(needs)
        card.addView(gap(8))
        card.addView(text("Advances as of ${p.asOfDate().format(smallDate)}: Vendors ${rupees(data.availableVendor)}  ·  Staff ${rupees(data.availableStaff)}",
            10f, muted))
        card.addView(gap(5))
        card.addView(text("${p.label} · Bill settlements and dues are current as of today; tax is separate from Total Bills.", 10f, muted))
        parent.addView(card)
        parent.addView(gap(21))
    }

    private fun duesDetail(
        p: Period, vendorBills: List<FinanceActivityStore.Bill>,
        taxes: List<FinanceLeaseTaxStore.TaxBill>, vendorDue: Long, payrollDue: Long, taxDue: Long
    ) {
        var dialog: AlertDialog? = null
        val body = col().apply { setPadding(dp(16), dp(9), dp(16), dp(15)) }
        body.addView(text("${p.label} · Current outstanding balances", 12f, muted))
        body.addView(gap(13))
        body.addView(text("Total due  ${rupees(add(add(vendorDue, payrollDue), taxDue))}", 21f, red, true))
        body.addView(gap(16))
        body.addView(text("UNPAID VENDOR AND ISSUED SALARY BILLS", 12f, ink, true))
        body.addView(text("Vendor ${rupees(vendorDue)} · Salary ${rupees(payrollDue)}", 17f, red, true))
        body.addView(gap(8))
        if (vendorBills.isEmpty()) body.addView(text("No unpaid payee bills in this period.", 12f, muted))
        vendorBills.groupBy { it.vendorId }.values.sortedBy { it.first().vendor.lowercase() }.forEach { group ->
            val name = group.first().vendor
            val due = total(group.map { it.due })
            val entry = row().apply {
                setPadding(dp(10), dp(12), dp(10), dp(12))
                background = shape(Color.rgb(247, 250, 248), edge, 10)
                addView(text(name, 13f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(text(rupees(due) + "  ›", 13f, red, true))
                isClickable = true
                setOnClickListener {
                    dialog?.dismiss()
                    if (group.first().vendorId.startsWith("employee:"))
                        FinanceSalaryPlannerScreen(activity, onChanged).show()
                    else FinanceUnifiedStatementScreen(activity, onChanged).openPayeeLedger(group.first().vendorId)
                }
            }
            body.addView(entry); body.addView(gap(6))
        }
        body.addView(gap(13))
        body.addView(text("RENT GST / RCM", 12f, ink, true))
        body.addView(text(rupees(taxDue), 17f, amber, true))
        body.addView(gap(8))
        if (taxes.isEmpty()) body.addView(text("No rent tax dues in this period.", 12f, muted))
        taxes.groupBy { it.mode }.forEach { (mode, list) ->
            body.addView(text("$mode · ${list.size} obligation(s) · ${rupees(total(list.map { it.remainingPaise }))}",
                12f, muted))
            body.addView(gap(6))
        }
        body.addView(button("View rent GST / RCM details  ›") {
            dialog?.dismiss()
            FinanceLeaseTaxScreen(activity, onChanged).show()
        })
        body.addView(gap(11))
        body.addView(text("Amounts are from existing Finance DEV records; no new payment is recorded here.", 11f, muted))
        dialog = AlertDialog.Builder(activity).setTitle("Vendor & Tax Dues")
            .setView(ScrollView(activity).apply { addView(body) })
            .setPositiveButton("Close", null).create()
        dialog.show()
    }

    /** Positive, *unallocated* advances only: never send an advance tap to a generic ledger. */
    private fun advancesDetail(data: Snapshot, asOf: LocalDate) {
        var dialog: AlertDialog? = null
        val body = col().apply { setPadding(dp(16), dp(6), dp(16), dp(10)) }

        val vendorGroups = data.vendorAdvances.groupBy { it.vendorId }.values
            .filter { total(it.map { payment -> payment.available }) > 0L }
            .sortedBy { it.first().vendor.lowercase(Locale.ROOT) }
        val staffGroups = data.staffAdvances.groupBy { it.vendorId }.values
            .filter { total(it.map { payment -> payment.available }) > 0L }
            .sortedBy { it.first().vendor.lowercase(Locale.ROOT) }
        val available = add(data.availableVendor, data.availableStaff)

        body.addView(text("AVAILABLE ADVANCES · AS OF ${asOf.format(smallDate)}", 11f, muted, true))
        body.addView(gap(5))
        body.addView(text(rupees(available), 25f, green, true))
        body.addView(gap(5))
        body.addView(text("Only vendors and staff with an unapplied balance at this date are shown.",
            12f, muted))
        body.addView(gap(15))

        fun person(items: List<FinanceActivityStore.Payment>, isStaff: Boolean) {
            val id = items.first().vendorId
            val name = items.first().vendor
            val amount = total(items.map { it.available })
            if (amount <= 0L) return
            val tint = if (isStaff) blue else green
            val card = row().apply {
                background = shape(Color.rgb(247, 250, 249), edge, 12)
                setPadding(dp(12), dp(13), dp(10), dp(13))
                addView(col().apply {
                    addView(text(name, 15f, ink, true).apply { maxLines = 2 })
                    addView(gap(3))
                    addView(text(if (isStaff) "View staff record in Manpower" else "View this vendor profile",
                        11f, muted).apply { maxLines = 2 })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(col().apply {
                    gravity = Gravity.END
                    addView(text(rupees(amount), 15f, tint, true).apply { gravity = Gravity.END })
                    addView(text("Available  ›", 11f, muted).apply { gravity = Gravity.END })
                }, LinearLayout.LayoutParams(-2, -2))
                isClickable = true
                isFocusable = true
                contentDescription = "Open ${name}, available advance ${rupees(amount)}"
                setOnClickListener {
                    dialog?.dismiss()
                    if (isStaff) {
                        FinanceSalaryPlannerScreen(activity, onChanged)
                            .showForEmployee(id.removePrefix("employee:"), 1)
                    } else {
                        // Go straight to this V25 Vendors profile, NOT the legacy ledger directory.
                        onOpenVendorProfile(id)
                    }
                }
            }
            body.addView(card)
            body.addView(gap(8))
        }

        if (vendorGroups.isNotEmpty()) {
            body.addView(text("VENDOR ADVANCES  ·  ${rupees(data.availableVendor)}", 12f, ink, true))
            body.addView(gap(8))
            vendorGroups.forEach { person(it, false) }
            body.addView(gap(7))
        }
        if (staffGroups.isNotEmpty()) {
            body.addView(text("STAFF ADVANCES  ·  ${rupees(data.availableStaff)}", 12f, ink, true))
            body.addView(gap(8))
            staffGroups.forEach { person(it, true) }
        }
        if (vendorGroups.isEmpty() && staffGroups.isEmpty()) {
            body.addView(text("No outstanding advance balances.", 13f, muted))
        }
        body.addView(gap(10))
        body.addView(text("Historical advances use actual payment and allocation dates. Tapping a name opens their current record.",
            11f, muted))
        dialog = AlertDialog.Builder(activity).setTitle("Advance paid")
            .setView(ScrollView(activity).apply { addView(body) })
            .setPositiveButton("Close", null).create()
        dialog.show()
    }

    private fun activityList(
        parent: LinearLayout, data: Snapshot, p: Period, drafts: List<FinanceDemoDraftBill>,
        statusFilter: String, refresh: () -> Unit
    ) {
        val heading = row()
        heading.addView(text("◷  Recent Activity", 19f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(text(if (showAllActivity) "Show less  ›" else "View All  ›", 12f, green, true).apply {
            setPadding(dp(4), dp(8), dp(3), dp(8))
            setOnClickListener { showAllActivity = !showAllActivity; refresh() }
        })
        parent.addView(heading)
        parent.addView(gap(10))
        val filters = row()
        val labels = listOf("All", "Bills", "Payments", "Advances", "Revenue")
        for ((index, label) in labels.withIndex()) {
            if (index > 0) filters.addView(View(activity), LinearLayout.LayoutParams(dp(6), 1))
            filters.addView(button(label, activityFilter == label) {
                activityFilter = label; showAllActivity = false; refresh()
            }.apply { setPadding(dp(13), dp(9), dp(13), dp(9)); isSingleLine = true },
                LinearLayout.LayoutParams(-2, dp(40)))
        }
        parent.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(filters)
        })
        parent.addView(gap(12))
        val bills = data.bills.associateBy { it.id }
        val events = data.events.filter(p::hasEvent).filter { e ->
            val typeShown = when (activityFilter) {
                "Bills" -> e.type == "BILL"
                "Payments" -> e.type in listOf("PAYMENT", "ADJUSTMENT", "REVERSAL")
                "Advances" -> e.type in listOf("ADVANCE", "ADJUSTMENT", "REVERSAL")
                "Revenue" -> e.type == "REVENUE"
                else -> true
            }
            val bill = e.billId?.let(bills::get)
            typeShown && (bill == null || statusFilter == "All" || bill.status == statusFilter)
        }.sortedWith(compareByDescending<FinanceActivityStore.Event> { it.date }.thenByDescending { it.id })
        val shown = if (showAllActivity) events else events.take(6)
        if (shown.isEmpty()) parent.addView(pageCard().apply {
            addView(text("No transactions in this period.", 13f, muted))
        })
        shown.forEach { e ->
            val bill = e.billId?.let(bills::get)
            val badge = when (e.type) {
                "BILL" -> bill?.status ?: "Bill"
                "PAYMENT" -> "Payment"
                "ADVANCE" -> "Advance"
                "REVENUE" -> "Revenue"
                "ADJUSTMENT" -> "Advance used"
                else -> e.type.lowercase().replaceFirstChar { it.uppercase() }
            }
            val icon = when (e.type) {
                "BILL" -> "▤"
                "PAYMENT", "ADJUSTMENT", "REVERSAL" -> "↔"
                "ADVANCE" -> "◉"
                "REVENUE" -> "↗"
                else -> "▤"
            }
            val tint = when (e.type) {
                "PAYMENT", "REVENUE" -> green
                "ADVANCE" -> amber
                "ADJUSTMENT" -> blue
                else -> ink
            }
            val amountText = when (e.type) {
                "PAYMENT", "ADVANCE", "ADJUSTMENT" -> "−" + rupees(e.amount)
                "REVENUE", "REVERSAL" -> "+" + rupees(e.amount)
                else -> rupees(e.amount)
            }
            val card = row().apply {
                background = shape(Color.WHITE, Color.rgb(234, 238, 242), 13)
                setPadding(dp(9), dp(11), dp(8), dp(11))
                addView(text(icon, 21f, tint, true).apply {
                    gravity = Gravity.CENTER
                    background = shape(mint, mint, 11)
                }, LinearLayout.LayoutParams(dp(36), dp(43)))
                addView(gap(8))
                addView(col().apply {
                    val title = e.title.removePrefix("Payment · ").removePrefix("Bill · ")
                    addView(text(title, 13f, ink, true).apply { maxLines = 2 })
                    addView(text("${e.vendor} · ${e.date}", 10f, muted).apply { maxLines = 2 })
                    addView(gap(4))
                    addView(text(badge, 10f, if (badge == "Paid" || badge == "Payment") green else tint, true))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(col().apply {
                    gravity = Gravity.END
                    addView(text(amountText, 13f, tint, true).apply { gravity = Gravity.END; maxLines = 2 })
                    addView(text("›", 19f, muted).apply { gravity = Gravity.END })
                }, LinearLayout.LayoutParams(dp(91), -2))
                isClickable = true; isFocusable = true
                contentDescription = "Open ${e.title} · ${e.date} · $amountText"
                setOnClickListener {
                    FinanceUnifiedStatementScreen(activity, onChanged)
                        .openExpenseEvent(e.id, drafts, onManualActions)
                }
            }
            parent.addView(card); parent.addView(gap(6))
        }
        if (!showAllActivity && events.size > shown.size) parent.addView(button("View all ${events.size} activities  ›") {
            showAllActivity = true; refresh()
        })
        parent.addView(gap(9))
        if (data.bills.any { it.openingPaid > 0L }) parent.addView(text(
            "Earlier manual entries may have paid amounts without transaction dates. Balances include those payments.",
            10f, muted))
    }
}
