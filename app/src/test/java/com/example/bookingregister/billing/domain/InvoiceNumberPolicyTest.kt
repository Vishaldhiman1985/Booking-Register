package com.example.bookingregister.billing.domain

import com.example.bookingregister.data.entities.HotelEntity
import com.example.bookingregister.data.entities.ManagedPropertyEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class InvoiceNumberPolicyTest {

    @Test
    fun `next invoice number advances inside same prefix and financial year`() {
        val time = millis(2026, Calendar.JUNE, 24)

        val next = InvoiceNumberPolicy.nextInvoiceNumber(
            prefix = "WL",
            timeMillis = time,
            existingBillNumbers = listOf(
                "WL/26-27/0001",
                "WL/26-27/0002",
                "HB/26-27/0009",
                "WL/25-26/0040"
            )
        )

        assertEquals("WL/26-27/0003", next)
    }

    @Test
    fun `financial year starts in april`() {
        assertEquals("25-26", InvoiceNumberPolicy.financialYearShort(millis(2026, Calendar.MARCH, 31)))
        assertEquals("26-27", InvoiceNumberPolicy.financialYearShort(millis(2026, Calendar.APRIL, 1)))
    }

    @Test
    fun `property with own gst becomes bill seller`() {
        val organization = HotelEntity(
            remoteId = "org_1",
            hotelName = "Wildleaf",
            gstNumber = "ORG-GST",
            address = "Org Address",
            phone = "999"
        )
        val property = ManagedPropertyEntity(
            remoteId = "hotel_a",
            hotelRemoteId = "org_1",
            propertyName = "Hotel A",
            legalName = "Hotel A Private Limited",
            gstNumber = "HOTEL-GST",
            invoicePrefix = "HA"
        )

        val profile = InvoiceNumberPolicy.resolveBillingProfile(organization, property)

        assertEquals("Hotel A Private Limited", profile.supplierName)
        assertEquals("HOTEL-GST", profile.supplierGstin)
        assertEquals("HA", profile.invoicePrefix)
    }

    @Test
    fun `property without gst stays property seller and does not fallback to organization gst`() {
        val organization = HotelEntity(
            remoteId = "org_1",
            hotelName = "Wildleaf",
            gstNumber = "ORG-GST",
            address = "Org Address",
            phone = "999"
        )
        val property = ManagedPropertyEntity(
            remoteId = "hotel_a",
            hotelRemoteId = "org_1",
            propertyName = "Hotel A",
            legalName = "Hotel A Private Limited",
            gstNumber = null,
            invoicePrefix = "WL"
        )

        val profile = InvoiceNumberPolicy.resolveBillingProfile(organization, property)

        assertEquals("Hotel A Private Limited", profile.supplierName)
        assertEquals(null, profile.supplierGstin)
        assertEquals("Hotel A", profile.propertyDisplayName)
        assertEquals("WL", profile.invoicePrefix)
    }

    private fun millis(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance(Locale.US).apply {
            clear()
            set(year, month, day)
        }.timeInMillis
    }
}
