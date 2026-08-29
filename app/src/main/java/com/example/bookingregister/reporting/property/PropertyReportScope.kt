package com.example.bookingregister.reporting.property

import com.example.bookingregister.booking.domain.BookingPropertyPolicy

/**
 * Read-only reporting scope. It never mutates booking, billing, payment or sync state.
 *
 * propertyRemoteId == null with includeAllProperties == false means the legacy/main property.
 * includeAllProperties == true means a consolidated organization view.
 */
data class PropertyReportScope(
    val hotelRemoteId: String,
    val propertyRemoteId: String?,
    val includeAllProperties: Boolean,
    val startMillis: Long,
    val endMillis: Long
) {
    init {
        require(hotelRemoteId.isNotBlank()) { "Hotel id is required for reporting." }
        require(endMillis > startMillis) { "Report end must be after report start." }
    }

    fun matchesProperty(candidatePropertyRemoteId: String?): Boolean {
        if (includeAllProperties) return true
        return BookingPropertyPolicy.propertyKey(candidatePropertyRemoteId) ==
            BookingPropertyPolicy.propertyKey(propertyRemoteId)
    }
}
