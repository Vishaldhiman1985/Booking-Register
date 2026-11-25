package com.example.bookingregister.finance.dev

import android.app.Dialog
import com.example.bookingregister.finance.domain.FinanceV25RecurringGuide
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.time.LocalDate
import java.time.YearMonth

/** Offline finance DEV management UI: scheduled bills, revisions, dues, payments and history. */
internal class FinanceFixedExpensesScreen(private val activity: AppCompatActivity, private val onClosed: () -> Unit = {}) {
    private val db by lazy { FinanceFixedExpenseStore(activity) }
    private var dialog: Dialog? = null
    private val navy = Color.rgb(24, 42, 65)
    private val muted = Color.rgb(103, 116, 132)
    private val green = Color.rgb(7, 111, 82)
    private val pale = Color.rgb(239, 246, 243)
    private val edge = Color.rgb(224, 231, 237)
    private val background = Color.rgb(247, 249, 252)
    private val danger = Color.rgb(186, 57, 63)
    private fun dp(x: Int) = (x * activity.resources.displayMetrics.density + .5f).toInt()
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun gap(h: Int) = View(activity).apply { layoutParams = LinearLayout.LayoutParams(1,dp(h)) }
    private fun rounded(fill:Int,stroke:Int=edge,radius:Int=16) = GradientDrawable().apply {
        setColor(fill);cornerRadius=dp(radius).toFloat();setStroke(dp(1),stroke)
    }
    private fun txt(value:String,size:Float=14f,bold:Boolean=false,color:Int=navy)=TextView(activity).apply {
        text=value;textSize=size;setTextColor(color)
        if(bold) setTypeface(null,Typeface.BOLD)
    }
    private fun card() = column().apply { background=rounded(Color.WHITE);setPadding(dp(17),dp(15),dp(17),dp(15)) }
    private fun action(name:String,primary:Boolean=false,click:()->Unit)=TextView(activity).apply {
        text=name;textSize=14f;gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD)
        setTextColor(if(primary)Color.WHITE else green);minHeight=dp(45)
        setPadding(dp(11),dp(9),dp(11),dp(9));background=rounded(if(primary)green else pale,if(primary)green else pale,12)
        setOnClickListener { click() }
    }
    private fun edit(value:String,hint:String,number:Boolean=false)=EditText(activity).apply {
        setText(value);this.hint=hint;textSize=15f;setSingleLine(true)
        setPadding(dp(12),dp(10),dp(12),dp(10));background=rounded(Color.WHITE,edge,11)
        if(number) inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }
    private fun spinner(values:List<String>) = Spinner(activity).apply {
        adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,values)
        background=rounded(Color.WHITE,edge,10);minimumHeight=dp(45)
    }
    private fun field(form:LinearLayout,label:String,control:View) {
        form.addView(txt(label,12f,true,muted));form.addView(gap(5));form.addView(control,LinearLayout.LayoutParams(-1,dp(48)));form.addView(gap(12))
    }
    private fun notify(text:String) { Toast.makeText(activity,text,Toast.LENGTH_LONG).show() }

    fun show() {
        dialog=Dialog(activity,android.R.style.Theme_Material_Light_NoActionBar).also { it.setOnDismissListener { onClosed() }; it.show() }
        render()
    }
    /** Payments tab opens the ORIGINAL fixed-bill payment dialog, never a copy. */
    fun payBill(billId: String) {
        try {
            db.issueThrough(YearMonth.now())
            val bill = FinanceActivityStore(activity).use { ledger ->
                ledger.synchronize(includeManual=false)
                val accounted=ledger.bill(billId)
                db.bills().firstOrNull { it.id == billId }?.copy(paid=accounted?.paid ?: 0L)
            }
                ?: error("Fixed bill not found. Reopen Payments.")
            require(bill.remaining > 0L) { "This bill has already been paid." }
            require(!LocalDate.parse(bill.dueDate).isAfter(LocalDate.now())) {
                "This bill is not due yet. Use Recurring to review upcoming obligations."
            }
            showPayment(bill)
        } catch (error: Exception) {
            notify(error.message ?: "Unable to open fixed-bill payment")
        }
    }

    /** V25: contract maintenance lives under Vendors. Bills and settlements stay in Expenses. */
    private fun contractMoney(paise: Long): String {
        val raw = java.math.BigDecimal.valueOf(paise, 2).toPlainString()
        val digits = raw.removePrefix("-").substringBefore(".")
        val head = digits.dropLast(3)
        val grouped = if (head.isEmpty()) digits else
            head.reversed().chunked(2).map { it.reversed() }.asReversed().joinToString(",") +
                "," + digits.takeLast(3)
        val cents = if (paise % 100L == 0L) "" else "." + raw.substringAfter(".")
        return (if (paise < 0L) "-₹" else "₹") + grouped + cents
    }

    private var showPreviousContracts = false

    private fun render() {
        // Original bill issuance remains idempotent; no new payable or stock path is introduced.
        try { db.issueThrough(YearMonth.now()) }
        catch (error: Exception) { notify(error.message ?: "Unable to refresh recurring contracts") }
        val d = dialog ?: return
        val root = column().apply { setBackgroundColor(this@FinanceFixedExpensesScreen.background) }
        // Full-screen dialog can render edge-to-edge. Reserve the actual status-bar inset
        // so the back arrow and title never overlap Android's clock / notification icons.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusTop, 0, 0)
            insets
        }
        val header = row().apply {
            setPadding(dp(16), dp(16), dp(16), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        header.addView(action("‹") { d.dismiss() }, LinearLayout.LayoutParams(dp(46), dp(44)))
        header.addView(column().apply {
            addView(txt("Recurring Contracts", 20f, true))
            addView(txt("Manage regular vendor commitments", 12f, false, muted))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)

        val scroller = ScrollView(activity).apply { isFillViewport = false; clipToPadding = false }
        val content = column().apply { setPadding(dp(15), dp(16), dp(15), dp(28)) }
        val rules = db.rules()
        val active = rules.filter { it.active }.sortedWith(compareBy({ it.vendor.lowercase() }, { it.type.lowercase() }))
        val previous = rules.filterNot { it.active }.sortedWith(compareBy({ it.vendor.lowercase() }, { it.type.lowercase() }))

        content.addView(action("+ Add Contract", true) { showAdd() })
        content.addView(gap(19))
        content.addView(row().apply {
            addView(txt("Existing Contracts", 18f, true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(txt("${active.size} active", 12f, true, muted))
        })
        content.addView(gap(10))

        if (active.isEmpty()) {
            content.addView(card().apply {
                addView(txt("No active contracts. Tap Add Contract to create one.", 13f, false, muted))
            })
            content.addView(gap(10))
        }
        fun addContract(rule: FinanceFixedExpenseStore.Rule) {
            val monthlyRate = db.revisions(rule.id).lastOrNull()?.second ?: 0L
            content.addView(card().apply {
                addView(row().apply {
                    addView(txt(rule.type, 16f, true), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(txt(if (rule.active) "ACTIVE" else "ENDED", 10f, true, if (rule.active) green else muted))
                })
                addView(gap(5))
                addView(txt("${rule.vendor} · ${rule.department}", 12f, false, muted))
                addView(gap(7))
                addView(txt("${contractMoney(monthlyRate)} / month", 16f, true))
                addView(gap(3))
                addView(txt("Due on day ${rule.dueDay} of each month", 12f, false, muted))
                if (rule.active) {
                    addView(gap(10))
                    addView(row().apply {
                        addView(action("Edit Contract", true) { showEditContract(rule) }, LinearLayout.LayoutParams(0, dp(44), 1f))
                        addView(View(activity), LinearLayout.LayoutParams(dp(8), dp(1)))
                        addView(action("Stop") { showStop(rule) }, LinearLayout.LayoutParams(0, dp(44), .7f))
                    })
                } else {
                    addView(gap(6))
                    addView(txt("Previous agreement · Ended ${rule.endMonth ?: "before current period"}", 11f, false, muted))
                }
            })
            content.addView(gap(9))
        }
        active.forEach(::addContract)
        if (previous.isNotEmpty()) {
            content.addView(gap(7))
            content.addView(action(if (showPreviousContracts) "Hide Previous Contracts" else "View ${previous.size} Previous Contracts") {
                showPreviousContracts = !showPreviousContracts
                render()
            })
            if (showPreviousContracts) {
                content.addView(gap(10))
                previous.forEach(::addContract)
            }
        }
        scroller.addView(content)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        d.setContentView(root)
        d.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        ViewCompat.requestApplyInsets(root)
    }
    /** V25: vendor-first recurring setup. Same underlying fixed_rules + fixed_bills store. */
    private fun showAdd() {
        val catalog = FinanceExpenseCatalogLoader.load(activity)
        val visibility = FinanceExpenseVisibility(activity)
        val configurations = FinanceGlobalCatalogSettings(activity)
        val vendorStore = HotelPurchaseVendorStore(activity)
        val vendors = vendorStore.all().filterNot { it.id.startsWith("employee:") }
        if (vendors.isEmpty()) {
            notify("Add a supplier or landlord in Vendors before setting up a recurring expense.")
            return
        }
        val allCategories = catalog.categories.filter { category ->
            category.code != FinanceExpenseCategoryRules.STAFF_ONLY &&
                visibility.categoryVisible(category) &&
                category.types.any { visibility.typeVisible(category, it) }
        }
        if (allCategories.isEmpty()) { notify("No active expense categories are available."); return }
        val form = column().apply { setPadding(dp(16), dp(12), dp(16), dp(9)) }
        form.addView(txt("Use this for agreed monthly commitments such as rent, Wi-Fi, software, maintenance contracts or equipment hire.", 13f, false, muted))
        form.addView(gap(10))
        val vendor = spinner(vendors.map { it.name })
        val category = spinner(listOf("Select category"))
        val type = spinner(listOf("Select expense type"))
        val detail = txt("", 12f, false, muted)
        val departments = FinanceGlobalCatalogDefaults.departments
        val dept = spinner(departments)
        val amount = edit("", "Amount in rupees", true)
        val day = edit("5", "Day of month: 1–31", true)
        val start = edit(YearMonth.now().toString(), "YYYY-MM")
        fun selectable(c: SmartExpenseCategory, t: SmartExpenseType, assigned: Set<String>): Boolean =
            if (assigned.isEmpty()) visibility.typeVisible(c, t)
            else t.code in assigned && visibility.typeSelectableForVendor(c, t)

        var shownCategories = emptyList<SmartExpenseCategory>()
        var shownTypes = emptyList<SmartExpenseType>()

        fun chosenType(): Pair<SmartExpenseCategory, SmartExpenseType>? {
            val c = shownCategories.getOrNull(category.selectedItemPosition)
            val t = shownTypes.getOrNull(type.selectedItemPosition)
            return if (c != null && t != null) c to t else null
        }
        fun updateAdvice() {
            val chosen = chosenType()
            if (chosen == null) { detail.text = "Choose a category and expense type."; return }
            val advice = FinanceV25RecurringGuide.advise(chosen.first.name, chosen.second.name)
            val settings = configurations.forType(chosen.second.code)
            detail.text = if (settings.stockable)
                "This is a stock-tracked expense type. Use the existing Purchase / Stock flow so quantity receipts are not bypassed."
            else FinanceV25RecurringGuide.hint(advice)
            detail.setTextColor(if (settings.stockable || advice == FinanceV25RecurringGuide.Advice.MANPOWER_ONLY) danger else muted)
            val suggestedDept = settings.department
            val idx = departments.indexOf(suggestedDept)
            if (idx >= 0) dept.setSelection(idx)
        }
        fun refreshTypes() {
            val c = shownCategories.getOrNull(category.selectedItemPosition)
            val allowedCodes = vendorStore.typeCodes(vendors[vendor.selectedItemPosition].id)
            shownTypes = c?.types.orEmpty().filter { t ->
                selectable(c!!, t, allowedCodes)
            }.sortedWith(compareBy<SmartExpenseType> {
                when (FinanceV25RecurringGuide.advise(c!!.name, it.name)) {
                    FinanceV25RecurringGuide.Advice.RECOMMENDED -> 0
                    FinanceV25RecurringGuide.Advice.REVIEW -> 1
                    FinanceV25RecurringGuide.Advice.VARIABLE -> 2
                    FinanceV25RecurringGuide.Advice.MANPOWER_ONLY -> 3
                }
            }.thenBy { it.name })
            type.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, shownTypes.map { it.name })
            type.isEnabled = shownTypes.isNotEmpty()
            updateAdvice()
        }
        fun refreshCategories() {
            val allowedCodes = vendorStore.typeCodes(vendors[vendor.selectedItemPosition].id)
            shownCategories = allCategories.filter { c ->
                c.types.any { t -> selectable(c, t, allowedCodes) }
            }.sortedWith(compareBy<SmartExpenseCategory> { c ->
                if (c.types.any { t -> selectable(c, t, allowedCodes) &&
                        FinanceV25RecurringGuide.advise(c.name, t.name) == FinanceV25RecurringGuide.Advice.RECOMMENDED }) 0 else 1
            }.thenBy { it.name })
            category.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, shownCategories.map { it.name })
            category.isEnabled = shownCategories.isNotEmpty()
            refreshTypes()
            if (shownCategories.isEmpty()) {
                detail.text = "This vendor has no enabled expense types. Open Vendors and edit the vendor categories first."
            }
        }
        vendor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = refreshCategories()
        }
        category.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = refreshTypes()
        }
        type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateAdvice()
        }
        field(form, "1. VENDOR / LANDLORD", vendor)
        field(form, "2. EXPENSE CATEGORY", category)
        field(form, "3. EXPENSE TYPE", type)
        form.addView(detail); form.addView(gap(13))
        field(form, "4. FIXED MONTHLY AMOUNT (EXCLUDING SEPARATE TAX)", amount)
        field(form, "5. DUE DAY EACH MONTH", day)
        field(form, "6. FIRST MONTH (YYYY-MM)", start)
        val advanced = column().apply { this.visibility = View.GONE }
        field(advanced, "DEPARTMENT (SUGGESTED FROM EXPENSE TYPE)", dept)
        form.addView(action("Change department  ▾") {
            advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        form.addView(advanced)
        form.addView(gap(12))
        form.addView(txt("Monthly schedules only. The bill becomes payable; it is never marked paid automatically. Salaries stay in Manpower. Existing stock purchases stay in Purchase/Stock.", 12f, false, muted))
        refreshCategories()

        val alert = AlertDialog.Builder(activity).setTitle("Add Recurring Expense")
            .setView(ScrollView(activity).apply { addView(form) })
            .setNegativeButton("Cancel", null).setPositiveButton("Save Schedule", null).create()
        alert.setOnShowListener {
            alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val selected = chosenType() ?: error("Select a vendor category and expense type first")
                    val c = selected.first
                    val t = selected.second
                    val v = vendors[vendor.selectedItemPosition]
                    val advice = FinanceV25RecurringGuide.advise(c.name, t.name)
                    require(advice != FinanceV25RecurringGuide.Advice.MANPOWER_ONLY) {
                        "Staff salaries and employee benefits must be recorded in Manpower"
                    }
                    require(FinanceV25RecurringGuide.permittedForMonthlySchedule(
                        advice, configurations.forType(t.code).stockable)) {
                        "Stock-managed expense types must use the original Purchase / Stock flow"
                    }
                    val month = YearMonth.parse(start.text.toString().trim())
                    val amountPaise = FinanceFixedPolicy.amount(amount.text.toString())
                    val dueDay = day.text.toString().trim().toInt()
                    require(dueDay in 1..31) { "Choose a due day from 1 to 31" }
                    require(db.rules().none { rule ->
                        rule.vendorId == v.id && rule.typeCode == t.code &&
                            (rule.endMonth == null || !YearMonth.parse(rule.endMonth).isBefore(month))
                    }) {
                        "A monthly schedule already covers this vendor and expense type. Change the existing schedule instead."
                    }
                    val selectedDepartment = departments[dept.selectedItemPosition]
                    fun saveOnce() {
                        try {
                            // Original authoritative recurring engine: one fixed rule and its idempotent monthly bills.
                            db.create(c.code, c.name, t.code, t.name, v.id, v.name,
                                selectedDepartment, dueDay, month.toString(), amountPaise)
                            alert.dismiss()
                            render()
                        } catch (error: Exception) { notify(error.message ?: "Could not save recurring expense") }
                    }
                    if (advice == FinanceV25RecurringGuide.Advice.RECOMMENDED) saveOnce()
                    else AlertDialog.Builder(activity)
                        .setTitle("Confirm monthly agreement")
                        .setMessage(FinanceV25RecurringGuide.hint(advice) +
                            "\n\nCreate a fixed monthly bill for ${v.name} from $month?")
                        .setNegativeButton("Go back", null)
                        .setPositiveButton("Create anyway") { _, _ -> saveOnce() }
                        .show()
                } catch (error: Exception) { notify(error.message ?: "Check the schedule details") }
            }
        }
        alert.show()
    }
    /** Full contract editing with an effective date and immutable already-issued bills. */
    private fun showEditContract(rule: FinanceFixedExpenseStore.Rule) {
        val vendorStore = HotelPurchaseVendorStore(activity)
        val vendors = vendorStore.all().filterNot { it.id.startsWith("employee:") }
        val vendorPosition = vendors.indexOfFirst { it.id == rule.vendorId }
        if (vendorPosition < 0) {
            notify("This agreement's vendor is archived. Restore the vendor before editing.")
            return
        }
        val catalog = FinanceExpenseCatalogLoader.load(activity)
        val visibility = FinanceExpenseVisibility(activity)
        val settings = FinanceGlobalCatalogSettings(activity)
        val departments = FinanceGlobalCatalogDefaults.departments
        val form = column().apply { setPadding(dp(16), dp(12), dp(16), dp(10)) }
        val issued = db.latestBillMonth(rule.id)
        val earliestMonth = if (issued == null) YearMonth.parse(rule.startMonth)
                            else maxOf(YearMonth.now().plusMonths(1), issued.plusMonths(1))
        form.addView(txt(if (issued == null)
            "No bills have been issued yet. You can correct the agreement directly."
            else "Existing bills stay unchanged. Your edits will create a new version from the selected future month.",
            13f, false, muted))
        form.addView(gap(12))
        val vendor = spinner(vendors.map { it.name })
        val category = spinner(emptyList())
        val type = spinner(emptyList())
        val dept = spinner(departments)
        val rate = db.revisions(rule.id).lastOrNull()?.second ?: 0L
        val amount = edit(FinanceFixedPolicy.rupees(rate).removePrefix("₹"), "Monthly amount ₹", true)
        val day = edit(rule.dueDay.toString(), "Day 1–31", true)
        val effective = edit(earliestMonth.toString(), "YYYY-MM")
        val reason = edit("", "Required: why is this contract changing?")
        val advice = txt("", 12f, false, muted)
        var shownCategories = emptyList<SmartExpenseCategory>()
        var shownTypes = emptyList<SmartExpenseType>()
        var pendingPreferredType: String? = null
        var firstVendorSelection = true
        val selectable: (SmartExpenseCategory, SmartExpenseType, Set<String>) -> Boolean = { c, t, assigned ->
            if (assigned.isEmpty()) visibility.typeVisible(c, t)
            else t.code in assigned && visibility.typeSelectableForVendor(c, t)
        }
        fun chosen(): Pair<SmartExpenseCategory, SmartExpenseType>? {
            val c = shownCategories.getOrNull(category.selectedItemPosition)
            val t = shownTypes.getOrNull(type.selectedItemPosition)
            return if (c == null || t == null) null else c to t
        }
        fun updateAdvice() {
            val selected = chosen()
            advice.text = when {
                selected == null -> "Select an expense category and type."
                settings.forType(selected.second.code).stockable ->
                    "Stock-managed expenses must remain in the original Purchase / Stock flow."
                else -> FinanceV25RecurringGuide.hint(
                    FinanceV25RecurringGuide.advise(selected.first.name, selected.second.name))
            }
        }
        fun refreshTypes(preferredCode: String? = null) {
            val c = shownCategories.getOrNull(category.selectedItemPosition)
            val assigned = vendorStore.typeCodes(vendors[vendor.selectedItemPosition].id)
            shownTypes = c?.types.orEmpty().filter { t ->
                c != null && selectable(c, t, assigned)
            }
            type.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
                shownTypes.map { it.name })
            type.isEnabled = shownTypes.isNotEmpty()
            val index = shownTypes.indexOfFirst { it.code == preferredCode }
            if (index >= 0) type.setSelection(index)
            updateAdvice()
        }
        fun refreshCategories(preferredCode: String? = null, preferredTypeCode: String? = null) {
            val assigned = vendorStore.typeCodes(vendors[vendor.selectedItemPosition].id)
            shownCategories = catalog.categories.filter { c ->
                c.code != FinanceExpenseCategoryRules.STAFF_ONLY && visibility.categoryVisible(c) &&
                    c.types.any { t -> selectable(c, t, assigned) }
            }
            pendingPreferredType = preferredTypeCode
            category.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
                shownCategories.map { it.name })
            category.isEnabled = shownCategories.isNotEmpty()
            val index = shownCategories.indexOfFirst { it.code == preferredCode }
            if (index >= 0) category.setSelection(index)
            refreshTypes(preferredTypeCode)
        }
        vendor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (firstVendorSelection && position == vendorPosition) {
                    firstVendorSelection = false
                    refreshCategories(rule.categoryCode, rule.typeCode)
                } else {
                    firstVendorSelection = false
                    refreshCategories()
                }
            }
        }
        category.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val preferred = pendingPreferredType
                pendingPreferredType = null
                refreshTypes(preferred)
            }
        }
        type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateAdvice()
        }
        field(form, "VENDOR / LANDLORD", vendor)
        field(form, "CATEGORY", category)
        field(form, "EXPENSE TYPE", type)
        form.addView(advice);form.addView(gap(10))
        field(form, "DEPARTMENT", dept)
        field(form, "MONTHLY AMOUNT (EXCLUDING SEPARATE TAX)", amount)
        field(form, "DUE DAY", day)
        field(form, if (issued == null) "AGREEMENT START MONTH (YYYY-MM)" else "APPLY FROM (YYYY-MM)", effective)
        field(form, "REASON FOR CHANGE", reason)
        form.addView(txt("Existing bills, payments and rent GST/RCM obligations will not be changed.",
            12f, false, muted))
        // Initialize before showing the form. Delayed Spinner callbacks only refresh dependent menus.
        vendor.setSelection(vendorPosition)
        refreshCategories(rule.categoryCode, rule.typeCode)
        val deptIndex = departments.indexOf(rule.department)
        if (deptIndex >= 0) dept.setSelection(deptIndex)
        val alert = AlertDialog.Builder(activity).setTitle("Edit Recurring Contract")
            .setView(ScrollView(activity).apply { addView(form) })
            .setNegativeButton("Cancel", null).setPositiveButton("Save Changes", null).create()
        alert.setOnShowListener {
            alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val chosen = chosen() ?: error("Select a category and expense type")
                    val c = chosen.first
                    val t = chosen.second
                    val supplier = vendors[vendor.selectedItemPosition]
                    val guidance = FinanceV25RecurringGuide.advise(c.name, t.name)
                    require(guidance != FinanceV25RecurringGuide.Advice.MANPOWER_ONLY) {
                        "Staff and salaries remain in Manpower"
                    }
                    require(FinanceV25RecurringGuide.permittedForMonthlySchedule(
                        guidance, settings.forType(t.code).stockable)) {
                        "Use Purchase / Stock for stock-tracked expense types"
                    }
                    val applied = YearMonth.parse(effective.text.toString().trim())
                    require(issued != null || !applied.isBefore(YearMonth.now())) {
                        "The contract must start this month or later"
                    }
                    val dueDay = day.text.toString().trim().toInt()
                    val amountPaise = FinanceFixedPolicy.amount(amount.text.toString())
                    val selectedDept = departments[dept.selectedItemPosition]
                    val reasonText = reason.text.toString().trim()
                    fun performEdit() {
                        try {
                            db.editContract(rule, c.code, c.name, t.code, t.name,
                                supplier.id, supplier.name, selectedDept, dueDay,
                                applied.toString(), amountPaise, reasonText)
                            alert.dismiss()
                            render()
                        } catch (error: Exception) {
                            notify(error.message ?: "Could not edit the agreement")
                        }
                    }
                    if (guidance == FinanceV25RecurringGuide.Advice.RECOMMENDED) performEdit()
                    else AlertDialog.Builder(activity)
                        .setTitle("Review this monthly agreement")
                        .setMessage(FinanceV25RecurringGuide.hint(guidance) +
                            "\n\nSave these changes for ${supplier.name} from $applied?")
                        .setNegativeButton("Go back", null)
                        .setPositiveButton("Save") { _, _ -> performEdit() }
                        .show()
                } catch (error: Exception) {
                    notify(error.message ?: "Review the contract details")
                }
            }
        }
        alert.show()
    }

    private fun showRevision(rule:FinanceFixedExpenseStore.Rule) {
        val form=column().apply{setPadding(dp(18),dp(12),dp(18),dp(6))}
        val amount=edit("","New monthly amount",true)
        val last=db.latestBillMonth(rule.id)
        val from=edit(maxOf(YearMonth.now().plusMonths(1),last?.plusMonths(1)?:YearMonth.now()).toString(),"YYYY-MM")
        val reason=edit("","Reason / contract reference")
        field(form,"NEW MONTHLY AMOUNT",amount);field(form,"EFFECTIVE FROM (YYYY-MM)",from);field(form,"MANDATORY REASON",reason)
        form.addView(txt("Already issued bills and payments retain their historical values.",12f,false,muted))
        val alert=AlertDialog.Builder(activity).setTitle("Revise ${rule.type}")
            .setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save Revision",null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try { db.revise(rule,FinanceFixedPolicy.amount(amount.text.toString()),from.text.toString().trim(),reason.text.toString())
                alert.dismiss();render() } catch(e:Exception){ notify(e.message?:"Unable to revise") }
        }};alert.show()
    }
    private fun showStop(rule:FinanceFixedExpenseStore.Rule) {
        val form=column().apply{setPadding(dp(18),dp(12),dp(18),dp(6))}
        val last=db.latestBillMonth(rule.id)
        val month=edit(maxOf(YearMonth.now().plusMonths(1),last?.plusMonths(1)?:YearMonth.now().plusMonths(1)).toString(),"YYYY-MM")
        val reason=edit("","Reason")
        field(form,"STOP GENERATING FROM (YYYY-MM)",month);field(form,"MANDATORY REASON",reason)
        val alert=AlertDialog.Builder(activity).setTitle("Stop recurring obligation?")
            .setView(form).setNegativeButton("Cancel",null).setPositiveButton("Stop Schedule",null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try { db.stop(rule,month.text.toString().trim(),reason.text.toString());alert.dismiss();render() }
            catch(e:Exception){notify(e.message?:"Unable to stop")}
        }};alert.show()
    }
    private fun showPayment(bill:FinanceFixedExpenseStore.Bill) {
        val form=column().apply{setPadding(dp(18),dp(12),dp(18),dp(6))}
        val amount=edit(FinanceFixedPolicy.rupees(bill.remaining).removePrefix("₹"),"Amount ₹",true)
        val date=edit(LocalDate.now().toString(),"YYYY-MM-DD")
        val methods=listOf("Bank","UPI","Cash","Other")
        val method=spinner(methods);val reference=edit("","Reference / remarks")
        field(form,"PAYMENT AMOUNT",amount);field(form,"ACTUAL PAYMENT DATE",date)
        field(form,"METHOD",method);field(form,"REFERENCE",reference)
        val alert=AlertDialog.Builder(activity).setTitle("Payment · ${bill.type}")
            .setView(form).setNegativeButton("Cancel",null).setPositiveButton("Record",null).create()
        alert.setOnShowListener { alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                FinanceActivityStore(activity).use { ledger ->
                    ledger.synchronize(includeManual=false)
                    ledger.payBill(bill.id,FinanceFixedPolicy.amount(amount.text.toString()),date.text.toString().trim(),methods[method.selectedItemPosition],reference.text.toString())
                }
                alert.dismiss();render();onClosed() }catch(e:Exception){notify(e.message?:"Unable to record payment")}
        }};alert.show()
    }
    private fun showHistory() {
        val text=db.history().joinToString("\n\n") { "${java.time.Instant.ofEpochMilli(it.at)}  ·  ${it.action}\n${it.detail}" }.ifBlank {"No events yet"}
        AlertDialog.Builder(activity).setTitle("Recurring expense audit history").setMessage(text)
            .setPositiveButton("Close",null).show()
    }
}
