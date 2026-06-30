package com.example.bookingregister.common.domain

data class DateRange(
    val startMillis: Long,
    val endMillis: Long
) {
    init {
        require(endMillis > startMillis) { "Date range end must be after start" }
    }
}