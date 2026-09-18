package com.example.bookingregister.source.domain

import com.example.bookingregister.data.sync.SyncFailureCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSyncFailurePolicyTest {

    @Test
    fun transientInfrastructureFailuresRemainRetryable() {
        listOf(
            SyncFailureCode.UNAUTHENTICATED,
            SyncFailureCode.UNAVAILABLE,
            SyncFailureCode.INTERNAL,
            SyncFailureCode.UNKNOWN
        ).forEach { code ->
            assertEquals(
                SourceSyncFailureDisposition.RETRYABLE,
                SourceSyncFailurePolicy.disposition(code)
            )
        }
    }

    @Test
    fun permissionAndInvalidWritesAreTerminal() {
        listOf(
            SyncFailureCode.PERMISSION_DENIED,
            SyncFailureCode.STALE_REVISION,
            SyncFailureCode.ALREADY_EXISTS,
            SyncFailureCode.INVALID_ARGUMENT,
            SyncFailureCode.FAILED_PRECONDITION,
            SyncFailureCode.NOT_FOUND
        ).forEach { code ->
            assertEquals(
                SourceSyncFailureDisposition.TERMINAL,
                SourceSyncFailurePolicy.disposition(code)
            )
        }
    }

    @Test
    fun onlyNeverSyncedPermissionDeniedSourceMayBeDiscarded() {
        assertTrue(
            SourceSyncFailurePolicy.shouldDiscardNeverSyncedLocalSource(
                SyncFailureCode.PERMISSION_DENIED,
                revision = 0,
                lastSyncedAt = null
            )
        )
        assertFalse(
            SourceSyncFailurePolicy.shouldDiscardNeverSyncedLocalSource(
                SyncFailureCode.PERMISSION_DENIED,
                revision = 1,
                lastSyncedAt = 123L
            )
        )
        assertFalse(
            SourceSyncFailurePolicy.shouldDiscardNeverSyncedLocalSource(
                SyncFailureCode.UNAVAILABLE,
                revision = 0,
                lastSyncedAt = null
            )
        )
    }

    @Test
    fun readsNewStructuredFailureText() {
        assertEquals(
            SyncFailureCode.PERMISSION_DENIED,
            SourceSyncFailurePolicy.storedFailureCode(
                "[PERMISSION_DENIED] Missing or insufficient permissions."
            )
        )
    }

    @Test
    fun readsLegacyFirebaseFailureTextForMigration() {
        assertEquals(
            SyncFailureCode.PERMISSION_DENIED,
            SourceSyncFailurePolicy.storedFailureCode(
                "PERMISSION_DENIED: Missing or insufficient permissions."
            )
        )
    }

    @Test
    fun unknownHistoricalTextIsNotInvented() {
        assertNull(SourceSyncFailurePolicy.storedFailureCode("Something unexpected happened"))
    }
}
