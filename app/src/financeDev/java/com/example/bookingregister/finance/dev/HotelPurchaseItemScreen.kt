package com.example.bookingregister.finance.dev

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.widget.*
import android.view.Gravity
import androidx.appcompat.app.AlertDialog

/** A manageable catalog, not a full master-list of hundreds of views. */
class HotelPurchaseItemScreen(private val activity: Activity) {
    private val store = HotelPurchaseCustomItemStore(activity)
    private val categoryStore = HotelPurchaseCategoryStore(activity)
    private fun dp(n: Int) = (activity.resources.displayMetrics.density * n + 0.5f).toInt()
    private val units = listOf("kg", "gram", "litre", "ml", "piece", "packet", "box", "bottle", "dozen", "bag", "roll", "set", "pair", "carton", "service")

    fun show(onChanged: () -> Unit = {}) {
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 8, 16, 8) }
        val search = EditText(activity).apply { hint = "Search items"; setSingleLine(true) }
        val categorySelector = Spinner(activity)
        val groups = categoryStore.all()
        val filters = listOf("All Categories") + groups.map { it.name }
        categorySelector.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, filters)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroller = ScrollView(activity).apply { addView(list) }
        val count = TextView(activity).apply { textSize = 12f }
        val navigation = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val previous = Button(activity).apply { text = "‹ Previous"; isAllCaps = false }
        val next = Button(activity).apply { text = "Next ›"; isAllCaps = false }
        navigation.addView(previous, LinearLayout.LayoutParams(0, -2, 1f))
        navigation.addView(next, LinearLayout.LayoutParams(0, -2, 1f))
        var page = 0
        var totalPages = 1
        val pageSize = 25
        fun render() {
            list.removeAllViews()
            val q = search.text.toString().trim()
            val categoryId = groups.getOrNull(categorySelector.selectedItemPosition - 1)?.id
            val matching = store.catalogItems(includeArchived = true).filter { item ->
                (categoryId == null || categoryId == item.categoryId) &&
                    (q.isBlank() || item.name.contains(q, true) || item.synonyms.any { it.contains(q, true) })
            }
            totalPages = ((matching.size + pageSize - 1) / pageSize).coerceAtLeast(1)
            page = page.coerceIn(0, totalPages - 1)
            count.text = "${matching.size} items  ·  Page ${page + 1}/$totalPages"
            previous.isEnabled = page > 0
            next.isEnabled = page < totalPages - 1
            matching.drop(page * pageSize).take(pageSize).forEach { item ->
                val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val label = TextView(activity).apply {
                    text = "${item.name}  ·  ${item.unit}" + if (item.active) "" else "  (Archived)"
                    textSize = 14f
                }
                row.addView(label, LinearLayout.LayoutParams(0, dp(56), 1f))
                row.addView(Button(activity).apply {
                    text = "⋮"; isAllCaps = false
                    setOnClickListener {
                        val choices = if (item.active) arrayOf("Edit", "Archive") else arrayOf("Restore")
                        AlertDialog.Builder(activity).setTitle(item.name).setItems(choices) { _, which ->
                            if (item.active && which == 0) edit(item) { render(); onChanged() }
                            else if (item.active) archive(item) { render(); onChanged() }
                            else tryAction { store.restore(item.id); render(); onChanged() }
                        }.show()
                    }
                }, LinearLayout.LayoutParams(dp(52), dp(52)))
                list.addView(row)
            }
        }
        val add = Button(activity).apply {
            text = "+ Add Item"; isAllCaps = false
            setOnClickListener { HotelPurchaseAddItemDialog(activity).show { page = 0; render(); onChanged() } }
        }
        root.addView(add)
        root.addView(search)
        root.addView(categorySelector)
        root.addView(count)
        root.addView(scroller, LinearLayout.LayoutParams(-1, (activity.resources.displayMetrics.heightPixels * 0.52).toInt()))
        root.addView(navigation)
        previous.setOnClickListener { page--; render() }
        next.setOnClickListener { page++; render() }
        categorySelector.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { page = 0; render() }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { page = 0; render() }
        })
        render()
        AlertDialog.Builder(activity).setTitle("Manage Items")
            .setView(root).setPositiveButton("Close", null).show().apply {
                window?.setLayout(-1, (activity.resources.displayMetrics.heightPixels * 0.82).toInt())
            }
    }

    private fun edit(item: HotelPurchaseItem, done: () -> Unit) {
        val categories = categoryStore.all()
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 10, 28, 6) }
        val name = EditText(activity).apply { hint = "Item name"; setSingleLine(true); setText(item.name) }
        val category = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, categories.map { it.name })
            setSelection(categories.indexOfFirst { it.id == item.categoryId }.coerceAtLeast(0))
        }
        val allUnits = (units + item.unit).distinct()
        val unit = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, allUnits)
            setSelection(allUnits.indexOf(item.unit).coerceAtLeast(0))
        }
        val synonyms = EditText(activity).apply {
            hint = "Aliases / Hindi names (comma separated)"; setText(item.synonyms.joinToString(", "))
        }
        root.addView(name); root.addView(TextView(activity).apply { text = "Category" }); root.addView(category)
        root.addView(TextView(activity).apply { text = "Unit" }); root.addView(unit); root.addView(synonyms)
        val dlg = AlertDialog.Builder(activity).setTitle("Edit Item")
            .setView(root).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    store.edit(item.id, name.text.toString(), categories[category.selectedItemPosition].id,
                        unit.selectedItem.toString(), synonyms.text.toString().split(","))
                    dlg.dismiss(); done()
                } catch (e: Exception) { name.error = e.message ?: "Could not edit item" }
            }
        }
        dlg.show()
    }

    private fun archive(item: HotelPurchaseItem, done: () -> Unit) {
        val reason = EditText(activity).apply { hint = "Reason for archiving (required)"; setSingleLine(true) }
        val dlg = AlertDialog.Builder(activity).setTitle("Archive ${item.name}?")
            .setMessage("The item will disappear from new orders and counts. Existing records remain unchanged.")
            .setView(reason).setNegativeButton("Cancel", null).setPositiveButton("Archive", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (reason.text.isBlank()) { reason.error = "Enter reason"; return@setOnClickListener }
                tryAction {
                    store.archive(item.id)
                    HotelCatalogChangeLog(activity).record("ITEM_ARCHIVE", item.id, reason.text.toString())
                    dlg.dismiss(); done()
                }
            }
        }
        dlg.show()
    }
    private fun tryAction(body: () -> Unit) {
        try { body() } catch (e: Exception) {
            Toast.makeText(activity, e.message ?: "Catalog operation failed", Toast.LENGTH_LONG).show()
        }
    }
}
