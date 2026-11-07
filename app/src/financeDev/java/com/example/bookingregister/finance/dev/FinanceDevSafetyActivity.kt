package com.example.bookingregister.finance.dev

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.bookingregister.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.text.NumberFormat
import java.util.Locale

/** Finance DEV offline preview. The production launcher and existing database are unchanged. */
class FinanceDevSafetyActivity : AppCompatActivity() {
    private val tabs = listOf("Dashboard", "Expenses", "Payments", "Vendors", "Reports")
    private var currentTab = "Dashboard"
    private val pageBackgroundColor = Color.rgb(247, 248, 250)
    private val ink = Color.rgb(32, 42, 56)
    private val muted = Color.rgb(94, 106, 120)
    private val accent = Color.rgb(7, 113, 49)
    private var expenseFilter = "All"
    private val expenseFilters = listOf("All", "Not paid", "Partially paid", "Paid")

    private data class ScreenSnapshot(
        val drafts: List<FinanceDemoDraftBill>,
        val activeCount: Long,
        val discardedCount: Long,
        val totalDraftPaise: Long
    )

    private val localDatabaseDelegate = lazy {
        FinanceDemoDraftDatabase.build(applicationContext)
    }
    private val localDatabase by localDatabaseDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        currentTab = savedInstanceState?.getString("finance_dev_tab")
            ?.takeIf { it in tabs } ?: "Dashboard"
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("finance_dev_tab", currentTab)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (localDatabaseDelegate.isInitialized()) localDatabase.close()
        super.onDestroy()
    }

    private fun refresh() {
        lifecycleScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    val dao = localDatabase.drafts()
                    ScreenSnapshot(
                        drafts = dao.recentActive(
                            FinanceDemoDraftPolicy.TENANT,
                            FinanceDemoDraftPolicy.PROPERTY,
                            FinanceDemoDraftPolicy.STATUS_ACTIVE
                        ),
                        activeCount = dao.activeCount(
                            FinanceDemoDraftPolicy.TENANT,
                            FinanceDemoDraftPolicy.PROPERTY,
                            FinanceDemoDraftPolicy.STATUS_ACTIVE
                        ),
                        discardedCount = dao.discardedCount(
                            FinanceDemoDraftPolicy.TENANT,
                            FinanceDemoDraftPolicy.PROPERTY,
                            FinanceDemoDraftPolicy.STATUS_DISCARDED
                        ),
                        totalDraftPaise = dao.totalActiveDraftPaise(
                            FinanceDemoDraftPolicy.TENANT,
                            FinanceDemoDraftPolicy.PROPERTY,
                            FinanceDemoDraftPolicy.STATUS_ACTIVE
                        )
                    )
                }
                snapshot.drafts.forEach(FinanceDemoDraftPolicy::validate)
                showScreen(snapshot)
            } catch (error: Exception) {
                showDatabaseError(error)
            }
        }
    }

    private val forest = Color.rgb(0, 109, 43)
    private val navy = Color.rgb(21, 36, 57)
    private val red = Color.rgb(198, 42, 69)
    private val blue = Color.rgb(29, 83, 174)
    private val gold = Color.rgb(193, 132, 13)
    private val purple = Color.rgb(113, 57, 211)

    private fun showScreen(snapshot: ScreenSnapshot) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        // Respect Android edge-to-edge system bars on modern devices.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(7))
        }
        header.addView(label("⌂", 26f, forest, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(dp(40), dp(42)))
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Booking Register", 18f, Color.BLACK, true).apply { isSingleLine = true })
            addView(label("Finance", 13f, ink, false))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(label("⋮", 25f, ink, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(dp(34), dp(42)))
        root.addView(header)
        val scroller = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(12))
        }
        when (currentTab) {
            "Dashboard" -> dashboard(content, snapshot)
            "Expenses" -> expenses(content, snapshot)
            "Payments" -> unavailable(content, "Payments", "Payment management is coming soon.")
            "Vendors" -> unavailable(content, "Vendors", "Vendor management is coming soon.")
            "Reports" -> unavailable(content, "Reports", "Financial reports are coming soon.")
        }
        scroller.addView(content)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomNavigation())
        setContentView(root)
        // The root must be attached to the window before requesting system insets.
        // Otherwise Android 15 can render the header beneath the status bar and
        // the navigation tabs beneath the gesture/navigation bar.
        ViewCompat.requestApplyInsets(root)
    }

    private fun bottomNavigation(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
        background = rounded(Color.rgb(250, 251, 252), 0f)
        val symbols = listOf("⌂", "▤", "⇄", "♙", "▥")
        tabs.forEachIndexed { index, tab ->
            val selected = currentTab == tab
            val item = LinearLayout(this@FinanceDevSafetyActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(1), dp(3), dp(1), dp(3))
                background = rounded(if (selected) Color.rgb(235, 248, 239) else Color.TRANSPARENT, 12f)
                addView(label(symbols[index], 22f, if (selected) forest else Color.BLACK, true).apply {
                    gravity = Gravity.CENTER
                })
                addView(label(tab, 11f, if (selected) forest else Color.BLACK, selected).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                })
                setOnClickListener { if (currentTab != tab) { currentTab = tab; refresh() } }
            }
            addView(item, LinearLayout.LayoutParams(0, dp(54), 1f))
        }
    }

    private fun dashboard(page: LinearLayout, snapshot: ScreenSnapshot) {
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(selector("▦  Demo Property 01  ⌄"), LinearLayout.LayoutParams(0, dp(44), 1.5f))
        controls.addView(spaceHorizontal(10))
        controls.addView(selector("▣  ${LocalDate.now().format(DateTimeFormatter.ofPattern("MMM yyyy"))}  ⌄"),
            LinearLayout.LayoutParams(0, dp(44), 1f))
        page.addView(controls)
        page.addView(space(9))
        // Financial values remain unpopulated until a verified ledger exists.
        metricPair(page, "▥", "Revenue", "—", forest, 0xFFEAF8ED.toInt(),
            "▤", "Expenses", "—", red, 0xFFFFEEF1.toInt(), large = true)
        page.addView(space(8))
        metricPair(page, "▤", "Operating Profit", "—", forest, 0xFFE7FAED.toInt(),
            "▢", "Cash / Bank", "—", blue, 0xFFEDF3FF.toInt(), large = true)
        page.addView(space(9))
        val wide = resources.configuration.screenWidthDp >= 700
        if (wide) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listOf(
                Triple("♧", "Vendor Payables", purple),
                Triple("♙", "Receivables", blue),
                Triple("▤", "GST / TDS / TCS", gold),
                Triple("▣", "Overdue", red)
            ).forEachIndexed { i, triple ->
                if (i > 0) row.addView(spaceHorizontal(7))
                row.addView(metricCard(triple.first, triple.second, "—", triple.third,
                    Color.rgb(251, 250, 255), false), LinearLayout.LayoutParams(0, dp(90), 1f))
            }
            page.addView(row)
        } else {
            metricPair(page, "♧", "Vendor Payables", "—", purple, 0xFFF8F3FF.toInt(),
                "♙", "Receivables", "—", blue, 0xFFF1F5FF.toInt(), large = false)
            page.addView(space(8))
            metricPair(page, "▤", "GST / TDS / TCS", "—", gold, 0xFFFFFAEE.toInt(),
                "▣", "Overdue", "—", red, 0xFFFFF0F3.toInt(), large = false)
        }
        page.addView(space(10))
        if (wide) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(departmentCard(), LinearLayout.LayoutParams(0, dp(278), 1f))
            row.addView(spaceHorizontal(10))
            row.addView(trendCard(), LinearLayout.LayoutParams(0, dp(278), 1f))
            page.addView(row)
        } else {
            page.addView(departmentCard())
            page.addView(space(10))
            page.addView(trendCard())
        }
        page.addView(space(12))
        val draftPanel = panel().apply {
            background = rounded(0xFFEAF8ED.toInt(), 16f)
            addView(label("Expense drafts", 14f, forest, true))
            addView(space(5))
            addView(label(formatRupees(snapshot.totalDraftPaise), 24f, navy, true))
            addView(label("${snapshot.activeCount} active  •  ${snapshot.discardedCount} discarded", 12f, muted, false))
            setOnClickListener { currentTab = "Expenses"; refresh() }
        }
        page.addView(draftPanel)
    }

    private fun selector(title: String): TextView = label(title, 13f, navy, true).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), 0, dp(8), 0)
        background = outlinedCard(Color.WHITE, 12f)
        maxLines = 1
        setOnClickListener {
            Toast.makeText(this@FinanceDevSafetyActivity, "Selection will be available when property data is connected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun metricPair(page: LinearLayout, li: String, lt: String, lv: String, lc: Int, lb: Int,
                           ri: String, rt: String, rv: String, rc: Int, rb: Int, large: Boolean) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(metricCard(li, lt, lv, lc, lb, large), LinearLayout.LayoutParams(0, dp(if (large) 76 else 70), 1f))
        row.addView(spaceHorizontal(9))
        row.addView(metricCard(ri, rt, rv, rc, rb, large), LinearLayout.LayoutParams(0, dp(if (large) 76 else 70), 1f))
        page.addView(row)
    }

    private fun metricCard(symbol: String, title: String, value: String, tint: Int,
                           fill: Int, large: Boolean): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(7), dp(5), dp(7))
        background = outlinedCard(fill, 16f)
        addView(label(symbol, if (large) 25f else 19f, tint, true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(if (large) 34 else 27), -2))
        addView(LinearLayout(this@FinanceDevSafetyActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), 0, 0, 0)
            addView(label(title, if (large) 12f else 10.5f, Color.BLACK, false).apply { maxLines = 2 })
            addView(space(2))
            addView(label(value, if (large) 20f else 18f, Color.BLACK, true))
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun departmentCard(): LinearLayout = panel().apply {
        background = outlinedCard(Color.WHITE, 16f)
        addView(label("Departmental Profit     ›", 18f, Color.BLACK, true))
        addView(space(6))
        listOf(
            Triple("▣", "Rooms", blue),
            Triple("♜", "Kitchen (F&B)", Color.rgb(219, 110, 30)),
            Triple("△", "Activities", gold),
            Triple("◇", "Other / Services", purple)
        ).forEachIndexed { i, (symbol, title, tint) ->
            val line = LinearLayout(this@FinanceDevSafetyActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(5), 0, dp(5))
                addView(label(symbol, 22f, tint, true), LinearLayout.LayoutParams(dp(35), -2))
                addView(label(title, 13f, ink, false), LinearLayout.LayoutParams(0, -2, 1f))
                addView(label("—", 16f, muted, true).apply { gravity = Gravity.END },
                    LinearLayout.LayoutParams(dp(35), -2))
            }
            addView(line)
            if (i < 3) addView(View(this@FinanceDevSafetyActivity).apply {
                setBackgroundColor(0xFFE8EBF0.toInt())
            }, LinearLayout.LayoutParams(-1, dp(1)))
        }
    }

    private fun trendCard(): LinearLayout = panel().apply {
        background = outlinedCard(Color.WHITE, 16f)
        addView(label("Revenue vs Expenses     ›", 18f, Color.BLACK, true))
        addView(space(8))
        addView(LinearLayout(this@FinanceDevSafetyActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(label("● Revenue", 12f, forest, true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(label("● Expenses", 12f, red, true), LinearLayout.LayoutParams(0, -2, 1f))
        })
        addView(space(12))
        addView(label("Chart available when financial records are connected", 13f, muted, false).apply {
            gravity = Gravity.CENTER
            background = rounded(0xFFF8FAFC.toInt(), 10f)
        }, LinearLayout.LayoutParams(-1, dp(112)))
    }

    private fun outlinedCard(fill: Int, radius: Float): GradientDrawable = rounded(fill, radius).apply {
        setStroke(dp(1), 0xFFE1E5ED.toInt())
    }

    private fun formatRupees(paise: Long): String =
        "₹" + NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
            minimumFractionDigits = if (paise % 100L == 0L) 0 else 2
            maximumFractionDigits = 2
        }.format(paise / 100.0)

    private fun expenses(page: LinearLayout, snapshot: ScreenSnapshot) {
        page.addView(label("Expenses", 23f, ink, true))
        page.addView(space(12))
        page.addView(Button(this).apply {
            text = getString(R.string.finance_dev_create_expense_draft)
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(accent, 10f)
            setOnClickListener { showExpenseForm(existing = null) }
        })
        page.addView(space(10))
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        expenseFilters.forEach { filter ->
            filterRow.addView(Button(this).apply {
                text = filter
                textSize = 11f
                isAllCaps = false
                setTextColor(if (expenseFilter == filter) Color.WHITE else ink)
                background = rounded(if (expenseFilter == filter) accent else Color.WHITE, 10f)
                setOnClickListener { expenseFilter = filter; refresh() }
            })
            filterRow.addView(spaceHorizontal(5))
        }
        page.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(filterRow)
        })
        val filteredDrafts = snapshot.drafts.filter { draft ->
            expenseFilter == "All" ||
                FinanceDemoDraftPolicy.paymentLabel(draft.paymentStatus) == expenseFilter
        }
        page.addView(space(8))
        page.addView(label("Showing ${filteredDrafts.size} of ${snapshot.drafts.size} recent active drafts", 12f, muted, false))
        if (filteredDrafts.isEmpty()) {
            page.addView(space(12))
            page.addView(panel().apply {
                addView(
                    label(
                        if (snapshot.drafts.isEmpty()) "No active drafts. Add a fictional expense to test offline persistence."
                        else "No drafts match this filter.",
                        14f,
                        muted,
                        false
                    )
                )
            })
        } else {
            filteredDrafts.forEach { draft ->
                page.addView(space(10))
                page.addView(draftCard(draft))
            }
        }
        if (snapshot.discardedCount > 0L) {
            page.addView(space(12))
            page.addView(
                label(
                    "${snapshot.discardedCount} discarded drafts are hidden but retained in local history.",
                    12f,
                    muted,
                    false
                )
            )
        }
    }

    private fun draftCard(draft: FinanceDemoDraftBill): LinearLayout = panel().apply {
        addView(label(draft.category, 17f, ink, true))
        addView(label(FinanceDemoDraftPolicy.moneyText(draft.amountPaise), 20f, ink, true))
        addView(
            label(
                "Vendor: ${FinanceDemoDraftPolicy.vendorName(draft.vendorCode)}",
                12f,
                muted,
                false
            )
        )
        addView(label("Invoice date: ${draft.invoiceDate}", 12f, muted, false))
        val paidDescription = if (draft.amountPaidPaise > 0L) {
            "  •  Marked paid ${FinanceDemoDraftPolicy.moneyText(draft.amountPaidPaise)}"
        } else {
            ""
        }
        addView(
            label(
                "Status: ${FinanceDemoDraftPolicy.paymentLabel(draft.paymentStatus)}$paidDescription",
                12f,
                muted,
                false
            )
        )
        addView(
            label(
                "Month: ${draft.accountingPeriod}  •  Revision ${draft.revision}  •  LOCAL DRAFT — NOT POSTED",
                12f,
                accent,
                true
            )
        )
        addView(space(10))
        addView(LinearLayout(this@FinanceDevSafetyActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(actionButton("History", false) { showDraftHistory(draft) })
            addView(spaceHorizontal(8))
            addView(actionButton("Edit", false) { showExpenseForm(draft) })
            addView(spaceHorizontal(8))
            addView(actionButton("Discard", true) { showDiscardDialog(draft) })
        })
    }

    private fun showDraftHistory(draft: FinanceDemoDraftBill) {
        lifecycleScope.launch {
            try {
                val events = withContext(Dispatchers.IO) {
                    localDatabase.drafts().events(draft.draftId)
                }
                val content = LinearLayout(this@FinanceDevSafetyActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20), dp(12), dp(20), dp(12))
                }
                content.addView(label("LOCAL HISTORY ONLY — NOT A FINANCIAL AUDIT LEDGER", 12f, accent, true))
                content.addView(space(12))
                val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
                events.forEach { event ->
                    val date = Instant.ofEpochMilli(event.occurredAtMillis)
                        .atZone(ZoneId.systemDefault()).format(formatter)
                    content.addView(label("${event.eventType}  •  Revision ${event.resultingRevision}", 15f, ink, true))
                    content.addView(label(date, 12f, muted, false))
                    content.addView(label("Reason: ${event.reason}", 13f, ink, false))
                    content.addView(space(14))
                }
                if (events.isEmpty()) content.addView(label("No history events found.", 13f, muted, false))
                AlertDialog.Builder(this@FinanceDevSafetyActivity)
                    .setTitle("Draft change history")
                    .setView(ScrollView(this@FinanceDevSafetyActivity).apply { addView(content) })
                    .setPositiveButton("Close", null)
                    .show()
            } catch (error: Exception) {
                Toast.makeText(this@FinanceDevSafetyActivity,
                    "Unable to load local draft history", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun unavailable(page: LinearLayout, title: String, description: String) {
        page.addView(label(title, 23f, ink, true))
        page.addView(space(12))
        page.addView(panel().apply {
            addView(label("Not yet implemented", 17f, accent, true))
            addView(space(8))
            addView(label(description, 14f, muted, false))
        })
    }

    private fun showExpenseForm(existing: FinanceDemoDraftBill?) {
        val vendors = FinanceDemoDraftPolicy.vendors
        val categories = FinanceDemoDraftPolicy.categories
        val paymentStatuses = FinanceDemoDraftPolicy.PaymentStatus.entries

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        form.addView(
            label(
                "Fictional local data only. Saving does not create an expense, payable or payment.",
                12f,
                accent,
                true
            )
        )
        form.addView(space(12))

        val vendor = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@FinanceDevSafetyActivity,
                android.R.layout.simple_spinner_dropdown_item,
                vendors.map { it.name }
            )
            setSelection(vendors.indexOfFirst { it.code == existing?.vendorCode }.coerceAtLeast(0))
        }
        addFormField(form, "Vendor", vendor)

        val category = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@FinanceDevSafetyActivity,
                android.R.layout.simple_spinner_dropdown_item,
                categories
            )
            setSelection(categories.indexOf(existing?.category).coerceAtLeast(0))
        }
        addFormField(form, "Expense category", category)

        val amount = EditText(this).apply {
            hint = "Example: 12500.00"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            existing?.let {
                setText(FinanceDemoDraftPolicy.moneyText(it.amountPaise).removePrefix("₹"))
            }
        }
        addFormField(form, "Total amount (₹)", amount)

        val initialDate = existing?.invoiceDate?.let(LocalDate::parse) ?: LocalDate.now()
        val invoiceDate = EditText(this).apply {
            isFocusable = false
            isClickable = true
            setText(initialDate.toString())
            setOnClickListener { showDatePicker(this) }
        }
        addFormField(form, "Invoice date", invoiceDate)

        val paymentStatus = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@FinanceDevSafetyActivity,
                android.R.layout.simple_spinner_dropdown_item,
                paymentStatuses.map { it.label }
            )
            setSelection(
                paymentStatuses.indexOfFirst { it.name == existing?.paymentStatus }.coerceAtLeast(0)
            )
        }
        addFormField(form, "Payment status (draft description only)", paymentStatus)

        val amountPaid = EditText(this).apply {
            hint = "Amount marked paid"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(
                existing?.let {
                    FinanceDemoDraftPolicy.moneyText(it.amountPaidPaise).removePrefix("₹")
                } ?: "0.00"
            )
        }
        addFormField(form, "Amount marked paid (₹)", amountPaid)

        val reason = if (existing != null) {
            EditText(this).apply {
                hint = "Why is this draft being changed?"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines = 2
                maxLines = 4
            }.also { addFormField(form, "Mandatory edit reason", it) }
        } else {
            null
        }

        fun applyPaymentMode(clearPartial: Boolean) {
            when (paymentStatuses[paymentStatus.selectedItemPosition]) {
                FinanceDemoDraftPolicy.PaymentStatus.NOT_PAID -> {
                    amountPaid.setText(getString(R.string.finance_dev_zero_amount))
                    amountPaid.isEnabled = false
                }
                FinanceDemoDraftPolicy.PaymentStatus.PAID -> {
                    amountPaid.setText(
                        amount.text.toString().ifBlank {
                            getString(R.string.finance_dev_zero_amount)
                        }
                    )
                    amountPaid.isEnabled = false
                }
                FinanceDemoDraftPolicy.PaymentStatus.PARTIALLY_PAID -> {
                    amountPaid.isEnabled = true
                    if (clearPartial) amountPaid.setText("")
                }
            }
        }
        paymentStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var firstSelection = true

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                applyPaymentMode(clearPartial = !firstSelection)
                firstSelection = false
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        applyPaymentMode(clearPartial = false)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "New fictional expense draft" else "Edit fictional draft")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton(if (existing == null) "Save draft" else "Save changes", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val totalPaise = FinanceDemoDraftPolicy.parseMoney(amount.text.toString())
                    val selectedPayment = paymentStatuses[paymentStatus.selectedItemPosition]
                    val paidPaise = when (selectedPayment) {
                        FinanceDemoDraftPolicy.PaymentStatus.NOT_PAID -> 0L
                        FinanceDemoDraftPolicy.PaymentStatus.PAID -> totalPaise
                        FinanceDemoDraftPolicy.PaymentStatus.PARTIALLY_PAID ->
                            FinanceDemoDraftPolicy.parseMoney(amountPaid.text.toString())
                    }
                    val input = FinanceDemoDraftPolicy.DraftInput(
                        vendorCode = vendors[vendor.selectedItemPosition].code,
                        category = categories[category.selectedItemPosition],
                        invoiceDate = invoiceDate.text.toString(),
                        amountPaise = totalPaise,
                        paymentStatus = selectedPayment,
                        amountPaidPaise = paidPaise
                    )
                    val now = System.currentTimeMillis()
                    if (existing == null) {
                        val row = FinanceDemoDraftPolicy.create(
                            input = input,
                            id = UUID.randomUUID().toString(),
                            nowMillis = now
                        )
                        persistCreate(row)
                    } else {
                        val reasonText = reason?.text?.toString().orEmpty()
                        val row = FinanceDemoDraftPolicy.edit(existing, input, reasonText, now)
                        persistChange(row, FinanceDemoDraftPolicy.editedEvent(row, reasonText))
                    }
                    dialog.dismiss()
                } catch (error: RuntimeException) {
                    amount.error = error.message ?: "Check the draft details"
                    Toast.makeText(
                        this,
                        error.message ?: "Check the draft details",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        dialog.show()
    }

    private fun showDiscardDialog(draft: FinanceDemoDraftBill) {
        val reason = EditText(this).apply {
            hint = "Why is this draft being discarded?"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2
            maxLines = 4
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Discard this local draft?")
            .setMessage(
                "The draft will disappear from the active list but remain in local change history. " +
                    "No financial transaction will be deleted."
            )
            .setView(reason)
            .setPositiveButton("Discard draft", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val reasonText = reason.text.toString()
                    val row = FinanceDemoDraftPolicy.discard(
                        original = draft,
                        reason = reasonText,
                        nowMillis = System.currentTimeMillis()
                    )
                    persistChange(row, FinanceDemoDraftPolicy.discardedEvent(row, reasonText))
                    dialog.dismiss()
                } catch (error: RuntimeException) {
                    reason.error = error.message ?: "A reason is mandatory"
                }
            }
        }
        dialog.show()
    }

    private fun persistCreate(row: FinanceDemoDraftBill) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    localDatabase.drafts().createWithEvent(
                        row,
                        FinanceDemoDraftPolicy.createdEvent(row)
                    )
                }
                Toast.makeText(
                    this@FinanceDevSafetyActivity,
                    "Fictional local draft saved — not posted",
                    Toast.LENGTH_SHORT
                ).show()
                refresh()
            } catch (error: Exception) {
                showDatabaseError(error)
            }
        }
    }

    private fun persistChange(row: FinanceDemoDraftBill, event: FinanceDemoDraftEvent) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    localDatabase.drafts().replaceWithEvent(row, event)
                }
                Toast.makeText(
                    this@FinanceDevSafetyActivity,
                    "Local draft history updated — not posted",
                    Toast.LENGTH_SHORT
                ).show()
                refresh()
            } catch (error: Exception) {
                showDatabaseError(error)
            }
        }
    }

    private fun showDatePicker(field: EditText) {
        val date = runCatching { LocalDate.parse(field.text.toString()) }
            .getOrElse { LocalDate.now() }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                field.setText(LocalDate.of(year, month + 1, day).toString())
            },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth
        ).show()
    }

    private fun addFormField(container: LinearLayout, title: String, field: View) {
        container.addView(label(title, 12f, muted, true))
        container.addView(field)
        container.addView(space(10))
    }

    private fun actionButton(
        title: String,
        destructive: Boolean,
        action: () -> Unit
    ): Button = Button(this).apply {
        text = title
        isAllCaps = false
        setTextColor(if (destructive) accent else ink)
        background = rounded(Color.rgb(242, 243, 246), 9f)
        layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
        setOnClickListener { action() }
    }

    private fun showDatabaseError(error: Exception) {
        Toast.makeText(this, "Finance DEV local draft error. No posting occurred.", Toast.LENGTH_LONG)
            .show()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            setBackgroundColor(pageBackgroundColor)
        }
        page.addView(label("FINANCE DEV — PREVIEW STOPPED", 22f, accent, true))
        page.addView(
            label(
                "Local draft data could not be validated or loaded. No data was posted.",
                15f,
                ink,
                false
            )
        )
        page.addView(label("Error category: ${error.javaClass.simpleName}", 12f, muted, false))
        setContentView(page)
    }

    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = rounded(Color.WHITE, 15f)
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.START
        if (bold) setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun space(height: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun spaceHorizontal(width: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(width), 1)
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
}
