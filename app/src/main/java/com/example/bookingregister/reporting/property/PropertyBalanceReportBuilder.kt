package com.example.bookingregister.reporting.property

import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.folio.domain.FolioSnapshotBuilder

/**
 * Read-only property balance/receivables builder.
 *
 * No payment is created or changed here.
 * - Amount due follows the application's existing BookingPaymentCalculator policy.
 * - Amount received is reconstructed from actual payment/refund/correction rows
 *   through the existing FolioSnapshotBuilder payment-allocation logic.
 * - Excess payment on one booking is reported separately and is never applied
 *   to another booking's outstanding balance.
 */
class PropertyBalanceReportBuilder(
    private val folioSnapshotBuilder: FolioSnapshotBuilder = FolioSnapshotBuilder()
) {
    fun build(dataset: PropertyReportDataset): PropertyBalanceFacts {
        val activeBookings = dataset.bookings.filter {
            !it.isDeleted && it.bookingStatus != BookingStatus.CANCELLED
        }

        val rows = activeBookings.map { booking ->
            val bookingPayments = dataset.payments.filter {
                !it.isDeleted && it.bookingRemoteId == booking.remoteId
            }
            val bookingFinancialLines = dataset.financialLines.filter {
                !it.isDeleted && it.bookingRemoteId == booking.remoteId
            }

            val snapshot = folioSnapshotBuilder.build(
                booking = booking,
                payments = bookingPayments,
                bookingFinancialLines = bookingFinancialLines
            )

            val receivable = when {
                booking.sourceType == BookingSourceType.OTA && booking.expectedPayout > 0.0 ->
                    booking.expectedPayout
                booking.receivable > 0.0 ->
                    booking.receivable
                booking.rate > 0.0 ->
                    booking.rate
                else ->
                    0.0
            }.coerceAtLeast(0.0)

            val received = snapshot.room.paid.coerceAtLeast(0.0)
            val appliedReceived = received.coerceAtMost(receivable)
            val excessPayment = (received - receivable).coerceAtLeast(0.0)
            val outstanding = (receivable - appliedReceived).coerceAtLeast(0.0)

            PropertyBalanceBookingFacts(
                bookingRemoteId = booking.remoteId,
                guestName = booking.guestName,
                sourceType = booking.sourceType,
                sourceRemoteId = booking.sourceRemoteId,
                sourceName = booking.sourceName,
                receivable = receivable,
                received = received,
                outstanding = outstanding,
                storedPaidCache = booking.paid,
                storedBalanceCache = booking.balance,
                appliedReceived = appliedReceived,
                excessPayment = excessPayment
            )
        }

        return PropertyBalanceFacts(
            totalReceivable = rows.sumOf { it.receivable },
            totalReceived = rows.sumOf { it.received },
            totalOutstanding = rows.sumOf { it.outstanding },
            otaOutstanding = rows
                .filter { it.sourceType == BookingSourceType.OTA }
                .sumOf { it.outstanding },
            guestOutstanding = rows
                .filter { it.sourceType != BookingSourceType.OTA }
                .sumOf { it.outstanding },
            openBookingCount = rows.count { it.outstanding > 0.001 },
            bookings = rows,
            totalAppliedReceived = rows.sumOf { it.appliedReceived },
            totalExcessPayment = rows.sumOf { it.excessPayment }
        )
    }
}
