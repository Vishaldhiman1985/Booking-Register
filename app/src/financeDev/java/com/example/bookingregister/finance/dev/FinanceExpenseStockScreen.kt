package com.example.bookingregister.finance.dev

import android.app.Activity
import android.app.Dialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.time.LocalDate

/** Finance DEV local receipt workflow. No accounting or production writes. */
internal class FinanceExpenseStockScreen(private val activity: Activity) {
    private val store = FinanceExpenseStockLinkStore(activity)
    private val ink = Color.rgb(29, 45, 63)
    private val muted = Color.rgb(104, 118, 133)
    private val green = Color.rgb(0, 111, 84)
    private val dp: (Int) -> Int = { x -> (x * activity.resources.displayMetrics.density + 0.5f).toInt() }

    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun gap(n: Int = 9) = View(activity).apply { layoutParams = LinearLayout.LayoutParams(1, dp(n)) }
    private fun text(label: String, size: Float = 14f, bold: Boolean = false): TextView = TextView(activity).apply {
        this.text = label
        textSize = size
        setTextColor(if (bold) ink else muted)
        if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun edit(hint: String): EditText = EditText(activity).apply {
        this.hint = hint
        setSingleLine(true)
        textSize = 15f
    }
    private fun spinner(labels: List<String>): Spinner = Spinner(activity).apply {
        adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, labels)
        minimumHeight = dp(50)
    }
    private fun showError(message: String) = Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    private fun field(root: LinearLayout, caption: String, control: View) {
        root.addView(text(caption, 12f, true))
        root.addView(gap(4))
        root.addView(control)
        root.addView(gap(14))
    }
    private fun action(title: String, detail: String, click: () -> Unit): View = column().apply {
        setPadding(dp(14), dp(15), dp(14), dp(14))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(dp(1), Color.rgb(223, 231, 239))
            cornerRadius = dp(14).toFloat()
        }
        addView(text(title, 16f, true))
        addView(gap(5))
        addView(text(detail, 12f))
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    fun show(draft: FinanceDemoDraftBill, onChanged: () -> Unit = {}) {
        try {
            val link = store.get(draft.draftId)
            if (link == null) {
                showError("This older draft has no V15 stock task. New stockable drafts are enrolled on save.")
                return
            }
            if (link.state != FinanceExpenseStockPolicy.PENDING) {
                val description = when (link.state) {
                    FinanceExpenseStockPolicy.RECEIVED_DIRECT ->
                        "Direct receipt on ${link.receivedOn}:\n" +
                            link.lines.joinToString("\n") { "${it.itemName} · ${it.quantity} ${it.unit}" }
                    FinanceExpenseStockPolicy.LINKED_PO -> "Linked Purchase Order: ${link.poOrderId}. Its original goods receipts are counted once."
                    else -> "This task is no longer active."
                }
                AlertDialog.Builder(activity).setTitle("Stock · ${link.state.replace('_', ' ')}")
                    .setMessage(description).setPositiveButton("Close", null).show()
                return
            }
            val container = column().apply { setPadding(dp(18), dp(14), dp(18), dp(14)) }
            container.addView(text(draft.expenseTypeLabel ?: "Stockable expense", 19f, true))
            container.addView(gap(5))
            container.addView(text("Expense draft ${draft.draftId.take(8)} · ₹${FinanceDemoDraftPolicy.moneyText(draft.amountPaise).removePrefix("₹")}"))
            container.addView(gap(11))
            container.addView(text("Confirm what physically arrived. Saving the expense alone does not increase stock."))
            container.addView(gap(15))
            lateinit var dialog: AlertDialog
            container.addView(action("1  Enter Items Now", "Record each item and received quantity. Creates one direct receipt in Monthly Stock.") {
                dialog.dismiss(); directEntry(draft, link, onChanged)
            })
            container.addView(gap(9))
            container.addView(action("2  Complete Later", "Keep this expense in the Stock Pending list until the quantities are confirmed.") {
                dialog.dismiss()
                showError("Saved to Stock Pending. Monthly closing will require this task to be completed.")
                onChanged()
            })
            container.addView(gap(9))
            container.addView(action("3  Attach Received Purchase Order", "Choose a delivered PO for this vendor with the same final amount. The existing stock receipt is not added again.") {
                dialog.dismiss(); attachOrder(draft, onChanged)
            })
            dialog = AlertDialog.Builder(activity)
                .setTitle("Stock confirmation · DEV")
                .setView(ScrollView(activity).apply { addView(container) })
                .setNegativeButton("Close", null).create()
            dialog.show()
        } catch (error: Exception) { showError(error.message ?: "Unable to load stock task") }
    }

