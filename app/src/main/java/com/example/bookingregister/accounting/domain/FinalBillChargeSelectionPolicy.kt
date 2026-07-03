package com.example.bookingregister.accounting.domain

import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
import com.example.bookingregister.data.entities.BookingAccountingChargeType

object FinalBillChargeSelectionPolicy {
    fun unbilledServiceCharges(charges: List<BookingAccountingChargeEntity>) =
        unbilledCharges(charges, BookingAccountingChargeType.SERVICE_CHARGE)

    fun unbilledDamageCharges(charges: List<BookingAccountingChargeEntity>) =
        unbilledCharges(charges, BookingAccountingChargeType.DAMAGE_CHARGE)

    fun chargesToArchiveAfterFinalBill(charges: List<BookingAccountingChargeEntity>) =
        charges.filter { charge ->
            isUnbilled(charge) && BookingAccountingChargeType.normalize(charge.chargeType) in setOf(
                BookingAccountingChargeType.SERVICE_CHARGE,
                BookingAccountingChargeType.DAMAGE_CHARGE
            )
        }

    private fun unbilledCharges(charges: List<BookingAccountingChargeEntity>, type: String) =
        charges.filter { charge ->
            isUnbilled(charge) && BookingAccountingChargeType.normalize(charge.chargeType) == type
        }

    private fun isUnbilled(charge: BookingAccountingChargeEntity): Boolean =
        !charge.isDeleted && charge.linkedFinalBillId.isNullOrBlank() && charge.amount > 0.0
}
