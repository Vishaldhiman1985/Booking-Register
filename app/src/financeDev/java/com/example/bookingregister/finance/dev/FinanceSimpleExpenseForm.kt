package com.example.bookingregister.finance.dev

import android.app.Activity
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.bookingregister.finance.domain.FinanceGstBillNote
import java.time.LocalDate

/**
 * V19 Finance DEV quick expense entry. Uses the existing audited local draft save callback;
 * no database migration, stock receipt, tax computation or Booking Register integration.
 */
internal class FinanceSimpleExpenseForm(
    private val activity: Activity,
    private val catalog: SmartExpenseCatalog,
    private val visibility: FinanceExpenseVisibility,
    private val stockSettings: FinanceGlobalCatalogSettings,
    private val vendors: List<FinanceDemoDraftPolicy.VendorOption>
) {
    private val ink = Color.rgb(29, 43, 59)
    private val muted = Color.rgb(103, 114, 128)
    private val accent = Color.rgb(18, 98, 76)
    private val line = Color.rgb(228, 233, 238)

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density + .5f).toInt()
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String, size: Float = 13f, strong: Boolean = false, color: Int = muted) =
        TextView(activity).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (strong) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
    private fun gap(height: Int) = View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }
    private fun field(parent: LinearLayout, title: String, control: View) {
        parent.addView(text(title, 12f, true, ink))
        parent.addView(gap(4))
        parent.addView(control, LinearLayout.LayoutParams(-1, -2))
        parent.addView(gap(12))
    }
    private fun spinner(values: List<String>) = Spinner(activity).apply {
        adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, values)
        minimumHeight = dp(48)
    }
    private fun edit(hint: String, original: String = "", multiline: Boolean = false) = EditText(activity).apply {
        this.hint = hint
        setText(original)
        textSize = 15f
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), line)
        }
        setPadding(dp(12), dp(10), dp(12), dp(10))
        if (multiline) {
            minLines = 2
            maxLines = 5
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        } else {
            setSingleLine(true)
        }
    }
    private fun link(title: String, action: () -> Unit) = text(title, 13f, true, accent).apply {
        setPadding(0, dp(10), 0, dp(10))
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }
    private fun notice(message: String) = text(message, 12f).apply {
        setPadding(dp(12), dp(9), dp(12), dp(9))
        background = GradientDrawable().apply {
            setColor(Color.rgb(243, 247, 247))
            cornerRadius = dp(10).toFloat()
        }
    }
    private fun warn(message: String) = Toast.makeText(activity, message, Toast.LENGTH_LONG).show()

    fun show(
        existing: FinanceDemoDraftBill?,
        initialSelection: Pair<SmartExpenseCategory, SmartExpenseType>?,
        onSave: (FinanceDemoDraftPolicy.DraftInput, String) -> Unit
    ) {
        val previousType = initialSelection ?: existing?.expenseTypeCode?.let(catalog::findType)
        if (previousType?.first?.code == FinanceExpenseCategoryRules.STAFF_ONLY) {
            warn("Manage staff expenses in Manpower. Existing records are preserved.")
            return
        }
        val propertyId = existing?.propertyId ?: FinanceDemoDraftPolicy.PROPERTY
        val vendorCategoryPrefs = FinanceVendorExpenseCategories(activity, propertyId)
        val parsedNotes = FinanceGstBillNote.read(existing?.notes.orEmpty())
        val legacyVendor = existing?.vendorCode?.takeIf { code ->
            FinanceDemoDraftPolicy.vendors.any { it.code == code } && vendors.none { it.code == code }
        }?.let { code -> FinanceDemoDraftPolicy.vendors.firstOrNull { it.code == code } }
        val vendorOptions = vendors + listOfNotNull(legacyVendor)
        val payeeCodes = listOf("no_payee", "one_time") + vendorOptions.map { it.code }
        val selectedVendorIndex = payeeCodes.indexOfFirst { it == existing?.vendorCode }.let { index ->
            if (existing?.vendorCode?.startsWith("one_time:") == true) 1 else index.coerceAtLeast(0)
        }

        val form = column().apply { setPadding(dp(16), dp(9), dp(16), dp(20)) }
        val typePicker = spinner(listOf("Select expense type"))
        val vendor = spinner(listOf("No vendor / payee", "One-time supplier") + vendorOptions.map { it.name })
        vendor.setSelection(selectedVendorIndex)
        val oneTimeName = edit("Supplier name", existing?.vendorCode
            ?.takeIf { it.startsWith("one_time:") }?.removePrefix("one_time:").orEmpty())
        val amount = edit("0.00", existing?.let {
            FinanceDemoDraftPolicy.moneyText(it.amountPaise).removePrefix("₹")
        }.orEmpty()).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val gstChoices = listOf("Not recorded", "Yes — GST invoice", "No — no GST invoice")
        val gstStatuses = listOf(FinanceGstBillNote.Status.NOT_RECORDED,
            FinanceGstBillNote.Status.YES, FinanceGstBillNote.Status.NO)
        val gst = spinner(gstChoices)
        gst.setSelection(gstStatuses.indexOf(parsedNotes.status).coerceAtLeast(0))
        val statuses = FinanceDemoDraftPolicy.PaymentStatus.entries
        val payment = spinner(statuses.map { it.label })
        payment.setSelection(statuses.indexOfFirst { it.name == existing?.paymentStatus }.coerceAtLeast(0))
        val paid = edit("Amount already paid", existing?.let {
            FinanceDemoDraftPolicy.moneyText(it.amountPaidPaise).removePrefix("₹")
        }.orEmpty()).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }

        // The CA category and department are retained, but auto-selected from the expense type.
        val departmentOptions = FinanceGlobalCatalogDefaults.departments
        val department = spinner(departmentOptions)
        val date = edit("YYYY-MM-DD", existing?.invoiceDate ?: LocalDate.now().toString()).apply {
            isFocusable = false
            isClickable = true
            setOnClickListener {
                val day = runCatching { LocalDate.parse(text.toString()) }.getOrDefault(LocalDate.now())
                DatePickerDialog(activity, { _, y, m, d ->
                    setText(LocalDate.of(y, m + 1, d).toString())
                }, day.year, day.monthValue - 1, day.dayOfMonth).show()
            }
        }
        val notes = edit("Invoice number / optional remarks", parsedNotes.notes, multiline = true)
        val stockMessage = notice("")
        val selectionHelp = notice("Select an expense type to continue.")
        var choices: List<Pair<SmartExpenseCategory, SmartExpenseType>> = emptyList()
        var chosenCode: String? = null

        fun selectedType() = choices.getOrNull(typePicker.selectedItemPosition - 1)
        fun updateTypeHint() {
            val selected = selectedType()
            val config = selected?.second?.let { stockSettings.forType(it.code) }
            if (selected == null || config == null) {
                stockMessage.visibility = View.GONE
                return
            }
            if (chosenCode != selected.second.code) {
                chosenCode = selected.second.code
                val dept = if (existing?.expenseTypeCode == selected.second.code &&
                    existing.department in departmentOptions) existing.department else config.department
                department.setSelection(departmentOptions.indexOf(dept).coerceAtLeast(0))
            }
            val description = buildList {
                if (config.stockable) add("Stock items: enter now, complete later, or link a received PO after saving.")
                if (selected.second.requiresAccountantReview) add("Accountant review required for this expense type.")
            }.joinToString(" ")
            stockMessage.text = description
            stockMessage.visibility = if (description.isEmpty()) View.GONE else View.VISIBLE
        }

        fun refreshTypes(prefer: String? = selectedType()?.second?.code) {
            val chosenVendor = vendorOptions.getOrNull(vendor.selectedItemPosition - 2)
            val vendorAllowed = chosenVendor?.let { vendorCategoryPrefs.allowed(it.code) }
            choices = catalog.categories.filter { category ->
                category.code != FinanceExpenseCategoryRules.STAFF_ONLY &&
                    (visibility.categoryVisible(category) ||
                        (existing != null && category.code == previousType?.first?.code)) &&
                    (chosenVendor == null || category.code in (vendorAllowed ?: emptySet()) ||
                        (existing != null && chosenVendor.code == existing.vendorCode && category.code == previousType?.first?.code))
            }.flatMap { category ->
                category.types.filter { type ->
                    visibility.typeVisible(category, type) ||
                        (existing != null && category.code == previousType?.first?.code && type.code == previousType?.second?.code)
                }.map { category to it }
            }.sortedWith(compareBy({ it.second.name.lowercase() }, { it.first.name.lowercase() }))
            typePicker.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Select expense type") + choices.map { it.second.name })
            typePicker.isEnabled = choices.isNotEmpty()
            val index = choices.indexOfFirst { it.second.code == prefer }
            typePicker.setSelection(if (index >= 0) index + 1 else 0)
            selectionHelp.text = when {
                choices.isEmpty() && chosenVendor != null ->
                    "No expense categories assigned to ${chosenVendor.name}. Open More details to assign them."
                choices.isEmpty() -> "No active expense types. Open More details to choose active categories."
                else -> ""
            }
            selectionHelp.visibility = if (choices.isEmpty()) View.VISIBLE else View.GONE
            updateTypeHint()
        }
        typePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateTypeHint()
        }

        // Everyday entry is intentionally short and in a fixed order.
        field(form, "EXPENSE TYPE *", typePicker)
        field(form, "VENDOR / PAYEE", vendor)
        oneTimeName.visibility = if (selectedVendorIndex == 1) View.VISIBLE else View.GONE
        form.addView(oneTimeName)
        form.addView(gap(8))
        field(form, "TOTAL BILL AMOUNT (₹) *", amount)
        field(form, "GST BILL? (OPTIONAL)", gst)
        field(form, "PAYMENT STATUS", payment)
        val paidContainer = column()
        field(paidContainer, "AMOUNT PAID (₹) *", paid)
        form.addView(paidContainer)
        fun updatePaid() {
            paidContainer.visibility = if (statuses[payment.selectedItemPosition] ==
                FinanceDemoDraftPolicy.PaymentStatus.PARTIALLY_PAID) View.VISIBLE else View.GONE
        }
        payment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updatePaid()
        }
        updatePaid()
        form.addView(selectionHelp)
        form.addView(stockMessage)
        stockMessage.visibility = View.GONE
        form.addView(gap(7))

        val advanced = column()
        field(advanced, "DEPARTMENT (AUTO-SELECTED)", department)
        field(advanced, "INVOICE DATE", date)
        field(advanced, "NOTES", notes)
        val settingsLinks = column()
        advanced.addView(settingsLinks)
        val toggle = link("More details and category settings  ▾") {
            advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        form.addView(toggle)
        form.addView(advanced)
        advanced.visibility = if (existing?.notes?.isNotBlank() == true) View.VISIBLE else View.GONE

        fun configurePropertyCategories() {
            val categories = catalog.categories.filter { it.code != FinanceExpenseCategoryRules.STAFF_ONLY }
            val checked = categories.map(visibility::categoryVisible).toBooleanArray()
            AlertDialog.Builder(activity)
                .setTitle("Active expense categories")
                .setMultiChoiceItems(categories.map { it.name }.toTypedArray(), checked) { _, index, selected -> checked[index] = selected }
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save") { _, _ ->
                    if (!checked.any { it }) { warn("Keep at least one active expense category"); return@setPositiveButton }
                    try {
                        categories.forEachIndexed { i, category -> visibility.setCategoryVisible(category.code, checked[i]) }
                        refreshTypes()
                    } catch (error: Exception) { warn(error.message ?: "Unable to update categories") }
                }.show()
        }
        fun configureVendorCategories() {
            val current = vendorOptions.getOrNull(vendor.selectedItemPosition - 2)
                ?: run { warn("Choose a vendor from Vendor Master first"); return }
            val categories = catalog.categories.filter { category ->
                category.code != FinanceExpenseCategoryRules.STAFF_ONLY &&
                    visibility.categoryVisible(category) && category.types.any { visibility.typeVisible(category, it) }
            }
            val saved = vendorCategoryPrefs.allowed(current.code).orEmpty()
            val selected = categories.map { it.code in saved }.toBooleanArray()
            AlertDialog.Builder(activity)
                .setTitle("Allowed categories · ${current.name}")
                .setMultiChoiceItems(categories.map { it.name }.toTypedArray(), selected) { _, index, checked -> selected[index] = checked }
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save") { _, _ ->
                    val allowed = categories.filterIndexed { i, _ -> selected[i] }.map { it.code }.toSet()
                    if (allowed.isEmpty()) { warn("Select at least one category"); return@setPositiveButton }
                    try {
                        vendorCategoryPrefs.assign(current.code, allowed)
                        refreshTypes()
                    } catch (error: Exception) { warn(error.message ?: "Unable to update vendor categories") }
                }.show()
        }
        settingsLinks.addView(link("Manage property expense categories  ›") { configurePropertyCategories() })
        val vendorCategoriesLink = link("Manage vendor expense categories  ›") { configureVendorCategories() }
        settingsLinks.addView(vendorCategoriesLink)
        vendor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                oneTimeName.visibility = if (position == 1) View.VISIBLE else View.GONE
                vendorCategoriesLink.visibility = if (position >= 2) View.VISIBLE else View.GONE
                refreshTypes()
            }
        }
        vendorCategoriesLink.visibility = if (vendor.selectedItemPosition >= 2) View.VISIBLE else View.GONE
        refreshTypes(previousType?.second?.code)

        val reason = if (existing != null) edit("Why are you changing this expense?", multiline = true).also {
            field(form, "EDIT REASON *", it)
        } else null
        // Never silently change a historical GST declaration on edit.
        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (existing == null) "Add Expense" else "Edit Expense")
            .setView(ScrollView(activity).apply { clipToPadding = false; addView(form) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (existing == null) "Save Expense" else "Save Changes", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val selected = selectedType() ?: error("Choose an expense type")
                    val category = selected.first
                    val expenseType = selected.second
                    require(category.code != FinanceExpenseCategoryRules.STAFF_ONLY) {
                        "Staff & Employee Benefits must be managed in Manpower"
                    }
                    val registeredCode = vendorOptions.getOrNull(vendor.selectedItemPosition - 2)?.code
                    val assigned = registeredCode?.let { vendorCategoryPrefs.allowed(it)
                        ?: error("Assign expense categories for this vendor in More details first") }
                    require(FinanceExpenseCategoryRules.permitted(category.code, assigned)) {
                        "Expense type is not assigned to this vendor"
                    }
                    require((visibility.categoryVisible(category) && visibility.typeVisible(category, expenseType)) ||
                        (existing != null && expenseType.code == existing.expenseTypeCode && category.code == previousType?.first?.code)) {
                        "This expense type is disabled for the property"
                    }
                    val payee = when (vendor.selectedItemPosition) {
                        0 -> "no_payee"
                        1 -> {
                            val name = oneTimeName.text.toString().trim()
                            require(name.isNotBlank() && name.length <= 120) { "Enter a one-time supplier name (max 120 characters)" }
                            "one_time:$name"
                        }
                        else -> vendorOptions[vendor.selectedItemPosition - 2].code
                    }
                    val total = FinanceDemoDraftPolicy.parseMoney(amount.text.toString())
                    val payStatus = statuses[payment.selectedItemPosition]
                    val paidAmount = when (payStatus) {
                        FinanceDemoDraftPolicy.PaymentStatus.NOT_PAID -> 0L
                        FinanceDemoDraftPolicy.PaymentStatus.PAID -> total
                        FinanceDemoDraftPolicy.PaymentStatus.PARTIALLY_PAID ->
                            FinanceDemoDraftPolicy.parseMoney(paid.text.toString())
                    }
                    val remarks = FinanceGstBillNote.write(gstStatuses[gst.selectedItemPosition], notes.text.toString())
                    val input = FinanceDemoDraftPolicy.DraftInput(
                        vendorCode = payee,
                        category = category.name,
                        invoiceDate = date.text.toString(),
                        amountPaise = total,
                        paymentStatus = payStatus,
                        amountPaidPaise = paidAmount,
                        expenseCategoryCode = category.code,
                        expenseTypeCode = expenseType.code,
                        expenseTypeLabel = expenseType.name,
                        department = departmentOptions[department.selectedItemPosition],
                        notes = remarks
                    )
                    val editReason = reason?.text?.toString().orEmpty()
                    if (existing != null) require(editReason.trim().length >= 3) { "Enter a reason for editing" }
                    onSave(input, editReason)
                    dialog.dismiss()
                } catch (error: RuntimeException) {
                    warn(error.message ?: "Check expense details")
                }
            }
        }
        dialog.show()
    }
}
