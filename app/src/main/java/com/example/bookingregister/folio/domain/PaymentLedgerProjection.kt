package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import kotlin.math.abs

/**
 * Read-only projection of the payment ledger.
 *
 * Important:
 * - AUTO is an entry-time allocation instruction, not a payment bucket.
 * - Stored allocation fields are the final accounting result.
 * - unappliedAmount is Guest Credit.
 * - Category fallback is used only for legacy rows that contain no allocation metadata at all.
 *
 * This class is intentionally isolated and is not wired into production screens yet.
 */
data class PaymentLedgerProjection(
    val stayApplied: Double,
    val foodApplied: Double,
    val serviceApplied: Double,
    val damageApplied: Double,
    val guestCredit: Double,
    val netRecordedPayment: Double,
    val legacyFallbackPaymentIds: List<String> = emptyList(),
    val integrityErrors: List<String> = emptyList()
) {
    val appliedTotal: Double
        get() = stayApplied + foodApplied + serviceApplied + damageApplied

    val accountedPayment: Double
        get() = appliedTotal + guestCredit

    val reconciliationDifference: Double
        get() = netRecordedPayment - accountedPayment

    val reconciles: Boolean
        get() = abs(reconciliationDifference) <= 0.02
}

object PaymentLedgerProjector {
    private const val TOLERANCE = 0.001
    private const val MONEY_TOLERANCE = 0.02

    fun project(payments: List<BookingPaymentEntity>): PaymentLedgerProjection {
        var stay = 0.0
        var food = 0.0
        var service = 0.0
        var damage = 0.0
        var guestCredit = 0.0
        var netRecordedPayment = 0.0

        val legacyFallbackIds = mutableListOf<String>()
        val integrityErrors = mutableListOf<String>()

        payments
            .asSequence()
            .filter { !it.isDeleted }
            .forEach { payment ->
                val sign = when (payment.paymentType) {
                    BookingPaymentType.REFUND,
                    BookingPaymentType.ADJUSTMENT -> -1.0
                    else -> 1.0
                }

                netRecordedPayment += sign * payment.amount

                val explicitApplied =
                    payment.allocatedStayAmount +
                        payment.allocatedFoodAmount +
                        payment.allocatedServiceAmount +
                        payment.allocatedDamageAmount

                val explicitAccountingTotal = explicitApplied + payment.unappliedAmount

                if (explicitAccountingTotal > TOLERANCE) {
                    if (explicitAccountingTotal > payment.amount + MONEY_TOLERANCE) {
                        integrityErrors +=
                            "Payment ${payment.remoteId} allocations exceed payment amount"
                    }

                    // Some older rows may have partial explicit allocation metadata.
                    // Preserve the explicit buckets and treat any unexplained positive
                    // remainder as Guest Credit rather than guessing another bucket.
                    val implicitGuestCredit =
                        (payment.amount - explicitAccountingTotal).coerceAtLeast(0.0)

                    stay += sign * payment.allocatedStayAmount
                    food += sign * payment.allocatedFoodAmount
                    service += sign * payment.allocatedServiceAmount
                    damage += sign * payment.allocatedDamageAmount
                    guestCredit += sign * (payment.unappliedAmount + implicitGuestCredit)
                } else {
                    // Genuine legacy compatibility only: this row has no allocation
                    // metadata at all. Do not use this path merely because AUTO was chosen.
                    legacyFallbackIds += payment.remoteId

                    when (BookingPaymentCategory.normalize(payment.paymentCategory)) {
                        BookingPaymentCategory.FOOD ->
                            food += sign * payment.amount
                        BookingPaymentCategory.SERVICE ->
                            service += sign * payment.amount
                        BookingPaymentCategory.DAMAGE ->
                            damage += sign * payment.amount
                        else ->
                            stay += sign * payment.amount
                    }
                }
            }

        val projection = PaymentLedgerProjection(
            stayApplied = stay,
            foodApplied = food,
            serviceApplied = service,
            damageApplied = damage,
            guestCredit = guestCredit,
            netRecordedPayment = netRecordedPayment,
            legacyFallbackPaymentIds = legacyFallbackIds,
            integrityErrors = integrityErrors
        )

        if (!projection.reconciles) {
            return projection.copy(
                integrityErrors = projection.integrityErrors +
                    "Payment ledger does not reconcile by ${projection.reconciliationDifference}"
            )
        }

        return projection
    }
}
