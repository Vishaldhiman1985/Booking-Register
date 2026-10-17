package com.example.bookingregister.data.sync

import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingRoomNightAssignmentEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingRoomPlanOrphanReconciliationPolicyTest {
    @Test
    fun `matching cloud assignment clears historical pending status without an outbox`() {
        val cloud = assignment(revision = 3)
        val local = cloud.copy(
            syncState = SyncState.PENDING,
            lastSyncedAt = 100
        )

        assertTrue(
            BookingRoomPlanOrphanReconciliationPolicy.canAcceptMatchingCloudAssignment(
                local = local,
                cloud = cloud,
                hasPendingIntent = false
            )
        )
    }

    @Test
    fun `durable booking command prevents cloud echo from clearing pending status`() {
        val cloud = assignment(revision = 3)
        val local = cloud.copy(syncState = SyncState.PENDING)

        assertFalse(
            BookingRoomPlanOrphanReconciliationPolicy.canAcceptMatchingCloudAssignment(
                local = local,
                cloud = cloud,
                hasPendingIntent = true
            )
        )
    }

    @Test
    fun `different room remains preserved for reconciliation`() {
        val cloud = assignment(roomRemoteId = "room-cloud", revision = 3)
        val local = cloud.copy(
            roomRemoteId = "room-local",
            syncState = SyncState.PENDING
        )

        assertFalse(
            BookingRoomPlanOrphanReconciliationPolicy.canAcceptMatchingCloudAssignment(
                local = local,
                cloud = cloud,
                hasPendingIntent = false
            )
        )
    }

    @Test
    fun `older cloud revision never clears newer local assignment`() {
        val local = assignment(revision = 4).copy(syncState = SyncState.PENDING)
        val cloud = assignment(revision = 3)

        assertFalse(
            BookingRoomPlanOrphanReconciliationPolicy.canAcceptMatchingCloudAssignment(
                local = local,
                cloud = cloud,
                hasPendingIntent = false
            )
        )
    }

    @Test
    fun `matching cloud tombstone safely clears pending tombstone`() {
        val cloud = assignment(revision = 2, isDeleted = true)
        val local = cloud.copy(syncState = SyncState.PENDING)

        assertTrue(
            BookingRoomPlanOrphanReconciliationPolicy.canAcceptMatchingCloudAssignment(
                local = local,
                cloud = cloud,
                hasPendingIntent = false
            )
        )
    }

    @Test
    fun `unrelated failed assignment is not silently cleared`() {
        val cloud = assignment(revision = 3)
        val local = cloud.copy(
            syncState = SyncState.FAILED,
            lastSyncError = "[FAILED_PRECONDITION] Room conflict"
        )

        assertFalse(
            BookingRoomPlanOrphanReconciliationPolicy.canAcceptMatchingCloudAssignment(
                local = local,
                cloud = cloud,
                hasPendingIntent = false
            )
        )
    }

    private fun assignment(
        roomRemoteId: String = "room-1",
        revision: Long = 1,
        isDeleted: Boolean = false
    ) = BookingRoomNightAssignmentEntity(
        remoteId = "room-plan-1",
        hotelRemoteId = "hotel-1",
        bookingRemoteId = "booking-1",
        roomRemoteId = roomRemoteId,
        propertyRemoteId = "property-1",
        businessDateMillis = 1_000,
        updatedAt = 2_000,
        isDeleted = isDeleted,
        revision = revision,
        baseRevision = revision
    )
}
