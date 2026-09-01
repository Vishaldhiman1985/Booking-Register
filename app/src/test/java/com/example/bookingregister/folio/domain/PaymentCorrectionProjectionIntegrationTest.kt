package com.example.bookingregister.folio.domain

import com.example.bookingregister.accounting.domain.PaymentCorrectionPolicy
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentCorrectionProjectionIntegrationTest {

    @Test
    fun correction_policy_reverses_wrong_30000_across_room_food_and_guest_credit() {
        val wrong = payment(
            id = "wrong_30000",
            amount = 30_000.0,
            stay = 3_000.0,
            food = 4_000.0,
            unapplied = 23_000.0,
            category = BookingPaymentCategory.AUTO
        )

        val correctionAllocation = PaymentCorrectionPolicy.reverseRemaining(
            original = wrong,
            payments = listOf(wrong),
            correctionAmount = 30_000.0
        )

        assertNotNull(correctionAllocation)
        correctionAllocation!!

        assertMoney(3_000.0, correctionAllocation.stayAmount)
        assertMoney(4_000.0, correctionAllocation.foodAmount)
        assertMoney(0.0, correctionAllocation.serviceAmount)
        assertMoney(0.0, correctionAllocation.damageAmount)
        assertMoney(23_000.0, correctionAllocation.unappliedAmount)

        val correction = adjustmentFrom(
            id = "correct_wrong_30000",
            original = wrong,
            allocation = correctionAllocation
        )

        val correctPayment = payment(
            id = "correct_3000",
            amount = 3_000.0,
            stay = 3_000.0,
            category = BookingPaymentCategory.AUTO
        )

        val projection = PaymentLedgerProjector.project(
            listOf(wrong, correction, correctPayment)
        )

        assertMoney(3_000.0, projection.stayApplied)
        assertMoney(0.0, projection.foodApplied)
        assertMoney(0.0, projection.guestCredit)
        assertMoney(3_000.0, projection.netRecordedPayment)
        assertTrue(projection.reconciles)
    }

    @Test
    fun correction_policy_reverses_fully_unapplied_auto_payment_as_guest_credit() {
        val roomPayment = payment(
            id = "room_3000",
            amount = 3_000.0,
            stay = 3_000.0,
            category = BookingPaymentCategory.AUTO
        )

        val extraPayment = payment(
            id = "extra_3000",
            amount = 3_000.0,
            unapplied = 3_000.0,
            category = BookingPaymentCategory.AUTO
        )

        val correctionAllocation = PaymentCorrectionPolicy.reverseRemaining(
            original = extraPayment,
            payments = listOf(roomPayment, extraPayment),
            correctionAmount = 3_000.0
        )

        assertNotNull(correctionAllocation)
        correctionAllocation!!

        assertMoney(0.0, correctionAllocation.stayAmount)
        assertMoney(0.0, correctionAllocation.foodAmount)
        assertMoney(3_000.0, correctionAllocation.unappliedAmount)

        val correction = adjustmentFrom(
            id = "correct_extra_3000",
            original = extraPayment,
            allocation = correctionAllocation
        )

        val projection = PaymentLedgerProjector.project(
            listOf(roomPayment, extraPayment, correction)
        )

        assertMoney(3_000.0, projection.stayApplied)
        assertMoney(0.0, projection.guestCredit)
        assertMoney(3_000.0, projection.netRecordedPayment)
        assertTrue(projection.reconciles)
    }

    @Test
    fun four_subham_style_linked_corrections_restore_only_the_genuine_10500_payment() {
        val genuine = payment(
            id = "genuine_10500",
            amount = 10_500.0,
            stay = 10_500.0,
            category = BookingPaymentCategory.STAY
        )

        val wrongPayments = listOf(
            payment(
                id = "wrong_3000",
                amount = 3_000.0,
                unapplied = 3_000.0,
                category = BookingPaymentCategory.STAY
            ),
            payment(
                id = "wrong_2500",
                amount = 2_500.0,
                unapplied = 2_500.0,
                category = BookingPaymentCategory.AUTO
            ),
            payment(
                id = "wrong_2000",
                amount = 2_000.0,
                unapplied = 2_000.0,
                category = BookingPaymentCategory.AUTO
            ),
            payment(
                id = "wrong_5500",
                amount = 5_500.0,
                unapplied = 5_500.0,
                category = BookingPaymentCategory.AUTO
            )
        )

        val runningLedger = mutableListOf<BookingPaymentEntity>()
        runningLedger += genuine
        runningLedger += wrongPayments

        val corrections = wrongPayments.mapIndexed { index, wrong ->
            val allocation = PaymentCorrectionPolicy.reverseRemaining(
                original = wrong,
                payments = runningLedger.toList(),
                correctionAmount = wrong.amount
            )
            assertNotNull("Correction allocation missing for ${wrong.remoteId}", allocation)

            adjustmentFrom(
                id = "correction_$index",
                original = wrong,
                allocation = allocation!!
            ).also { runningLedger += it }
        }

        val projection = PaymentLedgerProjector.project(
            listOf(genuine) + wrongPayments + corrections
        )

        assertMoney(10_500.0, projection.stayApplied)
        assertMoney(0.0, projection.foodApplied)
        assertMoney(0.0, projection.serviceApplied)
        assertMoney(0.0, projection.damageApplied)
        assertMoney(0.0, projection.guestCredit)
        assertMoney(10_500.0, projection.netRecordedPayment)
        assertTrue(projection.reconciles)
    }

    @Test
    fun correction_policy_rejects_partial_correction_of_a_still_uncorrected_payment() {
        val wrong = payment(
            id = "wrong_30000",
            amount = 30_000.0,
            stay = 3_000.0,
            food = 4_000.0,
            unapplied = 23_000.0,
            category = BookingPaymentCategory.AUTO
        )

        val partial = PaymentCorrectionPolicy.reverseRemaining(
            original = wrong,
            payments = listOf(wrong),
            correctionAmount = 27_000.0
        )

        assertEquals(null, partial)
    }

    private fun adjustmentFrom(
        id: String,
        original: BookingPaymentEntity,
        allocation: com.example.bookingregister.accounting.domain.PaymentAllocation
    ): BookingPaymentEntity = BookingPaymentEntity(
        remoteId = id,
        hotelRemoteId = original.hotelRemoteId,
        bookingRemoteId = original.bookingRemoteId,
        originalPaymentRemoteId = original.remoteId,
        paymentType = BookingPaymentType.ADJUSTMENT,
        paymentCategory = allocation.selectedCategory,
        amount = original.amount,
        allocatedStayAmount = allocation.stayAmount,
        allocatedFoodAmount = allocation.foodAmount,
        allocatedServiceAmount = allocation.serviceAmount,
        allocatedDamageAmount = allocation.damageAmount,
        unappliedAmount = allocation.unappliedAmount,
        paymentMillis = original.paymentMillis + 1L,
        updatedAt = original.updatedAt + 1L
    )

    private fun payment(
        id: String,
        amount: Double,
        stay: Double = 0.0,
        food: Double = 0.0,
        service: Double = 0.0,
        damage: Double = 0.0,
        unapplied: Double = 0.0,
        category: String = BookingPaymentCategory.AUTO
    ): BookingPaymentEntity = BookingPaymentEntity(
        remoteId = id,
        hotelRemoteId = "hotel_1",
        bookingRemoteId = "booking_1",
        paymentType = BookingPaymentType.PAYMENT,
        paymentCategory = category,
        amount = amount,
        allocatedStayAmount = stay,
        allocatedFoodAmount = food,
        allocatedServiceAmount = service,
        allocatedDamageAmount = damage,
        unappliedAmount = unapplied,
        paymentMillis = 1L,
        updatedAt = 1L
    )

    private fun assertMoney(expected: Double, actual: Double) {
        assertEquals(expected, actual, 0.001)
    }
}
