package com.example.bookingregister.data.repository

import com.example.bookingregister.booking.domain.BookingRoomNightAssignment
import com.example.bookingregister.booking.domain.BookingRoomPlanCommandAssignment
import com.example.bookingregister.booking.domain.BookingRoomPlanIdentity
import com.example.bookingregister.booking.domain.BookingRoomPlanPolicy
import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingRoomNightAssignmentEntity

data class BookingRoomPlanWritePreparation(
    val commandAssignments: List<BookingRoomPlanCommandAssignment>,
    val localRows: List<BookingRoomNightAssignmentEntity>
)

object BookingRoomPlanWriteComposer {

    fun prepare(
        booking: BookingEntity,
        targetAssignments: List<BookingRoomNightAssignment>,
        existingRows: List<BookingRoomNightAssignmentEntity>,
        now: Long
    ): BookingRoomPlanWritePreparation {
        val validation = BookingRoomPlanPolicy.validate(
            checkInMillis = booking.checkInMillis,
            checkOutMillis = booking.checkOutMillis,
            assignments = targetAssignments
        )
        require(validation.isValid) {
            validation.errors.joinToString()
        }

        val normalizedAssignments = targetAssignments
            .map {
                BookingRoomNightAssignment(
                    businessDateMillis = it.businessDateMillis,
                    roomRemoteId = it.roomRemoteId.trim(),
                    propertyRemoteId = it.propertyRemoteId
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                )
            }
            .distinctBy {
                Triple(
                    it.businessDateMillis,
                    it.roomRemoteId,
                    it.propertyRemoteId
                )
            }
            .sortedWith(
                compareBy<BookingRoomNightAssignment> { it.businessDateMillis }
                    .thenBy { it.roomRemoteId }
            )

        val existingByRemoteId = existingRows
            .filter {
                it.hotelRemoteId == booking.hotelRemoteId &&
                    it.bookingRemoteId == booking.remoteId
            }
            .associateBy { it.remoteId }

        val activeRows = normalizedAssignments.map { assignment ->
            val remoteId = BookingRoomPlanIdentity.documentId(
                bookingRemoteId = booking.remoteId,
                businessDateMillis = assignment.businessDateMillis,
                roomRemoteId = assignment.roomRemoteId
            )
            val existing = existingByRemoteId[remoteId]

            BookingRoomNightAssignmentEntity(
                localId = existing?.localId ?: 0,
                remoteId = remoteId,
                hotelRemoteId = booking.hotelRemoteId,
                bookingRemoteId = booking.remoteId,
                roomRemoteId = assignment.roomRemoteId,
                propertyRemoteId = assignment.propertyRemoteId,
                businessDateMillis = assignment.businessDateMillis,
                updatedAt = now,
                isDeleted = false,
                syncState = SyncState.PENDING,
                lastSyncError = null,
                lastSyncedAt = existing?.lastSyncedAt,
                revision = existing?.revision ?: 0,
                baseRevision = existing
                    ?.baseRevision
                    ?.takeIf { it > 0 }
                    ?: existing?.revision
                    ?: 0,
                updatedByUid = existing?.updatedByUid
            )
        }

        val desiredIds = activeRows.mapTo(mutableSetOf()) { it.remoteId }

        val tombstones = existingByRemoteId.values
            .filter { !it.isDeleted && it.remoteId !in desiredIds }
            .map { existing ->
                existing.copy(
                    updatedAt = now,
                    isDeleted = true,
                    syncState = SyncState.PENDING,
                    lastSyncError = null,
                    baseRevision = existing.baseRevision
                        .takeIf { it > 0 }
                        ?: existing.revision
                )
            }

        val commandAssignments = normalizedAssignments.map { assignment ->
            BookingRoomPlanCommandAssignment(
                businessDateMillis = assignment.businessDateMillis,
                roomRemoteId = assignment.roomRemoteId,
                propertyRemoteId = assignment.propertyRemoteId
            )
        }

        return BookingRoomPlanWritePreparation(
            commandAssignments = commandAssignments,
            localRows = (activeRows + tombstones)
                .sortedWith(
                    compareBy<BookingRoomNightAssignmentEntity> { it.businessDateMillis }
                        .thenBy { it.roomRemoteId }
                        .thenBy { it.isDeleted }
                )
        )
    }
}