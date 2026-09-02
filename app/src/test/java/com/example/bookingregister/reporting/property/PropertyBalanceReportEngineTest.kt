package com.example.bookingregister.reporting.property

import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.data.entities.RoomEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PropertyBalanceReportEngineTest {

    private val engine = PropertyBalanceReportEngine()
    private val day = 86_400_000L

    @Test
    fun build_keepsBalanceStrictlyInsideSelectedProperty() {
        val propertyA = "property_a"
        val propertyB = "property_b"

        val agoda = BookingEntity(
            remoteId = "booking_agoda_a",
            bookingUuid = "uuid_agoda_a",
            hotelRemoteId = "hotel_1",
            propertyRemoteId = propertyA,
            guestName = "OTA Guest",
            checkInMillis = day,
            checkOutMillis = 2 * day,
            roomRemoteIds = listOf("room_a"),
            bookingStatus = BookingStatus.RESERVED,
            sourceType = BookingSourceType.OTA,
            sourceRemoteId = "agoda_id",
            sourceName = "Agoda",
            expectedPayout = 700.0
        )

        val direct = BookingEntity(
            remoteId = "booking_direct_a",
            bookingUuid = "uuid_direct_a",
            hotelRemoteId = "hotel_1",
            propertyRemoteId = propertyA,
            guestName = "Direct Guest",
            checkInMillis = day,
            checkOutMillis = 2 * day,
            roomRemoteIds = listOf("room_a"),
            bookingStatus = BookingStatus.RESERVED,
            sourceType = BookingSourceType.DIRECT,
            receivable = 500.0
        )

        val bookingCom = BookingEntity(
            remoteId = "booking_ota_b",
            bookingUuid = "uuid_ota_b",
            hotelRemoteId = "hotel_1",
            propertyRemoteId = propertyB,
            guestName = "Other Property Guest",
            checkInMillis = day,
            checkOutMillis = 2 * day,
            roomRemoteIds = listOf("room_b"),
            bookingStatus = BookingStatus.RESERVED,
            sourceType = BookingSourceType.OTA,
            sourceRemoteId = "booking_com_id",
            sourceName = "Booking.com",
            expectedPayout = 900.0
        )

        val raw = PropertyReportRawData(
            properties = emptyList(),
            rooms = listOf(
                RoomEntity(
                    remoteId = "room_a",
                    hotelRemoteId = "hotel_1",
                    roomName = "A1",
                    propertyRemoteId = propertyA
                ),
                RoomEntity(
                    remoteId = "room_b",
                    hotelRemoteId = "hotel_1",
                    roomName = "B1",
                    propertyRemoteId = propertyB
                )
            ),
            bookings = listOf(agoda, direct, bookingCom),
            financialLines = emptyList(),
            payments = emptyList()
        )

        val result = engine.build(
            raw = raw,
            scope = PropertyReportScope(
                hotelRemoteId = "hotel_1",
                propertyRemoteId = propertyA,
                includeAllProperties = false,
                startMillis = 0,
                endMillis = 10 * day
            )
        )

        assertEquals(1_200.0, result.facts.totalReceivable, 0.001)
        assertEquals(1_200.0, result.facts.totalOutstanding, 0.001)
        assertEquals(700.0, result.facts.otaOutstanding, 0.001)
        assertEquals(500.0, result.facts.guestOutstanding, 0.001)
        assertEquals(2, result.facts.openBookingCount)

        assertEquals(1, result.otaReceivables.size)
        assertEquals("Agoda", result.otaReceivables.single().sourceName)
        assertEquals(700.0, result.otaReceivables.single().totalOutstanding, 0.001)

        assertEquals(1, result.directGuestBalances.size)
        assertEquals(
            "Direct Guest",
            result.directGuestBalances.single().guestName
        )
    }

    @Test
    fun otaGrouping_groupsSameOtaWithoutChangingAmounts() {
        val rows = listOf(
            PropertyBalanceBookingFacts(
                bookingRemoteId = "b1",
                guestName = "Guest One",
                sourceType = BookingSourceType.OTA,
                sourceRemoteId = "agoda_id",
                sourceName = "Agoda",
                receivable = 500.0,
                received = 100.0,
                outstanding = 400.0,
                storedPaidCache = 0.0,
                storedBalanceCache = 0.0
            ),
            PropertyBalanceBookingFacts(
                bookingRemoteId = "b2",
                guestName = "Guest Two",
                sourceType = BookingSourceType.OTA,
                sourceRemoteId = "agoda_id",
                sourceName = "AGODA",
                receivable = 800.0,
                received = 200.0,
                outstanding = 600.0,
                storedPaidCache = 0.0,
                storedBalanceCache = 0.0
            )
        )

        val groups = OtaReceivableGrouping.build(rows)

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().bookingCount)
        assertEquals(1_000.0, groups.single().totalOutstanding, 0.001)
        assertEquals("agoda_id", groups.single().sourceRemoteId)
    }

    @Test
    fun otaGrouping_doesNotInventStableSourceIdWhenRowsDisagree() {
        val rows = listOf(
            PropertyBalanceBookingFacts(
                bookingRemoteId = "b1",
                guestName = "Guest One",
                sourceType = BookingSourceType.OTA,
                sourceRemoteId = "source_1",
                sourceName = "Agoda",
                receivable = 500.0,
                received = 0.0,
                outstanding = 500.0,
                storedPaidCache = 0.0,
                storedBalanceCache = 0.0
            ),
            PropertyBalanceBookingFacts(
                bookingRemoteId = "b2",
                guestName = "Guest Two",
                sourceType = BookingSourceType.OTA,
                sourceRemoteId = "source_2",
                sourceName = "Agoda",
                receivable = 400.0,
                received = 0.0,
                outstanding = 400.0,
                storedPaidCache = 0.0,
                storedBalanceCache = 0.0
            )
        )

        val group = OtaReceivableGrouping.build(rows).single()

        assertNull(group.sourceRemoteId)
        assertEquals(900.0, group.totalOutstanding, 0.001)
    }
}
