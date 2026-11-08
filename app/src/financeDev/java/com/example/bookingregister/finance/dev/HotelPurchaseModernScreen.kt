package com.example.bookingregister.finance.dev

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import java.util.UUID

/** Full-screen Finance DEV purchasing UI. Existing category/item/vendor/order stores are reused. */
class HotelPurchaseModernScreen(private val activity: Activity) {
    private val vendors = HotelPurchaseVendorStore(activity)
    private val orders = HotelPurchaseOrderStore(activity)
    private val workflows = HotelPurchaseReceivingWorkflowStore(activity)
    private val receipts = HotelPurchaseGoodsReceiptStore(activity)
    private val dark = Color.rgb(33, 46, 68)
    private val mint = Color.rgb(224, 247, 239)
    private val lavender = Color.rgb(243, 240, 254)
    private val white = Color.WHITE
    private val bg = Color.rgb(250, 250, 254)
    private var dialog: Dialog? = null
    private val selected = linkedMapOf<String, Double>()
    private var selectedVendorId: String? = null
    private var selectedCategoryId: String? = null
    private var query = ""
    private var archiveMode = false
    private lateinit var page: LinearLayout

    private fun dp(n: Int): Int = (activity.resources.displayMetrics.density * n).toInt()
    private fun shape(color: Int, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(13).toFloat()
        setColor(color)
        if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun text(label: String, size: Float = 14f, bold: Boolean = false): TextView = TextView(activity).apply {
        this.text = label
        textSize = size
        setTextColor(dark)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun button(label: String, color: Int = lavender, action: () -> Unit): Button = Button(activity).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(dark)
        background = shape(color)
        setOnClickListener { action() }
    }
    private fun column(): LinearLayout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row(): LinearLayout = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun pad(view: View, x: Int = 10, y: Int = 8): View = view.apply { setPadding(dp(x), dp(y), dp(x), dp(y)) }
    private fun section(): LinearLayout = column().apply { background = shape(white, Color.rgb(231, 232, 242)); setPadding(dp(9), dp(8), dp(9), dp(8)) }
    private fun toast(s: String) = Toast.makeText(activity, s, Toast.LENGTH_LONG).show()

    fun show() {
        dialog = Dialog(activity).apply {
            setContentView(ScrollView(activity).apply {
                isFillViewport = true
                page = column().apply { setPadding(dp(12), dp(10), dp(12), dp(12)); setBackgroundColor(bg) }
                addView(page)
            })
            window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog?.show()
        dialog?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        repaint()
    }

    private fun activeVendors() = vendors.all()
    private fun activeVendor(): HotelPurchaseVendor? = activeVendors().firstOrNull { it.id == selectedVendorId }
    private fun fullCatalog(): List<HotelPurchaseItem> {
        val original = HotelPurchaseItems.items
        val master = HotelPurchaseMasterCatalog.items
        return original.map { base ->
            val alt = master.firstOrNull { it.categoryId == base.categoryId && it.name.equals(base.name, true) }
            base.copy(synonyms = (base.synonyms + (alt?.synonyms ?: emptyList())).distinct())
        } + master.filter { m -> original.none { o -> o.categoryId == m.categoryId && o.name.equals(m.name, true) } } +
            HotelPurchaseCustomItemStore(activity).all()
    }
    private fun available(): List<HotelPurchaseItem> = activeVendor()?.let { v ->
        HotelPurchasePolicy.itemsForVendor(v, fullCatalog())
    } ?: emptyList()

    private fun repaint() {
        page.removeAllViews()
        val header = row()
        header.addView(text("Purchasing", 22f, true), LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(button("Pending", if (!archiveMode) mint else lavender) { archiveMode = false; repaint() })
        header.addView(button("Archive", if (archiveMode) mint else lavender) { archiveMode = true; repaint() })
        header.addView(button("✕") { dialog?.dismiss() })
        page.addView(header)
        page.addView(text("Purchase orders • goods receiving • vendor history", 12f).apply { setTextColor(Color.GRAY) })
        val vendorList = activeVendors()
        if (vendorList.isEmpty()) {
            page.addView(text("Add a vendor using Manage Vendors to begin.", 16f))
            page.addView(button("Manage Vendors") { manage("Manage Vendors") })
            return
        }
        if (selectedVendorId == null || vendorList.none { it.id == selectedVendorId }) selectedVendorId = vendorList.first().id
        val vendorArea = row().apply { setPadding(0, dp(14), 0, dp(8)) }
        vendorArea.addView(text("Vendor  ", 14f, true))
        val picker = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, vendorList.map { it.name })
            setSelection(vendorList.indexOfFirst { it.id == selectedVendorId }.coerceAtLeast(0))
        }
        vendorArea.addView(picker, LinearLayout.LayoutParams(0, dp(48), 1f))
        picker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (selectedVendorId != vendorList[position].id) {
                    if (selected.isNotEmpty()) {
                        AlertDialog.Builder(activity).setTitle("Change vendor?")
                            .setMessage("Changing vendor will clear the unsent item selection.")
                            .setNegativeButton("Keep current vendor") { _, _ -> picker.setSelection(vendorList.indexOfFirst { it.id == selectedVendorId }) }
                            .setPositiveButton("Change") { _, _ -> selected.clear(); selectedVendorId = vendorList[position].id; selectedCategoryId = null; repaint() }
                            .show()
                    } else { selectedVendorId = vendorList[position].id; selectedCategoryId = null; repaint() }
                }
            }
        }
        page.addView(vendorArea)
        val toolbar = row()
        toolbar.addView(button("Items") { manage("Manage Items") })
        toolbar.addView(button("Categories") { manage("Manage Categories") })
        toolbar.addView(button("Vendors") { manage("Manage Vendors") })
        page.addView(toolbar)
        if (archiveMode) { renderHistory(true); return }
        val contentArea = column().apply { tag = "itemsHolder" }
        val searchBox = EditText(activity).apply {
            hint = "Search items by name"
            setSingleLine(true)
            setText(query)
            background = shape(white, Color.rgb(228, 229, 239))
            setPadding(dp(14), dp(8), dp(14), dp(8))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s.toString(); rebuildContent(contentArea)
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        page.addView(searchBox, LinearLayout.LayoutParams(-1, dp(49)).apply { topMargin = dp(8) })
        page.addView(contentArea)
        rebuildContent(contentArea)
        renderHistory(false)
    }

