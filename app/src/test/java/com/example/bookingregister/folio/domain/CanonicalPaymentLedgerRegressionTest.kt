package com.example.bookingregister.folio.domain

import com.example.bookingregister.accounting.domain.PaymentCorrectionPolicy
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import com.example.bookingregister.data.entities.BookingSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Architecture-level regression tests for the canonical payment truth.
 *
 * These tests intentionally define the accounting contract before production code is changed:
 * 1. cash movement and bucket allocation are separate facts;
 * 2. unapplied money must not silently become room-paid money;
 * 3. a linked correction reverses the exact original transaction once;
 * 4. FolioSnapshot.totalPaid must reconcile to the immutable payment ledger.
 *
 * Some tests are expected to fail against the current FolioSnapshotBuilder. That is deliberate:
 * they are the safety net for the central accounting-core correction that will follow.
 */
class CanonicalPaymentLedgerRegressionTest {

    @Test
    fun normal_room_payment_reconciles_cash_and_room_allocation() {
        val booking = booking(charge = 10_500.0)
        val payments = listOf(
            payment(
                id = "good_10500",
                amount = 10_500.0,
                allocatedStay = 10_500.0
            )
        )

        val snapshot = snapshot(booking, payments)

        assertEquals(10_500.0, snapshot.room.paid, 0.01)
        assertEquals(0.0, snapshot.unappliedPaid, 0.01)
        assertEquals(10_500.0, snapshot.totalPaid, 0.01)
        assertEquals(0.0, snapshot.folioBalance, 0.01)
        assertEquals(0.0, snapshot.guestCredit, 0.01)
        assertLedgerReconciles(snapshot, payments)
    }

    @Test
    fun fully_unapplied_extra_payment_must_not_become_room_paid() {
        val booking = booking(charge = 10_500.0)
        val payments = listOf(
            payment("good_10500", 10_500.0, allocatedStay = 10_500.0),
            payment(
                "extra_3000",
                3_000.0,
                unapplied = 3_000.0,
                category = BookingPaymentCategory.AUTO
            )
        )

        val snapshot = snapshot(booking, payments)

        assertEquals(10_500.0, snapshot.room.paid, 0.01)
        assertEquals(3_000.0, snapshot.unappliedPaid, 0.01)
        assertEquals(13_500.0, snapshot.totalPaid, 0.01)
        assertEquals(3_000.0, snapshot.guestCredit, 0.01)
        assertLedgerReconciles(snapshot, payments)
    }

    @Test
    fun correction_policy_preserves_fully_unapplied_nature_of_original_payment() {
        val original = payment(
            id = "extra_3000",
            amount = 3_000.0,
            unapplied = 3_000.0,
            category = BookingPaymentCategory.AUTO
        )

        val reversal = PaymentCorrectionPolicy.reverseRemaining(
            original = original,
            payments = listOf(original),
            correctionAmount = 3_000.0
        )

        assertNotNull(reversal)
        assertEquals(0.0, reversal!!.stayAmount, 0.01)
        assertEquals(0.0, reversal.foodAmount, 0.01)
        assertEquals(0.0, reversal.serviceAmount, 0.01)
        assertEquals(0.0, reversal.damageAmount, 0.01)
        assertEquals(3_000.0, reversal.unappliedAmount, 0.01)
    }

    @Test
    fun linked_correction_of_fully_unapplied_payment_restores_exact_cash_truth() {
        val booking = booking(charge = 10_500.0)
        val extra = payment(
            "extra_3000",
            3_000.0,
            unapplied = 3_000.0,
            category = BookingPaymentCategory.AUTO
        )
        val correction = adjustment(
            id = "correction_3000",
            originalId = extra.remoteId,
            amount = 3_000.0,
            unapplied = 3_000.0,
            category = BookingPaymentCategory.AUTO
        )
        val payments = listOf(
            payment("good_10500", 10_500.0, allocatedStay = 10_500.0),
            extra,
            correction
        )

        val snapshot = snapshot(booking, payments)

        assertEquals(10_500.0, snapshot.room.paid, 0.01)
        assertEquals(0.0, snapshot.unappliedPaid, 0.01)
        assertEquals(10_500.0, snapshot.totalPaid, 0.01)
        assertEquals(0.0, snapshot.folioBalance, 0.01)
        assertEquals(0.0, snapshot.guestCredit, 0.01)
        assertLedgerReconciles(snapshot, payments)
    }

