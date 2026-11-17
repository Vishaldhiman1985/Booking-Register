package com.example.bookingregister.legacy

import java.math.BigDecimal

/**
 * Pure, offline-testable diagnostic of the independent Stage 2 snapshots.
 * Readiness here is for further development ONLY. It is never passed to the
 * migration gate as proof of owner consent, backup, or concurrency protection.
 */
internal object Legacy103PropertyReadinessPolicy {
    fun evaluate(input: Legacy103PropertyReadinessInput): Legacy103PropertyReadinessReport {
        val issues = mutableListOf<String>()
        val cloud = input.cloud
        val local = input.local
        val expected = Legacy103PropertyReadinessAudit.COLLECTION_TABLES.keys
        if (!input.bookingPaymentAudit.snapshotReconciled) issues += "ORIGINAL_AUDIT_NOT_PASSED"
        for (collection in expected) {
            if (!cloud.containsKey(collection)) issues += "CLOUD_COLLECTION_NOT_READ:$collection"
            if (!local.collections.containsKey(collection)) issues += "LOCAL_TABLE_NOT_READ:$collection"
        }
        if (issues.isNotEmpty()) return makeReport(cloud, issues)

        fun docs(collection: String) = cloud.getValue(collection)
        val rooms = docs("rooms")
        val bookings = docs("bookings")
        val sources = docs("bookingSources")
        val bills = docs("foodBills")
        val orders = docs("foodOrders")
        val allProperties = docs("managedProperties")

        if (allProperties.isNotEmpty()) issues += "PROPERTY_DOCUMENT_ALREADY_EXISTS"
        val activeRooms = rooms.filterValues { !deleted(it) }
        if (activeRooms.isEmpty()) issues += "NO_ACTIVE_CLOUD_ROOMS"
        if (activeRooms.values.any { !blank(it["propertyRemoteId"]) }) {
            issues += "ROOM_ALREADY_HAS_PROPERTY_ID"
        }
        if (local.originalRooms.isEmpty()) issues += "ORIGINAL_ROOM_BACKUP_EMPTY"
        val originalRoomIds = local.originalRooms.map { it.remoteId }
        if (originalRoomIds.distinct().size != originalRoomIds.size) {
            issues += "DUPLICATE_ORIGINAL_ROOM_IDS"
        }
        for (row in local.originalRooms) {
            val server = rooms[row.remoteId]
            if (server == null) issues += "ORIGINAL_ROOM_MISSING:${row.remoteId}"
            else if (row.fields["roomName"]?.toString() != server["roomName"]?.toString()) {
                issues += "ORIGINAL_ROOM_NAME_CHANGED:${row.remoteId}"
            }
        }
        if (local.pendingOutboxOperations > 0) issues += "BOOKING_OUTBOX_NOT_EMPTY"
        if (local.pendingRecoveryReviews > 0) issues += "ORIGINAL_BOOKING_REVIEW_PENDING"

        // The normal app can show local state as SYNCED even when a dependent
        // document is absent remotely. Check existence and financially sensitive
        // fields independently of the normal sync implementation.
        for ((collection, localRows) in local.collections) {
            val serverRows = docs(collection)
            if (localRows.map { it.remoteId }.distinct().size != localRows.size) {
                issues += "DUPLICATE_LOCAL_IDS:$collection"
            }
            for (row in localRows) {
                if (row.fields["syncState"]?.toString() in setOf("FAILED", "PENDING")) {
                    issues += "LOCAL_SYNC_OUTSTANDING:$collection:${row.remoteId}"
                }
                val server = serverRows[row.remoteId]
                if (server == null) {
                    issues += "LOCAL_ROW_ABSENT_IN_CLOUD:$collection:${row.remoteId}"
                    continue
                }
                if (server["hotelRemoteId"]?.toString() != input.hotelRemoteId) {
                    issues += "CLOUD_HOTEL_ID_MISMATCH:$collection:${row.remoteId}"
                }
                val fields = COMPARE_FIELDS[collection].orEmpty()
                if (fields.any { field ->
                        !equal(row.fields[field], server[field], field)
                    }) {
                    issues += "LOCAL_CLOUD_VALUES_DIFFER:$collection:${row.remoteId}"
                }
            }
        }

        for ((collection, rows) in cloud) {
            for ((id, row) in rows) {
                if (row["hotelRemoteId"] != null &&
                    row["hotelRemoteId"]?.toString() != input.hotelRemoteId) {
                    issues += "CLOUD_TENANT_MISMATCH:$collection:$id"
                }
                if (collection in PROPERTY_SCOPED && !blank(row["propertyRemoteId"])) {
                    issues += "PROPERTY_REFERENCE_ALREADY_PRESENT:$collection:$id"
                }
            }
        }

        for ((id, booking) in bookings) {
            if (deleted(booking)) continue
            val assignedRooms = values(booking["roomRemoteIds"])
            if (assignedRooms.isEmpty() || assignedRooms.any { room ->
                    val target = rooms[room]
                    target == null || deleted(target)
                }) issues += "BOOKING_ROOM_LINK_INVALID:$id"
            val sourceId = booking["sourceRemoteId"]?.toString()?.trim().orEmpty()
            if (sourceId.isNotEmpty() && sourceId !in sources) {
                issues += "BOOKING_SOURCE_LINK_INVALID:$id"
            }
        }
        fun checkParent(collection: String, field: String, parents: Map<String, Map<String, Any?>>,
                        optional: Boolean = false) {
            for ((id, row) in docs(collection)) {
                if (deleted(row)) continue
                val target = row[field]?.toString()?.trim().orEmpty()
                if ((target.isEmpty() && !optional) ||
                    (target.isNotEmpty() && target !in parents)) {
                    issues += "BROKEN_REFERENCE:$collection:$id:$field"
                }
            }
        }
        checkParent("bookingPayments", "bookingRemoteId", bookings)
        checkParent("bookingFinancialLines", "bookingRemoteId", bookings)
        checkParent("bookingFinancialLines", "roomRemoteId", rooms)
        checkParent("bookingRoomNightAssignments", "bookingRemoteId", bookings)
        checkParent("bookingRoomNightAssignments", "roomRemoteId", rooms)
        checkParent("bookingAccountingCharges", "bookingRemoteId", bookings)
        checkParent("foodOrders", "bookingRemoteId", bookings, optional = true)
        checkParent("foodOrders", "roomRemoteId", rooms, optional = true)
        checkParent("foodOrders", "billRemoteId", bills, optional = true)
        checkParent("foodOrderItems", "orderRemoteId", orders)
        checkParent("foodBillItems", "billRemoteId", bills)

        return makeReport(cloud, issues)
    }

