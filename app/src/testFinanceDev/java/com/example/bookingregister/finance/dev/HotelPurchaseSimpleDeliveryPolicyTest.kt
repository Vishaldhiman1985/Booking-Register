package com.example.bookingregister.finance.dev

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class HotelPurchaseSimpleDeliveryPolicyTest {
    @Test fun fullReceiptCompletesItem() {
        assertTrue(HotelPurchaseSimpleDeliveryPolicy.validateUpdate(25.0, 25.0, 0.0))
    }

    @Test fun firstShortReceiptStaysPending() {
        assertFalse(HotelPurchaseSimpleDeliveryPolicy.validateUpdate(5.0, 2.0, 0.0))
        assertEquals(3.0, HotelPurchaseSimpleDeliveryPolicy.outstanding(5.0, 2.0), 0.000001)
    }

    @Test fun secondReceiptCompletesBalance() {
        assertTrue(HotelPurchaseSimpleDeliveryPolicy.validateUpdate(3.0, 3.0, 0.0))
    }

    @Test fun unavailableItemCanBeResolvedWithoutFakeReceipt() {
        assertTrue(HotelPurchaseSimpleDeliveryPolicy.validateUpdate(200.0, 0.0, 200.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun excessiveReceiptIsRejected() {
        HotelPurchaseSimpleDeliveryPolicy.validateUpdate(3.0, 5.0, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateReceiptAndUnavailableIsRejected() {
        HotelPurchaseSimpleDeliveryPolicy.validateUpdate(3.0, 3.0, 3.0)
    }
}
