package com.example.bookingregister.finance.dev

import android.app.Activity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

class HotelPurchaseCategoryScreen(private val activity: Activity) {

    private val store = HotelPurchaseCategoryStore(activity)

    fun show() {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 12)
        }

        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(Button(activity).apply {
            text = "+ Add Category"
            setOnClickListener { editCategory(null) { render(list) } }
        })

        root.addView(list)
        render(list)

        AlertDialog.Builder(activity)
            .setTitle("Manage Categories")
            .setView(ScrollView(activity).apply { addView(root) })
            .setPositiveButton("Close", null)
            .show()
    }

    private fun render(container: LinearLayout) {
        container.removeAllViews()

        val customIds = store.customCategories().map { it.id }.toSet()

        store.all().forEach { category ->
            container.addView(Button(activity).apply {
                text = category.name +
                    if (category.id in customIds) "  ·  Edit" else ""
                setOnClickListener {
                    if (category.id in customIds) {
                        editCategory(category) { render(container) }
                    } else {
                        Toast.makeText(
                            activity,
                            "Built-in categories cannot be renamed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
        }
    }

    private fun editCategory(
        existing: HotelPurchaseCategory?,
        onSaved: () -> Unit
    ) {
        val input = EditText(activity).apply {
            hint = "Category name"
            setSingleLine(true)
            setText(existing?.name.orEmpty())
            setPadding(32, 16, 32, 16)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (existing == null) "New Category" else "Rename Category")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    try {
                        if (existing == null) {
                            store.add(input.text.toString())
                        } else {
                            store.rename(existing.id, input.text.toString())
                        }

                        dialog.dismiss()
                        onSaved()
                    } catch (e: Exception) {
                        input.error = e.message ?: "Could not save category"
                    }
                }
        }

        dialog.show()
    }
}