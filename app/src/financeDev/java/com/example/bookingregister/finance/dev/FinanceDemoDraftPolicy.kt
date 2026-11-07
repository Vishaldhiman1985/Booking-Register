package com.example.bookingregister.finance.dev

import com.bookingregister.finance.domain.CurrencyCode
import com.bookingregister.finance.domain.Money

/** Explicitly fictional fixtures; this preview must never ingest real hotel data. */
internal object FinanceDemoDraftPolicy {
    const val ENVIRONMENT = "FINANCE_DEV"
    const val TENANT = "DEMO_TENANT_ONLY"
    const val HOTEL = "DEMO_HOTEL_ONLY"
    const val PROPERTY = "DEMO_PROPERTY_ONLY"
    const val STATUS = "LOCAL_DRAFT_NOT_POSTED"

    data class Fixture(val code: String, val category: String, val vendor: String, val paise: Long)

    val fixtures: List<Fixture> = listOf(
        Fixture("demo-laundry", "Laundry", "Demo Laundry Supplier", 12_500_00L),
        Fixture("demo-rent", "Lease / Rent", "Demo Landlord", 2_50_000_00L),
        Fixture("demo-maintenance", "Maintenance", "Demo Maintenance Vendor", 8_750_50L)
    )

    fun create(code: String, id: String, nowMillis: Long): FinanceDemoDraftBill {
        val fixture = fixtures.singleOrNull { it.code == code }
            ?: throw IllegalArgumentException("Unknown synthetic fixture")
        require(id.isNotBlank() && nowMillis > 0L) { "Missing draft identity or timestamp" }
        return FinanceDemoDraftBill(
            draftId = id,
            environment = ENVIRONMENT,
            tenantId = TENANT,
            hotelId = HOTEL,
            propertyId = PROPERTY,
            vendorCode = fixture.code,
            category = fixture.category,
            invoiceDate = "2026-11-01",
            accountingPeriod = "2026-11",
            currencyCode = CurrencyCode.INR.name,
            amountPaise = fixture.paise,
            status = STATUS,
            createdAtMillis = nowMillis
        )
    }

    fun validate(row: FinanceDemoDraftBill) {
        require(row.environment == ENVIRONMENT && row.tenantId == TENANT &&
            row.hotelId == HOTEL && row.propertyId == PROPERTY) { "Not a synthetic Finance DEV record" }
        require(row.currencyCode == CurrencyCode.INR.name && row.status == STATUS) {
            "Unknown draft currency or status"
        }
        require(row.amountPaise > 0 && fixtures.any {
            it.code == row.vendorCode && it.category == row.category && it.paise == row.amountPaise
        }) { "Only fixed fictional samples are allowed" }
        require(row.invoiceDate == "2026-11-01" && row.accountingPeriod == "2026-11") {
            "Unexpected sample accounting period"
        }
    }

    fun moneyText(paise: Long): String = "₹" + Money(CurrencyCode.INR, paise).toMajorUnitsText()
}
