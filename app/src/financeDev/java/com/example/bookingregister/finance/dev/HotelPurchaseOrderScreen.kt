package com.example.bookingregister.finance.dev

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import androidx.appcompat.app.AlertDialog
import java.util.UUID

/** Finance DEV purchasing: home -> compact item picker -> review. */
class HotelPurchaseOrderScreen(private val activity: Activity) {
    private val vendors = HotelPurchaseVendorStore(activity)
    private val orders = HotelPurchaseOrderStore(activity)
    private val receipts = HotelPurchaseGoodsReceiptStore(activity)
    private val workflows = HotelPurchaseReceivingWorkflowStore(activity)
    private val closures = HotelPurchaseSimpleClosureStore(activity)
    private val customItems = HotelPurchaseCustomItemStore(activity)
    private val categories = HotelPurchaseCategoryStore(activity)
    private var host: LinearLayout? = null
    private var mainToolbar: View? = null
    fun bindToolbar(toolbar: View) { mainToolbar = toolbar }
    private var page = "HOME"
    private var selectedVendorId: String? = null
    private val selectedQuantities = mutableMapOf<String, Double>()
    private val pendingFields = mutableMapOf<String, EditText>()
    private var selectedCategoryId: String? = null
    private var query = ""
    private var editingOrder: HotelPurchaseOrder? = null

    private fun dp(n: Int) = (activity.resources.displayMetrics.density * n + 0.5f).toInt()
    private val green = Color.rgb(0, 115, 46)
    private val dark = Color.rgb(31, 42, 59)
    private val pale = Color.rgb(227, 247, 238)
    private val light = Color.rgb(247, 247, 251)
    private val pageBackground = Color.WHITE
    private val neutralCard = Color.rgb(251, 251, 253)
    private val neutralStroke = Color.rgb(224, 226, 232)
    private val blue = Color.rgb(28, 102, 225)
    private val blueCard = Color.rgb(245, 249, 255)
    private val blueStroke = Color.rgb(201, 221, 255)
    private val orange = Color.rgb(245, 124, 0)
    private val orangeCard = Color.rgb(255, 249, 244)
    private val orangeStroke = Color.rgb(255, 224, 196)
    private val red = Color.rgb(214, 48, 49)
    private val redBadge = Color.rgb(255, 230, 226)
    private val greenCard = Color.rgb(244, 252, 246)
    private val greenStroke = Color.rgb(205, 236, 213)

