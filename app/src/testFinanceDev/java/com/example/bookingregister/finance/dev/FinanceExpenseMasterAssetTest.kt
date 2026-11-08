package com.example.bookingregister.finance.dev

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the actual shipped seed rather than a separate test-only copy. */
class FinanceExpenseMasterAssetTest {
    @Test fun bundledHospitalityMasterHasStableCodesAndAllExpectedTypes() {
        val file = File("src/financeDev/assets/finance_expense_master_v1.json")
        assertTrue("Finance DEV master asset is missing", file.isFile)
        val root = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
        val categories = root.getAsJsonArray("categories")
        assertEquals(18, categories.size())
        val codes = mutableSetOf<String>()
        var typeCount = 0
        var visibleCount = 0
        categories.forEach { categoryElement ->
            val category = categoryElement.asJsonObject
            assertTrue(codes.add(category.get("code").asString))
            val categoryVisible = category.get("visible_in_small_hotel_preset").asBoolean
            category.getAsJsonArray("types").forEach { typeElement ->
                val type = typeElement.asJsonObject
                assertTrue("Duplicate expense type code", codes.add(type.get("code").asString))
                typeCount++
                if (categoryVisible && type.get("enabled_by_default").asBoolean) visibleCount++
            }
        }
        assertEquals(175, typeCount)
        assertEquals(53, visibleCount)
        val aliases = categories.flatMap { c -> c.asJsonObject.getAsJsonArray("types").toList() }
            .first { it.asJsonObject.get("code").asString == "HE0606" }
            .asJsonObject.getAsJsonArray("aliases").map { it.asString }
        assertTrue("kolla" in aliases)
        assertTrue("कोयला" in aliases)
    }
}
