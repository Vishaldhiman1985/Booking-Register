package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.finalbill.domain.FinalBillPreviewBuilder
import com.example.bookingregister.folio.domain.FolioSummaryBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomSingleSourceTruthTest {

    @Test
    fun roomFinancialLines_are_single_source_for_folio_and_final_bill() {
        val booking = BookingEntity(
            remoteId = "booking_1",
            bookingUuid = "BR-1",
            hotelRemoteId = "hotel_1",
            guestName = "Ram",
            sourceName = "Walk-in",
            sourceType = BookingSourceType.DIRECT,
            checkInMillis = 1_700_000_000_000L,
            checkOutMillis = 1_700_086_400_000L,
            roomRemoteIds = listOf("room_101", "room_102"),
            grossCharges = 10_000.0,
            roomRevenue = 9_523.82,
            propertyTax = 476.18,
            rate = 10_000.0,
            receivable = 10_000.0
        )

        val rooms = listOf(
            RoomEntity(
                remoteId = "room_101",
                hotelRemoteId = "hotel_1",
                roomName = "101"
            ),
            RoomEntity(
                remoteId = "room_102",
                hotelRemoteId = "hotel_1",
                roomName = "102"
            )
        )

        val financialLines = listOf(
            roomLine("line_1", "booking_1", "room_101", 5_000.0, 4_761.91, 238.09),
            roomLine("line_2", "booking_1", "room_102", 5_000.0, 4_761.91, 238.09)
        )

        val folioSummary = FolioSummaryBuilder.build(
            booking = booking,
            payments = emptyList(),
            foodOrders = emptyList(),
            bookingFinancialLines = financialLines
        )

        val finalBillPreview = FinalBillPreviewBuilder().build(
            booking = booking,
            rooms = rooms,
            bookingPayments = emptyList(),
            bookingFinancialLines = financialLines,
            accountingCharges = emptyList(),
            foodOrders = emptyList(),
            foodOrderItems = emptyList()
        )

        assertEquals(10_000.0, financialLines.sumOf { it.grossAmount }, 0.01)
        assertEquals(9_523.82, financialLines.sumOf { it.taxableAmount }, 0.01)
        assertEquals(476.18, financialLines.sumOf { it.gstAmount }, 0.01)

        assertEquals(10_000.0, folioSummary.stayTotal, 0.01)
        assertEquals(10_000.0, finalBillPreview.roomCharges, 0.01)

        assertEquals(2, finalBillPreview.roomItems.size)
        assertEquals("996311", finalBillPreview.roomItems.first().hsnSacCode)
    }

    @Test
    fun roomFinancialLines_win_over_stale_booking_cache_after_reinstall() {
        val bookingWithStaleCache = BookingEntity(
            remoteId = "booking_stale_cache",
            bookingUuid = "BR-STale-1",
            hotelRemoteId = "hotel_1",
            guestName = "new sync",
            sourceName = "Walk-in",
            sourceType = BookingSourceType.DIRECT,
            checkInMillis = 1_720_310_400_000L,
            checkOutMillis = 1_720_483_200_000L,
            roomRemoteIds = listOf("room_101"),
            grossCharges = 10_700.0,
            roomRevenue = 10_700.0,
            propertyTax = 0.0,
            rate = 10_700.0,
            receivable = 10_700.0,
            paid = 500.0
        )

        val financialLines = listOf(
            roomLine(
                remoteId = "line_new_total",
                bookingRemoteId = "booking_stale_cache",
                roomRemoteId = "room_101",
                gross = 29_999.0,
                taxable = 28_570.44,
                gst = 1_428.56
            )
        )

        val rooms = listOf(
            RoomEntity(
                remoteId = "room_101",
                hotelRemoteId = "hotel_1",
                roomName = "101"
            )
        )

        val financialSummary = BookingFinancialCalculator().summarize(
            lines = financialLines,
            fallbackFinalCharges = bookingWithStaleCache.grossCharges,
            fallbackRoomCount = bookingWithStaleCache.roomRemoteIds.size,
            fallbackNights = 2,
            gstEnabled = true
        )
        val folioSummary = FolioSummaryBuilder.build(
            booking = bookingWithStaleCache,
            payments = emptyList(),
            foodOrders = emptyList(),
            bookingFinancialLines = financialLines
        )
        val finalBillPreview = FinalBillPreviewBuilder().build(
            booking = bookingWithStaleCache,
            rooms = rooms,
            bookingPayments = emptyList(),
            bookingFinancialLines = financialLines,
            accountingCharges = emptyList(),
            foodOrders = emptyList(),
            foodOrderItems = emptyList()
        )

        assertEquals(29_999.0, financialSummary.grossCharges, 0.01)
        assertEquals(28_570.44, financialSummary.roomRevenue, 0.01)
        assertEquals(1_428.56, financialSummary.propertyTax, 0.01)
        assertEquals(29_999.0, folioSummary.stayTotal, 0.01)
        assertEquals(29_999.0, finalBillPreview.roomCharges, 0.01)
    }

    private fun roomLine(
        remoteId: String,
        bookingRemoteId: String,
        roomRemoteId: String,
        gross: Double,
        taxable: Double,
        gst: Double
    ): BookingFinancialLineEntity {
        return BookingFinancialLineEntity(
            remoteId = remoteId,
            hotelRemoteId = "hotel_1",
            bookingRemoteId = bookingRemoteId,
            roomRemoteId = roomRemoteId,
            businessDateMillis = 1_700_000_000_000L,
            grossAmount = gross,
            taxableAmount = taxable,
            gstRatePercent = 5.0,
            gstAmount = gst,
            hsnSacCode = "996311",
            cgstRatePercent = 2.5,
            sgstRatePercent = 2.5,
            cgstAmount = gst / 2.0,
            sgstAmount = gst / 2.0
        )
    }
}
