package com.example.bookingregister.finance.dev

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotelPurchaseInvoicePendingPolicyTest {
    @Test fun noInvoiceMayBeSavedDuringDelivery() {
        assertTrue(HotelPurchaseInvoicePendingPolicy.validAmount(null))
        assertTrue(HotelPurchaseInvoicePendingPolicy.validAmount(2400.0))
        assertFalse(HotelPurchaseInvoicePendingPolicy.validAmount(-1.0))
        assertFalse(HotelPurchaseInvoicePendingPolicy.validAmount(Double.NaN))
    }

    @Test fun onlyPhysicallyClosedUnbilledOrdersArePendingInvoice() {
        assertFalse(HotelPurchaseInvoicePendingPolicy.isPending(0L, null))
        assertTrue(HotelPurchaseInvoicePendingPolicy.isPending(12345L, null))
        assertFalse(HotelPurchaseInvoicePendingPolicy.isPending(12345L, 2400.0))
    }
}