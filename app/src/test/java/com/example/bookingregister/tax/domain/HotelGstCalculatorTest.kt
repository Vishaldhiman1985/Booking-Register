package com.example.bookingregister.tax.domain
import org.junit.Assert.assertEquals
import org.junit.Test

class HotelGstCalculatorTest {

    private val calculator = HotelGstCalculator()

    @Test
    fun `room charge up to 1000 has zero gst`() {
        val result = calculator.calculate(
            finalCharges = 1000.0,
            roomCount = 1,
            nights = 1,
            gstEnabled = true
        )

        assertEquals(1000.0, result.grossCharges, 0.01)
        assertEquals(1000.0, result.roomRevenue, 0.01)
        assertEquals(0.0, result.ratePercent, 0.01)
        assertEquals(0.0, result.taxAmount, 0.01)
    }

    @Test
    fun `room gross 7875 extracts 5 percent gst`() {
        val result = calculator.calculate(
            finalCharges = 7875.0,
            roomCount = 1,
            nights = 1,
            gstEnabled = true
        )

        assertEquals(7875.0, result.grossCharges, 0.01)
        assertEquals(7500.0, result.roomRevenue, 0.01)
        assertEquals(5.0, result.ratePercent, 0.01)
        assertEquals(375.0, result.taxAmount, 0.01)
    }

    @Test
    fun `room gross above lower slab extracts 18 percent gst`() {
        val result = calculator.calculate(
            finalCharges = 9440.0,
            roomCount = 1,
            nights = 1,
            gstEnabled = true
        )

        assertEquals(9440.0, result.grossCharges, 0.01)
        assertEquals(8000.0, result.roomRevenue, 0.01)
        assertEquals(18.0, result.ratePercent, 0.01)
        assertEquals(1440.0, result.taxAmount, 0.01)
    }

    @Test
    fun `gst disabled keeps full room amount as revenue`() {
        val result = calculator.calculate(
            finalCharges = 9440.0,
            roomCount = 1,
            nights = 1,
            gstEnabled = false
        )

        assertEquals(9440.0, result.grossCharges, 0.01)
        assertEquals(9440.0, result.roomRevenue, 0.01)
        assertEquals(0.0, result.ratePercent, 0.01)
        assertEquals(0.0, result.taxAmount, 0.01)
    }
}