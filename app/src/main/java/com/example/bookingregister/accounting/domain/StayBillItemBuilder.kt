package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.FoodBillItemEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object StayBillItemBuilder {
    private const val ROOM_STAY_HSN = "996311"
    private val billDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun build(
        billRemoteId: String,
        hotelRemoteId: String,
        booking: BookingEntity,
        roomsIncluded: String,
        stayTotal: Double,
        financialLines: List<BookingFinancialLineEntity>,
        roomNamesById: Map<String, String> = emptyMap(),
        now: Long,
        idFactory: () -> String = { UUID.randomUUID().toString() }
    ): List<FoodBillItemEntity> {

        if (stayTotal <= 0.0) return emptyList()

        val activeLines = financialLines
            .filter { !it.isDeleted && it.grossAmount > 0.0 }
            .sortedWith(
                compareBy<BookingFinancialLineEntity> { it.businessDateMillis }
                    .thenBy { it.roomRemoteId }
            )

        if (activeLines.isEmpty()) {
            return emptyList()
        }

        return activeLines.map { line ->
            val gross = line.grossAmount.coerceAtLeast(0.0)
            val taxable = line.taxableAmount.coerceAtLeast(0.0)
            val gst = line.gstAmount.coerceAtLeast(0.0)

            val guestPayable = (taxable + gst)
                .takeIf { taxable > 0.0 && it > 0.0 }
                ?: gross

            FoodBillItemEntity(
                remoteId = "${billRemoteId}_stay_${line.remoteId}_${idFactory()}",
                hotelRemoteId = hotelRemoteId,
                billRemoteId = billRemoteId,
                orderRemoteId = booking.remoteId,
                orderNumber = booking.bookingUuid,
                orderMillis = line.businessDateMillis,
                roomName = roomNamesById[line.roomRemoteId]?.takeIf { it.isNotBlank() }
                    ?: roomsIncluded,
                itemName = roomNightLabel(line, roomNamesById),
                quantity = 1.0,
                unitPrice = guestPayable,
                lineSubtotal = guestPayable,

                hsnSacCode = line.hsnSacCode ?: ROOM_STAY_HSN,

                gstRatePercent = line.gstRatePercent.coerceAtLeast(0.0),
                cgstRatePercent = line.cgstRatePercent.coerceAtLeast(0.0),
                sgstRatePercent = line.sgstRatePercent.coerceAtLeast(0.0),
                cessRatePercent = line.cessRatePercent.coerceAtLeast(0.0),

                taxableAmount = taxable,

                cgstAmount = line.cgstAmount.coerceAtLeast(0.0),
                sgstAmount = line.sgstAmount.coerceAtLeast(0.0),
                cessAmount = line.cessAmount.coerceAtLeast(0.0),
                gstAmount = gst,

                lineTotal = guestPayable,

                updatedAt = now,
                syncState = SyncState.PENDING
            )
        }
    }

    private fun roomNightLabel(
        line: BookingFinancialLineEntity,
        roomNamesById: Map<String, String>
    ): String {
        val dateText = billDateFormat.format(Date(line.businessDateMillis))
        val gstText = line.gstRatePercent
            .takeIf { it > 0.0 }
            ?.let { " (${formatRate(it)}% GST)" }
            .orEmpty()

        val roomName = roomNamesById[line.roomRemoteId]
            ?.takeIf { it.isNotBlank() }
            ?: line.roomRemoteId

        return "Room stay - $roomName - $dateText$gstText"
    }

    private fun formatRate(rate: Double): String {
        return if (rate % 1.0 == 0.0) {
            rate.toInt().toString()
        } else {
            "%.2f".format(rate)
        }
    }
}