package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.room.domain.RoomLifecycleStatus
import kotlin.math.round

object RoomConflictResolutionPolicy {
    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun availableRooms(
        hotelRemoteId: String,
        propertyRemoteId: String?,
        bookingRemoteId: String,
        checkInMillis: Long,
        checkOutMillis: Long,
        serverRooms: List<RoomEntity>,
        serverBookings: List<BookingEntity>
    ): List<RoomEntity> {
        if (checkOutMillis <= checkInMillis) return emptyList()

        val propertyKey = propertyRemoteId.cleanPropertyId()
        val occupiedRoomIds = serverBookings
            .asSequence()
            .filter { booking ->
                !booking.isDeleted &&
                    booking.remoteId != bookingRemoteId &&
                    booking.bookingStatus != BookingStatus.CANCELLED &&
                    booking.checkInMillis < checkOutMillis &&
                    booking.checkOutMillis > checkInMillis
            }
            .flatMap { it.roomRemoteIds.asSequence() }
            .toSet()

        return serverRooms
            .filter { room ->
                room.hotelRemoteId == hotelRemoteId &&
                    !room.isDeleted &&
                    RoomLifecycleStatus.normalize(room.lifecycleStatus) == RoomLifecycleStatus.ACTIVE &&
                    room.propertyRemoteId.cleanPropertyId() == propertyKey &&
                    room.remoteId !in occupiedRoomIds
            }
            .sortedWith(
                compareBy<RoomEntity> { it.categorySortOrder }
                    .thenBy { it.categoryName }
                    .thenBy { it.sortOrder }
                    .thenBy { it.roomName }
            )
    }

    fun preservesRoomCount(current: BookingEntity, requestedRoomRemoteIds: List<String>): Boolean {
        val cleanRequested = requestedRoomRemoteIds.map { it.trim() }.filter { it.isNotBlank() }
        return cleanRequested.distinct().size == cleanRequested.size &&
            cleanRequested.size == current.roomRemoteIds.distinct().size
    }

    fun canDiscardRejectedCreateOperationsForManualRecovery(
        booking: BookingEntity,
        serverBookingExists: Boolean,
        hasRejectedCreateAudit: Boolean,
        hasServerBusinessHistory: Boolean,
        hasPendingOperations: Boolean,
        allPendingOperationsAreRejectedRoomConflicts: Boolean
    ): Boolean =
        !serverBookingExists &&
            hasRejectedCreateAudit &&
            !hasServerBusinessHistory &&
            booking.revision == 0L &&
            booking.baseRevision == 0L &&
            booking.lastSyncedAt == null &&
            hasPendingOperations &&
            allPendingOperationsAreRejectedRoomConflicts

    fun financialLinesCanBeRebuiltWithoutChangingMoney(
        booking: BookingEntity,
        lines: List<BookingFinancialLineEntity>
    ): Boolean {
        val activeLines = lines.filter { !it.isDeleted }

        if (BookingPricingStatus.isPending(booking.pricingStatus)) {
            return activeLines.isEmpty()
        }

        val roomIds = booking.roomRemoteIds.distinct()
        val duration = booking.checkOutMillis - booking.checkInMillis
        if (roomIds.isEmpty() || duration <= 0L || duration % DAY_MILLIS != 0L) return false

        val nights = (duration / DAY_MILLIS).toInt()
        val expectedCount = roomIds.size * nights

        if (activeLines.isEmpty()) {
            return booking.grossCharges <= 0.001
        }
        if (activeLines.size != expectedCount) return false

        val expectedKeys = roomIds.flatMap { roomId ->
            (0 until nights).map { night ->
                roomId to (booking.checkInMillis + night * DAY_MILLIS)
            }
        }.toSet()
        val actualKeys = activeLines.map { it.roomRemoteId to it.businessDateMillis }.toSet()
        if (actualKeys != expectedKeys) return false

        val taxShapes = activeLines.map { line ->
            listOf(
                line.gstRatePercent,
                line.hsnSacCode,
                line.slabRemoteId,
                line.slabName,
                line.cgstRatePercent,
                line.sgstRatePercent,
                line.cessRatePercent,
                line.source
            )
        }.distinct()
        if (taxShapes.size != 1) return false

        val totalPaise = round(booking.grossCharges.coerceAtLeast(0.0) * 100.0).toLong()
        val actualPaise = activeLines
            .map { round(it.grossAmount.coerceAtLeast(0.0) * 100.0).toLong() }
            .sorted()

        if (actualPaise.sum() != totalPaise) return false

        val basePaise = totalPaise / expectedCount
        val remainder = (totalPaise % expectedCount).toInt()
        val expectedPaise = List(expectedCount) { index ->
            basePaise + if (index < remainder) 1L else 0L
        }.sorted()

        return actualPaise == expectedPaise
    }

    private fun String?.cleanPropertyId(): String? =
        this?.trim()?.takeIf { it.isNotBlank() }
}