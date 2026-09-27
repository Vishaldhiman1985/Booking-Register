package com.example.bookingregister.data.repository

import com.example.bookingregister.booking.domain.BookingRoomNightAssignment
import com.example.bookingregister.booking.domain.BookingRoomPlanResolver
import com.example.bookingregister.booking.domain.EffectiveBookingRoomPlan
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingRoomNightAssignmentEntity

object BookingRoomPlanReadResolver {

    fun resolve(
        booking: BookingEntity,
        assignmentEntities: List<BookingRoomNightAssignmentEntity>
    ): EffectiveBookingRoomPlan {
        val explicitAssignments = assignmentEntities
            .asSequence()
            .filter { !it.isDeleted }
            .filter { it.hotelRemoteId == booking.hotelRemoteId }
            .filter { it.bookingRemoteId == booking.remoteId }
            .map { entity ->
                BookingRoomNightAssignment(
                    businessDateMillis = entity.businessDateMillis,
                    roomRemoteId = entity.roomRemoteId,
                    propertyRemoteId = entity.propertyRemoteId
                )
            }
            .toList()

        return BookingRoomPlanResolver.resolve(
            checkInMillis = booking.checkInMillis,
            checkOutMillis = booking.checkOutMillis,
            legacyRoomRemoteIds = booking.roomRemoteIds,
            legacyPropertyRemoteId = booking.propertyRemoteId,
            explicitAssignments = explicitAssignments
        )
    }
}