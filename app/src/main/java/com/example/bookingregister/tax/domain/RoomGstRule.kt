package com.example.bookingregister.tax.domain

import com.example.bookingregister.data.entities.RoomGstSlabEntity
import kotlin.math.round

data class RoomGstRuleResult(
    val slabRemoteId: String?,
    val slabName: String,
    val hsnSacCode: String,
    val grossAmount: Double,
    val taxableAmount: Double,
    val gstRatePercent: Double,
    val cgstRatePercent: Double,
    val sgstRatePercent: Double,
    val cessRatePercent: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val cessAmount: Double,
    val totalTaxAmount: Double
)

class RoomGstRule {

    fun calculateFromGross(
        grossRoomTariffPerRoomNight: Double,
        slabs: List<RoomGstSlabEntity>,
        gstEnabled: Boolean
    ): RoomGstRuleResult {
        val gross = roundMoney(grossRoomTariffPerRoomNight.coerceAtLeast(0.0))

        if (!gstEnabled || gross <= 0.0) {
            return zeroResult(gross)
        }

        val slab = slabs
            .filter { !it.isDeleted && it.isActive }
            .firstOrNull { slab ->
                gross >= slab.minGrossAmount &&
                        (slab.maxGrossAmount == null || gross <= slab.maxGrossAmount)
            }
            ?: return zeroResult(gross)

        val rate = slab.gstRatePercent.coerceAtLeast(0.0)
        val cgstRate = slab.cgstRatePercent.coerceAtLeast(0.0)
        val sgstRate = slab.sgstRatePercent.coerceAtLeast(0.0)
        val cessRate = slab.cessRatePercent.coerceAtLeast(0.0)
        val totalRate = rate + cessRate

        val taxable = if (totalRate > 0.0) {
            roundMoney(gross / (1.0 + totalRate / 100.0))
        } else {
            gross
        }

        val cgstAmount = roundMoney(taxable * cgstRate / 100.0)
        val sgstAmount = roundMoney(taxable * sgstRate / 100.0)
        val cessAmount = roundMoney(taxable * cessRate / 100.0)
        val totalTax = roundMoney(gross - taxable)

        return RoomGstRuleResult(
            slabRemoteId = slab.remoteId,
            slabName = slab.slabName,
            hsnSacCode = slab.hsnSacCode,
            grossAmount = gross,
            taxableAmount = taxable,
            gstRatePercent = rate,
            cgstRatePercent = cgstRate,
            sgstRatePercent = sgstRate,
            cessRatePercent = cessRate,
            cgstAmount = cgstAmount,
            sgstAmount = sgstAmount,
            cessAmount = cessAmount,
            totalTaxAmount = totalTax
        )
    }

    private fun zeroResult(gross: Double): RoomGstRuleResult {
        return RoomGstRuleResult(
            slabRemoteId = null,
            slabName = "No GST",
            hsnSacCode = "996311",
            grossAmount = gross,
            taxableAmount = gross,
            gstRatePercent = 0.0,
            cgstRatePercent = 0.0,
            sgstRatePercent = 0.0,
            cessRatePercent = 0.0,
            cgstAmount = 0.0,
            sgstAmount = 0.0,
            cessAmount = 0.0,
            totalTaxAmount = 0.0
        )
    }

    private fun roundMoney(value: Double): Double = round(value * 100.0) / 100.0
}