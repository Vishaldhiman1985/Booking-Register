package com.example.bookingregister.finalbill.domain

import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.folio.domain.MiniFolioLineKind
import com.example.bookingregister.folio.domain.MiniFolioLineType
import kotlin.math.abs

object FinalBillTextFormatter {
    fun formatFinalSettlement(preview: FinalBillPreview): String = buildString {
        appendLine("Final Settlement")
        appendLine()

        appendLine("Guest: ${preview.guestName}")
        appendLine("Rooms: ${preview.roomNames}")
        appendLine()

        appendRoomDeal(preview)
        appendLine()

        appendLine("Food Orders")
        appendFoodTotals(preview)
        if (preview.foodOrders.isNotEmpty()) {
            preview.foodOrders.forEach { order ->
                appendLine("  ${order.orderNumber}: Rs ${amountText(order.totalAmount)}")
            }
        }
        appendLine()

        appendServicesSummary(preview)
        appendLine()

        appendPaymentSummary(preview)
        appendLine()

        appendLedgerEntries(preview, useAbsoluteAmount = false)
    }

    fun formatBalanceLedger(preview: FinalBillPreview): String = buildString {
        appendLine("Balance Ledger")
        appendLine()

        appendRoomDeal(preview)
        appendLine()

        appendLine("Food Orders")
        if (preview.foodOrders.isEmpty()) {
            appendLine("No food orders linked")
        } else {
            preview.foodOrders.forEach { order ->
                appendLine("${order.orderNumber}: Rs ${amountText(order.totalAmount)}")
            }
        }
        appendFoodTotals(preview)
        appendLine()

        appendServicesSummary(preview)
        appendLine()

        appendPaymentSummary(preview)
        appendLedgerEntries(preview, useAbsoluteAmount = true, leadingBlankLine = true)
    }


    fun formatBalanceSummary(preview: FinalBillPreview): String = buildString {
        appendRoomDeal(preview)
        appendLine()

        appendLine("Food Orders")
        if (preview.foodOrders.isEmpty()) {
            appendLine("No food orders linked")
        } else {
            preview.foodOrders.forEach { order ->
                appendLine("${order.orderNumber}: Rs ${amountText(order.totalAmount)}")
            }
        }
        appendFoodTotals(preview)
        appendLine()

        appendServicesSummary(preview)
        appendLine()

        appendPaymentSummary(preview)
    }

    fun formatLedgerEntries(preview: FinalBillPreview, useAbsoluteAmount: Boolean = true): String = buildString {
        appendLedgerEntries(preview, useAbsoluteAmount = useAbsoluteAmount)
    }

    fun amountText(value: Double?): String {
        val amount = value ?: 0.0
        return if (amount == 0.0) {
            "0"
        } else if (amount % 1.0 == 0.0) {
            amount.toLong().toString()
        } else {
            "%.2f".format(amount)
        }
    }

    private fun StringBuilder.appendRoomDeal(preview: FinalBillPreview) {
        val title = if (preview.isOta()) "OTA Room Settlement" else "Room Deal"
        appendLine(title)

        if (preview.roomItems.isNotEmpty()) {
            preview.roomItems.forEach { item ->
                appendLine(
                    "${item.roomName} | ${amountText(item.grossAmount)} gross | GST ${amountText(item.gstRatePercent)}% | Tax ${amountText(item.gstAmount)}"
                )
            }
            appendLine("Room Final Price: Rs ${amountText(preview.roomCharges)}")
        } else {
            appendLine("Room Final Price: Rs ${amountText(preview.roomCharges)}")
        }

        if (preview.roomDiscount > 0.01) {
            appendLine("Room Discount: Rs ${amountText(preview.roomDiscount)}")
            appendLine("Room Net: Rs ${amountText((preview.roomCharges - preview.roomDiscount).coerceAtLeast(0.0))}")
        }

        if (preview.isOta()) {
            appendLine("OTA Payout Received: Rs ${amountText(preview.roomPaid)}")
            appendLine("OTA Receivable: Rs ${amountText(preview.roomBalance)}")
        } else {
            appendLine("Room Paid: Rs ${amountText(preview.roomPaid)}")
            appendLine("Room Balance: Rs ${amountText(preview.roomBalance)}")
        }
    }

