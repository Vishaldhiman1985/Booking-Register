package com.example.bookingregister.source.domain

import com.example.bookingregister.data.entities.BookingSourceEntity
import kotlin.math.round

data class SourceSettlement(
    val grossCharges: Double,
    val roomRevenue: Double,
    val propertyTax: Double,
    val commission: Double,
    val commissionTax: Double,
    val fixedFee: Double,
    val tcs: Double,
    val tds: Double,
    val expectedPayout: Double
)

class SourceSettlementCalculator {
    fun calculate(
        source: BookingSourceEntity?,
        roomCharges: Double,
        propertyTax: Double,
        hotelHasGst: Boolean
    ): SourceSettlement {
        val safeRoomCharges = roomCharges.coerceAtLeast(0.0)
        val safePropertyTax = if (hotelHasGst) propertyTax.coerceAtLeast(0.0) else 0.0
        val gross = safeRoomCharges + safePropertyTax
        val commissionBase = safeRoomCharges
        val commission = percentOf(commissionBase, source?.commissionPercent ?: 0.0)
        val commissionTax = percentOf(commission, source?.commissionGstPercent ?: 0.0)
        val fixedFee = (source?.fixedFee ?: 0.0).coerceAtLeast(0.0)
        val tcs = percentOf(safeRoomCharges, source?.tcsPercent ?: 0.0)
        val tds = percentOf(safeRoomCharges, source?.tdsPercent ?: 0.0)
        val payout = (gross - commission - commissionTax - fixedFee - tcs - tds).coerceAtLeast(0.0)
        return SourceSettlement(
            grossCharges = roundMoney(gross),
            roomRevenue = roundMoney(safeRoomCharges),
            propertyTax = roundMoney(safePropertyTax),
            commission = roundMoney(commission),
            commissionTax = roundMoney(commissionTax),
            fixedFee = roundMoney(fixedFee),
            tcs = roundMoney(tcs),
            tds = roundMoney(tds),
            expectedPayout = roundMoney(payout)
        )
    }

    private fun percentOf(amount: Double, percent: Double): Double {
        return amount.coerceAtLeast(0.0) * percent.coerceAtLeast(0.0) / 100.0
    }

    private fun roundMoney(amount: Double): Double {
        return round(amount * 100.0) / 100.0
    }
}
