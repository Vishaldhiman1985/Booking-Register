package com.example.bookingregister.finance.dev

import android.graphics.Color
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bookingregister.finance.domain.FinanceV21TaxEvidencePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/** User-entered tax DOCUMENT evidence only, never an automatic filing or GST/ITC eligibility assertion. */
internal class FinanceV21TaxEvidenceScreen(private val activity: AppCompatActivity) {
    private val policy = FinanceV21TaxEvidencePolicy
    private val ink = Color.rgb(37,49,62)
    private val warning = Color.rgb(161,92,22)
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density + .5f).toInt()
    private fun column() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16),dp(8),dp(16),dp(12))
    }
    private fun label(message: String, size: Float = 13f, color: Int = ink) = TextView(activity).apply {
        text = message; textSize = size; setTextColor(color)
        setPadding(0,dp(5),0,dp(5))
    }
    private fun field(parent: LinearLayout, hintText: String, initial: String = "", numeric: Boolean = false): EditText =
        EditText(activity).apply {
            hint = hintText; setText(initial)
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            parent.addView(this)
        }
    private fun cash(v: Long) = FinanceFixedPolicy.rupees(v)
    private fun notifyError(error: Throwable) {
        Toast.makeText(activity, error.message ?: "Cannot save evidence", Toast.LENGTH_LONG).show()
    }

    fun show(initialMonth: String) {
        var month = YearMonth.parse(initialMonth)
        val outer = column()
        val heading = label("", 16f)
        outer.addView(heading)
        outer.addView(label("Manual document evidence only. Amounts are not validated against invoices, GSTR, Form 26AS or bank statements. Rent GST/RCM is managed separately; do not duplicate it here.", 12f, warning))
        val nav = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val previous = Button(activity).apply { text = "Earlier"; isAllCaps = false }
        val next = Button(activity).apply { text = "Later"; isAllCaps = false }
        nav.addView(previous, LinearLayout.LayoutParams(0,-2,1f))
        nav.addView(next, LinearLayout.LayoutParams(0,-2,1f))
        outer.addView(nav)
        val add = Button(activity).apply { text = "Record tax document"; isAllCaps = false }
        outer.addView(add)
        val status = label("", 13f)
        outer.addView(status)
        val list = column().apply { setPadding(0,0,0,0) }
        outer.addView(list)
        val dialog = AlertDialog.Builder(activity)
            .setTitle("V21 | Tax Evidence Register")
            .setView(ScrollView(activity).apply { addView(outer) })
            .setPositiveButton("Close", null)
            .create()
        var generation = 0
        fun refresh() {
            heading.text = "Period: $month"
            next.isEnabled = month.isBefore(YearMonth.now())
            list.removeAllViews()
            status.text = "Loading documents..."
            val current = ++generation
            activity.lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { FinanceV21TaxEvidenceStore(activity.applicationContext).use { it.entries(month.toString()) } }
                }
                if (current != generation || !dialog.isShowing || activity.isFinishing) return@launch
                result.fold(onSuccess = { entries ->
                    val active = entries.filterNot { it.cancelled }
                    status.text = "${active.size} active documents | declared ${cash(active.sumOf { it.amountPaise })} | " +
                        "settlement evidence ${cash(active.sumOf { it.settledPaise })}"
                    if (entries.isEmpty()) list.addView(label("No documents recorded for $month"))
                    entries.forEach { entry ->
                        val line = column().apply { setPadding(dp(9),dp(8),dp(9),dp(8)) }
                        line.addView(label("${entry.kind.display} ${if (entry.cancelled) "[CANCELLED]" else ""}",13f))
                        line.addView(label("${entry.property} | ${entry.documentRef} | ${entry.documentDate}",12f))
                        line.addView(label("${entry.party} | Declared ${cash(entry.amountPaise)}",12f))
                        if (entry.kind.payable) line.addView(label(
                            "Recorded settled ${cash(entry.settledPaise)} | open ${cash(entry.openPaise)}",12f))
                        val auditButton = Button(activity).apply {
                            text = "Payments & change history"; isAllCaps = false
                            setOnClickListener { showHistory(entry) { refresh() } }
                        }
                        line.addView(auditButton)
                        if (!entry.cancelled) {
                            if (entry.kind.payable && entry.openPaise > 0) line.addView(Button(activity).apply {
                                text = "Record settlement"; isAllCaps = false
                                setOnClickListener { showSettlement(entry) { refresh() } }
                            })
                            line.addView(Button(activity).apply {
                                text = "Cancel incorrect document (reason required)"; isAllCaps = false
                                setOnClickListener { showCancellation(entry) { refresh() } }
                            })
                        }
                        list.addView(line)
                        list.addView(View(activity).apply {
                            minimumHeight = dp(1); setBackgroundColor(Color.rgb(225,229,233))
                        })
                    }
                }, onFailure = { error -> status.text = "Unable to read register: ${error.message}" })
            }
        }
        previous.setOnClickListener { month = month.minusMonths(1); refresh() }
        next.setOnClickListener { if (month.isBefore(YearMonth.now())) { month = month.plusMonths(1); refresh() } }
        add.setOnClickListener { showInput(month) { refresh() } }
        dialog.show()
        refresh()
    }

    private fun showInput(selectedMonth: YearMonth, afterSave: () -> Unit) {
        val body = column()
        body.addView(label("One document per property, tax type, period and reference. GST / TDS / TCS amounts must be entered from supporting documents, not guessed.",12f,warning))
        val kinds = FinanceV21TaxEvidencePolicy.Kind.values()
        val kind = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, kinds.map { it.display })
        }
        body.addView(kind)
        val property = field(body,"Property / hotel name")
        val party = field(body,"Customer, supplier or OTA")
        val document = field(body,"Invoice / document reference")
        val defaultDay = minOf(selectedMonth.atEndOfMonth(),LocalDate.now()).toString()
        val date = field(body,"Document date YYYY-MM-DD",defaultDay)
        val amount = field(body,"Tax amount in rupees (e.g. 115.50)", numeric = true)
        val gstin = field(body,"Counterparty GSTIN (optional)")
        val section = field(body,"TDS/TCS section (required for TDS/TCS)")
        val notes = field(body,"Explanation / notes (optional)")
        val dialog = AlertDialog.Builder(activity).setTitle("Record tax evidence")
            .setView(ScrollView(activity).apply { addView(body) })
            .setNegativeButton("Cancel",null).setPositiveButton("Record",null).create()
        dialog.setOnShowListener {
            val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            save.setOnClickListener {
                val result = runCatching {
                    val input = FinanceV21TaxEvidencePolicy.Input(kinds[kind.selectedItemPosition],property.text.toString(),
                        date.text.toString().trim(),document.text.toString(),party.text.toString(),
                        policy.rupeesToPaise(amount.text.toString()),gstin.text.toString(),
                        section.text.toString(),notes.text.toString())
                    val period = policy.validate(input)
                    require(period == selectedMonth.toString()) { "Document date must belong to selected month $selectedMonth" }
                    input
                }
                result.fold(onSuccess = { input ->
                    save.isEnabled = false
                    activity.lifecycleScope.launch {
                        val outcome = withContext(Dispatchers.IO) {
                            runCatching { FinanceV21TaxEvidenceStore(activity.applicationContext).use { it.add(input) } }
                        }
                        outcome.fold(onSuccess = {
                            dialog.dismiss(); afterSave()
                        }, onFailure = { save.isEnabled = true; notifyError(it) })
                    }
                }, onFailure = ::notifyError)
            }
        }
        dialog.show()
    }

    private fun showSettlement(entry: FinanceV21TaxEvidenceStore.Entry, afterSave: () -> Unit) {
        val body = column()
        body.addView(label("This records a user-declared payment against this document. No bank transfer or government filing is performed.",12f,warning))
        val amount = field(body,"Payment amount in rupees (max ${cash(entry.openPaise)})",numeric=true)
        val date = field(body,"Payment date YYYY-MM-DD",LocalDate.now().toString())
        val method = field(body,"Method (Bank / UPI / Cash)","Bank")
        val reference = field(body,"Bank / challan reference")
        val dialog = AlertDialog.Builder(activity).setTitle("Record declared settlement")
            .setView(body).setNegativeButton("Cancel",null).setPositiveButton("Record",null).create()
        dialog.setOnShowListener {
            val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            save.setOnClickListener {
                val parsed = runCatching { policy.rupeesToPaise(amount.text.toString()) }
                parsed.fold(onSuccess = { paise ->
                    save.isEnabled = false
                    activity.lifecycleScope.launch {
                        val outcome = withContext(Dispatchers.IO) { runCatching {
                            FinanceV21TaxEvidenceStore(activity.applicationContext).use {
                                it.settle(entry.id,paise,date.text.toString().trim(),method.text.toString(),reference.text.toString())
                            }
                        } }
                        outcome.fold(onSuccess = { dialog.dismiss(); afterSave() },
                            onFailure = { save.isEnabled = true; notifyError(it) })
                    }
                }, onFailure = ::notifyError)
            }
        }
        dialog.show()
    }

    private fun showCancellation(entry: FinanceV21TaxEvidenceStore.Entry, afterSave: () -> Unit) {
        val note = EditText(activity).apply { hint = "Why is this document incorrect? (8+ characters)"; minLines = 2 }
        val dialog = AlertDialog.Builder(activity).setTitle("Cancel tax evidence")
            .setMessage("The entry remains in history. Recorded settlements must be voided first.")
            .setView(note).setNegativeButton("Back",null).setPositiveButton("Cancel document",null).create()
        dialog.setOnShowListener {
            val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            save.setOnClickListener {
                save.isEnabled = false
                activity.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching {
                        FinanceV21TaxEvidenceStore(activity.applicationContext).use { it.cancel(entry.id,note.text.toString()) }
                    } }
                    result.fold(onSuccess = { dialog.dismiss(); afterSave() },
                        onFailure = { save.isEnabled = true; notifyError(it) })
                }
            }
        }
        dialog.show()
    }

    private fun showHistory(entry: FinanceV21TaxEvidenceStore.Entry, afterSave: () -> Unit) {
        val body = column()
        val dialog = AlertDialog.Builder(activity).setTitle("Document evidence history")
            .setView(ScrollView(activity).apply { addView(body) }).setPositiveButton("Close",null).create()
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                FinanceV21TaxEvidenceStore(activity.applicationContext).use { it.settlements(entry.id) to it.history(entry.id) }
            } }
            result.fold(onSuccess = { (payments,events) ->
                if (payments.isEmpty()) body.addView(label("No recorded settlements"))
                payments.forEach { s ->
                    body.addView(label("${s.date}: ${cash(s.amountPaise)} | ${s.reference} ${if (s.voided) "[VOIDED]" else ""}"))
                    if (!s.voided) body.addView(Button(activity).apply {
                        text = "Void this settlement"; isAllCaps = false
                        setOnClickListener { showVoid(s) { dialog.dismiss(); afterSave() } }
                    })
                }
                body.addView(label("Change history",15f))
                events.forEach { e -> body.addView(label("${e.action}: ${e.detail}",12f)) }
            }, onFailure = { body.addView(label("Could not read history: ${it.message}")) })
            if (!activity.isFinishing) dialog.show()
        }
    }

    private fun showVoid(settlement: FinanceV21TaxEvidenceStore.Settlement, afterSave: () -> Unit) {
        val note = EditText(activity).apply { hint = "Reason for correcting this settlement (8+ characters)" }
        val dialog = AlertDialog.Builder(activity).setTitle("Void recorded settlement")
            .setView(note).setNegativeButton("Back",null).setPositiveButton("Void",null).create()
        dialog.setOnShowListener {
            val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            save.setOnClickListener {
                save.isEnabled = false
                activity.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching {
                        FinanceV21TaxEvidenceStore(activity.applicationContext).use { it.voidSettlement(settlement.id,note.text.toString()) }
                    } }
                    result.fold(onSuccess = { dialog.dismiss(); afterSave() },
                        onFailure = { save.isEnabled = true; notifyError(it) })
                }
            }
        }
        dialog.show()
    }
}