    @Test
    fun subham_four_linked_corrections_restore_10500_paid_zero_balance_zero_credit() {
        val booking = booking(
            id = "subham_booking",
            guestName = "Subham Singh",
            charge = 10_500.0
        )
        val wrongPayments = listOf(
            payment(
                "wrong_3000",
                3_000.0,
                unapplied = 3_000.0,
                category = BookingPaymentCategory.STAY,
                bookingId = booking.remoteId
            ),
            payment(
                "wrong_2500",
                2_500.0,
                unapplied = 2_500.0,
                category = BookingPaymentCategory.AUTO,
                bookingId = booking.remoteId
            ),
            payment(
                "wrong_2000",
                2_000.0,
                unapplied = 2_000.0,
                category = BookingPaymentCategory.AUTO,
                bookingId = booking.remoteId
            ),
            payment(
                "wrong_5500",
                5_500.0,
                unapplied = 5_500.0,
                category = BookingPaymentCategory.AUTO,
                bookingId = booking.remoteId
            )
        )
        val corrections = wrongPayments.map { wrong ->
            adjustment(
                id = "correction_${wrong.remoteId}",
                originalId = wrong.remoteId,
                amount = wrong.amount,
                unapplied = wrong.amount,
                category = wrong.paymentCategory,
                bookingId = booking.remoteId
            )
        }
        val payments = listOf(
            payment(
                "good_10500",
                10_500.0,
                allocatedStay = 10_500.0,
                bookingId = booking.remoteId
            )
        ) + wrongPayments + corrections

        val snapshot = snapshot(booking, payments)

        assertEquals(10_500.0, snapshot.room.paid, 0.01)
        assertEquals(0.0, snapshot.unappliedPaid, 0.01)
        assertEquals(10_500.0, snapshot.totalPaid, 0.01)
        assertEquals(0.0, snapshot.folioBalance, 0.01)
        assertEquals(0.0, snapshot.guestCredit, 0.01)
        assertLedgerReconciles(snapshot, payments)
    }

    @Test
    fun correction_of_partly_applied_payment_reverses_same_allocation_without_guessing() {
        val booking = booking(charge = 10_000.0)
        val mixed = payment(
            id = "mixed_3000",
            amount = 3_000.0,
            allocatedStay = 2_000.0,
            unapplied = 1_000.0
        )
        val correction = adjustment(
            id = "correction_mixed_3000",
            originalId = mixed.remoteId,
            amount = 3_000.0,
            allocatedStay = 2_000.0,
            unapplied = 1_000.0
        )
        val payments = listOf(
            payment("good_5000", 5_000.0, allocatedStay = 5_000.0),
            mixed,
            correction
        )

        val snapshot = snapshot(booking, payments)

        assertEquals(5_000.0, snapshot.room.paid, 0.01)
        assertEquals(0.0, snapshot.unappliedPaid, 0.01)
        assertEquals(5_000.0, snapshot.totalPaid, 0.01)
        assertEquals(5_000.0, snapshot.folioBalance, 0.01)
        assertLedgerReconciles(snapshot, payments)
    }

    @Test
    fun refund_reverses_the_same_stay_allocation_and_cash() {
        val booking = booking(charge = 10_500.0)
        val original = payment("good_10500", 10_500.0, allocatedStay = 10_500.0)
        val refund = BookingPaymentEntity(
            remoteId = "refund_2500",
            hotelRemoteId = booking.hotelRemoteId,
            bookingRemoteId = booking.remoteId,
            originalPaymentRemoteId = original.remoteId,
            paymentType = BookingPaymentType.REFUND,
            paymentCategory = BookingPaymentCategory.STAY,
            amount = 2_500.0,
            allocatedStayAmount = 2_500.0
        )
        val payments = listOf(original, refund)

        val snapshot = snapshot(booking, payments)

        assertEquals(8_000.0, snapshot.room.paid, 0.01)
        assertEquals(8_000.0, snapshot.totalPaid, 0.01)
        assertEquals(2_500.0, snapshot.folioBalance, 0.01)
        assertLedgerReconciles(snapshot, payments)
    }

    @Test
    fun august_overpayment_guard_stays_correct_when_unapplied_credit_is_corrected() {
        val booking = booking(charge = 4_500.0)
        val payments = listOf(
            payment(
                "advance_1500",
                1_500.0,
                allocatedStay = 1_500.0,
                type = BookingPaymentType.ADVANCE
            ),
            payment("payment_3500", 3_500.0, allocatedStay = 3_000.0, unapplied = 500.0),
            adjustment("legacy_credit_correction_500", amount = 500.0, unapplied = 500.0)
        )

        val snapshot = snapshot(booking, payments)

        assertEquals(4_500.0, snapshot.room.paid, 0.01)
        assertEquals(0.0, snapshot.unappliedPaid, 0.01)
        assertEquals(4_500.0, snapshot.totalPaid, 0.01)
        assertEquals(0.0, snapshot.folioBalance, 0.01)
        assertLedgerReconciles(snapshot, payments)
    }

