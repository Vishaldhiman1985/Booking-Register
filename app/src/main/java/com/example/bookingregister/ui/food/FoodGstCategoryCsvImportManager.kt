package com.example.bookingregister.ui.food

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.example.bookingregister.data.entities.FoodGstCategoryEntity
import com.example.bookingregister.data.repository.FoodBillingRepository

class FoodGstCategoryCsvImportManager(
    private val context: Context,
    private val repository: FoodBillingRepository
) {
    fun importCategoriesFromCsv(uri: Uri): Int {
        var importedCount = 0

        runCatching {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            val rows = parseCsv(text)

            if (rows.isEmpty()) {
                Toast.makeText(context, "No valid GST category rows found.", Toast.LENGTH_LONG).show()
                return 0
            }

            rows.forEach { row ->
                repository.saveFoodGstCategory(
                    existing = null,
                    categoryName = row.categoryName,
                    hsnSacCode = row.hsnSacCode,
                    gstRatePercent = row.gstRatePercent,
                    cgstRatePercent = row.cgstRatePercent,
                    sgstRatePercent = row.sgstRatePercent,
                    cessRatePercent = row.cessRatePercent,
                    isDefault = row.isDefault
                )
            }

            importedCount = rows.size
            Toast.makeText(context, "Imported ${rows.size} GST categories.", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(context, "Could not import GST CSV: ${it.message}", Toast.LENGTH_LONG).show()
        }

        return importedCount
    }

    fun writeSampleCsv(uri: Uri, existingCategories: List<FoodGstCategoryEntity>) {
        val rows = buildList {
            add("category_name,hsn_sac_code,gst_percent,cgst_percent,sgst_percent,cess_percent,is_default")
            val active = existingCategories.filter { !it.isDeleted && it.isActive }
            if (active.isEmpty()) {
                add("Restaurant / In-Room Food Service,996331,5,2.5,2.5,0,Yes")
                add("Bread,1905,0,0,0,0,No")
                add("Packaged Drinking Water,2201,12,6,6,0,No")
                add("Chocolates / Cocoa Products,1806,18,9,9,0,No")
            } else {
                active.forEach {
                    add(
                        listOf(
                            it.categoryName,
                            it.hsnSacCode.orEmpty(),
                            it.gstRatePercent.toString(),
                            it.cgstRatePercent.toString(),
                            it.sgstRatePercent.toString(),
                            it.cessRatePercent.toString(),
                            if (it.isDefault) "Yes" else "No"
                        ).joinToString(",")
                    )
                }
            }
        }

        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(rows.joinToString("\n").toByteArray(Charsets.UTF_8))
            }
        }.onSuccess {
            Toast.makeText(context, "GST sample CSV created.", Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(context, "Could not create GST sample CSV: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun parseCsv(text: String): List<GstCategoryCsvRow> {
        return text.lines()
            .drop(1)
            .mapNotNull { line ->
                val cols = splitCsvLine(line)
                if (cols.size < 7) return@mapNotNull null

                val categoryName = cols[0].trim()
                val hsnSacCode = cols[1].trim()
                val gst = cols[2].trim().toDoubleOrNull()
                val cgst = cols[3].trim().toDoubleOrNull()
                val sgst = cols[4].trim().toDoubleOrNull()
                val cess = cols[5].trim().toDoubleOrNull() ?: 0.0
                val isDefault = cols[6].trim().equals("yes", ignoreCase = true) ||
                        cols[6].trim().equals("true", ignoreCase = true)

                if (categoryName.isBlank() || hsnSacCode.isBlank() || gst == null || cgst == null || sgst == null) {
                    return@mapNotNull null
                }

                GstCategoryCsvRow(
                    categoryName = categoryName,
                    hsnSacCode = hsnSacCode,
                    gstRatePercent = gst,
                    cgstRatePercent = cgst,
                    sgstRatePercent = sgst,
                    cessRatePercent = cess,
                    isDefault = isDefault
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

    private data class GstCategoryCsvRow(
        val categoryName: String,
        val hsnSacCode: String,
        val gstRatePercent: Double,
        val cgstRatePercent: Double,
        val sgstRatePercent: Double,
        val cessRatePercent: Double,
        val isDefault: Boolean
    )
}