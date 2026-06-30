package com.example.bookingregister.revenue.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.folio.domain.MiniFolio
import com.example.bookingregister.folio.domain.MiniFolioBuilder
import com.example.bookingregister.folio.domain.MiniFolioLine
import com.example.bookingregister.folio.domain.MiniFolioLineType

class RevenueLedgerBuilder(
    private val miniFolioBuilder: MiniFolioBuilder = MiniFolioBuilder()
) {
    fun build(
        rooms: List<RoomEntity>,
        bookings: List<BookingEntity>
    ): List<RevenueLedgerEntry> {
        return build(miniFolioBuilder.build(rooms, bookings))
    }

    fun build(folios: List<MiniFolio>): List<RevenueLedgerEntry> {
        return folios
            .flatMap { folio -> folio.lines.map { line -> line.toLedgerEntry(folio) } }
            .sortedWith(compareBy<RevenueLedgerEntry> { it.businessDateMillis }.thenBy { it.type })
    }

    private fun MiniFolioLine.toLedgerEntry(folio: MiniFolio): RevenueLedgerEntry {
        return RevenueLedgerEntry(
            bookingRemoteId = bookingRemoteId,
            businessDateMillis = businessDateMillis,
            type = toRevenueType(type),
            amount = amount,
            guestName = folio.guestName,
            roomRemoteId = roomRemoteId,
            source = RevenueLedgerSource.MINI_FOLIO
        )
    }

    private fun toRevenueType(folioLineType: String): String {
        return when (folioLineType) {
            MiniFolioLineType.ROOM_CHARGE -> RevenueLedgerEntryType.ROOM_REVENUE
            MiniFolioLineType.PAYMENT_RECEIVED -> RevenueLedgerEntryType.PAYMENT_RECEIVED
            MiniFolioLineType.DISCOUNT -> RevenueLedgerEntryType.DISCOUNT
            MiniFolioLineType.REFUND -> RevenueLedgerEntryType.REFUND
            else -> RevenueLedgerEntryType.ADJUSTMENT
        }
    }
}
