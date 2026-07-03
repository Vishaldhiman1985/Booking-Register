package com.example.bookingregister.accounting.integrity

import com.example.bookingregister.accounting.domain.FinalBillChargeSelectionPolicy
import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
import com.example.bookingregister.data.entities.BookingAccountingChargeType
import com.example.bookingregister.data.entities.BookingPaymentCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceDamageBillingLifecycleTest {

    @Test
    fun billed_service_and_damage_do_not_reappear_in_next_final_bill() {
        val firstBillId = "final_bill_1"

        val charges = listOf(
            BookingAccountingChargeEntity(
                remoteId = "service_old",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_1",
                chargeType = BookingAccountingChargeType.SERVICE_CHARGE,
                accountBucket = BookingPaymentCategory.SERVICE,
                amount = 500.0,
                description = "Bonfire",
                linkedFinalBillId = firstBillId,
                archivedAt = 1_700_000_000_000L
            ),
            BookingAccountingChargeEntity(
                remoteId = "damage_old",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_1",
                chargeType = BookingAccountingChargeType.DAMAGE_CHARGE,
                accountBucket = BookingPaymentCategory.DAMAGE,
                amount = 1_000.0,
                description = "Broken glass",
                linkedFinalBillId = firstBillId,
                archivedAt = 1_700_000_000_000L
            ),
            BookingAccountingChargeEntity(
                remoteId = "service_new",
                hotelRemoteId = "hotel_1",
                bookingRemoteId = "booking_1",
                chargeType = BookingAccountingChargeType.SERVICE_CHARGE,
                accountBucket = BookingPaymentCategory.SERVICE,
                amount = 300.0,
                description = "Late bonfire"
            )
        )

        val unbilledServices = FinalBillChargeSelectionPolicy.unbilledServiceCharges(charges)
        val unbilledDamages = FinalBillChargeSelectionPolicy.unbilledDamageCharges(charges)
        val toArchive = FinalBillChargeSelectionPolicy.chargesToArchiveAfterFinalBill(charges)

        assertEquals(1, unbilledServices.size)
        assertEquals("service_new", unbilledServices.first().remoteId)

        assertEquals(0, unbilledDamages.size)

        assertEquals(1, toArchive.size)
        assertEquals("service_new", toArchive.first().remoteId)
    }
}