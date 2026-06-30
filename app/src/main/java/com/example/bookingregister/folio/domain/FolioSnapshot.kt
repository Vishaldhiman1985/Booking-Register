package com.example.bookingregister.folio.domain

import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderItemEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity

data class FolioBucketSnapshot(
    val charge: Double = 0.0,
    val discount: Double = 0.0,
    val paid: Double = 0.0
) {
    val netCharge: Double get() = (charge - discount).coerceAtLeast(0.0)
    val balance: Double get() = (netCharge - paid).coerceAtLeast(0.0)
}

data class FolioSnapshot(
    val bookingRemoteId: String,
    val sourceType: String,
    val room: FolioBucketSnapshot,
    val food: FolioBucketSnapshot,
    val service: FolioBucketSnapshot,
    val damage: FolioBucketSnapshot,
    val generalDiscount: Double,
    val unappliedPaid: Double,
    val lines: List<MiniFolioLine>
) {
    val grossCharges: Double get() = room.charge + food.charge + service.charge + damage.charge
    val totalDiscount: Double get() = room.discount + food.discount + service.discount + generalDiscount
    val netCharges: Double get() = (grossCharges - totalDiscount).coerceAtLeast(0.0)
    val bucketPaid: Double get() = room.paid + food.paid + service.paid + damage.paid
    val totalPaid: Double get() = bucketPaid + unappliedPaid
    val folioBalance: Double get() = (netCharges - totalPaid).coerceAtLeast(0.0)
    val guestCredit: Double get() = (totalPaid - netCharges).coerceAtLeast(0.0)
    val guestCheckoutBalance: Double
        get() = if (sourceType == BookingSourceType.OTA) {
            (food.balance + service.balance + damage.balance - generalDiscount).coerceAtLeast(0.0)
        } else {
            folioBalance
        }

    fun bucket(category: String): FolioBucketSnapshot {
        return when (BookingPaymentCategory.normalize(category)) {
            BookingPaymentCategory.FOOD -> food
            BookingPaymentCategory.SERVICE -> service
            BookingPaymentCategory.DAMAGE -> damage
            else -> room
        }
    }
}

class FolioSnapshotBuilder {
    private val miniFolioBuilder = MiniFolioBuilder()

    fun build(
        booking: BookingEntity,
        activeRoomIds: Set<String> = booking.roomRemoteIds.toSet(),
        payments: List<BookingPaymentEntity> = emptyList(),
        accountingCharges: List<BookingAccountingChargeEntity> = emptyList(),
        foodOrders: List<FoodOrderEntity> = emptyList(),
        foodOrderItems: List<FoodOrderItemEntity> = emptyList(),
        bookingFinancialLines: List<BookingFinancialLineEntity> = emptyList()
    ): FolioSnapshot {
        val folio = miniFolioBuilder.buildForBooking(
            booking = booking,
            activeRoomIds = activeRoomIds,
            bookingPayments = payments,
            accountingCharges = accountingCharges,
            foodOrders = foodOrders,
            foodOrderItems = foodOrderItems,
            bookingFinancialLines = bookingFinancialLines

        )
        val lines = folio?.lines.orEmpty()
        val paymentBuckets = payments.paymentBuckets()
        val actualPaymentTotal = payments.actualPaymentTotal()
        val appliedPaid = paymentBuckets.values.sum()
        val unappliedPaid = (actualPaymentTotal - appliedPaid).coerceAtLeast(0.0)

        return FolioSnapshot(
            bookingRemoteId = booking.remoteId,
            sourceType = booking.sourceType,
            room = bucketSnapshot(lines, BookingPaymentCategory.STAY, paymentBuckets),
            food = bucketSnapshot(lines, BookingPaymentCategory.FOOD, paymentBuckets),
            service = bucketSnapshot(lines, BookingPaymentCategory.SERVICE, paymentBuckets),
            damage = bucketSnapshot(lines, BookingPaymentCategory.DAMAGE, paymentBuckets),
            generalDiscount = lines.discountFor(null),
            unappliedPaid = unappliedPaid,
            lines = lines
        )
    }

    private fun bucketSnapshot(
        lines: List<MiniFolioLine>,
        bucket: String,
        payments: Map<String, Double>
    ): FolioBucketSnapshot {
        return FolioBucketSnapshot(
            charge = lines
                .filter {
                    it.kind == MiniFolioLineKind.CHARGE &&
                            BookingPaymentCategory.normalize(it.accountBucket) == bucket
                }
                .sumOf { it.amount }
                .coerceAtLeast(0.0),
            discount = lines.discountFor(bucket).coerceAtLeast(0.0),
            paid = payments[bucket].orZero().coerceAtLeast(0.0)
        )
    }

    private fun List<MiniFolioLine>.discountFor(bucket: String?): Double {
        return filter {
            it.kind == MiniFolioLineKind.DISCOUNT &&
                    if (bucket == null) it.accountBucket == null else BookingPaymentCategory.normalize(it.accountBucket) == bucket
        }.sumOf { it.amount }
    }

    private fun List<BookingPaymentEntity>.paymentBuckets(): Map<String, Double> {
        val totals = mutableMapOf(
            BookingPaymentCategory.STAY to 0.0,
            BookingPaymentCategory.FOOD to 0.0,
            BookingPaymentCategory.SERVICE to 0.0,
            BookingPaymentCategory.DAMAGE to 0.0
        )
        filter { !it.isDeleted }.forEach { payment ->
            val sign = when (payment.paymentType) {
                BookingPaymentType.REFUND,
                BookingPaymentType.ADJUSTMENT -> -1.0
                else -> 1.0
            }
            val allocatedTotal = payment.allocatedStayAmount +
                    payment.allocatedFoodAmount +
                    payment.allocatedServiceAmount
            if (allocatedTotal > 0.0) {
                totals.addTo(BookingPaymentCategory.STAY, sign * payment.allocatedStayAmount)
                totals.addTo(BookingPaymentCategory.FOOD, sign * payment.allocatedFoodAmount)
                totals.addTo(BookingPaymentCategory.SERVICE, sign * payment.allocatedServiceAmount)
            } else {
                val category = BookingPaymentCategory.normalize(payment.paymentCategory)
                val bucket = when (category) {
                    BookingPaymentCategory.FOOD,
                    BookingPaymentCategory.SERVICE,
                    BookingPaymentCategory.DAMAGE -> category
                    else -> BookingPaymentCategory.STAY
                }
                totals.addTo(bucket, sign * payment.amount)
            }
        }
        return totals
    }

    private fun List<BookingPaymentEntity>.actualPaymentTotal(): Double {
        return filter { !it.isDeleted }.sumOf { payment ->
            when (payment.paymentType) {
                BookingPaymentType.REFUND,
                BookingPaymentType.ADJUSTMENT -> -payment.amount
                else -> payment.amount
            }
        }.coerceAtLeast(0.0)
    }

    private fun MutableMap<String, Double>.addTo(bucket: String, amount: Double) {
        this[bucket] = (this[bucket] ?: 0.0) + amount
    }

    private fun Double?.orZero(): Double = this ?: 0.0
}
