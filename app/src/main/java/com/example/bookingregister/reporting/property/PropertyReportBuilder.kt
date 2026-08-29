package com.example.bookingregister.reporting.property

import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.data.entities.BookingPaymentType

/**
 * Pure reporting code: filters existing records and exposes already-stored accounting facts.
 * It does not save, update, sync or recalculate booking settlement rules.
 */
class PropertyReportBuilder {

    fun scope(raw: PropertyReportRawData, scope: PropertyReportScope): PropertyReportDataset {
        val properties = raw.properties.filter { !it.isDeleted }
        val rooms = raw.rooms.filter {
            !it.isDeleted && scope.matchesProperty(it.propertyRemoteId)
        }
        val bookings = raw.bookings.filter {
            !it.isDeleted && scope.matchesProperty(it.propertyRemoteId)
        }
        val bookingIds = bookings.map { it.remoteId }.toSet()
        val financialLines = raw.financialLines.filter {
            !it.isDeleted &&
                it.bookingRemoteId in bookingIds &&
                scope.matchesProperty(it.propertyRemoteId)
        }
        val payments = raw.payments.filter {
            !it.isDeleted && it.bookingRemoteId in bookingIds
        }

        return PropertyReportDataset(
            scope = scope,
            properties = properties,
            rooms = rooms,
            bookings = bookings,
            financialLines = financialLines,
            payments = payments
        )
    }

    fun revenueFacts(dataset: PropertyReportDataset): PropertyRevenueFacts {
        val range = dataset.scope.startMillis until dataset.scope.endMillis
        val activeBookings = dataset.bookings.filter {
            !it.isDeleted && it.bookingStatus != BookingStatus.CANCELLED
        }
        val activeBookingIds = activeBookings.map { it.remoteId }.toSet()
        val activeLines = dataset.financialLines.filter {
            !it.isDeleted && it.bookingRemoteId in activeBookingIds
        }
        val periodLines = activeLines.filter { it.businessDateMillis in range }

        val periodPayments = dataset.payments.filter {
            !it.isDeleted && it.bookingRemoteId in activeBookingIds && it.paymentMillis in range
        }
        val paymentsRecorded = periodPayments
            .filter { it.paymentType != BookingPaymentType.REFUND && it.paymentType != BookingPaymentType.ADJUSTMENT }
            .sumOf { it.amount.coerceAtLeast(0.0) }
        val refundsRecorded = periodPayments
            .filter { it.paymentType == BookingPaymentType.REFUND }
            .sumOf { it.amount.coerceAtLeast(0.0) }

        val linesByBooking = activeLines.groupBy { it.bookingRemoteId }
        val periodLinesByBooking = periodLines.groupBy { it.bookingRemoteId }

        val settlements = activeBookings.mapNotNull { booking ->
            val bookingPeriodLines = periodLinesByBooking[booking.remoteId].orEmpty()
            if (bookingPeriodLines.isEmpty()) return@mapNotNull null

            BookingSettlementFacts(
                bookingRemoteId = booking.remoteId,
                sourceType = booking.sourceType,
                roomRevenueInReportPeriod = bookingPeriodLines.sumOf { it.taxableAmount.coerceAtLeast(0.0) },
                fullBookingRoomRevenueFromFinancialLines = linesByBooking[booking.remoteId]
                    .orEmpty()
                    .sumOf { it.taxableAmount.coerceAtLeast(0.0) },
                storedCommissionAmount = booking.commissionAmount,
                storedCommissionTax = booking.commissionTax,
                storedSourceFee = booking.sourceFee,
                storedTdsAmount = booking.tdsAmount,
                storedTcsAmount = booking.tcsAmount,
                storedExpectedPayout = booking.expectedPayout
            )
        }

        return PropertyRevenueFacts(
            grossRoomBilling = periodLines.sumOf { it.grossAmount.coerceAtLeast(0.0) },
            roomRevenue = periodLines.sumOf { it.taxableAmount.coerceAtLeast(0.0) },
            gstCollected = periodLines.sumOf { it.gstAmount.coerceAtLeast(0.0) },
            cgstCollected = periodLines.sumOf { it.cgstAmount.coerceAtLeast(0.0) },
            sgstCollected = periodLines.sumOf { it.sgstAmount.coerceAtLeast(0.0) },
            cessCollected = periodLines.sumOf { it.cessAmount.coerceAtLeast(0.0) },
            paymentsRecordedInPeriod = paymentsRecorded,
            refundsRecordedInPeriod = refundsRecorded,
            bookingSettlements = settlements
        )
    }
}
