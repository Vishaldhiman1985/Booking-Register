package com.example.bookingregister.booking.domain

object BookingPropertyPolicy {
    private const val MAIN_PROPERTY_KEY = "__MAIN_PROPERTY__"

    fun propertyKey(propertyRemoteId: String?): String =
        propertyRemoteId?.trim()?.takeIf { it.isNotEmpty() } ?: MAIN_PROPERTY_KEY

    fun belongsToSingleProperty(propertyRemoteIds: Iterable<String?>): Boolean =
        propertyRemoteIds.map(::propertyKey).distinct().size <= 1
}
