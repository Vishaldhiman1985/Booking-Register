package com.example.bookingregister.finance.dev

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
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

/** A read-only, full-screen quality worklist. No automatic item or stock changes. */
internal class FinanceCatalogQualityScreen(private val activity: Activity) {
    private val ink = Color.rgb(30, 46, 64)
    private val secondary = Color.rgb(100, 116, 135)
    private val border = Color.rgb(225, 232, 240)
    private val surface = Color.rgb(247, 249, 252)
    private val teal = Color.rgb(5, 112, 82)
    private fun dp(n: Int) = (activity.resources.displayMetrics.density * n + 0.5f).toInt()

    private fun round(fill: Int, outline: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(14).toFloat()
        setColor(fill)
        if (outline != null) setStroke(dp(1), outline)
    }

    private fun label(message: String, size: Float = 14f, bold: Boolean = false, color: Int = ink) =
        TextView(activity).apply {
            text = message
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun col() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun gap(size: Int = 9) = View(activity).apply { layoutParams = LinearLayout.LayoutParams(1, dp(size)) }

    fun show(
        groupCount: Int,
        itemCount: Int,
        typeCount: Int,
        stockableCount: Int,
        findings: List<FinanceQualityIssue>,
        inspect: (FinanceQualityIssue) -> Unit
    ) {
        val dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
        val root = col().apply { setBackgroundColor(surface) }
        val top = row().apply { setPadding(dp(18), dp(14), dp(18), dp(13)); setBackgroundColor(Color.WHITE) }
        top.addView(col().apply {
            addView(label("CATALOG HEALTH", 11f, true, teal))
            addView(label("Quality Review", 21f, true))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(label("Close", 14f, true, teal).apply {
            setPadding(dp(14), dp(12), dp(4), dp(12))
            isClickable = true
            setOnClickListener { dialog.dismiss() }
        })
        root.addView(top)

        val intro = col().apply { setPadding(dp(18), dp(17), dp(18), dp(8)) }
        intro.addView(label("$itemCount catalog items  |  $groupCount item groups", 16f, true))
        intro.addView(gap(5))
        intro.addView(label("$typeCount CA expense types  |  $stockableCount stock-enabled types", 12f, false, secondary))
        intro.addView(gap(12))
        intro.addView(label(
            "Review suggestions only. Nothing on this screen updates item IDs, stock quantities or accounting records.",
            12f, false, secondary
        ))
        root.addView(intro)

        val filters = row().apply { setPadding(dp(18), dp(5), dp(18), dp(12)) }
        val search = EditText(activity).apply {
            hint = "Search issues, products or codes"
            isSingleLine = true
            textSize = 14f
            setPadding(dp(12), 0, dp(10), 0)
            background = round(Color.WHITE, border)
        }
        filters.addView(search, LinearLayout.LayoutParams(0, dp(48), 1f))
        filters.addView(View(activity), LinearLayout.LayoutParams(dp(8), 1))
        val filterNames = listOf("All", "Priority", "Unit review", "Duplicate")
        val selector = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, filterNames)
            minimumHeight = dp(48)
        }
        filters.addView(selector, LinearLayout.LayoutParams(dp(116), dp(48)))
        root.addView(filters)

        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val content = col().apply { setPadding(dp(18), dp(3), dp(18), dp(24)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        fun draw() {
            content.removeAllViews()
            val query = search.text.toString().trim()
            val selected = selector.selectedItemPosition
            val visible = findings.filter { issue ->
                val matchesFilter = when (selected) {
                    1 -> issue.priority <= 1
                    2 -> issue.kind == "Unit review"
                    3 -> issue.kind == "Duplicate"
                    else -> true
                }
                matchesFilter && (query.isBlank() || listOf(issue.title, issue.detail, issue.kind, issue.typeCode.orEmpty(),
                    issue.groupId.orEmpty()).any { it.contains(query, ignoreCase = true) })
            }
            content.addView(label("${visible.size} findings  |  ${findings.count { it.priority <= 1 }} requiring priority review", 13f, true))
            content.addView(gap(11))
            if (findings.isEmpty()) {
                content.addView(col().apply {
                    background = round(Color.WHITE, border)
                    setPadding(dp(18), dp(22), dp(18), dp(22))
                    addView(label("No issues found by the current checks", 16f, true, teal))
                    addView(gap(6))
                    addView(label("This is not a certification of catalog completeness or CA approval.", 13f, false, secondary))
                })
            } else if (visible.isEmpty()) {
                content.addView(label("No matching findings. Try another filter or search.", 13f, false, secondary))
            }
            visible.take(80).forEach { issue ->
                val panel = col().apply {
                    background = round(Color.WHITE, border)
                    setPadding(dp(14), dp(13), dp(14), dp(13))
                }
                val flag = if (issue.priority == 0) "ACTION NEEDED" else if (issue.priority == 1) "REVIEW" else "UNIT CHECK"
                panel.addView(label("${issue.kind.uppercase()}  |  $flag", 11f, true,
                    if (issue.priority == 0) Color.rgb(175, 65, 44) else teal))
                panel.addView(gap(5))
                panel.addView(label(issue.title, 15f, true))
                panel.addView(gap(5))
                panel.addView(label(issue.detail, 12f, false, secondary))
                panel.addView(gap(9))
                panel.addView(label("Inspect in Global Catalog  >", 13f, true, teal).apply {
                    setPadding(0, dp(6), 0, dp(5))
                    isClickable = true
                    setOnClickListener { dialog.dismiss(); inspect(issue) }
                })
                content.addView(panel)
                content.addView(gap(10))
            }
            if (visible.size > 80) {
                content.addView(label("Showing 80 of ${visible.size} findings. Use search or filters to narrow the review.",
                    12f, false, secondary))
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = draw()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        selector.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = draw()
        }
        dialog.setContentView(root)
        dialog.show()
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            statusBarColor = surface
            navigationBarColor = Color.WHITE
        }
        draw()
    }
}
