package com.example.bookingregister.legacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class Legacy103ReadOnlyAuditTest {
    private val booking = Legacy103SnapshotRow("booking_1", mapOf(
        "remoteId" to "booking_1",
        "hotelRemoteId" to "hotel_test",
        "bookingUuid" to "uuid_1",
        "checkInMillis" to 123456789L,
        "checkOutMillis" to 123546789L,
        "roomRemoteIds" to "room_1,room_2",
        "rate" to 2500.0,
        "receivable" to 5000.0,
        "paid" to 500.0,
        "isDeleted" to 0,
        "localId" to 4,
        "syncState" to "FAILED",
        "updatedAt" to 10L
    ))
    private val payment = Legacy103SnapshotRow("payment_1", mapOf(
        "remoteId" to "payment_1",
        "hotelRemoteId" to "hotel_test",
        "bookingRemoteId" to "booking_1",
        "amount" to 500.0,
        "paymentMillis" to 987654321L,
        "isDeleted" to 0
    ))

    private val cloudBooking = booking.fields.filterKeys { it !in setOf("localId", "syncState", "updatedAt") } +
        mapOf("roomRemoteIds" to listOf("room_1", "room_2"), "isDeleted" to false)
    private val cloudPayment = payment.fields + ("isDeleted" to false)

    private fun compare(
        bookingCloud: Map<String, Map<String, Any?>> = mapOf("booking_1" to cloudBooking),
        paymentCloud: Map<String, Map<String, Any?>> = mapOf("payment_1" to cloudPayment),
        bookings: List<Legacy103SnapshotRow> = listOf(booking),
        payments: List<Legacy103SnapshotRow> = listOf(payment)
    ) = Legacy103RecordComparator.compare(bookings, bookingCloud, payments, paymentCloud)

    @Test fun matchesOnlyWhenEveryOriginalIdAndBusinessValueIsPresent() {
        val r = compare()
        assertTrue(r.snapshotReconciled)
        assertEquals(1, r.matchedBookings)
        assertEquals(1, r.matchedPayments)
    }

    @Test fun missingBookingBlocks() {
        val r = compare(bookingCloud = emptyMap())
        assertFalse(r.snapshotReconciled)
        assertTrue(r.issues.contains("BOOKING_ABSENT:booking_1"))
    }

    @Test fun missingPaymentBlocks() {
        val r = compare(paymentCloud = emptyMap())
        assertFalse(r.snapshotReconciled)
        assertTrue(r.issues.contains("PAYMENT_ABSENT:payment_1"))
    }

    @Test fun changedBookingReceivableBlocks() {
        val r = compare(bookingCloud = mapOf("booking_1" to cloudBooking + ("receivable" to 4999.99)))
        assertFalse(r.snapshotReconciled)
        assertTrue(r.issues.contains("BOOKING_MISMATCH:booking_1"))
    }

    @Test fun changedPaymentAmountBlocks() {
        val r = compare(paymentCloud = mapOf("payment_1" to cloudPayment + ("amount" to 499.99)))
        assertFalse(r.snapshotReconciled)
        assertTrue(r.issues.contains("PAYMENT_MISMATCH:payment_1"))
    }

    @Test fun changedRoomMembershipBlocks() {
        val r = compare(bookingCloud = mapOf("booking_1" to cloudBooking + ("roomRemoteIds" to listOf("room_3"))))
        assertFalse(r.snapshotReconciled)
    }

    @Test fun unavailableCloudFieldCannotCountAsVerified() {
        val r = compare(bookingCloud = mapOf("booking_1" to cloudBooking - "paid"))
        assertFalse(r.snapshotReconciled)
    }

    @Test fun emptyOriginalBookingSnapshotBlocks() {
        val r = compare(bookings = emptyList(), payments = emptyList())
        assertFalse(r.snapshotReconciled)
        assertTrue("ORIGINAL_BOOKING_SNAPSHOT_EMPTY" in r.issues)
    }

    @Test fun paymentWithBrokenOriginalParentReferenceBlocks() {
        val r = compare(payments = listOf(payment.copy(fields = payment.fields +
            ("bookingRemoteId" to "nonexistent"))))
        assertFalse(r.snapshotReconciled)
        assertTrue(r.issues.any { it.startsWith("ORIGINAL_PAYMENT_PARENT_NOT_IN_SNAPSHOT") })
    }

    @Test fun duplicateOriginalIdsBlock() {
        val r = compare(bookings = listOf(booking, booking))
        assertFalse(r.snapshotReconciled)
        assertTrue("DUPLICATE_LOCAL_BOOKING_IDENTITIES" in r.issues)
    }

    @Test fun syncedMetadataCanDifferWithoutBlocking() {
        val r = compare(bookingCloud = mapOf("booking_1" to cloudBooking +
            mapOf("updatedAt" to 999L, "revision" to 14L, "syncState" to "SYNCED")))
        assertTrue(r.snapshotReconciled)
    }

    @Test fun extraNewServerBookingsDoNotHideOriginalMismatch() {
        val r = compare(bookingCloud = mapOf(
            "booking_1" to (cloudBooking + ("paid" to 1.0)),
            "new_booking" to cloudBooking
        ))
        assertFalse(r.snapshotReconciled)
    }
}
