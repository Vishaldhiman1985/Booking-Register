package com.example.bookingregister.finance.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceDemoDraftPolicyTest {
    @Test
    fun createsOnlySyntheticScopedDrafts() {
        val row = FinanceDemoDraftPolicy.create("demo-rent", "test-1", 123L)
        FinanceDemoDraftPolicy.validate(row)
        assertEquals("DEMO_TENANT_ONLY", row.tenantId)
        assertEquals(FinanceDemoDraftPolicy.STATUS_ACTIVE, row.status)
        assertEquals(25_000_000L, row.amountPaise)
        assertEquals(0L, row.amountPaidPaise)
        assertEquals(123L, row.updatedAtMillis)
        assertEquals(1L, row.revision)
    }

    @Test
    fun createsCompletePartiallyPaidDraftUsingIntegerPaise() {
        val row = FinanceDemoDraftPolicy.create(
            input = input(
                amountPaise = FinanceDemoDraftPolicy.parseMoney("12500.25"),
                paymentStatus = FinanceDemoDraftPolicy.PaymentStatus.PARTIALLY_PAID,
                amountPaidPaise = FinanceDemoDraftPolicy.parseMoney("2500.10")
            ),
            id = "complete-draft",
            nowMillis = 1_000L
        )
        assertEquals(1_250_025L, row.amountPaise)
        assertEquals(250_010L, row.amountPaidPaise)
        assertEquals("2026-12", row.accountingPeriod)
        assertEquals("PARTIALLY_PAID", row.paymentStatus)
    }

    @Test
    fun rejectsUnknownFixtureVendorCategoryOrPostedStatus() {
        assertFails { FinanceDemoDraftPolicy.create("live-landlord", "test-2", 123L) }
        val row = FinanceDemoDraftPolicy.create("demo-laundry", "test-3", 123L)
        assertFails { FinanceDemoDraftPolicy.validate(row.copy(vendorCode = "real-vendor")) }
        assertFails { FinanceDemoDraftPolicy.validate(row.copy(category = "Secret category")) }
        assertFails { FinanceDemoDraftPolicy.validate(row.copy(status = "POSTED")) }
    }

    @Test
    fun rejectsWrongPropertyCurrencyOrAccountingPeriod() {
        val row = FinanceDemoDraftPolicy.create("demo-laundry", "test-4", 123L)
        assertFails { FinanceDemoDraftPolicy.validate(row.copy(propertyId = "REAL_PROPERTY")) }
        assertFails { FinanceDemoDraftPolicy.validate(row.copy(currencyCode = "USD")) }
        assertFails { FinanceDemoDraftPolicy.validate(row.copy(accountingPeriod = "2027-01")) }
    }

    @Test
    fun paymentStatusAndPaidAmountMustAgreeExactly() {
        assertFails {
            FinanceDemoDraftPolicy.create(
                input(
                    paymentStatus = FinanceDemoDraftPolicy.PaymentStatus.NOT_PAID,
                    amountPaidPaise = 1L
                ),
                "bad-not-paid",
                100L
            )
        }
        assertFails {
            FinanceDemoDraftPolicy.create(
                input(
                    paymentStatus = FinanceDemoDraftPolicy.PaymentStatus.PARTIALLY_PAID,
                    amountPaidPaise = 1_000_000L
                ),
                "bad-partial",
                100L
            )
        }
        assertFails {
            FinanceDemoDraftPolicy.create(
                input(
                    paymentStatus = FinanceDemoDraftPolicy.PaymentStatus.PAID,
                    amountPaidPaise = 999_999L
                ),
                "bad-paid",
                100L
            )
        }
    }

    @Test
    fun editingRequiresReasonAndPreservesDraftIdentity() {
        val original = FinanceDemoDraftPolicy.create("demo-maintenance", "edit-1", 100L)
        assertFails {
            FinanceDemoDraftPolicy.edit(original, input(), "  ", 200L)
        }

        val edited = FinanceDemoDraftPolicy.edit(
            original = original,
            input = input(
                vendorCode = "demo-utilities",
                category = "Utilities",
                amountPaise = 777_777L
            ),
            reason = "Correct fictional category",
            nowMillis = 200L
        )
        assertEquals(original.draftId, edited.draftId)
        assertEquals(original.createdAtMillis, edited.createdAtMillis)
        assertEquals(2L, edited.revision)
        assertEquals(777_777L, edited.amountPaise)
        assertEquals("demo-utilities", edited.vendorCode)
        assertEquals(
            "Correct fictional category",
            FinanceDemoDraftPolicy.editedEvent(edited, "Correct fictional category").reason
        )
    }

    @Test
    fun discardRequiresReasonAndNeverDeletesOrPostsDraft() {
        val original = FinanceDemoDraftPolicy.create("demo-laundry", "discard-1", 100L)
        assertFails { FinanceDemoDraftPolicy.discard(original, "", 200L) }

        val discarded = FinanceDemoDraftPolicy.discard(
            original,
            "Duplicate fictional draft",
            200L
        )
        assertEquals(FinanceDemoDraftPolicy.STATUS_DISCARDED, discarded.status)
        assertEquals(2L, discarded.revision)
        assertEquals(200L, discarded.discardedAtMillis)
        assertNotNull(discarded.discardedAtMillis)
        assertEquals(
            FinanceDemoDraftPolicy.EVENT_DISCARDED,
            FinanceDemoDraftPolicy.discardedEvent(
                discarded,
                "Duplicate fictional draft"
            ).eventType
        )
        assertFails {
            FinanceDemoDraftPolicy.edit(discarded, input(), "Try to restore", 300L)
        }
    }

    @Test
    fun activeDraftHasNoDiscardTimestamp() {
        val row = FinanceDemoDraftPolicy.create("demo-laundry", "active-1", 123L)
        assertNull(row.discardedAtMillis)
    }

    @Test
    fun rendersAndParsesAmountsWithoutFloatingPoint() {
        assertEquals("₹250000.00", FinanceDemoDraftPolicy.moneyText(25_000_000L))
        assertEquals("₹8750.50", FinanceDemoDraftPolicy.moneyText(875_050L))
        assertEquals(25_000_025L, FinanceDemoDraftPolicy.parseMoney("250000.25"))
        assertFails { FinanceDemoDraftPolicy.parseMoney("1.001") }
        assertFails { FinanceDemoDraftPolicy.parseMoney("1e6") }
    }

    @Test
    fun smartSearchSelectionPersistsCanonicalCodesInLocalDraft() {
        val row = FinanceDemoDraftPolicy.create(
            input(
                category = "IT, Communications & TV",
                expenseCategoryCode = "HC10",
                expenseTypeCode = "HE1003",
                expenseTypeLabel = "DTH / Cable TV Recharge"
            ), "canonical-dth", 1_000L
        )
        assertEquals("HC10", row.expenseCategoryCode)
        assertEquals("HE1003", row.expenseTypeCode)
        assertEquals("DTH / Cable TV Recharge", row.expenseTypeLabel)
        assertEquals("IT, Communications & TV", row.category)
        FinanceDemoDraftPolicy.validate(row)
    }

    @Test
    fun legacyDraftCanBeEditedWithoutLosingItsOriginalClassification() {
        val old = FinanceDemoDraftPolicy.create("demo-laundry", "legacy", 1_000L)
        val edited = FinanceDemoDraftPolicy.edit(
            old,
            input(category = old.category),
            "Corrected fictional invoice", 2_000L
        )
        assertEquals("Laundry", edited.category)
        assertNull(edited.expenseCategoryCode)
        assertNull(edited.expenseTypeCode)
        assertNull(edited.expenseTypeLabel)
        assertEquals(2L, edited.revision)
    }

    @Test
    fun rejectsPartialOrMismatchedCanonicalClassification() {
        assertFails {
            FinanceDemoDraftPolicy.create(
                input(category = "IT, Communications & TV", expenseTypeCode = "HE1003"),
                "missing-category", 1_000L
            )
        }
        assertFails {
            FinanceDemoDraftPolicy.create(
                input(
                    category = "Rooms", expenseCategoryCode = "HC04",
                    expenseTypeCode = "HE1003", expenseTypeLabel = "DTH"
                ), "mismatch", 1_000L
            )
        }
    }

    private fun input(
        vendorCode: String = "demo-maintenance",
        category: String = "Maintenance",
        amountPaise: Long = 1_000_000L,
        paymentStatus: FinanceDemoDraftPolicy.PaymentStatus =
            FinanceDemoDraftPolicy.PaymentStatus.NOT_PAID,
        amountPaidPaise: Long = 0L,
        expenseCategoryCode: String? = null,
        expenseTypeCode: String? = null,
        expenseTypeLabel: String? = null
    ) = FinanceDemoDraftPolicy.DraftInput(
        vendorCode = vendorCode,
        category = category,
        invoiceDate = "2026-12-15",
        amountPaise = amountPaise,
        paymentStatus = paymentStatus,
        amountPaidPaise = amountPaidPaise,
        expenseCategoryCode = expenseCategoryCode,
        expenseTypeCode = expenseTypeCode,
        expenseTypeLabel = expenseTypeLabel
    )

    private fun assertFails(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: RuntimeException) {
            failed = true
        }
        assertTrue("Expected validation failure", failed)
    }
}
