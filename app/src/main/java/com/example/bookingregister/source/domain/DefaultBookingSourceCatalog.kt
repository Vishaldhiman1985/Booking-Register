package com.example.bookingregister.source.domain

import com.example.bookingregister.data.entities.BookingSourceType

data class DefaultBookingSourceDefinition(
    val displayName: String,
    val sourceType: String
)

/**
 * Built-in booking-source suggestions for Indian hospitality businesses.
 *
 * This is reference data only:
 * - It does not create database records.
 * - It does not sync anything to Firebase.
 * - It contains no commission, GST, TCS, TDS or fixed-fee assumptions.
 * - Commercial values remain explicitly configured by an authorised hotel user.
 *
 * Hotels may always create a custom source that is not present in this catalog.
 */
object DefaultBookingSourceCatalog {

    val sources: List<DefaultBookingSourceDefinition> = listOf(
        // Direct channels
        DefaultBookingSourceDefinition("Walk-in", BookingSourceType.DIRECT),
        DefaultBookingSourceDefinition("Direct Call", BookingSourceType.DIRECT),
        DefaultBookingSourceDefinition("WhatsApp", BookingSourceType.DIRECT),
        DefaultBookingSourceDefinition("Hotel Website", BookingSourceType.DIRECT),
        DefaultBookingSourceDefinition("Email", BookingSourceType.DIRECT),
        DefaultBookingSourceDefinition("Repeat Guest", BookingSourceType.DIRECT),
        DefaultBookingSourceDefinition("Guest Referral", BookingSourceType.DIRECT),
        DefaultBookingSourceDefinition("Corporate Direct", BookingSourceType.DIRECT),
        DefaultBookingSourceDefinition("Google", BookingSourceType.DIRECT),
        DefaultBookingSourceDefinition("Facebook", BookingSourceType.DIRECT),
        DefaultBookingSourceDefinition("Instagram", BookingSourceType.DIRECT),

        // Online travel agencies / online booking marketplaces
        DefaultBookingSourceDefinition("MakeMyTrip", BookingSourceType.OTA),
        DefaultBookingSourceDefinition("Goibibo", BookingSourceType.OTA),
        DefaultBookingSourceDefinition("Booking.com", BookingSourceType.OTA),
        DefaultBookingSourceDefinition("Agoda", BookingSourceType.OTA),
        DefaultBookingSourceDefinition("Airbnb", BookingSourceType.OTA),
        DefaultBookingSourceDefinition("Expedia", BookingSourceType.OTA),
        DefaultBookingSourceDefinition("Hotels.com", BookingSourceType.OTA),
        DefaultBookingSourceDefinition("Cleartrip", BookingSourceType.OTA),
        DefaultBookingSourceDefinition("Yatra", BookingSourceType.OTA),
        DefaultBookingSourceDefinition("EaseMyTrip", BookingSourceType.OTA),
        DefaultBookingSourceDefinition("Trip.com", BookingSourceType.OTA),
        DefaultBookingSourceDefinition("ixigo", BookingSourceType.OTA),

        // B2B / agent channels
        DefaultBookingSourceDefinition("Travel Agent", BookingSourceType.AGENT),
        DefaultBookingSourceDefinition("Tour Operator / DMC", BookingSourceType.AGENT),
        DefaultBookingSourceDefinition("Corporate Agent", BookingSourceType.AGENT),
        DefaultBookingSourceDefinition("TBO Holidays", BookingSourceType.AGENT),
        DefaultBookingSourceDefinition("Hotelbeds", BookingSourceType.AGENT)
    )

    fun findByName(name: String): DefaultBookingSourceDefinition? {
        val normalized = name.trim()
        if (normalized.isEmpty()) return null

        return sources.firstOrNull {
            it.displayName.equals(normalized, ignoreCase = true)
        }
    }
}