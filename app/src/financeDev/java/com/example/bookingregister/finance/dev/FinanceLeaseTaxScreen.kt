package com.example.bookingregister.finance.dev

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.YearMonth

/** Isolated Finance DEV UI. Lease rent uses existing recurring bills, GST/RCM is only tracked as a separate due. */
internal class FinanceLeaseTaxScreen(
    private val activity: AppCompatActivity,
    private val onChanged: () -> Unit = {}
) {
    private val navy = Color.rgb(28, 43, 58)
    private val green = Color.rgb(13, 108, 81)
    private val muted = Color.rgb(99, 113, 127)
    private val pale = Color.rgb(241, 247, 244)
    private val border = Color.rgb(223, 231, 234)
    private var dialog: Dialog? = null

    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density + 0.5f).toInt()
    private fun col() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun gap(n: Int) = View(activity).apply { layoutParams = LinearLayout.LayoutParams(1, dp(n)) }
    private fun shape(color: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(13).toFloat(); setStroke(dp(1), border)
    }
    private fun text(value: String, size: Float = 14f, bold: Boolean = false, color: Int = navy) = TextView(activity).apply {
        this.text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun button(value: String, primary: Boolean = false, tap: () -> Unit) = text(value, 14f, true,
        if (primary) Color.WHITE else green).apply {
        gravity = Gravity.CENTER
        minHeight = dp(48)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = shape(if (primary) green else pale)
        isClickable = true; isFocusable = true
        setOnClickListener { tap() }
    }
    private fun edit(value: String = "", hintText: String = "", numeric: Boolean = false) = EditText(activity).apply {
        setText(value); hint = hintText; textSize = 15f
        setSingleLine(true)
        background = shape(Color.WHITE)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        if (numeric) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }
    private fun selector(values: List<String>) = Spinner(activity).apply {
        adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, values)
        minimumHeight = dp(48)
        background = shape(Color.WHITE)
    }
    private fun field(target: LinearLayout, title: String, value: View) {
        target.addView(text(title, 12f, true, muted)); target.addView(gap(5))
        target.addView(value, LinearLayout.LayoutParams(-1, dp(50)))
        target.addView(gap(12))
    }
    private fun card() = col().apply {
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = shape(Color.WHITE)
    }
    private fun notify(message: String) = Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    private fun money(paise: Long) = FinanceFixedPolicy.rupees(paise)
    private val modes = listOf("No GST / RCM", "GST payable to landlord", "RCM payable to government")
    private val codes = listOf(FinanceLeaseTaxPolicy.NONE, FinanceLeaseTaxPolicy.LANDLORD_GST, FinanceLeaseTaxPolicy.RCM)
    private val statuses = listOf("GST registered", "Not GST registered", "Verification pending")
    private val statusCodes = listOf(FinanceLeaseTaxPolicy.REGISTERED, FinanceLeaseTaxPolicy.UNREGISTERED, FinanceLeaseTaxPolicy.UNKNOWN)

    fun show() {
        dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar).also { d ->
            d.setOnDismissListener { onChanged() }
            d.show()
        }
        render()
    }

    private fun issue(): Pair<List<FinanceFixedExpenseStore.Bill>, List<FinanceLeaseTaxStore.TaxBill>> {
        val rents = FinanceFixedExpenseStore(activity).use { fixed ->
            fixed.issueThrough(YearMonth.now())
            fixed.bills()
        }
        val taxes = FinanceLeaseTaxStore(activity).use { store ->
            store.issueForBills(rents)
            store.bills()
        }
        return rents to taxes
    }

    private fun render() {
        val d = dialog ?: return
        try {
            val (rents, taxes) = issue()
            val fixed = FinanceFixedExpenseStore(activity).use { it.rules() }
            val tracked = FinanceLeaseTaxStore(activity).use { it.trackedRuleIds() }
            val rentById = rents.associateBy { it.id }
            val paidById = FinanceActivityStore(activity).use { ledger ->
                ledger.synchronize(includeManual = false)
                ledger.bills().associateBy { it.id }
            }
            val outstanding = taxes.sumOf { it.remainingPaise }
            val root = col().apply { setBackgroundColor(Color.rgb(249, 250, 251)) }
            val head = row().apply {
                background = shape(Color.WHITE)
                setPadding(dp(14), dp(15), dp(14), dp(12))
            }
            head.addView(button("‹") { d.dismiss() }, LinearLayout.LayoutParams(dp(48), dp(48)))
            head.addView(text("Lease & Rent", 20f, true), LinearLayout.LayoutParams(0, -2, 1f))
            root.addView(head)
            val scroll = ScrollView(activity).apply { isFillViewport = true }
            val content = col().apply { setPadding(dp(15), dp(12), dp(15), dp(28)) }
            content.addView(card().apply {
                background = shape(pale)
                addView(text("Rent GST / RCM Due", 13f, true, green))
                addView(gap(6))
                addView(text(money(outstanding), 26f, true))
                addView(text("Separately tracked tax obligations · Not a second rent expense", 12f, false, muted))
            })
            content.addView(gap(12))
            content.addView(button("+ Add Monthly Lease", true) { addLease() })
            content.addView(gap(9))
            content.addView(button("Track an Existing Lease") { attachLease() })
            content.addView(gap(16))
            val rules = fixed.filter { it.id in tracked }
            content.addView(text("Lease schedules", 16f, true))
            content.addView(gap(9))
            if (rules.isEmpty()) content.addView(text("No lease tax declarations yet. Add a lease or track an existing rent schedule.", 13f, false, muted))
            rules.forEach { rule ->
                val declaration = FinanceLeaseTaxStore(activity).use { it.latestDeclaration(rule.id) }
                val current = taxes.firstOrNull { it.ruleId == rule.id && it.month == YearMonth.now().toString() }
                val fixedBill = current?.let { rentById[it.rentBillId] }
                val rentRemaining = fixedBill?.let { paidById[it.id]?.due ?: it.remaining } ?: 0L
                val taxRemaining = current?.remainingPaise ?: 0L
                content.addView(card().apply {
                    addView(text(declaration?.property ?: "Property", 15f, true))
                    addView(text(rule.vendor + " · " + rule.type, 12f, false, muted))
                    addView(gap(8))
                    addView(text("Rent due ${money(rentRemaining)}", 13f))
                    addView(text("${when (current?.mode) {
                        FinanceLeaseTaxPolicy.RCM -> "RCM"
                        FinanceLeaseTaxPolicy.LANDLORD_GST -> "Landlord GST"
                        else -> "Tax"
                    }} due ${money(taxRemaining)}", 13f, true))
                    addView(gap(10))
                    addView(button("View monthly dues") { details(rule.id) })
                })
                content.addView(gap(9))
            }
            content.addView(gap(10))
            content.addView(text("Declared tracking only. Confirm tax treatment with your CA. No GST filing or statutory payment is performed.", 11f, false, muted))
            scroll.addView(content)
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            d.setContentView(root)
            d.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        } catch (e: Exception) { notify(e.message ?: "Unable to load lease records") }
    }

    private fun addLease() {
        val vendors = HotelPurchaseVendorStore(activity).all()
        val catalog = FinanceExpenseCatalogLoader.load(activity)
        val rentCategory = catalog.categories.firstOrNull { c ->
            c.name.contains("Lease", true) || c.name.contains("Rent", true)
        }
        val rentTypes = rentCategory?.types?.filter { t ->
            t.name.contains("Lease", true) || t.name.contains("Rent", true)
        }.orEmpty()
        if (vendors.isEmpty()) { notify("Add the landlord in Vendor Master first"); return }
        if (rentCategory == null || rentTypes.isEmpty()) {
            notify("Lease & Rent expense type is missing from the catalog. Restore it in Masters before creating a lease.")
            return
        }
        val form = col().apply { setPadding(dp(15), dp(13), dp(15), dp(10)) }
        val property = edit("", "Property / hotel name")
        val landlord = selector(vendors.map { it.name })
        val rentType = selector(rentTypes.map { it.name })
        val rent = edit("", "Monthly base rent ₹", true)
        val dueDay = edit("5", "Due day (1–31)", true)
        val start = edit(YearMonth.now().toString(), "YYYY-MM")
        field(form, "PROPERTY", property)
        field(form, "LANDLORD", landlord)
        field(form, "LEASE EXPENSE TYPE", rentType)
        field(form, "MONTHLY BASE RENT", rent)
        field(form, "DUE DAY", dueDay)
        field(form, "START MONTH", start)
        val (gstStatus, gstin, taxMode, taxRate) = declarationFields(form)
        form.addView(text("Rent creates one normal recurring expense. GST / RCM is monitored separately as a liability. No tax is assumed paid.", 12f, false, muted))
        val alert = AlertDialog.Builder(activity).setTitle("New Monthly Lease")
            .setView(ScrollView(activity).apply { addView(form) })
            .setNegativeButton("Cancel", null).setPositiveButton("Save Lease", null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val s = statusCodes[gstStatus.selectedItemPosition]
                val m = codes[taxMode.selectedItemPosition]
                val bps = FinanceLeaseTaxPolicy.rateBasisPoints(taxRate.text.toString())
                val month = start.text.toString().trim()
                FinanceLeaseTaxPolicy.validateDeclaration(s, gstin.text.toString(), m, bps, month)
                val base = FinanceFixedPolicy.amount(rent.text.toString())
                val v = vendors[landlord.selectedItemPosition]
                val selectedType = rentTypes[rentType.selectedItemPosition]
                // Fixed rent is authoritative. A metadata-write failure is recoverable using Track Existing Lease.
                val ruleId = FinanceFixedExpenseStore(activity).use { fixed ->
                    fixed.create(rentCategory.code, rentCategory.name, selectedType.code, selectedType.name,
                        v.id, v.name, "Rooms", dueDay.text.toString().toInt(), month, base)
                }
                try {
                    FinanceLeaseTaxStore(activity).use { tax ->
                        tax.declare(ruleId, property.text.toString(), s, gstin.text.toString(),
                            m, bps, month, "Initial declaration by user")
                    }
                } catch (e: Exception) {
                    notify("Rent schedule saved but tax declaration failed. Use 'Track an Existing Lease' to complete: ${e.message}")
                    alert.dismiss(); render(); return@setOnClickListener
                }
                alert.dismiss(); render()
            } catch (e: Exception) { notify(e.message ?: "Check lease details") }
        } }
        alert.show()
    }

    private data class Controls(val status: Spinner, val gstin: EditText, val mode: Spinner, val rate: EditText)

    private fun declarationFields(form: LinearLayout, initial: FinanceLeaseTaxStore.Declaration? = null): Controls {
        val gstStatus = selector(statuses)
        gstStatus.setSelection(statusCodes.indexOf(initial?.landlordStatus ?: FinanceLeaseTaxPolicy.UNKNOWN).coerceAtLeast(0))
        val gstin = edit(initial?.gstin.orEmpty(), "Optional GSTIN, verify later")
        val taxMode = selector(modes)
        taxMode.setSelection(codes.indexOf(initial?.mode ?: FinanceLeaseTaxPolicy.NONE).coerceAtLeast(0))
        val taxRate = edit((initial?.rateBps ?: 1800).let { java.math.BigDecimal.valueOf(it.toLong(), 2).stripTrailingZeros().toPlainString() }, "Declared tax rate %", true)
        field(form, "LANDLORD GST STATUS", gstStatus)
        field(form, "LANDLORD GSTIN (IF KNOWN)", gstin)
        field(form, "TAX TREATMENT (MANUAL DECLARATION)", taxMode)
        field(form, "TAX RATE (%)", taxRate)
        return Controls(gstStatus, gstin, taxMode, taxRate)
    }

    private fun attachLease() {
        val tracked = FinanceLeaseTaxStore(activity).use { it.trackedRuleIds() }
        val candidates = FinanceFixedExpenseStore(activity).use { fixed ->
            fixed.rules().filter { rule ->
                rule.id !in tracked && (rule.type.contains("rent", true) ||
                    rule.type.contains("lease", true) || rule.category.contains("rent", true) || rule.category.contains("lease", true))
            }
        }
        if (candidates.isEmpty()) { notify("No untracked rent schedules found in Fixed Expenses"); return }
        val form = col().apply { setPadding(dp(15), dp(12), dp(15), dp(8)) }
        val ruleSelect = selector(candidates.map { "${it.vendor} · ${it.type} · ${it.startMonth}" })
        val property = edit("", "Property / hotel name")
        val start = edit(YearMonth.now().toString(), "Start tax tracking from YYYY-MM")
        field(form, "EXISTING RENT SCHEDULE", ruleSelect)
        field(form, "PROPERTY", property)
        field(form, "START TAX TRACKING MONTH", start)
        val c = declarationFields(form)
        form.addView(text("Previously issued rent bills remain unchanged. Tax tracking starts from the declared month.", 12f, false, muted))
        val alert = AlertDialog.Builder(activity).setTitle("Track Existing Lease")
            .setView(ScrollView(activity).apply { addView(form) })
            .setNegativeButton("Cancel", null).setPositiveButton("Track Lease", null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val rule = candidates[ruleSelect.selectedItemPosition]
                val startMonth = start.text.toString().trim()
                require(YearMonth.parse(startMonth) >= YearMonth.parse(rule.startMonth)) {
                    "Tax tracking cannot precede lease start"
                }
                val status = statusCodes[c.status.selectedItemPosition]
                val mode = codes[c.mode.selectedItemPosition]
                val bps = FinanceLeaseTaxPolicy.rateBasisPoints(c.rate.text.toString())
                FinanceLeaseTaxStore(activity).use { it.declare(rule.id, property.text.toString(), status,
                    c.gstin.text.toString(), mode, bps, startMonth, "Manual setup of existing lease") }
                alert.dismiss(); render()
            } catch (e: Exception) { notify(e.message ?: "Unable to track lease") }
        } }
        alert.show()
    }

    private fun details(ruleId: String) {
        val fixed = FinanceFixedExpenseStore(activity).use { store ->
            store.issueThrough(YearMonth.now()); store.bills().associateBy { it.id }
        }
        val ledger = FinanceActivityStore(activity).use { store ->
            store.synchronize(includeManual = false); store.bills().associateBy { it.id }
        }
        val rule = FinanceFixedExpenseStore(activity).use { it.rules().firstOrNull { r -> r.id == ruleId } }
            ?: run { notify("Lease no longer available"); return }
        val declarations = FinanceLeaseTaxStore(activity).use { it.declarations(ruleId) }
        val latest = declarations.lastOrNull() ?: run { notify("Declaration not found"); return }
        val taxes = FinanceLeaseTaxStore(activity).use { it.bills(ruleId) }
        val form = col().apply { setPadding(dp(15), dp(12), dp(15), dp(15)) }
        var sheet: AlertDialog? = null
        form.addView(text(latest.property, 17f, true))
        form.addView(text(rule.vendor + " · " + rule.type, 12f, false, muted))
        form.addView(gap(8))
        form.addView(button("Change GST declaration (future months)") { sheet?.dismiss(); revise(ruleId) })
        form.addView(gap(8))
        form.addView(button("View declaration & payment history") { showHistory(ruleId) })
        form.addView(gap(12))
        if (taxes.isEmpty()) form.addView(text("No issued tax months yet.", 13f, false, muted))
        taxes.forEach { tax ->
            val rent = fixed[tax.rentBillId]
            val rentDue = rent?.let { ledger[it.id]?.due ?: it.remaining } ?: 0L
            form.addView(card().apply {
                addView(text(tax.month, 15f, true))
                addView(gap(6))
                addView(text("Rent: ${money(tax.rentPaise)} · Unpaid ${money(rentDue)}", 13f))
                addView(text("${if(tax.mode == FinanceLeaseTaxPolicy.RCM) "RCM" else if(tax.mode == FinanceLeaseTaxPolicy.LANDLORD_GST) "Landlord GST" else "No GST"}: ${money(tax.taxPaise)}", 13f))
                addView(text("Tax balance: ${money(tax.remainingPaise)}", 13f, true,
                    if (tax.remainingPaise > 0L) Color.rgb(175, 77, 18) else green))
                if (rent != null && rentDue > 0L) {
                    addView(gap(8))
                    addView(button("Record Rent Payment") {
                        sheet?.dismiss()
                        FinanceFixedExpensesScreen(activity) { render() }.payBill(rent.id)
                    })
                }
                if (tax.remainingPaise > 0L) {
                    addView(gap(8))
                    addView(button(if (tax.mode == FinanceLeaseTaxPolicy.RCM) "Record RCM Payment" else "Record GST Payment") {
                        sheet?.dismiss()
                        payTax(tax)
                    })
                }
            })
            form.addView(gap(9))
        }
        sheet = AlertDialog.Builder(activity).setTitle("Monthly Rent & Tax Dues")
            .setView(ScrollView(activity).apply { addView(form) })
            .setPositiveButton("Close", null).create()
        sheet.show()
    }

    private fun revise(ruleId: String) {
        val previous = FinanceLeaseTaxStore(activity).use { it.latestDeclaration(ruleId) }
            ?: run { notify("No original declaration"); return }
        val latest = FinanceLeaseTaxStore(activity).use { it.latestIssuedMonth(ruleId) }
        val next = maxOf(YearMonth.now().plusMonths(1), latest?.plusMonths(1) ?: YearMonth.now().plusMonths(1))
        val form = col().apply { setPadding(dp(15), dp(12), dp(15), dp(10)) }
        val property = edit(previous.property, "Hotel name")
        val month = edit(next.toString(), "YYYY-MM")
        field(form, "PROPERTY", property)
        field(form, "EFFECTIVE FROM (FUTURE MONTH)", month)
        val c = declarationFields(form, previous)
        val reason = edit("", "Reason for change")
        field(form, "CHANGE REASON", reason)
        form.addView(text("Previous issued rent and GST/RCM amounts will not change.", 12f, false, muted))
        val alert = AlertDialog.Builder(activity).setTitle("Change Landlord GST Declaration")
            .setView(ScrollView(activity).apply { addView(form) })
            .setNegativeButton("Cancel", null).setPositiveButton("Save Change", null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val effective = month.text.toString().trim()
                require(YearMonth.parse(effective) >= next) { "Choose an unissued future month" }
                FinanceLeaseTaxStore(activity).use { it.declare(ruleId, property.text.toString(),
                    statusCodes[c.status.selectedItemPosition], c.gstin.text.toString(),
                    codes[c.mode.selectedItemPosition], FinanceLeaseTaxPolicy.rateBasisPoints(c.rate.text.toString()),
                    effective, reason.text.toString()) }
                alert.dismiss(); render()
            } catch (e: Exception) { notify(e.message ?: "Unable to save change") }
        } }
        alert.show()
    }

    private fun payTax(tax: FinanceLeaseTaxStore.TaxBill) {
        val form = col().apply { setPadding(dp(15), dp(12), dp(15), dp(10)) }
        val amount = edit(java.math.BigDecimal.valueOf(tax.remainingPaise, 2).toPlainString(), "Amount ₹", true)
        val date = edit(LocalDate.now().toString(), "YYYY-MM-DD")
        val methods = listOf("Bank", "UPI", "Cash", "Other")
        val method = selector(methods)
        val reference = edit("", "Transfer reference or payment note")
        field(form, "ACTUAL AMOUNT PAID", amount)
        field(form, "PAYMENT DATE", date)
        field(form, "METHOD", method)
        field(form, "PAYMENT REFERENCE / REMARK", reference)
        form.addView(text("This records a user-reported payment only. The app does not deposit GST or file a return.", 12f, false, muted))
        val alert = AlertDialog.Builder(activity).setTitle(if (tax.mode == FinanceLeaseTaxPolicy.RCM) "Record RCM Payment" else "Record Landlord GST Payment")
            .setView(ScrollView(activity).apply { addView(form) })
            .setNegativeButton("Cancel", null).setPositiveButton("Record", null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                FinanceLeaseTaxStore(activity).use { it.recordPayment(tax.id,
                    FinanceFixedPolicy.amount(amount.text.toString()), date.text.toString().trim(),
                    methods[method.selectedItemPosition], reference.text.toString()) }
                alert.dismiss(); render()
            } catch (e: Exception) { notify(e.message ?: "Could not record payment") }
        } }
        alert.show()
    }

    private fun showHistory(ruleId: String) {
        val items = FinanceLeaseTaxStore(activity).use { it.history(ruleId) }
        val message = items.joinToString("\n\n") { "${java.time.Instant.ofEpochMilli(it.timestamp)} · ${it.action}\n${it.description}" }
            .ifBlank { "No recorded events" }
        AlertDialog.Builder(activity).setTitle("Lease Tax History")
            .setMessage(message).setPositiveButton("Close", null).show()
    }
}
