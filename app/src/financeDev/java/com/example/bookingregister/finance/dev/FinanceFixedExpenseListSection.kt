package com.example.bookingregister.finance.dev

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.time.YearMonth

/** Expense-tab projection: one read-only view of the ORIGINAL fixed bills.
 * The Fixed Expenses screen remains the sole editor/payment entry point.
 */
internal class FinanceFixedExpenseListSection(
    private val activity: AppCompatActivity,
    private val onChanged: () -> Unit
) {
    private val navy = Color.rgb(27, 43, 61)
    private val muted = Color.rgb(102, 115, 131)
    private val green = Color.rgb(6, 108, 72)
    private val amber = Color.rgb(166, 99, 9)
    private val red = Color.rgb(185, 55, 63)
    private val border = Color.rgb(225, 231, 237)

    private fun dp(n: Int): Int = (n * activity.resources.displayMetrics.density + .5f).toInt()
    private fun text(value: String, size: Float = 13f, bold: Boolean = false, color: Int = navy): TextView =
        TextView(activity).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    private fun rounded(fill: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(13).toFloat()
        setStroke(dp(1), border)
    }
    private fun column(): LinearLayout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun openManager() {
        FinanceFixedExpensesScreen(activity, onChanged).show()
    }
    private fun money(paise: Long) = FinanceFixedPolicy.rupees(paise)

    fun render(parent: LinearLayout, filter: String) {
        val all = try {
            FinanceFixedExpenseStore(activity).use { store ->
                // Idempotent issue; a new month appears in Expenses without opening Fixed Expenses first.
                store.issueThrough(YearMonth.now())
                store.bills()
            }
        } catch (error: Exception) {
            parent.addView(text("Fixed expense records could not be loaded: ${error.message ?: "Unknown error"}", 12f, true, red))
            return
        }

        val visible = FinanceFixedExpenseListPolicy.visible(all, filter)
        val totals = FinanceFixedExpenseListPolicy.summary(all)

        val heading = row()
        val names = column().apply {
            addView(text("Fixed expense bills", 17f, true))
            addView(text("${all.size} issued bills · all months · one record per period", 11f, false, muted))
        }
        heading.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
        val manage = text("Manage  ›", 13f, true, green).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(8), dp(10))
            contentDescription = "Open fixed expenses"
            setOnClickListener { openManager() }
        }
        heading.addView(manage)
        parent.addView(heading)
        parent.addView(space(7))

        val summary = column().apply {
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = rounded(0xFFF1F7F4.toInt())
            addView(text("ALL ISSUED FIXED BILLS (LOCAL DEV)", 11f, true, green))
            addView(text(money(totals.billedPaise), 22f, true))
            addView(space(5))
            addView(text("Paid ${money(totals.paidPaise)}     ·     Unpaid ${money(totals.duePaise)}", 12f, true))
            addView(text("Separate from manual drafts. Not posted to accounts.", 11f, false, muted))
        }
        parent.addView(summary)
        parent.addView(space(8))

        if (all.isEmpty()) {
            parent.addView(text("No fixed bills yet. Use Manage to create a recurring expense.", 13f, false, muted))
        } else if (visible.isEmpty()) {
            parent.addView(text("No fixed bills match the selected payment filter.", 13f, false, muted))
        } else {
            parent.addView(text("${visible.size} fixed bill(s) · ${filter.lowercase()} payment filter", 12f, false, muted))
            visible.forEach { bill ->
                val paymentStatus = FinanceFixedExpenseListPolicy.status(bill)
                val color = when (paymentStatus) {
                    "Paid" -> green
                    "Partially paid" -> amber
                    else -> red
                }
                val card = column().apply {
                    setPadding(dp(13), dp(12), dp(13), dp(12))
                    background = rounded(Color.WHITE)
                    isClickable = true
                    isFocusable = true
                    contentDescription = "${bill.type}, ${bill.month}. Total ${money(bill.amount)}, paid ${money(bill.paid)}, balance ${money(bill.remaining)}. Manage fixed expenses"
                    setOnClickListener { openManager() }
                }
                val top = row()
                top.addView(column().apply {
                    addView(text(bill.type, 15f, true))
                    addView(text("FIXED · ${bill.vendor} · ${bill.department}", 11f, false, muted))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                top.addView(column().apply {
                    addView(text(money(bill.amount), 15f, true).apply { gravity = Gravity.END })
                    addView(text(paymentStatus.uppercase(), 11f, true, color).apply { gravity = Gravity.END })
                })
                card.addView(top)
                card.addView(space(7))
                card.addView(text("${bill.month} · Due ${bill.dueDate}", 11f, false, muted))
                card.addView(text("Paid ${money(bill.paid)}     ·     Balance ${money(bill.remaining)}", 12f, true,
                    if (bill.remaining == 0L) green else navy))
                parent.addView(card)
                parent.addView(space(7))
            }
        }
        parent.addView(space(14))
        parent.addView(text("Manual expense drafts", 16f, true))
        parent.addView(text("These are separate local draft records; fixed bills are not re-entered here.", 11f, false, muted))
        parent.addView(space(7))
    }

    private fun space(height: Int) = android.view.View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }
}
