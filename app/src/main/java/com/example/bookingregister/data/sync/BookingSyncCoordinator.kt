package com.example.bookingregister.data.sync

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes local booking-outbox processing for one hotel in this app process.
 *
 * WorkManager and foreground repositories use separate BookingRepository instances,
 * so repository-instance flags cannot coordinate them. The durable outbox and server
 * operation IDs remain the source of recovery after process death.
 */
object BookingSyncCoordinator {
    private val hotelLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withHotelLock(
        hotelRemoteId: String,
        block: suspend () -> T
    ): T {
        require(hotelRemoteId.isNotBlank()) { "Hotel access is missing." }
        val candidate = Mutex()
        val mutex = hotelLocks.putIfAbsent(hotelRemoteId, candidate) ?: candidate
        return mutex.withLock { block() }
    }
}