    private fun StringBuilder.appendFoodTotals(preview: FinalBillPreview) {
        appendLine("Food Total: Rs ${amountText(preview.foodTotal)}")
        if (preview.foodDiscount > 0.01) {
            appendLine("Food Discount: Rs ${amountText(preview.foodDiscount)}")
            appendLine("Food Net: Rs ${amountText((preview.foodTotal - preview.foodDiscount).coerceAtLeast(0.0))}")
        }
        appendLine("Food Paid: Rs ${amountText(preview.foodPaid)}")
        appendLine("Food Balance: Rs ${amountText(preview.foodBalance)}")
    }

    private fun StringBuilder.appendPaymentSummary(preview: FinalBillPreview) {
        appendLine("Payment Summary")
        appendLine("Grand Total: Rs ${amountText(preview.totalCharges)}")
        appendLine("Total Paid: Rs ${amountText(preview.totalPaid)}")

        val guestCredit = (preview.totalPaid - preview.totalCharges).coerceAtLeast(0.0)

        if (preview.isOta()) {
            appendLine("Guest Checkout Balance: Rs ${amountText(preview.guestCheckoutBalance)}")
            appendLine("OTA Receivable: Rs ${amountText(preview.roomBalance)}")
            if (guestCredit > 0.01) {
                appendLine("Guest Credit: Rs ${amountText(guestCredit)}")
            }
        } else {
            appendLine("Balance: Rs ${amountText(preview.folioBalance)}")
            if (guestCredit > 0.01) {
                appendLine("Guest Credit: Rs ${amountText(guestCredit)}")
            }
        }
    }

    private fun StringBuilder.appendServicesSummary(preview: FinalBillPreview) {
        val serviceCharges = preview.folioLines.filter {
            it.type == MiniFolioLineType.SERVICE_CHARGE ||
                    it.type == MiniFolioLineType.DAMAGE_CHARGE
        }
        val serviceTotal = serviceCharges.sumOf { it.amount }

        appendLine("Other Services")
        if (serviceCharges.isEmpty()) {
            appendLine("No services linked")
        } else {
            serviceCharges.forEach { charge ->
                appendLine("${charge.description}: Rs ${amountText(charge.amount)}")
            }
        }
        appendLine("Service Total: Rs ${amountText(serviceTotal)}")
        if (preview.serviceDiscount > 0.01) {
            appendLine("Service Discount: Rs ${amountText(preview.serviceDiscount)}")
            appendLine("Service Net: Rs ${amountText((serviceTotal - preview.serviceDiscount).coerceAtLeast(0.0))}")
        }
        appendLine("Service Paid: Rs ${amountText(preview.servicePaid + preview.damagePaid)}")
        appendLine("Service Balance: Rs ${amountText(preview.serviceBalance + preview.damageBalance)}")

        val generalDiscount = preview.discounts - preview.roomDiscount - preview.foodDiscount - preview.serviceDiscount
        if (generalDiscount > 0.01) {
            appendLine("Other Discounts: Rs ${amountText(generalDiscount)}")
        }
    }

    private fun FinalBillPreview.isOta(): Boolean = sourceType == BookingSourceType.OTA

    private fun StringBuilder.appendLedgerEntries(
        preview: FinalBillPreview,
        useAbsoluteAmount: Boolean,
        leadingBlankLine: Boolean = false
    ) {
        if (preview.folioLines.isEmpty()) return
        if (leadingBlankLine) appendLine()

        appendLine("Ledger Entries")

        preview.folioLines.forEach { line ->
            val sign = when (line.kind) {
                MiniFolioLineKind.CHARGE -> "+"
                MiniFolioLineKind.PAYMENT -> "-"
                MiniFolioLineKind.DISCOUNT -> "-"
                MiniFolioLineKind.REFUND -> "+"
                MiniFolioLineKind.ADJUSTMENT -> if (line.amount >= 0) "+" else "-"
                else -> "+"
            }

            appendLine("$sign Rs ${amountText(abs(line.amount))}  ${line.description}")
        }
    }
}
