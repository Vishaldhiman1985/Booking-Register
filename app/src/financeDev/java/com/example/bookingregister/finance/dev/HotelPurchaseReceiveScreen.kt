package com.example.bookingregister.finance.dev

import android.app.Activity
import android.content.Intent
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import java.util.UUID

/** Exceptions-only receiving. All unreported outstanding items are treated as fully delivered. */
class HotelPurchaseReceiveScreen(private val activity: Activity) {
    private val receipts = HotelPurchaseGoodsReceiptStore(activity)
    private val workflows = HotelPurchaseReceivingWorkflowStore(activity)
    private val invoices = HotelPurchaseInvoiceStore(activity)
    private fun fmt(q: Double) = if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString()
    private fun toast(s: String) = Toast.makeText(activity, s, Toast.LENGTH_LONG).show()

    /** Procurement-only shortage resolution. No cancellation is recorded as goods received. */
    fun showShortageOptions(order: HotelPurchaseOrder, onChanged: () -> Unit = {}) {
        if (workflows.get(order.id).archived) { review(order, onChanged); return }
        val existing = receipts.forOrder(order.id)
        if (existing.isEmpty()) { show(order, onChanged); return }
        val balances = HotelPurchaseGoodsReceiptPolicy.remaining(order, existing)
        val missing = order.lines.filter { (balances[it.itemId] ?: 0.0) > 0.000001 }
        if (missing.isEmpty() || workflows.get(order.id).resolution == "CANCELLED_BALANCE") {
            review(order, onChanged)
            return
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(20))
        }
        root.addView(heading("Outstanding items", 19f))
        root.addView(subtitle("${order.vendorName}  •  PO ${order.id.take(8)}"))
        missing.forEach { line ->
            root.addView(itemCard("${line.itemName}", "${fmt(balances[line.itemId] ?: 0.0)} ${line.unit} outstanding"))
        }
        root.addView(subtitle("Choose how to resolve the remaining items."))
        val dialog = AlertDialog.Builder(activity).setTitle("Resolve Shortage")
            .setView(ScrollView(activity).apply { isFillViewport = true; addView(root) })
            .setNegativeButton("Close", null).create()
        fun addAction(label: String, detail: String, action: () -> Unit) {
            root.addView(Button(activity).apply {
                text = label
                isAllCaps = false
                setOnClickListener { dialog.dismiss(); action() }
            })
            root.addView(subtitle(detail))
        }
        addAction("Receive remaining items", "Tick only the goods physically delivered.") {
            showRemainingGoods(order, onChanged)
        }
        addAction("Mark unavailable / buy elsewhere", "Cancel the remaining undelivered items. Keep the original PO and receipts.") {
            resolveUnavailable(order, false, onChanged)
        }
        addAction("Revised invoice / credit note", "Use when the vendor originally billed for goods they did not deliver.") {
            resolveUnavailable(order, true, onChanged)
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density + 0.5f).toInt()
    private fun heading(value: String, size: Float): TextView = TextView(activity).apply {
        text = value; textSize = size
        setTextColor(android.graphics.Color.rgb(27, 44, 54))
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, dp(8), 0, dp(8))
    }
    private fun subtitle(value: String): TextView = TextView(activity).apply {
        text = value; textSize = 13f
        setTextColor(android.graphics.Color.rgb(96, 109, 118))
        setPadding(0, dp(6), 0, dp(12))
    }
    private fun itemCard(name: String, detail: String): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(android.graphics.Color.rgb(243, 248, 246))
        }
        addView(heading(name, 15f))
        addView(subtitle(detail))
    }

    private fun resolveUnavailable(order: HotelPurchaseOrder, revisedRequired: Boolean, onChanged: () -> Unit) {
        val existing = receipts.forOrder(order.id)
        val missing = HotelPurchaseGoodsReceiptPolicy.remaining(order, existing)
        if (missing.values.none { it > 0.000001 }) { review(order, onChanged); return }
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20),dp(12),dp(20),dp(18)) }
        root.addView(subtitle(if (revisedRequired)
            "The original invoice included goods that were not supplied. Record the corrected invoice or credit note afterward."
        else "The vendor cannot supply the outstanding items. They will NOT be treated as received."))
        val reason = EditText(activity).apply {
            hint = "Reason / supplier confirmation (required)"
            minLines = 2
        }
        root.addView(reason)
        val originalInvoiceIncludesMissing = CheckBox(activity).apply {
            text = "The original invoice billed the missing items (correction required)"
            isChecked = revisedRequired
        }
        root.addView(originalInvoiceIncludesMissing)
        val confirmation = CheckBox(activity).apply {
            text = if (revisedRequired)
                "I confirm the original supplier invoice included the undelivered items"
            else "I confirm the vendor will not deliver the remaining items"
        }
        root.addView(confirmation)
        root.addView(subtitle("The final supplier invoice must cover only accepted goods. If the original invoice overbilled, updated invoice or credit-note evidence is mandatory before verification."))
        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (revisedRequired) "Revised invoice required" else "Mark items unavailable")
            .setView(ScrollView(activity).apply { addView(root) })
            .setNegativeButton("Back", null).setPositiveButton("Confirm cancellation", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    require(confirmation.isChecked) { "Please confirm the supplier's position" }
                    val note = reason.text.toString().trim()
                    require(note.length >= 5) { "Enter a meaningful reason (at least 5 characters)" }
                    // Recheck the active PO; never cancel quantities already received.
                    val latest = HotelPurchaseGoodsReceiptPolicy.remaining(order, receipts.forOrder(order.id))
                    require(latest == missing) { "Delivery changed. Reopen the shortage to refresh quantities." }
                    val summary = order.lines.filter { (latest[it.itemId] ?: 0.0) > 0.000001 }
                        .joinToString("; ") { "${it.itemName}: ${fmt(latest[it.itemId] ?: 0.0)} ${it.unit}" }
                    val needsCorrection = revisedRequired || originalInvoiceIncludesMissing.isChecked
                    workflows.append(order.id,
                        if (needsCorrection) "REVISED_DOCUMENT_REQUIRED" else "UNAVAILABLE_BALANCE_CANCELLED",
                        "$summary. Reason: $note", "CANCELLED_BALANCE")
                    dialog.dismiss(); onChanged()
                    if (needsCorrection) {
                        AlertDialog.Builder(activity).setTitle("Record corrected invoice")
                            .setMessage("Shortage cancelled, but manager approval is blocked until you add a new supplier invoice or credit note and verify the documents.")
                            .setPositiveButton("Add document") { _, _ -> addInvoice(order, onChanged) }
                            .setNegativeButton("Later", null).show()
                    } else {
                        review(order, onChanged)
                    }
                } catch (e: Exception) { toast(e.message ?: "Unable to cancel outstanding items") }
            }
        }
        dialog.show()
    }

    fun show(order: HotelPurchaseOrder, onChanged: () -> Unit = {}) {
        if (workflows.get(order.id).resolution == "CANCELLED_BALANCE") { review(order, onChanged); return }
        if (workflows.get(order.id).archived) { review(order, onChanged); return }
        val prior = receipts.forOrder(order.id)
        val remaining = HotelPurchaseGoodsReceiptPolicy.remaining(order, prior)
        if (remaining.values.all { it <= 0.000001 }) { review(order, onChanged); return }
        val unresolved = order.lines.filter { (remaining[it.itemId] ?: 0.0) > 0.000001 }
        val exceptions = linkedMapOf<String, Double>()
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 14, 24, 14) }
        root.addView(TextView(activity).apply { text = "${order.vendorName} • ${unresolved.size} outstanding items"; textSize = 17f })
        root.addView(TextView(activity).apply {
            text = "ONLY report missing or short items. All other items will be marked fully received."
            textSize = 14f
            setPadding(0, 12, 0, 14)
        })
        // Show every outstanding line: staff flag ONLY shortages. All other lines
        // are counted as fully received after explicit confirmation below.
        val search = EditText(activity).apply {
            hint = "Search ordered items"
            setSingleLine(true)
            textSize = 14f
        }
        root.addView(search)
        val issueSummary = TextView(activity).apply {
            textSize = 14f
            setPadding(0, 10, 0, 8)
        }
        root.addView(issueSummary)
        val results = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results)

        fun updateItems() {
            results.removeAllViews()
            issueSummary.text = "${unresolved.size} outstanding items - ${exceptions.size} marked short / missing"
            val query = search.text.toString().trim()
            val visible = unresolved.filter { query.isEmpty() || it.itemName.contains(query, ignoreCase = true) }
            if (visible.isEmpty()) {
                results.addView(TextView(activity).apply { text = "No matching ordered items"; setPadding(0, 12, 0, 12) })
            }
            visible.forEach { line ->
                val id = line.itemId
                val total = remaining[id] ?: 0.0
                val flagged = exceptions.containsKey(id)
                val itemBox = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 7, 0, 7)
                }
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(TextView(activity).apply {
                    text = "${line.itemName}\n${fmt(total)} ${line.unit} outstanding"
                    textSize = 14f
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Button(activity).apply {
                    text = if (flagged) "Short / Missing (selected)" else "Mark Short"
                    isAllCaps = false
                    textSize = 12f
                    if (flagged) backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(255, 225, 187))
                    setOnClickListener {
                        if (exceptions.containsKey(id)) exceptions.remove(id)
                        else exceptions[id] = 0.0
                        updateItems()
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                itemBox.addView(row)
                if (flagged) {
                    val quantityRow = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    quantityRow.addView(TextView(activity).apply {
                        text = "Actual received (${line.unit})"
                        textSize = 13f
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    val amount = EditText(activity).apply {
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        setSingleLine(true)
                        textSize = 15f
                        setSelectAllOnFocus(true)
                        setText(fmt(exceptions[id] ?: 0.0))
                        setOnClickListener { selectAll() }
                    }
                    amount.addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            exceptions[id] = s.toString().toDoubleOrNull() ?: Double.NaN
                        }
                        override fun afterTextChanged(s: android.text.Editable?) {}
                    })
                    quantityRow.addView(amount, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.55f))
                    itemBox.addView(quantityRow)
                }
                results.addView(itemBox)
                results.addView(android.view.View(activity).apply {
                    setBackgroundColor(android.graphics.Color.rgb(232, 235, 238))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            }
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateItems() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        updateItems()
        val note = EditText(activity).apply { hint = "Optional note for vendor"; minLines = 2 }
        root.addView(note)
        root.addView(Button(activity).apply {
            text = "Nothing delivered / delivery failed"
            isAllCaps = false
            setOnClickListener { failedDelivery(order, onChanged) }
        })
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Receive Goods")
            .setView(ScrollView(activity).apply { addView(root) })
            .setNegativeButton("Back", null)
            .setPositiveButton("Review", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    exceptions.forEach { (id, q) ->
                        require(q.isFinite() && q >= 0 && q < (remaining[id] ?: 0.0)) {
                            "Enter an actual quantity smaller than ordered for ${unresolved.first { it.itemId == id }.itemName}"
                        }
                    }
                    val entry = HotelPurchaseGoodsReceipt(
                        id = UUID.randomUUID().toString(),
                        orderId = order.id,
                        receivedAtMillis = System.currentTimeMillis(),
                        lines = unresolved.map { line ->
                            HotelPurchaseGoodsReceiptLine(
                                line.itemId, line.itemName, line.unit,
                                exceptions[line.itemId] ?: (remaining[line.itemId] ?: 0.0)
                            )
                        }, note = note.text.toString().trim()
                    )
                    val missing = unresolved.filter { it.itemId in exceptions }
                        .map { line ->
                            "${line.itemName}: short ${fmt((remaining[line.itemId] ?: 0.0) - (exceptions[line.itemId] ?: 0.0))} ${line.unit}"
                        }
                    val summary = if (missing.isEmpty()) "All outstanding goods received." else missing.joinToString("\n")
                    // The existing receipt policy deliberately prevents a completely empty delivery.
                    require(entry.lines.any { it.receivedQuantity > 0 }) {
                        "Nothing delivered. Record the shortage with the vendor before saving a goods receipt."
                    }
                    HotelPurchaseGoodsReceiptPolicy.validate(order, prior, entry)
                    AlertDialog.Builder(activity)
                        .setTitle("Confirm counted goods")
                        .setMessage("All unreported items will be marked fully received.\n\n$summary\n\nRecord this delivery? Expenses remain manual.")
                        .setNegativeButton("Back", null)
                        .setPositiveButton("Record Delivery") { _, _ ->
                            try {
                                receipts.add(order, entry)
                                if (missing.isNotEmpty()) {
                                    workflows.append(order.id, "SHORTAGE_RECORDED", missing.joinToString("; ") + if (entry.note.isBlank()) "" else "; Note: ${entry.note}", "OPEN")
                                }
                                dialog.dismiss()
                                onChanged()
                                if (missing.isNotEmpty()) offerIssue(order, missing, entry.note, onChanged)
                                else review(order, onChanged)
                            } catch (e: Exception) { toast(e.message ?: "Save failed") }
                        }.show()
                } catch (e: Exception) { toast(e.message ?: "Check entered quantities") }
            }
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
            (activity.resources.displayMetrics.heightPixels * 0.84f).toInt())
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    /** Receive previously missing goods without rewriting any prior goods receipt. */
    fun showRemainingGoods(order: HotelPurchaseOrder, onChanged: () -> Unit = {}) {
        if (workflows.get(order.id).resolution == "CANCELLED_BALANCE") { review(order, onChanged); return }
        if (workflows.get(order.id).archived) { review(order, onChanged); return }
        val prior = receipts.forOrder(order.id)
        val outstanding = try {
            HotelPurchaseRemainingGoodsPolicy.outstanding(order, prior)
        } catch (e: Exception) {
            toast(e.message ?: "Cannot load outstanding goods"); return
        }
        if (outstanding.isEmpty()) { review(order, onChanged); return }
        val balances = HotelPurchaseGoodsReceiptPolicy.remaining(order, prior)
        val selected = linkedSetOf<String>()
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 14, 28, 14)
        }
        root.addView(TextView(activity).apply {
            text = "${order.vendorName} • PO ${order.id.take(8)}"
            textSize = 16f
        })
        root.addView(TextView(activity).apply {
            text = "Tick only the items physically received now. Each tick records the full remaining quantity shown below. Unticked items stay pending."
            textSize = 13f
            setPadding(0, 10, 0, 14)
        })
        val checks = outstanding.map { item ->
            CheckBox(activity).apply {
                text = "${item.itemName}  •  ${fmt(balances[item.itemId] ?: 0.0)} ${item.unit}"
                textSize = 15f
                setPadding(0, 6, 0, 6)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(item.itemId) else selected.remove(item.itemId)
                }
                root.addView(this)
            }
        }
        root.addView(Button(activity).apply {
            text = "Select all outstanding items"
            isAllCaps = false
            setOnClickListener { checks.forEach { it.isChecked = true } }
        })
        val note = EditText(activity).apply {
            hint = "Optional delivery note / challan number"
            minLines = 2
        }
        root.addView(note)
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Receive Remaining Goods")
            .setView(ScrollView(activity).apply { addView(root) })
            .setNegativeButton("Back", null)
            .setPositiveButton("Record Received", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    // Re-read at save time to reject stale quantities or new receipts.
                    val current = receipts.forOrder(order.id)
                    val receipt = HotelPurchaseRemainingGoodsPolicy.createSupplementaryReceipt(
                        order, current, selected.toSet(), UUID.randomUUID().toString(),
                        System.currentTimeMillis(), note.text.toString()
                    )
                    val linesSummary = receipt.lines.joinToString("\n") {
                        "${it.itemName}: ${fmt(it.receivedQuantity)} ${it.unit}"
                    }
                    AlertDialog.Builder(activity)
                        .setTitle("Confirm physical receipt")
                        .setMessage("Record only these received quantities?\n\n$linesSummary\n\nEarlier deliveries will not change.")
                        .setNegativeButton("Back", null)
                        .setPositiveButton("Confirm Received") { _, _ ->
                            try {
                                // Goods first. If audit append fails the receipt is still preserved;
                                // its own immutable timestamp and contents remain in receipt history.
                                receipts.add(order, receipt)
                                val newHistory = receipts.forOrder(order.id)
                                val fullyReceived = HotelPurchaseRemainingGoodsPolicy.isFullyDelivered(order, newHistory)
                                try {
                                    workflows.append(order.id, "REMAINING_GOODS_RECEIVED", linesSummary.replace("\n", "; "))
                                    if (fullyReceived) {
                                        workflows.append(order.id, "DELIVERY_COMPLETED", "All ordered quantities have been physically received", "OPEN")
                                    }
                                } catch (auditError: Exception) {
                                    toast("Receipt saved; activity timeline update needs attention")
                                }
                                dialog.dismiss()
                                onChanged()
                                if (fullyReceived) {
                                    AlertDialog.Builder(activity)
                                        .setTitle("Delivery complete")
                                        .setMessage("All goods received. Supplier invoice verification and manager approval are still required before archiving.")
                                        .setPositiveButton("Manager Review") { _, _ -> review(order, onChanged) }
                                        .setNegativeButton("Later", null)
                                        .show()
                                } else {
                                    toast("Received items recorded. Other shortages remain pending.")
                                }
                            } catch (e: Exception) {
                                toast(e.message ?: "Unable to record supplementary delivery")
                            }
                        }.show()
                } catch (e: Exception) { toast(e.message ?: "Check selected items") }
            }
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
            (activity.resources.displayMetrics.heightPixels * 0.84f).toInt())
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun failedDelivery(order: HotelPurchaseOrder, onChanged: () -> Unit) {
        val reason = EditText(activity).apply {
            hint = "Why was nothing delivered? (required)"
            minLines = 2
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Record failed delivery attempt")
            .setMessage("This records an issue only. No goods receipt, stock or expense will be created.")
            .setView(reason)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Record attempt", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    require(order.status == "PLACED") { "Only placed orders can record a failed delivery" }
                    require(reason.text.toString().trim().length >= 5) { "Enter a meaningful reason" }
                    workflows.append(order.id, "DELIVERY_ATTEMPT_FAILED", reason.text.toString().trim())
                    dialog.dismiss()
                    toast("Failed delivery recorded. No goods were received.")
                    onChanged()
                } catch (e: Exception) { toast(e.message ?: "Could not record failed delivery") }
            }
        }
        dialog.show()
    }

    private fun offerIssue(order: HotelPurchaseOrder, missing: List<String>, note: String, onChanged: () -> Unit) {
        val message = buildString {
            appendLine("DELIVERY SHORTAGE • PO ${order.id.take(8)}")
            appendLine("Vendor: ${order.vendorName}")
            missing.forEach { appendLine("• $it") }
            appendLine("Please confirm delivery of the missing goods or provide a corrected invoice.")
            if (note.isNotBlank()) appendLine("Note: $note")
        }
        AlertDialog.Builder(activity).setTitle("Report shortage to vendor")
            .setMessage(message)
            .setNegativeButton("Later") { _, _ -> review(order, onChanged) }
            .setPositiveButton("Share message") { _, _ ->
                try {
                    workflows.append(order.id, "MESSAGE_PREPARED", message)
                    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message) }
                    activity.startActivity(Intent.createChooser(intent, "Share vendor shortage"))
                } catch (e: Exception) { toast("Unable to open sharing app") }
                review(order, onChanged)
            }.show()
    }

    private fun review(order: HotelPurchaseOrder, onChanged: () -> Unit) {
        val prior = receipts.forOrder(order.id)
        val remaining = HotelPurchaseGoodsReceiptPolicy.remaining(order, prior)
        val missing = order.lines.mapNotNull { line ->
            val q = remaining[line.itemId] ?: 0.0
            if (q > 0.000001) "${line.itemName}: ${fmt(q)} ${line.unit}" else null
        }
        val workflow = workflows.get(order.id)
        val history = workflow.events.joinToString("\n") { "${when (it.action) { "SHORTAGE_RECORDED" -> "Shortage recorded"; "REMAINING_GOODS_RECEIVED" -> "Remaining goods received"; "DELIVERY_COMPLETED" -> "Delivery completed"; "MESSAGE_PREPARED" -> "Vendor message prepared"; "DELIVERY_ATTEMPT_FAILED" -> "Delivery failed"; "INVOICE_RECORDED" -> "Supplier document added"; "INVOICE_VERIFIED" -> "Invoice verified"; "APPROVED" -> "Approved"; else -> it.action.replace('_', ' ').lowercase().replaceFirstChar { c -> c.uppercase() } }}: ${it.note}" }
        val overview = "${order.vendorName}\n${prior.size} delivery record(s)\n\n" +
            (if (missing.isEmpty()) "All quantities received" else "Pending shortages:\n${missing.joinToString("\n")}") +
            (if (history.isBlank()) "" else "\n\nIssue history:\n$history")
        if (workflow.archived) {
            AlertDialog.Builder(activity).setTitle("Archived Order").setMessage(overview).setPositiveButton("Close", null).show()
            return
        }
        val docs = invoices.forOrder(order.id)
        val invoiceSummary = buildString {
            appendLine("\nINVOICES (${docs.size})")
            if (docs.isEmpty()) appendLine("No invoice documents recorded")
            docs.forEach { document ->
                appendLine("${document.kind.replace('_', ' ')} ${document.invoiceNumber} - ${document.invoiceDate} - Rs ${document.amount}")
            }
            appendLine("Net documented: Rs ${invoices.netAmount(order.id)}")
            appendLine(if (invoices.isVerifiedFor(order.id, prior)) "Invoice manually reconciled (not automated price matching)" else "Invoice reconciliation required")
            appendLine("Invoice entries are evidence only: no expense, GST or payable has been posted.")
        }
        val correctionEvidenceReady = runCatching {
            HotelPurchaseShortageInvoicePolicy.requireCurrentDocuments(workflow, docs)
        }.isSuccess
        val canApprove = correctionEvidenceReady && invoices.isVerifiedFor(order.id, prior) &&
            (missing.isEmpty() || (workflow.resolution == "CANCELLED_BALANCE" && workflow.resolutionNote.isNotBlank()))
        val options = mutableListOf("Add supplier invoice / credit note", "Verify invoice")
        if (missing.isNotEmpty()) {
            options.add("Resolve shortage: unavailable or revised invoice")
            options.add("Receive remaining goods")
        }
        if (canApprove) options.add("Approve & Archive")
        options.add("Close")
        // A single scrollable panel prevents Android AlertDialog's list/message layout
        // from clipping the manager actions on smaller phones.
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(18))
        }
        panel.addView(heading("Delivery & invoice", 18f))
        val shortSummary = if (missing.isEmpty()) "Goods delivery accounted for" else
            "Outstanding: ${missing.joinToString("; ")}"
        panel.addView(itemCard(order.vendorName, shortSummary))
        panel.addView(subtitle("${prior.size} physical delivery record(s)  •  ${docs.size} supplier document(s)"))
        panel.addView(heading(if (canApprove) "Ready for approval" else "Needs reconciliation", 16f))
        panel.addView(subtitle(if (canApprove) "All current checks passed. You may archive the order." else
            "Resolve remaining quantity differences and verify supplier documents before approval."))
        panel.addView(heading("Activity", 16f))
        if (workflow.events.isEmpty()) panel.addView(subtitle("No activity recorded yet"))
        else workflow.events.takeLast(12).forEach { e ->
            val label = e.action.replace('_', ' ').lowercase()
                .replaceFirstChar { it.uppercase() }
            panel.addView(subtitle("$label  •  ${e.note.take(110)}"))
        }
        panel.addView(heading("Supplier documents", 16f))
        panel.addView(subtitle(if (docs.isEmpty()) "No invoice recorded" else
            docs.joinToString("\n") { "${it.kind.replace('_',' ')} ${it.invoiceNumber}  •  Rs ${it.amount}" }))
        panel.addView(subtitle("Net documented: Rs ${invoices.netAmount(order.id)}. Amount verification is manual, not automatic."))
        panel.addView(heading("Next action", 16f))
        val dialog = AlertDialog.Builder(activity).setTitle("Manager review")
            .setView(ScrollView(activity).apply { addView(panel); isFillViewport = false })
            .setNegativeButton("Close", null).create()
        fun action(label: String, operation: () -> Unit) {
            panel.addView(Button(activity).apply {
                text = label
                isAllCaps = false
                textSize = 14f
                setOnClickListener { dialog.dismiss(); operation() }
            })
        }
        action("Add invoice / credit note") { addInvoice(order, onChanged) }
        action("Verify supplier invoice") { verifyInvoice(order, onChanged) }
        if (missing.isNotEmpty() && workflow.resolution != "CANCELLED_BALANCE") {
            action("Resolve shortage") { showShortageOptions(order, onChanged) }
        }
        if (canApprove) action("Approve and archive") { approve(order, onChanged) }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
            (activity.resources.displayMetrics.heightPixels * 0.84f).toInt())
    }

    private fun addInvoice(order: HotelPurchaseOrder, onChanged: () -> Unit) {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 12)
        }
        val kind = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Supplier invoice", "Supplier credit note"))
        }
        val number = EditText(activity).apply { hint = "Supplier invoice / credit note number"; setSingleLine(true) }
        val date = EditText(activity).apply { hint = "Date YYYY-MM-DD"; setSingleLine(true) }
        val amount = EditText(activity).apply {
            hint = "Total document amount (Rs)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
        }
        root.addView(TextView(activity).apply { text = "Document type" })
        root.addView(kind); root.addView(number); root.addView(date); root.addView(amount)
        root.addView(TextView(activity).apply {
            text = "Record every supplier invoice or credit note under this PO. Keep the original documents separately. No accounting entry is created here."
        })
        val dialog = AlertDialog.Builder(activity).setTitle("Supplier invoice")
            .setView(root).setNegativeButton("Cancel", null).setPositiveButton("Save document", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    invoices.add(order, number.text.toString(), date.text.toString().trim(),
                        amount.text.toString().toDoubleOrNull() ?: Double.NaN,
                        if (kind.selectedItemPosition == 0) "INVOICE" else "CREDIT_NOTE")
                    workflows.append(order.id, "INVOICE_RECORDED", "Supplier document ${number.text.toString().trim()}")
                    dialog.dismiss(); onChanged(); review(order, onChanged)
                } catch (e: Exception) { toast(e.message ?: "Unable to record invoice") }
            }
        }
        dialog.show()
    }

    private fun verifyInvoice(order: HotelPurchaseOrder, onChanged: () -> Unit) {
        if (invoices.forOrder(order.id).isEmpty()) { toast("Add a supplier invoice first"); review(order, onChanged); return }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 12)
        }
        root.addView(TextView(activity).apply {
            text = "Documents: ${invoices.forOrder(order.id).size}\nNet documented amount: Rs ${invoices.netAmount(order.id)}\nManually reconcile accepted goods, supplier documents and amounts. Purchase orders do not yet contain prices, so amount matching is NOT automatic."
        })
        val goodsChecked = CheckBox(activity).apply {
            text = "Accepted quantities match goods-receipt records; shortages are resolved"
        }
        val amountsChecked = CheckBox(activity).apply {
            text = "I compared supplier invoice amounts with the actual supplier documents"
        }
        val documentsChecked = CheckBox(activity).apply {
            text = "Supplier name, invoice numbers, dates and credit notes are correct"
        }
        val note = EditText(activity).apply {
            hint = "Verification note / invoice reference"
            minLines = 2
        }
        root.addView(goodsChecked)
        root.addView(amountsChecked)
        root.addView(documentsChecked)
        root.addView(note)
        val dialog = AlertDialog.Builder(activity).setTitle("Verify supplier invoice")
            .setView(root).setNegativeButton("Cancel", null).setPositiveButton("Confirm verification", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val priorReceipts = receipts.forOrder(order.id)
                    val remaining = HotelPurchaseGoodsReceiptPolicy.remaining(order, priorReceipts)
                    val workflow = workflows.get(order.id)
                    val shortageResolved = workflow.resolution == "CANCELLED_BALANCE" && workflow.resolutionNote.isNotBlank()
                    HotelPurchaseShortageInvoicePolicy.requireCurrentDocuments(workflow, invoices.forOrder(order.id))
                    require(remaining.values.all { it <= 0.000001 } || shortageResolved) {
                        "Resolve shortages before verifying this invoice"
                    }
                    invoices.verify(
                        order, note.text.toString(), priorReceipts,
                        goodsChecked.isChecked, amountsChecked.isChecked,
                        documentsChecked.isChecked, shortageResolved
                    )
                    workflows.append(order.id, "INVOICE_VERIFIED", note.text.toString().trim())
                    dialog.dismiss(); onChanged(); review(order, onChanged)
                } catch (e: Exception) { toast(e.message ?: "Verification failed") }
            }
        }
        dialog.show()
    }
    private fun approve(order: HotelPurchaseOrder, onChanged: () -> Unit) {
        AlertDialog.Builder(activity).setTitle("Approve goods receipt & archive?")
            .setMessage("This moves the order to Archive. Purchasing records are retained. No expense, GST or payment entry will be created.")
            .setNegativeButton("Back", null)
            .setPositiveButton("Approve") { _, _ ->
                try {
                    workflows.approve(order, receipts.forOrder(order.id))
                    toast("Order approved and archived")
                    onChanged()
                } catch (e: Exception) { toast(e.message ?: "Approval blocked") }
            }.show()
    }
}
