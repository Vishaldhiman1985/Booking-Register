package com.example.bookingregister.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAcknowledgementPolicyTest {
    @Test
    fun `acknowledges the exact version that was sent`() {
        assertTrue(SyncAcknowledgementPolicy.isSameVersion(100, 2, 2, 100, 2, 2))
    }

    @Test
    fun `late response cannot acknowledge a newer local edit`() {
        assertFalse(SyncAcknowledgementPolicy.isSameVersion(100, 2, 2, 101, 2, 2))
    }

    @Test
    fun `revision changes are not mistaken for the sent version`() {
        assertFalse(SyncAcknowledgementPolicy.isSameVersion(100, 2, 2, 100, 3, 3))
    }
}