    /** Uses exactly the same shared two-rail item picker as Create Purchase Order. */
    private fun directEntry(draft: FinanceDemoDraftBill, link: FinanceExpenseStockLink, onChanged: () -> Unit) {
        val groups = HotelPurchaseCategoryStore(activity).all().filter { it.id in link.groupIds }
        val items = HotelPurchaseCustomItemStore(activity).catalogItems().filter { it.categoryId in link.groupIds }
        val usable = FinanceSharedItemSelectionPolicy.groupsWithItems(groups, items)
        if (usable.isEmpty()) {
            AlertDialog.Builder(activity).setTitle("No items available")
                .setMessage("Add items to a linked group in Global Catalog, then reopen this expense.")
                .setPositiveButton("OK", null).show()
            return
        }

        val quantities = linkedMapOf<String, Double>()
        var query = ""
        var selectedGroup: String? = usable.first().id
        var receivedOn = LocalDate.now().toString()
        val dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
        val dark = Color.rgb(31, 42, 59)
        val pale = Color.rgb(227, 247, 238)
        val green = Color.rgb(0, 115, 46)

        fun formatQty(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        fun button(label: String, click: () -> Unit) = TextView(activity).apply {
            text = label; textSize = 14f; setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER; setTextColor(dark); setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply { setColor(pale); cornerRadius = dp(10).toFloat() }
            isClickable = true; isFocusable = true; setOnClickListener { click() }
        }
        fun page(): LinearLayout = column().apply {
            setBackgroundColor(Color.WHITE)
            setPadding(dp(10), dp(14), dp(10), dp(6))
            // The full-screen dialog draws edge-to-edge; reserve real system/IME insets.
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout())
                val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
                view.setPadding(
                    dp(10), dp(14) + bars.top, dp(10),
                    dp(6) + maxOf(bars.bottom, keyboard.bottom)
                )
                insets
            }
        }
        fun subtitle(): TextView = text("Already purchased · only received quantities enter stock", 12f).apply {
            setPadding(dp(4), dp(3), dp(4), dp(8))
        }
        lateinit var openPicker: () -> Unit
        lateinit var openReview: () -> Unit

