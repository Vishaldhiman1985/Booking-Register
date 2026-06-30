package com.example.bookingregister.revenue.domain

import com.example.bookingregister.common.domain.BusinessDates
import com.example.bookingregister.common.domain.DateRange
import java.util.Calendar

object RevenuePeriod {
    fun financialYearFor(dateMillis: Long = System.currentTimeMillis()): DateRange {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = BusinessDates.startOfDay(dateMillis)
        }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val startYear = if (month >= Calendar.APRIL) year else year - 1

        val start = Calendar.getInstance().apply {
            clear()
            set(startYear, Calendar.APRIL, 1, 0, 0, 0)
        }
        val end = Calendar.getInstance().apply {
            clear()
            set(startYear + 1, Calendar.APRIL, 1, 0, 0, 0)
        }
        return DateRange(start.timeInMillis, end.timeInMillis)
    }

    fun month(year: Int, month: Int): DateRange {
        val start = Calendar.getInstance().apply {
            clear()
            set(year, month, 1, 0, 0, 0)
        }
        val end = Calendar.getInstance().apply {
            timeInMillis = start.timeInMillis
            add(Calendar.MONTH, 1)
        }
        return DateRange(start.timeInMillis, end.timeInMillis)
    }

    fun weekContaining(dateMillis: Long): DateRange {
        val start = Calendar.getInstance().apply {
            timeInMillis = BusinessDates.startOfDay(dateMillis)
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        return DateRange(start.timeInMillis, start.timeInMillis + 7 * BusinessDates.DAY_MILLIS)
    }
}

