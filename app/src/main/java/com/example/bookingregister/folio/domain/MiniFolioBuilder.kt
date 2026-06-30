package com.example.bookingregister.folio.domain

import com.example.bookingregister.common.domain.BusinessDates
import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
import com.example.bookingregister.data.entities.BookingAccountingChargeType
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderItemEntity
import com.example.bookingregister.data.entities.FoodOrderStatus
import com.example.bookingregister.data.entities.RoomEntity
import kotlin.math.roundToLong
import com.example.bookingregister.data.entities.BookingFinancialLineEntity

class MiniFolioBuilder {
    fun build(
        rooms: List<RoomEntity>,
        bookings: List<BookingEntity>,
        bookingPayments: List<BookingPaymentEntity> = emptyList(),
        accountingCharges: List<BookingAccountingChargeEntity> = emptyList(),
        foodOrders: List<FoodOrderEntity> = emptyList(),
        foodOrderItems: List<FoodOrderItemEntity> = emptyList(),
        bookingFinancialLines: List<BookingFinancialLineEntity> = emptyList()
    ): List<MiniFolio> {
        val activeRoomIds = rooms.filter { !it.isDeleted }.map { it.remoteId }.toSet()
        val paymentsByBooking = bookingPayments.filter { !it.isDeleted }.groupBy { it.bookingRemoteId }
        val chargesByBooking = accountingCharges.filter { !it.isDeleted }.groupBy { it.bookingRemoteId }
        val financialLinesByBooking = bookingFinancialLines
            .filter { !it.isDeleted }
            .groupBy { it.bookingRemoteId }

        return bookings
            .filter { !it.isDeleted }
            .mapNotNull { booking ->
                buildForBooking(
                    booking = booking,
                    activeRoomIds = activeRoomIds,
                    bookingPayments = paymentsByBooking[booking.remoteId].orEmpty(),
                    accountingCharges = chargesByBooking[booking.remoteId].orEmpty(),
                    foodOrders = foodOrders,
                    foodOrderItems = foodOrderItems,
                    bookingFinancialLines = financialLinesByBooking[booking.remoteId].orEmpty()
                )
            }
    }

    fun buildForBooking(
        booking: BookingEntity,
        activeRoomIds: Set<String>,
        bookingPayments: List<BookingPaymentEntity> = emptyList(),
        accountingCharges: List<BookingAccountingChargeEntity> = emptyList(),
        foodOrders: List<FoodOrderEntity> = emptyList(),
        foodOrderItems: List<FoodOrderItemEntity> = emptyList(),
        bookingFinancialLines: List<BookingFinancialLineEntity> = emptyList()
    ): MiniFolio? {
        val bookedRoomIds = booking.roomRemoteIds.filter { it in activeRoomIds }
        if (bookedRoomIds.isEmpty()) return null

        val lines = mutableListOf<MiniFolioLine>()
        lines += roomChargeLines(booking, bookedRoomIds, bookingFinancialLines)
        lines += foodChargeLines(booking, foodOrders, foodOrderItems)
        lines += accountingChargeLines(booking, accountingCharges)
        lines += paymentLines(booking, bookingPayments)

        val folio = MiniFolio(
            bookingRemoteId = booking.remoteId,
            hotelRemoteId = booking.hotelRemoteId,
            guestName = booking.guestName,
            status = MiniFolioStatus.OPEN,
            lines = lines
        )

        return folio.copy(
            status = if (folio.balance <= 0.01) MiniFolioStatus.SETTLED else MiniFolioStatus.OPEN
        )
    }

