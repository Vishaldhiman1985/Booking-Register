package com.example.bookingregister.revenue.domain

import com.example.bookingregister.common.domain.BusinessDates
import com.example.bookingregister.common.domain.DateRange

class RevenueCalculator {
    fun summarize(entries: List<RevenueLedgerEntry>, range: DateRange): RevenueSummary {
        val scoped = entries.filter { it.businessDateMillis in range.startMillis until range.endMillis }

        val roomRevenue = scoped.sumByType(RevenueLedgerEntryType.ROOM_REVENUE)
        val payments = scoped.sumByType(RevenueLedgerEntryType.PAYMENT_RECEIVED)
        val discounts = scoped.sumByType(RevenueLedgerEntryType.DISCOUNT)
        val refunds = scoped.sumByType(RevenueLedgerEntryType.REFUND)
        val adjustments = scoped.sumByType(RevenueLedgerEntryType.ADJUSTMENT)

        return RevenueSummary(
            roomRevenue = roomRevenue,
            paymentsReceived = payments,
            discounts = discounts,
            refunds = refunds,
            adjustments = adjustments,
            pendingBalance = (roomRevenue - discounts + adjustments - payments + refunds).coerceAtLeast(0.0)
        )
    }

    fun daily(entries: List<RevenueLedgerEntry>, range: DateRange): List<DailyRevenueSummary> {
        val byDay = entries
            .filter { it.businessDateMillis in range.startMillis until range.endMillis }
            .groupBy { BusinessDates.startOfDay(it.businessDateMillis) }

        val days = mutableListOf<DailyRevenueSummary>()
        var day = BusinessDates.startOfDay(range.startMillis)
        while (day < range.endMillis) {
            val dayEntries = byDay[day].orEmpty()
            val roomRevenue = dayEntries.sumByType(RevenueLedgerEntryType.ROOM_REVENUE)
            val payments = dayEntries.sumByType(RevenueLedgerEntryType.PAYMENT_RECEIVED)
            days += DailyRevenueSummary(
                businessDateMillis = day,
                roomRevenue = roomRevenue,
                paymentsReceived = payments,
                pendingBalance = (roomRevenue - payments).coerceAtLeast(0.0)
            )
            day += BusinessDates.DAY_MILLIS
        }
        return days
    }

    private fun List<RevenueLedgerEntry>.sumByType(type: String): Double {
        return filter { it.type == type }.sumOf { it.amount }
    }
}
