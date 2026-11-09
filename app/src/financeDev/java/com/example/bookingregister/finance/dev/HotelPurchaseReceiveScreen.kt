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

    fun show(order: HotelPurchaseOrder, onChanged: () -> Unit = {}) {
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
            issueSummary.text = "${unresolved.size} outstanding items â€¢ ${exceptions.size} marked short / missing"
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
                    text = if (flagged) "Short / Missing âœ“" else "Mark Short"
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
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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
        val history = workflow.events.joinToString("\n") { "${it.action}: ${it.note}" }
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
                appendLine("${document.kind.replace('_', ' ')} ${document.invoiceNumber} â€¢ ${document.invoiceDate} â€¢ Rs ${document.amount}")
            }
            appendLine("Net documented: Rs ${invoices.netAmount(order.id)}")
            appendLine(if (invoices.isVerified(order.id)) "Invoice manually verified" else "Invoice NOT verified")
            appendLine("Invoice entries are evidence only: no expense, GST or payable has been posted.")
        }
        val canApprove = invoices.isVerified(order.id) &&
            (missing.isEmpty() || (workflow.resolution == "CANCELLED_BALANCE" && workflow.resolutionNote.isNotBlank()))
        val options = mutableListOf("Add supplier invoice / credit note", "Verify invoice")
        if (missing.isNotEmpty()) {
            options.add("Record vendor response / corrected invoice")
            options.add("Receive remaining goods")
        }
        if (canApprove) options.add("Approve & Archive")
        options.add("Close")
        AlertDialog.Builder(activity).setTitle("Manager review")
            .setMessage(overview + invoiceSummary + if (canApprove) "\nReady for approval." else "\nApproval locked until receipt issues and invoice verification are resolved.")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Add supplier invoice / credit note" -> addInvoice(order, onChanged)
                    "Verify invoice" -> verifyInvoice(order, onChanged)
                    "Record vendor response / corrected invoice" -> resolve(order, onChanged)
                    "Receive remaining goods" -> show(order, onChanged)
                    "Approve & Archive" -> approve(order, onChanged)
                }
            }.show()
    }

    private fun resolve(order: HotelPurchaseOrder, onChanged: () -> Unit) {
        val note = EditText(activity).apply { hint = "E.g. Vendor agreed to replace invoice #123, shortage cancelled"; minLines = 2 }
        AlertDialog.Builder(activity).setTitle("Vendor confirmed corrected invoice")
            .setMessage("Record the vendor's response and why the remaining quantity is cancelled. Original order and delivery records are preserved.")
            .setView(note).setNegativeButton("Back", null)
            .setPositiveButton("Record resolution") { _, _ ->
                val reason = note.text.toString().trim()
                if (reason.isBlank()) { toast("Write the vendor response / invoice reference"); return@setPositiveButton }
                try {
                    workflows.append(order.id, "CORRECTED_INVOICE_OR_CANCELLED_BALANCE", reason, "CANCELLED_BALANCE")
                    onChanged(); review(order, onChanged)
                } catch (e: Exception) { toast(e.message ?: "Unable to record") }
            }.show()
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
            text = "Documents: ${invoices.forOrder(order.id).size}\nNet documented amount: Rs ${invoices.netAmount(order.id)}\nVerify invoice/credit notes against accepted goods and supplier documents. This is a manual check."
        })
        val checked = CheckBox(activity).apply {
            text = "I verified supplier document numbers, amounts and accepted goods."
        }
        val note = EditText(activity).apply {
            hint = "Verification note / invoice reference"
            minLines = 2
        }
        root.addView(checked); root.addView(note)
        val dialog = AlertDialog.Builder(activity).setTitle("Verify supplier invoice")
            .setView(root).setNegativeButton("Cancel", null).setPositiveButton("Confirm verification", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    require(checked.isChecked) { "Confirm manual invoice checks" }
                    invoices.verify(order, note.text.toString())
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
