package com.example.bookingregister.finance.dev

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * Finance DEV full-screen supplier ledger. Uses the existing activity ledger, vendor masters,
 * vendor advances and allocation methods; it never writes an alternate balance table.
 * Staff and employee payroll are handled only by Manpower.
 */
internal class FinanceLedgerHubScreen(
    private val activity: AppCompatActivity,
    private val onClose: () -> Unit,
    private val onNewAdvance: () -> Unit,
    private val onAllocateAdvance: (String) -> Unit
) {
    private data class VendorAccount(
        val id: String, val name: String, val outstanding: Long,
        val advance: Long, val entries: Int
    )
    private data class Snapshot(
        val vendors: List<VendorAccount>,
        val advances: List<FinanceActivityStore.Payment>
    )

    private val navy = 0xFF172B46.toInt()
    private val ink = 0xFF24344B.toInt()
    private val slate = 0xFF738197.toInt()
    private val blue = 0xFF315CB7.toInt()
    private val green = 0xFF117B64.toInt()
    private val amber = 0xFFAE6518.toInt()
    private val red = 0xFFBE4653.toInt()
    private val line = 0xFFE3E9F1.toInt()
    private val mist = 0xFFF5F7FA.toInt()
    private var tabIndex = 0
    private var vendorId: String? = null
    private var backCallback: OnBackPressedCallback? = null
    private lateinit var root: LinearLayout

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density + 0.5f).toInt()
    private fun col() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }
    private fun gap(value: Int) = View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(value))
    }
    private fun rule() = View(activity).apply {
        setBackgroundColor(line); layoutParams = LinearLayout.LayoutParams(-1, dp(1))
    }
    private fun rounded(color: Int, radius: Int = 14, border: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (border) setStroke(dp(1), line)
    }
    private fun label(value: String, size: Float = 14f, bold: Boolean = false, color: Int = ink) =
        TextView(activity).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    private fun rupees(paise: Long) = "₹" + NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
        minimumFractionDigits = if (paise % 100L == 0L) 0 else 2
        maximumFractionDigits = 2
    }.format(paise / 100.0)
    private fun isVendor(id: String): Boolean = id.isNotBlank() && id != "no_payee" &&
        !id.startsWith("employee:")

    private fun snapshot(): Snapshot {
        val names = linkedMapOf<String, String>()
        HotelPurchaseVendorStore(activity).allIncludingArchived().forEach {
            if (isVendor(it.id)) names[it.id] = it.name
        }
        return FinanceActivityStore(activity).use { ledger ->
            val bills = ledger.bills().filter { isVendor(it.vendorId) }
            val payments = ledger.payments().filter { isVendor(it.vendorId) }
            bills.forEach { names.putIfAbsent(it.vendorId, it.vendor) }
            payments.forEach { names.putIfAbsent(it.vendorId, it.vendor) }
            Snapshot(
                vendors = names.map { (id, name) ->
                    VendorAccount(
                        id = id, name = name,
                        outstanding = bills.filter { it.vendorId == id }.sumOf { it.due },
                        advance = payments.filter { it.vendorId == id && it.kind == "ADVANCE" }.sumOf { it.available },
                        entries = bills.count { it.vendorId == id } + payments.count { it.vendorId == id }
                    )
                }.sortedBy { it.name.lowercase(Locale.ROOT) },
                advances = payments.filter { it.kind == "ADVANCE" && it.available > 0L }
                    .sortedWith(compareByDescending<FinanceActivityStore.Payment> { it.date }.thenBy { it.vendor })
            )
        }
    }

    fun show() = open(null)
    fun showVendor(id: String) {
        require(isVendor(id)) { "Staff accounts belong exclusively in Manpower." }
        open(id)
    }
    private fun open(initialVendor: String?) {
        vendorId = initialVendor
        root = col().apply { setBackgroundColor(Color.WHITE) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { navigateBack() }
        }.also { activity.onBackPressedDispatcher.addCallback(activity, it) }
        activity.setContentView(root)
        ViewCompat.requestApplyInsets(root)
        render()
    }
    private fun navigateBack() {
        if (vendorId != null) {
            vendorId = null
            render()
        } else {
            backCallback?.remove(); backCallback = null
            onClose() // Re-render Expenses; the original bottom navigation returns normally.
        }
    }
    private fun leaveForAction(action: () -> Unit) {
        backCallback?.remove(); backCallback = null
        onClose()
        action() // Same existing advance and allocation methods as before.
    }
    private fun headline(title: String, subtitle: String, back: () -> Unit): LinearLayout = col().apply {
        background = rounded(navy, 16)
        setPadding(dp(12), dp(13), dp(16), dp(15))
        addView(row().apply {
            addView(label("‹", 32f, false, Color.WHITE).apply {
                gravity = Gravity.CENTER
                contentDescription = "Back"
                setOnClickListener { back() }
            }, LinearLayout.LayoutParams(dp(40), dp(48)))
            addView(col().apply {
                addView(label(title, 21f, true, Color.WHITE))
                addView(gap(3))
                addView(label(subtitle, 11.5f, false, 0xFFCBD7EA.toInt()))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(label("FINANCE", 10f, true, 0xFFB9CAE5.toInt()))
        })
    }
    private fun metric(title: String, value: String, tint: Int): LinearLayout = col().apply {
        background = rounded(mist, 12)
        setPadding(dp(12), dp(12), dp(10), dp(12))
        addView(label(title, 11f, false, slate))
        addView(gap(5))
        addView(label(value, 17f, true, tint).apply { maxLines = 2 })
    }
    private fun scrollBody(): LinearLayout {
        val body = col().apply { setPadding(dp(15), dp(14), dp(15), dp(32)) }
        root.addView(ScrollView(activity).apply {
            isFillViewport = false
            addView(body, ViewGroup.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        return body
    }
    private fun render() {
        root.removeAllViews()
        val selectedId = vendorId
        if (selectedId == null) renderDirectory() else renderVendor(selectedId)
    }
    private fun renderDirectory() {
        root.addView(col().apply {
            setPadding(dp(14), dp(10), dp(14), 0)
            addView(headline("Ledger", "Vendors · Balances · Advances", ::navigateBack))
            addView(gap(12))
            addView(row().apply {
                background = rounded(mist, 13)
                setPadding(dp(4), dp(4), dp(4), dp(4))
                listOf("Vendor Ledger", "Vendor Advances").forEachIndexed { index, title ->
                    val chosen = tabIndex == index
                    addView(label(title, 13f, chosen, if (chosen) Color.WHITE else slate).apply {
                        gravity = Gravity.CENTER
                        setPadding(dp(4), dp(12), dp(4), dp(12))
                        background = rounded(if (chosen) blue else Color.TRANSPARENT, 10)
                        setOnClickListener { if (tabIndex != index) { tabIndex = index; render() } }
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                }
            })
        })
        val body = scrollBody()
        try {
            val data = snapshot()
            if (tabIndex == 0) renderVendors(body, data) else renderAdvances(body, data)
        } catch (error: Exception) {
            body.addView(label("Vendor ledger unavailable: ${error.message ?: "Review records"}", 13f, true, red))
        }
    }
    private fun renderVendors(body: LinearLayout, data: Snapshot) {
        body.addView(row().apply {
            addView(metric("Vendors", data.vendors.size.toString(), blue), LinearLayout.LayoutParams(0, -2, 1f))
            addView(gap(8))
            addView(metric("Outstanding", rupees(data.vendors.sumOf { it.outstanding }), red),
                LinearLayout.LayoutParams(0, -2, 1f))
        }); body.addView(gap(13))
        val search = EditText(activity).apply {
            hint = "Search vendor"
            textSize = 15f
            setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, 12, border = true)
        }
        body.addView(search); body.addView(gap(12))
        val list = col()
        body.addView(list)
        fun filter(query: String) {
            list.removeAllViews()
            val matching = data.vendors.filter { it.name.contains(query.trim(), ignoreCase = true) }
            list.addView(label("${matching.size} VENDORS", 11f, true, slate).apply {
                setPadding(dp(4), dp(3), 0, dp(9))
            })
            if (matching.isEmpty()) {
                list.addView(label("No vendors found.", 13f, false, slate))
                return
            }
            matching.forEach { vendor ->
                val tile = row().apply {
                    setPadding(dp(13), dp(14), dp(12), dp(14))
                    background = rounded(Color.WHITE, 13, border = true)
                }
                val initial = vendor.name.trim().take(1).uppercase(Locale.ROOT).ifBlank { "V" }
                tile.addView(label(initial, 17f, true, blue).apply {
                    background = rounded(0xFFEAF1FF.toInt(), 12)
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(dp(43), dp(43)))
                tile.addView(gap(11))
                tile.addView(col().apply {
                    addView(label(vendor.name, 15f, true).apply { maxLines = 1 })
                    addView(gap(3))
                    addView(label("${vendor.entries} entries", 11f, false, slate))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                tile.addView(col().apply {
                    gravity = Gravity.END
                    addView(label(rupees(vendor.outstanding), 13f, true,
                        if (vendor.outstanding > 0L) red else green).apply { gravity = Gravity.END })
                    addView(gap(3))
                    addView(label(if (vendor.advance > 0) "Credit ${rupees(vendor.advance)}" else "View ledger  ›",
                        10f, false, if (vendor.advance > 0) amber else blue).apply { gravity = Gravity.END })
                })
                tile.contentDescription = "Open vendor ledger for ${vendor.name}"
                tile.isClickable = true
                tile.isFocusable = true
                tile.setOnClickListener { vendorId = vendor.id; render() }
                list.addView(tile); list.addView(gap(8))
            }
        }
        filter("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        body.addView(gap(16))
        body.addView(label("Only vendors appear here. Staff salaries and advances remain in Manpower.", 11f, false, slate))
    }
    private fun renderAdvances(body: LinearLayout, data: Snapshot) {
        body.addView(row().apply {
            addView(metric("Available vendor credits", rupees(data.advances.sumOf { it.available }), amber),
                LinearLayout.LayoutParams(0, -2, 1f))
            addView(gap(8))
            addView(metric("Open advances", data.advances.size.toString(), blue),
                LinearLayout.LayoutParams(0, -2, 1f))
        }); body.addView(gap(14))
        body.addView(label("+ Record Vendor Advance", 14f, true, Color.WHITE).apply {
            gravity = Gravity.CENTER
            background = rounded(green, 13)
            setPadding(dp(12), dp(15), dp(12), dp(15))
            setOnClickListener { leaveForAction(onNewAdvance) }
        })
        body.addView(gap(13))
        if (data.advances.isEmpty()) {
            body.addView(label("No available vendor advances. Newly recorded advances will appear here.",
                13f, false, slate))
        }
        data.advances.forEach { advance ->
            body.addView(col().apply {
                background = rounded(Color.WHITE, 13, border = true)
                setPadding(dp(12), dp(13), dp(12), dp(12))
                addView(row().apply {
                    addView(col().apply {
                        addView(label(advance.vendor, 15f, true))
                        addView(gap(3))
                        addView(label("${advance.date} · ${advance.method}", 11f, false, slate))
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(label(rupees(advance.available), 15f, true, amber))
                })
                addView(gap(11))
                addView(label("Allocate to bill  ›", 12f, true, blue).apply {
                    setPadding(dp(2), dp(7), dp(2), dp(7))
                    setOnClickListener { leaveForAction { onAllocateAdvance(advance.id) } }
                })
            })
            body.addView(gap(9))
        }
        body.addView(gap(12))
        body.addView(label("Allocating a vendor advance to a bill does not create a second payment.", 11f, false, slate))
    }
    private fun renderVendor(id: String) {
        val displayName = runCatching {
            snapshot().vendors.firstOrNull { it.id == id }?.name
        }.getOrNull() ?: "Vendor"
        root.addView(col().apply {
            setPadding(dp(14), dp(10), dp(14), 0)
            addView(headline(displayName, "Vendor Ledger · Transaction register", ::navigateBack))
        })
        val body = scrollBody()
        try {
            FinanceActivityStore(activity).use { ledger ->
                val today = LocalDate.now()
                val bills = ledger.bills().filter { it.vendorId == id }
                val payments = ledger.payments().filter { it.vendorId == id }
                val events = ledger.events().filter { it.vendorId == id && !LocalDate.parse(it.date).isAfter(today) }
                    .sortedWith(compareBy<FinanceActivityStore.Event> { it.date }
                        .thenBy { when (it.type) { "BILL" -> 0; "ADVANCE" -> 1; "PAYMENT" -> 2; "ADJUSTMENT" -> 3; else -> 4 } }
                        .thenBy { it.id })
                val outstanding = bills.filter { !LocalDate.parse(it.date).isAfter(today) }.sumOf { it.due }
                val credits = payments.filter { it.kind == "ADVANCE" }.sumOf { it.available }
                body.addView(row().apply {
                    addView(metric("Outstanding", rupees(outstanding), if (outstanding > 0L) red else green),
                        LinearLayout.LayoutParams(0, -2, 1f))
                    addView(gap(8))
                    addView(metric("Vendor advances", rupees(credits), amber),
                        LinearLayout.LayoutParams(0, -2, 1f))
                }); body.addView(gap(17))
                body.addView(label("TRANSACTION REGISTER", 11f, true, slate)); body.addView(gap(9))
                body.addView(row().apply {
                    background = rounded(mist, 11)
                    setPadding(dp(9), dp(10), dp(9), dp(10))
                    addView(label("Date / Particulars", 11f, true), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(label("Debit / Credit", 11f, true).apply { gravity = Gravity.END })
                }); body.addView(gap(6))
                if (events.isEmpty()) body.addView(label("No dated transactions for this vendor.", 13f, false, slate))
                var running = 0L
                events.forEach { event ->
                    val debit = if (event.type == "PAYMENT" || event.type == "ADJUSTMENT") event.amount else 0L
                    val credit = when (event.type) {
                        "BILL" -> event.amount
                        "REVERSAL" -> if (event.detail.contains(" · PAYMENT · ")) event.amount else 0L
                        else -> 0L
                    }
                    running = Math.subtractExact(Math.addExact(running, credit), debit)
                    body.addView(row().apply {
                        setPadding(dp(7), dp(13), dp(7), dp(13))
                        addView(col().apply {
                            addView(label(event.date, 10f, false, slate))
                            addView(gap(3))
                            addView(label(event.title, 14f, true))
                            addView(gap(3))
                            addView(label(event.detail, 11f, false, slate).apply { maxLines = 3 })
                            addView(gap(4))
                            addView(label("Running payable ${rupees(running)}", 11f, true,
                                if (running > 0L) red else green))
                        }, LinearLayout.LayoutParams(0, -2, 1f))
                        addView(col().apply {
                            gravity = Gravity.END
                            if (debit > 0L) addView(label("Dr ${rupees(debit)}", 12f, true, green))
                            if (credit > 0L) addView(label("Cr ${rupees(credit)}", 12f, true, red))
                            if (debit == 0L && credit == 0L) addView(label("—", 12f, false, slate))
                        }, LinearLayout.LayoutParams(dp(104), -2))
                    })
                    body.addView(rule())
                }
                if (bills.any { it.openingPaid > 0L }) {
                    body.addView(gap(11))
                    body.addView(label("Earlier undated settlements affect outstanding totals, but cannot appear as dated transactions.",
                        11f, false, amber))
                }
                body.addView(gap(14))
                body.addView(label("Local Finance DEV register · Not a statutory double-entry ledger.", 10f, false, slate))
            }
        } catch (error: Exception) {
            body.addView(label("Unable to show vendor transactions: ${error.message ?: "Review data"}", 13f, true, red))
        }
    }
}
