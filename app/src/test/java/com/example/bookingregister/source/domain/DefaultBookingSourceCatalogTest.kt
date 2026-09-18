package com.example.bookingregister.source.domain

import com.example.bookingregister.data.entities.BookingSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultBookingSourceCatalogTest {

    @Test
    fun containsExpectedCoreIndianHospitalitySources() {
        val names = DefaultBookingSourceCatalog.sources.map { it.displayName }

        assertTrue("Walk-in" in names)
        assertTrue("MakeMyTrip" in names)
        assertTrue("Goibibo" in names)
        assertTrue("Booking.com" in names)
        assertTrue("Agoda" in names)
        assertTrue("Airbnb" in names)
        assertTrue("Cleartrip" in names)
        assertTrue("Travel Agent" in names)
    }

    @Test
    fun sourceNamesAreUniqueIgnoringCaseAndWhitespace() {
        val normalizedNames = DefaultBookingSourceCatalog.sources
            .map { it.displayName.trim().lowercase() }

        assertEquals(
            "Built-in booking-source names must remain unique.",
            normalizedNames.size,
            normalizedNames.toSet().size
        )
    }

    @Test
    fun everyCatalogEntryHasAUsableNameAndSupportedType() {
        assertFalse(DefaultBookingSourceCatalog.sources.isEmpty())

        val supportedTypes = setOf(
            BookingSourceType.DIRECT,
            BookingSourceType.OTA,
            BookingSourceType.AGENT
        )

        assertTrue(
            DefaultBookingSourceCatalog.sources.all {
                it.displayName.isNotBlank() && it.sourceType in supportedTypes
            }
        )
    }

    @Test
    fun knownSourceLookupIsCaseInsensitiveAndTrimmed() {
        val source = DefaultBookingSourceCatalog.findByName("  makeMYtrip  ")

        assertNotNull(source)
        assertEquals("MakeMyTrip", source?.displayName)
        assertEquals(BookingSourceType.OTA, source?.sourceType)
    }

    @Test
    fun unknownSourceIsNotInventedByCatalog() {
        assertNull(DefaultBookingSourceCatalog.findByName("My Local Agent"))
    }

    @Test
    fun blankSourceNameDoesNotResolve() {
        assertNull(DefaultBookingSourceCatalog.findByName("   "))
    }

    @Test
    fun coreSourceTypesAreCorrect() {
        assertEquals(
            BookingSourceType.DIRECT,
            DefaultBookingSourceCatalog.findByName("Walk-in")?.sourceType
        )
        assertEquals(
            BookingSourceType.OTA,
            DefaultBookingSourceCatalog.findByName("Agoda")?.sourceType
        )
        assertEquals(
            BookingSourceType.AGENT,
            DefaultBookingSourceCatalog.findByName("Travel Agent")?.sourceType
        )
    }
}