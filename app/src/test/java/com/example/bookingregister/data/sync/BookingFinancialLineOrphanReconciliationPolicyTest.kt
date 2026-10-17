package com.example.bookingregister.data.sync

import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingFinancialLineOrphanReconciliationPolicyTest {
    @Test
    fun `identical cloud line clears pending status when no financial command remains`() {
        val cloud = line(revision = 3)
        val local = cloud.copy(syncState = SyncState.PENDING, lastSyncedAt = 100)

        assertTrue(
            BookingFinancialLineOrphanReconciliationPolicy.canAcceptMatchingCloudLine(
                local,
                cloud,
                hasPendingFinancialIntent = false
            )
        )
    }

    @Test
    fun `booking-only command does not block matching financial reconciliation`() {
        val cloud = line(revision = 3)
        val local = cloud.copy(syncState = SyncState.PENDING)

        assertTrue(
            BookingFinancialLineOrphanReconciliationPolicy.canAcceptMatchingCloudLine(
                local,
                cloud,
                hasPendingFinancialIntent = false
            )
        )
    }

    @Test
    fun `financial command protects pending local line from cloud replacement`() {
        val cloud = line(revision = 3)
        val local = cloud.copy(syncState = SyncState.PENDING)

        assertFalse(
            BookingFinancialLineOrphanReconciliationPolicy.canAcceptMatchingCloudLine(
                local,
                cloud,
                hasPendingFinancialIntent = true
            )
        )
    }

    @Test
    fun `different financial amount is always preserved`() {
        val cloud = line(grossAmount = 2_500.0, revision = 3)
        val local = cloud.copy(grossAmount = 2_750.0, syncState = SyncState.PENDING)

        assertFalse(
            BookingFinancialLineOrphanReconciliationPolicy.canAcceptMatchingCloudLine(
                local,
                cloud,
                hasPendingFinancialIntent = false
            )
        )
    }

    @Test
    fun `different room or deletion state is always preserved`() {
        val cloud = line(roomRemoteId = "room-d1", revision = 3)

        assertFalse(
            BookingFinancialLineOrphanReconciliationPolicy.canAcceptMatchingCloudLine(
                cloud.copy(roomRemoteId = "room-d2", syncState = SyncState.PENDING),
                cloud,
                hasPendingFinancialIntent = false
            )
        )
        assertFalse(
            BookingFinancialLineOrphanReconciliationPolicy.canAcceptMatchingCloudLine(
                cloud.copy(isDeleted = true, syncState = SyncState.PENDING),
                cloud,
                hasPendingFinancialIntent = false
            )
        )
    }

    @Test
    fun `different GST snapshot is always preserved`() {
        val cloud = line(revision = 3)
        val local = cloud.copy(gstAmount = 125.0, syncState = SyncState.PENDING)

        assertFalse(
            BookingFinancialLineOrphanReconciliationPolicy.canAcceptMatchingCloudLine(
                local,
                cloud,
                hasPendingFinancialIntent = false
            )
        )
    }

    @Test
    fun `older cloud revision never clears newer local line`() {
        val local = line(revision = 4).copy(syncState = SyncState.PENDING)

        assertFalse(
            BookingFinancialLineOrphanReconciliationPolicy.canAcceptMatchingCloudLine(
                local,
                line(revision = 3),
                hasPendingFinancialIntent = false
            )
        )
    }

    @Test
    fun `unrelated financial failure is not silently cleared`() {
        val cloud = line(revision = 3)
        val local = cloud.copy(
            syncState = SyncState.FAILED,
            lastSyncError = "[FAILED_PRECONDITION] Financial line is locked"
        )

        assertFalse(
            BookingFinancialLineOrphanReconciliationPolicy.canAcceptMatchingCloudLine(
                local,
                cloud,
                hasPendingFinancialIntent = false
            )
        )
    }

    @Test
    fun `sync metadata does not make identical accounting content look changed`() {
        val existing = line(revision = 3).copy(
            syncState = SyncState.SYNCED,
            updatedAt = 100,
            lastSyncedAt = 100
        )
        val requested = existing.copy(
            syncState = SyncState.PENDING,
            updatedAt = 200,
            lastSyncedAt = null,
            revision = 4,
            baseRevision = 3
        )

        assertTrue(
            BookingFinancialLineOrphanReconciliationPolicy.hasSameAccountingContent(
                existing,
                requested
            )
        )
    }

    private fun line(
        roomRemoteId: String = "room-d1",
        grossAmount: Double = 2_500.0,
        revision: Long = 1
    ) = BookingFinancialLineEntity(
        remoteId = "line-1",
        hotelRemoteId = "hotel-1",
        bookingRemoteId = "booking-1",
        roomRemoteId = roomRemoteId,
        propertyRemoteId = "property-1",
        businessDateMillis = 1_000,
        grossAmount = grossAmount,
        taxableAmount = grossAmount,
        hsnSacCode = "996311",
        slabName = "No GST",
        revision = revision,
        baseRevision = revision
    )
}
