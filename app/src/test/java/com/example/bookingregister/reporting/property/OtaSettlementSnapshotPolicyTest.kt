package com.example.bookingregister.reporting.property

import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.data.sync.OtaSettlementServerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OtaSettlementSnapshotPolicyTest {
    private val booking = BookingEntity(
        remoteId = "booking-1",
        bookingUuid = "booking-1",
        hotelRemoteId = "hotel-1",
        propertyRemoteId = "property-1",
        guestName = "Guest",
        sourceType = BookingSourceType.OTA,
        sourceRemoteId = "ota-1",
        sourceName = "OTA",
        checkInMillis = 0,
        checkOutMillis = 86_400_000,
        roomRemoteIds = listOf("room-1"),
        expectedPayout = 5_000.0,
        bookingStatus = BookingStatus.RESERVED
    )
    private val scope = PropertyReportScope(
        hotelRemoteId = "hotel-1",
        propertyRemoteId = "property-1",
        includeAllProperties = false,
        startMillis = 0,
        endMillis = 100_000_000
    )

    @Test
    fun cloudPaymentRemovesStaleLocalOtaReceivableWithoutWritingLocalData() {
        val otherBooking = booking.copy(remoteId = "booking-2", bookingUuid = "booking-2")
        val otherPayment = payment("other-paid", 1_000.0).copy(bookingRemoteId = otherBooking.remoteId)
        val local = rawData().copy(
            bookings = listOf(booking, otherBooking),
            payments = listOf(otherPayment)
        )
        assertEquals(5_000.0, outstanding(local, booking.remoteId), 0.001)

        val server = OtaSettlementServerSnapshot(
            bookings = listOf(booking),
            payments = listOf(payment("cloud-paid", 5_000.0))
        )
        val refreshed = OtaSettlementSnapshotPolicy.overlay(local, listOf(booking.remoteId), server)

        assertEquals(0.0, outstanding(refreshed, booking.remoteId), 0.001)
        assertEquals(5_000.0, outstanding(local, booking.remoteId), 0.001)
        assertEquals(2, refreshed.payments.size)
        assertEquals(otherBooking, refreshed.bookings.first { it.remoteId == otherBooking.remoteId })
        assertEquals(otherPayment, refreshed.payments.first { it.remoteId == otherPayment.remoteId })
    }

    @Test
    fun unsyncedLocalPaymentBlocksCloudOnlySettlementPreview() {
        val local = rawData().copy(
            payments = listOf(payment("pending", 1_000.0).copy(syncState = SyncState.PENDING))
        )

        assertThrows(IllegalArgumentException::class.java) {
            OtaSettlementSnapshotPolicy.overlay(
                local,
                listOf(booking.remoteId),
                OtaSettlementServerSnapshot(listOf(booking), emptyList())
            )
        }
    }

    @Test
    fun missingCloudBookingCannotBePresentedForSettlement() {
        assertThrows(IllegalArgumentException::class.java) {
            OtaSettlementSnapshotPolicy.overlay(
                rawData(),
                listOf(booking.remoteId),
                OtaSettlementServerSnapshot(emptyList(), emptyList())
            )
        }
    }

    @Test
    fun legacyCloudBookingWithoutFinancialFieldsKeepsVerifiedDevicePayoutInPreview() {
        val cloud = booking.copy(expectedPayout = 0.0, receivable = 0.0, rate = 0.0)
        val refreshed = OtaSettlementSnapshotPolicy.overlay(
            rawData(),
            listOf(booking.remoteId),
            OtaSettlementServerSnapshot(
                bookings = listOf(cloud),
                payments = emptyList(),
                missingFinancialBookingIds = setOf(booking.remoteId)
            )
        )

        assertEquals(5_000.0, outstanding(refreshed, booking.remoteId), 0.001)
    }

    @Test
    fun changedCloudRoomChargeCannotBorrowDevicePayout() {
        val cloud = booking.copy(expectedPayout = 0.0, receivable = 0.0, rate = 0.0,
            grossCharges = booking.grossCharges + 100.0)

        assertThrows(IllegalArgumentException::class.java) {
            OtaSettlementSnapshotPolicy.overlay(
                rawData(),
                listOf(booking.remoteId),
                OtaSettlementServerSnapshot(
                    bookings = listOf(cloud),
                    payments = emptyList(),
                    missingFinancialBookingIds = setOf(booking.remoteId)
                )
            )
        }
    }

    private fun rawData() = PropertyReportRawData(
        properties = emptyList(),
        rooms = emptyList(),
        bookings = listOf(booking),
        financialLines = emptyList(),
        payments = emptyList()
    )

    private fun payment(id: String, amount: Double) = BookingPaymentEntity(
        remoteId = id,
        hotelRemoteId = "hotel-1",
        bookingRemoteId = booking.remoteId,
        amount = amount,
        allocatedStayAmount = amount,
        paymentMillis = 1
    )

    private fun outstanding(raw: PropertyReportRawData, bookingId: String): Double =
        PropertyBalanceReportBuilder().build(PropertyReportBuilder().scope(raw, scope))
            .bookings.single { it.bookingRemoteId == bookingId }.outstanding
}
