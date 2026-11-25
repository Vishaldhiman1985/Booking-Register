package com.example.bookingregister.finance.dev

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.math.BigDecimal
import java.time.LocalDate
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

/** V25 presentation only. Payables and allocations are held by FinanceActivityStore.
 * GST/RCM stays with the separate existing FinanceLeaseTaxStore payment workflow.
 */
internal class FinanceV25PaymentsDuesScreen(
    private val activity: AppCompatActivity,
    private val onChanged: () -> Unit
) {
    private val dark = Color.rgb(26, 45, 58)
    private val muted = Color.rgb(103, 112, 124)
    private val green = Color.rgb(0, 110, 60)
    private val red = Color.rgb(186, 40, 54)
    private val border = Color.rgb(221, 232, 228)
    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density + .5f).toInt()
    private fun col() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun gap(n: Int) = View(activity).apply { layoutParams = LinearLayout.LayoutParams(1, dp(n)) }
    private fun bg(fill: Int, edge: Int = fill) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(14).toFloat(); setStroke(dp(1), edge)
    }
    private fun label(s: String, size: Float = 13f, bold: Boolean = false, color: Int = dark) =
        TextView(activity).apply { text = s; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD) }
    private fun money(paise: Long) = "₹" + NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = if (paise % 100 == 0L) 0 else 2
    }.format(paise / 100.0)
    private fun card() = col().apply { setPadding(dp(13), dp(13), dp(13), dp(13)); background = bg(Color.WHITE, border) }
    private fun action(title: String, click: () -> Unit) = label(title, 13f, true, Color.WHITE).apply {
        gravity = Gravity.CENTER; minHeight = dp(43); setPadding(dp(13), dp(11), dp(13), dp(11))
        background = bg(green); isClickable = true; isFocusable = true; setOnClickListener { click() }
    }
    private fun field(hint: String, value: String, numeric: Boolean = false) = EditText(activity).apply {
        setText(value); this.hint = hint; textSize = 15f; setSingleLine(true)
        if (numeric) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        background = bg(Color.rgb(248, 250, 249), border)
        setPadding(dp(12), dp(12), dp(12), dp(12))
    }
    private fun amountPaise(raw: String) = BigDecimal(raw.trim().replace(",", "").removePrefix("₹"))
        .multiply(BigDecimal(100)).longValueExact()
    private fun alert(msg: String) = Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
    private fun total(items: Iterable<Long>): Long = items.fold(0L) { sum, n -> Math.addExact(sum, n) }
    private fun eligibleBill(b: FinanceActivityStore.Bill) = b.due > 0L &&
        !LocalDate.parse(b.date).isAfter(LocalDate.now())
    private data class Group(val id: String, val name: String, val bills: List<FinanceActivityStore.Bill>) {
        val due: Long get() = bills.fold(0L) { s, b -> Math.addExact(s, b.due) }
        val staff: Boolean get() = id.startsWith("employee:")
    }

    fun render(parent: LinearLayout, drafts: List<FinanceDemoDraftBill>) {
        parent.removeAllViews()
        try {
            // Issued salary advances must be reconciled before showing the residual salary due.
            FinanceSalaryDueService.reconcileIssuedAdvances(activity)
            val open = FinanceActivityStore(activity).use { ledger ->
                ledger.synchronize(drafts)
                ledger.bills().filter(::eligibleBill)
            }
            val assigned = open.filter { it.vendorId.isNotBlank() && it.vendorId != "no_payee" }
            val unassigned = open.filterNot { it in assigned }
            val groups = assigned.groupBy { it.vendorId }.map { (id, bills) ->
                Group(id, bills.first().vendor, bills)
            }.sortedBy { it.name.lowercase(Locale.ROOT) }
            val tax = FinanceFixedExpenseStore(activity).use { fixed ->
                FinanceLeaseTaxStore(activity).use { taxStore ->
                    taxStore.issueForBills(fixed.bills())
                    taxStore.bills().filter { it.remainingPaise > 0L }
                }
            }
            val payeeDue = total(groups.map { it.due })
            val taxDue = total(tax.map { it.remainingPaise })
            val unassignedDue = total(unassigned.map { it.due })
            // The host activity already displays "Payments & Dues" in the toolbar.
            parent.addView(label("Lifetime unpaid balances · As of today", 12f, false, muted))
            parent.addView(gap(12))
            parent.addView(card().apply {
                addView(label("TOTAL OUTSTANDING", 11f, true, muted))
                addView(gap(5))
                addView(label(money(Math.addExact(Math.addExact(payeeDue, taxDue), unassignedDue)), 27f, true, red))
                addView(label("Vendors & staff ${money(payeeDue)}  ·  GST/RCM ${money(taxDue)}", 11f, false, muted))
                if (unassignedDue > 0L) addView(label("Unassigned payee review ${money(unassignedDue)}", 11f, true, red))
            })
            parent.addView(gap(15))
            parent.addView(label("VENDORS & MANPOWER", 12f, true, muted))
            parent.addView(gap(8))
            if (groups.isEmpty()) parent.addView(label("No open vendor or salary bills.", 13f, false, muted))
            groups.forEach { group ->
                parent.addView(card().apply {
                    addView(row().apply {
                        addView(col().apply {
                            addView(label(group.name, 15f, true))
                            addView(label("${if (group.staff) "Manpower" else "Vendor"} · ${group.bills.size} open bill(s)", 11f, false, muted))
                        }, LinearLayout.LayoutParams(0, -2, 1f))
                        addView(label(money(group.due), 17f, true, red))
                    })
                    addView(gap(8))
                    group.bills.sortedWith(compareBy<FinanceActivityStore.Bill> { it.date }
                        .thenBy { it.dueDate }.thenBy { it.id }).forEach { bill ->
                        addView(row().apply {
                            addView(label("${bill.title} · ${bill.period}", 11f, false, muted),
                                LinearLayout.LayoutParams(0, -2, 1f))
                            addView(label(money(bill.due), 11f, true))
                        })
                        addView(gap(4))
                    }
                    addView(gap(6))
                    addView(action("Pay ${group.name}  ›") { openPaymentDialog(group.id, drafts) })
                })
                parent.addView(gap(9))
            }
            if (unassignedDue > 0L) {
                parent.addView(card().apply {
                    addView(label("Unassigned payee · ${money(unassignedDue)}", 14f, true, red))
                    addView(label("Assign a valid payee in the original bill before recording payment.", 12f, false, muted))
                })
                parent.addView(gap(9))
            }
            if (taxDue > 0L) {
                parent.addView(card().apply {
                    addView(label("Rent GST / RCM", 15f, true))
                    addView(label("${tax.size} outstanding tax obligation(s) · ${money(taxDue)}", 12f, false, muted))
                    addView(gap(8))
                    addView(action("Open tax payment details  ›") {
                        FinanceLeaseTaxScreen(activity, onChanged).show()
                    })
                })
            }
            parent.addView(gap(15))
            parent.addView(label("This records a payment in Finance DEV; it does not transfer money through a bank.",
                11f, false, muted))
            parent.addView(gap(90))
        } catch (e: Exception) {
            parent.addView(label("Payments & Dues need review: ${e.message ?: "Unable to load balances"}", 13f, true, red))
        }
    }

    /** An existing V25 Vendor profile can open the same one-payee payment dialog. */
    fun openPaymentDialog(payeeId: String, drafts: List<FinanceDemoDraftBill>) {
        try {
            FinanceSalaryDueService.reconcileIssuedAdvances(activity)
            val open = FinanceActivityStore(activity).use { ledger ->
                ledger.synchronize(drafts)
                ledger.bills().filter { it.vendorId == payeeId && eligibleBill(it) }
            }
            if (open.isEmpty()) { alert("No outstanding bills for this payee"); return }
            val totalDue = total(open.map { it.due })
            val name = open.first().vendor
            val form = col().apply { setPadding(dp(18), dp(8), dp(18), dp(10)) }
            form.addView(label("${name} · Available to settle ${money(totalDue)}", 13f, true))
            form.addView(gap(12))
            form.addView(label("Payment amount (₹)", 12f, true))
            val amount = field("Amount", BigDecimal.valueOf(totalDue, 2).stripTrailingZeros().toPlainString(), true)
            form.addView(amount); form.addView(gap(10))
            form.addView(label("Actual payment date", 12f, true))
            val date = field("YYYY-MM-DD", LocalDate.now().toString())
            form.addView(date); form.addView(gap(10))
            form.addView(label("Method", 12f, true))
            val methods = listOf("Bank", "UPI", "Cash", "Other")
            val method = Spinner(activity).apply {
                adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, methods)
            }
            form.addView(method); form.addView(gap(10))
            form.addView(label("Reference / remarks", 12f, true))
            val reference = field("Bank reference or remark", "")
            form.addView(reference)
            form.addView(gap(9))
            form.addView(label("The amount will settle the oldest open bills first, keeping each original bill month unchanged.",
                11f, false, muted))
            val requestId = UUID.randomUUID().toString()
            val dlg = AlertDialog.Builder(activity).setTitle("Record payment")
                .setView(ScrollView(activity).apply { addView(form) })
                .setNegativeButton("Cancel", null).setPositiveButton("Review payment", null).create()
            dlg.setOnShowListener {
                dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    try {
                        val paise = amountPaise(amount.text.toString())
                        val paymentDate = LocalDate.parse(date.text.toString().trim())
                        require(!paymentDate.isAfter(LocalDate.now())) { "Future payments cannot be recorded" }
                        val current = FinanceActivityStore(activity).use { ledger ->
                            ledger.synchronize(drafts)
                            ledger.bills().filter { it.vendorId == payeeId && eligibleBill(it) &&
                                !LocalDate.parse(it.date).isAfter(paymentDate) }
                        }
                        val plan = FinancePayeeAllocationPolicy.plan(current.map {
                            FinancePayeeAllocationPolicy.OpenBill(it.id, it.date, it.dueDate, it.due)
                        }, paise)
                        val byId = current.associateBy { it.id }
                        val lines = plan.map { alloc ->
                            val b = byId.getValue(alloc.billId)
                            "${b.period} · ${b.title}: ${money(alloc.amountPaise)}"
                        }
                        val summary = buildString {
                            append("Pay ${money(paise)} to $name?\n\n")
                            append(lines.joinToString("\n"))
                            append("\n\nRemaining due after payment: ${money(totalDue - paise)}")
                        }
                        AlertDialog.Builder(activity).setTitle("Confirm actual payment")
                            .setMessage(summary)
                            .setNegativeButton("Back", null)
                            .setPositiveButton("Record Payment") { _, _ ->
                                try {
                                    FinanceActivityStore(activity).use { ledger ->
                                        ledger.recordPayeePayment(payeeId, paise, paymentDate.toString(),
                                            methods[method.selectedItemPosition], reference.text.toString(), requestId)
                                    }
                                    dlg.dismiss()
                                    alert("Payment recorded · ${money(paise)}")
                                    onChanged()
                                } catch (e: Exception) { alert(e.message ?: "Payment not recorded") }
                            }.show()
                    } catch (e: Exception) { alert(e.message ?: "Review payment amount or date") }
                }
            }
            dlg.show()
        } catch (e: Exception) { alert(e.message ?: "Unable to open payee payment") }
    }
}
