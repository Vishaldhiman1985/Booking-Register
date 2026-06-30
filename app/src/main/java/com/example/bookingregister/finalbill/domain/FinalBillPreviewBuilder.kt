package com.example.bookingregister.finalbill.domain

import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderItemEntity
import com.example.bookingregister.data.entities.FoodOrderStatus
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.folio.domain.FolioSnapshotBuilder
import com.example.bookingregister.folio.domain.MiniFolioBuilder
import com.example.bookingregister.tax.domain.FoodGstCalculator
import kotlin.math.roundToInt
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
class FinalBillPreviewBuilder {
    private val miniFolioBuilder = MiniFolioBuilder()
    private val snapshotBuilder = FolioSnapshotBuilder()
    private val foodGstCalculator = FoodGstCalculator()

    fun build(
        booking: BookingEntity,
        rooms: List<RoomEntity>,
        bookingPayments: List<BookingPaymentEntity> = emptyList(),
        bookingFinancialLines: List<BookingFinancialLineEntity> = emptyList(),
        accountingCharges: List<BookingAccountingChargeEntity> = emptyList(),
        foodOrders: List<FoodOrderEntity>,
        foodOrderItems: List<FoodOrderItemEntity>
    ): FinalBillPreview {
        val linkedOrders = foodOrders
            .filter { it.isLedgerFoodFor(booking.remoteId) }
            .sortedBy { it.orderMillis }

        val itemsByOrder = foodOrderItems
            .filter { !it.isDeleted && !it.isCancelled }
            .groupBy { it.orderRemoteId }

        val roomNameById = rooms.associate { it.remoteId to it.roomName }
        val roomItemPreviews = bookingFinancialLines
            .filter { !it.isDeleted && it.bookingRemoteId == booking.remoteId }
            .sortedWith(
                compareBy<BookingFinancialLineEntity> { it.businessDateMillis }
                    .thenBy { roomNameById[it.roomRemoteId].orEmpty() }
            )
            .map { line ->
                FinalBillRoomItemPreview(
                    roomName = roomNameById[line.roomRemoteId] ?: "Room",
                    businessDateMillis = line.businessDateMillis,
                    grossAmount = line.grossAmount,
                    taxableAmount = line.taxableAmount,
                    gstRatePercent = line.gstRatePercent,
                    gstAmount = line.gstAmount,
                    totalAmount = line.grossAmount,
                    hsnSacCode = null
                )
            }

        val bookingRoomNames = booking.roomRemoteIds
            .mapNotNull { roomNameById[it] }
            .joinToString(", ")
            .ifBlank { "Rooms not selected" }

        val activeRoomIds = rooms
            .filter { !it.isDeleted }
            .map { it.remoteId }
            .toSet()

        val snapshot = snapshotBuilder.build(
            booking = booking,
            activeRoomIds = activeRoomIds,
            payments = bookingPayments,
            accountingCharges = accountingCharges,
            foodOrders = foodOrders,
            foodOrderItems = foodOrderItems,
            bookingFinancialLines = bookingFinancialLines
        )

        val folioLines = snapshot.lines
        val roomCharges = snapshot.room.charge
        val foodCharges = snapshot.food.charge
        val extraCharges = snapshot.service.charge + snapshot.damage.charge
        val roomDiscount = snapshot.room.discount
        val foodDiscount = snapshot.food.discount
        val serviceDiscount = snapshot.service.discount
        val discounts = snapshot.totalDiscount

        val orderPreviews = linkedOrders.map { order ->
            val orderItems = itemsByOrder[order.remoteId].orEmpty()
            FinalBillFoodOrderPreview(
                orderRemoteId = order.remoteId,
                orderNumber = order.orderNumber ?: "Food order ${order.remoteId.takeLast(6)}",
                roomName = resolveRoomName(order, roomNameById),
                orderMillis = order.orderMillis,
                totalAmount = order.totalAmount.takeIf { it > 0.0 } ?: order.subtotal,
                itemCount = orderItems.sumOf { it.quantity }.roundToInt().coerceAtLeast(orderItems.size)
            )
        }

        val foodDiscountRatio = if (foodCharges > 0.0) {
            (foodDiscount / foodCharges).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val foodItemPreviews = linkedOrders.flatMap { order ->
            val roomName = resolveRoomName(order, roomNameById)
            itemsByOrder[order.remoteId].orEmpty().map { item ->
                val grossLineTotal = item.lineTotal.takeIf { it > 0.0 }
                    ?: item.lineSubtotal.takeIf { it > 0.0 }
                    ?: item.quantity * item.unitPrice

                val gstRate = item.gstRatePercent.coerceAtLeast(0.0)

                val gstBreakdown = foodGstCalculator.calculateDiscountedInclusive(
                    grossAmount = grossLineTotal,
                    discountRatio = foodDiscountRatio,
                    gstRatePercent = item.gstRatePercent,
                    cgstRatePercent = null,
                    sgstRatePercent = null,
                    cessRatePercent = 0.0,
                    withGst = item.gstRatePercent > 0.0
                )

                FinalBillFoodItemPreview(
                    orderRemoteId = order.remoteId,
                    roomName = roomName,
                    itemName = item.itemName,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    taxableAmount = gstBreakdown.taxableAmount,
                    gstRatePercent = gstBreakdown.gstRatePercent,
                    gstAmount = gstBreakdown.totalTaxAmount,
                    totalAmount = gstBreakdown.lineTotal
                )
            }
        }

        val gstSummaries = foodItemPreviews
            .groupBy { it.gstRatePercent }
            .map { (rate, items) ->
                val taxable = items.sumOf { it.taxableAmount }
                val gst = items.sumOf { it.gstAmount }
                FinalBillGstSummary(
                    gstRatePercent = rate,
                    taxableAmount = taxable,
                    cgstAmount = gst / 2.0,
                    sgstAmount = gst / 2.0,
                    totalGstAmount = gst
                )
            }
            .sortedBy { it.gstRatePercent }

        val otaReceivable = booking.expectedPayout.takeIf { booking.sourceType == BookingSourceType.OTA && it > 0.0 }
            ?: roomCharges
        val roomBalance = if (booking.sourceType == BookingSourceType.OTA) {
            (otaReceivable - roomDiscount - snapshot.room.paid).coerceAtLeast(0.0)
        } else {
            snapshot.room.balance
        }

        return FinalBillPreview(
            bookingRemoteId = booking.remoteId,
            guestName = booking.guestName.ifBlank { "Guest" },
            roomNames = bookingRoomNames,
            sourceName = booking.sourceName ?: "Walk-in",
            sourceType = booking.sourceType,

            roomCharges = roomCharges,
            roomPaid = snapshot.room.paid,
            roomBalance = roomBalance,
            roomItems = roomItemPreviews,

            foodOrders = orderPreviews,
            foodItems = foodItemPreviews,
            gstSummaries = gstSummaries,

            foodSubtotal = foodItemPreviews.sumOf { it.taxableAmount },
            foodGst = foodItemPreviews.sumOf { it.gstAmount },
            foodTotal = foodCharges,
            foodPaid = snapshot.food.paid,
            foodBalance = snapshot.food.balance,

            extraTotal = extraCharges,
            servicePaid = snapshot.service.paid,
            serviceBalance = snapshot.service.balance,
            damagePaid = snapshot.damage.paid,
            damageBalance = snapshot.damage.balance,
            roomDiscount = roomDiscount,
            foodDiscount = foodDiscount,
            serviceDiscount = serviceDiscount,
            discounts = discounts,

            totalCharges = snapshot.netCharges,
            totalPaid = snapshot.totalPaid,
            folioBalance = snapshot.folioBalance,

            folioLines = folioLines,
            checkoutBalanceOverride = if (booking.sourceType == BookingSourceType.OTA) {
                snapshot.guestCheckoutBalance
            } else {
                null
            }
        )
    }

    private fun FoodOrderEntity.isLedgerFoodFor(bookingRemoteId: String): Boolean {
        return !isDeleted &&
                bookingRemoteId == this.bookingRemoteId &&
                status != FoodOrderStatus.CANCELLED
    }

    private fun resolveRoomName(order: FoodOrderEntity, roomNameById: Map<String, String>): String {
        return order.roomName?.takeIf { it.isNotBlank() }
            ?: order.roomRemoteId?.let { roomNameById[it] }?.takeIf { it.isNotBlank() }
            ?: "Room"
    }
}
