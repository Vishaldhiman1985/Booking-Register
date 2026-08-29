package com.example.bookingregister.reporting.property

import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.common.domain.DateRange
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.RoomEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PropertyOccupancyReportBuilderTest {
    private val day = 86_400_000L
    private val builder = PropertyOccupancyReportBuilder()

    @Test
    fun occupancyUsesOnlyPropertyScopedRoomsAndExistingRoomNightRules() {
        val range = DateRange(0, 4 * day)
        val scope = PropertyReportScope(
            hotelRemoteId = "hotel_1",
            propertyRemoteId = "property_a",
            includeAllProperties = false,
            startMillis = range.startMillis,
            endMillis = range.endMillis
        )
        val dataset = PropertyReportDataset(
            scope = scope,
            properties = emptyList(),
            rooms = listOf(
                room("room_a1", "property_a"),
                room("room_a2", "property_a")
            ),
            bookings = listOf(
                booking(
                    remoteId = "booking_a",
                    propertyRemoteId = "property_a",
                    roomRemoteId = "room_a1",
                    checkInMillis = day,
                    checkOutMillis = 3 * day
                )
            ),
            financialLines = emptyList(),
            payments = emptyList()
        )

        val facts = builder.build(dataset, range)

        assertEquals(8, facts.availableRoomNights)
        assertEquals(2, facts.occupiedRoomNights)
        assertEquals(25, facts.occupancyPercent)
        assertEquals(0.5, facts.averageOccupiedRoomsPerDay, 0.001)
        assertEquals(2, facts.roomCount)
        assertEquals(25, builder.chartBucketPercent(dataset, range))
    }

    private fun room(remoteId: String, propertyRemoteId: String) = RoomEntity(
        remoteId = remoteId,
        hotelRemoteId = "hotel_1",
        roomName = remoteId,
        propertyRemoteId = propertyRemoteId
    )

    private fun booking(
        remoteId: String,
        propertyRemoteId: String,
        roomRemoteId: String,
        checkInMillis: Long,
        checkOutMillis: Long
    ) = BookingEntity(
        remoteId = remoteId,
        bookingUuid = "uuid_$remoteId",
        hotelRemoteId = "hotel_1",
        propertyRemoteId = propertyRemoteId,
        guestName = "Guest",
        checkInMillis = checkInMillis,
        checkOutMillis = checkOutMillis,
        roomRemoteIds = listOf(roomRemoteId),
        bookingStatus = BookingStatus.RESERVED
    )
}
