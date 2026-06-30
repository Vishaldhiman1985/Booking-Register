package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class StayBillItemBuilderTest {
    @Test
    fun groupsDetailedStayLinesByGstRate() {
        val items = StayBillItemBuilder.build(
            billRemoteId = "bill_1",
            hotelRemoteId = "hotel_1",
            booking = booking(roomRevenue = 0.0, propertyTax = 0.0),
            roomsIncluded = "101",
            stayTotal = 13_300.0,
            financialLines = listOf(
                line(gross = 5_250.0, taxable = 5_000.0, gst = 250.0, rate = 5.0),
                line(gross = 8_050.0, taxable = 6_822.03, gst = 1_227.97, rate = 18.0)
            ),
            now = 100L,
            idFactory = sequentialIds()
        )

        assertEquals(2, items.size)
        assertEquals(5.0, items[0].gstRatePercent, 0.001)
        assertEquals(5_000.0, items[0].taxableAmount, 0.001)
        assertEquals(250.0, items[0].gstAmount, 0.001)
        assertEquals(18.0, items[1].gstRatePercent, 0.001)
        assertEquals(6_822.03, items[1].taxableAmount, 0.001)
        assertEquals(1_227.97, items[1].gstAmount, 0.001)
    }

    @Test
    fun fallbackUsesStoredBookingRoomTaxWhenDetailedLinesAreMissing() {
        val items = StayBillItemBuilder.build(
            billRemoteId = "bill_1",
            hotelRemoteId = "hotel_1",
            booking = booking(roomRevenue = 4_761.90, propertyTax = 238.10),
            roomsIncluded = "101",
            stayTotal = 5_000.0,
            financialLines = emptyList(),
            now = 100L,
            idFactory = sequentialIds()
        )

        assertEquals(1, items.size)
        assertEquals(4_761.90, items.single().taxableAmount, 0.001)
        assertEquals(238.10, items.single().gstAmount, 0.001)
        assertEquals(5_000.0, items.single().lineTotal, 0.001)
    }

    @Test
    fun detailedOtaStayLineUsesGuestFinalPriceWhenGrossContainsNetPayout() {
        val items = StayBillItemBuilder.build(
            billRemoteId = "bill_1",
            hotelRemoteId = "hotel_1",
            booking = booking(roomRevenue = 2_095.24, propertyTax = 104.76),
            roomsIncluded = "H102",
            stayTotal = 1_692.95,
            financialLines = listOf(
                line(gross = 1_692.95, taxable = 2_095.24, gst = 104.76, rate = 5.0)
            ),
            now = 100L,
            idFactory = sequentialIds()
        )

        val item = items.single()
        assertEquals(2_095.24, item.taxableAmount, 0.001)
        assertEquals(104.76, item.gstAmount, 0.001)
        assertEquals(2_200.0, item.lineTotal, 0.001)
        assertEquals(2_200.0, item.unitPrice, 0.001)
    }

    @Test
    fun fallbackOtaStayLineUsesGuestFinalPriceWhenStayTotalIsNetPayout() {
        val items = StayBillItemBuilder.build(
            billRemoteId = "bill_1",
            hotelRemoteId = "hotel_1",
            booking = booking(roomRevenue = 2_095.24, propertyTax = 104.76),
            roomsIncluded = "H102",
            stayTotal = 1_692.95,
            financialLines = emptyList(),
            now = 100L,
            idFactory = sequentialIds()
        )

        val item = items.single()
        assertEquals(2_095.24, item.taxableAmount, 0.001)
        assertEquals(104.76, item.gstAmount, 0.001)
        assertEquals(2_200.0, item.lineTotal, 0.001)
    }

    private fun booking(roomRevenue: Double, propertyTax: Double): BookingEntity = BookingEntity(
        remoteId = "booking_1",
        bookingUuid = "BK-1",
        hotelRemoteId = "hotel_1",
        guestName = "Guest",
        checkInMillis = 1L,
        checkOutMillis = 2L,
        roomRemoteIds = listOf("room_1"),
        receivable = 5_000.0,
        grossCharges = 5_000.0,
        roomRevenue = roomRevenue,
        propertyTax = propertyTax
    )

    private fun line(
        gross: Double,
        taxable: Double,
        gst: Double,
        rate: Double
    ): BookingFinancialLineEntity = BookingFinancialLineEntity(
        remoteId = "line_${gross}_${rate}",
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        roomRemoteId = "room_1",
        businessDateMillis = 1L,
        grossAmount = gross,
        taxableAmount = taxable,
        gstRatePercent = rate,
        gstAmount = gst
    )

    private fun sequentialIds(): () -> String {
        var next = 0
        return { "id_${next++}" }
    }
}