    private fun rebuildContent(target: LinearLayout) {
        target.removeAllViews()
        val items = available()
        val categories = HotelPurchaseCategoryStore(activity).all().filter { c -> items.any { it.categoryId == c.id } }
        if (selectedCategoryId !in categories.map { it.id }) selectedCategoryId = categories.firstOrNull()?.id
        val wide = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            activity.resources.configuration.screenWidthDp >= 680
        val outer = if (wide) row() else column()
        target.addView(outer)
        val tabs = if (wide) column() else row()
        val catBox = section()
        catBox.addView(text("Categories", 16f, true))
        categories.forEach { cat ->
            val b = button(cat.name, if (selectedCategoryId == cat.id) mint else lavender) {
                selectedCategoryId = cat.id
                rebuildContent(target)
            }
            tabs.addView(b)
        }
        if (wide) {
            catBox.addView(ScrollView(activity).apply { addView(tabs) }, LinearLayout.LayoutParams(-1, dp(380)))
            outer.addView(catBox, LinearLayout.LayoutParams(0, -2, 0.28f).apply { rightMargin = dp(8) })
        } else {
            val horizontal = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
            catBox.addView(horizontal)
            outer.addView(catBox)
        }
        val listBox = section()
        listBox.addView(text("Items", 17f, true))
        val itemList = column()
        val visible = items.filter { item ->
            (query.isNotBlank() || item.categoryId == selectedCategoryId) &&
                (query.isBlank() || item.name.contains(query, true) || item.synonyms.any { it.contains(query, true) })
        }.take(120)
        if (visible.isEmpty()) itemList.addView(text("No matching items", 13f))
        visible.forEach { item ->
            val itemRow = row()
            itemRow.addView(text("${item.name}\n${item.unit}", 13f), LinearLayout.LayoutParams(0, dp(55), 1f))
            val minus = button("−", lavender) {
                val next = ((selected[item.id] ?: 0.0) - 1).coerceAtLeast(0.0)
                if (next == 0.0) selected.remove(item.id) else selected[item.id] = next
                rebuildContent(target)
            }
            itemRow.addView(minus)
            val qty = EditText(activity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setSingleLine(true)
                gravity = Gravity.CENTER
                setText((selected[item.id] ?: 0.0).let { fmt(it) })
                background = shape(lavender)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val q = s.toString().toDoubleOrNull()
                        if (q != null && q.isFinite() && q > 0) selected[item.id] = q
                        else selected.remove(item.id)
                        refreshSelectedPanel()
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
            }
            itemRow.addView(qty, LinearLayout.LayoutParams(dp(61), dp(43)))
            itemRow.addView(button("+", mint) {
                val next = (selected[item.id] ?: 0.0) + 1.0
                selected[item.id] = next
                rebuildContent(target)
            })
            itemList.addView(itemRow)
            itemList.addView(View(activity).apply { setBackgroundColor(Color.rgb(240, 240, 245)) }, LinearLayout.LayoutParams(-1, dp(1)))
        }
        listBox.addView(ScrollView(activity).apply { addView(itemList) }, LinearLayout.LayoutParams(-1, if (wide) dp(405) else dp(310)))
        if (wide) outer.addView(listBox, LinearLayout.LayoutParams(0, -2, 0.43f).apply { rightMargin = dp(8); topMargin = 0 })
        else outer.addView(listBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val summary = section()
        summary.tag = "selectedPanel"
        if (wide) outer.addView(summary, LinearLayout.LayoutParams(0, -2, 0.29f))
        else outer.addView(summary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        populateSelectedPanel(summary)
    }

    private fun refreshSelectedPanel() {
        val content = page.findViewWithTag<LinearLayout>("selectedPanel") ?: return
        populateSelectedPanel(content)
    }
    private fun populateSelectedPanel(view: LinearLayout) {
        view.removeAllViews()
        view.addView(text("Selected Items (${selected.size})", 16f, true))
        val names = fullCatalog().associateBy { it.id }
        selected.toMap().forEach { (id, q) ->
            val item = names[id] ?: return@forEach
            val itemRow = row()
            itemRow.addView(text("${item.name}  ${fmt(q)} ${item.unit}", 12f), LinearLayout.LayoutParams(0, dp(39), 1f))
            itemRow.addView(button("✕") { selected.remove(id); rebuildContent(page.findViewWithTag<LinearLayout>("itemsHolder") ?: return@button) })
            view.addView(itemRow)
        }
        val actions = column()
        actions.addView(button("Clear selection", lavender) {
            AlertDialog.Builder(activity).setMessage("Clear all selected items?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ -> selected.clear(); repaint() }.show()
        })
        actions.addView(button("Save Draft", lavender) { saveOrder(false) })
        actions.addView(button("Create & Share", mint) { saveOrder(true) })
        view.addView(actions)
    }

    private fun saveOrder(shareAfterSaving: Boolean) {
        val vendor = activeVendor() ?: return
        val items = available().associateBy { it.id }
        val lines = selected.mapNotNull { (id, qty) ->
            val item = items[id] ?: return@mapNotNull null
            HotelPurchaseOrderLine(item.id, item.name, qty, item.unit)
        }
        if (lines.isEmpty()) { toast("Select at least one item"); return }
        AlertDialog.Builder(activity)
            .setTitle("Create purchase order")
            .setMessage("${vendor.name} • ${lines.size} items. ${if (shareAfterSaving) "Create and open sharing?" else "Save as draft?"}")
            .setNegativeButton("Back", null)
            .setPositiveButton("Create") { _, _ ->
                try {
                    val order = HotelPurchaseOrder(
                        UUID.randomUUID().toString(), vendor.id, vendor.name,
                        System.currentTimeMillis(), lines,
                        if (shareAfterSaving) "READY_TO_SHARE" else "DRAFT"
                    )
                    orders.save(order)
                    selected.clear(); query = ""; toast("Order saved"); repaint()
                    if (shareAfterSaving) {
                        val message = buildString {
                            appendLine("PURCHASE ORDER ${order.id.take(8)}")
                            appendLine("Vendor: ${order.vendorName}")
                            order.lines.forEach { appendLine("- ${it.itemName}: ${fmt(it.quantity)} ${it.unit}") }
                            appendLine("Please confirm availability and delivery.")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        }
                        activity.startActivity(Intent.createChooser(intent, "Share purchase order"))
                    }
                } catch (e: Exception) { toast(e.message ?: "Order failed") }
            }.show()
    }

    private fun renderHistory(archived: Boolean) {
        val title = if (archived) "Archive • Approved orders" else "Pending Orders"
        val history = orders.all().filter { workflows.get(it.id).archived == archived }
        val panel = section().apply { setPadding(dp(12), dp(10), dp(12), dp(10)) }
        panel.addView(text("$title (${history.size})", 17f, true))
        if (history.isEmpty()) panel.addView(text("No orders here", 13f))
        history.forEach { order ->
            val received = receipts.forOrder(order.id)
            val pending = if (received.isNotEmpty()) HotelPurchaseGoodsReceiptPolicy.remaining(order, received).values.count { it > 0.000001 } else order.lines.size
            panel.addView(button("${order.vendorName} • ${order.lines.size} items • ${if (archived) "Approved" else if (received.isEmpty()) "Awaiting delivery" else "$pending items pending"}", white) {
                orderDetails(order)
            })
        }
        page.addView(panel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    private fun orderDetails(order: HotelPurchaseOrder) {
        val workflow = workflows.get(order.id)
        val prior = receipts.forOrder(order.id)
        val outstanding = HotelPurchaseGoodsReceiptPolicy.remaining(order, prior)
        val message = buildString {
            appendLine("Vendor: ${order.vendorName}")
            appendLine("Order: ${order.id.take(8)}")
            appendLine("${if (workflow.archived) "APPROVED / ARCHIVED" else "PENDING"}")
            appendLine()
            order.lines.forEach { appendLine("${it.itemName} • ordered ${fmt(it.quantity)} ${it.unit} • remaining ${fmt(outstanding[it.itemId] ?: 0.0)}") }
            appendLine()
            workflow.events.forEach { appendLine("${it.action}: ${it.note}") }
        }
        val builder = AlertDialog.Builder(activity).setTitle("Purchase Order").setMessage(message).setNegativeButton("Close", null)
            .setNeutralButton("Share PO") { _, _ ->
                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }
                activity.startActivity(Intent.createChooser(intent, "Share purchase order"))
            }
        if (!workflow.archived) builder.setPositiveButton("Receive / Review") { _, _ ->
            HotelPurchaseReceiveScreen(activity).show(order) { repaint() }
        }
        builder.show()
    }
    private fun manage(which: String) {
        when (which) {
            "Manage Items" -> HotelPurchaseItemScreen(activity).show()
            "Manage Categories" -> HotelPurchaseCategoryScreen(activity).show()
            else -> {
                val content = column()
                HotelPurchaseVendorScreen(activity).render(content)
                AlertDialog.Builder(activity).setTitle("Manage Vendors")
                    .setView(ScrollView(activity).apply { addView(content) })
                    .setPositiveButton("Done") { _, _ -> repaint() }.show()
            }
        }
    }
    private fun fmt(q: Double): String = if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString()
}
