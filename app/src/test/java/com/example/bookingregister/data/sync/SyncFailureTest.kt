package com.example.bookingregister.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFailureTest {
    @Test
    fun `stale revision uses structured code instead of English text`() {
        assertTrue(
            isStaleRevisionFailure(
                syncFailureText(
                    StructuredSyncException(SyncFailureCode.STALE_REVISION, "Translated conflict")
                )
            )
        )
    }

    @Test
    fun `ordinary text is not misclassified as a revision conflict`() {
        assertFalse(isStaleRevisionFailure("Cloud has another revision in a note"))
    }
}
