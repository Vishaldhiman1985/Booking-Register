package com.example.bookingregister.data.sync

import com.example.bookingregister.data.SyncState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDecisionTest {

    @Test
    fun shouldAcceptRemoteFoodEntity_rejectsRemoteWhenLocalChangeIsPending() {
        val accepted = shouldAcceptRemoteFoodEntity(
            localSyncState = SyncState.PENDING,
            localRevision = 1L,
            localUpdatedAt = 1_000L,
            remoteRevision = 99L,
            remoteUpdatedAt = 9_000L
        )

        assertFalse(accepted)
    }

    @Test
    fun shouldAcceptRemoteFoodEntity_acceptsHigherRemoteRevision() {
        val accepted = shouldAcceptRemoteFoodEntity(
            localSyncState = SyncState.SYNCED,
            localRevision = 1L,
            localUpdatedAt = 5_000L,
            remoteRevision = 2L,
            remoteUpdatedAt = 4_000L
        )

        assertTrue(accepted)
    }

    @Test
    fun shouldAcceptRemoteFoodEntity_rejectsOlderRemoteWhenRevisionAndUpdatedAtAreBehind() {
        val accepted = shouldAcceptRemoteFoodEntity(
            localSyncState = SyncState.SYNCED,
            localRevision = 3L,
            localUpdatedAt = 5_000L,
            remoteRevision = 2L,
            remoteUpdatedAt = 4_000L
        )

        assertFalse(accepted)
    }

    @Test
    fun failedHistoricalPaymentRewrite_recoversFromExistingCloudRevision() {
        val accepted = shouldAcceptRemotePaymentEntity(
            localSyncState = SyncState.FAILED,
            localRevision = 4L,
            localUpdatedAt = 9_000L,
            remoteRevision = 4L,
            remoteUpdatedAt = 5_000L
        )

        assertTrue(accepted)
    }

    @Test
    fun newPendingPayment_isNeverOverwrittenByListener() {
        val accepted = shouldAcceptRemotePaymentEntity(
            localSyncState = SyncState.PENDING,
            localRevision = 0L,
            localUpdatedAt = 9_000L,
            remoteRevision = 99L,
            remoteUpdatedAt = 10_000L
        )

        assertFalse(accepted)
    }

    @Test
    fun syncBoundary_returnsNullForFreshInstallSoCloudCanHydrateLocalDatabase() {
        assertNull(syncBoundary(localCount = 0, maxUpdatedAt = null))
    }

    @Test
    fun syncBoundary_usesLocalMaxUpdatedAtAfterInitialHydration() {
        val boundary = syncBoundary(localCount = 4, maxUpdatedAt = 5_000L)

        assertTrue(boundary == 5_000L)
    }
}
