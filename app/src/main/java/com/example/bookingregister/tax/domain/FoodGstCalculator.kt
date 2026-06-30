package com.example.bookingregister.tax.domain

import kotlin.math.round

data class FoodGstBreakdown(
    val grossAmount: Double,
    val taxableAmount: Double,
    val gstRatePercent: Double,
    val cgstRatePercent: Double,
    val sgstRatePercent: Double,
    val cessRatePercent: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val cessAmount: Double,
    val gstAmount: Double,
    val totalTaxAmount: Double,
    val lineTotal: Double
)

class FoodGstCalculator {

    fun calculateInclusive(
        grossAmount: Double,
        gstRatePercent: Double,
        cgstRatePercent: Double? = null,
        sgstRatePercent: Double? = null,
        cessRatePercent: Double = 0.0,
        withGst: Boolean = true
    ): FoodGstBreakdown {
        val gross = grossAmount.coerceAtLeast(0.0)

        if (!withGst || gross <= 0.0) {
            return FoodGstBreakdown(
                grossAmount = roundMoney(gross),
                taxableAmount = roundMoney(gross),
                gstRatePercent = 0.0,
                cgstRatePercent = 0.0,
                sgstRatePercent = 0.0,
                cessRatePercent = 0.0,
                cgstAmount = 0.0,
                sgstAmount = 0.0,
                cessAmount = 0.0,
                gstAmount = 0.0,
                totalTaxAmount = 0.0,
                lineTotal = roundMoney(gross)
            )
        }

        val gstRate = gstRatePercent.coerceAtLeast(0.0)
        val cessRate = cessRatePercent.coerceAtLeast(0.0)
        val taxRate = gstRate + cessRate

        val taxable = if (taxRate > 0.0) {
            gross / (1.0 + taxRate / 100.0)
        } else {
            gross
        }

        val cgstRate = cgstRatePercent?.takeIf { it > 0.0 } ?: gstRate / 2.0
        val sgstRate = sgstRatePercent?.takeIf { it > 0.0 } ?: gstRate / 2.0

        val cgst = taxable * cgstRate / 100.0
        val sgst = taxable * sgstRate / 100.0
        val cess = taxable * cessRate / 100.0
        val gst = cgst + sgst

        return FoodGstBreakdown(
            grossAmount = roundMoney(gross),
            taxableAmount = roundMoney(taxable),
            gstRatePercent = gstRate,
            cgstRatePercent = cgstRate,
            sgstRatePercent = sgstRate,
            cessRatePercent = cessRate,
            cgstAmount = roundMoney(cgst),
            sgstAmount = roundMoney(sgst),
            cessAmount = roundMoney(cess),
            gstAmount = roundMoney(gst),
            totalTaxAmount = roundMoney(gst + cess),
            lineTotal = roundMoney(gross)
        )
    }

    fun calculateDiscountedInclusive(
        grossAmount: Double,
        discountRatio: Double,
        gstRatePercent: Double,
        cgstRatePercent: Double? = null,
        sgstRatePercent: Double? = null,
        cessRatePercent: Double = 0.0,
        withGst: Boolean = true
    ): FoodGstBreakdown {
        val safeRatio = discountRatio.coerceIn(0.0, 1.0)
        val discountedGross = grossAmount.coerceAtLeast(0.0) * (1.0 - safeRatio)

        return calculateInclusive(
            grossAmount = discountedGross,
            gstRatePercent = gstRatePercent,
            cgstRatePercent = cgstRatePercent,
            sgstRatePercent = sgstRatePercent,
            cessRatePercent = cessRatePercent,
            withGst = withGst
        )
    }

    fun calculateExclusive(
        taxableAmount: Double,
        gstRatePercent: Double,
        cgstRatePercent: Double? = null,
        sgstRatePercent: Double? = null,
        cessRatePercent: Double = 0.0,
        withGst: Boolean = true
    ): FoodGstBreakdown {
        val taxable = taxableAmount.coerceAtLeast(0.0)

        if (!withGst || taxable <= 0.0) {
            return FoodGstBreakdown(
                grossAmount = roundMoney(taxable),
                taxableAmount = roundMoney(taxable),
                gstRatePercent = 0.0,
                cgstRatePercent = 0.0,
                sgstRatePercent = 0.0,
                cessRatePercent = 0.0,
                cgstAmount = 0.0,
                sgstAmount = 0.0,
                cessAmount = 0.0,
                gstAmount = 0.0,
                totalTaxAmount = 0.0,
                lineTotal = roundMoney(taxable)
            )
        }

        val gstRate = gstRatePercent.coerceAtLeast(0.0)
        val cessRate = cessRatePercent.coerceAtLeast(0.0)
        val cgstRate = cgstRatePercent?.takeIf { it > 0.0 } ?: gstRate / 2.0
        val sgstRate = sgstRatePercent?.takeIf { it > 0.0 } ?: gstRate / 2.0
        val cgst = taxable * cgstRate / 100.0
        val sgst = taxable * sgstRate / 100.0
        val cess = taxable * cessRate / 100.0
        val gst = cgst + sgst
        val total = taxable + gst + cess

        return FoodGstBreakdown(
            grossAmount = roundMoney(total),
            taxableAmount = roundMoney(taxable),
            gstRatePercent = gstRate,
            cgstRatePercent = cgstRate,
            sgstRatePercent = sgstRate,
            cessRatePercent = cessRate,
            cgstAmount = roundMoney(cgst),
            sgstAmount = roundMoney(sgst),
            cessAmount = roundMoney(cess),
            gstAmount = roundMoney(gst),
            totalTaxAmount = roundMoney(gst + cess),
            lineTotal = roundMoney(total)
        )
    }

    private fun roundMoney(amount: Double): Double {
        return round(amount * 100.0) / 100.0
    }
}
