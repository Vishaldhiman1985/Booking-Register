package com.example.bookingregister.ui.booking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingChartLoadingPolicyTest {
    @Test
    fun `dashboard is revealed only after every first-frame snapshot arrives`() {
        assertFalse(ready(rooms = false))
        assertFalse(ready(bookings = false))
        assertFalse(ready(roomPlan = false))
        assertFalse(ready(balance = false))
        assertTrue(ready())
    }

    @Test
    fun `empty local datasets are ready once their queries have emitted`() {
        assertTrue(
            BookingChartLoadingPolicy.isReady(
                roomsLoaded = true,
                bookingsLoaded = true,
                roomPlanLoaded = true,
                balanceLoaded = true
            )
        )
    }

    @Test
    fun `initial chart waits until its data has been quiet`() {
        assertEquals(
            650L,
            BookingChartLoadingPolicy.initialRenderDelayMillis(
                nowMillis = 1_000L,
                readyAtMillis = 500L,
                lastMutationAtMillis = 850L,
                quietPeriodMillis = 800L,
                maximumWaitMillis = 8_000L
            )
        )
    }

    @Test
    fun `initial chart is ready immediately after the quiet period`() {
        assertEquals(
            0L,
            BookingChartLoadingPolicy.initialRenderDelayMillis(
                nowMillis = 2_000L,
                readyAtMillis = 500L,
                lastMutationAtMillis = 1_100L,
                quietPeriodMillis = 800L,
                maximumWaitMillis = 8_000L
            )
        )
    }

    @Test
    fun `maximum wait prevents a continuous sync stream from blocking the chart`() {
        assertEquals(
            0L,
            BookingChartLoadingPolicy.initialRenderDelayMillis(
                nowMillis = 8_500L,
                readyAtMillis = 500L,
                lastMutationAtMillis = 8_450L,
                quietPeriodMillis = 800L,
                maximumWaitMillis = 8_000L
            )
        )
    }

    private fun ready(
        rooms: Boolean = true,
        bookings: Boolean = true,
        roomPlan: Boolean = true,
        balance: Boolean = true
    ): Boolean = BookingChartLoadingPolicy.isReady(
        roomsLoaded = rooms,
        bookingsLoaded = bookings,
        roomPlanLoaded = roomPlan,
        balanceLoaded = balance
    )
}
