package com.example.bookingregister.finance.dev

import android.app.Activity
import android.app.AlertDialog
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID

class HotelPurchaseVendorScreen(private val activity: Activity) {

    private val store = HotelPurchaseVendorStore(activity)

    fun render(container: LinearLayout) {
        container.removeAllViews()

        val title = TextView(activity).apply {
            text = "Purchasing Vendors"
            textSize = 22f
            setPadding(0, 12, 0, 20)
        }
        container.addView(title)

        container.addView(Button(activity).apply {
            text = "Purchase Orders"
            setOnClickListener {
                HotelPurchaseOrderScreen(activity).show()
            }
        })

        container.addView(Button(activity).apply {
            text = "+ Add Vendor"
            setOnClickListener { showVendorDialog(container) }
        })

        val vendors = store.all()

        if (vendors.isEmpty()) {
            container.addView(TextView(activity).apply {
                text = "No purchasing vendors added yet."
                textSize = 15f
                setPadding(0, 20, 0, 12)
            })
        }

        vendors.forEach { vendor ->
            val categories = vendor.suppliedCategoryIds
                .mapNotNull { HotelPurchaseCategoryStore(activity).all().firstOrNull { category -> category.id == it }?.name }

            val availableItems = HotelPurchasePolicy.itemsForVendor(
                vendor,
                HotelPurchaseItems.items +
                    HotelPurchaseMasterCatalog.items.filter { master ->
                        HotelPurchaseItems.items.none { original ->
                            original.categoryId == master.categoryId &&
                                original.name.equals(master.name, ignoreCase = true)
                        }
                    } +
                    HotelPurchaseCustomItemStore(activity).all()
            )

            container.addView(TextView(activity).apply {
                text = "${vendor.name}\n${vendor.phone}\n" +
                    "Categories: ${categories.joinToString()}\n" +
                    "Available items: ${availableItems.size}"
                textSize = 15f
                setPadding(12, 20, 12, 20)
                setOnClickListener {
                    showVendorDialog(container, vendor)
                }
            })
        }
    }

    private fun showVendorDialog(
        container: LinearLayout,
        existing: HotelPurchaseVendor? = null
    ) {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 12)
        }

        val name = EditText(activity).apply {
            hint = "Vendor name"
            setText(existing?.name.orEmpty())
        }
        val phone = EditText(activity).apply {
            hint = "Phone number"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(existing?.phone.orEmpty())
        }

        layout.addView(name)
        layout.addView(phone)
        layout.addView(TextView(activity).apply {
            text = "Select supply categories"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        })

        val checks = HotelPurchaseCategoryStore(activity).all().map { category ->
            CheckBox(activity).apply {
                text = category.name
                isChecked = category.id in
                    (existing?.suppliedCategoryIds ?: emptySet())
                layout.addView(this)
            } to category.id
        }

        val scroll = ScrollView(activity).apply {
            addView(layout)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (existing == null) "Add Vendor" else "Edit Vendor")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val vendorName = name.text.toString().trim()
                    val selected = checks
                        .filter { it.first.isChecked }
                        .map { it.second }
                        .toSet()

                    if (vendorName.isBlank()) {
                        name.error = "Vendor name required"
                        return@setOnClickListener
                    }

                    if (selected.isEmpty()) {
                        android.widget.Toast.makeText(
                            activity,
                            "Select at least one supply category",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }

                    try {
                        store.save(
                            HotelPurchaseVendor(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                name = vendorName,
                                phone = phone.text.toString().trim(),
                                suppliedCategoryIds = selected
                            )
                        )
                        dialog.dismiss()
                        render(container)
                    } catch (error: Exception) {
                        android.widget.Toast.makeText(
                            activity,
                            error.message ?: "Unable to save vendor",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        dialog.show()
    }
}
