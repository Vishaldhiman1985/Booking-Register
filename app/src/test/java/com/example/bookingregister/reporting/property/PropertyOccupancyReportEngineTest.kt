package com.example.bookingregister.reporting.property

import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.common.domain.DateRange
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.RoomEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class PropertyOccupancyReportEngineTest {
    private val engine = PropertyOccupancyReportEngine()

    /*
     * All fixture dates are constructed at LOCAL midnight.
     *
     * Production reporting ranges are business-day aligned. Using epoch 0 in a
     * test is unsafe because epoch 0 is not local midnight in many time zones
     * (for example, 05:30 in India). Calendar.add keeps the fixture aligned to
     * local calendar days and also avoids hard-coding 24-hour assumptions.
     */
    private val fixtureStartMillis: Long =
        Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.JANUARY, 15, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun selectedPropertyCannotSeeAnotherPropertyOrLegacyOccupancy() {
        val reportRange = DateRange(day(0), day(4))
        val raw = rawData(
            rooms = listOf(
                room("a1", "property_a"),
                room("a2", "property_a"),
                room("b1", "property_b"),
                room("legacy1", null)
            ),
            bookings = listOf(
                booking("booking_a", "property_a", "a1", day(1), day(3)),
                booking("booking_b", "property_b", "b1", day(0), day(4)),
                booking("booking_legacy", null, "legacy1", day(0), day(4))
            )
        )

        val report = engine.build(
            raw = raw,
            scope = scope("property_a", false, reportRange),
            annual = request(reportRange),
            monthly = request(reportRange),
            weekly = request(reportRange)
        )

        assertEquals(8, report.annual.facts.availableRoomNights)
        assertEquals(2, report.annual.facts.occupiedRoomNights)
        assertEquals(25, report.annual.facts.occupancyPercent)
        assertEquals(2, report.annual.facts.roomCount)
        assertEquals(25.0, report.annual.entries.single().second, 0.001)
    }

    @Test
    fun consolidatedOccupancyCombinesPropertiesButStillIgnoresCancelledBookings() {
        val reportRange = DateRange(day(0), day(2))
        val raw = rawData(
            rooms = listOf(
                room("a1", "property_a"),
                room("b1", "property_b")
            ),
            bookings = listOf(
                booking("booking_a", "property_a", "a1", day(0), day(2)),
                booking(
                    "booking_b_cancelled",
                    "property_b",
                    "b1",
                    day(0),
                    day(2),
                    BookingStatus.CANCELLED
                )
            )
        )

        val report = engine.build(
            raw = raw,
            scope = scope(null, true, reportRange),
            annual = request(reportRange),
            monthly = request(reportRange),
            weekly = request(reportRange)
        )

        assertEquals(4, report.annual.facts.availableRoomNights)
        assertEquals(2, report.annual.facts.occupiedRoomNights)
        assertEquals(50, report.annual.facts.occupancyPercent)
        assertEquals(2, report.annual.facts.roomCount)
        assertEquals(50.0, report.annual.entries.single().second, 0.001)
    }

    @Test
    fun legacyScopeContainsOnlyUnassignedRoomsAndBookings() {
        val reportRange = DateRange(day(0), day(2))
        val raw = rawData(
            rooms = listOf(
                room("assigned", "property_a"),
                room("legacy", null)
            ),
            bookings = listOf(
                booking("assigned_booking", "property_a", "assigned", day(0), day(2)),
                booking("legacy_booking", null, "legacy", day(0), day(1))
            )
        )

        val report = engine.build(
            raw = raw,
            scope = scope(null, false, reportRange),
            annual = request(reportRange),
            monthly = request(reportRange),
            weekly = request(reportRange)
        )

        assertEquals(2, report.annual.facts.availableRoomNights)
        assertEquals(1, report.annual.facts.occupiedRoomNights)
        assertEquals(50, report.annual.facts.occupancyPercent)
        assertEquals(1, report.annual.facts.roomCount)
        assertEquals(50.0, report.annual.entries.single().second, 0.001)
    }

    private fun day(offset: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = fixtureStartMillis
            add(Calendar.DAY_OF_MONTH, offset)
        }.timeInMillis
    }

    private fun request(range: DateRange): PropertyOccupancyPeriodRequest {
        return PropertyOccupancyPeriodRequest(
            range = range,
            buckets = listOf(
                PropertyOccupancyBucket(
                    label = "Period",
                    range = range
                )
            )
        )
    }

    private fun scope(
        propertyRemoteId: String?,
        includeAllProperties: Boolean,
        range: DateRange
    ): PropertyReportScope {
        return PropertyReportScope(
            hotelRemoteId = "hotel_1",
            propertyRemoteId = propertyRemoteId,
            includeAllProperties = includeAllProperties,
            startMillis = range.startMillis,
            endMillis = range.endMillis
        )
    }

    private fun rawData(
        rooms: List<RoomEntity>,
        bookings: List<BookingEntity>
    ): PropertyReportRawData {
        return PropertyReportRawData(
            properties = emptyList(),
            rooms = rooms,
            bookings = bookings,
            financialLines = emptyList(),
            payments = emptyList()
        )
    }

    private fun room(
        remoteId: String,
        propertyRemoteId: String?
    ): RoomEntity {
        return RoomEntity(
            remoteId = remoteId,
            hotelRemoteId = "hotel_1",
            roomName = remoteId,
            propertyRemoteId = propertyRemoteId
        )
    }

    private fun booking(
        remoteId: String,
        propertyRemoteId: String?,
        roomRemoteId: String,
        checkInMillis: Long,
        checkOutMillis: Long,
        status: String = BookingStatus.RESERVED
    ): BookingEntity {
        return BookingEntity(
            remoteId = remoteId,
            bookingUuid = "uuid_$remoteId",
            hotelRemoteId = "hotel_1",
            propertyRemoteId = propertyRemoteId,
            guestName = "Guest",
            checkInMillis = checkInMillis,
            checkOutMillis = checkOutMillis,
            roomRemoteIds = listOf(roomRemoteId),
            bookingStatus = status
        )
    }
}
