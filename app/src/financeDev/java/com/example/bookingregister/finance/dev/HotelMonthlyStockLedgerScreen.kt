package com.example.bookingregister.finance.dev

import android.app.Activity
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.PopupMenu
import android.widget.Toast
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import androidx.appcompat.app.AlertDialog
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Compact, read-only month ledger backed by the existing stock store and Purchase catalog. */
class HotelMonthlyStockLedgerScreen(private val activity: Activity) {
    private val stock = HotelMonthlyStockStore(activity)
    private val ink = Color.rgb(27, 40, 54)
    private val muted = Color.rgb(103, 115, 129)
    private val green = Color.rgb(0, 113, 77)
    private val line = Color.rgb(230, 234, 240)
    private val surface = Color.rgb(248, 250, 252)
    private val format = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density + 0.5f).toInt()
    private fun col() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun text(value: String, size: Float = 13f, bold: Boolean = false, color: Int = ink) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun number(n: Double) = if (n == n.toLong().toDouble()) n.toLong().toString()
        else String.format(Locale.US, "%.3f", n).trimEnd('0').trimEnd('.')
    private fun rounded(fill: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(10).toFloat(); setStroke(dp(1), line)
    }
    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    private fun separator(parent: LinearLayout) {
        parent.addView(View(activity).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, dp(1)))
    }

    fun show(requestedMonth: String? = null) {
        val initial = requestedMonth?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        // FINANCE_STOCK_LEDGER_DIRECT_V18: always open the actual current month.
        var period = initial ?: YearMonth.now()
        runCatching { FinanceStockMonthEndReminderWorker.schedule(activity.applicationContext) }
        val dialog = android.app.Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val root = col().apply {
            setPadding(dp(14), dp(12), dp(14), dp(22))
            setBackgroundColor(Color.WHITE)
        }
        val header = row()
        header.addView(text("‹", 30f, true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Back to inventory"
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(43), dp(53)))
        val titles = col().apply {
            addView(text("Stock Ledger", 22f, true))
            addView(text("Monthly movement & consumption", 12f, color = muted))
        }
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        val more = text("⋮", 25f, true, ink).apply {
            gravity = Gravity.CENTER
            contentDescription = "Stock Ledger actions"
            setPadding(dp(8), 0, dp(8), 0)
        }
        header.addView(more, LinearLayout.LayoutParams(dp(43), dp(53)))
        root.addView(header)
        val monthRow = row().apply { setPadding(0, dp(12), 0, dp(9)) }
        val previous = text("‹", 24f, true, green).apply { gravity = Gravity.CENTER }
        val monthText = text("", 16f, true).apply { gravity = Gravity.CENTER }
        val next = text("›", 24f, true, green).apply { gravity = Gravity.CENTER }
        monthRow.addView(previous, LinearLayout.LayoutParams(dp(45), dp(46)))
        monthRow.addView(monthText, LinearLayout.LayoutParams(0, dp(46), 1f))
        monthRow.addView(next, LinearLayout.LayoutParams(dp(45), dp(46)))
        root.addView(monthRow)
        val status = text("", 12f, true, muted).apply { setPadding(dp(6), dp(5), dp(6), dp(10)) }
        root.addView(status)
        val firstMonthArea = col()
        root.addView(firstMonthArea)
        val search = EditText(activity).apply {
            hint = "Search stock item"
            setSingleLine(true)
            textSize = 14f
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(surface)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(11) })
        val tableHead = row().apply { setPadding(dp(7), dp(10), dp(7), dp(10)); setBackgroundColor(surface) }
        fun headerCell(value: String, weight: Float, align: Int) {
            tableHead.addView(text(value, 10f, true, muted).apply { gravity = align },
                LinearLayout.LayoutParams(0, -2, weight))
        }
        headerCell("Item", 2.3f, Gravity.START)
        headerCell("Open", 1f, Gravity.CENTER)
        headerCell("In", 0.8f, Gravity.CENTER)
        headerCell("Close", 1f, Gravity.CENTER)
        headerCell("Used", 1f, Gravity.CENTER)
        root.addView(tableHead)
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val content = col()
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val note = text("Used = Opening + Received − Closing. Quantities only; wastage and transfers require separate adjustments.",
            11f, color = muted).apply { setPadding(dp(5), dp(12), dp(5), dp(4)) }
        root.addView(note)
        fun information(value: String) {
            content.addView(text(value, 13f, color = muted).apply { setPadding(dp(8), dp(13), dp(8), dp(13)) })
        }
        fun tableRow(name: String, unit: String, opening: Double, received: Double, closing: Double?) {
            val result = closing?.let { opening + received - it }
            val r = row().apply { setPadding(dp(7), dp(11), dp(7), dp(11)) }
            val names = col().apply {
                addView(text(name, 12f, true))
                addView(text(unit, 10f, color = muted))
            }
            r.addView(names, LinearLayout.LayoutParams(0, -2, 2.3f))
            fun figure(value: String, weight: Float, emphasized: Boolean = false) {
                r.addView(text(value, 12f, emphasized, if (emphasized) green else ink).apply {
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(0, -2, weight))
            }
            figure(number(opening), 1f)
            figure(number(received), 0.8f)
            figure(closing?.let(::number) ?: "—", 1f)
            figure(result?.let(::number) ?: "—", 1f, result != null && result >= 0)
            content.addView(r)
            separator(content)
            if (result != null && result < -0.000001) information("Review $name: closing exceeds recorded available stock")
        }
        fun refresh() {
            val key = period.toString()
            monthText.text = period.format(format) + "  ▾"
            content.removeAllViews()
            val closed = stock.all().firstOrNull { it.month == key }
            val opening = closed?.opening ?: stock.opening(key).orEmpty()
            val purchased = closed?.purchased ?: stock.purchased(key).first
            val closing = closed?.closing.orEmpty()
            status.text = if (closed != null) "APPROVED · ${closed.approvedBy}"
                else if (stock.opening(key) != null) "OPENING SAVED · MONTH NOT CLOSED"
                else "NO OPENING STOCK SAVED"
            status.setTextColor(if (closed != null) green else muted)
            // FINANCE_STOCK_LEDGER_DIRECT_V18: setup once, otherwise chronological carry-forward.
            firstMonthArea.removeAllViews()
            val initialMonths = stock.savedInitialMonths()
            if (initialMonths.isEmpty() && stock.all().isEmpty()) {
                firstMonthArea.addView(text("FIRST-TIME STOCK SETUP", 12f, true, green).apply {
                    setPadding(dp(8), dp(12), dp(8), dp(3))
                })
                val check = CheckBox(activity).apply {
                    text = "This is our first stock month (${period.format(format)})"
                    textSize = 13f
                }
                firstMonthArea.addView(check)
                val enter = text("Enter first opening stock  ›", 15f, true, green).apply {
                    setPadding(dp(12), dp(8), dp(12), dp(14))
                    isEnabled = false
                    alpha = .40f
                    setOnClickListener {
                        if (check.isChecked) {
                            dialog.dismiss()
                            HotelMonthlyStockScreen(activity).editOpening(key)
                        }
                    }
                }
                check.setOnCheckedChangeListener { _, checked ->
                    enter.isEnabled = checked
                    enter.alpha = if (checked) 1f else .40f
                }
                firstMonthArea.addView(enter)
            } else {
                val first = initialMonths.firstOrNull()?.let(YearMonth::parse)
                if (first != null && period > first && stock.opening(key) == null && closed == null) {
                    val previousPeriod = period.minusMonths(1)
                    firstMonthArea.addView(text("Close ${previousPeriod.format(format)} to carry opening stock into this month.",
                        12f, true, Color.rgb(166, 77, 33)).apply { setPadding(dp(7), dp(9), dp(7), dp(12)) })
                }
                if (closed == null && stock.opening(key) != null) {
                    val due = period < YearMonth.now() ||
                        (period == YearMonth.now() && LocalDate.now().dayOfMonth == period.lengthOfMonth())
                    if (due) {
                        val message = if (period < YearMonth.now()) "CLOSING OVERDUE · Complete the physical stock count"
                            else "CLOSING DUE TODAY · Count remaining stock at the end of the day"
                        firstMonthArea.addView(text(message, 12f, true, Color.rgb(168, 65, 45)).apply {
                            setPadding(dp(7), dp(8), dp(7), dp(12))
                            setOnClickListener { dialog.dismiss(); HotelMonthlyStockScreen(activity).editClosing(key) }
                        })
                    }
                }
            }
            val changes = stock.corrections(key)
            if (changes.isNotEmpty()) information("CORRECTION HISTORY · ${changes.size} change(s)\n" + changes.joinToString("\n"))
            if (closed != null && stock.receiptsChangedAfterClose(key))
                information("Warning: receipts changed after approval. Reconcile the period before relying on consumption.")
            val begins = opening.associateBy { it.itemId }
            val receipts = purchased.associateBy { it.itemId }
            val ends = closing.associateBy { it.itemId }
            val ids = (begins.keys + receipts.keys + ends.keys).distinct()
            val catalog = HotelPurchaseCustomItemStore(activity).catalogItems(includeArchived = true).associateBy { it.id }
            val categories = HotelPurchaseCategoryStore(activity).allIncludingArchived().associate { it.first.id to it.first.name }
            val query = search.text.toString().trim()
            val groups = ids.map { id ->
                val snap = begins[id] ?: receipts[id] ?: ends.getValue(id)
                val category = catalog[id]?.categoryId?.let { categories[it] } ?: "Other / historical items"
                Triple(id, category, snap)
            }.filter { query.isBlank() || it.third.name.contains(query, true) }
                .groupBy { it.second }
            if (groups.isEmpty()) {
                information(if (ids.isEmpty()) "No saved opening or received stock for this month."
                    else "No matching items.")
            }
            groups.toSortedMap().forEach { (category, entries) ->
                content.addView(text("$category · ${entries.size} items", 13f, true).apply {
                    setPadding(dp(9), dp(17), dp(8), dp(9))
                })
                entries.sortedBy { it.third.name.lowercase(Locale.ROOT) }.forEach { (id, _, item) ->
                    tableRow(item.name, item.unit, begins[id]?.quantity ?: 0.0,
                        receipts[id]?.quantity ?: 0.0, ends[id]?.quantity)
                }
            }
            if (closed != null) information("Approved closing automatically carries forward to ${period.plusMonths(1).format(format)}.")
        }
        // Restrict date navigation to the declared initial month through today.
        fun changePeriod(target: YearMonth) {
            val first = stock.savedInitialMonths().firstOrNull()?.let(YearMonth::parse)
            when {
                target > YearMonth.now() -> toast("Cannot view a future stock month")
                first != null && target < first -> toast("Stock started in ${first.format(format)}")
                else -> { period = target; refresh() }
            }
        }
        previous.setOnClickListener { changePeriod(period.minusMonths(1)) }
        next.setOnClickListener { changePeriod(period.plusMonths(1)) }
        monthText.setOnClickListener {
            DatePickerDialog(activity, { _, y, m, _ -> changePeriod(YearMonth.of(y, m + 1)) },
                period.year, period.monthValue - 1, 1).show()
        }
        more.setOnClickListener {
            val month = period.toString()
            val isApproved = stock.all().any { it.month == month }
            val available = stock.opening(month) != null
            val readyToClose = (period < YearMonth.now() ||
                (period == YearMonth.now() && LocalDate.now().dayOfMonth == period.lengthOfMonth()))
            PopupMenu(activity, more).apply {
                menu.add("Opening stock").isEnabled = stock.initialOpening(month) != null
                menu.add("Closing stock").isEnabled = isApproved || (readyToClose && available)
                menu.add("Enable closing notifications")
                setOnMenuItemClickListener { item ->
                    when (item.title.toString()) {
                        "Opening stock" -> { dialog.dismiss(); HotelMonthlyStockScreen(activity).editOpening(month) }
                        "Closing stock" -> { dialog.dismiss(); HotelMonthlyStockScreen(activity).editClosing(month) }
                        else -> {
                            FinanceStockMonthEndReminderWorker.schedule(activity.applicationContext)
                            if (Build.VERSION.SDK_INT >= 33 &&
                                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                AlertDialog.Builder(activity)
                                    .setTitle("Monthly stock reminders")
                                    .setMessage("Allow Finance DEV notifications to receive a month-end stock closing reminder. The ledger will still display due and overdue warnings without permission.")
                                    .setPositiveButton("Continue") { _, _ ->
                                        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1911)
                                    }.setNegativeButton("Not now", null).show()
                            } else toast("Month-end closing reminders enabled")
                        }
                    }
                    true
                }
                show()
            }
        }
        val refreshTask = Runnable { refresh() }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                search.removeCallbacks(refreshTask)
                search.postDelayed(refreshTask, 180)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        refresh()
        dialog.setContentView(root)
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
