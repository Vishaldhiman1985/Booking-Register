package com.example.bookingregister.ui.food

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.example.bookingregister.data.entities.FoodGstCategoryEntity
import com.example.bookingregister.data.repository.FoodBillingRepository
import com.example.bookingregister.data.repository.FoodBillingRepository.FoodMenuImportItem

class FoodMenuCsvImportManager(
    private val context: Context,
    private val repository: FoodBillingRepository
) {

    fun importMenuItemsFromCsv(
        uri: Uri,
        gstCategories: List<FoodGstCategoryEntity>,
        propertyRemoteId: String? = null
    ): Int {
        var importedCount = 0
        runCatching {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            val rows = parseCsv(text)

            if (rows.isEmpty()) {
                Toast.makeText(context, "No valid menu rows found.", Toast.LENGTH_LONG).show()
                return 0
            }

            val categoryByName = gstCategories.associateBy {
                it.categoryName.trim().lowercase()
            }

            val missingCategories = rows
                .mapNotNull { it.gstCategoryName }
                .filter { it.isNotBlank() }
                .filter { categoryByName[it.trim().lowercase()] == null }
                .distinct()

            if (missingCategories.isNotEmpty()) {
                Toast.makeText(
                    context,
                    "GST category not found: ${missingCategories.joinToString(", ")}",
                    Toast.LENGTH_LONG
                ).show()
                return 0
            }

            val importedItems = rows.map { row ->
                val gstCategory = categoryByName[row.gstCategoryName.trim().lowercase()]

                FoodMenuImportItem(
                    itemName = row.itemName,
                    categoryName = row.menuCategory,
                    price = row.priceInclusiveGst,
                    gstRatePercent = gstCategory?.gstRatePercent ?: 0.0,
                    gstCategoryRemoteId = gstCategory?.remoteId,
                    gstCategoryName = gstCategory?.categoryName,
                    hsnSacCode = gstCategory?.hsnSacCode,
                    cgstRatePercent = gstCategory?.cgstRatePercent
                        ?: ((gstCategory?.gstRatePercent ?: 0.0) / 2.0),
                    sgstRatePercent = gstCategory?.sgstRatePercent
                        ?: ((gstCategory?.gstRatePercent ?: 0.0) / 2.0),
                    cessRatePercent = gstCategory?.cessRatePercent ?: 0.0
                )
            }
            repository.replaceFoodMenuItems(
                propertyRemoteId = propertyRemoteId,
                importedItems = importedItems
            )
            importedCount = rows.size

            Toast.makeText(
                context,
                "Menu replaced with ${rows.size} item(s).",
                Toast.LENGTH_LONG
            ).show()
        }.onFailure {
            Toast.makeText(
                context,
                "Could not import CSV: ${it.message}",
                Toast.LENGTH_LONG
            ).show()
        }
        return importedCount
    }

    fun writeSampleCsv(uri: Uri, gstCategories: List<FoodGstCategoryEntity>) {
        val sampleCategories = gstCategories
            .filter { !it.isDeleted && it.isActive }
            .takeIf { it.isNotEmpty() }
            ?: listOf(
                FoodGstCategoryEntity(
                    remoteId = "",
                    hotelRemoteId = repository.hotelRemoteId,
                    categoryName = "Restaurant / In-Room Food Service",
                    gstRatePercent = 5.0
                )
            )

        val sampleRows = buildList {
            add("item_name,menu_category,price_inclusive_gst,gst_category_name")
            add("Tea,Beverages,30,${sampleCategories.first().categoryName}")
            add("Coffee,Beverages,50,${sampleCategories.first().categoryName}")
            add("Paneer Pakora,Snacks,180,${sampleCategories.first().categoryName}")

            sampleCategories.drop(1).forEachIndexed { index, category ->
                add("Sample Item ${index + 1},Food,100,${category.categoryName}")
            }
        }

        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(sampleRows.joinToString("\n").toByteArray(Charsets.UTF_8))
            }
        }.onSuccess {
            Toast.makeText(context, "Sample CSV created.", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(
                context,
                "Could not create sample CSV: ${it.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun parseCsv(text: String): List<MenuCsvRow> {
        return text
            .lines()
            .drop(1)
            .mapNotNull { line ->
                val cols = splitCsvLine(line)
                if (cols.size < 4) return@mapNotNull null

                val itemName = cols[0].trim()
                val menuCategory = cols[1].trim().ifBlank { "Food" }
                val price = cols[2].trim().toDoubleOrNull()
                val gstCategoryName = cols[3].trim()

                if (itemName.isBlank() || price == null || gstCategoryName.isBlank()) {
                    return@mapNotNull null
                }

                MenuCsvRow(
                    itemName = itemName,
                    menuCategory = menuCategory,
                    priceInclusiveGst = price,
                    gstCategoryName = gstCategoryName
                )
            }
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var insideQuotes = false

        line.forEach { char ->
            when {
                char == '"' -> insideQuotes = !insideQuotes
                char == ',' && !insideQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }

        result.add(current.toString())
        return result
    }

    private data class MenuCsvRow(
        val itemName: String,
        val menuCategory: String,
        val priceInclusiveGst: Double,
        val gstCategoryName: String
    )
}
