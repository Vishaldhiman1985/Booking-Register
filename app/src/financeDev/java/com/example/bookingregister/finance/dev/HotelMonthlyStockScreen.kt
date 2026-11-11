package com.example.bookingregister.finance.dev

import android.app.Activity
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import java.time.YearMonth
import java.util.Locale

/** Finance DEV: catalog-based physical monthly count. Existing stock store is unchanged. */
class HotelMonthlyStockScreen(private val activity: Activity) {
    private val store = HotelMonthlyStockStore(activity)
    private val dark = Color.rgb(31, 42, 59)
    private val muted = Color.rgb(105, 116, 130)
    private val border = Color.rgb(230, 234, 239)
    private val emerald = Color.rgb(18, 124, 84)
    private fun dp(v: Int) = (activity.resources.displayMetrics.density * v + 0.5f).toInt()
    private fun linear(vertical: Boolean = true) = LinearLayout(activity).apply {
        orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
    }
    private fun label(s: String, size: Float = 14f, bold: Boolean = false, color: Int = dark) = TextView(activity).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun padding(v: View, x: Int = 12, y: Int = 9) { v.setPadding(dp(x), dp(y), dp(x), dp(y)) }
    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    private fun qty(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else
        String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
    private fun stepFor(unit: String): Double = when (unit.lowercase(Locale.ROOT)) {
        "kg", "kilogram", "litre", "liter", "l" -> 0.1
        "g", "gram", "ml", "millilitre" -> 50.0
        else -> 1.0
    }

    /** Full-screen inventory workspace; never embed the dashboard in a small alert. */
    fun show() {
        var selected = YearMonth.now().minusMonths(1)
        val surface = Color.rgb(248, 250, 252)
        val root = linear().apply {
            setBackgroundColor(surface)
            setPadding(dp(16), dp(12), dp(16), dp(18))
        }
        val dialog = android.app.Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val heading = linear(false).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(label("‹", 30f, true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Back to Purchase"
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(42), dp(52)))
        val title = linear().apply {
            addView(label("Kitchen Inventory", 23f, true))
            addView(label("Monthly stock management", 12f, false, muted))
        }
        heading.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(heading)

        val monthButton = Button(activity).apply {
            isAllCaps = false
            textSize = 16f
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }
        root.addView(monthButton, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(14) })

        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val body = linear().apply { setPadding(dp(2), dp(14), dp(2), dp(22)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        fun tile(title: String, value: String, detail: String): View = linear().apply {
            setPadding(dp(15), dp(14), dp(12), dp(14))
            setBackgroundColor(Color.WHITE)
            addView(label(title, 12f, false, muted))
            addView(label(value, 20f, true).apply { setPadding(0, dp(2), 0, dp(2)) })
            addView(label(detail, 11f, false, muted))
        }
        fun action(title: String, subtitle: String, primary: Boolean = false, onClick: () -> Unit): View =
            linear(false).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(14), dp(12))
                setBackgroundColor(if (primary) emerald else Color.WHITE)
                val descriptions = linear().apply {
                    addView(label(title, 16f, true, if (primary) Color.WHITE else dark))
                    addView(label(subtitle, 12f, false, if (primary) Color.WHITE else muted))
                }
                addView(descriptions, LinearLayout.LayoutParams(0, -2, 1f))
                addView(label("›", 27f, true, if (primary) Color.WHITE else emerald))
                minimumHeight = dp(68)
                setOnClickListener { onClick() }
            }
        fun section(value: String) {
            body.addView(label(value, 13f, true, muted), LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(20); bottomMargin = dp(9)
            })
        }
        fun render() {
            body.removeAllViews()
            val month = selected.toString()
            monthButton.text = selected.month.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() } +
                " ${selected.year}  ▾"
            val closed = store.all().firstOrNull { it.month == month }
            val opening = closed?.opening ?: store.opening(month)
            val receipts = closed?.purchased ?: store.purchased(month).first
            val corrections = store.corrections(month)
            body.addView(label(if (closed != null) "MONTH APPROVED" else if (opening != null) "MONTH IN PROGRESS" else "NOT STARTED",
                12f, true, if (closed != null) emerald else muted))
            val overview = linear(false)
            overview.addView(tile("Opening", "${opening?.size ?: 0} items", "Saved opening balance"),
                LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(7) })
            overview.addView(tile("Received", "${receipts.size} items", "Purchase deliveries"),
                LinearLayout.LayoutParams(0, -2, 1f))
            body.addView(overview, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            body.addView(tile("Month-end", if (closed == null) "Pending" else "Approved",
                if (closed == null) "Closing stock not yet recorded" else "${closed.closing.size} closing items · ${closed.approvedBy}"),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            section("STOCK MOVEMENT")
            body.addView(action("View stock ledger", "Opening · received · closing · consumed", true) {
                HotelMonthlyStockLedgerScreen(activity).show(month)
            })
            section("STOCK COUNT & CORRECTIONS")
            body.addView(action("Opening stock", if (store.initialOpening(month) != null)
                "View or correct saved opening · reason required" else if (opening != null)
                "Inherited from previous month's closing" else "Enter first opening quantities") {
                dialog.dismiss()
                start(month, true)
            })
            body.addView(action("Month-end count", if (closed == null)
                "Record remaining quantities · blanks become zero" else "View approved closing or correct with reason") {
                dialog.dismiss()
                start(month, false)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            section("ACTIVITY")
            body.addView(label(if (corrections.isEmpty()) "No stock corrections recorded for this month"
                else "${corrections.size} correction(s) recorded · view reasons in Stock Ledger", 13f, false, muted))
            body.addView(label("Catalog items and categories are managed in Purchase, not duplicated here.",
                12f, false, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        }
        monthButton.setOnClickListener {
            DatePickerDialog(activity, { _, year, month, _ ->
                selected = YearMonth.of(year, month + 1)
                render()
            }, selected.year, selected.monthValue - 1, 1).show()
        }
        render()
        dialog.setContentView(root)
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun start(month: String, initial: Boolean) {
        try {
            val period = HotelMonthlyStockPolicy.validMonth(month)
            if (initial) {
                if (store.initialOpening(month) != null) {
                    showOpeningSummary(month)
                    return
                }
                require(store.opening(month) == null) { "Opening is inherited from the preceding month. Edit that month's closing to correct it." }
            } else {
                require(period < YearMonth.now()) { "Select a completed month for closing." }
                if (store.all().any { it.month == month }) { showSummary(month); return }
                require(store.opening(month) != null) { "First set opening stock for your initial month." }
            }
            showEditor(month, initial)
        } catch (e: Exception) { toast(e.message ?: "Unable to open stock") }
    }

    private fun showSummary(month: String) {
        val record = store.all().firstOrNull { it.month == month } ?: return
        showSavedCount(month, "Approved Closing Stock", record.closing, "Approved by ${record.approvedBy}",
            canEdit = store.all().none { it.month > month } && !store.receiptsChangedAfterClose(month),
            opening = false)
    }

    private fun showOpeningSummary(month: String) {
        val lines = store.initialOpening(month).orEmpty()
        showSavedCount(month, "Saved Opening Stock", lines, "${lines.size} recorded items",
            canEdit = store.all().none { it.month >= month }, opening = true)
    }

    /** Show every saved quantity, not a truncated message or a preview of eight items. */
    private fun showSavedCount(
        month: String, title: String, lines: List<HotelStockQuantity>, subheading: String,
        canEdit: Boolean, opening: Boolean
    ) {
        val dialog = android.app.Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val root = linear().apply { setPadding(dp(16), dp(12), dp(16), dp(24)); setBackgroundColor(Color.WHITE) }
        val top = linear(false).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(label("‹", 30f, true).apply {
            gravity = Gravity.CENTER; setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(40), dp(48)))
        val titles = linear().apply {
            addView(label(title, 21f, true))
            addView(label("$month · $subheading", 12f, false, muted))
        }
        top.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top)
        val scroll = ScrollView(activity)
        val content = linear()
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val catalog = HotelPurchaseCustomItemStore(activity).catalogItems(true).associateBy { it.id }
        val categoryNames = HotelPurchaseCategoryStore(activity).allIncludingArchived()
            .associate { it.first.id to it.first.name }
        val groups = lines.groupBy { item ->
            catalog[item.itemId]?.categoryId?.let { categoryNames[it] } ?: "Historical / other items"
        }
        if (lines.isEmpty()) content.addView(label("No saved quantities", 14f, false, muted))
        groups.toSortedMap().forEach { (category, items) ->
            content.addView(label("$category · ${items.size}", 14f, true).apply {
                setPadding(dp(5), dp(20), dp(5), dp(10))
            })
            items.sortedBy { it.name }.forEach { item ->
                val row = linear(false).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(11), dp(8), dp(11))
                    setBackgroundColor(Color.rgb(248, 250, 252))
                }
                row.addView(label(item.name, 14f, true), LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(label("${qty(item.quantity)} ${item.unit}", 14f, true, emerald))
                content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(3) })
            }
        }
        val foot = linear(false).apply {
            setPadding(0, dp(14), 0, dp(12)); gravity = Gravity.CENTER_VERTICAL
        }
        val ledger = Button(activity).apply {
            text = "View Ledger"; isAllCaps = false
            setOnClickListener { dialog.dismiss(); HotelMonthlyStockLedgerScreen(activity).show(month) }
        }
        foot.addView(ledger, LinearLayout.LayoutParams(0, dp(52), 1f))
        val edit = Button(activity).apply {
            text = if (canEdit) "Edit · reason required" else "Locked"
            isAllCaps = false
            isEnabled = canEdit
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(emerald)
            setOnClickListener {
                dialog.dismiss()
                showEditor(month, initial = opening, correction = true)
            }
        }
        foot.addView(edit, LinearLayout.LayoutParams(0, dp(52), 1.3f).apply { leftMargin = dp(8) })
        root.addView(foot)
        dialog.setContentView(root)
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun showEditor(month: String, initial: Boolean, correction: Boolean = false) {
        val opening = if (initial) emptyList() else store.opening(month).orEmpty()
        val purchased = if (initial) emptyList() else store.purchased(month).first
        val required = (opening + purchased).associateBy { it.itemId }
        val counts = mutableMapOf<String, String>()
        if (correction) {
            val saved = if (initial) store.initialOpening(month).orEmpty()
                else store.all().firstOrNull { it.month == month }?.closing.orEmpty()
            saved.forEach { counts[it.itemId] = qty(it.quantity) }
        }

        // Same live, customizable catalog used by Create Purchase Order.
        val catalogStore = HotelPurchaseCustomItemStore(activity)
        val activeCatalog = catalogStore.catalogItems()
        val activeIds = activeCatalog.map { it.id }.toSet()
        val archivedRequired = catalogStore.catalogItems(true).filter { it.id in required.keys && it.id !in activeIds }
        val purchaseItems = activeCatalog + archivedRequired
        val byId = purchaseItems.associateBy { it.id }
        val allStock = linkedMapOf<String, HotelStockQuantity>()
        required.values.forEach { allStock[it.itemId] = it }
        purchaseItems.forEach { allStock.putIfAbsent(it.id, HotelStockQuantity(it.id, it.name, it.unit, 0.0)) }

        val categoryList = HotelPurchaseCategoryStore(activity).allIncludingArchived().map { it.first }
        val categoryIds = purchaseItems.map { it.categoryId }.toSet()
        val selectableCategories = categoryList.filter { it.id in categoryIds }
        var selectedCategoryId = selectableCategories.firstOrNull()?.id
        var searchQuery = ""
        var pageSize = 24
        // Show the actual stock ledger items first; the full shared Purchase catalog is optional.
        var showAllCatalog = initial
        val root = linear().apply {
            setPadding(dp(12), dp(14), dp(12), dp(34))
            setBackgroundColor(Color.WHITE)
        }
        lateinit var dialog: android.app.Dialog

        // Same catalog navigation layout as Create Order: search above, 32%/68% dual rails.
        val header = linear(false).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("‹", 30f, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { dialog.dismiss() }
            contentDescription = "Close stock entry"
        }, LinearLayout.LayoutParams(dp(44), dp(52)))
        val heading = linear().apply {
            addView(label(if (correction) "Correct Stock" else if (initial) "Opening Stock" else "Month-End Count", 19f, true))
            addView(label(month + " · " + (if (correction) "Correction with audit" else "Kitchen inventory"), 11f, false, muted))
        }
        header.addView(heading, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(header)
        val search = EditText(activity).apply {
            hint = "Search items"
            textSize = 14f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(Color.rgb(247, 247, 251))
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
        val filter = CheckBox(activity).apply {
            text = "Show entire Purchase catalog"
            textSize = 13f
            setTextColor(muted)
            isChecked = showAllCatalog
        }
        root.addView(filter, LinearLayout.LayoutParams(-1, dp(48)))

        val rails = linear(false)
        val left = linear()
        val right = linear()
        val categoriesScroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(left)
        }
        val itemsScroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(right)
        }
        rails.addView(categoriesScroll, LinearLayout.LayoutParams(0, -1, 0.35f))
        rails.addView(itemsScroll, LinearLayout.LayoutParams(0, -1, 0.65f))
        root.addView(rails, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = linear().apply {
            setBackgroundColor(Color.WHITE)
            setPadding(dp(2), dp(10), dp(2), dp(30))
        }
        val progress = label("", 12f, true, emerald)
        footer.addView(progress)
        val bottom = linear(false).apply { gravity = Gravity.CENTER_VERTICAL }
        val showMore = Button(activity).apply { text = "More Items"; isAllCaps = false; visibility = View.GONE; minHeight = dp(52) }
        bottom.addView(showMore, LinearLayout.LayoutParams(0, dp(52), 0.85f))
        val review = Button(activity).apply {
            text = if (correction) "Review Correction  →" else if (initial) "Save Opening  →" else "Review & Close  →"
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(0, 115, 46))
        }
        bottom.addView(review, LinearLayout.LayoutParams(0, dp(52), 1.6f))
        footer.addView(bottom)
        root.addView(footer)

        val visibleFields = mutableMapOf<String, EditText>()
        fun saveVisible() {
            visibleFields.forEach { (id, field) -> counts[id] = field.text.toString() }
            visibleFields.clear()
        }
        fun updateProgress() {
            val done = required.keys.count { counts[it]?.isNotBlank() == true }
            progress.text = if (initial) "${counts.count { it.value.isNotBlank() }} items entered" else
                "$done of ${required.size} stocked items counted · remaining will be zero"
        }
        fun itemButton(title: String, selected: Boolean, action: () -> Unit): Button = Button(activity).apply {
            text = title
            textSize = 11f
            isAllCaps = false
            setTextColor(if (selected) Color.rgb(0, 115, 46) else dark)
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (selected) Color.rgb(227, 247, 238) else Color.rgb(247, 247, 251))
            setPadding(dp(3), 0, dp(3), 0)
            maxLines = 4
            ellipsize = android.text.TextUtils.TruncateAt.END
            minHeight = dp(64)
            setOnClickListener { action() }
        }
        fun rebuild() {
            saveVisible()
            left.removeAllViews()
            right.removeAllViews()
            val navigable = if (showAllCatalog) selectableCategories
                else selectableCategories.filter { cat -> required.values.any { byId[it.itemId]?.categoryId == cat.id } }
            if (selectedCategoryId !in navigable.map { it.id }) selectedCategoryId = navigable.firstOrNull()?.id
            navigable.forEach { category ->
                val selected = category.id == selectedCategoryId
                left.addView(itemButton(category.name, selected) {
                    if (selectedCategoryId != category.id) {
                        selectedCategoryId = category.id
                        pageSize = 24
                        rebuild()
                    }
                }, LinearLayout.LayoutParams(-1, dp(68)))
                left.addView(View(activity), LinearLayout.LayoutParams(-1, dp(3)))
            }
            val q = searchQuery.trim()
            val visibleCatalog = if (showAllCatalog) purchaseItems else purchaseItems.filter { it.id in required.keys }
            // Preserve Create Order's category-left and item-right navigation.
            val matching = visibleCatalog.filter { item ->
                (q.isNotBlank() && (item.name.contains(q, true) || item.synonyms.any { it.contains(q, true) })) ||
                    (q.isBlank() && item.categoryId == selectedCategoryId)
            }
            // Retain previously counted/received legacy item IDs, even if removed from catalog.
            val legacy = required.values.filter { it.itemId !in byId &&
                (q.isNotBlank() && it.name.contains(q, true)) }
            val visible = (matching.map { allStock.getValue(it.id) } + legacy).take(pageSize)
            showMore.visibility = if (matching.size + legacy.size > pageSize) View.VISIBLE else View.GONE
            if (visible.isEmpty()) {
                right.addView(label("No matching items", 13f, false, muted))
            }
            for (item in visible) {
                // Two-line rows: names no longer collide with the quantity selector on small phones.
                val row = linear().apply { setPadding(dp(7), dp(10), dp(6), dp(10)) }
                val itemTitle = linear(false).apply { gravity = Gravity.CENTER_VERTICAL }
                itemTitle.addView(label(item.name, 14f, true), LinearLayout.LayoutParams(0, -2, 1f))
                itemTitle.addView(label(item.unit, 11f, false, muted))
                row.addView(itemTitle, LinearLayout.LayoutParams(-1, dp(30)))
                val controls = linear(false).apply { gravity = Gravity.CENTER_VERTICAL }
                val field = EditText(activity).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    gravity = Gravity.CENTER
                    textSize = 17f
                    setSingleLine(true)
                    setSelectAllOnFocus(true)
                    minHeight = dp(48)
                    hint = "0"
                    setText(counts[item.itemId].orEmpty())
                    setOnClickListener { selectAll() }
                }
                visibleFields[item.itemId] = field
                field.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        counts[item.itemId] = s?.toString().orEmpty()
                        updateProgress()
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
                val minus = itemButton("−", false) {
                    val n = field.text.toString().toDoubleOrNull() ?: 0.0
                    field.setText(qty((n - stepFor(item.unit)).coerceAtLeast(0.0)))
                }
                val plus = itemButton("+", true) {
                    val n = field.text.toString().toDoubleOrNull() ?: 0.0
                    field.setText(qty(n + stepFor(item.unit)))
                }
                controls.addView(minus, LinearLayout.LayoutParams(dp(48), dp(48)))
                controls.addView(field, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    leftMargin = dp(5); rightMargin = dp(5)
                })
                controls.addView(plus, LinearLayout.LayoutParams(dp(48), dp(48)))
                row.addView(controls, LinearLayout.LayoutParams(-1, dp(52)))
                right.addView(row)
                right.addView(View(activity).apply { setBackgroundColor(border) }, LinearLayout.LayoutParams(-1, dp(1)))
            }
            updateProgress()
        }
        showMore.setOnClickListener { pageSize += 24; rebuild() }
        filter.setOnCheckedChangeListener { _, enabled ->
            saveVisible()
            showAllCatalog = enabled
            pageSize = 24
            rebuild()
        }
        val refreshSearch = Runnable {
            pageSize = 24
            rebuild()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                search.removeCallbacks(refreshSearch)
                search.postDelayed(refreshSearch, 200)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialog = android.app.Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen).apply {
            setContentView(root)
        }
        review.setOnClickListener {
            try {
                saveVisible()
                // Explicit opening counts may be empty for a zero-stock start.
                // Closing counts: every blank item is an intentional zero on approval.
                val entered = counts.filterValues { it.isNotBlank() }.map { (id, raw) ->
                    val value = raw.toDoubleOrNull()
                    require(value != null && value.isFinite() && value >= 0) { "Invalid quantity: ${allStock[id]?.name}" }
                    allStock.getValue(id).copy(quantity = value)
                }
                val result = if (initial) entered else {
                    val selected = entered.associateBy { it.itemId }
                    val complete = linkedMapOf<String, HotelStockQuantity>()
                    required.values.forEach { requiredItem ->
                        complete[requiredItem.itemId] = selected[requiredItem.itemId] ?: requiredItem.copy(quantity = 0.0)
                    }
                    entered.forEach { complete[it.itemId] = it }
                    complete.values.toList()
                }
                if (initial && !correction) {
                    store.setInitialOpening(month, result)
                    dialog.dismiss()
                    toast("Opening stock saved. View or edit it from Opening Stock / Stock Ledger.")
                } else {
                    val details = linear().apply { padding(this, 16, 8) }
                    val zeroCount = result.count { it.quantity == 0.0 }
                    val unentered = if (initial) 0 else required.keys.count { counts[it].isNullOrBlank() }
                    details.addView(label("${result.size} items · $zeroCount zero quantities ($unentered left blank).", 14f, true))
                    details.addView(label("Blank quantities will be saved as 0. Review carefully before approving.", 12f, false, muted))
                    val confirmZero = CheckBox(activity).apply {
                        text = "I confirm the zero quantities and have reviewed this month."
                        textSize = 13f
                        isChecked = unentered == 0
                    }
                    if (!correction && !initial && unentered > 0) details.addView(confirmZero)
                    val manager = EditText(activity).apply { hint = "Approving manager"; setSingleLine(true) }
                    if (!initial) details.addView(manager)
                    val reason = EditText(activity).apply {
                        hint = "Mandatory correction reason"; setSingleLine(false); minLines = 2
                    }
                    if (correction) details.addView(reason)
                    AlertDialog.Builder(activity)
                        .setTitle(if (correction) "Confirm stock correction" else "Review & approve closing")
                        .setView(details)
                        .setNegativeButton("Back", null)
                        .setPositiveButton(if (correction) "Save Correction" else "Approve", null)
                        .create().apply {
                            setOnShowListener {
                                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                    try {
                                        if (correction) {
                                            require(reason.text.toString().trim().length >= 5) { "Enter a correction reason" }
                                            if (initial) store.amendOpening(month, result, reason.text.toString())
                                            else store.amendClosing(month, result, reason.text.toString(), manager.text.toString())
                                        } else {
                                            require(manager.text.toString().isNotBlank()) { "Enter manager name" }
                                            require(unentered == 0 || confirmZero.isChecked) {
                                                "Confirm that the blank items represent zero stock"
                                            }
                                            HotelMonthlyStockPolicy.balance(opening, purchased, result)
                                            store.approve(month, result, manager.text.toString())
                                        }
                                        dismiss()
                                        dialog.dismiss()
                                        toast(if (correction) "Stock correction saved and audited" else "Monthly closing approved")
                                        HotelMonthlyStockLedgerScreen(activity).show(month)
                                    } catch (e: Exception) { toast(e.message ?: "Please review quantities") }
                                }
                            }
                            show()
                        }
                }
            } catch (e: Exception) { toast(e.message ?: "Check quantities") }
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        rebuild()
    }
}
