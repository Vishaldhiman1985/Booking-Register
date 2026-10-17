package com.example.bookingregister.data.sync

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingSyncCoordinatorTest {

    @Test
    fun `foreground and worker processors cannot enter the same hotel concurrently`() =
        runBlocking {
            val hotelRemoteId = "hotel-coordinator-regression"
            val firstEntered = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val secondEntered = CompletableDeferred<Unit>()
            val activeProcessors = AtomicInteger(0)
            val maximumActiveProcessors = AtomicInteger(0)

            val foreground = launch {
                BookingSyncCoordinator.withHotelLock(hotelRemoteId) {
                    val active = activeProcessors.incrementAndGet()
                    maximumActiveProcessors.updateAndGet { current ->
                        maxOf(current, active)
                    }
                    firstEntered.complete(Unit)
                    releaseFirst.await()
                    activeProcessors.decrementAndGet()
                }
            }

            firstEntered.await()

            val worker = launch(start = CoroutineStart.UNDISPATCHED) {
                BookingSyncCoordinator.withHotelLock(hotelRemoteId) {
                    val active = activeProcessors.incrementAndGet()
                    maximumActiveProcessors.updateAndGet { current ->
                        maxOf(current, active)
                    }
                    secondEntered.complete(Unit)
                    activeProcessors.decrementAndGet()
                }
            }

            // UNDISPATCHED executes until the lock attempt suspends, so this check
            // does not depend on thread scheduling or an arbitrary delay.
            assertEquals(1, maximumActiveProcessors.get())
            assertEquals(false, secondEntered.isCompleted)

            releaseFirst.complete(Unit)
            withTimeout(5_000) {
                joinAll(foreground, worker)
            }

            assertEquals(true, secondEntered.isCompleted)
            assertEquals(1, maximumActiveProcessors.get())
            assertEquals(0, activeProcessors.get())
        }
}
