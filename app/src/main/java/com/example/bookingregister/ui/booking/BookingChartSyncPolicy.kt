package com.example.bookingregister.ui.booking

internal object BookingChartSyncPolicy {
    fun hasRetryableWork(
        hasActionableRealtimeError: Boolean,
        coreSyncStates: Iterable<String>,
        financialLineSyncStates: Iterable<String>,
        roomPlanAssignmentSyncStates: Iterable<String>
    ): Boolean =
        hasActionableRealtimeError ||
            sequenceOf(
                coreSyncStates,
                financialLineSyncStates,
                roomPlanAssignmentSyncStates
            ).flatten().any { state ->
                state == "PENDING" || state == "FAILED"
            }
}
