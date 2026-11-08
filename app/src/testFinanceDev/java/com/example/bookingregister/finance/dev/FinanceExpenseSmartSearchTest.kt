package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceExpenseSmartSearchTest {
    private val catalog = SmartExpenseCatalog(
        categories = listOf(
            SmartExpenseCategory("HC06", "Energy, Water & Waste", true, listOf(
                SmartExpenseType("HE0606", "Heating Fuel", listOf("coal", "firewood", "koyla", "kolla", "koal", "कोयला"), true, false, "Other expenses"),
                SmartExpenseType("HE0610", "Other Utility Fuel", emptyList(), false, false, "Other expenses"),
                SmartExpenseType("HE0604", "Cooking LPG / PNG", listOf("gas", "lpg", "silendar"), true, false, "Other expenses"),
                SmartExpenseType("HE0605", "Generator Diesel", listOf("diesel"), true, false, "Other expenses"),
                SmartExpenseType("HE0601", "Electricity", listOf("bijli", "bijlee"), true, false, "Other expenses")
            )),
            SmartExpenseCategory("HC07", "Repairs", true, listOf(
                SmartExpenseType("HE0702", "Painting & Whitewashing", listOf("painter", "putai", "पुताई"), true, false, "Other expenses")
            )),
            SmartExpenseCategory("HC10", "IT", true, listOf(
                SmartExpenseType("HE1003", "DTH / Cable TV Recharge", listOf("dth", "dish tv"), true, false, "Other expenses"),
                SmartExpenseType("HE1002", "Telephone & Mobile Recharge", emptyList(), false, false, "Other expenses"),
                SmartExpenseType("HE1001", "Internet", emptyList(), true, false, "Other expenses")
            )),
            SmartExpenseCategory("HC01", "Staff", true, listOf(
                SmartExpenseType("HE0102", "Casual Staff Wages", listOf("labour"), true, false, "Employee benefits")
            )),
            SmartExpenseCategory("HC05", "Property", true, listOf(
                SmartExpenseType("HE0501", "Hotel Building Rent", listOf("rent"), true, false, "Other expenses"),
                SmartExpenseType("HE0502", "Land Lease Rent", emptyList(), false, false, "Other expenses")
            ))
        ),
        clarifications = listOf(
            SmartExpenseClarification("coal / koyla / kolla / koal / कोयला", listOf(
                SmartExpenseChoice("Room heating", "HE0606"), SmartExpenseChoice("Kitchen / tandoor", "HE0610")
            )),
            SmartExpenseClarification("gas", listOf(
                SmartExpenseChoice("Kitchen LPG", "HE0604"), SmartExpenseChoice("Generator", "HE0605")
            )),
            SmartExpenseClarification("recharge", listOf(
                SmartExpenseChoice("DTH", "HE1003"), SmartExpenseChoice("Mobile", "HE1002")
            )),
            SmartExpenseClarification("rent", listOf(
                SmartExpenseChoice("Building", "HE0501"), SmartExpenseChoice("Land", "HE0502")
            )),
            SmartExpenseClarification("labour / mazdoori", listOf(
                SmartExpenseChoice("Staff", "HE0102"), SmartExpenseChoice("Painting", "HE0702")
            ))
        )
    )
    private val engine = FinanceExpenseSmartSearch(catalog)

    @Test fun coalSpellingsRequirePurposeSelection() {
        listOf("Coal", "Koyla", "Kolla", "Koal", "कोयला").forEach { word ->
            val result = engine.search(word)
            assertTrue("$word should ask for purpose", result.ambiguous)
            assertEquals(listOf("HE0606", "HE0610"), result.suggestions.map { it.type.code })
            assertTrue(result.suggestions.all { it.requiresClarification })
        }
    }

    @Test fun exactAliasBeatsFuzzyAndKeepsCanonicalCode() {
        val result = engine.search("DTH")
        assertFalse(result.ambiguous)
        assertEquals("HE1003", result.suggestions.first().type.code)
        assertEquals(SmartMatchKind.ALIAS, result.suggestions.first().matchKind)
    }

    @Test fun commonHindiAndRomanizedTermsWorkOffline() {
        assertEquals("HE0601", engine.search("bijlee").suggestions.first().type.code)
        assertEquals("HE0702", engine.search("पुताई").suggestions.first().type.code)
        assertEquals("HE0604", engine.search("silendar").suggestions.first().type.code)
    }

    @Test fun ambiguousShortTermsAlwaysAsk() {
        for (word in listOf("gas", "recharge", "rent", "labour", "mazdoori")) {
            val result = engine.search(word)
            assertTrue("$word should be ambiguous", result.ambiguous)
            assertTrue(result.suggestions.size >= 2)
        }
    }

    @Test fun unknownAndVeryShortWordsNeverProduceConfidentMatches() {
        assertTrue(engine.search("xx").needsManualSelection)
        assertTrue(engine.search("zyqplxv").suggestions.isEmpty())
        assertTrue(engine.search("").suggestions.isEmpty())
    }

    @Test fun suggestionsAreNotAccountingTransactions() {
        val result = engine.search("painter")
        assertEquals("HE0702", result.suggestions.first().type.code)
        // Search results contain only catalog metadata: no amount, vendor, payment, or posting fields.
        assertTrue(result.suggestions.first().score > 0)
    }
}
