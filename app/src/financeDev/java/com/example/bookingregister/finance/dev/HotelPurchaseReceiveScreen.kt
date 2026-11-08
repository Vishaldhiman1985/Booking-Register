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
        val search = EditText(activity).apply { hint = "Search missing item (e.g. paneer)"; setSingleLine(true) }
        root.addView(search)
        val results = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results)
        val issueList = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        root.addView(issueList)
        fun updateIssues() {
            issueList.removeAllViews()
            issueList.addView(TextView(activity).apply { text = "Short or missing (${exceptions.size})"; textSize = 17f })
            exceptions.toMap().forEach { (id, q) ->
                val line = unresolved.first { it.itemId == id }
                val total = remaining[id] ?: 0.0
                val lineBox = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                lineBox.addView(TextView(activity).apply {
                    text = "${line.itemName}\nPending ${fmt(total)} ${line.unit}"
                }, LinearLayout.LayoutParams(0, -2, 1f))
                val field = EditText(activity).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setSingleLine(true)
                    setText(fmt(q))
                    hint = "Actual received"
                    textSize = 15f
                }
                field.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        exceptions[id] = s.toString().toDoubleOrNull() ?: Double.NaN
                    }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                })
                lineBox.addView(field, LinearLayout.LayoutParams(0, -2, 0.7f))
                lineBox.addView(Button(activity).apply {
                    text = "✕"
                    setOnClickListener { exceptions.remove(id); updateIssues() }
                })
                issueList.addView(lineBox)
            }
        }
        fun updateSearch() {
            results.removeAllViews()
            val q = search.text.toString().trim()
            if (q.isEmpty()) return
            unresolved.filter { it.itemId !in exceptions && it.itemName.contains(q, true) }
                .take(10).forEach { line ->
                    results.addView(Button(activity).apply {
                        text = "+ ${line.itemName} (${fmt(remaining[line.itemId] ?: 0.0)} ${line.unit})"
                        setOnClickListener {
                            exceptions[line.itemId] = 0.0
                            search.setText("")
                            updateIssues()
                        }
                    })
                }
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateSearch() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        updateIssues()
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
        val options = if (missing.isEmpty()) arrayOf("Approve & Archive", "Close")
        else arrayOf("Record vendor response / corrected invoice", "Receive remaining goods", "Approve & Archive", "Close")
        AlertDialog.Builder(activity).setTitle("Manager review").setMessage(overview)
            .setItems(options) { _, which ->
                when (options[which]) {
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
