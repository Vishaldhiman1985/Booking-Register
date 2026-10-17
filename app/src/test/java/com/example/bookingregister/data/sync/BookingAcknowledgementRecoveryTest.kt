package com.example.bookingregister.data.sync

import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingRoomNightAssignmentEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingAcknowledgementRecoveryTest {

    @Test
    fun `second acknowledgement is ignored after first one removed the operation`() {
        val afterFirstAcknowledgement = AggregateSnapshot(
            booking = booking(),
            payment = payment(),
            financialLine = financialLine(),
            roomNight = roomNight()
        )

        val operationDisposition =
            BookingAckDispositionPolicy.operationDisposition(
                operationWasRemoved = false
            )

        val afterDuplicateAcknowledgement = when (operationDisposition) {
            BookingAckDispositionPolicy.OperationDisposition.APPLY ->
                error("A duplicate acknowledgement must not be applied")

            BookingAckDispositionPolicy.OperationDisposition.IGNORE_ALREADY_ACKNOWLEDGED ->
                afterFirstAcknowledgement
        }

        assertEquals(afterFirstAcknowledgement, afterDuplicateAcknowledgement)
        assertEquals("Rohit", afterDuplicateAcknowledgement.booking.guestName)
        assertEquals(5_000.0, afterDuplicateAcknowledgement.booking.grossCharges, 0.0)
        assertEquals(4_500.0, afterDuplicateAcknowledgement.booking.balance, 0.0)
        assertEquals(500.0, afterDuplicateAcknowledgement.payment.amount, 0.0)
        assertEquals(5_000.0, afterDuplicateAcknowledgement.financialLine.grossAmount, 0.0)
        assertEquals("room-d3", afterDuplicateAcknowledgement.roomNight.roomRemoteId)
        assertEquals(SyncState.SYNCED, afterDuplicateAcknowledgement.booking.syncState)
        assertEquals(SyncState.SYNCED, afterDuplicateAcknowledgement.financialLine.syncState)
        assertEquals(SyncState.SYNCED, afterDuplicateAcknowledgement.roomNight.syncState)
    }

    @Test
    fun `unchanged booking with no later operation is synced`() {
        val result = BookingAckDispositionPolicy.decide(
            sentVersionIsCurrent = true,
            hasLaterOperation = false
        )

        assertEquals(BookingAckDispositionPolicy.Disposition.SYNCED, result)
    }

    @Test
    fun `later operation keeps booking pending`() {
        val result = BookingAckDispositionPolicy.decide(
            sentVersionIsCurrent = false,
            hasLaterOperation = true
        )

        assertEquals(
            BookingAckDispositionPolicy.Disposition.PENDING_WITH_OUTBOX,
            result
        )
    }

    @Test
    fun `changed booking without later operation requires reconciliation`() {
        val result = BookingAckDispositionPolicy.decide(
            sentVersionIsCurrent = false,
            hasLaterOperation = false
        )

        assertEquals(
            BookingAckDispositionPolicy.Disposition.RECONCILIATION_REQUIRED,
            result
        )
    }

    @Test
    fun `changed child row without later operation cannot be left pending`() {
        val result = BookingAckDispositionPolicy.decideChildRow(
            sentVersionIsCurrent = false,
            hasLaterCoveringOperation = false
        )

        assertEquals(
            BookingAckDispositionPolicy.Disposition.RECONCILIATION_REQUIRED,
            result
        )
    }

    @Test
    fun `changed child row with later operation remains pending`() {
        val result = BookingAckDispositionPolicy.decideChildRow(
            sentVersionIsCurrent = false,
            hasLaterCoveringOperation = true
        )

        assertEquals(
            BookingAckDispositionPolicy.Disposition.PENDING_WITH_OUTBOX,
            result
        )
    }

    @Test
    fun `unchanged child row is synced even when later booking-only operation exists`() {
        val result = BookingAckDispositionPolicy.decideChildRow(
            sentVersionIsCurrent = true,
            hasLaterCoveringOperation = true
        )

        assertEquals(BookingAckDispositionPolicy.Disposition.SYNCED, result)
    }

    @Test
    fun `booking-only later command does not cover financial lines`() {
        val changeSet = com.example.bookingregister.booking.domain.BookingChangeSet.create(
            previous = booking(),
            requested = booking().copy(notes = "Later note"),
            previousLines = listOf(financialLine()),
            requestedLines = listOf(financialLine())
        )

        assertEquals(
            false,
            BookingAckDispositionPolicy.operationCoversFinancialLines(changeSet.toJson())
        )
    }

    @Test
    fun `room move command covers financial lines`() {
        val changeSet = com.example.bookingregister.booking.domain.BookingChangeSet.create(
            previous = booking(),
            requested = booking().copy(roomRemoteIds = listOf("room-d1")),
            previousLines = listOf(financialLine()),
            requestedLines = listOf(
                financialLine().copy(roomRemoteId = "room-d1")
            )
        )

        assertEquals(
            true,
            BookingAckDispositionPolicy.operationCoversFinancialLines(changeSet.toJson())
        )
    }

    private data class AggregateSnapshot(
        val booking: BookingEntity,
        val payment: BookingPaymentEntity,
        val financialLine: BookingFinancialLineEntity,
        val roomNight: BookingRoomNightAssignmentEntity
    )

    private fun booking() = BookingEntity(
        remoteId = "booking-rohit",
        bookingUuid = "booking-rohit",
        hotelRemoteId = "hotel-1",
        guestName = "Rohit",
        checkInMillis = 1_760_572_800_000,
        checkOutMillis = 1_760_659_200_000,
        roomRemoteIds = listOf("room-d3"),
        grossCharges = 5_000.0,
        paid = 500.0,
        balance = 4_500.0,
        syncState = SyncState.SYNCED,
        revision = 1,
        baseRevision = 1
    )

    private fun payment() = BookingPaymentEntity(
        remoteId = "payment-rohit-500",
        hotelRemoteId = "hotel-1",
        bookingRemoteId = "booking-rohit",
        amount = 500.0,
        allocatedStayAmount = 500.0,
        syncState = SyncState.SYNCED,
        revision = 1,
        baseRevision = 1
    )

    private fun financialLine() = BookingFinancialLineEntity(
        remoteId = "line-rohit-d3",
        hotelRemoteId = "hotel-1",
        bookingRemoteId = "booking-rohit",
        roomRemoteId = "room-d3",
        businessDateMillis = 1_760_572_800_000,
        grossAmount = 5_000.0,
        syncState = SyncState.SYNCED,
        revision = 1,
        baseRevision = 1
    )

    private fun roomNight() = BookingRoomNightAssignmentEntity(
        remoteId = "room-night-rohit-d3",
        hotelRemoteId = "hotel-1",
        bookingRemoteId = "booking-rohit",
        roomRemoteId = "room-d3",
        businessDateMillis = 1_760_572_800_000,
        syncState = SyncState.SYNCED,
        revision = 1,
        baseRevision = 1
    )
}