    @Test
    fun legacy_unlinked_13000_adjustment_may_reduce_only_unapplied_credit_not_room_paid() {
        val booking = booking(
            id = "legacy_subham_shape",
            guestName = "Legacy correction case",
            charge = 10_500.0
        )
        val payments = listOf(
            payment(
                "good_10500",
                10_500.0,
                allocatedStay = 10_500.0,
                bookingId = booking.remoteId
            ),
            payment(
                "extra_3000",
                3_000.0,
                unapplied = 3_000.0,
                category = BookingPaymentCategory.STAY,
                bookingId = booking.remoteId
            ),
            payment(
                "extra_2500",
                2_500.0,
                unapplied = 2_500.0,
                category = BookingPaymentCategory.AUTO,
                bookingId = booking.remoteId
            ),
            payment(
                "extra_2000",
                2_000.0,
                unapplied = 2_000.0,
                category = BookingPaymentCategory.AUTO,
                bookingId = booking.remoteId
            ),
            payment(
                "extra_5500",
                5_500.0,
                unapplied = 5_500.0,
                category = BookingPaymentCategory.AUTO,
                bookingId = booking.remoteId
            ),
            adjustment(
                "legacy_adjustment_13000",
                amount = 13_000.0,
                unapplied = 13_000.0,
                bookingId = booking.remoteId
            )
        )

        val snapshot = snapshot(booking, payments)

        assertEquals(10_500.0, snapshot.room.paid, 0.01)
        assertEquals(0.0, snapshot.unappliedPaid, 0.01)
        assertEquals(10_500.0, snapshot.totalPaid, 0.01)
        assertEquals(0.0, snapshot.folioBalance, 0.01)
        assertEquals(0.0, snapshot.guestCredit, 0.01)
        assertLedgerReconciles(snapshot, payments)
    }

    private fun snapshot(
        booking: BookingEntity,
        payments: List<BookingPaymentEntity>
    ): FolioSnapshot = FolioSnapshotBuilder().build(
        booking = booking,
        payments = payments,
        bookingFinancialLines = roomLines(booking)
    )

    private fun assertLedgerReconciles(
        snapshot: FolioSnapshot,
        payments: List<BookingPaymentEntity>
    ) {
        val immutableLedgerNetCash = payments
            .filter { !it.isDeleted }
            .sumOf { payment ->
                when (payment.paymentType) {
                    BookingPaymentType.REFUND,
                    BookingPaymentType.ADJUSTMENT -> -payment.amount
                    else -> payment.amount
                }
            }
            .coerceAtLeast(0.0)

        assertEquals(
            "Canonical folio totalPaid must equal immutable payment-ledger net cash",
            immutableLedgerNetCash,
            snapshot.totalPaid,
            0.01
        )
    }

    private fun booking(
        id: String = "booking_1",
        guestName: String = "Test Guest",
        charge: Double
    ): BookingEntity = BookingEntity(
        remoteId = id,
        bookingUuid = id.uppercase(),
        hotelRemoteId = "hotel_1",
        guestName = guestName,
        sourceName = "Direct",
        sourceType = BookingSourceType.DIRECT,
        checkInMillis = 1_700_000_000_000L,
        checkOutMillis = 1_700_086_400_000L,
        roomRemoteIds = listOf("room_1"),
        grossCharges = charge,
        rate = charge,
        receivable = charge
    )

    private fun roomLines(booking: BookingEntity): List<BookingFinancialLineEntity> = listOf(
        BookingFinancialLineEntity(
            remoteId = "${booking.remoteId}_line",
            hotelRemoteId = booking.hotelRemoteId,
            bookingRemoteId = booking.remoteId,
            roomRemoteId = "room_1",
            businessDateMillis = booking.checkInMillis,
            grossAmount = booking.receivable,
            taxableAmount = booking.receivable,
            gstAmount = 0.0,
            hsnSacCode = "996311"
        )
    )

    private fun payment(
        id: String,
        amount: Double,
        allocatedStay: Double = 0.0,
        unapplied: Double = 0.0,
        type: String = BookingPaymentType.PAYMENT,
        category: String = BookingPaymentCategory.STAY,
        bookingId: String = "booking_1"
    ): BookingPaymentEntity = BookingPaymentEntity(
        remoteId = id,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = bookingId,
        paymentType = type,
        paymentCategory = category,
        amount = amount,
        allocatedStayAmount = allocatedStay,
        unappliedAmount = unapplied
    )

    private fun adjustment(
        id: String,
        originalId: String? = null,
        amount: Double,
        allocatedStay: Double = 0.0,
        unapplied: Double = 0.0,
        category: String = BookingPaymentCategory.STAY,
        bookingId: String = "booking_1"
    ): BookingPaymentEntity = BookingPaymentEntity(
        remoteId = id,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = bookingId,
        originalPaymentRemoteId = originalId,
        paymentType = BookingPaymentType.ADJUSTMENT,
        paymentCategory = category,
        amount = amount,
        allocatedStayAmount = allocatedStay,
        unappliedAmount = unapplied
    )
}