    private fun roomChargeLines(
        booking: BookingEntity,
        bookedRoomIds: List<String>,
        bookingFinancialLines: List<BookingFinancialLineEntity>
    ): List<MiniFolioLine> {
        val validFinancialLines = bookingFinancialLines
            .filter { line ->
                !line.isDeleted &&
                        line.bookingRemoteId == booking.remoteId &&
                        line.roomRemoteId in bookedRoomIds &&
                        line.grossAmount > 0.0
            }
            .sortedWith(
                compareBy<BookingFinancialLineEntity> { BusinessDates.startOfDay(it.businessDateMillis) }
                    .thenBy { it.roomRemoteId }
                    .thenBy { it.remoteId }
            )

        if (validFinancialLines.isNotEmpty()) {
            return validFinancialLines.map { line ->
                MiniFolioLine(
                    bookingRemoteId = booking.remoteId,
                    businessDateMillis = BusinessDates.startOfDay(line.businessDateMillis),
                    type = MiniFolioLineType.ROOM_CHARGE,
                    kind = MiniFolioLineKind.CHARGE,
                    amount = line.grossAmount,
                    description = "Room charge",
                    roomRemoteId = line.roomRemoteId,
                    accountBucket = BookingPaymentCategory.STAY
                )
            }
        }

        val stayNights = BusinessDates.stayNights(booking.checkInMillis, booking.checkOutMillis)
        val roomNightCount = stayNights * bookedRoomIds.size

        val revenueAmount = booking.grossCharges
            .takeIf { it > 0.0 }
            ?: booking.receivable.takeIf { it > 0.0 }
            ?: booking.rate

        if (roomNightCount <= 0 || revenueAmount <= 0.0) return emptyList()

        val totalMinor = (revenueAmount * 100).roundToLong()
        val baseMinor = totalMinor / roomNightCount
        var remainder = totalMinor % roomNightCount
        val lines = mutableListOf<MiniFolioLine>()

        var nightStart = BusinessDates.startOfDay(booking.checkInMillis)
        repeat(stayNights) {
            bookedRoomIds.forEach { roomId ->
                val extraMinor = if (remainder > 0) {
                    remainder -= 1
                    1
                } else {
                    0
                }

                lines += MiniFolioLine(
                    bookingRemoteId = booking.remoteId,
                    businessDateMillis = nightStart,
                    type = MiniFolioLineType.ROOM_CHARGE,
                    kind = MiniFolioLineKind.CHARGE,
                    amount = (baseMinor + extraMinor) / 100.0,
                    description = "Room charge",
                    roomRemoteId = roomId,
                    accountBucket = BookingPaymentCategory.STAY
                )
            }
            nightStart += BusinessDates.DAY_MILLIS
        }

        return lines
    }

    private fun foodChargeLines(
        booking: BookingEntity,
        foodOrders: List<FoodOrderEntity>,
        foodOrderItems: List<FoodOrderItemEntity>
    ): List<MiniFolioLine> {
        val linkedOrders = foodOrders
            .filter {
                !it.isDeleted &&
                        it.bookingRemoteId == booking.remoteId &&
                        it.status != FoodOrderStatus.CANCELLED
            }
            .sortedBy { it.orderMillis }

        if (linkedOrders.isEmpty()) return emptyList()

        val itemsByOrder = foodOrderItems
            .filter { !it.isDeleted && !it.isCancelled }
            .groupBy { it.orderRemoteId }

        val lines = mutableListOf<MiniFolioLine>()

        linkedOrders.forEach { order ->
            val itemCount = itemsByOrder[order.remoteId].orEmpty()
                .sumOf { it.quantity }
                .roundToLong()
                .coerceAtLeast(1)

            val grossAmount = order.subtotal.takeIf { it > 0.0 }
                ?: order.totalAmount.takeIf { it > 0.0 }
                ?: order.taxableAmount

            if (grossAmount > 0.0) {
                lines += MiniFolioLine(
                    bookingRemoteId = booking.remoteId,
                    businessDateMillis = BusinessDates.startOfDay(order.orderMillis),
                    type = MiniFolioLineType.FOOD_CHARGE,
                    kind = MiniFolioLineKind.CHARGE,
                    amount = grossAmount,
                    description = "Food order ${order.orderNumber ?: order.remoteId.takeLast(6)} ($itemCount items)",
                    roomRemoteId = order.roomRemoteId,
                    accountBucket = BookingPaymentCategory.FOOD
                )
            }

            val discount = order.discountAmount.coerceAtLeast(0.0)

            if (discount > 0.0) {
                lines += MiniFolioLine(
                    bookingRemoteId = booking.remoteId,
                    businessDateMillis = BusinessDates.startOfDay(order.orderMillis),
                    type = MiniFolioLineType.DISCOUNT,
                    kind = MiniFolioLineKind.DISCOUNT,
                    amount = discount,
                    description = "Food Discount${order.notes?.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()}",
                    roomRemoteId = order.roomRemoteId,
                    accountBucket = BookingPaymentCategory.FOOD
                )
            }
        }

        return lines
    }

