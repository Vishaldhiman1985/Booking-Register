package com.example.bookingregister.finance.dev

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast

/** One operational item selector for Purchase Orders and already-received direct purchases. */
internal object FinanceSharedItemSelectionPolicy {
    fun groupsWithItems(groups: List<HotelPurchaseCategory>, items: List<HotelPurchaseItem>) =
        groups.filter { group -> items.any { it.active && it.categoryId == group.id } }

    fun displayedItems(items: List<HotelPurchaseItem>, groupId: String?, search: String): List<HotelPurchaseItem> {
        val term = search.trim()
        return items.filter { it.active &&
            (term.isNotEmpty() || it.categoryId == groupId) &&
            (term.isEmpty() || it.name.contains(term, ignoreCase = true) ||
                it.synonyms.any { alias -> alias.contains(term, ignoreCase = true) })
        }
    }

    fun selectedLines(items: List<HotelPurchaseItem>, quantities: Map<String, Double>): List<Pair<HotelPurchaseItem, Double>> {
        val known = items.filter { it.active }.associateBy { it.id }
        require(quantities.values.all { it.isFinite() && it >= 0.0 }) { "Enter valid quantities" }
        require(quantities.filterValues { it > 0.0 }.keys.all { it in known }) {
            "An item is no longer available in this item group"
        }
        val result = quantities.filterValues { it > 0.0 }.map { (id, q) -> known.getValue(id) to q }
        require(result.isNotEmpty()) { "Add at least one item" }
        return result.sortedBy { it.first.name.lowercase() }
    }
}

/**
 * Consistent two-rail, search-first, +/- quantity picker. Both screens call this *same* class,
 * not two copied implementations. This class does not write Purchase Orders or stock.
 */
internal class FinanceSharedItemPicker(private val activity: Activity) {
    private val green = Color.rgb(0, 115, 46)
    private val dark = Color.rgb(31, 42, 59)
    private val pale = Color.rgb(227, 247, 238)
    private val light = Color.rgb(247, 247, 251)
    private fun dp(n: Int) = (activity.resources.displayMetrics.density * n + 0.5f).toInt()
    private fun text(label: String, size: Float = 15f, bold: Boolean = false): TextView = TextView(activity).apply {
        this.text = label; textSize = size; setTextColor(dark)
        if (bold) setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun button(label: String, onClick: () -> Unit): Button = Button(activity).apply {
        text = label; isAllCaps = false; textSize = 13f; minHeight = dp(48)
        setTextColor(dark); backgroundTintList = ColorStateList.valueOf(pale)
        setOnClickListener { onClick() }
    }
    private fun space(n: Int) = Space(activity).apply { minimumHeight = dp(n) }
    private fun qtyLabel(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()

    fun render(
        root: LinearLayout,
        groups: List<HotelPurchaseCategory>,
        items: List<HotelPurchaseItem>,
        quantities: MutableMap<String, Double>,
        initialGroupId: String?,
        initialQuery: String,
        reviewLabel: String,
        onStateChanged: (groupId: String?, query: String) -> Unit,
        onReview: () -> Unit
    ) {
        val activeItems = items.filter { it.active }
        val allowedGroups = FinanceSharedItemSelectionPolicy.groupsWithItems(groups, activeItems)
        var selectedGroupId = initialGroupId?.takeIf { key -> allowedGroups.any { it.id == key } }
            ?: allowedGroups.firstOrNull()?.id
        var query = initialQuery
        onStateChanged(selectedGroupId, query)
        val fields = linkedMapOf<String, EditText>()

        val searchField = EditText(activity).apply {
            hint = "Search items"; setSingleLine(true); textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(14), 0, dp(14), 0); setBackgroundColor(light)
            setText(query)
        }
        root.addView(searchField, LinearLayout.LayoutParams(-1, dp(43)))
        root.addView(space(7))

        val body = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val left = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val right = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val categoryScroll = ScrollView(activity).apply {
            isFillViewport = false; isVerticalScrollBarEnabled = false; addView(left)
        }
        val itemScroll = ScrollView(activity).apply {
            isFillViewport = false; isVerticalScrollBarEnabled = false; addView(right)
        }
        body.addView(categoryScroll, LinearLayout.LayoutParams(0, -1, 0.32f))
        body.addView(itemScroll, LinearLayout.LayoutParams(0, -1, 0.68f))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(5), dp(3), dp(5)); setBackgroundColor(Color.WHITE)
        }
        val count = text("0 items added", 14f, true).apply { setTextColor(green) }
        footer.addView(count, LinearLayout.LayoutParams(0, dp(49), 1f))
        fun flushVisible(): Boolean {
            var valid = true
            fields.forEach { (id, field) ->
                val input = field.text.toString().trim()
                val q = if (input.isBlank()) 0.0 else input.toDoubleOrNull()
                if (q == null || !q.isFinite() || q < 0.0) valid = false
                else if (q == 0.0) quantities.remove(id) else quantities[id] = q
            }
            return valid
        }
        val reviewButton = button(reviewLabel) {
            if (!flushVisible()) {
                Toast.makeText(activity, "Correct invalid quantities", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    FinanceSharedItemSelectionPolicy.selectedLines(activeItems, quantities)
                    onReview()
                } catch (error: IllegalArgumentException) {
                    Toast.makeText(activity, error.message ?: "Choose received items", Toast.LENGTH_LONG).show()
                } catch (error: IllegalStateException) {
                    Toast.makeText(activity, error.message ?: "Choose received items", Toast.LENGTH_LONG).show()
                }
            }
        }.apply { setTextColor(Color.WHITE); backgroundTintList = ColorStateList.valueOf(green) }
        footer.addView(reviewButton, LinearLayout.LayoutParams(0, dp(49), 1f))

