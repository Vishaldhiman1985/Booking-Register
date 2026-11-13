package com.example.bookingregister.finance.dev

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.PopupMenu
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
    @Deprecated("Handled for isolated Finance DEV purchasing navigation")
    override fun onBackPressed() {
        if (currentTab == "Purchase" && purchaseScreen?.navigateBack() == true) return
        super.onBackPressed()
    }
    private val tabs = listOf("Dashboard", "Expenses", "Payments", "Purchase", "Reports")
    private var currentTab = "Dashboard"
    private var purchaseScreen: HotelPurchaseOrderScreen? = null
    private val pageBackgroundColor = Color.rgb(247, 248, 250)
    private val ink = Color.rgb(32, 42, 56)
    private val muted = Color.rgb(94, 106, 120)
    private val accent = Color.rgb(7, 113, 49)
    private var paymentVendorIndex: Int? = null
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
    private val expenseCatalog by lazy { FinanceExpenseCatalogLoader.load(applicationContext) }
    private val expenseSearch by lazy { FinanceExpenseSmartSearch(expenseCatalog) }
    private val expenseVisibility by lazy { FinanceExpenseVisibility(applicationContext) }

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

    /** Shared Finance DEV vendor directory. Same vendor IDs and preferences used by Purchase. */
    private fun showGlobalCatalog() {
        FinanceGlobalCatalogScreen(this).show { refresh() }
    }

    private fun showGlobalVendors() {
        val vendorContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        HotelPurchaseVendorScreen(this).render(vendorContainer)
        AlertDialog.Builder(this)
            .setTitle("Vendors")
            .setView(ScrollView(this).apply { addView(vendorContainer) })
            .setPositiveButton("Close") { _, _ -> refresh() }
            .show()
    }
    private fun showScreen(snapshot: ScreenSnapshot) {
        purchaseScreen = if (currentTab == "Purchase") HotelPurchaseOrderScreen(this) else null
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
        header.addView(label("⋮", 25f, ink, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { anchor ->
                if (currentTab == "Expenses") {
                    PopupMenu(this@FinanceDevSafetyActivity, anchor).apply {
                        expenseFilters.forEachIndexed { index, filter ->
                            menu.add(0, index, index, if (filter == "All") "All Expenses" else filter).apply {
                                isCheckable = true
                                isChecked = expenseFilter == filter
                            }
                        }
                        menu.add(0, 100, 100, "Accounting Standards & Tax Rules")
                        menu.add(0, 101, 101, "Vendors")
                        menu.add(0, 102, 102, "Global Catalog")
                        setOnMenuItemClickListener { item ->
                            if (item.itemId == 100) {
                                showAccountingRules()
                            } else if (item.itemId == 101) {
                                showGlobalVendors()
                            } else if (item.itemId == 102) {
                                showGlobalCatalog()
                            } else {
                                expenseFilter = expenseFilters[item.itemId]
                                refresh()
                            }
                            true
                        }
                        show()
                    }
                } else if (currentTab == "Purchase") {
                    purchaseScreen?.showMenu(anchor)
                } else {
                                        PopupMenu(this@FinanceDevSafetyActivity, anchor).apply {
                        menu.add("Vendors")
                        menu.add("Global Catalog")
                        setOnMenuItemClickListener { selected ->
                            if (selected.title.toString() == "Global Catalog") showGlobalCatalog()
                            else showGlobalVendors()
                            true
                        }
                        show()
                    }
                }
            }
        }, LinearLayout.LayoutParams(dp(34), dp(42)))
        root.addView(header)
        val scroller = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(12))
        }
        when (currentTab) {
            "Dashboard" -> dashboard(content, snapshot)
            "Expenses" -> expenses(content, snapshot)
            "Payments" -> if (paymentVendorIndex == null) payments(content, snapshot) else paymentDetails(content, snapshot, paymentVendorIndex!!)
            "Purchase" -> purchaseScreen?.apply { bindToolbar(header); render(content) }
            "Reports" -> unavailable(content, "Reports", "Financial reports are coming soon.")
        }
        val contentArea = FrameLayout(this)
        if (currentTab == "Purchase") {
            // Purchase has its own independent scrolling item list and fixed footer.
            contentArea.addView(content, FrameLayout.LayoutParams(-1, -1))
        } else {
            scroller.addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            contentArea.addView(scroller, FrameLayout.LayoutParams(-1, -1))
        }
        if (currentTab == "Expenses") {
            val addExpense = TextView(this).apply {
                text = "+"
                textSize = 30f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                elevation = dp(7).toFloat()
                background = rounded(forest, 50f)
                contentDescription = "Add Expense"
                setOnClickListener { showExpenseForm(null) }
            }
            contentArea.addView(addExpense, FrameLayout.LayoutParams(dp(58), dp(58),
                Gravity.END or Gravity.BOTTOM).apply {
                marginEnd = dp(18)
                bottomMargin = dp(18)
            })
        }
        root.addView(contentArea, LinearLayout.LayoutParams(-1, 0, 1f))
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
                setOnClickListener { if (currentTab != tab || paymentVendorIndex != null) { currentTab = tab; paymentVendorIndex = null; refresh() } }
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

    /** One source for Finance vendors; historical demo identifiers are read-only fallbacks. */
    private fun sharedVendorOptions(): List<FinanceDemoDraftPolicy.VendorOption> =
        HotelPurchaseVendorStore(this).all().map {
            FinanceDemoDraftPolicy.VendorOption(it.id, it.name)
        }

    private fun vendorDisplayName(code: String): String = when {
        code == "no_payee" -> "No vendor / payee"
        code.startsWith("one_time:") -> code.removePrefix("one_time:")
        else -> sharedVendorOptions().firstOrNull { it.code == code }?.name
            ?: FinanceDemoDraftPolicy.vendors.firstOrNull { it.code == code }?.name
            ?: "Unknown vendor (" + code.take(8) + ")"
    }
    private fun expenses(page: LinearLayout, snapshot: ScreenSnapshot) {
        page.addView(label("Expenses", 24f, navy, true))
        page.addView(space(11))
        page.addView(label("${LocalDate.now().format(DateTimeFormatter.ofPattern("MMM yyyy"))}  ·  Local drafts (not posted)", 12f, muted, false))
        page.addView(space(10))
        page.addView(TextView(this).apply {
            text = "+ Add Expense"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(forest)
            gravity = Gravity.CENTER
            background = outlinedCard(0xFFEAF8EF.toInt(), 10f)
            minHeight = dp(48)
            contentDescription = "Add expense draft"
            setOnClickListener { showExpenseForm(null) }
        })
        page.addView(space(8))
        page.addView(label("Category  >  Expense Type  >  Department  >  Amount", 12f, muted, false))
        page.addView(space(12))
        val drafts = snapshot.drafts
        val total = drafts.sumOf { it.amountPaise }
        val paid = drafts.sumOf { it.amountPaidPaise }
        val pending = total - paid
        val summaries = listOf(
            Triple("Total Drafts", formatRupees(total), 0xFFEDF4FF.toInt()),
            Triple("Marked Paid", formatRupees(paid), 0xFFEAF8EF.toInt()),
            Triple("Unpaid Balance", formatRupees(pending), 0xFFFFEEF0.toInt())
        )
        val summaryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        summaries.forEachIndexed { i, item ->
            if (i > 0) summaryRow.addView(spaceHorizontal(6))
            summaryRow.addView(softSummary(item.first, item.second, item.third), LinearLayout.LayoutParams(0, dp(85), 1f))
        }
        page.addView(summaryRow)
        page.addView(space(12))
        val filtered = drafts.filter { expenseFilter == "All" || FinanceDemoDraftPolicy.paymentLabel(it.paymentStatus) == expenseFilter }
        page.addView(label("${filtered.size} expense draft(s)", 13f, muted, false))
        if (filtered.isEmpty()) {
            page.addView(space(12))
            page.addView(label("No drafts match this selection.", 14f, muted, false))
        } else filtered.forEach {
            page.addView(space(8))
            page.addView(draftCard(it))
        }
        if (snapshot.discardedCount > 0) {
            page.addView(space(12))
            page.addView(label("${snapshot.discardedCount} discarded draft(s) retained in local history.", 12f, muted, false))
        }
        page.addView(space(82)) // Keep the last card clear of the floating Add Expense button.
    }

    private fun draftCard(draft: FinanceDemoDraftBill): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(13), dp(11), dp(13), dp(11))
        background = outlinedCard(Color.WHITE, 12f)
        val first = LinearLayout(this@FinanceDevSafetyActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        first.addView(label("▤", 23f, forest, true), LinearLayout.LayoutParams(dp(34), -2))
        val textColumn = LinearLayout(this@FinanceDevSafetyActivity).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(label(draft.expenseTypeLabel ?: draft.category, 15f, navy, true))
        if (draft.expenseCategoryCode != null) {
            textColumn.addView(label("${draft.category} · ${draft.expenseTypeCode}", 11f, muted, false))
        }
        textColumn.addView(label("${draft.department}  ·  ${vendorDisplayName(draft.vendorCode)}  ·  ${draft.invoiceDate}", 11f, muted, false))
        first.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
        val amountColumn = LinearLayout(this@FinanceDevSafetyActivity).apply { orientation = LinearLayout.VERTICAL }
        amountColumn.addView(label(formatRupees(draft.amountPaise), 15f, navy, true).apply { gravity = Gravity.END })
        amountColumn.addView(label(FinanceDemoDraftPolicy.paymentLabel(draft.paymentStatus), 11f,
            if (draft.amountPaidPaise >= draft.amountPaise) forest else red, true).apply { gravity = Gravity.END })
        first.addView(amountColumn)
        val editIcon = label("✎", 22f, forest, false).apply {
            gravity = Gravity.CENTER
            contentDescription = "Expense actions"
            setOnClickListener { anchor ->
                PopupMenu(this@FinanceDevSafetyActivity, anchor).apply {
                    menu.add("History")
                    menu.add("Edit")
                    menu.add("Discard")
                    setOnMenuItemClickListener { item ->
                        when (item.title.toString()) {
                            "History" -> showDraftHistory(draft)
                            "Edit" -> showExpenseForm(draft)
                            "Discard" -> showDiscardDialog(draft)
                        }
                        true
                    }
                    show()
                }
            }
        }
        first.addView(editIcon, LinearLayout.LayoutParams(dp(38), dp(44)))
        addView(first)
        addView(space(6))
        addView(label("Marked paid ${formatRupees(draft.amountPaidPaise)}  ·  Due ${formatRupees(draft.amountPaise - draft.amountPaidPaise)}", 11f, muted, false))
        addView(label("${draft.accountingPeriod}  ·  Local draft, not posted", 11f, forest, true))
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

    /** Payment UI preview: draft balances are illustrative, never posted or settled. */
    private fun payments(page: LinearLayout, snapshot: ScreenSnapshot) {
        val pending = snapshot.drafts.filter {
            it.status == FinanceDemoDraftPolicy.STATUS_ACTIVE && it.amountPaise > it.amountPaidPaise
        }
        val total = pending.sumOf { it.amountPaise - it.amountPaidPaise }
        page.addView(label("Payments", 24f, navy, true))
        page.addView(label("Pending bills & payment history", 12f, muted, false))
        page.addView(space(14))
        val cards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cards.addView(softSummary("Pending draft bills", formatRupees(total), 0xFFFFECEC.toInt()), LinearLayout.LayoutParams(0, dp(88), 1f))
        cards.addView(spaceHorizontal(9))
        cards.addView(softSummary("Paid this month", "—", 0xFFEAF8EF.toInt()), LinearLayout.LayoutParams(0, dp(88), 1f))
        page.addView(cards)
        page.addView(space(15))
        page.addView(Button(this).apply {
            text = "+ Make Payment"; isAllCaps = false; textSize = 16f
            setTextColor(Color.WHITE); background = rounded(forest, 12f)
            setOnClickListener { paymentVendorIndex = 0; refresh() }
        }, LinearLayout.LayoutParams(-1, dp(50)))
        page.addView(space(19))
        page.addView(label("Pending vendors", 18f, navy, true))
        page.addView(space(8))
        var any = false
        sharedVendorOptions().forEachIndexed { index, vendor ->
            val bills = pending.filter { it.vendorCode == vendor.code }
            if (bills.isNotEmpty()) {
                any = true
                val due = bills.sumOf { it.amountPaise - it.amountPaidPaise }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(13), dp(12), dp(13))
                    background = outlinedCard(Color.WHITE, 13f)
                }
                row.addView(label("▣", 23f, forest, true), LinearLayout.LayoutParams(dp(38), -2))
                row.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label(vendor.name, 15f, navy, true))
                    addView(label("${bills.size} unpaid bill(s)", 12f, muted, false))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.END
                    addView(label(formatRupees(due), 16f, navy, true))
                    addView(label("Pay  ›", 13f, forest, true))
                })
                row.setOnClickListener { paymentVendorIndex = index; refresh() }
                page.addView(row); page.addView(space(9))
            }
        }
        if (!any) page.addView(label("No unpaid draft bills.", 13f, muted, false))
        page.addView(space(17))
        page.addView(label("Recent payments", 18f, navy, true))
        page.addView(space(6))
        page.addView(label("No payments recorded yet.", 13f, muted, false))
        page.addView(space(8))
        page.addView(label("Offline preview · Draft bills are not confirmed liabilities.", 11f, muted, false))
    }

    private fun softSummary(title: String, amount: String, tint: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(10), dp(10))
            background = rounded(tint, 15f)
            addView(label(title, 12f, muted, false))
            addView(space(7))
            addView(label(amount, 20f, navy, true))
        }

    /** Simple three-stage payment flow. Review is read-only until a payable ledger exists. */
    private fun paymentDetails(page: LinearLayout, snapshot: ScreenSnapshot, initialIndex: Int) {
        val vendors = sharedVendorOptions()
        if (vendors.isEmpty()) {
            page.addView(label("Add a vendor from Home > â‹® > Vendors first.", 14f, muted, false))
            return
        }
        val drafts = snapshot.drafts.filter {
            it.status == FinanceDemoDraftPolicy.STATUS_ACTIVE && it.amountPaise > it.amountPaidPaise
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(label("‹", 30f, navy, true).apply {
            setOnClickListener { paymentVendorIndex = null; refresh() }
        }, LinearLayout.LayoutParams(dp(40), dp(42)))
        top.addView(label("Make Payment", 23f, navy, true), LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(top); page.addView(space(13))

        val vendorCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(0xFFEAF8EF.toInt(), 14f)
        }
        val picker = Spinner(this).apply {
            adapter = ArrayAdapter(this@FinanceDevSafetyActivity, android.R.layout.simple_spinner_dropdown_item, vendors.map { it.name })
            setSelection(initialIndex.coerceIn(0, vendors.lastIndex))
        }
        vendorCard.addView(label("PAY TO", 11f, forest, true))
        vendorCard.addView(picker)
        val balance = label("", 20f, navy, true)
        vendorCard.addView(balance)
        page.addView(vendorCard); page.addView(space(17))

        page.addView(label("Amount to pay (₹)", 16f, navy, true))
        val amountField = EditText(this).apply {
            hint = "Enter amount"; setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 23f
        }
        page.addView(amountField); page.addView(space(17))

        val billHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        billHeader.addView(label("Bills being settled", 17f, navy, true), LinearLayout.LayoutParams(0, -2, 1f))
        billHeader.addView(label("Oldest first", 12f, forest, true))
        page.addView(billHeader); page.addView(space(8))
        val billRows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(11))
            minimumHeight = 0
            background = outlinedCard(Color.WHITE, 13f)
        }
        page.addView(billRows, LinearLayout.LayoutParams(-1, -2)); page.addView(space(14))
        val methodLabel = label("Payment method", 15f, navy, true)
        page.addView(methodLabel)
        val method = Spinner(this).apply {
            adapter = ArrayAdapter(this@FinanceDevSafetyActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("UPI", "Bank Transfer", "Cash"))
        }
        page.addView(method); page.addView(space(10))
        val optionalToggle = label("More details (optional)  ▾", 14f, forest, true).apply {
            setPadding(0, dp(10), 0, dp(10))
        }
        page.addView(optionalToggle)
        val optional = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val date = EditText(this).apply {
            isFocusable = false; isClickable = true; setText(LocalDate.now().toString())
            setOnClickListener { showDatePicker(this) }
        }
        addFormField(optional, "Payment date", date)
        addFormField(optional, "Reference number", EditText(this).apply { hint = "Optional reference"; setSingleLine(true) })
        addFormField(optional, "Remarks", EditText(this).apply { hint = "Optional remarks"; setSingleLine(true) })
        page.addView(optional)
        optionalToggle.setOnClickListener {
            optional.visibility = if (optional.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            optionalToggle.text = if (optional.visibility == View.VISIBLE) "More details (optional)  ▴" else "More details (optional)  ▾"
        }
        page.addView(space(14))
        val review = Button(this).apply {
            text = "Review Payment"; isAllCaps = false; textSize = 16f
            setTextColor(Color.WHITE); background = rounded(forest, 12f)
        }
        page.addView(review, LinearLayout.LayoutParams(-1, dp(52)))
        page.addView(space(9))
        page.addView(label("Preview only · No payment is saved.", 11f, muted, false))

        fun openBills(): List<FinanceDemoDraftBill> = drafts.filter {
            it.vendorCode == vendors[picker.selectedItemPosition].code
        }.sortedWith(compareBy<FinanceDemoDraftBill> { it.invoiceDate }.thenBy { it.draftId })
        fun amount(): Long = runCatching { FinanceDemoDraftPolicy.parseMoney(amountField.text.toString()) }.getOrDefault(0L)
        fun render() {
            val bills = openBills()
            val total = bills.sumOf { it.amountPaise - it.amountPaidPaise }
            balance.text = "Outstanding: ${formatRupees(total)}"
            billRows.removeAllViews()
            if (bills.isEmpty()) {
                billRows.addView(label("No pending bills for this vendor.", 13f, muted, false))
            } else {
                var remaining = amount()
                bills.forEach { bill ->
                    val due = bill.amountPaise - bill.amountPaidPaise
                    val applied = minOf(due, remaining)
                    remaining -= applied
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    row.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(label(bill.category, 14f, navy, true))
                        addView(label("${bill.invoiceDate} · Due ${formatRupees(due)}", 11f, muted, false))
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    row.addView(label(formatRupees(applied), 14f, navy, true))
                    billRows.addView(row); billRows.addView(space(9))
                }
                val left = (total - amount()).coerceAtLeast(0L)
                billRows.addView(label("Balance after payment: ${formatRupees(left)}", 13f, forest, true))
                if (amount() > total) billRows.addView(label("Amount exceeds outstanding bills", 12f, red, true))
            }
        }
        picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                paymentVendorIndex = position; amountField.setText(""); render()
            }
        }
        amountField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render()
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        review.setOnClickListener {
            try {
                val bills = openBills()
                val paying = FinanceDemoDraftPolicy.parseMoney(amountField.text.toString())
                val total = bills.sumOf { it.amountPaise - it.amountPaidPaise }
                require(paying > 0L) { "Enter a payment amount" }
                require(paying <= total) { "Payment exceeds outstanding bills" }
                val allocations = FinanceDemoPaymentPolicy.oldestFirst(bills.map {
                    FinanceDemoPaymentPolicy.OpenBill(it.draftId, it.invoiceDate, it.amountPaise - it.amountPaidPaise)
                }, paying)
                val byId = bills.associateBy { it.draftId }
                val message = buildString {
                    append("Vendor: ${vendors[picker.selectedItemPosition].name}\n")
                    append("Amount: ${formatRupees(paying)}\n")
                    append("Method: ${method.selectedItem}\n")
                    append("Balance after payment: ${formatRupees(total - paying)}\n\n")
                    allocations.allocations.forEach { append("${byId.getValue(it.billId).category}: ${formatRupees(it.appliedPaise)}\n") }
                    append("\nPreview only. No payment is recorded.")
                }
                AlertDialog.Builder(this).setTitle("Review Payment").setMessage(message)
                    .setPositiveButton("Close", null).show()
            } catch (error: Exception) {
                Toast.makeText(this, error.message ?: "Check payment details", Toast.LENGTH_LONG).show()
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


    /** Search suggests a canonical classification, then opens the real local draft form. */
    private fun showSmartExpenseSearch(
        onSelected: ((SmartExpenseCategory, SmartExpenseType) -> Unit)? = null
    ) {
        val catalog = try { expenseCatalog } catch (error: Exception) {
            Toast.makeText(this, "Expense master could not be loaded; no data changed", Toast.LENGTH_LONG).show()
            return
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(12))
        }
        container.addView(label("Search what was purchased or paid for", 14f, navy, true))
        container.addView(space(8))
        val searchBox = EditText(this).apply {
            hint = "Coal, koyla, kolla, DTH, gas..."
            setSingleLine(true)
            textSize = 16f
        }
        container.addView(searchBox)
        container.addView(space(8))
        container.addView(label("Confirm a suggestion to continue to the expense draft.", 12f, muted, false))
        container.addView(space(10))
        val suggestions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(suggestions)
        container.addView(space(12))
        lateinit var dialog: AlertDialog
        fun accept(category: SmartExpenseCategory, type: SmartExpenseType) {
            if (!expenseVisibility.typeVisible(category, type)) {
                Toast.makeText(this, "Ask the manager to enable this expense type", Toast.LENGTH_LONG).show()
                return
            }
            dialog.dismiss()
            if (onSelected == null) showExpenseForm(null, category to type)
            else onSelected(category, type)
        }
        container.addView(Button(this).apply {
            text = "Choose category / type manually"
            isAllCaps = false
            setOnClickListener {
                showManualExpenseTypePicker(catalog) { category, type -> accept(category, type) }
            }
        })
        val settings = TextView(this).apply {
            text = "Manage visible categories & types (DEV preview)"
            textSize = 12f
            setTextColor(forest)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(10), dp(4), dp(10))
        }
        container.addView(settings)

        fun renderSuggestions() {
            suggestions.removeAllViews()
            val input = searchBox.text.toString()
            val result = expenseSearch.search(input)
            if (input.trim().length < 3) {
                suggestions.addView(label("Type at least 3 characters to search.", 13f, muted, false))
                return
            }
            if (result.suggestions.isEmpty()) {
                suggestions.addView(label("No safe match. Choose manually or ask the manager to add an alias.", 13f, muted, false))
                return
            }
            if (result.ambiguous) {
                suggestions.addView(label("Which one did you mean?", 14f, navy, true))
                suggestions.addView(space(6))
            }
            result.suggestions.forEach { suggestion ->
                val enabled = expenseVisibility.typeVisible(suggestion.category, suggestion.type)
                val title = suggestion.contextLabel ?: suggestion.type.name
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = outlinedCard(if (enabled) Color.WHITE else 0xFFF4F4F4.toInt(), 9f)
                    addView(label(title, 14f, if (enabled) navy else muted, true))
                    addView(label("${suggestion.category.name} → ${suggestion.type.name}", 11f, muted, false))
                    if (!enabled) addView(label("Hidden for this hotel · manager must enable", 11f, red, true))
                    if (enabled && suggestion.type.requiresAccountantReview) {
                        addView(label("Accountant review required", 11f, red, true))
                    }
                    setOnClickListener {
                        if (enabled) accept(suggestion.category, suggestion.type)
                        else Toast.makeText(this@FinanceDevSafetyActivity,
                            "Ask the manager to enable this expense type", Toast.LENGTH_SHORT).show()
                    }
                }
                suggestions.addView(row)
                suggestions.addView(space(6))
            }
        }
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderSuggestions()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        dialog = AlertDialog.Builder(this)
            .setTitle("Smart Expense Search · DEV")
            .setView(ScrollView(this).apply { addView(container) })
            .setNegativeButton("Close", null)
            .create()
        settings.setOnClickListener { showExpenseVisibilitySettings { renderSuggestions() } }
        dialog.show()
        renderSuggestions()
    }

    private fun showManualExpenseTypePicker(
        catalog: SmartExpenseCatalog,
        onSelect: (SmartExpenseCategory, SmartExpenseType) -> Unit
    ) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(8))
        }
        val categories = catalog.categories
        val categorySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@FinanceDevSafetyActivity,
                android.R.layout.simple_spinner_dropdown_item, categories.map { it.name })
        }
        addFormField(form, "Category", categorySpinner)
        val typeSpinner = Spinner(this)
        addFormField(form, "Expense type", typeSpinner)
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                typeSpinner.adapter = ArrayAdapter(this@FinanceDevSafetyActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    categories[position].types.map { it.name })
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Manual classification")
            .setView(form)
            .setPositiveButton("Select", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val category = categories[categorySpinner.selectedItemPosition]
                val type = category.types.getOrNull(typeSpinner.selectedItemPosition)
                if (type == null || !expenseVisibility.typeVisible(category, type)) {
                    Toast.makeText(this, "This type is hidden. Enable it in settings first.", Toast.LENGTH_LONG).show()
                } else {
                    onSelect(category, type)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    /** Finance DEV only: visibility preferences, not a user/role-authorized settings page. */
    private fun showExpenseVisibilitySettings(onClose: (() -> Unit)? = null) {
        val catalog = try { expenseCatalog } catch (error: Exception) {
            Toast.makeText(this, "Unable to load expense master", Toast.LENGTH_LONG).show()
            return
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(12))
        }
        container.addView(label("DEV preview: role permissions not implemented yet.", 12f, red, true))
        container.addView(space(8))
        container.addView(label("Choose a category, then tick the expense types to show.", 13f, muted, false))
        container.addView(space(8))
        val categorySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@FinanceDevSafetyActivity,
                android.R.layout.simple_spinner_dropdown_item, catalog.categories.map { it.name })
        }
        container.addView(categorySpinner)
        val checkboxes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(checkboxes)
        fun renderCategory(index: Int) {
            checkboxes.removeAllViews()
            val category = catalog.categories[index]
            val categoryCheck = CheckBox(this).apply {
                text = "Show ${category.name}"
                isChecked = expenseVisibility.categoryVisible(category)
                setOnCheckedChangeListener { _, checked ->
                    expenseVisibility.setCategoryVisible(category.code, checked)
                    renderCategory(index)
                }
            }
            checkboxes.addView(categoryCheck)
            checkboxes.addView(space(6))
            category.types.forEach { type ->
                val typeCheck = CheckBox(this).apply {
                    text = type.name
                    isChecked = expenseVisibility.typeVisible(category, type)
                    isEnabled = expenseVisibility.categoryVisible(category)
                    setOnCheckedChangeListener { _, checked ->
                        expenseVisibility.setTypeVisible(type.code, checked)
                    }
                }
                checkboxes.addView(typeCheck)
            }
        }
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                renderCategory(position)
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Expense visibility · DEV")
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton("Done", null)
            .create()
        if (onClose != null) dialog.setOnDismissListener { onClose() }
        dialog.show()
    }

    /** Preview only: append-only proposed changes, never activated without verified CA approval. */
    private fun showAccountingRules() {
        val catalog = expenseCatalog
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(10))
        }
        form.addView(label("FINANCE DEV · No authenticated CA role yet", 12f, red, true))
        form.addView(space(6))
        form.addView(label("Proposals are stored locally with an effective date and history. " +
            "They DO NOT change any accounting classification or tax calculation.", 13f, muted, false))
        form.addView(space(10))
        val categorySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@FinanceDevSafetyActivity,
                android.R.layout.simple_spinner_dropdown_item, catalog.categories.map { it.name })
        }
        addFormField(form, "Category", categorySpinner)
        val typeSpinner = Spinner(this)
        addFormField(form, "Expense type", typeSpinner)
        val kindSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@FinanceDevSafetyActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("CA ledger mapping", "GST/TDS policy note"))
        }
        addFormField(form, "Change kind", kindSpinner)
        val currentHint = label("", 12f, muted, false)
        form.addView(currentHint)
        form.addView(space(10))
        fun chosenType(): SmartExpenseType? = catalog.categories
            .getOrNull(categorySpinner.selectedItemPosition)?.types
            ?.getOrNull(typeSpinner.selectedItemPosition)
        fun updateHint() {
            currentHint.text = "Master mapping hint: ${chosenType()?.accountingHint ?: "—"} " +
                "(unverified seed, unchanged by proposals)"
        }
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                typeSpinner.adapter = ArrayAdapter(this@FinanceDevSafetyActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    catalog.categories[position].types.map { it.name })
                updateHint()
            }
        }
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateHint()
        }
        val proposed = EditText(this).apply { hint = "e.g. Communication Charges"; setSingleLine(true) }
        addFormField(form, "Proposed ledger / policy note *", proposed)
        val group = EditText(this).apply { hint = "e.g. Other Expenses"; setSingleLine(true) }
        addFormField(form, "Proposed statutory group / tax domain *", group)
        val effective = EditText(this).apply {
            isFocusable = false; isClickable = true
            setText(LocalDate.now().toString())
            setOnClickListener { showDatePicker(this) }
        }
        addFormField(form, "Effective from *", effective)
        val source = EditText(this).apply {
            hint = "Notification number / CA working paper / reference"
            setSingleLine(true)
        }
        addFormField(form, "Source reference *", source)
        val reason = EditText(this).apply {
            hint = "Why should this mapping change?"
            minLines = 2; maxLines = 4
        }
        addFormField(form, "Reason *", reason)
        form.addView(Button(this).apply {
            text = "View pending proposal history"
            isAllCaps = false
            setOnClickListener { showAccountingRuleHistory() }
        })
        form.addView(space(8))
        form.addView(label("Tax rates are NOT bundled or auto-updated. No government rule is " +
            "assumed from this preview.", 12f, red, true))
        val dialog = AlertDialog.Builder(this)
            .setTitle("Accounting Standards & Tax Rules")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Save pending proposal", null)
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val type = requireNotNull(chosenType()) { "Choose an expense type" }
                    val id = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    val proposal = FinanceDemoAccountingRuleProposal(
                        proposalId = id,
                        typeCode = type.code,
                        ruleKind = if (kindSpinner.selectedItemPosition == 0)
                            FinanceDemoAccountingRulePolicy.LEDGER else FinanceDemoAccountingRulePolicy.TAX,
                        revisionNumber = 0L, // DAO assigns a unique per-type version atomically.
                        previousMappingHint = type.accountingHint,
                        proposedValue = proposed.text.toString().trim(),
                        proposedGroup = group.text.toString().trim(),
                        effectiveFrom = effective.text.toString(),
                        sourceReference = source.text.toString().trim(),
                        reason = reason.text.toString().trim(),
                        status = FinanceDemoAccountingRulePolicy.PENDING,
                        submittedAtMillis = now
                    )
                    FinanceDemoAccountingRulePolicy.validate(proposal)
                    val event = FinanceDemoAccountingRuleEvent(
                        eventId = "submitted-$id", proposalId = id,
                        eventType = "SUBMITTED", occurredAtMillis = now,
                        description = "Pending local CA review; never activated"
                    )
                    lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                localDatabase.accountingRules().submitProposal(proposal, event)
                            }
                            Toast.makeText(this@FinanceDevSafetyActivity,
                                "Proposal saved locally; not approved or applied", Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                        } catch (error: Exception) {
                            Toast.makeText(this@FinanceDevSafetyActivity,
                                "Could not save proposal; no rule changed", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (error: IllegalArgumentException) {
                    Toast.makeText(this, error.message ?: "Check proposal details", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private fun showAccountingRuleHistory() {
        lifecycleScope.launch {
            try {
                val proposals = withContext(Dispatchers.IO) {
                    localDatabase.accountingRules().recentProposals()
                }
                val content = LinearLayout(this@FinanceDevSafetyActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(18), dp(8), dp(18), dp(10))
                }
                content.addView(label("Pending proposals only. No approved rules exist in Finance DEV.",
                    12f, red, true))
                content.addView(space(10))
                if (proposals.isEmpty()) content.addView(label("No proposals submitted yet.", 13f, muted, false))
                proposals.forEach { proposal ->
                    content.addView(label("${proposal.typeCode} · ${proposal.ruleKind} · v${proposal.revisionNumber}",
                        14f, navy, true))
                    content.addView(label("${proposal.status} · From ${proposal.effectiveFrom}",
                        12f, forest, true))
                    content.addView(label("Proposed: ${proposal.proposedValue} → ${proposal.proposedGroup}",
                        12f, ink, false))
                    content.addView(label("Source: ${proposal.sourceReference}", 11f, muted, false))
                    content.addView(label("Reason: ${proposal.reason}", 11f, muted, false))
                    content.addView(space(12))
                }
                AlertDialog.Builder(this@FinanceDevSafetyActivity)
                    .setTitle("Local rule proposal history")
                    .setView(ScrollView(this@FinanceDevSafetyActivity).apply { addView(content) })
                    .setPositiveButton("Close", null).show()
            } catch (error: Exception) {
                Toast.makeText(this@FinanceDevSafetyActivity,
                    "Could not load rule history", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** V14: straightforward CA category/type form; old smart search is no longer part of entry. */
    private fun showExpenseForm(
        existing: FinanceDemoDraftBill?,
        initialSelection: Pair<SmartExpenseCategory, SmartExpenseType>? = null
    ) {
        FinanceSimpleExpenseForm(
            activity = this,
            catalog = expenseCatalog,
            visibility = expenseVisibility,
            stockSettings = FinanceGlobalCatalogSettings(this),
            vendors = sharedVendorOptions()
        ).show(existing, initialSelection) { input, editReason ->
            val now = System.currentTimeMillis()
            if (existing == null) {
                val row = FinanceDemoDraftPolicy.create(input, UUID.randomUUID().toString(), now)
                persistCreate(row)
            } else {
                val row = FinanceDemoDraftPolicy.edit(existing, input, editReason, now)
                persistChange(row, FinanceDemoDraftPolicy.editedEvent(row, editReason))
            }
        }
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
        // A label is also used inside horizontal LinearLayouts. MATCH_PARENT
        // width there can create enormous measurements / blank scrolling space.
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
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


