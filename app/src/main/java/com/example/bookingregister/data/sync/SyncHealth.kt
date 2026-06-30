package com.example.bookingregister.data.sync

import com.example.bookingregister.data.SyncState

object SyncArea {
    const val HOTEL = "HOTEL"
    const val ROOMS = "ROOMS"
    const val CATEGORIES = "CATEGORIES"
    const val BOOKINGS = "BOOKINGS"
}

data class SyncHealthItem(
    val area: String,
    val pendingCount: Int,
    val failedCount: Int,
    val lastError: String? = null
) {
    val isHealthy: Boolean = pendingCount == 0 && failedCount == 0 && lastError == null
}

data class SyncHealthSnapshot(
    val items: List<SyncHealthItem>,
    val realtimeError: String? = null
) {
    val isHealthy: Boolean = realtimeError == null && items.all { it.isHealthy }
    val pendingCount: Int = items.sumOf { it.pendingCount }
    val failedCount: Int = items.sumOf { it.failedCount }
}

fun Iterable<String>.countSyncState(state: String): Int = count { it == state }

fun syncHealthItem(area: String, states: Iterable<String>, lastError: String? = null): SyncHealthItem {
    return SyncHealthItem(
        area = area,
        pendingCount = states.countSyncState(SyncState.PENDING),
        failedCount = states.countSyncState(SyncState.FAILED),
        lastError = lastError
    )
}
