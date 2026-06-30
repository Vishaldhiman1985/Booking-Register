package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.FoodBillItemEntity
import java.util.UUID

object StayBillItemBuilder {
    private const val ROOM_STAY_HSN = "996311"

    fun build(
        billRemoteId: String,
        hotelRemoteId: String,
        booking: BookingEntity,
        roomsIncluded: String,
        stayTotal: Double,
        financialLines: List<BookingFinancialLineEntity>,
        now: Long,
        idFactory: () -> String = { UUID.randomUUID().toString() }
    ): List<FoodBillItemEntity> {
        if (stayTotal <= 0.0) return emptyList()
        val activeLines = financialLines.filter { !it.isDeleted && it.grossAmount > 0.0 }
        if (activeLines.isEmpty()) {
            return listOf(fallbackItem(billRemoteId, hotelRemoteId, booking, roomsIncluded, stayTotal, now, idFactory))
        }

        return activeLines
            .groupBy { it.gstRatePercent.coerceAtLeast(0.0) }
            .toSortedMap()
            .map { (rate, lines) ->
                val taxable = lines.sumOf { it.taxableAmount.coerceAtLeast(0.0) }
                val gst = lines.sumOf { it.gstAmount.coerceAtLeast(0.0) }
                val storedGross = lines.sumOf { it.grossAmount.coerceAtLeast(0.0) }
                val grossFromTax = taxable + gst
                val guestPayable = if (gst > 0.0 && grossFromTax > 0.0) grossFromTax else storedGross
                val quantity = lines.size.toDouble().coerceAtLeast(1.0)
                FoodBillItemEntity(
                    remoteId = "${billRemoteId}_stay_${idFactory()}",
                    hotelRemoteId = hotelRemoteId,
                    billRemoteId = billRemoteId,
                    orderRemoteId = booking.remoteId,
                    orderNumber = booking.bookingUuid,
                    orderMillis = lines.minOfOrNull { it.businessDateMillis } ?: booking.checkInMillis,
                    roomName = roomsIncluded,
                    itemName = if (rate > 0.0) "Room stay (${formatRate(rate)}% GST)" else "Room stay",
                    quantity = quantity,
                    unitPrice = guestPayable / quantity,
                    lineSubtotal = guestPayable,
                    hsnSacCode = ROOM_STAY_HSN,
                    gstRatePercent = rate,
                    cgstRatePercent = rate / 2.0,
                    sgstRatePercent = rate / 2.0,
                    taxableAmount = taxable,
                    cgstAmount = gst / 2.0,
                    sgstAmount = gst / 2.0,
                    gstAmount = gst,
                    lineTotal = guestPayable,
                    updatedAt = now,
                    syncState = SyncState.PENDING
                )
            }
    }

    private fun fallbackItem(
        billRemoteId: String,
        hotelRemoteId: String,
        booking: BookingEntity,
        roomsIncluded: String,
        stayTotal: Double,
        now: Long,
        idFactory: () -> String
    ): FoodBillItemEntity {
        val storedTaxable = booking.roomRevenue.takeIf { it > 0.0 }
        val storedGst = booking.propertyTax.takeIf { it > 0.0 }
        val guestStayTotal = booking.grossCharges.takeIf { it > 0.0 } ?: stayTotal
        val taxable = storedTaxable ?: stayTotal
        val gst = storedGst ?: 0.0
        val guestPayable = if (gst > 0.0 && taxable + gst > 0.0) taxable + gst else guestStayTotal
        val rate = if (taxable > 0.0 && gst > 0.0) (gst / taxable) * 100.0 else 0.0
        return FoodBillItemEntity(
            remoteId = "${billRemoteId}_stay_${idFactory()}",
            hotelRemoteId = hotelRemoteId,
            billRemoteId = billRemoteId,
            orderRemoteId = booking.remoteId,
            orderNumber = booking.bookingUuid,
            orderMillis = booking.checkInMillis,
            roomName = roomsIncluded,
            itemName = if (rate > 0.0) "Room stay (${formatRate(rate)}% GST)" else "Room stay",
            quantity = 1.0,
            unitPrice = guestPayable,
            lineSubtotal = guestPayable,
            hsnSacCode = ROOM_STAY_HSN,
            gstRatePercent = rate,
            cgstRatePercent = rate / 2.0,
            sgstRatePercent = rate / 2.0,
            taxableAmount = taxable,
            cgstAmount = gst / 2.0,
            sgstAmount = gst / 2.0,
            gstAmount = gst,
            lineTotal = guestPayable,
            updatedAt = now,
            syncState = SyncState.PENDING
        )
    }

    private fun formatRate(rate: Double): String {
        return if (rate % 1.0 == 0.0) rate.toInt().toString() else "%.2f".format(rate)
    }
}
