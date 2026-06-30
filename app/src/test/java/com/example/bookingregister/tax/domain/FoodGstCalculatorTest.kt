package com.example.bookingregister.tax.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodGstCalculatorTest {

    private val calculator = FoodGstCalculator()

    @Test
    fun `inclusive food gst 5 percent extracts taxable and gst correctly`() {
        val result = calculator.calculateInclusive(
            grossAmount = 105.0,
            gstRatePercent = 5.0
        )

        assertEquals(105.0, result.lineTotal, 0.01)
        assertEquals(100.0, result.taxableAmount, 0.01)
        assertEquals(2.5, result.cgstAmount, 0.01)
        assertEquals(2.5, result.sgstAmount, 0.01)
        assertEquals(5.0, result.gstAmount, 0.01)
        assertEquals(5.0, result.totalTaxAmount, 0.01)
    }

    @Test
    fun `inclusive food gst 18 percent extracts taxable and gst correctly`() {
        val result = calculator.calculateInclusive(
            grossAmount = 118.0,
            gstRatePercent = 18.0
        )

        assertEquals(118.0, result.lineTotal, 0.01)
        assertEquals(100.0, result.taxableAmount, 0.01)
        assertEquals(9.0, result.cgstAmount, 0.01)
        assertEquals(9.0, result.sgstAmount, 0.01)
        assertEquals(18.0, result.gstAmount, 0.01)
    }

    @Test
    fun `food without gst keeps gross as taxable`() {
        val result = calculator.calculateInclusive(
            grossAmount = 500.0,
            gstRatePercent = 5.0,
            withGst = false
        )

        assertEquals(500.0, result.lineTotal, 0.01)
        assertEquals(500.0, result.taxableAmount, 0.01)
        assertEquals(0.0, result.cgstAmount, 0.01)
        assertEquals(0.0, result.sgstAmount, 0.01)
        assertEquals(0.0, result.totalTaxAmount, 0.01)
    }
}