package com.example.bookingregister.booking.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingPropertyPolicyTest {
    @Test
    fun `rooms from the same property are allowed`() {
        assertTrue(BookingPropertyPolicy.belongsToSingleProperty(listOf("property-a", "property-a")))
    }

    @Test
    fun `rooms from different properties are rejected`() {
        assertFalse(BookingPropertyPolicy.belongsToSingleProperty(listOf("property-a", "property-b")))
    }

    @Test
    fun `legacy main-property rooms can be booked together`() {
        assertTrue(BookingPropertyPolicy.belongsToSingleProperty(listOf(null, "", "  ")))
    }

    @Test
    fun `main-property and managed-property rooms are rejected together`() {
        assertFalse(BookingPropertyPolicy.belongsToSingleProperty(listOf(null, "property-a")))
    }
}
