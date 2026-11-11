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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
    private fun separator(parent: LinearLayout) {
        parent.addView(View(activity).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, dp(1)))
    }

    fun show(requestedMonth: String? = null) {
        val initial = requestedMonth?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        var period = initial ?: stock.savedInitialMonths().lastOrNull()?.let { YearMonth.parse(it) }
            ?: stock.all().lastOrNull()?.month?.let { YearMonth.parse(it) }
            ?: YearMonth.now().minusMonths(1)
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
        previous.setOnClickListener { period = period.minusMonths(1); refresh() }
        next.setOnClickListener { period = period.plusMonths(1); refresh() }
        monthText.setOnClickListener {
            DatePickerDialog(activity, { _, y, m, _ -> period = YearMonth.of(y, m + 1); refresh() },
                period.year, period.monthValue - 1, 1).show()
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
