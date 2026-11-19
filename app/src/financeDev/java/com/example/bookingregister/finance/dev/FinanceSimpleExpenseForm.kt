package com.example.bookingregister.finance.dev

import android.app.Activity
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
import java.time.LocalDate

/**
 * Simple Finance DEV-only expense editor. No search-first UX, no stock or accounting posting.
 * Saves a LOCAL_DRAFT_NOT_POSTED via the caller's existing Room + audit policy.
 */
internal class FinanceSimpleExpenseForm(
    private val activity: Activity,
    private val catalog: SmartExpenseCatalog,
    private val visibility: FinanceExpenseVisibility,
    private val stockSettings: FinanceGlobalCatalogSettings,
    private val vendors: List<FinanceDemoDraftPolicy.VendorOption>
) {
    private val navy = Color.rgb(25, 42, 60)
    private val muted = Color.rgb(101, 115, 132)
    private val teal = Color.rgb(0, 112, 83)
    private fun dp(x: Int) = (x * activity.resources.displayMetrics.density + 0.5f).toInt()
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun caption(s: String, size: Float = 13f, bold: Boolean = false) = TextView(activity).apply {
        text = s
        textSize = size
        setTextColor(if (bold) navy else muted)
        if (bold) setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun gap(h: Int) = View(activity).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun field(parent: LinearLayout, title: String, view: View) {
        parent.addView(caption(title, 12f, true))
        parent.addView(gap(5))
        parent.addView(view, LinearLayout.LayoutParams(-1, -2))
        parent.addView(gap(14))
    }
    private fun spinner(values: List<String>): Spinner = Spinner(activity).apply {
        adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, values)
        minimumHeight = dp(48)
    }
    private fun entry(hintText: String, initial: String = "", multiline: Boolean = false): EditText = EditText(activity).apply {
        hint = hintText
        setText(initial)
        textSize = 15f
        if (multiline) {
            minLines = 2
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        } else {
            setSingleLine(true)
        }
    }
    private fun info(textValue: String): TextView = caption(textValue, 12f).apply {
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = GradientDrawable().apply {
            setColor(Color.rgb(236, 248, 243))
            cornerRadius = dp(10).toFloat()
        }
    }

    fun show(
        existing: FinanceDemoDraftBill?,
        initialSelection: Pair<SmartExpenseCategory, SmartExpenseType>?,
        onSave: (FinanceDemoDraftPolicy.DraftInput, String) -> Unit
    ) {
        val oldSelection = initialSelection ?: existing?.expenseTypeCode?.let(catalog::findType)
        if (oldSelection?.first?.code == FinanceExpenseCategoryRules.STAFF_ONLY) {
            Toast.makeText(activity, "Staff expenses must be managed through Manpower. Existing records remain in Reports for review.", Toast.LENGTH_LONG).show()
            return
        }
        val propertyId = existing?.propertyId ?: FinanceDemoDraftPolicy.PROPERTY
        val vendorCategoryPrefs = FinanceVendorExpenseCategories(activity, propertyId)
        var allCategories: List<SmartExpenseCategory> = emptyList()
        val form = column().apply { setPadding(dp(20), dp(10), dp(20), dp(18)) }
        form.addView(info("FINANCE DEV · Local draft only. Nothing is posted to hotel accounts or stock."))
        form.addView(gap(16))

        val legacyVendorCode = existing?.vendorCode?.takeIf { saved ->
            FinanceDemoDraftPolicy.vendors.any { it.code == saved } && vendors.none { it.code == saved }
        }
        val vendorOptions = vendors + listOfNotNull(legacyVendorCode?.let { code ->
            FinanceDemoDraftPolicy.vendors.firstOrNull { it.code == code }
        })
        val vendorNames = listOf("No Vendor", "Un-Registered Vendor") + vendorOptions.map { it.name }
        val vendor = spinner(vendorNames)
        val previousPayee = existing?.vendorCode.orEmpty()
        val vendorIndex = when {
            previousPayee.startsWith("one_time:") -> 1
            else -> vendorOptions.indexOfFirst { it.code == previousPayee }.let { if (it < 0) 0 else it + 2 }
        }
        vendor.setSelection(vendorIndex)
        field(form, "VENDOR", vendor)
        val oneTimeName = entry("Enter un-registered vendor name", previousPayee.removePrefix("one_time:").takeIf {
            previousPayee.startsWith("one_time:")
        }.orEmpty())
        oneTimeName.visibility = if (vendorIndex == 1) View.VISIBLE else View.GONE
        form.addView(oneTimeName)
        form.addView(gap(10))


        val category = spinner(listOf("Choose expense category"))
        field(form, "CA CATEGORY  *", category)
        val type = spinner(listOf("Choose expense type"))
        field(form, "EXPENSE TYPE  *", type)
        val departmentOptions = FinanceGlobalCatalogDefaults.departments
        val department = spinner(departmentOptions)
        field(form, "DEPARTMENT  *", department)
        val stockNotice = info("Choose an expense type. Stock tracking settings are read from the Global Catalog.")
        form.addView(stockNotice)
        form.addView(gap(14))

        var visibleTypes: List<SmartExpenseType> = emptyList()
        var chosenTypeCode: String? = null
        fun syncStockInfo(expenseType: SmartExpenseType?, newSelection: Boolean) {
            if (expenseType == null) {
                stockNotice.text = "Choose an expense type to see its stock classification."
                return
            }
            val config = stockSettings.forType(expenseType.code)
            val initialDept = if (!newSelection && expenseType.code == oldSelection?.second?.code &&
                existing?.department?.let { it in departmentOptions } == true) existing!!.department else config.department
            department.setSelection(departmentOptions.indexOf(initialDept).coerceAtLeast(0))
            stockNotice.text = if (config.stockable)
                "STOCKABLE · ${config.groupIds.size} linked item groups. After saving, choose Enter Items Now, Complete Later, or Attach Received Purchase Order."
            else "ACCOUNTING EXPENSE · No physical stock is recorded for this type."
            if (expenseType.requiresAccountantReview) {
                stockNotice.text = stockNotice.text.toString() + " Accountant review is required."
            }
        }
        type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = visibleTypes.getOrNull(position)
                val changed = chosenTypeCode != null && chosenTypeCode != selected?.code
                chosenTypeCode = selected?.code
                syncStockInfo(selected, changed)
            }
        }
        fun categoriesForCurrentVendor(): List<SmartExpenseCategory> {
            val vendorCode = vendorOptions.getOrNull(vendor.selectedItemPosition - 2)?.code
            val assigned = vendorCode?.let { vendorCategoryPrefs.allowed(it) }
            return catalog.categories.filter { ca ->
                ca.code != FinanceExpenseCategoryRules.STAFF_ONLY &&
                    ((this@FinanceSimpleExpenseForm.visibility.categoryVisible(ca) && ca.types.any { this@FinanceSimpleExpenseForm.visibility.typeVisible(ca, it) }) ||
                        (existing != null && ca.code == oldSelection?.first?.code)) &&
                    (vendorCode == null || ca.code in (assigned ?: emptySet()) ||
                        (existing != null && existing.vendorCode == vendorCode && ca.code == oldSelection?.first?.code))
            }
        }
        fun updateTypes(position: Int, preferCode: String? = null) {
            val ca = allCategories.getOrNull(position)
            visibleTypes = if (ca == null) emptyList() else ca.types.filter {
                this@FinanceSimpleExpenseForm.visibility.typeVisible(ca, it) || (existing != null && it.code == oldSelection?.second?.code)
            }
            type.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
                if (visibleTypes.isEmpty()) listOf("Select category first") else visibleTypes.map { it.name })
            type.isEnabled = visibleTypes.isNotEmpty()
            if (visibleTypes.isNotEmpty()) {
                val selectedIndex = visibleTypes.indexOfFirst { it.code == preferCode }
                type.setSelection(if (selectedIndex >= 0) selectedIndex else 0)
            }
            syncStockInfo(visibleTypes.getOrNull(type.selectedItemPosition), ca?.code != oldSelection?.first?.code)
        }
        fun refreshCategories(preferCode: String? = null) {
            val currentCode = allCategories.getOrNull(category.selectedItemPosition)?.code
            allCategories = categoriesForCurrentVendor()
            category.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
                if (allCategories.isEmpty()) listOf("Choose categories below first") else allCategories.map { it.name })
            category.isEnabled = allCategories.isNotEmpty()
            if (allCategories.isNotEmpty()) {
                val index = allCategories.indexOfFirst { it.code == (preferCode ?: currentCode) }
                category.setSelection(if (index >= 0) index else 0)
                val preferredType = if (allCategories[category.selectedItemPosition].code == oldSelection?.first?.code)
                    oldSelection?.second?.code else null
                updateTypes(category.selectedItemPosition, preferredType)
            } else updateTypes(-1)
        }
        category.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position !in allCategories.indices) return
                val preferred = if (allCategories[position].code == oldSelection?.first?.code)
                    oldSelection?.second?.code else null
                updateTypes(position, preferred)
            }
        }
        val propertyOptions = caption("Categories used at this property  ›", 13f, true).apply {
            setTextColor(teal); setPadding(dp(1), dp(9), dp(1), dp(11)); isClickable = true
            setOnClickListener {
                val options = catalog.categories.filter { it.code != FinanceExpenseCategoryRules.STAFF_ONLY }
                val checked = options.map { this@FinanceSimpleExpenseForm.visibility.categoryVisible(it) }.toBooleanArray()
                AlertDialog.Builder(activity)
                    .setTitle("Choose active expense categories")
                    .setMultiChoiceItems(options.map { it.name }.toTypedArray(), checked) { _, index, isChecked ->
                        checked[index] = isChecked
                    }
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save") { _, _ ->
                        if (!checked.any { it }) Toast.makeText(activity, "Keep at least one active category", Toast.LENGTH_LONG).show()
                        else {
                            options.forEachIndexed { index, ca -> this@FinanceSimpleExpenseForm.visibility.setCategoryVisible(ca.code, checked[index]) }
                            refreshCategories()
                        }
                    }.show()
            }
        }
        form.addView(propertyOptions)
        val vendorOptionsAction = caption("Choose categories for this vendor  ›", 13f, true).apply {
            setTextColor(teal); setPadding(dp(1), dp(9), dp(1), dp(11)); isClickable = true
            setOnClickListener {
                val chosenVendor = vendorOptions.getOrNull(vendor.selectedItemPosition - 2) ?: return@setOnClickListener
                val options = catalog.categories.filter { ca ->
                    ca.code != FinanceExpenseCategoryRules.STAFF_ONLY && this@FinanceSimpleExpenseForm.visibility.categoryVisible(ca) &&
                        ca.types.any { this@FinanceSimpleExpenseForm.visibility.typeVisible(ca, it) }
                }
                if (options.isEmpty()) {
                    Toast.makeText(activity, "First select active property categories", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val saved = vendorCategoryPrefs.allowed(chosenVendor.code).orEmpty()
                val checked = options.map { it.code in saved }.toBooleanArray()
                AlertDialog.Builder(activity)
                    .setTitle("Allowed categories · ${chosenVendor.name}")
                    .setMultiChoiceItems(options.map { it.name }.toTypedArray(), checked) { _, index, isChecked ->
                        checked[index] = isChecked
                    }
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save") { _, _ ->
                        val allowed = options.filterIndexed { index, _ -> checked[index] }.map { it.code }.toSet()
                        if (allowed.isEmpty()) Toast.makeText(activity, "Choose at least one vendor category", Toast.LENGTH_LONG).show()
                        else {
                            try {
                                vendorCategoryPrefs.assign(chosenVendor.code, allowed)
                                refreshCategories()
                            } catch (error: Exception) {
                                Toast.makeText(activity, error.message ?: "Vendor category error", Toast.LENGTH_LONG).show()
                            }
                        }
                    }.show()
            }
        }
        form.addView(vendorOptionsAction)
        vendor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                oneTimeName.visibility = if (position == 1) View.VISIBLE else View.GONE
                vendorOptionsAction.visibility = if (position >= 2) View.VISIBLE else View.GONE
                refreshCategories(if (existing?.vendorCode == vendorOptions.getOrNull(position - 2)?.code)
                    oldSelection?.first?.code else null)
            }
        }
        vendorOptionsAction.visibility = if (vendor.selectedItemPosition >= 2) View.VISIBLE else View.GONE
        refreshCategories(oldSelection?.first?.code)

        val amount = entry("0.00", existing?.let {
            FinanceDemoDraftPolicy.moneyText(it.amountPaise).removePrefix("₹")
        }.orEmpty()).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        field(form, "TOTAL AMOUNT (₹)  *", amount)
        val statuses = FinanceDemoDraftPolicy.PaymentStatus.entries
        val status = spinner(statuses.map { it.label })
        val statusIndex = statuses.indexOfFirst { it.name == existing?.paymentStatus }.coerceAtLeast(0)
        status.setSelection(statusIndex)
        field(form, "PAYMENT STATUS", status)
        val paid = entry("Enter amount paid", existing?.let {
            FinanceDemoDraftPolicy.moneyText(it.amountPaidPaise).removePrefix("₹")
        }.orEmpty()).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        field(form, "AMOUNT PAID (₹)", paid)
        fun syncPaid() {
            paid.visibility = if (statuses[status.selectedItemPosition] == FinanceDemoDraftPolicy.PaymentStatus.PARTIALLY_PAID)
                View.VISIBLE else View.GONE
        }
        status.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { syncPaid() }
        }
        syncPaid()

        val more = TextView(activity).apply {
            text = "More details  ▾"
            textSize = 14f
            setTextColor(teal)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(12))
            isClickable = true
        }
        form.addView(more)
        val details = column()
        val date = entry("YYYY-MM-DD", existing?.invoiceDate ?: LocalDate.now().toString()).apply {
            isFocusable = false
            isClickable = true
            setOnClickListener {
                val initial = runCatching { LocalDate.parse(text.toString()) }.getOrDefault(LocalDate.now())
                DatePickerDialog(activity, { _, y, month, day ->
                    setText(LocalDate.of(y, month + 1, day).toString())
                }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
            }
        }
        field(details, "INVOICE DATE", date)
        val notes = entry("Optional description or purchase details", existing?.notes.orEmpty(), multiline = true)
        field(details, "NOTES", notes)
        form.addView(details)
        details.visibility = if (existing?.notes?.isNotBlank() == true) View.VISIBLE else View.GONE
        more.setOnClickListener {
            details.visibility = if (details.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            more.text = if (details.visibility == View.VISIBLE) "Fewer details  ▴" else "More details  ▾"
        }

        val reason = if (existing != null) entry("Reason for changing this draft", multiline = true).also {
            field(form, "EDIT REASON  *", it)
        } else null

        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (existing == null) "Add Expense · DEV draft" else "Edit Expense · DEV draft")
            .setView(ScrollView(activity).apply { addView(form) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (existing == null) "Save draft" else "Save changes", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val ca = allCategories.getOrNull(category.selectedItemPosition)
                        ?: error("Choose an allowed expense category. Registered vendors need assigned categories first.")
                    val exType = visibleTypes.getOrNull(type.selectedItemPosition)
                        ?: error("Select a valid expense type")
                    require(ca.code != FinanceExpenseCategoryRules.STAFF_ONLY) {
                        "Staff & Employee Benefits is managed in Manpower, not Add Expense"
                    }
                    val registeredCode = vendorOptions.getOrNull(vendor.selectedItemPosition - 2)?.code
                    val allowedVendorCategories = registeredCode?.let { vendorCategoryPrefs.allowed(it)
                        ?: error("First choose categories for this registered vendor") }
                    require(FinanceExpenseCategoryRules.permitted(ca.code, allowedVendorCategories)) {
                        "This category is not assigned to this vendor"
                    }
                    require((this@FinanceSimpleExpenseForm.visibility.categoryVisible(ca) && this@FinanceSimpleExpenseForm.visibility.typeVisible(ca, exType)) ||
                        (existing != null && exType.code == existing.expenseTypeCode && ca.code == oldSelection?.first?.code)) {
                        "This expense category or type is not enabled for this property"
                    }
                    val selectedPayee = when (vendor.selectedItemPosition) {
                        0 -> "no_payee"
                        1 -> {
                            val text = oneTimeName.text.toString().trim()
                            require(text.isNotBlank() && text.length <= 120) { "Enter un-registered vendor name (max 120 letters)" }
                            "one_time:$text"
                        }
                        else -> vendorOptions[vendor.selectedItemPosition - 2].code
                    }
                    val amountPaise = FinanceDemoDraftPolicy.parseMoney(amount.text.toString())
                    val payMode = statuses[status.selectedItemPosition]
                    val paidPaise = when (payMode) {
                        FinanceDemoDraftPolicy.PaymentStatus.NOT_PAID -> 0L
                        FinanceDemoDraftPolicy.PaymentStatus.PAID -> amountPaise
                        FinanceDemoDraftPolicy.PaymentStatus.PARTIALLY_PAID ->
                            FinanceDemoDraftPolicy.parseMoney(paid.text.toString())
                    }
                    val input = FinanceDemoDraftPolicy.DraftInput(
                        vendorCode = selectedPayee,
                        category = ca.name,
                        invoiceDate = date.text.toString(),
                        amountPaise = amountPaise,
                        paymentStatus = payMode,
                        amountPaidPaise = paidPaise,
                        expenseCategoryCode = ca.code,
                        expenseTypeCode = exType.code,
                        expenseTypeLabel = exType.name,
                        department = departmentOptions[department.selectedItemPosition],
                        notes = notes.text.toString().trim()
                    )
                    val reasonText = reason?.text?.toString().orEmpty()
                    if (existing != null) require(reasonText.trim().length >= 3) { "Enter a reason for editing" }
                    onSave(input, reasonText)
                    dialog.dismiss()
                } catch (error: RuntimeException) {
                    Toast.makeText(activity, error.message ?: "Check expense details", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }
}