        fun updateCount() { count.text = "${quantities.count { it.value > 0 }} items added" }
        fun rebuild() {
            flushVisible()
            fields.clear()
            left.removeAllViews(); right.removeAllViews()
            allowedGroups.forEach { group ->
                val active = group.id == selectedGroupId
                left.addView(button(group.name) {
                    if (selectedGroupId != group.id) {
                        flushVisible(); selectedGroupId = group.id
                        onStateChanged(selectedGroupId, query)
                        rebuild()
                    }
                }.apply {
                    textSize = 11f; isAllCaps = false
                    setTextColor(if (active) green else dark)
                    backgroundTintList = ColorStateList.valueOf(if (active) pale else light)
                    setPadding(dp(3), 0, dp(3), 0)
                }, LinearLayout.LayoutParams(-1, dp(52)))
                left.addView(space(3))
            }
            val visible = FinanceSharedItemSelectionPolicy.displayedItems(activeItems, selectedGroupId, query)
            if (visible.isEmpty()) right.addView(text("No matching items", 13f))
            visible.forEach { item ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(5), dp(3), 0, dp(3))
                }
                val info = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                info.addView(text(item.name, 13f, true)); info.addView(text(item.unit, 11f))
                row.addView(info, LinearLayout.LayoutParams(0, dp(48), 1.1f))
                val minus = button("−") {}.apply { backgroundTintList = ColorStateList.valueOf(light) }
                val quantity = EditText(activity).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    gravity = Gravity.CENTER; textSize = 15f; setSingleLine(true)
                    setSelectAllOnFocus(true); setText(qtyLabel(quantities[item.id] ?: 0.0))
                    setOnClickListener { selectAll() }
                }
                fields[item.id] = quantity
                quantity.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val q = s.toString().trim().toDoubleOrNull()
                        if (q == null || q <= 0.0 || !q.isFinite()) quantities.remove(item.id)
                        else quantities[item.id] = q
                        updateCount()
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                minus.setOnClickListener {
                    val next = ((quantity.text.toString().toDoubleOrNull() ?: 0.0) - 1.0).coerceAtLeast(0.0)
                    quantity.setText(qtyLabel(next))
                }
                val plus = button("+") {
                    val next = (quantity.text.toString().toDoubleOrNull() ?: 0.0) + 1.0
                    quantity.setText(qtyLabel(next))
                }.apply { backgroundTintList = ColorStateList.valueOf(pale) }
                row.addView(minus, LinearLayout.LayoutParams(dp(35), dp(42)))
                row.addView(quantity, LinearLayout.LayoutParams(dp(50), dp(42)))
                row.addView(plus, LinearLayout.LayoutParams(dp(35), dp(42)))
                right.addView(row)
                right.addView(View(activity).apply { setBackgroundColor(Color.rgb(230, 233, 237)) },
                    LinearLayout.LayoutParams(-1, dp(1)))
            }
            updateCount()
        }
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty()
                onStateChanged(selectedGroupId, query)
                rebuild()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        rebuild()
        root.addView(footer)
    }
}
