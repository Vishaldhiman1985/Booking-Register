package com.example.bookingregister.legacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class Legacy103PropertyReadinessPolicyTest {
    private val originalAudit = Legacy103AuditReport(
        originalBookings = 1, originalPayments = 0,
        matchedBookings = 1, matchedPayments = 0, issues = emptyList()
    )
    private val roomFields = mapOf<String, Any?>(
        "hotelRemoteId" to "hotel_1", "roomName" to "101",
        "isDeleted" to false, "propertyRemoteId" to null
    )
    private val bookingFields = mapOf<String, Any?>(
        "hotelRemoteId" to "hotel_1", "roomRemoteIds" to listOf("r1"),
        "isDeleted" to false, "propertyRemoteId" to null,
        "receivable" to 2000.0, "paid" to 1000.0,
        "grossCharges" to 2000.0, "roomRevenue" to 2000.0, "propertyTax" to 0.0
    )
    private fun input(): Legacy103PropertyReadinessInput {
        val cloud = Legacy103PropertyReadinessAudit.COLLECTION_TABLES.keys
            .associateWith { emptyMap<String, Map<String, Any?>>() }.toMutableMap()
        cloud["rooms"] = mapOf("r1" to roomFields)
        cloud["bookings"] = mapOf("b1" to bookingFields)
        val local = Legacy103PropertyReadinessAudit.COLLECTION_TABLES.keys
            .associateWith { emptyList<Legacy103SnapshotRow>() }.toMutableMap()
        local["rooms"] = listOf(Legacy103SnapshotRow("r1", roomFields))
        local["bookings"] = listOf(Legacy103SnapshotRow("b1", bookingFields +
            ("roomRemoteIds" to "r1")))
        return Legacy103PropertyReadinessInput(
            originalAudit,
            Legacy103LocalReadinessSnapshot(
                originalRooms = listOf(Legacy103SnapshotRow("r1", roomFields)),
                collections = local,
                pendingOutboxOperations = 0,
                pendingRecoveryReviews = 0
            ), cloud = cloud, hotelRemoteId = "hotel_1"
        )
    }
    private fun inspect(i: Legacy103PropertyReadinessInput) =
        Legacy103PropertyReadinessPolicy.evaluate(i)

    @Test fun intactSinglePropertyLegacyInventoryPassesDiagnosticOnly() {
        assertTrue(inspect(input()).inventoryReconciled)
    }
    @Test fun propertyAlreadyCreatedBlocks() {
        val i = input()
        val r = inspect(i.copy(cloud = i.cloud +
            ("managedProperties" to mapOf("p1" to mapOf("hotelRemoteId" to "hotel_1")))))
        assertFalse(r.inventoryReconciled)
        assertTrue("PROPERTY_DOCUMENT_ALREADY_EXISTS" in r.issues)
    }
    @Test fun assignedRoomBlocks() {
        val i = input()
        val r = inspect(i.copy(cloud = i.cloud +
            ("rooms" to mapOf("r1" to (roomFields + ("propertyRemoteId" to "p1"))))))
        assertFalse(r.inventoryReconciled)
        assertTrue("ROOM_ALREADY_HAS_PROPERTY_ID" in r.issues)
    }
    @Test fun brokenBookingRoomReferenceBlocks() {
        val i = input()
        val r = inspect(i.copy(cloud = i.cloud +
            ("bookings" to mapOf("b1" to
                (bookingFields + ("roomRemoteIds" to listOf("missing_room")))))))
        assertFalse(r.inventoryReconciled)
        assertTrue("BOOKING_ROOM_LINK_INVALID:b1" in r.issues)
    }
    @Test fun pendingOutboxBlocks() {
        val i = input()
        val r = inspect(i.copy(local = i.local.copy(pendingOutboxOperations = 1)))
        assertFalse(r.inventoryReconciled)
        assertTrue("BOOKING_OUTBOX_NOT_EMPTY" in r.issues)
    }
    @Test fun failedLocalRecordBlocks() {
        val i = input()
        val updatedRows = i.local.collections.toMutableMap()
        updatedRows["rooms"] = listOf(Legacy103SnapshotRow("r1", roomFields +
            ("syncState" to "FAILED")))
        val r = inspect(i.copy(local = i.local.copy(collections = updatedRows)))
        assertFalse(r.inventoryReconciled)
        assertTrue(r.issues.any { it.startsWith("LOCAL_SYNC_OUTSTANDING") })
    }
    @Test fun changedFinancialValueBlocks() {
        val i = input()
        val r = inspect(i.copy(cloud = i.cloud +
            ("bookings" to mapOf("b1" to (bookingFields + ("paid" to 800.0))))))
        assertFalse(r.inventoryReconciled)
        assertTrue("LOCAL_CLOUD_VALUES_DIFFER:bookings:b1" in r.issues)
    }
    @Test fun orphanPaymentBlocks() {
        val i = input()
        val p = mapOf<String, Any?>("hotelRemoteId" to "hotel_1", "bookingRemoteId" to "missing",
            "amount" to 100.0, "paymentMillis" to 1234L, "paymentType" to "PAYMENT", "isDeleted" to false)
        val r = inspect(i.copy(cloud = i.cloud + ("bookingPayments" to mapOf("p1" to p))))
        assertFalse(r.inventoryReconciled)
        assertTrue(r.issues.any { it == "BROKEN_REFERENCE:bookingPayments:p1:bookingRemoteId" })
    }
    @Test fun localServerMissingPaymentBlocks() {
        val i = input()
        val p = Legacy103SnapshotRow("p1", mapOf("hotelRemoteId" to "hotel_1"))
        val r = inspect(i.copy(local = i.local.copy(collections = i.local.collections +
            ("bookingPayments" to listOf(p)))))
        assertFalse(r.inventoryReconciled)
        assertTrue("LOCAL_ROW_ABSENT_IN_CLOUD:bookingPayments:p1" in r.issues)
    }
    @Test fun originalRoomMissingBlocks() {
        val i = input()
        val r = inspect(i.copy(cloud = i.cloud + ("rooms" to emptyMap())))
        assertFalse(r.inventoryReconciled)
        assertTrue("ORIGINAL_ROOM_MISSING:r1" in r.issues)
    }
    @Test fun missingCloudCollectionBlocks() {
        val i = input()
        val r = inspect(i.copy(cloud = i.cloud - "serviceMenuItems"))
        assertFalse(r.inventoryReconciled)
        assertTrue("CLOUD_COLLECTION_NOT_READ:serviceMenuItems" in r.issues)
    }
    @Test fun originalRecoveryAuditFailureBlocks() {
        val i = input()
        val r = inspect(i.copy(bookingPaymentAudit = originalAudit.copy(issues = listOf("MISSING"))))
        assertFalse(r.inventoryReconciled)
        assertTrue("ORIGINAL_AUDIT_NOT_PASSED" in r.issues)
    }
    @Test fun crossHotelRecordBlocks() {
        val i = input()
        val r = inspect(i.copy(cloud = i.cloud +
            ("rooms" to mapOf("r1" to (roomFields + ("hotelRemoteId" to "hotel_other"))))))
        assertFalse(r.inventoryReconciled)
        assertTrue("CLOUD_TENANT_MISMATCH:rooms:r1" in r.issues)
    }
    @Test fun pendingTaxReviewBlocks() {
        val i = input()
        val r = inspect(i.copy(local = i.local.copy(pendingRecoveryReviews = 1)))
        assertFalse(r.inventoryReconciled)
        assertTrue("ORIGINAL_BOOKING_REVIEW_PENDING" in r.issues)
    }
}
