package com.example.bookingregister.tax.domain

import kotlin.math.round

data class HotelGstBreakdown(
    val gstEnabled: Boolean,
    val grossCharges: Double,
    val roomRevenue: Double,
    val roomChargePerRoomNight: Double,
    val ratePercent: Double,
    val taxAmount: Double
)

class HotelGstCalculator {
    private companion object {
        const val GST_FREE_TARIFF_LIMIT = 1000.0
        const val LOWER_SLAB_TARIFF_LIMIT = 7500.0
        const val LOWER_SLAB_RATE = 5.0
        const val HIGHER_SLAB_RATE = 18.0
    }

    fun calculate(
        finalCharges: Double,
        roomCount: Int,
        nights: Int,
        gstEnabled: Boolean
    ): HotelGstBreakdown {
        val grossCharges = finalCharges.coerceAtLeast(0.0)
        val roomNights = (roomCount.coerceAtLeast(1) * nights.coerceAtLeast(1)).coerceAtLeast(1)
        val grossPerRoomNight = grossCharges / roomNights
        val rate = rateForGross(grossPerRoomNight, gstEnabled)
        val roomRevenue = if (rate > 0.0) {
            grossCharges / (1.0 + rate / 100.0)
        } else {
            grossCharges
        }
        val tax = grossCharges - roomRevenue

        return HotelGstBreakdown(
            gstEnabled = gstEnabled,
            grossCharges = roundMoney(grossCharges),
            roomRevenue = roundMoney(roomRevenue),
            roomChargePerRoomNight = roundMoney(roomRevenue / roomNights),
            ratePercent = rate,
            taxAmount = roundMoney(tax)
        )
    }

    private fun rateForGross(grossPerRoomNight: Double, gstEnabled: Boolean): Double {
        if (!gstEnabled || grossPerRoomNight <= 0.0) return 0.0
        val lowerSlabGrossLimit = LOWER_SLAB_TARIFF_LIMIT * (1.0 + LOWER_SLAB_RATE / 100.0)
        return when {
            grossPerRoomNight <= GST_FREE_TARIFF_LIMIT -> 0.0
            grossPerRoomNight <= lowerSlabGrossLimit -> LOWER_SLAB_RATE
            else -> HIGHER_SLAB_RATE
        }
    }

    private fun roundMoney(amount: Double): Double {
        return round(amount * 100.0) / 100.0
    }
}
