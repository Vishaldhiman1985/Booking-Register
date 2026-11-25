package com.example.bookingregister.finance.domain

/**
 * Human-friendly hints for an existing monthly recurring expense schedule.
 * Not a ledger classifier or a tax rule. Manager may confirm an unusual contract.
 * Salary is managed exclusively by Manpower.
 */
object FinanceV25RecurringGuide {
    enum class Advice { RECOMMENDED, VARIABLE, REVIEW, MANPOWER_ONLY }

    private val salaryTerms = listOf("salary", "salaries", "wages", "payroll", "employee benefit", "staff salary")
    private val variableTerms = listOf(
        "grocery", "groceries", "vegetable", "fruit", "food ingredient", "kitchen grocery",
        "staff food", "laundry", "electricity", "water usage", "water bill", "cooking gas",
        "lpg", "diesel", "petrol", "fuel", "housekeeping supplies", "cleaning chemical",
        "raw material", "consumable", "milk", "dairy", "gas cylinder", "daily purchase"
    )
    private val recurringTerms = listOf(
        "rent", "lease", "internet", "wi-fi", "wifi", "broadband", "subscription",
        "software", "insurance", "maintenance contract", "amc", "security contract",
        "equipment rental", "equipment hire", "telephone plan", "licence fee",
        "license fee", "fixed service contract"
    )

    fun advise(category: String, type: String): Advice {
        val text = "${category.trim()} ${type.trim()}".lowercase()
        if (salaryTerms.any { it in text }) return Advice.MANPOWER_ONLY
        if (variableTerms.any { it in text }) return Advice.VARIABLE
        if (recurringTerms.any { it in text }) return Advice.RECOMMENDED
        return Advice.REVIEW
    }

    fun permittedForMonthlySchedule(advice: Advice, stockTracked: Boolean): Boolean =
        advice != Advice.MANPOWER_ONLY && !stockTracked

    fun hint(advice: Advice): String = when (advice) {
        Advice.RECOMMENDED -> "Common recurring commitment. A monthly bill will be generated; record payment separately."
        Advice.VARIABLE -> "Usually a variable expense, not a fixed monthly bill. Continue only if this vendor has an agreed fixed monthly contract."
        Advice.REVIEW -> "Use this only for a genuine fixed monthly agreement. Daily or occasional purchases belong in Add Expense."
        Advice.MANPOWER_ONLY -> "Staff salaries and employee benefits belong only in Manpower."
    }

    /** Only use these names for suggestions; actual categories and types come from the existing catalog. */
    val examples = listOf("Property Rent", "Internet / Wi-Fi", "Software Subscription", "Maintenance Contract", "Equipment Rental", "Insurance")
}
