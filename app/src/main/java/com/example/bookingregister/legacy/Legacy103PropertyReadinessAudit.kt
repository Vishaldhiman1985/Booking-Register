package com.example.bookingregister.legacy

import android.database.Cursor
import com.example.bookingregister.data.AppDatabase
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/**
 * Stage 2: Independent, read-only inventory. Never call from the normal login/sync
 * flow. This is NOT permission to migrate; ownership, immutable backups, server
 * locks, concurrency, and rollback are intentionally outside this audit.
 *
 * All cloud queries use Source.SERVER. Failure is a blocker, never a cache read.
 */
internal class Legacy103PropertyReadinessAudit(
    private val db: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val hotelRemoteId: String
) {
    suspend fun run(): Legacy103PropertyReadinessReport {
        if (hotelRemoteId.isBlank()) return Legacy103PropertyReadinessReport.blocked("HOTEL_ID_MISSING")
        val original = Legacy103ReadOnlyAudit(db, firestore, hotelRemoteId).run()
        if (!original.snapshotReconciled) {
            return Legacy103PropertyReadinessReport.blocked(
                "ORIGINAL_BOOKINGS_OR_PAYMENTS_NOT_VERIFIED",
                original.issues
            )
        }
        val local = try {
            val sqlite = db.openHelper.readableDatabase
            val originalRooms = readRows("legacy_v11_backup_rooms")
            val tables = COLLECTION_TABLES.mapValues { (_, table) -> readRows(table) }
            val outbox = sqlite.query(
                "SELECT COUNT(*) FROM booking_sync_outbox WHERE hotelRemoteId = ?",
                arrayOf(hotelRemoteId)
            ).use { if (it.moveToFirst()) it.getInt(0) else error("Missing outbox result") }
            val reviews = sqlite.query(
                "SELECT COUNT(*) FROM legacy_v11_recovery_stage " +
                    "WHERE hotelRemoteId = ? AND kind = 'booking_review'",
                arrayOf(hotelRemoteId)
            ).use { if (it.moveToFirst()) it.getInt(0) else error("Missing recovery result") }
            Legacy103LocalReadinessSnapshot(originalRooms, tables, outbox, reviews)
        } catch (_: Exception) {
            return Legacy103PropertyReadinessReport.blocked("LOCAL_INVENTORY_READ_FAILED")
        }
        val cloud = try {
            val hotel = firestore.collection("hotels").document(hotelRemoteId)
            COLLECTION_TABLES.keys.associateWith { collection ->
                val documents = linkedMapOf<String, Map<String, Any?>>()
                var last: com.google.firebase.firestore.DocumentSnapshot? = null
                do {
                    var query = hotel.collection(collection)
                        .orderBy(FieldPath.documentId())
                        .limit(200)
                    if (last != null) query = query.startAfter(last)
                    val page = query.get(Source.SERVER).await()
                    for (doc in page.documents) {
                        require(doc.id !in documents) { "Repeated cloud document" }
                        documents[doc.id] = doc.data ?: error("Document without data")
                    }
                    require(documents.size <= MAX_COLLECTION_DOCUMENTS) {
                        "Collection exceeds safe phone-side audit limit"
                    }
                    last = page.documents.lastOrNull()
                    val hasMore = page.size() == 200
                } while (hasMore)
                documents
            }
        } catch (_: Exception) {
            return Legacy103PropertyReadinessReport.blocked("AUTHORITATIVE_INVENTORY_READ_FAILED")
        }
        return Legacy103PropertyReadinessPolicy.evaluate(
            Legacy103PropertyReadinessInput(original, local, cloud, hotelRemoteId)
        )
    }

    private fun readRows(table: String): List<Legacy103SnapshotRow> {
        require(table == "legacy_v11_backup_rooms" || table in COLLECTION_TABLES.values)
        val rows = mutableListOf<Legacy103SnapshotRow>()
        db.openHelper.readableDatabase.query(
            "SELECT * FROM $table WHERE hotelRemoteId = ?", arrayOf(hotelRemoteId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val data = linkedMapOf<String, Any?>()
                for (i in 0 until cursor.columnCount) {
                    data[cursor.getColumnName(i)] = when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                        else -> error("Unsupported column type")
                    }
                }
                val id = data["remoteId"]?.toString()?.trim().orEmpty()
                require(id.isNotBlank() && data["hotelRemoteId"]?.toString() == hotelRemoteId)
                rows += Legacy103SnapshotRow(id, data)
            }
        }
        return rows
    }

    companion object {
        private const val MAX_COLLECTION_DOCUMENTS = 10_000
        internal val COLLECTION_TABLES = linkedMapOf(
            "rooms" to "rooms",
            "managedProperties" to "managed_properties",
            "bookings" to "bookings",
            "bookingPayments" to "booking_payments",
            "bookingSources" to "booking_sources",
            "bookingFinancialLines" to "booking_financial_lines",
            "bookingRoomNightAssignments" to "booking_room_night_assignments",
            "bookingAccountingCharges" to "booking_accounting_charges",
            "foodOrders" to "food_orders",
            "foodOrderItems" to "food_order_items",
            "foodBills" to "food_bills",
            "foodBillItems" to "food_bill_items",
            "foodMenuItems" to "food_menu_items",
            "serviceMenuItems" to "service_menu_items"
        )
    }
}

internal data class Legacy103LocalReadinessSnapshot(
    val originalRooms: List<Legacy103SnapshotRow>,
    val collections: Map<String, List<Legacy103SnapshotRow>>,
    val pendingOutboxOperations: Int,
    val pendingRecoveryReviews: Int
)

internal data class Legacy103PropertyReadinessInput(
    val bookingPaymentAudit: Legacy103AuditReport,
    val local: Legacy103LocalReadinessSnapshot,
    val cloud: Map<String, Map<String, Map<String, Any?>>>,
    val hotelRemoteId: String
)

/**
 * Only a diagnostic. A clean report is NOT a migration authorization.
 * No record content, guest name, telephone, or payment amount is output.
 */
internal data class Legacy103PropertyReadinessReport(
    val inventoryReconciled: Boolean,
    val cloudRecordCounts: Map<String, Int>,
    val issues: List<String>
) {
    companion object {
        fun blocked(reason: String, extra: List<String> = emptyList()) =
            Legacy103PropertyReadinessReport(false, emptyMap(), listOf(reason) + extra)
    }
}
