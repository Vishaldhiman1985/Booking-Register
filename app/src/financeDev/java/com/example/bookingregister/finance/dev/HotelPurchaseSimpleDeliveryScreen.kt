package com.example.bookingregister.finance.dev

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Staff-facing delivery UI only. Legacy invoice and approval evidence remains untouched. */
class HotelPurchaseSimpleDeliveryScreen(private val activity: Activity) {
    private val receipts = HotelPurchaseGoodsReceiptStore(activity)
    private val closures = HotelPurchaseSimpleClosureStore(activity)
    private val workflow = HotelPurchaseReceivingWorkflowStore(activity)
    private val ink = Color.rgb(35, 48, 64)
    private val soft = Color.rgb(106, 118, 129)
    private val green = Color.rgb(35, 133, 94)
    private val red = Color.rgb(201, 72, 73)
    private val amber = Color.rgb(179, 119, 41)
    private val lineColor = Color.rgb(232, 236, 240)
    private val offWhite = Color.rgb(248, 250, 250)
    private fun dp(n: Int) = (activity.resources.displayMetrics.density * n + 0.5f).toInt()
    private fun rounded(color: Int, border: Int? = null): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(color)
        if (border != null) setStroke(dp(1), border)
    }
    private fun text(value: String, size: Float = 14f, heavy: Boolean = false, color: Int = ink): TextView =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (heavy) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
    private fun col(): LinearLayout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun gap(n: Int) = android.widget.Space(activity).apply { layoutParams = LinearLayout.LayoutParams(1, dp(n)) }
    private fun rule(): View = View(activity).apply { setBackgroundColor(lineColor) }
    private fun pill(label: String, color: Int, onClick: () -> Unit): TextView =
        text(label, 13f, true, color).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(if (color == green) Color.rgb(230, 246, 238) else Color.rgb(255, 240, 238))
            setOnClickListener { onClick() }
        }
    private fun money(amount: Double): String = "₹" + String.format(Locale.getDefault(), "%,.2f", amount)
    private fun fmt(qty: Double) = if (qty == qty.toLong().toDouble()) qty.toLong().toString() else qty.toString()
    private fun toast(s: String) = Toast.makeText(activity, s, Toast.LENGTH_LONG).show()

    private data class Choice(var mode: String, var qty: Double, var field: EditText? = null)

    fun open(order: HotelPurchaseOrder, onChanged: () -> Unit = {}) {
        val saved = closures.get(order.id)
        if (saved.closed || workflow.get(order.id).archived || order.status == "ARCHIVED") {
            showHistory(order)
            return
        }
        val earlier = receipts.forOrder(order.id)
        val rawBalances = HotelPurchaseGoodsReceiptPolicy.remaining(order, earlier)
        val balances = rawBalances.mapValues { (id, qty) ->
            (qty - (saved.unavailable[id] ?: 0.0)).coerceAtLeast(0.0)
        }
        // Fully received legacy orders can be closed with only a final bill amount.
        val first = earlier.isEmpty() && saved.updates.isEmpty()
        val choices = mutableMapOf<String, Choice>()
        order.lines.forEach { item ->
            choices[item.itemId] = Choice(if (first) "FULL" else "PENDING", if (first) item.quantity else 0.0)
        }
        val dialog = Dialog(activity)
        // Use one scrollable root. A weighted child inside a wrap-content dialog can
        // measure to zero height on some devices, leaving a blank white screen.
        val outer = col().apply {
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(20))
        }
        val content = col()
        outer.addView(content)
        val title = if (first) "Receive Goods" else "Short Delivery"
        content.addView(text(title, 23f, true))
        content.addView(gap(3))
        content.addView(text("${order.vendorName}   ·   Order ${order.id.take(8)}", 13f, false, soft))
        content.addView(text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(order.createdAtMillis)), 12f, false, soft))
        content.addView(gap(14))
        content.addView(text(if (first) "Ordered items" else "Ordered items · remaining quantities", 16f, true))
        content.addView(gap(8))
        var actionButton: TextView? = null
        val summary = col().apply {
            background = rounded(offWhite)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        fun actualReceived(choice: Choice, outstanding: Double): Double = when (choice.mode) {
            "FULL", "RECEIVED" -> outstanding
            "MISSING", "PENDING", "UNAVAILABLE" -> 0.0
            else -> choice.field?.text?.toString()?.toDoubleOrNull() ?: Double.NaN
        }
        fun calculate(): Triple<List<String>, List<String>, Map<String, Double>> {
            val pending = mutableListOf<String>()
            val short = mutableListOf<String>()
            val unresolved = mutableMapOf<String, Double>()
            order.lines.forEach { item ->
                val outstanding = balances[item.itemId] ?: 0.0
                if (outstanding <= 0.000001) return@forEach
                val choice = choices.getValue(item.itemId)
                val receivedNow = actualReceived(choice, outstanding)
                val left = outstanding - receivedNow
                when (choice.mode) {
                    "MISSING", "PENDING" -> pending.add("${item.itemName} · ${fmt(outstanding)} ${item.unit}")
                    "UNAVAILABLE" -> short.add("${item.itemName} · ${fmt(outstanding)} ${item.unit} not available")
                    else -> if (!left.isFinite()) short.add("${item.itemName} · enter received quantity")
                        else if (left > 0.000001) short.add("${item.itemName} · ${fmt(left)} ${item.unit} short")
                }
                unresolved[item.itemId] = left
            }
            return Triple(pending, short, unresolved)
        }
        fun redrawSummary() {
            val (missing, short, unresolved) = calculate()
            val closable = unresolved.all { (id, outstanding) ->
                outstanding.isFinite() && (outstanding <= 0.000001 || (!first && choices[id]?.mode == "UNAVAILABLE"))
            }
            actionButton?.text = if (closable) "Mark Delivered & Close" else if (first) "Save & Share Shortage" else "Save Short Delivery"
            summary.removeAllViews()
            summary.addView(text(if (missing.isEmpty() && short.isEmpty()) "All items accounted for" else "Delivery issues", 15f, true))
            if (missing.isNotEmpty()) {
                summary.addView(gap(8))
                summary.addView(text("Pending items", 12f, true, red))
                missing.forEach { summary.addView(text(it, 13f)) }
            }
            if (short.isNotEmpty()) {
                summary.addView(gap(8))
                summary.addView(text("Short / unavailable items", 12f, true, amber))
                short.forEach { summary.addView(text(it, 13f)) }
            }
        }
        order.lines.forEach { item ->
            val balance = balances[item.itemId] ?: 0.0
            val itemRow = row().apply { setPadding(0, dp(8), 0, dp(8)) }
            val label = col()
            label.addView(text(item.itemName, 15f, true))
            val alreadyReceived = item.quantity - (rawBalances[item.itemId] ?: item.quantity)
            label.addView(text("Ordered ${fmt(item.quantity)} ${item.unit}" +
                if (first) "" else " · Received ${fmt(alreadyReceived)} · Left ${fmt(balance)} ${item.unit}", 12f, false, soft))
            itemRow.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            if (!first) {
                if (balance <= 0.000001) {
                    val unavailableEarlier = (saved.unavailable[item.itemId] ?: 0.0) > 0.000001
                    itemRow.addView(text(if (unavailableEarlier) "Not Available" else "✓ Received", 12f, true,
                        if (unavailableEarlier) red else green))
                } else {
                    itemRow.addView(text("Review below", 12f, true, amber))
                }
                content.addView(itemRow)
            } else {
                val choice = choices.getValue(item.itemId)
                val toggleHolder = row()
                val toggle = pill("✓", green) {}
                val actual = EditText(activity).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setSingleLine(true)
                    textSize = 14f
                    setText("")
                    setSelectAllOnFocus(true)
                    background = rounded(offWhite, lineColor)
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    visibility = View.GONE
                    choice.field = this
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { redrawSummary() }
                        override fun afterTextChanged(s: android.text.Editable?) {}
                    })
                }
                val shortButton = text("Short", 12f, true, soft).apply {
                    gravity = Gravity.CENTER
                    background = rounded(offWhite, lineColor)
                    setPadding(dp(9), dp(10), dp(9), dp(10))
                    setOnClickListener {
                        // Tapping Short again cancels the accidental selection.
                        choice.mode = if (choice.mode == "SHORT") "FULL" else "SHORT"
                        toggle.text = "✓"
                        toggle.setTextColor(green)
                        toggle.background = rounded(Color.rgb(230, 246, 238))
                        actual.visibility = if (choice.mode == "SHORT") View.VISIBLE else View.GONE
                        redrawSummary()
                    }
                }
                toggle.setOnClickListener {
                    choice.mode = if (choice.mode == "MISSING") "FULL" else if (choice.mode == "SHORT") "FULL" else "MISSING"
                    toggle.text = if (choice.mode == "MISSING") "✕" else "✓"
                    toggle.setTextColor(if (choice.mode == "MISSING") red else green)
                    toggle.background = rounded(if (choice.mode == "MISSING") Color.rgb(255, 240, 238) else Color.rgb(230, 246, 238))
                    actual.visibility = View.GONE
                    redrawSummary()
                }
                toggleHolder.addView(toggle)
                toggleHolder.addView(shortButton, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
                itemRow.addView(toggleHolder)
                val itemCol = col()
                itemCol.addView(itemRow)
                itemCol.addView(actual, LinearLayout.LayoutParams(dp(120), -2).apply { gravity = Gravity.END; bottomMargin = dp(5) })
                content.addView(itemCol)
            }
            content.addView(rule(), LinearLayout.LayoutParams(-1, dp(1)))
        }
        // Reopened short deliveries keep the complete order above. Only unresolved
        // items receive an action dropdown below; no action is selected by default.
        if (!first) {
            content.addView(gap(14))
            content.addView(text("Review outstanding items", 17f, true))
            content.addView(gap(5))
            content.addView(text("Select an action for each issue, then confirm your update.", 12f, false, soft))
            val actionLabels = listOf("Pending — send later", "Received remaining", "Partially received", "Not Available")
            val actionModes = listOf("PENDING", "RECEIVED", "PARTIAL", "UNAVAILABLE")
            order.lines.forEach { item ->
                val left = balances[item.itemId] ?: 0.0
                if (left <= 0.000001) return@forEach
                val choice = choices.getValue(item.itemId)
                val card = col().apply {
                    background = rounded(offWhite, lineColor)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                card.addView(text(item.itemName, 15f, true))
                card.addView(text("Remaining ${fmt(left)} ${item.unit}", 12f, false, soft))
                val quantity = EditText(activity).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setSingleLine(true)
                    hint = "Received now (${item.unit})"
                    background = rounded(Color.WHITE, lineColor)
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    visibility = View.GONE
                    choice.field = this
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { redrawSummary() }
                        override fun afterTextChanged(s: android.text.Editable?) {}
                    })
                }
                val dropdown = Spinner(activity).apply {
                    adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, actionLabels)
                    setSelection(0)
                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            choice.mode = actionModes[position]
                            quantity.visibility = if (choice.mode == "PARTIAL") View.VISIBLE else View.GONE
                            redrawSummary()
                        }
                    }
                }
                card.addView(dropdown, LinearLayout.LayoutParams(-1, dp(48)))
                card.addView(quantity, LinearLayout.LayoutParams(-1, -2))
                content.addView(gap(8))
                content.addView(card)
            }
        }
        content.addView(gap(14))
        content.addView(summary)
        redrawSummary()
        content.addView(gap(14))
        content.addView(text(if (first) "Bill amount (₹)" else "Check final bill amount (₹)", 14f, true))
        val amount = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            hint = "Enter amount"
            setText(saved.updates.lastOrNull()?.billAmount?.let { fmt(it) } ?: "")
            textSize = 19f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(offWhite, lineColor)
        }
        content.addView(amount, LinearLayout.LayoutParams(-1, dp(52)))
        content.addView(gap(8))
        content.addView(text(if (saved.updates.isNotEmpty())
            "Previous amount: ${money(saved.updates.last().billAmount)} · Keep or correct it."
            else "Only a delivery reference. Expenses and payments stay in Finance.", 11f, false, soft))
        val verifiedAmount = CheckBox(activity).apply {
            text = "I checked the final amount against the vendor bill"
            textSize = 13f
            setTextColor(ink)
            isChecked = false
        }
        content.addView(verifiedAmount)
        content.addView(gap(16))
        val footer = row()
        val cancel = pill("Back", soft) { dialog.dismiss() }
        cancel.background = rounded(offWhite)
        footer.addView(cancel)
        val save = text("Save Delivery", 15f, true, Color.WHITE).apply {
            gravity = Gravity.CENTER
            background = rounded(green)
            setPadding(dp(14), dp(15), dp(14), dp(15))
        }
        footer.addView(save, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        actionButton = save
        redrawSummary()
        outer.addView(gap(8))
        outer.addView(footer)
        val scrollRoot = ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            setBackgroundColor(Color.WHITE)
            addView(outer, ViewGroup.LayoutParams(-1, -2))
        }
        dialog.setContentView(scrollRoot)
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        save.setOnClickListener {
            val finalBill = amount.text?.toString()?.trim()?.toDoubleOrNull()
            if (finalBill == null || !finalBill.isFinite() || finalBill < 0) { amount.error = "Enter the bill amount"; return@setOnClickListener }
            val latest = receipts.forOrder(order.id)
            if (latest.map { it.id } != earlier.map { it.id } || closures.get(order.id) != saved) {
                toast("This order changed. Reopen it for the latest quantities.")
                return@setOnClickListener
            }
            val latestBalance = HotelPurchaseGoodsReceiptPolicy.remaining(order, latest).mapValues { (id, qty) ->
                (qty - (saved.unavailable[id] ?: 0.0)).coerceAtLeast(0.0)
            }
            val delivered = mutableListOf<HotelPurchaseGoodsReceiptLine>()
            val unavailable = mutableMapOf<String, Double>()
            try {
                order.lines.forEach { item ->
                    val remainingQty = latestBalance[item.itemId] ?: 0.0
                    if (remainingQty <= 0.000001) return@forEach
                    val choice = choices.getValue(item.itemId)
                    val receivedQty = when (choice.mode) {
                        "FULL", "RECEIVED" -> remainingQty
                        "MISSING", "PENDING", "UNAVAILABLE" -> 0.0
                        else -> choice.field?.text?.toString()?.toDoubleOrNull()
                            ?: throw IllegalArgumentException("Enter actual quantity for ${item.itemName}")
                    }
                    require(receivedQty.isFinite() && receivedQty >= 0 && receivedQty <= remainingQty + 0.000001) {
                        "Invalid quantity for ${item.itemName}"
                    }
                    if (choice.mode == "SHORT" || choice.mode == "PARTIAL") {
                        require(receivedQty > 0.000001 && receivedQty < remainingQty - 0.000001) {
                            "Enter a partial quantity for ${item.itemName}, or select Received / Not Received"
                        }
                    }
                    HotelPurchaseSimpleDeliveryPolicy.validateUpdate(remainingQty, receivedQty,
                        if (!first && choice.mode == "UNAVAILABLE") remainingQty else 0.0)
                    if (receivedQty > 0.000001) delivered.add(HotelPurchaseGoodsReceiptLine(item.itemId, item.itemName, item.unit, receivedQty))
                    if (!first && choice.mode == "UNAVAILABLE") unavailable[item.itemId] = remainingQty
                }
                if (!first && delivered.isEmpty() && unavailable.isEmpty() && latestBalance.values.any { it > 0.000001 }) {
                    toast("Select Received, Partial or Not Available for an outstanding item")
                    return@setOnClickListener
                }
                val residual = latestBalance.mapValues { (id, qty) ->
                    (qty - delivered.filter { it.itemId == id }.sumOf { it.receivedQuantity }).coerceAtLeast(0.0)
                }
                val totalUnavailable = saved.unavailable.toMutableMap().apply {
                    unavailable.forEach { (id, qty) -> this[id] = (this[id] ?: 0.0) + qty }
                }
                val complete = residual.all { (id, qty) -> kotlin.math.abs(qty - (unavailable[id] ?: 0.0)) < 0.000001 }
                if (complete && !verifiedAmount.isChecked) {
                    toast("Confirm that you checked the final bill amount")
                    return@setOnClickListener
                }
                val message = when {
                    complete -> "Final bill: ${money(finalBill)}. Close this order? All received and unavailable quantities will be retained in history."
                    first -> "Save this delivery and share the missing quantities with the vendor?"
                    else -> "Save received quantities and keep this order under Short Delivered?"
                }
                AlertDialog.Builder(activity).setTitle(if (complete) "Mark Delivered & Close" else "Record Short Delivery")
                    .setMessage(message)
                    .setNegativeButton("Back", null)
                    .setPositiveButton("Confirm") { _, _ ->
                        try {
                            val receiptId = if (delivered.isNotEmpty()) UUID.randomUUID().toString() else ""
                            if (delivered.isNotEmpty()) receipts.add(order, HotelPurchaseGoodsReceipt(
                                receiptId, order.id, System.currentTimeMillis(), delivered,
                                if (first) "First delivery" else "Additional delivery"
                            ))
                            closures.saveUpdate(order.id, finalBill, receiptId,
                                if (first) "First delivery" else "Remaining goods update", unavailable)
                            if (complete) closures.close(order, receipts.forOrder(order.id), totalUnavailable, finalBill)
                            dialog.dismiss()
                            onChanged()
                            toast(if (complete) "Order delivered and closed" else "Short delivery saved")
                            if (!complete) shareShort(order, receipts.forOrder(order.id))
                        } catch (error: Exception) {
                            toast(error.message ?: "Unable to save delivery")
                            onChanged()
                        }
                    }.show()
            } catch (error: Exception) { toast(error.message ?: "Correct the quantities") }
        }
    }

    private fun shareShort(order: HotelPurchaseOrder, history: List<HotelPurchaseGoodsReceipt>) {
        val unavailable = closures.get(order.id).unavailable
        val remaining = HotelPurchaseGoodsReceiptPolicy.remaining(order, history).mapValues { (id, qty) ->
            (qty - (unavailable[id] ?: 0.0)).coerceAtLeast(0.0)
        }
        val message = buildString {
            appendLine("Purchase Order ${order.id.take(8)} — ${order.vendorName}")
            appendLine("Please send these outstanding items or confirm if unavailable:")
            order.lines.forEach { item ->
                val missing = remaining[item.itemId] ?: 0.0
                if (missing > 0.000001) appendLine("${item.itemName}: ${fmt(missing)} ${item.unit}")
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }
        activity.startActivity(Intent.createChooser(intent, "Share missing goods with vendor"))
    }

    fun showHistory(order: HotelPurchaseOrder) {
        val record = closures.get(order.id)
        val history = receipts.forOrder(order.id)
        val missing = HotelPurchaseGoodsReceiptPolicy.remaining(order, history)
        val content = col().apply { setPadding(dp(16), dp(14), dp(16), dp(20)) }
        content.addView(text("${order.vendorName}  ·  ${order.id.take(8)}", 17f, true))
        content.addView(gap(5))
        content.addView(text("Delivered: ${history.size} deliveries · ${record.unavailable.count { it.value > 0.000001 }} unavailable item(s)", 12f, false, soft))
        content.addView(gap(13))
        order.lines.forEach { item ->
            val notAvailable = record.unavailable[item.itemId] ?: 0.0
            val receivedQty = item.quantity - (missing[item.itemId] ?: 0.0)
            val line = row().apply { setPadding(0, dp(10), 0, dp(10)) }
            val name = col()
            name.addView(text(item.itemName, 14f, true))
            name.addView(text("Ordered ${fmt(item.quantity)} ${item.unit}", 12f, false, soft))
            line.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(text(if (notAvailable > 0.000001) "${fmt(receivedQty)} ${item.unit} · Not Available ${fmt(notAvailable)}" else "${fmt(receivedQty)} ${item.unit}", 12f, true, if (notAvailable > 0.000001) red else green))
            content.addView(line)
            content.addView(rule(), LinearLayout.LayoutParams(-1, dp(1)))
        }
        content.addView(gap(13))
        content.addView(text("Final bill: " + (record.finalAmount?.let(::money) ?: "Not captured (legacy order)"), 18f, true, green))
        content.addView(gap(11))
        content.addView(text("Delivery history", 14f, true))
        history.forEachIndexed { index, receipt ->
            val entry = receipt.lines.joinToString(", ") { "${it.itemName} ${fmt(it.receivedQuantity)} ${it.unit}" }
            content.addView(text("${index + 1}. $entry", 12f, false, soft))
        }
        record.updates.forEach { update -> content.addView(text("Bill reference: ${money(update.billAmount)}", 12f, false, soft)) }
        AlertDialog.Builder(activity).setTitle("Delivered Order").setView(ScrollView(activity).apply { addView(content) })
            .setPositiveButton("Close", null).show()
    }
}
