package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.FoodBillItemEntity

data class FoodBillTotals(
    val subtotal: Double,
    val taxableAmount: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val cessAmount: Double,
    val gstAmount: Double,
    val grandTotal: Double
)

object FoodBillTotalsCalculator {
    fun calculate(items: List<FoodBillItemEntity>): FoodBillTotals {
        val activeItems = items.filter { !it.isDeleted }
        return FoodBillTotals(
            subtotal = activeItems.sumOf { it.lineSubtotal.takeIf { subtotal -> subtotal > 0.0 } ?: it.lineTotal },
            taxableAmount = activeItems.sumOf { it.taxableAmount },
            cgstAmount = activeItems.sumOf { it.cgstAmount },
            sgstAmount = activeItems.sumOf { it.sgstAmount },
            cessAmount = activeItems.sumOf { it.cessAmount },
            gstAmount = activeItems.sumOf { it.gstAmount },
            grandTotal = activeItems.sumOf { it.lineTotal }
        )
    }
}