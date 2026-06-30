package com.example.bookingregister.ui.views

import java.util.Calendar
import java.util.Date

class BookingDaysProvider(
    private val daysBefore: Int = 90,
    private val daysAfter: Int = 89
) {

    fun getDaysList(centerDate: Date = Date()): MutableList<Date> {
        val daysList = mutableListOf<Date>()

        val cal = Calendar.getInstance().apply {
            time = centerDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, -daysBefore)
        }

        repeat(daysBefore + daysAfter + 1) {
            daysList.add(cal.time)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        return daysList
    }

    fun getTodayIndex(daysList: List<Date>, today: Date = Date()): Int {
        val todayCal = Calendar.getInstance().apply {
            time = today
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return daysList.indexOfFirst { date ->
            val dCal = Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            dCal.timeInMillis == todayCal.timeInMillis
        }
    }

    fun getMonthStartIndex(daysList: List<Date>, year: Int, month: Int): Int {
        val cal = Calendar.getInstance()

        return daysList.indexOfFirst { date ->
            cal.time = date
            cal.get(Calendar.YEAR) == year &&
                    cal.get(Calendar.MONTH) == month
        }.takeIf { it >= 0 } ?: 0
    }
}
