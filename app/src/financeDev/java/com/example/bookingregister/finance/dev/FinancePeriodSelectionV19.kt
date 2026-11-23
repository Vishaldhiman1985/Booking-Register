package com.example.bookingregister.finance.dev

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display-only period selection shared by Dashboard and Finance Statement.
 * Stores UI preferences only: no booking, expense, payment or tax records are changed.
 */
internal class FinancePeriodSelectionV19(private val activity: AppCompatActivity) {
    data class Selection(val month: YearMonth, val allTime: Boolean) {
        val periodLabel: String get() = if (allTime) "All recorded periods"
            else month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
    }

    private val preferences = activity.getSharedPreferences("finance_dev_period_selection_v19", Context.MODE_PRIVATE)
    private val ink = Color.rgb(29, 43, 58)
    private val forest = Color.rgb(12, 111, 80)
    private val pale = Color.rgb(240, 246, 243)
    private fun dp(n: Int): Int = (activity.resources.displayMetrics.density * n + .5f).toInt()

    fun read(): Selection {
        val now = YearMonth.now()
        val previous = runCatching { YearMonth.parse(preferences.getString("month", now.toString())) }
            .getOrDefault(now)
        return Selection(if (previous.isAfter(now)) now else previous, preferences.getBoolean("all_time", false))
    }

    private fun save(month: YearMonth, allTime: Boolean) {
        require(!month.isAfter(YearMonth.now())) { "Future periods are unavailable" }
        check(preferences.edit().putString("month", month.toString())
            .putBoolean("all_time", allTime).commit()) { "Could not save reporting period" }
    }

    private fun pill(label: String, selected: Boolean, onClick: () -> Unit) = TextView(activity).apply {
        text = label
        textSize = 12f
        gravity = Gravity.CENTER
        setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(if (selected) Color.WHITE else forest)
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(if (selected) forest else pale)
        }
        minHeight = dp(42)
        setPadding(dp(6), dp(9), dp(6), dp(9))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    fun render(parent: LinearLayout, onUpdate: () -> Unit) {
        val selection = read()
        val tabs = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        tabs.addView(pill("Monthly", !selection.allTime) {
            save(selection.month, false); onUpdate()
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        tabs.addView(View(activity), LinearLayout.LayoutParams(dp(8), 1))
        tabs.addView(pill("All time", selection.allTime) {
            save(selection.month, true); onUpdate()
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        parent.addView(tabs)
        parent.addView(View(activity).apply { minimumHeight = dp(9) })

        if (selection.allTime) {
            parent.addView(TextView(activity).apply {
                text = "All recorded periods · current unpaid balances"
                textSize = 12f
                setTextColor(ink)
                gravity = Gravity.CENTER
                setPadding(0, dp(7), 0, dp(7))
            })
        } else {
            val months = LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL
            }
            months.addView(pill("‹", false) {
                save(selection.month.minusMonths(1), false); onUpdate()
            }, LinearLayout.LayoutParams(dp(46), dp(43)))
            months.addView(TextView(activity).apply {
                text = selection.periodLabel
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ink)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(0, dp(43), 1f))
            val next = pill("›", false) {
                if (selection.month.isBefore(YearMonth.now())) {
                    save(selection.month.plusMonths(1), false); onUpdate()
                }
            }
            next.isEnabled = selection.month.isBefore(YearMonth.now())
            next.alpha = if (next.isEnabled) 1f else .35f
            months.addView(next, LinearLayout.LayoutParams(dp(46), dp(43)))
            parent.addView(months)
        }
        parent.addView(View(activity).apply { minimumHeight = dp(9) })
    }
}