    private fun accountingChargeLines(
        booking: BookingEntity,
        accountingCharges: List<BookingAccountingChargeEntity>
    ): List<MiniFolioLine> {
        return accountingCharges
            .filter {
                !it.isDeleted &&
                        it.bookingRemoteId == booking.remoteId &&
                        it.amount > 0.0
            }
            .sortedBy { it.chargeMillis }
            .map { charge ->
                val type = BookingAccountingChargeType.normalize(charge.chargeType)
                MiniFolioLine(
                    bookingRemoteId = booking.remoteId,
                    businessDateMillis = BusinessDates.startOfDay(charge.chargeMillis),
                    type = when (type) {
                        BookingAccountingChargeType.DAMAGE_CHARGE -> MiniFolioLineType.DAMAGE_CHARGE
                        BookingAccountingChargeType.DISCOUNT -> MiniFolioLineType.DISCOUNT
                        else -> MiniFolioLineType.SERVICE_CHARGE
                    },
                    kind = when (type) {
                        BookingAccountingChargeType.DISCOUNT -> MiniFolioLineKind.DISCOUNT
                        else -> MiniFolioLineKind.CHARGE
                    },
                    amount = charge.amount,
                    description = charge.description,
                    accountBucket = when (type) {
                        BookingAccountingChargeType.DAMAGE_CHARGE -> BookingPaymentCategory.DAMAGE
                        BookingAccountingChargeType.DISCOUNT -> BookingPaymentCategory.normalize(charge.accountBucket)
                            .takeIf { it in setOf(BookingPaymentCategory.STAY, BookingPaymentCategory.FOOD, BookingPaymentCategory.SERVICE) }
                        else -> BookingPaymentCategory.SERVICE
                    }
                )
            }
    }

    private fun paymentLines(
        booking: BookingEntity,
        bookingPayments: List<BookingPaymentEntity>
    ): List<MiniFolioLine> {
        val validPayments = bookingPayments
            .filter { !it.isDeleted }
            .sortedBy { it.paymentMillis }

        if (validPayments.isNotEmpty()) {
            return validPayments.flatMap { payment ->
                payment.toFolioPaymentLines(booking.remoteId)
            }
        }

        // Backward compatibility for old bookings without payment rows.
        if (booking.paid > 0.0) {
            return listOf(
                MiniFolioLine(
                    bookingRemoteId = booking.remoteId,
                    businessDateMillis = BusinessDates.startOfDay(booking.updatedAt),
                    type = MiniFolioLineType.PAYMENT_RECEIVED,
                    kind = MiniFolioLineKind.PAYMENT,
                    amount = booking.paid,
                    description = "Payment received",
                    accountBucket = BookingPaymentCategory.STAY
                )
            )
        }

        return emptyList()
    }


    private fun BookingPaymentEntity.toFolioPaymentLines(bookingRemoteId: String): List<MiniFolioLine> {
        val sign = when (paymentType) {
            BookingPaymentType.REFUND,
            BookingPaymentType.ADJUSTMENT -> -1.0
            else -> 1.0
        }

        val bucket = BookingPaymentCategory.normalize(paymentCategory)
            .takeIf { it != BookingPaymentCategory.AUTO }
        val description = paymentDescription().withPaymentNote(this)

        return listOf(
            MiniFolioLine(
                bookingRemoteId = bookingRemoteId,
                businessDateMillis = BusinessDates.startOfDay(paymentMillis),
                type = MiniFolioLineType.PAYMENT_RECEIVED,
                kind = MiniFolioLineKind.PAYMENT,
                amount = sign * amount,
                description = description,
                accountBucket = bucket
            )
        )
    }

    private fun BookingPaymentEntity.paymentDescription(): String {
        return when (paymentType) {
            BookingPaymentType.ADVANCE -> "Advance received"
            BookingPaymentType.REFUND -> "Refund issued"
            BookingPaymentType.ADJUSTMENT -> "Payment correction"
            else -> "Payment received"
        }
    }

    private fun String.withPaymentNote(payment: BookingPaymentEntity): String {
        val methodText = payment.method?.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
        val noteText = payment.note?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        return this + methodText + noteText
    }
}
