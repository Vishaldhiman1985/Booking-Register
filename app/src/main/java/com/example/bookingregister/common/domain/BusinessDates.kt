package com.example.bookingregister.common.domain

import java.util.Calendar

object BusinessDates {
    const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

    fun todayRange(): DateRange {
        val start = startOfDay(System.currentTimeMillis())
        return DateRange(start, start + DAY_MILLIS)
    }

    fun weekToDateRange(): DateRange {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = startOfDay(System.currentTimeMillis())
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        val start = startOfDay(calendar.timeInMillis)
        return DateRange(start, start + 7 * DAY_MILLIS)
    }

    fun monthToDateRange(): DateRange {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = startOfDay(System.currentTimeMillis())
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val start = startOfDay(calendar.timeInMillis)
        calendar.add(Calendar.MONTH, 1)
        return DateRange(start, startOfDay(calendar.timeInMillis))
    }

    fun startOfDay(millis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun overlapNights(checkInMillis: Long, checkOutMillis: Long, range: DateRange): Int {
        val start = maxOf(startOfDay(checkInMillis), range.startMillis)
        val end = minOf(startOfDay(checkOutMillis), range.endMillis)
        if (end <= start) return 0
        return ((end - start) / DAY_MILLIS).toInt()
    }

    fun stayNights(checkInMillis: Long, checkOutMillis: Long): Int {
        val nights = (startOfDay(checkOutMillis) - startOfDay(checkInMillis)) / DAY_MILLIS
        return nights.toInt().coerceAtLeast(1)
    }

    fun rangeDays(range: DateRange): Int {
        return ((range.endMillis - range.startMillis) / DAY_MILLIS).toInt().coerceAtLeast(1)
    }
}
