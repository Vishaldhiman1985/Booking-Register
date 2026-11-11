package com.example.bookingregister.finance.dev

import com.bookingregister.finance.domain.CurrencyCode
import com.bookingregister.finance.domain.Money
import java.time.LocalDate
import java.time.YearMonth

/** Explicitly fictional data rules; this offline preview must never ingest real hotel data. */
internal object FinanceDemoDraftPolicy {
    const val ENVIRONMENT = "FINANCE_DEV"
    const val TENANT = "DEMO_TENANT_ONLY"
    const val HOTEL = "DEMO_HOTEL_ONLY"
    const val PROPERTY = "DEMO_PROPERTY_ONLY"
    const val STATUS_ACTIVE = "LOCAL_DRAFT_NOT_POSTED"
    const val STATUS_DISCARDED = "LOCAL_DRAFT_DISCARDED_NOT_POSTED"
    const val EVENT_CREATED = "CREATED"
    const val EVENT_EDITED = "EDITED"
    const val EVENT_DISCARDED = "DISCARDED"

    enum class PaymentStatus(val label: String) {
        NOT_PAID("Not paid"),
        PARTIALLY_PAID("Partially paid"),
        PAID("Paid")
    }

    data class VendorOption(val code: String, val name: String)

    data class Fixture(
        val code: String,
        val category: String,
        val vendor: String,
        val paise: Long
    )

    data class DraftInput(
        val vendorCode: String,
        val category: String,
        val invoiceDate: String,
        val amountPaise: Long,
        val paymentStatus: PaymentStatus,
        val amountPaidPaise: Long,
        val expenseCategoryCode: String? = null,
        val expenseTypeCode: String? = null,
        val expenseTypeLabel: String? = null
    )

    val vendors: List<VendorOption> = listOf(
        VendorOption("demo-laundry", "Demo Laundry Supplier"),
        VendorOption("demo-rent", "Demo Landlord"),
        VendorOption("demo-maintenance", "Demo Maintenance Vendor"),
        VendorOption("demo-utilities", "Demo Utility Provider"),
        VendorOption("demo-housekeeping", "Demo Housekeeping Supplier"),
        VendorOption("demo-general", "Demo General Supplier")
    )

    val categories: List<String> = listOf(
        "Laundry",
        "Lease / Rent",
        "Maintenance",
        "Utilities",
        "Housekeeping Supplies",
        "Other Operating Expense"
    )

    val fixtures: List<Fixture> = listOf(
        Fixture("demo-laundry", "Laundry", "Demo Laundry Supplier", 12_500_00L),
        Fixture("demo-rent", "Lease / Rent", "Demo Landlord", 2_50_000_00L),
        Fixture("demo-maintenance", "Maintenance", "Demo Maintenance Vendor", 8_750_50L)
    )

    fun parseMoney(text: String): Long =
        Money.parseMajorUnits(CurrencyCode.INR, text.trim()).minorUnits

    fun create(input: DraftInput, id: String, nowMillis: Long): FinanceDemoDraftBill {
        require(id.isNotBlank() && nowMillis > 0L) { "Missing draft identity or timestamp" }
        val date = parseDate(input.invoiceDate)
        return FinanceDemoDraftBill(
            draftId = id,
            environment = ENVIRONMENT,
            tenantId = TENANT,
            hotelId = HOTEL,
            propertyId = PROPERTY,
            vendorCode = input.vendorCode,
            category = input.category,
            invoiceDate = date.toString(),
            accountingPeriod = YearMonth.from(date).toString(),
            currencyCode = CurrencyCode.INR.name,
            amountPaise = input.amountPaise,
            status = STATUS_ACTIVE,
            createdAtMillis = nowMillis,
            paymentStatus = input.paymentStatus.name,
            amountPaidPaise = input.amountPaidPaise,
            updatedAtMillis = nowMillis,
            discardedAtMillis = null,
            revision = 1L,
            expenseCategoryCode = input.expenseCategoryCode,
            expenseTypeCode = input.expenseTypeCode,
            expenseTypeLabel = input.expenseTypeLabel
        ).also(::validate)
    }

    /** Backwards-compatible helper for the three Phase 2A fictional fixtures. */
    fun create(code: String, id: String, nowMillis: Long): FinanceDemoDraftBill {
        val fixture = fixtures.singleOrNull { it.code == code }
            ?: throw IllegalArgumentException("Unknown synthetic fixture")
        return create(
            input = DraftInput(
                vendorCode = fixture.code,
                category = fixture.category,
                invoiceDate = "2026-11-01",
                amountPaise = fixture.paise,
                paymentStatus = PaymentStatus.NOT_PAID,
                amountPaidPaise = 0L
            ),
            id = id,
            nowMillis = nowMillis
        )
    }

    fun edit(
        original: FinanceDemoDraftBill,
        input: DraftInput,
        reason: String,
        nowMillis: Long
    ): FinanceDemoDraftBill {
        validate(original)
        require(original.status == STATUS_ACTIVE) { "Only an active local draft can be edited" }
        requireReason(reason)
        require(nowMillis >= original.updatedAtMillis) { "Edit timestamp moved backwards" }
        val date = parseDate(input.invoiceDate)
        return original.copy(
            vendorCode = input.vendorCode,
            category = input.category,
            invoiceDate = date.toString(),
            accountingPeriod = YearMonth.from(date).toString(),
            amountPaise = input.amountPaise,
            paymentStatus = input.paymentStatus.name,
            amountPaidPaise = input.amountPaidPaise,
            expenseCategoryCode = input.expenseCategoryCode,
            expenseTypeCode = input.expenseTypeCode,
            expenseTypeLabel = input.expenseTypeLabel,
            updatedAtMillis = nowMillis,
            revision = Math.addExact(original.revision, 1L)
        ).also(::validate)
    }

