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

    @Test
    fun `earlier operation cannot mark aggregate synced while a later save is waiting`() {
        assertFalse(
            SyncAcknowledgementPolicy.canMarkAggregateSynced(
                sentVersionIsCurrent = true,
                hasLaterOperation = true
            )
        )
    }

    @Test
    fun `latest unchanged operation may mark aggregate synced`() {
        assertTrue(
            SyncAcknowledgementPolicy.canMarkAggregateSynced(
                sentVersionIsCurrent = true,
                hasLaterOperation = false
            )
        )
    }
}
