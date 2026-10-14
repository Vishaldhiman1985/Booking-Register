package com.example.bookingregister.accounting.domain

import com.example.bookingregister.booking.domain.BookingRoomNightAssignment
import com.example.bookingregister.booking.domain.BookingRoomPlanResolver
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.finalbill.domain.FinalBillPreviewBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingRoomAttributionIntegrationTest {
    private val day1 = 0L
    private val day2 = 86_400_000L
    private val propertyId = "property_1"

    private fun booking() = BookingEntity(
        remoteId = "booking_lali",
        bookingUuid = "BK-LALI",
        hotelRemoteId = "hotel_1",
        guestName = "Lali",
        sourceType = BookingSourceType.OTA,
        checkInMillis = day1,
        checkOutMillis = day2 + 86_400_000L,
        roomRemoteIds = listOf("p3"),
        propertyRemoteId = propertyId,
        receivable = 5_200.0,
        rate = 5_200.0,
        grossCharges = 5_200.0,
        expectedPayout = 4_232.80
    )

    private fun lines() = listOf(day1, day2).mapIndexed { index, date ->
        BookingFinancialLineEntity(
            remoteId = "line_$index",
            hotelRemoteId = "hotel_1",
            bookingRemoteId = "booking_lali",
            roomRemoteId = "p3",
            propertyRemoteId = propertyId,
            businessDateMillis = date,
            grossAmount = 2_600.0,
            taxableAmount = 2_600.0
        )
    }

    private fun plan() = BookingRoomPlanResolver.resolve(
        checkInMillis = day1,
        checkOutMillis = day2 + 86_400_000L,
        legacyRoomRemoteIds = listOf("p3"),
        legacyPropertyRemoteId = propertyId,
        explicitAssignments = listOf(
            BookingRoomNightAssignment(day1, "p2", propertyId),
            BookingRoomNightAssignment(day2, "p3", propertyId)
        )
    )

    @Test
    fun invoiceShowsOccupiedRoomButKeepsBilledRoomAndAmounts() {
        val items = StayBillItemBuilder.build(
            billRemoteId = "bill_1",
            hotelRemoteId = "hotel_1",
            booking = booking(),
            roomsIncluded = "P3",
            stayTotal = 5_200.0,
            financialLines = lines(),
            roomNamesById = mapOf("p2" to "P2", "p3" to "P3"),
            now = 1L,
            idFactory = { "fixed" },
            roomPlan = plan()
        )
        assertEquals(2, items.size)
        assertTrue(items[0].itemName.contains("P2 (billed P3)"))
        assertEquals("P3", items[0].roomName)
        assertTrue(items[1].itemName.contains("P3"))
        assertEquals("P3", items[1].roomName)
        assertEquals(5_200.0, items.sumOf { it.lineTotal }, 0.001)
        assertEquals(5_200.0, items.sumOf { it.taxableAmount }, 0.001)
    }

    @Test
    fun previewShowsSameOccupancyAndPreservesOtaPayout() {
        val preview = FinalBillPreviewBuilder().build(
            booking = booking(),
            rooms = listOf(
                RoomEntity(remoteId = "p2", hotelRemoteId = "hotel_1", roomName = "P2", propertyRemoteId = propertyId),
                RoomEntity(remoteId = "p3", hotelRemoteId = "hotel_1", roomName = "P3", propertyRemoteId = propertyId)
            ),
            bookingFinancialLines = lines(),
            foodOrders = emptyList(),
            foodOrderItems = emptyList(),
            roomPlan = plan()
        )
        assertEquals(2, preview.roomItems.size)
        assertEquals("P2 (billed P3)", preview.roomItems[0].roomName)
        assertEquals("P3", preview.roomItems[1].roomName)
        assertEquals(5_200.0, preview.roomCharges, 0.001)
        assertEquals(4_232.80, preview.roomBalance, 0.001)
    }

    @Test
    fun missingBilledPropertyDoesNotInventNewInvoiceRoom() {
        val items = StayBillItemBuilder.build(
            billRemoteId = "bill_2",
            hotelRemoteId = "hotel_1",
            booking = booking(),
            roomsIncluded = "P3",
            stayTotal = 5_200.0,
            financialLines = lines().map { it.copy(propertyRemoteId = null) },
            roomNamesById = mapOf("p2" to "P2", "p3" to "P3"),
            now = 1L,
            idFactory = { "fixed" },
            roomPlan = plan()
        )
        assertTrue(items[0].itemName.contains("Room stay - P3 -"))
        assertEquals(5_200.0, items.sumOf { it.lineTotal }, 0.001)
    }
}
