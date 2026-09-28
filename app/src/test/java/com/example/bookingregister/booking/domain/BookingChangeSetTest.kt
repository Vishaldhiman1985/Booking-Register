package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingChangeSetTest {
    @Test
    fun `one save contains only changed price and added room`() {
        val previous = booking(3_000.0, listOf("H101"))
        val requested = booking(3_200.0, listOf("H101", "H102"))
        val changeSet = BookingChangeSet.create(previous, requested, emptyList(), emptyList())

        assertEquals(mapOf("grossCharges" to 3_200.0), changeSet.setFields)
        assertEquals(listOf("H102"), changeSet.addRoomRemoteIds)
        assertTrue(changeSet.removeRoomRemoteIds.isEmpty())
        assertTrue(changeSet.rebuildFinancialLines)
    }

    @Test
    fun `json round trip preserves durable offline command`() {
        val changeSet = BookingChangeSet.create(
            booking(3_000.0, listOf("H101")),
            booking(3_200.0, listOf("H101", "H102")),
            emptyList(), emptyList()
        )
        val decoded = BookingChangeSet.fromJson(changeSet.toJson())
        assertEquals(changeSet.bookingRemoteId, decoded.bookingRemoteId)
        assertEquals(changeSet.addRoomRemoteIds, decoded.addRoomRemoteIds)
        assertEquals(3_200.0, (decoded.setFields["grossCharges"] as Number).toDouble(), 0.001)
        assertFalse(decoded.create)
    }

    @Test
    fun `legacy command keeps exact backward compatible room plan absence`() {
        val changeSet = BookingChangeSet.create(
            booking(3_000.0, listOf("H101")),
            booking(3_200.0, listOf("H101", "H102")),
            emptyList(),
            emptyList()
        )

        val json = changeSet.toJson()
        val decoded = BookingChangeSet.fromJson(json)

        assertFalse(json.contains("roomPlanAssignments"))
        assertNull(decoded.roomPlanAssignments)
    }

    @Test
    fun `explicit room plan survives durable command json round trip`() {
        val changeSet = BookingChangeSet(
            bookingRemoteId = "booking-a",
            create = false,
            setFields = emptyMap(),
            addRoomRemoteIds = emptyList(),
            removeRoomRemoteIds = emptyList(),
            rebuildFinancialLines = false,
            financialLineTemplate = null,
            financialLineRemoteIdsByKey = emptyMap(),
            roomPlanAssignments = listOf(
                BookingRoomPlanCommandAssignment(
                    businessDateMillis = 1_000,
                    roomRemoteId = "H101",
                    propertyRemoteId = "property-a"
                ),
                BookingRoomPlanCommandAssignment(
                    businessDateMillis = 2_000,
                    roomRemoteId = "H201",
                    propertyRemoteId = "property-b"
                )
            )
        )

        val decoded = BookingChangeSet.fromJson(changeSet.toJson())

        assertTrue(changeSet.hasChanges)
        assertEquals(changeSet.roomPlanAssignments, decoded.roomPlanAssignments)
    }

    @Test
    fun `attaching a room plan does not change financial rebuild intent`() {
        val financialIds = mapOf("H101|1000" to "line-1")
        val financialTemplate = mapOf<String, Any?>(
            "gstRatePercent" to 5.0,
            "source" to "ROOM"
        )
        val base = BookingChangeSet(
            bookingRemoteId = "booking-a",
            create = false,
            setFields = mapOf("notes" to "Keep late arrival note"),
            addRoomRemoteIds = emptyList(),
            removeRoomRemoteIds = emptyList(),
            rebuildFinancialLines = false,
            financialLineTemplate = financialTemplate,
            financialLineRemoteIdsByKey = financialIds
        )
        val roomPlan = listOf(
            BookingRoomPlanCommandAssignment(1_000, "H101", "property-a"),
            BookingRoomPlanCommandAssignment(2_000, "H201", "property-a")
        )

        val result = base.withRoomPlanAssignments(roomPlan)

        assertEquals(roomPlan, result.roomPlanAssignments)
        assertFalse(result.rebuildFinancialLines)
        assertEquals(financialTemplate, result.financialLineTemplate)
        assertEquals(financialIds, result.financialLineRemoteIdsByKey)
        assertEquals(base.setFields, result.setFields)
        assertTrue(result.hasChanges)
    }

    @Test
    fun `room plan survives a later command that does not change the plan`() {
        val firstPlan = listOf(
            BookingRoomPlanCommandAssignment(1_000, "H101", "property-a"),
            BookingRoomPlanCommandAssignment(2_000, "H201", "property-a")
        )

        val first = BookingChangeSet(
            bookingRemoteId = "booking-a",
            create = false,
            setFields = emptyMap(),
            addRoomRemoteIds = emptyList(),
            removeRoomRemoteIds = emptyList(),
            rebuildFinancialLines = false,
            financialLineTemplate = null,
            financialLineRemoteIdsByKey = emptyMap(),
            roomPlanAssignments = firstPlan
        )
        val laterUnrelatedChange = BookingChangeSet(
            bookingRemoteId = "booking-a",
            create = false,
            setFields = mapOf("notes" to "Late arrival"),
            addRoomRemoteIds = emptyList(),
            removeRoomRemoteIds = emptyList(),
            rebuildFinancialLines = false,
            financialLineTemplate = null,
            financialLineRemoteIdsByKey = emptyMap()
        )

        val combined = first.followedBy(laterUnrelatedChange)

        assertEquals(firstPlan, combined.roomPlanAssignments)
        assertEquals("Late arrival", combined.setFields["notes"])
    }

    @Test
    fun `later explicit room plan replaces earlier explicit room plan`() {
        val firstPlan = listOf(
            BookingRoomPlanCommandAssignment(1_000, "H101", "property-a")
        )
        val laterPlan = listOf(
            BookingRoomPlanCommandAssignment(1_000, "H301", "property-a"),
            BookingRoomPlanCommandAssignment(2_000, "H301", "property-a")
        )

        val first = BookingChangeSet(
            bookingRemoteId = "booking-a",
            create = false,
            setFields = emptyMap(),
            addRoomRemoteIds = emptyList(),
            removeRoomRemoteIds = emptyList(),
            rebuildFinancialLines = false,
            financialLineTemplate = null,
            financialLineRemoteIdsByKey = emptyMap(),
            roomPlanAssignments = firstPlan
        )
        val later = BookingChangeSet(
            bookingRemoteId = "booking-a",
            create = false,
            setFields = emptyMap(),
            addRoomRemoteIds = emptyList(),
            removeRoomRemoteIds = emptyList(),
            rebuildFinancialLines = false,
            financialLineTemplate = null,
            financialLineRemoteIdsByKey = emptyMap(),
            roomPlanAssignments = laterPlan
        )

        val combined = first.followedBy(later)

        assertEquals(laterPlan, combined.roomPlanAssignments)
    }

    @Test
    fun `rejected room move followed by another room choice keeps original server baseline`() {
        val serverState = booking(3_000.0, listOf("H101"))
        val rejectedLocalState = booking(3_000.0, listOf("H102"))
        val finalLocalState = booking(3_000.0, listOf("H103"))

        val rejectedMove = BookingChangeSet.create(
            serverState,
            rejectedLocalState,
            emptyList(),
            emptyList()
        )
        val nextMove = BookingChangeSet.create(
            rejectedLocalState,
            finalLocalState,
            emptyList(),
            emptyList()
        )

        val combined = rejectedMove.followedBy(nextMove)

        assertEquals(listOf("H103"), combined.addRoomRemoteIds)
        assertEquals(listOf("H101"), combined.removeRoomRemoteIds)
        assertFalse(combined.addRoomRemoteIds.contains("H102"))
        assertFalse(combined.removeRoomRemoteIds.contains("H102"))
        assertFalse(combined.create)
    }
    @Test
    fun `cancellation settlement decision is synced without rebuilding room charges`() {
        val previous = booking(3_000.0, listOf("H101"))
        val requested = previous.copy(
            bookingStatus = BookingStatus.CANCELLED,
            cancelledAt = 4_000,
            cancellationReason = "Guest cancelled",
            cancellationSettlementStatus = CancellationSettlementStatus.DECIDED,
            cancellationSettlementOutcome = CancellationSettlementOutcome.PARTIAL_REFUND,
            cancellationApprovedRefundAmount = 100.0,
            cancellationFeeAmount = 100.0,
            cancellationRefundBaselineAmount = 0.0,
            cancellationDecisionAt = 4_000
        )

        val changeSet = BookingChangeSet.create(previous, requested, emptyList(), emptyList())

        assertEquals(BookingStatus.CANCELLED, changeSet.setFields["bookingStatus"])
        assertEquals(CancellationSettlementStatus.DECIDED, changeSet.setFields["cancellationSettlementStatus"])
        assertEquals(100.0, changeSet.setFields["cancellationApprovedRefundAmount"])
        assertFalse(changeSet.rebuildFinancialLines)
    }

    @Test
    fun `unchanged booking does not create an empty sync command`() {
        val existing = booking(3_000.0, listOf("H101"))

        val changeSet = BookingChangeSet.create(existing, existing, emptyList(), emptyList())

        assertFalse(changeSet.hasChanges)
    }

    private fun booking(total: Double, rooms: List<String>) = BookingEntity(
        remoteId = "booking-a", bookingUuid = "booking-a", hotelRemoteId = "hotel-a",
        guestName = "Guest", checkInMillis = 1_000, checkOutMillis = 2_000,
        roomRemoteIds = rooms, grossCharges = total, rate = total, receivable = total
    )
}
