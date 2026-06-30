package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.tax.domain.HotelGstBreakdown
import com.example.bookingregister.tax.domain.HotelGstCalculator
import kotlin.math.round
import com.example.bookingregister.data.entities.RoomGstSlabEntity
import com.example.bookingregister.tax.domain.RoomGstRule

data class BookingFinancialSummary(
    val grossCharges: Double,
    val roomRevenue: Double,
    val propertyTax: Double,
    val usesDetailedLines: Boolean
)

class BookingFinancialCalculator(
    private val simpleGstCalculator: HotelGstCalculator = HotelGstCalculator(),
    private val roomGstRule: RoomGstRule = RoomGstRule()
) {

    fun summarize(
        lines: List<BookingFinancialLineEntity>,
        fallbackFinalCharges: Double,
        fallbackRoomCount: Int,
        fallbackNights: Int,
        gstEnabled: Boolean
    ): BookingFinancialSummary {

        val activeLines = lines.filter { !it.isDeleted }

        if (activeLines.isEmpty()) {
            return simpleGstCalculator.calculate(
                finalCharges = fallbackFinalCharges,
                roomCount = fallbackRoomCount,
                nights = fallbackNights,
                gstEnabled = gstEnabled
            ).toSummary()
        }

        return BookingFinancialSummary(
            grossCharges = roundMoney(
                activeLines.sumOf { it.grossAmount.coerceAtLeast(0.0) }
            ),
            roomRevenue = roundMoney(
                activeLines.sumOf { it.taxableAmount.coerceAtLeast(0.0) }
            ),
            propertyTax = if (gstEnabled) {
                roundMoney(
                    activeLines.sumOf { it.gstAmount.coerceAtLeast(0.0) }
                )
            } else {
                0.0
            },
            usesDetailedLines = true
        )
    }

    fun lineFromGross(
        remoteId: String,
        hotelRemoteId: String,
        bookingRemoteId: String,
        roomRemoteId: String,
        businessDateMillis: Long,
        grossAmount: Double,
        gstEnabled: Boolean,
        source: String,
        roomGstSlabs: List<RoomGstSlabEntity> = emptyList()
    ): BookingFinancialLineEntity {

        val safeGross = grossAmount.coerceAtLeast(0.0)

        val breakdown = roomGstRule.calculateFromGross(
            grossRoomTariffPerRoomNight = safeGross,
            slabs = roomGstSlabs,
            gstEnabled = gstEnabled
        )

        return BookingFinancialLineEntity(
            remoteId = remoteId,
            hotelRemoteId = hotelRemoteId,
            bookingRemoteId = bookingRemoteId,
            roomRemoteId = roomRemoteId,
            businessDateMillis = businessDateMillis,
            grossAmount = roundMoney(safeGross),
            taxableAmount = roundMoney(breakdown.taxableAmount),
            gstRatePercent = breakdown.gstRatePercent,
            gstAmount = roundMoney(breakdown.totalTaxAmount),
            hsnSacCode = breakdown.hsnSacCode,
            slabRemoteId = breakdown.slabRemoteId,
            slabName = breakdown.slabName,
            cgstRatePercent = breakdown.cgstRatePercent,
            sgstRatePercent = breakdown.sgstRatePercent,
            cessRatePercent = breakdown.cessRatePercent,
            cgstAmount = roundMoney(breakdown.cgstAmount),
            sgstAmount = roundMoney(breakdown.sgstAmount),
            cessAmount = roundMoney(breakdown.cessAmount),
            source = source
        )
    }

    private fun HotelGstBreakdown.toSummary(): BookingFinancialSummary {
        return BookingFinancialSummary(
            grossCharges = grossCharges,
            roomRevenue = roomRevenue,
            propertyTax = taxAmount,
            usesDetailedLines = false
        )
    }

    private fun roundMoney(amount: Double): Double {
        return round(amount * 100.0) / 100.0
    }
}