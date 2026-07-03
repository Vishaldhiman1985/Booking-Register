package com.example.bookingregister

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentType
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderItemEntity

private const val DAY_MILLIS = 86_400_000L

fun authoritativeRoomLines(booking: BookingEntity): List<BookingFinancialLineEntity> {
    val rooms = booking.roomRemoteIds.distinct()
    val dates = generateSequence(booking.checkInMillis) { it + DAY_MILLIS }
        .takeWhile { it < booking.checkOutMillis }
        .toList()
    val keys = rooms.flatMap { room -> dates.map { date -> room to date } }
    if (keys.isEmpty()) return emptyList()
    val grossTotal = booking.grossCharges.takeIf { it > 0.0 }
        ?: booking.receivable.takeIf { it > 0.0 }
        ?: booking.rate
    return keys.mapIndexed { index, (room, date) ->
        val gross = if (index == keys.lastIndex) grossTotal - (grossTotal / keys.size) * index else grossTotal / keys.size
        BookingFinancialLineEntity(
            remoteId = "${booking.remoteId}_test_line_$index",
            hotelRemoteId = booking.hotelRemoteId,
            bookingRemoteId = booking.remoteId,
            roomRemoteId = room,
            businessDateMillis = date,
            grossAmount = gross,
            taxableAmount = gross,
            gstAmount = 0.0,
            hsnSacCode = "996311"
        )
    }
}

fun authoritativePaymentRows(booking: BookingEntity): List<BookingPaymentEntity> =
    if (booking.paid <= 0.0) emptyList() else listOf(
        BookingPaymentEntity(
            remoteId = "${booking.remoteId}_test_payment",
            hotelRemoteId = booking.hotelRemoteId,
            bookingRemoteId = booking.remoteId,
            paymentType = BookingPaymentType.PAYMENT,
            paymentCategory = BookingPaymentCategory.STAY,
            amount = booking.paid,
            allocatedStayAmount = booking.paid
        )
    )

fun authoritativeFoodItems(orders: List<FoodOrderEntity>): List<FoodOrderItemEntity> =
    orders.filter { !it.isDeleted }.map { order ->
        val gross = order.subtotal.takeIf { it > 0.0 } ?: order.totalAmount
        FoodOrderItemEntity(
            remoteId = "${order.remoteId}_test_item",
            hotelRemoteId = order.hotelRemoteId,
            orderRemoteId = order.remoteId,
            itemName = "Test food item",
            quantity = 1.0,
            unitPrice = gross,
            lineSubtotal = gross,
            lineTotal = order.totalAmount.takeIf { it > 0.0 } ?: order.subtotal
        )
    }
