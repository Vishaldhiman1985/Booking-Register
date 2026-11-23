package com.example.bookingregister.finance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FinanceGstBillNoteTest {
    @Test fun oldNotesStayUntouchedWhenNoDeclaration() {
        val previous = "Fresh vegetables from the market"
        assertEquals(FinanceGstBillNote.Status.NOT_RECORDED, FinanceGstBillNote.read(previous).status)
        assertEquals(previous, FinanceGstBillNote.write(FinanceGstBillNote.Status.NOT_RECORDED, previous))
    }

    @Test fun declarationRoundTripsWithoutDuplicating() {
        val first = FinanceGstBillNote.write(FinanceGstBillNote.Status.YES, "Paid by UPI")
        assertEquals(FinanceGstBillNote.Status.YES, FinanceGstBillNote.read(first).status)
        assertEquals("Paid by UPI", FinanceGstBillNote.read(first).notes)
        assertEquals(first, FinanceGstBillNote.write(FinanceGstBillNote.Status.YES, first))
    }

    @Test fun changingDeclarationDoesNotEraseStaffNotes() {
        val old = FinanceGstBillNote.write(FinanceGstBillNote.Status.YES, "Supplier bill #234")
        val changed = FinanceGstBillNote.write(FinanceGstBillNote.Status.NO, old)
        assertEquals("Supplier bill #234", FinanceGstBillNote.read(changed).notes)
        assertEquals(FinanceGstBillNote.Status.NO, FinanceGstBillNote.read(changed).status)
        assertEquals("Supplier bill #234", FinanceGstBillNote.write(FinanceGstBillNote.Status.NOT_RECORDED, changed))
    }

    @Test fun limitIncludesDeclaration() {
        assertThrows(IllegalArgumentException::class.java) {
            FinanceGstBillNote.write(FinanceGstBillNote.Status.YES, "x".repeat(1995))
        }
    }

    @Test fun similarHumanNotesAreNotParsedAsDeclaration() {
        val note = "Supplier wrote [GST Bill: Yes] in their email"
        assertEquals(FinanceGstBillNote.Status.NOT_RECORDED, FinanceGstBillNote.read(note).status)
    }
}
