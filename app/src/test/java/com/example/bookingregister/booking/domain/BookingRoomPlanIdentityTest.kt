package com.example.bookingregister.booking.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BookingRoomPlanIdentityTest {

    @Test
    fun `ordinary booking and room ids match server document id format`() {
        assertEquals(
            "room_plan_booking-123_1700000000000_room-301",
            BookingRoomPlanIdentity.documentId(
                bookingRemoteId = "booking-123",
                businessDateMillis = 1_700_000_000_000L,
                roomRemoteId = "room-301"
            )
        )
    }

    @Test
    fun `javascript encodeURIComponent compatibility is preserved`() {
        assertEquals(
            "room_plan_booking%20A%2F1_1700000000000_room%20301%2FA",
            BookingRoomPlanIdentity.documentId(
                bookingRemoteId = "booking A/1",
                businessDateMillis = 1_700_000_000_000L,
                roomRemoteId = "room 301/A"
            )
        )
    }

    @Test
    fun `encodeURIComponent leaves javascript safe punctuation unchanged`() {
        assertEquals(
            "room_plan_a!~*'()_1700000000000_b!~*'()",
            BookingRoomPlanIdentity.documentId(
                bookingRemoteId = "a!~*'()",
                businessDateMillis = 1_700_000_000_000L,
                roomRemoteId = "b!~*'()"
            )
        )
    }

    @Test
    fun `blank identities and invalid dates are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BookingRoomPlanIdentity.documentId(
                bookingRemoteId = "",
                businessDateMillis = 1_700_000_000_000L,
                roomRemoteId = "301"
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            BookingRoomPlanIdentity.documentId(
                bookingRemoteId = "booking-1",
                businessDateMillis = 0L,
                roomRemoteId = "301"
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            BookingRoomPlanIdentity.documentId(
                bookingRemoteId = "booking-1",
                businessDateMillis = 1_700_000_000_000L,
                roomRemoteId = ""
            )
        }
    }
}