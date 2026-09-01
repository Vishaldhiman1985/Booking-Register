package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType

enum class PaymentLedgerIntegrityIssueType {
    LEGACY_UNLINKED_ADJUSTMENT,
    LEGACY_UNLINKED_REFUND,
    BROKEN_ORIGINAL_LINK
}

data class PaymentLedgerIntegrityIssue(
    val paymentRemoteId: String,
    val issueType: PaymentLedgerIntegrityIssueType,
    val originalPaymentRemoteId: String? = null
)

object PaymentLedgerIntegrityAnalyzer {

    fun analyze(payments: List<BookingPaymentEntity>): List<PaymentLedgerIntegrityIssue> {
        val activePayments = payments.filter { !it.isDeleted }
        val byRemoteId = activePayments.associateBy { it.remoteId }

        return activePayments.mapNotNull { payment ->
            when (payment.paymentType) {
                BookingPaymentType.ADJUSTMENT -> analyzeReversal(
                    payment = payment,
                    byRemoteId = byRemoteId,
                    unlinkedType = PaymentLedgerIntegrityIssueType.LEGACY_UNLINKED_ADJUSTMENT
                )

                BookingPaymentType.REFUND -> analyzeReversal(
                    payment = payment,
                    byRemoteId = byRemoteId,
                    unlinkedType = PaymentLedgerIntegrityIssueType.LEGACY_UNLINKED_REFUND
                )

                else -> null
            }
        }
    }

    private fun analyzeReversal(
        payment: BookingPaymentEntity,
        byRemoteId: Map<String, BookingPaymentEntity>,
        unlinkedType: PaymentLedgerIntegrityIssueType
    ): PaymentLedgerIntegrityIssue? {
        val originalId = payment.originalPaymentRemoteId?.takeIf { it.isNotBlank() }

        if (originalId == null) {
            return PaymentLedgerIntegrityIssue(
                paymentRemoteId = payment.remoteId,
                issueType = unlinkedType
            )
        }

        if (!byRemoteId.containsKey(originalId)) {
            return PaymentLedgerIntegrityIssue(
                paymentRemoteId = payment.remoteId,
                issueType = PaymentLedgerIntegrityIssueType.BROKEN_ORIGINAL_LINK,
                originalPaymentRemoteId = originalId
            )
        }

        return null
    }
}
