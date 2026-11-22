package com.example.bookingregister.finance.dev

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

/** Manually declared, local-only lease tax tracking. This does NOT assess legal liability or ITC. */
internal object FinanceLeaseTaxPolicy {
    const val NONE = "NONE"
    const val LANDLORD_GST = "LANDLORD_GST"
    const val RCM = "RCM"
    const val REGISTERED = "REGISTERED"
    const val UNREGISTERED = "UNREGISTERED"
    const val UNKNOWN = "UNKNOWN"

    data class Split(val rentPaise: Long, val taxPaise: Long, val landlordPaise: Long, val governmentPaise: Long) {
        val totalPaise: Long get() = Math.addExact(landlordPaise, governmentPaise)
    }

    fun rateBasisPoints(value: String): Int {
        val percent = BigDecimal(value.trim())
        require(percent >= BigDecimal.ZERO && percent <= BigDecimal(100)) { "Tax rate must be between 0 and 100%" }
        return percent.multiply(BigDecimal(100)).setScale(0, RoundingMode.UNNECESSARY).intValueExact()
    }

    fun taxPaise(rentPaise: Long, rateBasisPoints: Int): Long {
        require(rentPaise >= 0 && rateBasisPoints in 0..10000)
        return BigDecimal.valueOf(rentPaise).multiply(BigDecimal.valueOf(rateBasisPoints.toLong()))
            .divide(BigDecimal(10000), 0, RoundingMode.HALF_UP).longValueExact()
    }

    fun split(rentPaise: Long, mode: String, rateBasisPoints: Int): Split {
        require(mode in setOf(NONE, LANDLORD_GST, RCM)) { "Choose a valid tax treatment" }
        require(rentPaise >= 0)
        val tax = if (mode == NONE) 0L else taxPaise(rentPaise, rateBasisPoints)
        return Split(
            rentPaise, tax,
            if (mode == LANDLORD_GST) Math.addExact(rentPaise, tax) else rentPaise,
            if (mode == RCM) tax else 0L
        )
    }

    fun validateDeclaration(status: String, gstin: String, mode: String, bps: Int, month: String) {
        YearMonth.parse(month)
        require(status in setOf(REGISTERED, UNREGISTERED, UNKNOWN)) { "Invalid landlord GST status" }
        require(mode in setOf(NONE, LANDLORD_GST, RCM)) { "Invalid GST/RCM choice" }
        require(bps in 0..10000) { "Invalid declared tax rate" }
        if (mode != NONE) require(bps > 0) { "Enter a positive GST rate" }
        if (status == REGISTERED && gstin.isNotBlank())
            require(Regex("^[0-9]{2}[A-Z0-9]{10}[0-9A-Z]{3}$").matches(gstin.trim().uppercase())) {
                "Enter a 15-character GSTIN or leave it blank for verification"
            }
        if (status == UNREGISTERED) require(gstin.isBlank()) { "Unregistered landlord cannot have GSTIN here" }
    }
}
