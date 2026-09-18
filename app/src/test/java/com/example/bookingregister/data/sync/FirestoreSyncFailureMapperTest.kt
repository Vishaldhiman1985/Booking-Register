package com.example.bookingregister.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class FirestoreSyncFailureMapperTest {

    @Test
    fun permissionDeniedIsTerminalPermissionFailure() {
        assertEquals(
            SyncFailureCode.PERMISSION_DENIED,
            firestoreSyncFailureCodeName("PERMISSION_DENIED")
        )
    }

    @Test
    fun authenticationFailureIsPreserved() {
        assertEquals(
            SyncFailureCode.UNAUTHENTICATED,
            firestoreSyncFailureCodeName("UNAUTHENTICATED")
        )
    }

    @Test
    fun temporaryFirestoreFailuresMapToUnavailable() {
        listOf(
            "CANCELLED",
            "DEADLINE_EXCEEDED",
            "RESOURCE_EXHAUSTED",
            "ABORTED",
            "UNAVAILABLE"
        ).forEach { code ->
            assertEquals(
                SyncFailureCode.UNAVAILABLE,
                firestoreSyncFailureCodeName(code)
            )
        }
    }

    @Test
    fun validationAndPreconditionFailuresRemainDistinct() {
        assertEquals(
            SyncFailureCode.INVALID_ARGUMENT,
            firestoreSyncFailureCodeName("INVALID_ARGUMENT")
        )
        assertEquals(
            SyncFailureCode.FAILED_PRECONDITION,
            firestoreSyncFailureCodeName("FAILED_PRECONDITION")
        )
        assertEquals(
            SyncFailureCode.NOT_FOUND,
            firestoreSyncFailureCodeName("NOT_FOUND")
        )
        assertEquals(
            SyncFailureCode.ALREADY_EXISTS,
            firestoreSyncFailureCodeName("ALREADY_EXISTS")
        )
    }

    @Test
    fun internalFirestoreFailuresMapToInternal() {
        assertEquals(
            SyncFailureCode.INTERNAL,
            firestoreSyncFailureCodeName("INTERNAL")
        )
        assertEquals(
            SyncFailureCode.INTERNAL,
            firestoreSyncFailureCodeName("DATA_LOSS")
        )
    }

    @Test
    fun unknownOrFutureFirestoreCodeFailsSafe() {
        assertEquals(
            SyncFailureCode.UNKNOWN,
            firestoreSyncFailureCodeName("SOME_FUTURE_FIREBASE_CODE")
        )
    }
}
