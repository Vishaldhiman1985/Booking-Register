package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import com.example.bookingregister.data.entities.BookingSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellationSettlementPolicyTest {
    @Test
    fun `unpaid direct cancellation needs no settlement`() {
        val decision = decide(BookingSourceType.DIRECT, emptyList(), DirectCancellationChoice.DECIDE_LATER)

        assertEquals(CancellationSettlementStatus.NOT_REQUIRED, decision.status)
        assertNull(decision.outcome)
        assertEquals(0.0, decision.approvedRefundAmount, 0.001)
    }

    @Test
    fun `OTA cancellation always remains pending`() {
        val decision = decide(
            BookingSourceType.OTA,
            listOf(payment("advance", 200.0)),
            DirectCancellationChoice.FULL_REFUND
        )

        assertEquals(CancellationSettlementStatus.PENDING, decision.status)
        assertNull(decision.outcome)
        assertEquals(0.0, decision.approvedRefundAmount, 0.001)
    }

    @Test
    fun `direct paid booking can remain pending without changing money`() {
        val decision = decide(
            BookingSourceType.DIRECT,
            listOf(payment("advance", 200.0)),
            DirectCancellationChoice.DECIDE_LATER
        )

        assertEquals(CancellationSettlementStatus.PENDING, decision.status)
        assertNull(decision.outcome)
        assertEquals(0.0, decision.cancellationFeeAmount, 0.001)
    }

    @Test
    fun `no refund records paid amount as cancellation fee`() {
        val decision = decide(
            BookingSourceType.DIRECT,
            listOf(payment("advance", 200.0)),
            DirectCancellationChoice.NO_REFUND
        )

        assertEquals(CancellationSettlementStatus.DECIDED, decision.status)
        assertEquals(CancellationSettlementOutcome.NO_REFUND, decision.outcome)
        assertEquals(0.0, decision.approvedRefundAmount, 0.001)
        assertEquals(200.0, decision.cancellationFeeAmount, 0.001)
    }

    @Test
    fun `partial refund splits approved refund and cancellation fee`() {
        val decision = decide(
            BookingSourceType.DIRECT,
            listOf(payment("advance", 200.0)),
            DirectCancellationChoice.PARTIAL_REFUND,
            75.0
        )

        assertEquals(CancellationSettlementOutcome.PARTIAL_REFUND, decision.outcome)
        assertEquals(75.0, decision.approvedRefundAmount, 0.001)
        assertEquals(125.0, decision.cancellationFeeAmount, 0.001)
    }

    @Test
    fun `partial refund rejects zero or full paid amount`() {
        assertTrue(
            CancellationSettlementPolicy.decide(
                BookingSourceType.DIRECT,
                listOf(payment("advance", 200.0)),
                DirectCancellationChoice.PARTIAL_REFUND,
                200.0
            ).isFailure
        )
    }

    @Test
    fun `refund due counts only refunds issued after decision baseline`() {
        val payments = listOf(
            payment("advance", 300.0),
            payment("old-refund", 50.0, BookingPaymentType.REFUND),
            payment("new-refund", 80.0, BookingPaymentType.REFUND)
        )

        assertEquals(
            70.0,
            CancellationSettlementPolicy.refundDue(
                bookingApprovedAmount = 150.0,
                refundBaselineAmount = 50.0,
                payments = payments
            ),
            0.001
        )
    }

    private fun decide(
        sourceType: String,
        payments: List<BookingPaymentEntity>,
        choice: DirectCancellationChoice,
        partial: Double? = null
    ): CancellationSettlementDecision =
        CancellationSettlementPolicy.decide(sourceType, payments, choice, partial).getOrThrow()

    private fun payment(
        id: String,
        amount: Double,
        type: String = BookingPaymentType.ADVANCE
    ) = BookingPaymentEntity(
        remoteId = id,
        hotelRemoteId = "hotel-a",
        bookingRemoteId = "booking-a",
        paymentType = type,
        amount = amount
    )
}
