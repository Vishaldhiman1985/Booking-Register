package com.example.bookingregister.billing.domain

import com.example.bookingregister.data.entities.HotelEntity
import com.example.bookingregister.data.entities.ManagedPropertyEntity
import java.util.Calendar
import java.util.Locale

data class BillingProfileSnapshot(
    val supplierName: String?,
    val supplierGstin: String?,
    val supplierAddress: String?,
    val supplierPhone: String?,
    val supplierState: String?,
    val propertyDisplayName: String?,
    val invoicePrefix: String
)

object InvoiceNumberPolicy {
    private const val DEFAULT_PREFIX = "INV"

    fun resolveBillingProfile(
        organization: HotelEntity?,
        property: ManagedPropertyEntity?,
        fallbackPrefix: String = DEFAULT_PREFIX
    ): BillingProfileSnapshot {
        val propertyPrefix = property?.invoicePrefix?.trim()?.takeIf { it.isNotBlank() }
        val prefix = normalizePrefix(propertyPrefix ?: fallbackPrefix)

        return BillingProfileSnapshot(
            supplierName = property?.legalName?.takeIf { it.isNotBlank() }
                ?: property?.propertyName
                ?: organization?.hotelName,
            supplierGstin = property?.gstNumber?.trim()?.takeIf { it.isNotBlank() },
            supplierAddress = property?.address ?: organization?.address,
            supplierPhone = property?.phone ?: organization?.phone,
            supplierState = property?.state,
            propertyDisplayName = property?.propertyName,
            invoicePrefix = prefix
        )
    }

    fun financialYearShort(timeMillis: Long): String {
        val calendar = Calendar.getInstance(Locale.US).apply { timeInMillis = timeMillis }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val startYear = if (month >= Calendar.APRIL) year else year - 1
        val endYear = startYear + 1
        return "${startYear % 100}-${endYear % 100}"
    }

    fun seriesPrefix(prefix: String, timeMillis: Long): String {
        return "${normalizePrefix(prefix)}/${financialYearShort(timeMillis)}/"
    }

    fun nextInvoiceNumber(
        prefix: String,
        timeMillis: Long,
        existingBillNumbers: List<String>
    ): String {
        val series = seriesPrefix(prefix, timeMillis)
        val next = (existingBillNumbers.mapNotNull { billNumber ->
            billNumber.takeIf { it.startsWith(series) }
                ?.substringAfterLast("/")
                ?.toIntOrNull()
        }.maxOrNull() ?: 0) + 1
        return "$series${next.toString().padStart(4, '0')}"
    }

    private fun normalizePrefix(prefix: String): String {
        val cleaned = prefix
            .trim()
            .uppercase(Locale.ROOT)
            .replace(Regex("[^A-Z0-9-]"), "")
            .take(12)
        return cleaned.ifBlank { DEFAULT_PREFIX }
    }
}
