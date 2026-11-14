package com.example.bookingregister.finance.dev

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

/** Simple recurring-expense setup using the existing expense category/type master. */
internal class FinanceRecurringScreen(
    private val activity: AppCompatActivity,
    private val database: FinanceDemoDraftDatabase,
    private val catalog: SmartExpenseCatalog,
    private val visibility: FinanceExpenseVisibility,
    private val onChanged: () -> Unit
) {
    private val dark = Color.rgb(25, 42, 60)
    private val muted = Color.rgb(94, 106, 120)
    private val green = Color.rgb(0, 109, 43)
    private val red = Color.rgb(190, 45, 61)
    private fun dp(v: Int) = (activity.resources.displayMetrics.density * v + 0.5f).toInt()
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String, size: Float = 14f, bold: Boolean = false, color: Int = dark) =
        TextView(activity).apply {
            this.text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    private fun gap(value: Int) = View(activity).apply { minimumHeight = dp(value) }
    private fun toast(value: String) = Toast.makeText(activity, value, Toast.LENGTH_LONG).show()
    private fun spinner(values: List<String>) = Spinner(activity).apply {
        adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, values)
        minimumHeight = dp(48)
    }

    fun showManage() {
        activity.lifecycleScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    materializeDueBills()
                    database.recurring().allRules(FinanceDemoDraftPolicy.TENANT, FinanceDemoDraftPolicy.PROPERTY) to
                        database.recurring().openBills(
                            FinanceDemoDraftPolicy.TENANT,
                            FinanceDemoDraftPolicy.PROPERTY,
                            LocalDate.now().toString()
                        )
                }
                renderManage(snapshot.first, snapshot.second)
            } catch (error: Exception) {
                toast(error.message ?: "Unable to load recurring expenses")
            }
        }
    }

    private fun renderManage(rules: List<FinanceRecurringRule>, bills: List<FinanceRecurringBill>) {
        val root = column().apply { setPadding(dp(18), dp(12), dp(18), dp(18)) }
        root.addView(text("Recurring Bills", 22f, true))
        root.addView(text("Uses the same expense categories and types.", 12f, color = muted))
        root.addView(gap(12))
        root.addView(Button(activity).apply {
            text = "+ Mark Expense as Recurring"; isAllCaps = false
            setTextColor(Color.WHITE); setBackgroundColor(green)
            setOnClickListener { showAdd() }
        })
        root.addView(gap(16))
        root.addView(text("ACTIVE RULES", 12f, true, green))
        val active = rules.filter { it.active }
        if (active.isEmpty()) root.addView(text("No recurring expenses yet.", 13f, color = muted))
        active.forEach { rule ->
            val row = column().apply {
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(Color.rgb(246, 249, 247))
                addView(text(rule.expenseTypeLabel, 15f, true))
                addView(text("${rule.expenseCategoryLabel} · ${FinanceDemoDraftPolicy.moneyText(rule.amountPaise)}", 12f, color = muted))
                addView(text("First due ${rule.firstDueDate}" + (rule.endDate?.let { " · Ends $it" } ?: " · No end date"), 12f, color = muted))
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(Button(activity).apply {
                        text = "Change Amount"; isAllCaps = false; setTextColor(green)
                        setOnClickListener { showRevision(rule) }
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(Button(activity).apply {
                        text = "Stop"; isAllCaps = false; setTextColor(red)
                        setOnClickListener { confirmStop(rule) }
                    }, LinearLayout.LayoutParams(0, -2, 0.75f))
                })
            }
            root.addView(row); root.addView(gap(8))
        }
        root.addView(gap(8))
        root.addView(text("DUE TO PAY", 12f, true, green))
        if (bills.isEmpty()) root.addView(text("Nothing is due.", 13f, color = muted))
        bills.forEach { bill ->
            val status = FinanceRecurringPolicy.displayStatus(bill, LocalDate.now()).name.replace('_', ' ')
            root.addView(text(
                "${bill.expenseTypeLabel} · $status\nDue ${bill.dueDate} · ${FinanceDemoDraftPolicy.moneyText(bill.amountPaise - bill.amountPaidPaise)}",
                13f, true, if (status == "OVERDUE") red else dark
            ).apply { setPadding(dp(8), dp(8), dp(8), dp(8)) })
        }
        AlertDialog.Builder(activity)
            .setView(ScrollView(activity).apply { addView(root) })
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showAdd() {
        val categories = catalog.categories.filter { category ->
            visibility.categoryVisible(category) && category.types.any { visibility.typeVisible(category, it) }
        }
        if (categories.isEmpty()) {
            toast("Enable an expense category first")
            return
        }
        val form = column().apply { setPadding(dp(18), dp(8), dp(18), dp(12)) }
        val category = spinner(categories.map { it.name })
        val type = spinner(emptyList())
        var visibleTypes = emptyList<SmartExpenseType>()
        fun updateTypes(index: Int) {
            val selected = categories[index]
            visibleTypes = selected.types.filter { visibility.typeVisible(selected, it) }
            type.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, visibleTypes.map { it.name })
        }
        category.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = updateTypes(position)
        }
        updateTypes(0)
        field(form, "CATEGORY", category)
        field(form, "EXPENSE TYPE", type)
        val amount = EditText(activity).apply {
            hint = "0.00"; setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        field(form, "AMOUNT (₹)", amount)
        val firstDue = dateField(LocalDate.now())
        field(form, "FIRST PAYMENT DATE", firstDue)
        val noEnd = CheckBox(activity).apply { text = "No ending date"; isChecked = true }
        form.addView(noEnd)
        val endDate = dateField(LocalDate.now().plusMonths(12)).apply { isEnabled = false }
        field(form, "ENDING DATE", endDate)
        noEnd.setOnCheckedChangeListener { _, checked -> endDate.isEnabled = !checked }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("New Recurring Expense")
            .setView(ScrollView(activity).apply { addView(form) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val selectedCategory = categories[category.selectedItemPosition]
                    val selectedType = visibleTypes.getOrNull(type.selectedItemPosition) ?: error("Choose an expense type")
                    val now = System.currentTimeMillis()
                    val rule = FinanceRecurringPolicy.newRule(
                        categoryCode = selectedCategory.code,
                        categoryLabel = selectedCategory.name,
                        typeCode = selectedType.code,
                        typeLabel = selectedType.name,
                        amountPaise = FinanceDemoDraftPolicy.parseMoney(amount.text.toString()),
                        firstDueDate = firstDue.text.toString(),
                        endDate = if (noEnd.isChecked) null else endDate.text.toString(),
                        nowMillis = now
                    )
                    activity.lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                database.recurring().createRule(
                                    rule,
                                    FinanceRecurringEvent(
                                        eventId = UUID.randomUUID().toString(),
                                        referenceId = rule.ruleId,
                                        eventType = "RULE_CREATED",
                                        description = "Recurring ${rule.expenseTypeLabel} created",
                                        occurredAtMillis = now
                                    )
                                )
                                materializeDueBills()
                            }
                            dialog.dismiss(); onChanged(); toast("Recurring expense saved")
                        } catch (error: Exception) {
                            toast(error.message ?: "Unable to save recurring expense")
                        }
                    }
                } catch (error: Exception) {
                    toast(error.message ?: "Check recurring expense details")
                }
            }
        }
        dialog.show()
    }

    fun showPayment(bill: FinanceRecurringBill) {
        val due = bill.amountPaise - bill.amountPaidPaise
        val form = column().apply { setPadding(dp(18), dp(8), dp(18), dp(12)) }
        form.addView(text(bill.expenseTypeLabel, 17f, true))
        form.addView(text("Due ${FinanceDemoDraftPolicy.moneyText(due)} · ${bill.dueDate}", 13f, color = muted))
        form.addView(gap(12))
        val amount = EditText(activity).apply {
            setText(FinanceDemoDraftPolicy.moneyText(due).removePrefix("₹")); setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        field(form, "AMOUNT TO PAY (₹)", amount)
        val methods = listOf("UPI", "Bank Transfer", "Cash")
        val method = spinner(methods)
        field(form, "PAYMENT METHOD", method)
        val paymentDate = dateField(LocalDate.now())
        field(form, "PAYMENT DATE", paymentDate)
        val reference = EditText(activity).apply { hint = "Optional reference"; setSingleLine(true) }
        field(form, "REFERENCE", reference)
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Pay Recurring Bill")
            .setView(ScrollView(activity).apply { addView(form) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Record Payment", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val now = System.currentTimeMillis()
                    val payment = FinanceRecurringPayment(
                        paymentId = UUID.randomUUID().toString(),
                        billId = bill.billId,
                        amountPaise = FinanceDemoDraftPolicy.parseMoney(amount.text.toString()),
                        paymentDate = paymentDate.text.toString(),
                        method = when (method.selectedItemPosition) { 0 -> "UPI"; 1 -> "BANK_TRANSFER"; else -> "CASH" },
                        reference = reference.text.toString().trim(),
                        createdAtMillis = now
                    )
                    FinanceRecurringPolicy.validatePayment(bill, payment)
                    activity.lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                database.recurring().recordPayment(
                                    payment,
                                    FinanceRecurringEvent(
                                        eventId = UUID.randomUUID().toString(),
                                        referenceId = bill.billId,
                                        eventType = "PAYMENT_RECORDED",
                                        description = "${payment.method} payment ${payment.amountPaise} paise",
                                        occurredAtMillis = now
                                    )
                                )
                            }
                            dialog.dismiss(); onChanged(); toast("Payment recorded")
                        } catch (error: Exception) {
                            toast(error.message ?: "Unable to record payment")
                        }
                    }
                } catch (error: Exception) {
                    toast(error.message ?: "Check payment details")
                }
            }
        }
        dialog.show()
    }

    private fun showRevision(rule: FinanceRecurringRule) {
        val form = column().apply { setPadding(dp(18), dp(8), dp(18), dp(12)) }
        form.addView(text(rule.expenseTypeLabel, 16f, true))
        form.addView(text("Old bills will remain unchanged.", 12f, color = muted))
        val amount = EditText(activity).apply {
            setText(FinanceDemoDraftPolicy.moneyText(rule.amountPaise).removePrefix("₹")); setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        field(form, "NEW AMOUNT (₹)", amount)
        val oldAnchor = LocalDate.parse(rule.firstDueDate)
        val nextMonthValue = LocalDate.now().plusMonths(1)
        val nextDue = nextMonthValue.withDayOfMonth(oldAnchor.dayOfMonth.coerceAtMost(nextMonthValue.lengthOfMonth()))
        val effective = dateField(nextDue)
        field(form, "APPLY FROM PAYMENT DATE", effective)
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Change Recurring Amount")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val effectiveDate = LocalDate.parse(effective.text.toString())
                    require(effectiveDate.isAfter(LocalDate.now())) { "Choose a future payment date" }
                    val now = System.currentTimeMillis()
                    val end = rule.endDate?.let(LocalDate::parse)
                    require(end == null || !end.isBefore(effectiveDate)) { "The current rule ends before this date" }
                    val replacement = FinanceRecurringPolicy.newRule(
                        categoryCode = rule.expenseCategoryCode,
                        categoryLabel = rule.expenseCategoryLabel,
                        typeCode = rule.expenseTypeCode,
                        typeLabel = rule.expenseTypeLabel,
                        amountPaise = FinanceDemoDraftPolicy.parseMoney(amount.text.toString()),
                        firstDueDate = effectiveDate.toString(),
                        endDate = rule.endDate,
                        nowMillis = now,
                        seriesId = rule.seriesId
                    )
                    val oldBills = FinanceRecurringPolicy.billsThrough(rule, effectiveDate.minusDays(1), now)
                    activity.lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                database.recurring().reviseRule(rule.ruleId, oldBills, replacement, now)
                                materializeDueBills()
                            }
                            dialog.dismiss(); onChanged(); toast("New amount will apply from $effectiveDate")
                        } catch (error: Exception) {
                            toast(error.message ?: "Unable to change recurring amount")
                        }
                    }
                } catch (error: Exception) {
                    toast(error.message ?: "Check the new amount and date")
                }
            }
        }
        dialog.show()
    }

    private suspend fun materializeDueBills() {
        val dao = database.recurring()
        val now = System.currentTimeMillis()
        val bills = dao.activeRules(FinanceDemoDraftPolicy.TENANT, FinanceDemoDraftPolicy.PROPERTY)
            .flatMap { FinanceRecurringPolicy.billsThrough(it, LocalDate.now(), now) }
        if (bills.isNotEmpty()) dao.insertBills(bills)
    }

    private fun confirmStop(rule: FinanceRecurringRule) {
        AlertDialog.Builder(activity)
            .setTitle("Stop Future Bills?")
            .setMessage("Existing bills and payments will remain unchanged.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Stop") { _, _ ->
                activity.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) { database.recurring().stopRule(rule.ruleId, System.currentTimeMillis()) }
                        onChanged(); toast("Future bills stopped")
                    } catch (error: Exception) {
                        toast(error.message ?: "Unable to stop recurring expense")
                    }
                }
            }.show()
    }

    private fun dateField(initial: LocalDate) = EditText(activity).apply {
        setText(initial.toString()); isFocusable = false; isClickable = true
        setOnClickListener {
            val start = runCatching { LocalDate.parse(text.toString()) }.getOrDefault(initial)
            DatePickerDialog(activity, { _, year, month, day -> setText(LocalDate.of(year, month + 1, day).toString()) },
                start.year, start.monthValue - 1, start.dayOfMonth).show()
        }
    }

    private fun field(parent: LinearLayout, title: String, view: View) {
        parent.addView(text(title, 11f, true, muted))
        parent.addView(view, LinearLayout.LayoutParams(-1, -2))
        parent.addView(gap(10))
    }
}