    private fun makeReport(
        cloud: Map<String, Map<String, Map<String, Any?>>>,
        issues: List<String>
    ): Legacy103PropertyReadinessReport = Legacy103PropertyReadinessReport(
        inventoryReconciled = issues.isEmpty(),
        cloudRecordCounts = cloud.mapValues { it.value.size },
        issues = issues.distinct()
    )

    private fun deleted(row: Map<String, Any?>): Boolean = when (val value = row["isDeleted"]) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        else -> value?.toString()?.equals("true", ignoreCase = true) == true
    }

    private fun blank(value: Any?): Boolean = value == null || value.toString().isBlank()

    private fun values(value: Any?): List<String> = when (value) {
        is List<*> -> value.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
        is String -> value.split(',').map(String::trim).filter(String::isNotBlank)
        else -> emptyList()
    }

    private fun equal(left: Any?, right: Any?, field: String): Boolean {
        if (field == "roomRemoteIds") return values(left) == values(right)
        if (field == "isDeleted") return deleted(mapOf("isDeleted" to left)) ==
            deleted(mapOf("isDeleted" to right))
        if (left == null || right == null) return left == right
        if (left is Number && right is Number) return try {
            BigDecimal(left.toString()).compareTo(BigDecimal(right.toString())) == 0
        } catch (_: Exception) { false }
        return left == right
    }

    private val PROPERTY_SCOPED = setOf(
        "rooms", "bookings", "bookingSources", "bookingFinancialLines",
        "bookingRoomNightAssignments", "foodOrders", "foodBills",
        "foodMenuItems", "serviceMenuItems"
    )

    // Check fields where client-side loss or different financial data is critical.
    // This is not a full business-object diff, and no clean report authorizes writing.
    private val COMPARE_FIELDS: Map<String, Set<String>> = mapOf(
        "rooms" to setOf("roomName", "isDeleted", "propertyRemoteId"),
        "bookings" to setOf("roomRemoteIds", "receivable", "paid", "grossCharges",
            "roomRevenue", "propertyTax", "isDeleted", "propertyRemoteId"),
        "bookingPayments" to setOf("bookingRemoteId", "amount", "paymentMillis",
            "paymentType", "isDeleted"),
        "bookingSources" to setOf("sourceName", "sourceType", "commissionPercent",
            "commissionGstPercent", "tcsPercent", "tdsPercent", "fixedFee", "propertyRemoteId"),
        "bookingFinancialLines" to setOf("bookingRemoteId", "roomRemoteId",
            "businessDateMillis", "grossAmount", "taxableAmount", "gstAmount",
            "gstRatePercent", "propertyRemoteId", "isDeleted"),
        "bookingRoomNightAssignments" to setOf("bookingRemoteId", "roomRemoteId",
            "businessDateMillis", "propertyRemoteId", "isDeleted"),
        "bookingAccountingCharges" to setOf("bookingRemoteId", "amount",
            "gstRatePercent", "taxableAmount", "linkedFinalBillId", "isDeleted"),
        "foodOrders" to setOf("bookingRemoteId", "roomRemoteId", "billRemoteId",
            "propertyRemoteId", "totalAmount", "gstAmount", "isDeleted"),
        "foodBills" to setOf("propertyRemoteId", "grandTotal", "gstAmount",
            "status", "isDeleted"),
        "foodBillItems" to setOf("billRemoteId", "lineTotal", "gstAmount", "isDeleted"),
        "foodOrderItems" to setOf("orderRemoteId", "lineTotal", "isDeleted"),
        "foodMenuItems" to setOf("propertyRemoteId", "price", "gstRatePercent", "isDeleted"),
        "serviceMenuItems" to setOf("propertyRemoteId", "price", "gstRatePercent", "isDeleted")
    )
}