    private fun text(label: String, size: Float = 15f, bold: Boolean = false): TextView = TextView(activity).apply {
        text = label
        textSize = size
        setTextColor(dark)
        if (bold) setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun button(label: String, onClick: () -> Unit): Button = Button(activity).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        minHeight = dp(48)
        setTextColor(dark)
        backgroundTintList = android.content.res.ColorStateList.valueOf(pale)
        setOnClickListener { onClick() }
    }
    private fun space(px: Int) = Space(activity).apply { minimumHeight = dp(px) }
    private fun line(parent: LinearLayout) {
        parent.addView(View(activity).apply { setBackgroundColor(Color.rgb(230, 233, 237)) }, LinearLayout.LayoutParams(-1, dp(1)))
    }
    private fun rounded(fill: Int, stroke: Int? = null, radiusDp: Int = 24): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun homeCard(
        title: String,
        subtitle: String,
        count: Int,
        cardColor: Int,
        borderColor: Int,
        accentColor: Int,
        badgeColor: Int,
        iconGlyph: String,
        onClick: () -> Unit
    ): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(cardColor, borderColor, 22)
        elevation = dp(2).toFloat()
        setPadding(dp(18), dp(18), dp(18), dp(18))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }

        val iconWrap = FrameLayout(activity).apply {
            val bubble = TextView(activity).apply {
                text = iconGlyph
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(accentColor)
                background = rounded(Color.TRANSPARENT, Color.TRANSPARENT, 16)
            }
            addView(bubble, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER))
        }
        addView(iconWrap, LinearLayout.LayoutParams(dp(52), dp(52)))

        addView(View(activity).apply { setBackgroundColor(borderColor) }, LinearLayout.LayoutParams(dp(1), dp(42)).apply { marginStart = dp(14); marginEnd = dp(16) })

        val textCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 18f, true).apply { setTextColor(Color.BLACK) })
            addView(text(subtitle, 12f, false).apply { setTextColor(Color.rgb(88, 96, 110)) })
        }
        addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val rightCol = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val badge = TextView(activity).apply {
            text = count.toString()
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(accentColor)
            background = rounded(badgeColor, null, 18)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            minWidth = dp(54)
        }
        rightCol.addView(badge)
        rightCol.addView(TextView(activity).apply {
            text = "›"
            textSize = 26f
            setTextColor(dark)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(14) })
        addView(rightCol)
    }

    private data class HomeBuckets(
        val drafts: List<HotelPurchaseOrder>,
        val awaitingDelivery: List<HotelPurchaseOrder>,
        val shortages: List<HotelPurchaseOrder>,
        val delivered: List<HotelPurchaseOrder>
    )

    private fun isDelivered(order: HotelPurchaseOrder): Boolean {
        if (closures.get(order.id).closed || workflows.get(order.id).archived || order.status == "ARCHIVED") return true
        val history = receipts.forOrder(order.id)
        if (history.isEmpty()) return false
        return HotelPurchaseGoodsReceiptPolicy.remaining(order, history).values.all { it <= 0.000001 } ||
            workflows.get(order.id).resolution == "CANCELLED_BALANCE"
    }

    private fun homeBuckets(): HomeBuckets {
        val drafts = orders.all().filter(::isDraft)
        val placed = orders.all().filter { it.status == "PLACED" && !isDelivered(it) }
        val shortages = placed.filter { order ->
            val history = receipts.forOrder(order.id)
            history.isNotEmpty() || closures.get(order.id).updates.isNotEmpty()
        }
        val awaitingDelivery = placed.filter { it !in shortages }
        val delivered = orders.all().filter(::isDelivered)
        return HomeBuckets(drafts, awaitingDelivery, shortages, delivered)
    }

    private fun sectionScreenHeader(root: LinearLayout, title: String, subtitle: String) {
        root.addView(button("‹ Back to Purchase") {
            page = "HOME"
            renderPage()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
        root.addView(space(6))
        root.addView(text(title, 22f, true).apply { setTextColor(Color.BLACK) })
        root.addView(text(subtitle, 13f).apply { setTextColor(Color.rgb(88, 96, 110)) })
        root.addView(space(12))
    }
    private fun masterItems(): List<HotelPurchaseItem> = customItems.catalogItems()
    private fun chosenVendor(): HotelPurchaseVendor? = vendors.all().firstOrNull { it.id == selectedVendorId }
    private fun availableItems(): List<HotelPurchaseItem> = chosenVendor()?.let {
        HotelPurchasePolicy.itemsForVendor(it, masterItems())
    } ?: emptyList()
    private fun allSelectedLines(): List<HotelPurchaseOrderLine> {
        saveVisibleInputs()
        val byId = masterItems().associateBy { it.id }
        return selectedQuantities.filterValues { it.isFinite() && it > 0 }.mapNotNull { (id, qty) ->
            byId[id]?.let { HotelPurchaseOrderLine(it.id, it.name, qty, it.unit) }
                ?: editingOrder?.lines?.firstOrNull { it.itemId == id }?.copy(quantity = qty)
        }
    }
    private fun saveVisibleInputs() {
        pendingFields.forEach { (id, field) ->
            val value = field.text.toString().trim().toDoubleOrNull()
            if (value != null && value.isFinite() && value >= 0) {
                if (value == 0.0) selectedQuantities.remove(id) else selectedQuantities[id] = value
            } else if (field.text.isBlank()) {
                selectedQuantities.remove(id)
            }
        }
        pendingFields.clear()
    }
    fun render(container: LinearLayout) {
        host = container
        renderPage()
    }
    private fun renderPage() {
        val root = host ?: return
        root.removeAllViews()
        root.orientation = LinearLayout.VERTICAL
        mainToolbar?.visibility = if (page == "CREATE") View.GONE else View.VISIBLE
        if (page == "CREATE") {
            // Keep the two independently scrolling catalog rails and a fixed review footer.
            drawCreate(root)
            return
        }
        // Every non-catalog purchase page scrolls vertically. Previously these pages
        // were rendered in a fixed, non-scrollable FrameLayout on the Finance tab.
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(5), dp(3), dp(24))
        }
        val scroller = ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            isFillViewport = true
            addView(content)
        }
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        when (page) {
            "REVIEW" -> drawReview(content)
            "ARCHIVE" -> drawArchive(content)
            "DRAFTS" -> drawDrafts(content)
            "PENDING_DELIVERY" -> drawPendingDelivery(content)
            "SHORT_DELIVERY" -> drawShortDelivery(content)
            "DELIVERED" -> drawDelivered(content)
            else -> drawHome(content)
        }
    }

    private fun isArchived(order: HotelPurchaseOrder): Boolean = isDelivered(order)

    private fun isPlaced(order: HotelPurchaseOrder): Boolean =
        order.status == "PLACED" && !isArchived(order)

    private fun isDraft(order: HotelPurchaseOrder): Boolean =
        !isArchived(order) && !isPlaced(order)

    private fun openPlacedOrder(order: HotelPurchaseOrder) {
        HotelPurchaseSimpleDeliveryScreen(activity).open(order) { renderPage() }
    }

    private fun sectionHeader(label: String, count: Int): TextView = text("$label  ($count)", 17f, true).apply {
        setPadding(dp(8), dp(12), dp(8), dp(10))
        setTextColor(dark)
    }

    private fun drawHome(root: LinearLayout) {
        root.setBackgroundColor(pageBackground)
        root.setPadding(dp(12), dp(10), dp(12), dp(30))
        val buckets = homeBuckets()

        root.addView(space(6))
        val createCard = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(241, 250, 244), green, 22)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            elevation = dp(2).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val available = vendors.all()
                if (available.isEmpty()) {
                    Toast.makeText(activity, "Add a vendor from the three-dot menu first", Toast.LENGTH_LONG).show()
                } else {
                    selectedQuantities.clear()
                    selectedVendorId = available.first().id
                    selectedCategoryId = null
                    editingOrder = null
                    query = ""
                    page = "CREATE"
                    renderPage()
                }
            }
            addView(TextView(activity).apply {
                text = "+"
                textSize = 34f
                setTypeface(null, Typeface.BOLD)
                setTextColor(green)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(38), dp(38)))
            addView(text("Create New Order", 20f, true).apply {
                setTextColor(Color.BLACK)
                setPadding(dp(10), 0, 0, 0)
            })
        }
        root.addView(createCard, LinearLayout.LayoutParams(-1, -2))

        root.addView(space(16))
        root.addView(homeCard(
            title = "Drafts",
            subtitle = "Incomplete orders",
            count = buckets.drafts.size,
            cardColor = neutralCard,
            borderColor = neutralStroke,
            accentColor = Color.rgb(88, 96, 110),
            badgeColor = Color.rgb(242, 244, 247),
            iconGlyph = "📝"
        ) {
            page = "DRAFTS"
            renderPage()
        }, LinearLayout.LayoutParams(-1, -2))

        root.addView(space(12))
        root.addView(homeCard(
            title = "Pending Delivery",
            subtitle = "Orders awaiting delivery",
            count = buckets.awaitingDelivery.size,
            cardColor = blueCard,
            borderColor = blueStroke,
            accentColor = blue,
            badgeColor = Color.rgb(232, 242, 255),
            iconGlyph = "🚚"
        ) {
            page = "PENDING_DELIVERY"
            renderPage()
        }, LinearLayout.LayoutParams(-1, -2))

        root.addView(space(12))
        root.addView(homeCard(
            title = "Short Delivery",
            subtitle = "Missing items to resolve",
            count = buckets.shortages.size,
            cardColor = orangeCard,
            borderColor = orangeStroke,
            accentColor = orange,
            badgeColor = redBadge,
            iconGlyph = "⚠"
        ) {
            page = "SHORT_DELIVERY"
            renderPage()
        }, LinearLayout.LayoutParams(-1, -2))

        root.addView(space(12))
        root.addView(homeCard(
            title = "Delivered",
            subtitle = "Completed orders & bill references",
            count = buckets.delivered.size,
            cardColor = greenCard,
            borderColor = greenStroke,
            accentColor = green,
            badgeColor = pale,
            iconGlyph = "📄"
        ) {
            page = "DELIVERED"
            renderPage()
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun drawPendingDelivery(root: LinearLayout) {
        val queue = homeBuckets().awaitingDelivery
        sectionScreenHeader(root, "Pending Delivery", "Orders awaiting delivery")
        if (queue.isEmpty()) {
            root.addView(text("No orders are awaiting delivery.", 14f).apply { setTextColor(Color.rgb(88, 96, 110)) })
            return
        }
        queue.forEach { order ->
            root.addView(button("${order.vendorName}  •  ${order.lines.size} items  ›") {
                openPlacedOrder(order)
            }, LinearLayout.LayoutParams(-1, dp(56)))
            root.addView(space(8))
        }
    }

    private fun drawShortDelivery(root: LinearLayout) {
        val queue = homeBuckets().shortages
        sectionScreenHeader(root, "Short Delivery", "Orders with missing items")
        if (queue.isEmpty()) {
            root.addView(text("No orders currently have missing items.", 14f).apply { setTextColor(Color.rgb(88, 96, 110)) })
            return
        }
        queue.forEach { order ->
            val balances = HotelPurchaseGoodsReceiptPolicy.remaining(order, receipts.forOrder(order.id))
            val shortLines = order.lines.count { (balances[it.itemId] ?: 0.0) > 0.000001 }
            root.addView(button("${order.vendorName}  •  $shortLines missing item(s)  ›") {
                HotelPurchaseSimpleDeliveryScreen(activity).open(order) { renderPage() }
            }, LinearLayout.LayoutParams(-1, dp(58)))
            root.addView(space(8))
        }
    }

    private fun drawDelivered(root: LinearLayout) {
        val queue = homeBuckets().delivered
        sectionScreenHeader(root, "Delivered", "Completed orders & bill references")
        if (queue.isEmpty()) {
            root.addView(text("No delivered orders yet.", 14f).apply { setTextColor(Color.rgb(88, 96, 110)) })
            return
        }
        queue.groupBy { it.vendorId }.forEach { (_, grouped) ->
            root.addView(text(grouped.first().vendorName, 17f, true))
            root.addView(space(6))
            grouped.forEach { order ->
                val recorded = receipts.forOrder(order.id)
                val countUnavailable = closures.get(order.id).unavailable.count { it.value > 0.000001 }
                root.addView(button("Order ${order.id.take(8)} · ${recorded.size} deliveries · $countUnavailable unavailable  ›") {
                    HotelPurchaseSimpleDeliveryScreen(activity).showHistory(order)
                }, LinearLayout.LayoutParams(-1, dp(60)))
                root.addView(space(7))
            }
            root.addView(space(12))
        }
    }

    private fun drawDrafts(root: LinearLayout) {
        root.addView(button("‹ Back to Pending") { page = "HOME"; renderPage() })
        root.addView(text("Draft Orders", 19f, true))
        root.addView(space(8))
        val drafts = orders.all().filter(::isDraft)
        if (drafts.isEmpty()) root.addView(text("No saved drafts."))
        drafts.forEach { order ->
            root.addView(button("${order.vendorName}   •   ${order.lines.size} items   ›") {
                editingOrder = order
                selectedVendorId = order.vendorId
                selectedQuantities.clear()
                order.lines.forEach { selectedQuantities[it.itemId] = it.quantity }
                page = "REVIEW"
                renderPage()
            }, LinearLayout.LayoutParams(-1, dp(53)))
        }
    }

    private fun drawCreate(root: LinearLayout) {
        val availableVendors = vendors.all()
        if (availableVendors.isEmpty()) { page = "HOME"; drawHome(root); return }
        if (selectedVendorId !in availableVendors.map { it.id }) selectedVendorId = availableVendors.first().id
        // Compact navigation: back, vendor dropdown and menu on one row.
        val selectorRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = TextView(activity).apply {
            text = "‹"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(dark)
            contentDescription = "Back to pending orders"
            setOnClickListener { saveVisibleInputs(); page = "HOME"; renderPage() }
        }
        selectorRow.addView(back, LinearLayout.LayoutParams(dp(38), dp(45)))
        val spinner = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, availableVendors.map { it.name })
            setSelection(availableVendors.indexOfFirst { it.id == selectedVendorId }.coerceAtLeast(0))
        }
        selectorRow.addView(spinner, LinearLayout.LayoutParams(0, dp(45), 1f))
        val menu = TextView(activity).apply {
            text = "⋮"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(dark)
            contentDescription = "Purchase management menu"
            setOnClickListener { showMenu(this) }
        }
        selectorRow.addView(menu, LinearLayout.LayoutParams(dp(38), dp(45)))
        root.addView(selectorRow)

        val search = EditText(activity).apply {
            hint = "Search items"
            setSingleLine(true)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(14), 0, dp(14), 0)
            setBackgroundColor(light)
            setText(query)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(43)))
        root.addView(space(7))

        // Both rails get their own scrolling; scrolling items does not move categories.
        val body = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val left = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val right = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val categoriesScroll = ScrollView(activity).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            addView(left)
        }
        val itemsScroll = ScrollView(activity).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            addView(right)
        }
        body.addView(categoriesScroll, LinearLayout.LayoutParams(0, -1, 0.32f))
        body.addView(itemsScroll, LinearLayout.LayoutParams(0, -1, 0.68f))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

        // Fixed footer: never scrolls away from the manager.
        val footer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(5), dp(3), dp(5))
            setBackgroundColor(Color.WHITE)
        }
        val count = text("0 items added", 14f, true).apply { setTextColor(green) }
        footer.addView(count, LinearLayout.LayoutParams(0, dp(49), 1f))
        val reviewButton = button("Review Order  →") {
            val valid = pendingFields.values.all { field ->
                field.text.isBlank() ||
                    (field.text.toString().toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true)
            }
            if (!valid) {
                Toast.makeText(activity, "Correct invalid quantities", Toast.LENGTH_SHORT).show()
                return@button
            }
            saveVisibleInputs()
            if (allSelectedLines().isEmpty()) {
                Toast.makeText(activity, "Add at least one item", Toast.LENGTH_SHORT).show()
            } else {
                page = "REVIEW"
                renderPage()
            }
        }.apply {
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(green)
        }
        footer.addView(reviewButton, LinearLayout.LayoutParams(0, dp(49), 1f))
        fun refreshCount() {
            count.text = "${selectedQuantities.count { it.value > 0 }} items added"
        }
        fun rebuild() {
            saveVisibleInputs()
            left.removeAllViews(); right.removeAllViews()
            val items = availableItems()
            val allowed = categories.all().filter { category -> items.any { it.categoryId == category.id } }
            if (selectedCategoryId !in allowed.map { it.id }) selectedCategoryId = allowed.firstOrNull()?.id
            allowed.forEach { category ->
                val selected = category.id == selectedCategoryId
                left.addView(button(category.name) {
                    if (selectedCategoryId != category.id) {
                        saveVisibleInputs()
                        selectedCategoryId = category.id
                        rebuild()
                    }
                }.apply {
                    textSize = 11f
                    isAllCaps = false
                    setTextColor(if (selected) green else dark)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(if (selected) pale else light)
                    setPadding(dp(3), 0, dp(3), 0)
                }, LinearLayout.LayoutParams(-1, dp(52)))
                left.addView(space(3))
            }
            val matching = items.filter { item ->
                (query.isBlank() || item.name.contains(query, true) || item.synonyms.any { it.contains(query, true) }) &&
                    (query.isNotBlank() || item.categoryId == selectedCategoryId)
            }
            if (matching.isEmpty()) right.addView(text("No matching items", 13f))
            matching.forEach { item ->
                val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(5), dp(3), 0, dp(3)) }
                val itemLabel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                itemLabel.addView(text(item.name, 13f, true))
                itemLabel.addView(text(item.unit, 11f))
                row.addView(itemLabel, LinearLayout.LayoutParams(0, dp(48), 1.1f))
                val minus = button("−") { }.apply {
                    backgroundTintList = android.content.res.ColorStateList.valueOf(light)
                }
                val qtyField = EditText(activity).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    gravity = Gravity.CENTER
                    textSize = 15f
                    setSingleLine(true)
                    setSelectAllOnFocus(true)
                    setText(formatQty(selectedQuantities[item.id] ?: 0.0))
                    setOnClickListener { selectAll() }
                }
                pendingFields[item.id] = qtyField
                qtyField.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val value = s.toString().toDoubleOrNull()
                        if (value == null || value <= 0 || !value.isFinite()) selectedQuantities.remove(item.id)
                        else selectedQuantities[item.id] = value
                        refreshCount()
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
                minus.setOnClickListener {
                    val next = ((qtyField.text.toString().toDoubleOrNull() ?: 0.0) - 1.0).coerceAtLeast(0.0)
                    qtyField.setText(formatQty(next))
                }
                val plus = button("+") {
                    val next = (qtyField.text.toString().toDoubleOrNull() ?: 0.0) + 1.0
                    qtyField.setText(formatQty(next))
                }.apply {
                    backgroundTintList = android.content.res.ColorStateList.valueOf(pale)
                }
                row.addView(minus, LinearLayout.LayoutParams(dp(35), dp(42)))
                row.addView(qtyField, LinearLayout.LayoutParams(dp(50), dp(42)))
                row.addView(plus, LinearLayout.LayoutParams(dp(35), dp(42)))
                right.addView(row)
                line(right)
            }
            refreshCount()
        }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newId = availableVendors[position].id
                if (selectedVendorId != newId) {
                    saveVisibleInputs()
                    selectedVendorId = newId
                    selectedQuantities.clear()
                    selectedCategoryId = null
                    rebuild()
                }
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString().orEmpty(); rebuild() }
            override fun afterTextChanged(s: Editable?) {}
        })
        rebuild()
        root.addView(footer)
    }
    private fun formatQty(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
    private fun drawReview(root: LinearLayout) {
        root.addView(text("Review Order", 19f, true))
        root.addView(space(8))
        root.addView(text("Vendor: ${chosenVendor()?.name ?: editingOrder?.vendorName.orEmpty()}", 15f, true))
        root.addView(space(9))
        val lines = allSelectedLines()
        if (lines.isEmpty()) root.addView(text("No items selected"))
        lines.forEach { orderLine ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val label = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            label.addView(text(orderLine.itemName, 14f, true))
            label.addView(text(orderLine.unit, 12f))
            row.addView(label, LinearLayout.LayoutParams(0, dp(55), 1f))
            row.addView(button("−") {
                selectedQuantities[orderLine.itemId] = ((selectedQuantities[orderLine.itemId] ?: orderLine.quantity) - 1).coerceAtLeast(0.0)
                renderPage()
            }, LinearLayout.LayoutParams(dp(40), dp(45)))
            val quantity = EditText(activity).apply {
                gravity = Gravity.CENTER
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setSingleLine(true)
                textSize = 15f
                setText(formatQty(selectedQuantities[orderLine.itemId] ?: orderLine.quantity))
                setSelectAllOnFocus(true)
                setOnClickListener { selectAll() }
            }
            row.addView(quantity, LinearLayout.LayoutParams(dp(57), dp(45)))
            quantity.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val q = s.toString().toDoubleOrNull()
                    if (q != null && q.isFinite() && q >= 0) selectedQuantities[orderLine.itemId] = q
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            row.addView(button("+") {
                selectedQuantities[orderLine.itemId] = (quantity.text.toString().toDoubleOrNull() ?: 0.0) + 1
                renderPage()
            }, LinearLayout.LayoutParams(dp(40), dp(45)))
            row.addView(button("×") { selectedQuantities.remove(orderLine.itemId); renderPage() }, LinearLayout.LayoutParams(dp(42), dp(45)))
            root.addView(row)
            line(root)
        }
        root.addView(space(12))
        root.addView(text("Total Items: ${lines.size}", 16f, true))
        root.addView(space(12))
        root.addView(button("Add More Items") { page = "CREATE"; renderPage() })
        root.addView(button("Save Draft") { persist("DRAFT", share = false) })
        root.addView(button("Place Order & Share") { persist("PLACED", share = true) })

    }
    private fun persist(status: String, share: Boolean) {
        val vendor = chosenVendor()
        if (vendor == null) { Toast.makeText(activity, "Choose a vendor", Toast.LENGTH_SHORT).show(); return }
        val lines = allSelectedLines()
        if (lines.isEmpty()) { Toast.makeText(activity, "Add at least one item", Toast.LENGTH_SHORT).show(); return }
        try {
            val existing = editingOrder
            if (existing != null && !isDraft(existing)) {
                Toast.makeText(activity, "Placed orders cannot be edited. Use receiving to record deliveries.", Toast.LENGTH_LONG).show()
                return
            }
            val order = HotelPurchaseOrder(
                id = existing?.id ?: UUID.randomUUID().toString(), vendorId = vendor.id,
                vendorName = vendor.name, createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                lines = lines, status = status
            )
            orders.save(order)
            editingOrder = null
            page = "HOME"
            renderPage()
            if (share) share(order)
            else Toast.makeText(activity, "Draft saved", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) { Toast.makeText(activity, error.message ?: "Unable to save order", Toast.LENGTH_LONG).show() }
    }
    private fun share(order: HotelPurchaseOrder) {
        val message = buildString {
            appendLine("PURCHASE ORDER")
            appendLine("Vendor: ${order.vendorName}")
            appendLine("Order ID: ${order.id.take(8)}")
            appendLine()
            order.lines.forEachIndexed { index, line -> appendLine("${index + 1}. ${line.itemName} - ${formatQty(line.quantity)} ${line.unit}") }
            appendLine()
            appendLine("Please confirm availability and delivery.")
        }
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }
        activity.startActivity(Intent.createChooser(intent, "Share purchase order"))
    }
    private fun drawArchive(root: LinearLayout) {
        root.addView(button("‹ Back to Pending") { page = "HOME"; renderPage() })
        root.addView(text("Archived Orders", 19f, true))
        val archived = orders.all().filter(::isArchived)
        if (archived.isEmpty()) root.addView(text("No completed orders yet"))
        val invoiceStore = HotelPurchaseInvoiceStore(activity)
        archived.groupBy { it.vendorId }.forEach { (_, vendorOrders) ->
            val name = vendors.all().firstOrNull { it.id == vendorOrders.first().vendorId }?.name
                ?: vendorOrders.first().vendorName
            root.addView(space(10))
            root.addView(text("$name  â€¢  ${vendorOrders.size} archived orders", 17f, true))
            vendorOrders.forEach { order ->
                val verified = workflows.get(order.id).archived
                val tag = if (verified) "Completed & approved" else "Previously archived (unverified)"
                root.addView(button("PO ${order.id.take(8)}  â€¢  ${order.lines.size} items  â€¢  $tag") {
                    val orderReceipts = receipts.forOrder(order.id)
                    val remaining = HotelPurchaseGoodsReceiptPolicy.remaining(order, orderReceipts)
                    val invoiceDocs = invoiceStore.forOrder(order.id)
                    val audit = workflows.get(order.id)
                    val message = buildString {
                        appendLine("Vendor: $name")
                        appendLine("PO: ${order.id.take(8)}")
                        appendLine("Status: $tag")
                        appendLine("Deliveries: ${orderReceipts.size}")
                        appendLine()
                        appendLine("ORDER AND RECEIPT")
                        order.lines.forEach { item ->
                            appendLine("${item.itemName}: ${formatQty(item.quantity)} ${item.unit} ordered, " +
                                "${formatQty(item.quantity - (remaining[item.itemId] ?: 0.0))} received")
                        }
                        appendLine()
                        appendLine("SUPPLIER DOCUMENTS (${invoiceDocs.size})")
                        invoiceDocs.forEach { doc ->
                            appendLine("${doc.kind}: ${doc.invoiceNumber}, ${doc.invoiceDate}, Rs ${doc.amount}")
                        }
                        appendLine("Net documented: Rs ${invoiceStore.netAmount(order.id)}")
                        appendLine("Invoice verified: ${if (invoiceStore.isVerified(order.id)) "Yes" else "No / historical"}")
                        appendLine()
                        appendLine("DELIVERY / APPROVAL HISTORY")
                        audit.events.forEach { event -> appendLine("${event.action}: ${event.note}") }
                    }
                    AlertDialog.Builder(activity).setTitle("$name â€¢ Archived Purchase Order")
                        .setMessage(message)
                        .setNegativeButton("Close", null)
                        .setPositiveButton("Share PO") { _, _ -> share(order) }.show()
                })
            }
        }
    }
    fun showMenu(anchor: View, onChanged: () -> Unit = {}) {
        PopupMenu(activity, anchor).apply {
            menu.add("Monthly Stock")
            menu.add("Manage Items")
            menu.add("Manage Categories")
            menu.add("Manage Vendors")
            menu.add("Draft Orders")
            menu.add("Delivered Orders")
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Monthly Stock" -> HotelMonthlyStockScreen(activity).show()
                    "Manage Items" -> { saveVisibleInputs(); HotelPurchaseItemScreen(activity).show { renderPage() } }
                    "Manage Categories" -> { saveVisibleInputs(); HotelPurchaseCategoryScreen(activity).show { renderPage() } }
                    "Manage Vendors" -> {
                        val vendorContainer = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
                        HotelPurchaseVendorScreen(activity).render(vendorContainer)
                        AlertDialog.Builder(activity).setTitle("Manage Vendors")
                            .setView(ScrollView(activity).apply { addView(vendorContainer) })
                            .setPositiveButton("Close") { _, _ -> onChanged(); renderPage() }.show()
                    }
                    "Draft Orders" -> { saveVisibleInputs(); page = "DRAFTS"; renderPage() }
                    "Delivered Orders" -> { saveVisibleInputs(); page = "DELIVERED"; renderPage() }
                }
                true
            }
            show()
        }
    }
    fun navigateBack(): Boolean {
        if (page == "HOME") return false
        if (page == "REVIEW") { page = "CREATE"; renderPage(); return true }
        if (page == "CREATE") saveVisibleInputs()
        page = "HOME"
        renderPage()
        return true
    }
}
