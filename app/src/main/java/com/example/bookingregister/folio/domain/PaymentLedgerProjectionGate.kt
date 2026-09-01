package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingPaymentEntity
import kotlin.math.abs

enum class PaymentLedgerProjectionReadiness {
    SAFE_FOR_CANONICAL_PROJECTION,
    LEGACY_REVIEW_REQUIRED
}

data class PaymentLedgerProjectionGateResult(
    val readiness: PaymentLedgerProjectionReadiness,
    val issues: List<PaymentLedgerIntegrityIssue>
) {
    val isSafe: Boolean
        get() = readiness == PaymentLedgerProjectionReadiness.SAFE_FOR_CANONICAL_PROJECTION
}

object PaymentLedgerProjectionGate {
    private const val MONEY_TOLERANCE = 0.02

    fun evaluate(payments: List<BookingPaymentEntity>): PaymentLedgerProjectionGateResult {
        val issues = PaymentLedgerIntegrityAnalyzer.analyze(payments)

        if (issues.isEmpty()) {
            return safeResult()
        }

        val activeByRemoteId = payments
            .asSequence()
            .filter { !it.isDeleted }
            .associateBy { it.remoteId }

        val blockingIssues = issues.filter { issue ->
            when (issue.issueType) {
                PaymentLedgerIntegrityIssueType.LEGACY_UNLINKED_ADJUSTMENT -> {
                    val payment = activeByRemoteId[issue.paymentRemoteId]
                    payment == null || !payment.hasCompleteExplicitAccounting()
                }

                PaymentLedgerIntegrityIssueType.LEGACY_UNLINKED_REFUND,
                PaymentLedgerIntegrityIssueType.BROKEN_ORIGINAL_LINK -> true
            }
        }

        if (blockingIssues.isNotEmpty()) {
            return legacyResult(blockingIssues)
        }

        val projection = PaymentLedgerProjector.project(payments)

        val projectionIsSafe =
            projection.integrityErrors.isEmpty() &&
                projection.reconciles &&
                projection.netRecordedPayment >= -MONEY_TOLERANCE &&
                projection.stayApplied >= -MONEY_TOLERANCE &&
                projection.foodApplied >= -MONEY_TOLERANCE &&
                projection.serviceApplied >= -MONEY_TOLERANCE &&
                projection.damageApplied >= -MONEY_TOLERANCE &&
                projection.guestCredit >= -MONEY_TOLERANCE

        return if (projectionIsSafe) {
            safeResult()
        } else {
            legacyResult(issues)
        }
    }

    private fun BookingPaymentEntity.hasCompleteExplicitAccounting(): Boolean {
        val explicitTotal =
            allocatedStayAmount +
                allocatedFoodAmount +
                allocatedServiceAmount +
                allocatedDamageAmount +
                unappliedAmount

        return explicitTotal > MONEY_TOLERANCE &&
            abs(explicitTotal - amount) <= MONEY_TOLERANCE
    }

    private fun safeResult(): PaymentLedgerProjectionGateResult {
        return PaymentLedgerProjectionGateResult(
            readiness = PaymentLedgerProjectionReadiness.SAFE_FOR_CANONICAL_PROJECTION,
            issues = emptyList()
        )
    }

    private fun legacyResult(
        issues: List<PaymentLedgerIntegrityIssue>
    ): PaymentLedgerProjectionGateResult {
        return PaymentLedgerProjectionGateResult(
            readiness = PaymentLedgerProjectionReadiness.LEGACY_REVIEW_REQUIRED,
            issues = issues
        )
    }
}