        openReview = {
            val root = page()
            root.addView(button("‹  Back to items") { openPicker() }, LinearLayout.LayoutParams(-1, dp(46)))
            root.addView(gap(8))
            root.addView(text("Review Received Items", 20f, true))
            root.addView(subtitle())
            root.addView(text("Received: $receivedOn", 13f, true))
            root.addView(gap(10))
            val scroll = ScrollView(activity)
            val list = column()
            scroll.addView(list)
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            val byId = items.associateBy { it.id }

            fun refreshReview() {
                list.removeAllViews()
                val current = quantities.filterValues { it > 0.0 }.toList().sortedBy { byId[it.first]?.name }
                if (current.isEmpty()) list.addView(text("No received items. Return to the item picker."))
                current.forEach { (id, q) ->
                    val product = byId[id] ?: return@forEach
                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(2), dp(6), dp(2), dp(6))
                    }
                    val label = column()
                    label.addView(text(product.name, 13f, true))
                    label.addView(text(product.unit, 11f))
                    row.addView(label, LinearLayout.LayoutParams(0, dp(54), 1f))
                    val field = edit("Qty").apply {
                        gravity = Gravity.CENTER
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        setText(formatQty(q)); setSelectAllOnFocus(true)
                        setOnClickListener { selectAll() }
                    }
                    row.addView(button("−") {
                        val next = ((field.text.toString().toDoubleOrNull() ?: 0.0) - 1.0).coerceAtLeast(0.0)
                        field.setText(formatQty(next))
                        if (next == 0.0) { quantities.remove(id); refreshReview() }
                    }, LinearLayout.LayoutParams(dp(38), dp(46)))
                    row.addView(field, LinearLayout.LayoutParams(dp(55), dp(46)))
                    row.addView(button("+") {
                        val next = (field.text.toString().toDoubleOrNull() ?: 0.0) + 1.0
                        field.setText(formatQty(next))
                    }, LinearLayout.LayoutParams(dp(38), dp(46)))
                    row.addView(button("×") { quantities.remove(id); refreshReview() },
                        LinearLayout.LayoutParams(dp(37), dp(46)))
                    field.addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            val value = s.toString().trim().toDoubleOrNull()
                            if (value != null && value.isFinite() && value > 0) quantities[id] = value
                            else quantities.remove(id)
                        }
                        override fun afterTextChanged(s: android.text.Editable?) = Unit
                    })
                    list.addView(row)
                    list.addView(View(activity).apply { setBackgroundColor(Color.rgb(232, 234, 239)) },
                        LinearLayout.LayoutParams(-1, dp(1)))
                }
            }
            refreshReview()
            root.addView(gap(10))
            root.addView(button("Add More Items") { openPicker() }, LinearLayout.LayoutParams(-1, dp(48)))
            root.addView(gap(8))
            root.addView(button("Confirm Received Stock") {
                try {
                    val confirmed = FinanceSharedItemSelectionPolicy.selectedLines(items, quantities).map { (item, q) ->
                        FinanceExpenseStockLine(item.id, item.name, item.unit, q)
                    }
                    store.receiveDirect(draft, receivedOn, confirmed)
                    dialog.dismiss()
                    showError("Received quantities saved to Monthly Stock once")
                    onChanged()
                } catch (error: Exception) {
                    showError(error.message ?: "Check the received items")
                }
            }.apply {
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply { setColor(green); cornerRadius = dp(10).toFloat() }
            }, LinearLayout.LayoutParams(-1, dp(52)))
            dialog.setContentView(root)
            ViewCompat.requestApplyInsets(root)
        }

        openPicker = {
            val root = page()
            val toolbar = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            toolbar.addView(button("‹") { dialog.dismiss(); show(draft, onChanged) }, LinearLayout.LayoutParams(dp(45), dp(48)))
            val vendorName = HotelPurchaseVendorStore(activity).all().firstOrNull { it.id == draft.vendorCode }?.name
                ?: when {
                    draft.vendorCode == "no_payee" -> "No payee"
                    draft.vendorCode.startsWith("one_time:") -> draft.vendorCode.removePrefix("one_time:")
                    else -> "Direct purchase"
                }
            toolbar.addView(text(vendorName, 16f, true), LinearLayout.LayoutParams(0, -2, 1f))
            toolbar.addView(button("×") { dialog.dismiss() }, LinearLayout.LayoutParams(dp(45), dp(48)))
            root.addView(toolbar)
            val receivedDate = button("Received date: $receivedOn  ▾") {
                val chosen = LocalDate.parse(receivedOn)
                DatePickerDialog(activity, { _, year, month, day ->
                    receivedOn = LocalDate.of(year, month + 1, day).toString()
                    openPicker()
                }, chosen.year, chosen.monthValue - 1, chosen.dayOfMonth).show()
            }.apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setTextColor(dark)
                background = GradientDrawable().apply { setColor(Color.rgb(247, 247, 251)); cornerRadius = dp(7).toFloat() }
            }
            root.addView(receivedDate, LinearLayout.LayoutParams(-1, dp(44)))
            root.addView(gap(7))
            FinanceSharedItemPicker(activity).render(
                root = root,
                groups = usable,
                items = items,
                quantities = quantities,
                initialGroupId = selectedGroup,
                initialQuery = query,
                reviewLabel = "Review Received Items  →",
                onStateChanged = { group, term -> selectedGroup = group; query = term },
                onReview = { openReview() }
            )
            dialog.setContentView(root)
            ViewCompat.requestApplyInsets(root)
        }
        dialog.show()
        dialog.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
        dialog.window?.setLayout(-1, -1)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        openPicker()
    }

    /** FINANCE_V18_MANUAL_DELIVERED_PO_MATCH
     * Show ONLY delivered orders belonging to this expense vendor whose final bill
     * exactly equals the expense amount. The operator picks the order manually.
     * This selection does not create another goods receipt or verify an invoice.
     */
    private fun attachOrder(draft: FinanceDemoDraftBill, onChanged: () -> Unit) {
        if (draft.vendorCode == "no_payee" || draft.vendorCode.startsWith("one_time:")) {
            AlertDialog.Builder(activity).setTitle("Registered vendor required")
                .setMessage("Select the same registered vendor used on the delivered Purchase Order.")
                .setPositiveButton("OK", null).show()
            return
        }

        val receipts = HotelPurchaseGoodsReceiptStore(activity)
        val closures = HotelPurchaseSimpleClosureStore(activity)
        val invoices = HotelPurchaseInvoiceStore(activity)
        val linked = store.all().filter { it.state == FinanceExpenseStockPolicy.LINKED_PO }
            .mapNotNull { it.poOrderId }.toSet()

        val matchingOrders = HotelPurchaseOrderStore(activity).all().filter { order ->
            val delivery = closures.get(order.id)
            order.vendorId == draft.vendorCode && order.status == "PLACED" &&
                delivery.closed && delivery.finalAmount?.let { amount ->
                    runCatching { FinanceExpenseStockPolicy.poAmountInPaise(amount) }.getOrNull() == draft.amountPaise
                } == true &&
                order.id !in linked &&
                receipts.forOrder(order.id).any { receipt ->
                    receipt.lines.any { line -> line.receivedQuantity > 0.0 }
                }
        }.sortedWith(compareByDescending<HotelPurchaseOrder> { closures.get(it.id).closedAtMillis }
            .thenBy { it.id })

        if (matchingOrders.isEmpty()) {
            AlertDialog.Builder(activity).setTitle("No matching delivered Purchase Orders")
                .setMessage("No unlinked delivered order from this vendor has a final bill of " +
                    "${FinanceDemoDraftPolicy.moneyText(draft.amountPaise)}. " +
                    "Check the expense vendor, amount and delivered orders in Purchasing.")
                .setPositiveButton("OK", null).show()
            return
        }

        val choices = matchingOrders.map { order ->
            val closed = closures.get(order.id)
            val deliveredOn = java.time.Instant.ofEpochMilli(closed.closedAtMillis)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
            val savedInvoice = invoices.forOrder(order.id).firstOrNull { it.kind == "INVOICE" }
            val invoiceDetail = savedInvoice?.let { "  |  Bill ${it.invoiceNumber}" }.orEmpty()
            "PO: ${order.id.take(8)}$invoiceDetail\n" +
                "Delivered: $deliveredOn  |  ${FinanceDemoDraftPolicy.moneyText(draft.amountPaise)}"
        }
        var picked = -1
        val picker = AlertDialog.Builder(activity)
            .setTitle("Delivered PO · ${matchingOrders.size} matching ${FinanceDemoDraftPolicy.moneyText(draft.amountPaise)}")
            .setSingleChoiceItems(choices.toTypedArray(), -1) { _, position -> picked = position }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Review selected PO", null)
            .create()
        picker.setOnShowListener {
            picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (picked !in matchingOrders.indices) {
                    showError("Select the correct Purchase Order first")
                    return@setOnClickListener
                }
                val selected = matchingOrders[picked]
                val closed = closures.get(selected.id)
                val deliveredOn = java.time.Instant.ofEpochMilli(closed.closedAtMillis)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                picker.dismiss()
                AlertDialog.Builder(activity)
                    .setTitle("Confirm Purchase Order")
                    .setMessage("Vendor: ${selected.vendorName}\n" +
                        "Order no: ${selected.id}\n" +
                        "Delivered: $deliveredOn\n" +
                        "Final bill: ${FinanceDemoDraftPolicy.moneyText(draft.amountPaise)}\n" +
                        "Expense: ${FinanceDemoDraftPolicy.moneyText(draft.amountPaise)}\n\n" +
                        "Attach this order to the expense? The PO receipt is already included " +
                        "in stock; no quantities will be added again. " +
                        "This manual link does not verify the supplier invoice.")
                    .setNegativeButton("Back") { _, _ -> attachOrder(draft, onChanged) }
                    .setPositiveButton("Attach") { _, _ ->
                        try {
                            store.attachDeliveredOrderByManualMatch(draft, selected)
                            showError("Selected Purchase Order linked. No duplicate stock receipt.")
                            onChanged()
                        } catch (error: Exception) {
                            showError(error.message ?: "Could not link selected Purchase Order")
                        }
                    }.show()
            }
        }
        picker.show()
    }
}