    fun discard(
        original: FinanceDemoDraftBill,
        reason: String,
        nowMillis: Long
    ): FinanceDemoDraftBill {
        validate(original)
        require(original.status == STATUS_ACTIVE) { "Only an active local draft can be discarded" }
        requireReason(reason)
        require(nowMillis >= original.updatedAtMillis) { "Discard timestamp moved backwards" }
        return original.copy(
            status = STATUS_DISCARDED,
            updatedAtMillis = nowMillis,
            discardedAtMillis = nowMillis,
            revision = Math.addExact(original.revision, 1L)
        ).also(::validate)
    }

    fun createdEvent(row: FinanceDemoDraftBill): FinanceDemoDraftEvent =
        FinanceDemoDraftEvent(
            eventId = "created-" + row.draftId,
            draftId = row.draftId,
            eventType = EVENT_CREATED,
            reason = "Created fictional offline draft",
            occurredAtMillis = row.createdAtMillis,
            resultingRevision = row.revision
        )

    fun editedEvent(row: FinanceDemoDraftBill, reason: String): FinanceDemoDraftEvent =
        changeEvent(row, EVENT_EDITED, reason)

    fun discardedEvent(row: FinanceDemoDraftBill, reason: String): FinanceDemoDraftEvent =
        changeEvent(row, EVENT_DISCARDED, reason)

    fun validate(row: FinanceDemoDraftBill) {
        require(
            row.environment == ENVIRONMENT && row.tenantId == TENANT &&
                row.hotelId == HOTEL && row.propertyId == PROPERTY
        ) { "Not a synthetic Finance DEV record" }
        require(row.currencyCode == CurrencyCode.INR.name) { "Unknown draft currency" }
        require(row.vendorCode in vendors.map { it.code } ||
            runCatching { java.util.UUID.fromString(row.vendorCode) }.isSuccess) {
            "Unknown vendor reference"
        }
        if (row.expenseTypeCode == null) {
            require(row.expenseCategoryCode == null && row.expenseTypeLabel == null) {
                "Legacy draft cannot contain partial canonical classification"
            }
            require(row.category in categories) { "Unknown legacy expense category" }
        } else {
            val categoryCode = row.expenseCategoryCode
            require(categoryCode != null && Regex("HC[0-9]{2}").matches(categoryCode)) {
                "Missing canonical category code"
            }
            require(Regex("HE[0-9]{4}").matches(row.expenseTypeCode) &&
                row.expenseTypeCode.substring(2, 4) == categoryCode.substring(2, 4)) {
                "Canonical type does not belong to category"
            }
            require(row.category.isNotBlank() && !row.expenseTypeLabel.isNullOrBlank()) {
                "Missing classification display snapshot"
            }
        }
        require(row.amountPaise > 0L) { "Expense amount must be positive" }
        require(row.createdAtMillis > 0L && row.updatedAtMillis >= row.createdAtMillis) {
            "Invalid draft timestamps"
        }
        require(row.revision > 0L) { "Invalid draft revision" }

        val date = parseDate(row.invoiceDate)
        require(row.accountingPeriod == YearMonth.from(date).toString()) {
            "Accounting period must follow invoice date"
        }

        val payment = runCatching { PaymentStatus.valueOf(row.paymentStatus) }.getOrNull()
        require(payment != null) { "Unknown payment status" }
        when (payment) {
            PaymentStatus.NOT_PAID -> require(row.amountPaidPaise == 0L) {
                "Not-paid draft cannot contain a paid amount"
            }
            PaymentStatus.PARTIALLY_PAID -> require(
                row.amountPaidPaise > 0L && row.amountPaidPaise < row.amountPaise
            ) { "Partial payment must be positive and less than the total" }
            PaymentStatus.PAID -> require(row.amountPaidPaise == row.amountPaise) {
                "Paid draft must have the full amount marked paid"
            }
        }

        when (row.status) {
            STATUS_ACTIVE -> require(row.discardedAtMillis == null) {
                "Active draft cannot have a discard timestamp"
            }
            STATUS_DISCARDED -> require(
                row.discardedAtMillis != null && row.discardedAtMillis == row.updatedAtMillis
            ) { "Discarded draft requires its discard timestamp" }
            else -> throw IllegalArgumentException("Draft is never allowed to become posted")
        }
    }

    fun vendorName(code: String): String =
        vendors.singleOrNull { it.code == code }?.name
            ?: throw IllegalArgumentException("Unknown fictional vendor")

    fun paymentLabel(value: String): String =
        runCatching { PaymentStatus.valueOf(value).label }
            .getOrElse { throw IllegalArgumentException("Unknown payment status") }

    fun moneyText(paise: Long): String =
        "₹" + Money(CurrencyCode.INR, paise).toMajorUnitsText()

    private fun changeEvent(
        row: FinanceDemoDraftBill,
        eventType: String,
        reason: String
    ): FinanceDemoDraftEvent = FinanceDemoDraftEvent(
        eventId = eventType.lowercase() + "-" + row.draftId + "-" + row.revision,
        draftId = row.draftId,
        eventType = eventType,
        reason = requireReason(reason),
        occurredAtMillis = row.updatedAtMillis,
        resultingRevision = row.revision
    )

    private fun parseDate(value: String): LocalDate {
        val date = runCatching { LocalDate.parse(value.trim()) }.getOrNull()
        require(date != null && date.year in 2020..2100) { "Use a valid invoice date" }
        return date
    }

    private fun requireReason(value: String): String {
        val reason = value.trim()
        require(reason.isNotBlank()) { "A reason is mandatory" }
        require(reason.length <= 240) { "Reason is too long" }
        return reason
    }
}
