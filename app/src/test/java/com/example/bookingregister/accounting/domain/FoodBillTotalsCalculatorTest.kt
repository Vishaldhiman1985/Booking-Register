package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.FoodBillItemEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodBillTotalsCalculatorTest {
    @Test
    fun totalsComeFromGeneratedBillItems() {
        val items = listOf(
            billItem(
                lineSubtotal = 5_000.0,
                taxableAmount = 5_000.0,
                lineTotal = 5_000.0
            ),
            billItem(
                lineSubtotal = 280.0,
                taxableAmount = 266.6666667,
                cgstAmount = 6.66666665,
                sgstAmount = 6.66666665,
                gstAmount = 13.3333333,
                lineTotal = 280.0
            )
        )

        val totals = FoodBillTotalsCalculator.calculate(items)

        assertEquals(5_280.0, totals.subtotal, 0.001)
        assertEquals(5_266.6666667, totals.taxableAmount, 0.001)
        assertEquals(6.66666665, totals.cgstAmount, 0.001)
        assertEquals(6.66666665, totals.sgstAmount, 0.001)
        assertEquals(13.3333333, totals.gstAmount, 0.001)
        assertEquals(5_280.0, totals.grandTotal, 0.001)
    }

    @Test
    fun deletedItemsAreIgnored() {
        val totals = FoodBillTotalsCalculator.calculate(
            listOf(
                billItem(lineSubtotal = 100.0, taxableAmount = 100.0, lineTotal = 100.0),
                billItem(lineSubtotal = 50.0, taxableAmount = 50.0, lineTotal = 50.0, isDeleted = true)
            )
        )

        assertEquals(100.0, totals.subtotal, 0.001)
        assertEquals(100.0, totals.taxableAmount, 0.001)
        assertEquals(100.0, totals.grandTotal, 0.001)
    }

    private fun billItem(
        lineSubtotal: Double,
        taxableAmount: Double,
        cgstAmount: Double = 0.0,
        sgstAmount: Double = 0.0,
        cessAmount: Double = 0.0,
        gstAmount: Double = 0.0,
        lineTotal: Double,
        isDeleted: Boolean = false
    ): FoodBillItemEntity = FoodBillItemEntity(
        remoteId = "item_${lineSubtotal}_${lineTotal}_${isDeleted}",
        hotelRemoteId = "hotel_1",
        billRemoteId = "bill_1",
        orderRemoteId = "order_1",
        itemName = "Item",
        lineSubtotal = lineSubtotal,
        taxableAmount = taxableAmount,
        cgstAmount = cgstAmount,
        sgstAmount = sgstAmount,
        cessAmount = cessAmount,
        gstAmount = gstAmount,
        lineTotal = lineTotal,
        isDeleted = isDeleted
    )
}