package com.example.bookingregister.tax.domain

object GstinValidator {
    private val gstinPattern = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")
    private val validStateCodes = setOf(
        "01", "02", "03", "04", "05", "06", "07", "08", "09", "10",
        "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
        "21", "22", "23", "24", "25", "26", "27", "28", "29", "30",
        "31", "32", "33", "34", "35", "36", "37", "38", "97"
    )

    fun normalize(value: String?): String {
        return value.orEmpty()
            .trim()
            .replace(" ", "")
            .uppercase()
    }

    fun isBlankOrValid(value: String?): Boolean {
        val normalized = normalize(value)
        return normalized.isBlank() || isValid(normalized)
    }

    fun isValid(value: String?): Boolean {
        val normalized = normalize(value)
        if (!gstinPattern.matches(normalized)) return false
        return normalized.take(2) in validStateCodes
    }
}
