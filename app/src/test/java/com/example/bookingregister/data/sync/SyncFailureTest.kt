package com.example.bookingregister.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test
    fun `missing cloud booking has a distinct recoverable code`() {
        val error = StructuredSyncException(
            SyncFailureCode.NOT_FOUND,
            "The booking no longer exists."
        )

        assertEquals(
            "[NOT_FOUND] The booking no longer exists.",
            syncFailureText(error)
        )
    }
}
