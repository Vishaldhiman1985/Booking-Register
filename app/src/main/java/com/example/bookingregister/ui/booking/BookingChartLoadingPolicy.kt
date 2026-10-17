package com.example.bookingregister.ui.booking

/**
 * The dashboard is useful only when every local snapshot that affects its first
 * frame has arrived. Cloud connectivity is deliberately not part of readiness:
 * an offline user should still see the complete locally stored register.
 */
object BookingChartLoadingPolicy {
    fun isReady(
        roomsLoaded: Boolean,
        bookingsLoaded: Boolean,
        roomPlanLoaded: Boolean,
        balanceLoaded: Boolean
    ): Boolean = roomsLoaded && bookingsLoaded && roomPlanLoaded && balanceLoaded

    /**
     * Wait for a quiet period after the last first-frame change, but never keep an
     * offline user behind the preparing screen forever. Times use the same
     * monotonic clock and are supplied by the caller to keep this deterministic.
     */
    fun initialRenderDelayMillis(
        nowMillis: Long,
        readyAtMillis: Long,
        lastMutationAtMillis: Long,
        quietPeriodMillis: Long,
        maximumWaitMillis: Long
    ): Long {
        val quietRemaining =
            (lastMutationAtMillis + quietPeriodMillis - nowMillis).coerceAtLeast(0L)
        val maximumWaitRemaining =
            (readyAtMillis + maximumWaitMillis - nowMillis).coerceAtLeast(0L)
        return minOf(quietRemaining, maximumWaitRemaining)
    }
}
