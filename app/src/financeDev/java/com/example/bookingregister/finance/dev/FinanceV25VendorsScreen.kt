package com.example.bookingregister.finance.dev

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.bookingregister.finance.domain.FinanceV25VendorSelectionPolicy
import java.time.LocalDate
import java.util.Locale

/** V25 UI only. All balances and lines come directly from the existing V17 payable store. */
internal class FinanceV25VendorsScreen(
    private val activity: AppCompatActivity,
    private val drafts: List<FinanceDemoDraftBill>,
    private val selectedVendor: String?,
    private val onOpen: (String?) -> Unit,
    private val onAdd: () -> Unit,
    private val onEdit: (String) -> Unit,
    private val onRecurringContracts: () -> Unit,
    private val onPay: (String) -> Unit,
    private val onRecordAdvance: (String) -> Unit,
    private val onAllocateAdvance: (String) -> Unit,
    private val onShare: (String) -> Unit,
    private val onDownload: (String, String) -> Unit
) {
    private val forest = Color.rgb(7, 112, 76)
    private val ink = Color.rgb(32, 45, 61)
    private val muted = Color.rgb(102, 114, 125)
    private val danger = Color.rgb(181, 47, 55)
    private val pale = Color.rgb(243, 247, 245)
    private fun dp(n: Int) = (activity.resources.displayMetrics.density * n + .5f).toInt()
    private fun col() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun space(n: Int) = View(activity).apply { layoutParams = LinearLayout.LayoutParams(1, dp(n)) }
    private fun label(value: String, size: Float = 13f, bold: Boolean = false, color: Int = ink) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun card() = col().apply {
        background = GradientDrawable().apply {
            setColor(Color.WHITE); cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.rgb(227, 234, 230))
        }
        setPadding(dp(12), dp(12), dp(12), dp(12))
    }
    private fun button(value: String, action: () -> Unit) = label(value, 13f, true, forest).apply {
        background = GradientDrawable().apply { setColor(pale); cornerRadius = dp(10).toFloat() }
        setPadding(dp(13), dp(13), dp(13), dp(13))
        gravity = Gravity.CENTER
        isClickable = true; isFocusable = true; setOnClickListener { action() }
    }
    private fun money(paise: Long): String {
        val raw = java.math.BigDecimal.valueOf(paise, 2).toPlainString()
        val digits = raw.removePrefix("-").substringBefore(".")
        val head = digits.dropLast(3)
        val grouped = if (head.isEmpty()) digits else
            head.reversed().chunked(2).map { it.reversed() }.asReversed().joinToString(",") +
                "," + digits.takeLast(3)
        val cents = if (paise % 100L == 0L) "" else "." + raw.substringAfter(".")
        return (if (paise < 0L) "-₹" else "₹") + grouped + cents
    }
    private fun exact(rows: List<Long>) = rows.fold(0L, Math::addExact)
    private fun isVendor(id: String) = FinanceV25VendorSelectionPolicy.canSelectVendor(id)

    private data class Account(val id: String, val name: String, val registered: Boolean,
                               val archived: Boolean, val bills: List<FinanceActivityStore.Bill>,
                               val payments: List<FinanceActivityStore.Payment>,
                               val events: List<FinanceActivityStore.Event>) {
        val due get() = bills.fold(0L) { a, b -> Math.addExact(a, b.due) }
        val advances get() = payments.filter { it.kind == "ADVANCE" }.fold(0L) { a, p -> Math.addExact(a, p.available) }
        val billed get() = bills.fold(0L) { a, b -> Math.addExact(a, b.amount) }
    }

    private fun accounts(): List<Account> {
        val master = HotelPurchaseVendorStore(activity)
        val suppliers = master.allIncludingArchived().filter { isVendor(it.id) }
        val names = linkedMapOf<String, String>()
        suppliers.forEach { names[it.id] = it.name }
        return FinanceActivityStore(activity).use { ledger ->
            ledger.synchronize(drafts)
            val today = LocalDate.now()
            val bills = ledger.bills().filter { isVendor(it.vendorId) && !LocalDate.parse(it.date).isAfter(today) }
            val payments = ledger.payments().filter { isVendor(it.vendorId) && !LocalDate.parse(it.date).isAfter(today) }
            val events = ledger.events().filter { isVendor(it.vendorId) && !LocalDate.parse(it.date).isAfter(today) }
            bills.forEach { names.putIfAbsent(it.vendorId, it.vendor) }
            payments.forEach { names.putIfAbsent(it.vendorId, it.vendor) }
            names.map { (id, name) ->
                Account(id, name, suppliers.any { it.id == id }, master.isArchived(id),
                    bills.filter { it.vendorId == id }, payments.filter { it.vendorId == id },
                    events.filter { it.vendorId == id })
            }.sortedBy { it.name.lowercase(Locale.ROOT) }
        }
    }

    fun render(parent: LinearLayout) {
        parent.removeAllViews()
        try {
            val all = accounts()
            val selected = all.firstOrNull { it.id == selectedVendor }
            if (selectedVendor != null && selected != null) vendor(parent, selected)
            else directory(parent, all)
        } catch (error: Exception) {
            parent.addView(label("Vendor records could not be loaded: ${error.message ?: "Review the ledger"}", 14f, true, danger))
        }
    }

    private fun directory(parent: LinearLayout, all: List<Account>) {
        parent.addView(row().apply {
            addView(col().apply {
                addView(label("Suppliers and landlords", 12f, false, muted))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(button("+ Add vendor", onAdd))
        })
        parent.addView(space(13))
        val search = EditText(activity).apply {
            hint = "Search vendors"; setSingleLine(true); textSize = 15f
        }
        parent.addView(search)
        parent.addView(space(9))
        val list = col()
        parent.addView(list)
        fun display(query: String) {
            list.removeAllViews()
            val matching = all.filter { it.name.contains(query.trim(), true) }
            list.addView(label("${matching.size} vendors", 12f, true, muted))
            list.addView(space(8))
            if (matching.isEmpty()) list.addView(label("No matching vendors. Use Add vendor above.", 13f, false, muted))
            matching.forEach { account ->
                list.addView(card().apply {
                    addView(row().apply {
                        addView(col().apply {
                            addView(label(account.name, 15f, true))
                            addView(label(if (account.archived) "Archived" else if (account.registered) "Registered vendor" else "Historical payee",
                                11f, false, muted))
                        }, LinearLayout.LayoutParams(0, -2, 1f))
                        addView(col().apply {
                            gravity = Gravity.END
                            addView(label(if (account.due > 0L) "Due ${money(account.due)}" else "No dues", 13f, true,
                                if (account.due > 0L) danger else forest).apply { gravity = Gravity.END })
                            if (account.advances > 0) addView(label("Advance ${money(account.advances)}", 11f, false, forest))
                        })
                    })
                    contentDescription = "Open ${account.name} vendor ledger"
                    isClickable = true; isFocusable = true
                    setOnClickListener { onOpen(account.id) }
                })
                list.addView(space(8))
            }
        }
        display("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { display(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        parent.addView(space(13))
        val activeContracts = runCatching {
            FinanceFixedExpenseStore(activity).use { store ->
                store.rules().count { it.active }
            }
        }.getOrNull()
        parent.addView(card().apply {
            addView(row().apply {
                addView(col().apply {
                    addView(label("Recurring Contracts", 16f, true))
                    addView(label("Rent and other regular vendor agreements", 11f, false, muted))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(label(if (activeContracts == null) "Open  ›"
                    else "$activeContracts Contracts  ›", 12f, true, forest))
            })
            isClickable = true; isFocusable = true
            contentDescription = "Open recurring contracts"
            setOnClickListener { onRecurringContracts() }
        })
        parent.addView(space(16))
    }

    private fun vendor(parent: LinearLayout, account: Account) {
        parent.addView(button("‹ All vendors") { onOpen(null) })
        parent.addView(space(12))
        parent.addView(label(account.name, 21f, true))
        parent.addView(label(if (account.registered) "Registered supplier" else "Historical payee · Master missing", 12f, false, muted))
        parent.addView(space(12))
        parent.addView(card().apply {
            addView(label("Vendor balance", 12f, false, muted))
            addView(label("Outstanding ${money(account.due)}", 20f, true, if (account.due > 0) danger else forest))
            addView(label("Advance available ${money(account.advances)}", 13f, true, forest))
            addView(label("Total recorded bills ${money(account.billed)}", 12f, false, muted))
        })
        parent.addView(space(10))
        parent.addView(row().apply {
            addView(button("Pay vendor") { onPay(account.id) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(space(7))
            addView(button("Edit categories") { onEdit(account.id) }.apply { isEnabled = account.registered }, LinearLayout.LayoutParams(0, -2, 1f))
        })
        parent.addView(space(8))
        if (account.registered && !account.archived) {
            parent.addView(button("+ Record vendor advance") { onRecordAdvance(account.id) })
            parent.addView(space(8))
        }
        parent.addView(row().apply {
            addView(button("Share ledger") { onShare(shareText(account.id)) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(space(7))
            addView(button("Download CSV") { val (name, content) = statement(account.id); onDownload(name, content) }, LinearLayout.LayoutParams(0, -2, 1f))
        })
        val openAdvances = account.payments.filter { it.kind == "ADVANCE" && it.available > 0L }
        if (openAdvances.isNotEmpty()) {
            parent.addView(space(14))
            parent.addView(label("Available vendor advances", 16f, true))
            parent.addView(label("Allocate an existing advance to a bill without recording another payment.", 11f, false, muted))
            parent.addView(space(8))
            openAdvances.forEach { advance ->
                parent.addView(card().apply {
                    addView(row().apply {
                        addView(col().apply {
                            addView(label("Advance - ${advance.date}", 13f, true))
                            addView(label("${advance.method}  ${advance.reference}", 11f, false, muted))
                        }, LinearLayout.LayoutParams(0, -2, 1f))
                        addView(label(money(advance.available), 14f, true, forest))
                    })
                    addView(space(9))
                    addView(button("Allocate to bill") { onAllocateAdvance(advance.id) })
                })
                parent.addView(space(8))
            }
        }
        parent.addView(space(14))
        parent.addView(label("Recent ledger activity", 16f, true))
        parent.addView(label("Bills and payments below are from the existing payable ledger only.", 11f, false, muted))
        parent.addView(space(8))
        if (account.events.isEmpty()) parent.addView(label("No recorded entries yet.", 13f, false, muted))
        account.events.take(80).forEach { entry ->
            parent.addView(card().apply {
                addView(row().apply {
                    addView(col().apply {
                        addView(label(entry.title, 13f, true))
                        addView(label("${entry.date} · ${entry.type}", 11f, false, muted))
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(label(money(entry.amount), 13f, true))
                })
            })
            parent.addView(space(6))
        }
        if (account.events.size > 80) parent.addView(label("Showing latest 80 items; export includes the complete register.", 11f, false, muted))
        parent.addView(space(24))
    }

    /** Export is a projection only. It never creates or settles a bill. */
    fun statement(vendorId: String): Pair<String, String> {
        require(isVendor(vendorId)) { "Employee accounts are exclusively in Manpower" }
        val account = accounts().firstOrNull { it.id == vendorId } ?: error("Vendor not found")
        val title = account.name.replace(Regex("[^A-Za-z0-9_-]+"), "_").take(48).ifBlank { "vendor" }
        fun csv(v: String): String {
            val first = v.trimStart().firstOrNull()
            val safe = if (first != null && first in listOf('=', '+', '-', '@', '\t')) "'" + v else v
            return "\"" + safe.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""
        }
        val rows = mutableListOf(listOf("Finance DEV · Vendor Statement · Not audited"),
            listOf("Vendor", account.name), listOf("Outstanding paise", account.due.toString()),
            listOf("Unallocated advance paise", account.advances.toString()),
            listOf("Note", "Earlier manual settled amounts may not have actual payment dates"),
            listOf("Date", "Event ID", "Event Type", "Title", "Details", "Amount (paise)"))
        account.events.sortedWith(compareBy<FinanceActivityStore.Event> { it.date }.thenBy { it.id }).forEach { e ->
            rows.add(listOf(e.date, e.id, e.type, e.title, e.detail, e.amount.toString()))
        }
        return "$title-ledger.csv" to rows.joinToString("\r\n") { r -> r.joinToString(",", transform = ::csv) }
    }

    fun shareText(vendorId: String): String {
        require(isVendor(vendorId)) { "Employee accounts are exclusively in Manpower" }
        val a = accounts().firstOrNull { it.id == vendorId } ?: error("Vendor not found")
        return buildString {
            appendLine("Booking Register Finance | ${a.name}")
            appendLine("Recorded bills: ${money(a.billed)}")
            appendLine("Outstanding: ${money(a.due)}")
            appendLine("Unallocated advances: ${money(a.advances)}")
            appendLine("Latest transactions:")
            a.events.take(25).forEach { appendLine("${it.date} | ${it.title} | ${money(it.amount)}") }
            if (a.events.size > 25) appendLine("More transactions exist; download the full CSV.")
            appendLine("Local Finance DEV records; confirm against source documents.")
        }
    }
}
