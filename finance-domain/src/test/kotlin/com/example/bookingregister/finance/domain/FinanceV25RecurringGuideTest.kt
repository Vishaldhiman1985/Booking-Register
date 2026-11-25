package com.example.bookingregister.finance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceV25RecurringGuideTest {
    @Test fun propertyRentRecommended() {
        assertEquals(FinanceV25RecurringGuide.Advice.RECOMMENDED,
            FinanceV25RecurringGuide.advise("Lease & Rent", "Monthly rent"))
    }
    @Test fun broadbandRecommended() {
        assertEquals(FinanceV25RecurringGuide.Advice.RECOMMENDED,
            FinanceV25RecurringGuide.advise("Communication", "Internet / Wi-Fi"))
    }
    @Test fun contractedMaintenanceRecommended() {
        assertEquals(FinanceV25RecurringGuide.Advice.RECOMMENDED,
            FinanceV25RecurringGuide.advise("Repairs", "Maintenance Contract"))
    }
    @Test fun groceriesFlagged() {
        assertEquals(FinanceV25RecurringGuide.Advice.VARIABLE,
            FinanceV25RecurringGuide.advise("Kitchen Grocery", "Vegetables"))
    }
    @Test fun variableElectricityFlagged() {
        assertEquals(FinanceV25RecurringGuide.Advice.VARIABLE,
            FinanceV25RecurringGuide.advise("Utilities", "Electricity"))
    }
    @Test fun staffSalaryBlocked() {
        assertEquals(FinanceV25RecurringGuide.Advice.MANPOWER_ONLY,
            FinanceV25RecurringGuide.advise("Staff & Employee Benefits", "Monthly salary"))
    }
    @Test fun unknownIsReviewNotSilentApproval() {
        assertEquals(FinanceV25RecurringGuide.Advice.REVIEW,
            FinanceV25RecurringGuide.advise("Miscellaneous", "General expense"))
    }
    @Test fun stockTrackingBlocksSeparateRecurringBill() {
        assertTrue(!FinanceV25RecurringGuide.permittedForMonthlySchedule(
            FinanceV25RecurringGuide.Advice.VARIABLE, stockTracked = true))
    }
    @Test fun nonStockVariableCanBeContracted() {
        assertTrue(FinanceV25RecurringGuide.permittedForMonthlySchedule(
            FinanceV25RecurringGuide.Advice.VARIABLE, stockTracked = false))
    }
    @Test fun salaryNeverPermitted() {
        assertTrue(!FinanceV25RecurringGuide.permittedForMonthlySchedule(
            FinanceV25RecurringGuide.Advice.MANPOWER_ONLY, stockTracked = false))
    }
    @Test fun noPostingOrTaxPromise() {
        assertTrue(FinanceV25RecurringGuide.hint(FinanceV25RecurringGuide.Advice.RECOMMENDED)
            .contains("record payment separately"))
    }
    @Test fun variableCanBeConfirmedWithAnAgreement() {
        assertTrue(FinanceV25RecurringGuide.hint(FinanceV25RecurringGuide.Advice.VARIABLE)
            .contains("fixed monthly contract"))
    }
}
