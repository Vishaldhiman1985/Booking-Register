package com.example.bookingregister.finance.dev

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

class HotelPurchaseItemScreen(private val activity: Activity) {

    fun show() {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 12)
        }

        val search = EditText(activity).apply {
            hint = "Search items or Hindi names"
            setSingleLine(true)
        }

        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        val expanded = mutableSetOf<String>()

        fun allItems(): List<HotelPurchaseItem> {
            val originals = HotelPurchaseItems.items
            val master = HotelPurchaseMasterCatalog.items.filter { candidate ->
                originals.none {
                    it.categoryId == candidate.categoryId &&
                    it.name.equals(candidate.name, ignoreCase = true)
                }
            }
            return originals + master +
                HotelPurchaseCustomItemStore(activity).all()
        }

        fun refresh() {
            list.removeAllViews()
            val query = search.text.toString().trim()
            val categories = HotelPurchaseCategoryStore(activity).all()

            val items = allItems().filter { item ->
                item.active && (
                    query.isEmpty() ||
                    item.name.contains(query, ignoreCase = true) ||
                    item.synonyms.any {
                        it.contains(query, ignoreCase = true)
                    }
                )
            }

            categories.forEach { category ->
                val matches = items.filter {
                    it.categoryId == category.id
                }

                if (matches.isNotEmpty() || query.isEmpty()) {
                    val isExpanded =
                        query.isNotEmpty() || category.id in expanded

                    list.addView(Button(activity).apply {
                        text = (if (isExpanded) "▼ " else "▶ ") +
                            category.name + " (${matches.size})"
                        setOnClickListener {
                            if (!expanded.add(category.id)) {
                                expanded.remove(category.id)
                            }
                            refresh()
                        }
                    })

                    if (isExpanded) {
                        matches.forEach { item ->
                            list.addView(TextView(activity).apply {
                                text = "   ${item.name}  ·  ${item.unit}"
                                textSize = 15f
                                setPadding(16, 12, 8, 12)
                            })
                        }
                    }
                }
            }
        }

        root.addView(Button(activity).apply {
            text = "+ Add New Item"
            setOnClickListener {
                HotelPurchaseAddItemDialog(activity).show {
                    refresh()
                }
            }
        })

        root.addView(search)
        root.addView(list)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                refresh()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        refresh()

        AlertDialog.Builder(activity)
            .setTitle("Manage Items")
            .setView(ScrollView(activity).apply { addView(root) })
            .setPositiveButton("Close", null)
            .show()
    }
}