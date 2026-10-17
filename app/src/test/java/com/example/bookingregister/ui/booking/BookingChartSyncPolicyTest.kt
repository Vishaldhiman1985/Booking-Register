package com.example.bookingregister.ui.booking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingChartSyncPolicyTest {
    @Test
    fun financialLineOnlyPendingWorkTriggersAutomaticRecovery() {
        assertTrue(
            BookingChartSyncPolicy.hasRetryableWork(
                hasActionableRealtimeError = false,
                coreSyncStates = listOf("SYNCED"),
                financialLineSyncStates = listOf("PENDING"),
                roomPlanAssignmentSyncStates = emptyList()
            )
        )
    }

    @Test
    fun roomPlanOnlyPendingWorkTriggersAutomaticRecovery() {
        assertTrue(
            BookingChartSyncPolicy.hasRetryableWork(
                hasActionableRealtimeError = false,
                coreSyncStates = listOf("SYNCED"),
                financialLineSyncStates = emptyList(),
                roomPlanAssignmentSyncStates = listOf("PENDING")
            )
        )
    }

    @Test
    fun completelySyncedStateDoesNotTriggerAutomaticRecovery() {
        assertFalse(
            BookingChartSyncPolicy.hasRetryableWork(
                hasActionableRealtimeError = false,
                coreSyncStates = listOf("SYNCED"),
                financialLineSyncStates = listOf("SYNCED"),
                roomPlanAssignmentSyncStates = listOf("SYNCED")
            )
        )
    }
}
