package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import org.junit.Assert.assertEquals
import org.junit.Test

class FolioSnapshotPaymentAllocationTest {
    private val booking = BookingEntity(
        remoteId = "lalita",
        bookingUuid = "lalita-uuid",
        hotelRemoteId = "staging",
        guestName = "Lalita",
        checkInMillis = 0L,
        checkOutMillis = 86_400_000L,
        roomRemoteIds = listOf("D1"),
        receivable = 25_000.0,
        grossCharges = 25_000.0,
        paid = 45_000.0 // intentionally stale booking cache
    )

    private fun payment(
        id: String,
        amount: Double,
        type: String = BookingPaymentType.PAYMENT,
        stay: Double = 0.0,
        unapplied: Double = 0.0,
        original: String? = null
    ) = BookingPaymentEntity(
        remoteId = id,
        hotelRemoteId = "staging",
        bookingRemoteId = booking.remoteId,
        originalPaymentRemoteId = original,
        paymentType = type,
        paymentCategory = BookingPaymentCategory.STAY,
        amount = amount,
        allocatedStayAmount = stay,
        unappliedAmount = unapplied
    )

    @Test
    fun duplicateUnappliedPaymentAndCorrectionDoNotInflateRoomPaid() {
        val payments = listOf(
            payment("advance", 5_000.0, BookingPaymentType.ADVANCE, stay = 5_000.0),
            payment("balance", 20_000.0, stay = 20_000.0),
            payment("duplicate", 20_000.0, unapplied = 20_000.0),
            payment("reverse-duplicate", 20_000.0,
                type = BookingPaymentType.ADJUSTMENT,
                unapplied = 20_000.0,
                original = "duplicate")
        )
        val folio = FolioSnapshotBuilder().build(booking, payments = payments)
        assertEquals(25_000.0, folio.room.paid, 0.001)
        assertEquals(25_000.0, folio.totalPaid, 0.001)
        assertEquals(0.0, folio.unappliedPaid, 0.001)
    }

    @Test
    fun excessReceiptRemainsUnappliedUntilCorrected() {
        val payments = listOf(
            payment("advance", 5_000.0, BookingPaymentType.ADVANCE, stay = 5_000.0),
            payment("balance", 20_000.0, stay = 20_000.0),
            payment("duplicate", 20_000.0, unapplied = 20_000.0)
        )
        val folio = FolioSnapshotBuilder().build(booking, payments = payments)
        assertEquals(25_000.0, folio.room.paid, 0.001)
        assertEquals(45_000.0, folio.totalPaid, 0.001)
        assertEquals(20_000.0, folio.unappliedPaid, 0.001)
    }

    @Test
    fun legacyPaymentWithoutAllocationsStillFallsBackToStay() {
        val folio = FolioSnapshotBuilder().build(
            booking,
            payments = listOf(payment("legacy", 5_000.0))
        )
        assertEquals(5_000.0, folio.room.paid, 0.001)
        assertEquals(5_000.0, folio.totalPaid, 0.001)
    }

    @Test
    fun allocatedReceiptCorrectionReversesAllocatedStay() {
        val folio = FolioSnapshotBuilder().build(
            booking,
            payments = listOf(
                payment("receipt", 5_000.0, stay = 5_000.0),
                payment("correction", 5_000.0,
                    type = BookingPaymentType.ADJUSTMENT,
                    stay = 5_000.0,
                    original = "receipt")
            )
        )
        assertEquals(0.0, folio.room.paid, 0.001)
        assertEquals(0.0, folio.totalPaid, 0.001)
    }
}
