package com.example.bookingregister.folio.domain

import com.example.bookingregister.accounting.domain.ChargeBuckets
import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity

data class FolioSummary(
    val stayTotal: Double,
    val foodTotal: Double,
    val serviceTotal: Double,
    val damageTotal: Double,
    val stayPaid: Double,
    val foodPaid: Double,
    val servicePaid: Double,
    val damagePaid: Double,
    val unappliedPaid: Double,
    val discountTotal: Double = 0.0,
    val stayDiscount: Double = 0.0,
    val foodDiscount: Double = 0.0,
    val serviceDiscount: Double = 0.0,
    val generalDiscount: Double = 0.0
) {
    val grandCharges: Double get() = stayTotal + foodTotal + serviceTotal + damageTotal
    val grandTotal: Double get() = (grandCharges - discountTotal).coerceAtLeast(0.0)
    val totalPaid: Double get() = stayPaid + foodPaid + servicePaid + damagePaid + unappliedPaid
    val stayNetTotal: Double get() = (stayTotal - stayDiscount).coerceAtLeast(0.0)
    val foodNetTotal: Double get() = (foodTotal - foodDiscount).coerceAtLeast(0.0)
    val serviceNetTotal: Double get() = (serviceTotal - serviceDiscount).coerceAtLeast(0.0)
    val stayBalance: Double get() = (stayNetTotal - stayPaid).coerceAtLeast(0.0)
    val foodBalance: Double get() = (foodNetTotal - foodPaid).coerceAtLeast(0.0)
    val serviceBalance: Double get() = (serviceNetTotal - servicePaid).coerceAtLeast(0.0)
    val damageBalance: Double get() = (damageTotal - damagePaid).coerceAtLeast(0.0)
    val grandBalance: Double get() = (grandTotal - totalPaid).coerceAtLeast(0.0)
    val guestCredit: Double get() = (totalPaid - grandTotal).coerceAtLeast(0.0)

    val chargeBuckets: ChargeBuckets get() = ChargeBuckets(stayNetTotal, foodNetTotal, serviceNetTotal)
    val paidBuckets: ChargeBuckets get() = ChargeBuckets(stayPaid, foodPaid, servicePaid)
}

object FolioSummaryBuilder {
    private val snapshotBuilder = FolioSnapshotBuilder()

    fun build(
        booking: BookingEntity,
        payments: List<BookingPaymentEntity>,
        foodOrders: List<FoodOrderEntity>,
        accountingCharges: List<BookingAccountingChargeEntity> = emptyList(),
        bookingFinancialLines: List<BookingFinancialLineEntity> = emptyList(),
        serviceTotal: Double = 0.0,
        damageTotal: Double = 0.0
    ): FolioSummary {
        val snapshot = snapshotBuilder.build(
            booking = booking,
            payments = payments,
            accountingCharges = accountingCharges,
            foodOrders = foodOrders,
            bookingFinancialLines = bookingFinancialLines
        )
        return FolioSummary(
            stayTotal = snapshot.room.charge,
            foodTotal = snapshot.food.charge,
            serviceTotal = (snapshot.service.charge + serviceTotal).coerceAtLeast(0.0),
            damageTotal = (snapshot.damage.charge + damageTotal).coerceAtLeast(0.0),
            stayPaid = snapshot.room.paid,
            foodPaid = snapshot.food.paid,
            servicePaid = snapshot.service.paid,
            damagePaid = snapshot.damage.paid,
            unappliedPaid = snapshot.unappliedPaid,
            discountTotal = snapshot.totalDiscount,
            stayDiscount = snapshot.room.discount,
            foodDiscount = snapshot.food.discount,
            serviceDiscount = snapshot.service.discount,
            generalDiscount = snapshot.generalDiscount
        )
    }
}
