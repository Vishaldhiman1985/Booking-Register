package com.example.bookingregister.finance.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * V21 manual tax evidence, NOT a statutory tax computation, ITC claim or filing.
 * Rent GST / RCM is tracked in a separate source ledger and must not be re-entered here.
 */
object FinanceV21TaxEvidencePolicy {
    enum class Kind(val display: String, val payable: Boolean) {
        INPUT_GST("Input GST invoice (potential ITC)", false),
        OUTPUT_GST("Output GST on sale (declared)", true),
        TDS_WITHHELD("TDS deducted from vendor (declared)", true),
        TCS_COLLECTED("TCS collected for remittance (declared)", true),
        TCS_CREDIT("TCS withheld by OTA (potential credit)", false)
    }

    data class Input(
        val kind: Kind, val property: String, val date: String, val documentRef: String,
        val party: String, val amountPaise: Long, val gstin: String = "",
        val section: String = "", val note: String = ""
    )

    data class Amounts(val declaredPaise: Long, val recordedPaidPaise: Long, val openPaise: Long)

    fun validate(input: Input, today: LocalDate = LocalDate.now()): String {
        val day = LocalDate.parse(input.date)
        require(!day.isAfter(today)) { "Evidence date cannot be in the future" }
        require(input.property.trim().length in 2..100) { "Property name: 2 to 100 characters" }
        require(input.documentRef.trim().length in 3..100) { "Document reference: 3 to 100 characters" }
        require(input.party.trim().length in 2..100) { "Party name: 2 to 100 characters" }
        require(input.amountPaise in 1..100_000_000_000L) { "Amount must be positive and within the allowed range" }
        require(input.note.trim().length <= 500) { "Notes must be at most 500 characters" }
        require(input.section.trim().length <= 30) { "Tax section must be at most 30 characters" }
        val gstin = input.gstin.trim().uppercase()
        require(gstin.isEmpty() || Regex("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]").matches(gstin)) {
            "Enter a 15-character GSTIN or leave it blank"
        }
        if (input.kind == Kind.TDS_WITHHELD || input.kind == Kind.TCS_COLLECTED || input.kind == Kind.TCS_CREDIT) {
            require(input.section.trim().length in 2..30) { "Specify the tax section for TDS/TCS" }
        }
        return YearMonth.from(day).toString()
    }

    fun rupeesToPaise(raw: String): Long {
        val value = BigDecimal(raw.trim())
        require(value > BigDecimal.ZERO && value.scale() <= 2) { "Enter a positive amount with at most two decimals" }
        return value.movePointRight(2).longValueExact().also {
            require(it <= 100_000_000_000L) { "Amount exceeds limit" }
        }
    }

    fun remaining(kind: Kind, amount: Long, paid: Long): Long {
        require(amount >= 0 && paid >= 0 && paid <= amount) { "Invalid declared/paid amounts" }
        return if (kind.payable) amount - paid else 0L
    }

    fun requireSettlement(kind: Kind, amount: Long, paid: Long, proposed: Long) {
        require(kind.payable) { "Credits and potential ITC cannot be settled as payable here" }
        require(proposed > 0 && proposed <= remaining(kind, amount, paid)) { "Payment exceeds outstanding declared liability" }
    }

    fun requireCorrectionReason(reason: String) {
        require(reason.trim().length in 8..240) { "Provide a correction reason of 8-240 characters" }
    }
}
