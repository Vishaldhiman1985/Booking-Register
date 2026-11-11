package com.example.bookingregister.finance.dev

import android.app.Activity
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog

/** All changes immediately feed both the Create Order and Monthly Stock category rails. */
class HotelPurchaseCategoryScreen(private val activity: Activity) {
    private fun dp(n: Int) = (activity.resources.displayMetrics.density * n + 0.5f).toInt()
    private val store = HotelPurchaseCategoryStore(activity)
    fun show(onChanged: () -> Unit = {}) {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 12, 18, 12)
        }
        val add = Button(activity).apply { text = "+ Add Category"; isAllCaps = false }
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply { addView(list) }
        root.addView(add)
        root.addView(scroll, LinearLayout.LayoutParams(-1, (activity.resources.displayMetrics.heightPixels * 0.56).toInt()))
        fun render() {
            list.removeAllViews()
            store.allIncludingArchived().forEach { (category, active) ->
                val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(TextView(activity).apply {
                    text = category.name + if (active) "" else " (Archived)"
                    textSize = 15f
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }, LinearLayout.LayoutParams(0, dp(52), 1f))
                row.addView(Button(activity).apply {
                    text = "⋮"; isAllCaps = false
                    setOnClickListener {
                        val labels = if (active) arrayOf("Rename", "Archive") else arrayOf("Restore")
                        AlertDialog.Builder(activity).setTitle(category.name).setItems(labels) { _, which ->
                            if (active && which == 0) editCategory(category.id, category.name) { render(); onChanged() }
                            else if (active) confirmArchive(category.id, category.name) { render(); onChanged() }
                            else tryAction { store.restore(category.id); render(); onChanged() }
                        }.show()
                    }
                }, LinearLayout.LayoutParams(dp(52), dp(52)))
                list.addView(row)
            }
        }
        add.setOnClickListener { editCategory(null, "") { render(); onChanged() } }
        render()
        AlertDialog.Builder(activity).setTitle("Manage Categories")
            .setView(root).setPositiveButton("Close", null).show().apply {
                window?.setLayout(-1, (activity.resources.displayMetrics.heightPixels * 0.78).toInt())
            }
    }

    private fun editCategory(id: String?, current: String, done: () -> Unit) {
        val field = EditText(activity).apply {
            hint = "Category name"; setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT
            setText(current); setSelectAllOnFocus(true); setPadding(32, 12, 32, 12)
        }
        val dlg = AlertDialog.Builder(activity)
            .setTitle(if (id == null) "New Category" else "Rename Category")
            .setView(field).setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    if (id == null) store.add(field.text.toString()) else store.rename(id, field.text.toString())
                    dlg.dismiss(); done()
                } catch (e: Exception) { field.error = e.message ?: "Could not save category" }
            }
        }
        dlg.show()
    }

    private fun confirmArchive(id: String, name: String, done: () -> Unit) {
        val note = EditText(activity).apply { hint = "Reason for archiving (required)"; setSingleLine(true) }
        val dlg = AlertDialog.Builder(activity)
            .setTitle("Archive $name?")
            .setMessage("Archived categories are hidden from new orders and counts. Existing records remain unchanged. Move/archive active items first.")
            .setView(note).setNegativeButton("Cancel", null).setPositiveButton("Archive", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (note.text.isBlank()) { note.error = "Enter reason"; return@setOnClickListener }
                tryAction {
                    store.archive(id)
                    HotelCatalogChangeLog(activity).record("CATEGORY_ARCHIVE", id, note.text.toString())
                    dlg.dismiss(); done()
                }
            }
        }
        dlg.show()
    }
    private fun tryAction(body: () -> Unit) {
        try { body() } catch (e: Exception) {
            Toast.makeText(activity, e.message ?: "Could not update category", Toast.LENGTH_LONG).show()
        }
    }
}
