package com.example.bookingregister.reporting.property

import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.sync.OtaSettlementServerSnapshot
import kotlin.math.abs

/**
 * Projects authoritative cloud reads into the Balance screen without changing
 * the device database or any financial record.
 */
object OtaSettlementSnapshotPolicy {
    fun overlay(
        local: PropertyReportRawData,
        bookingRemoteIds: List<String>,
        server: OtaSettlementServerSnapshot
    ): PropertyReportRawData {
        val ids = bookingRemoteIds.toSet()
        require(ids.isNotEmpty()) { "No OTA bookings were selected." }
        require(server.bookings.map { it.remoteId }.toSet() == ids) {
            "A selected booking is no longer available in the cloud. No payment was recorded."
        }
        require(local.bookings.none {
            it.remoteId in ids && it.syncState != SyncState.SYNCED
        } && local.payments.none {
            it.bookingRemoteId in ids && it.syncState != SyncState.SYNCED
        }) {
            "A selected booking has payments or changes still waiting to sync. No OTA payment was recorded."
        }
        require(server.payments.all { it.bookingRemoteId in ids }) {
            "Cloud payment data did not match the selected bookings. No payment was recorded."
        }

        val localById = local.bookings.associateBy { it.remoteId }
        val verifiedBookings = server.bookings.map { cloud ->
            if (cloud.remoteId !in server.missingFinancialBookingIds) return@map cloud

            val device = requireNotNull(localById[cloud.remoteId]) {
                "This OTA booking is missing from this device. No payment was recorded."
            }
            require(device.hotelRemoteId == cloud.hotelRemoteId &&
                device.propertyRemoteId == cloud.propertyRemoteId &&
                device.sourceRemoteId == cloud.sourceRemoteId &&
                device.sourceType == cloud.sourceType &&
                device.bookingStatus == cloud.bookingStatus &&
                device.pricingStatus == cloud.pricingStatus &&
                device.checkInMillis == cloud.checkInMillis &&
                device.checkOutMillis == cloud.checkOutMillis &&
                abs(device.grossCharges - cloud.grossCharges) <= 0.02 &&
                device.expectedPayout > 0.001) {
                "The cloud booking differs from this device. Refresh or reconcile it before recording an OTA payment."
            }
            // Older cloud booking documents omitted these fields. Keep the synced
            // device's financial snapshot for preview, but use cloud payments and
            // let the server independently verify the amount at settlement time.
            cloud.copy(
                roomRevenue = device.roomRevenue,
                propertyTax = device.propertyTax,
                commissionAmount = device.commissionAmount,
                commissionTax = device.commissionTax,
                sourceFee = device.sourceFee,
                tdsAmount = device.tdsAmount,
                tcsAmount = device.tcsAmount,
                expectedPayout = device.expectedPayout,
                rate = device.rate,
                receivable = device.receivable
            )
        }

        return local.copy(
            bookings = local.bookings.filterNot { it.remoteId in ids } + verifiedBookings,
            payments = local.payments.filterNot { it.bookingRemoteId in ids } + server.payments
        )
    }
}
