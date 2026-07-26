package com.example.bookingregister.booking.domain

import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import com.example.bookingregister.data.entities.BookingSourceType
import kotlin.math.round

object CancellationSettlementStatus {
    const val NOT_APPLICABLE = "NOT_APPLICABLE"
    const val NOT_REQUIRED = "NOT_REQUIRED"
    const val PENDING = "PENDING"
    const val DECIDED = "DECIDED"
}

object CancellationSettlementOutcome {
    const val NO_REFUND = "NO_REFUND"
    const val PARTIAL_REFUND = "PARTIAL_REFUND"
    const val FULL_REFUND = "FULL_REFUND"
}

enum class DirectCancellationChoice {
    DECIDE_LATER,
    NO_REFUND,
    PARTIAL_REFUND,
    FULL_REFUND
}

data class CancellationRequest(
    val reason: String,
    val directChoice: DirectCancellationChoice = DirectCancellationChoice.DECIDE_LATER,
    val partialRefundAmount: Double? = null
)

data class CancellationSettlementDecision(
    val status: String,
    val outcome: String?,
    val approvedRefundAmount: Double,
    val cancellationFeeAmount: Double,
    val refundBaselineAmount: Double
)

object CancellationSettlementPolicy {
    private const val MONEY_EPSILON = 0.001

    fun decide(
        sourceType: String,
        payments: List<BookingPaymentEntity>,
        choice: DirectCancellationChoice,
        partialRefundAmount: Double? = null
    ): Result<CancellationSettlementDecision> {
        val activePayments = payments.filter { !it.isDeleted }
        val netPaid = netPaid(activePayments)
        val refundBaseline = refundsIssued(activePayments)

        if (sourceType == BookingSourceType.OTA) {
            return Result.success(
                CancellationSettlementDecision(
                    status = CancellationSettlementStatus.PENDING,
                    outcome = null,
                    approvedRefundAmount = 0.0,
                    cancellationFeeAmount = 0.0,
                    refundBaselineAmount = refundBaseline
                )
            )
        }
        if (netPaid <= MONEY_EPSILON) {
            return Result.success(
                CancellationSettlementDecision(
                    status = CancellationSettlementStatus.NOT_REQUIRED,
                    outcome = null,
                    approvedRefundAmount = 0.0,
                    cancellationFeeAmount = 0.0,
                    refundBaselineAmount = refundBaseline
                )
            )
        }

        val approvedRefund = when (choice) {
            DirectCancellationChoice.DECIDE_LATER -> 0.0
            DirectCancellationChoice.NO_REFUND -> 0.0
            DirectCancellationChoice.FULL_REFUND -> netPaid
            DirectCancellationChoice.PARTIAL_REFUND -> {
                val requested = roundMoney(partialRefundAmount ?: 0.0)
                if (requested <= MONEY_EPSILON || requested >= netPaid - MONEY_EPSILON) {
                    return Result.failure(
                        IllegalArgumentException(
                            "Partial refund must be more than zero and less than the paid amount."
                        )
                    )
                }
                requested
            }
        }
        return Result.success(
            CancellationSettlementDecision(
                status = if (choice == DirectCancellationChoice.DECIDE_LATER) {
                    CancellationSettlementStatus.PENDING
                } else {
                    CancellationSettlementStatus.DECIDED
                },
                outcome = when (choice) {
                    DirectCancellationChoice.DECIDE_LATER -> null
                    DirectCancellationChoice.NO_REFUND -> CancellationSettlementOutcome.NO_REFUND
                    DirectCancellationChoice.PARTIAL_REFUND -> CancellationSettlementOutcome.PARTIAL_REFUND
                    DirectCancellationChoice.FULL_REFUND -> CancellationSettlementOutcome.FULL_REFUND
                },
                approvedRefundAmount = approvedRefund,
                cancellationFeeAmount = if (choice == DirectCancellationChoice.DECIDE_LATER) {
                    0.0
                } else {
                    roundMoney(netPaid - approvedRefund)
                },
                refundBaselineAmount = refundBaseline
            )
        )
    }

    fun netPaid(payments: List<BookingPaymentEntity>): Double =
        roundMoney(
            payments.filter { !it.isDeleted }.sumOf { payment ->
                when (payment.paymentType) {
                    BookingPaymentType.REFUND,
                    BookingPaymentType.ADJUSTMENT -> -payment.amount
                    else -> payment.amount
                }
            }.coerceAtLeast(0.0)
        )

    fun refundsIssued(payments: List<BookingPaymentEntity>): Double =
        roundMoney(
            payments.filter {
                !it.isDeleted && it.paymentType == BookingPaymentType.REFUND
            }.sumOf { it.amount.coerceAtLeast(0.0) }
        )

    fun refundDue(bookingApprovedAmount: Double, refundBaselineAmount: Double, payments: List<BookingPaymentEntity>): Double {
        val issuedAfterDecision = (refundsIssued(payments) - refundBaselineAmount).coerceAtLeast(0.0)
        return roundMoney((bookingApprovedAmount - issuedAfterDecision).coerceAtLeast(0.0))
    }

    private fun roundMoney(amount: Double): Double = round(amount * 100.0) / 100.0
}
