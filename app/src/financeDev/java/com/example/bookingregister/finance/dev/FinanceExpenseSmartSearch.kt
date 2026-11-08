package com.example.bookingregister.finance.dev

import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * Offline-only suggestion engine. Never creates, posts, or modifies accounting entries.
 * Stable type codes come from the hospitality master, not staff-entered text.
 */
internal data class SmartExpenseType(
    val code: String,
    val name: String,
    val aliases: List<String>,
    val enabledByDefault: Boolean,
    val requiresAccountantReview: Boolean,
    val accountingHint: String
)

internal data class SmartExpenseCategory(
    val code: String,
    val name: String,
    val visibleByDefault: Boolean,
    val types: List<SmartExpenseType>
)

internal data class SmartExpenseChoice(val label: String, val typeCode: String)
internal data class SmartExpenseClarification(val query: String, val choices: List<SmartExpenseChoice>)
internal data class SmartExpenseCatalog(
    val categories: List<SmartExpenseCategory>,
    val clarifications: List<SmartExpenseClarification>
) {
    val allTypes: List<Pair<SmartExpenseCategory, SmartExpenseType>>
        get() = categories.flatMap { category -> category.types.map { category to it } }

    fun findType(code: String): Pair<SmartExpenseCategory, SmartExpenseType>? =
        allTypes.firstOrNull { it.second.code == code }

    fun validate() {
        require(categories.size == 18) { "Expected 18 standardized categories" }
        require(allTypes.size == 175) { "Expected 175 standardized expense types" }
        val codes = categories.map { it.code } + allTypes.map { it.second.code }
        require(codes.size == codes.distinct().size) { "Duplicate expense master code" }
        require(categories.all { it.name.isNotBlank() && it.types.isNotEmpty() })
        require(allTypes.all { it.second.name.isNotBlank() })
        require(clarifications.flatMap { it.choices }.all { findType(it.typeCode) != null }) {
            "Clarification points to missing expense type"
        }
    }
}

internal enum class SmartMatchKind { EXACT, ALIAS, PREFIX, TOKEN, TYPO, CONTEXT }

internal data class SmartExpenseSuggestion(
    val category: SmartExpenseCategory,
    val type: SmartExpenseType,
    val matchKind: SmartMatchKind,
    val score: Int,
    val requiresClarification: Boolean,
    val contextLabel: String? = null
)

internal data class SmartExpenseSearchResult(
    val suggestions: List<SmartExpenseSuggestion>,
    val ambiguous: Boolean,
    val needsManualSelection: Boolean
)

internal class FinanceExpenseSmartSearch(private val catalog: SmartExpenseCatalog) {
    // Ambiguities are data-driven: the master JSON owns the clarification choices.
    private val ambiguousWords: Map<String, List<SmartExpenseChoice>> = buildMap {
        catalog.clarifications.forEach { clarification ->
            clarification.query.split("/").forEach { alias ->
                val key = normalize(alias)
                if (key.isNotBlank()) put(key, clarification.choices)
            }
        }
    }

    fun search(raw: String, maxResults: Int = 7): SmartExpenseSearchResult {
        val query = normalize(raw)
        if (query.length < 3 || maxResults < 1) {
            return SmartExpenseSearchResult(emptyList(), false, true)
        }

        val explicit = ambiguousWords[query]
        if (explicit != null) {
            val suggestions = explicit.mapNotNull { choice ->
                catalog.findType(choice.typeCode)?.let { (category, type) ->
                    SmartExpenseSuggestion(category, type, SmartMatchKind.CONTEXT, 100, true, choice.label)
                }
            }
            return SmartExpenseSearchResult(suggestions.take(maxResults), true, suggestions.isEmpty())
        }

        val suggestions = catalog.allTypes.mapNotNull { (category, type) ->
            val candidates = listOf(type.name) + type.aliases
            val best = candidates.mapNotNull { phrase -> score(query, normalize(phrase), phrase == type.name) }
                .maxByOrNull { it.first }
            best?.let { (score, kind) ->
                SmartExpenseSuggestion(category, type, kind, score, false)
            }
        }.sortedWith(compareByDescending<SmartExpenseSuggestion> { it.score }
            .thenBy { it.category.code }.thenBy { it.type.code }).take(maxResults)

        // A shared alias or a close-scoring collision needs explicit user selection.
        val ambiguous = suggestions.size > 1 && suggestions[0].score - suggestions[1].score <= 4
        return SmartExpenseSearchResult(
            suggestions = suggestions.map { if (ambiguous) it.copy(requiresClarification = true) else it },
            ambiguous = ambiguous,
            needsManualSelection = suggestions.isEmpty()
        )
    }

    private fun score(query: String, phrase: String, canonicalName: Boolean): Pair<Int, SmartMatchKind>? {
        if (phrase.isEmpty()) return null
        if (query == phrase) return (if (canonicalName) 100 else 98) to
            (if (canonicalName) SmartMatchKind.EXACT else SmartMatchKind.ALIAS)
        if (phrase.startsWith(query) && query.length >= 4) return 87 to SmartMatchKind.PREFIX
        if (phrase.split(' ').any { it == query } && query.length >= 4) return 83 to SmartMatchKind.TOKEN
        if (phrase.contains(" $query ") && query.length >= 4) return 80 to SmartMatchKind.TOKEN
        if (query.length >= 4 && phrase.length >= 4 && abs(query.length - phrase.length) <= 2) {
            val distance = distanceAtMostTwo(query, phrase)
            val allowance = if (query.length <= 5) 1 else 2
            if (distance in 1..allowance) return (70 - distance * 8) to SmartMatchKind.TYPO
        }
        return null
    }

    // Bounded Damerau-Levenshtein: includes adjacent swaps (e.g. "koal" -> "coal").
    private fun distanceAtMostTwo(a: String, b: String): Int {
        val rows = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) rows[i][0] = i
        for (j in 0..b.length) rows[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = min(min(rows[i - 1][j] + 1, rows[i][j - 1] + 1), rows[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = min(value, rows[i - 2][j - 2] + 1)
                }
                rows[i][j] = value
            }
        }
        return rows[a.length][b.length]
    }

    companion object {
        fun normalize(text: String): String = text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ")
            .trim().replace(Regex("\\s+"), " ")
    }
}
