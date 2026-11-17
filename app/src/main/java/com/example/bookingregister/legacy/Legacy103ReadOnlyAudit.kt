package com.example.bookingregister.legacy

import android.database.Cursor
import com.example.bookingregister.data.AppDatabase
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal

/**
 * An independent, strictly read-only audit of the original Play 1.0.3 snapshots.
 * This class is NOT wired into login, recovery, booking writes or migration.
 * It cannot certify that a property migration is safe: the remaining gate checks
 * (rooms, billing, concurrent writes, etc.) must also pass.
 */
internal class Legacy103ReadOnlyAudit(
    private val db: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val hotelRemoteId: String
) {
    suspend fun run(): Legacy103AuditReport {
        val local = try {
            val sqlite = db.openHelper.readableDatabase
            if (!listOf(BOOKINGS_BACKUP, PAYMENTS_BACKUP).all { table ->
                    sqlite.query(
                        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
                        arrayOf(table)
                    ).use { it.moveToFirst() }
                }) {
                return Legacy103AuditReport.blocked("LEGACY_BACKUP_NOT_FOUND")
            }
            val bookings = readBackedUpRows(BOOKINGS_BACKUP)
            val payments = readBackedUpRows(PAYMENTS_BACKUP)
            if (bookings.isEmpty()) {
                return Legacy103AuditReport.blocked("ORIGINAL_BOOKING_SNAPSHOT_EMPTY")
            }
            bookings to payments
        } catch (_: Exception) {
            return Legacy103AuditReport.blocked("LOCAL_BACKUP_READ_FAILED")
        }

        val remote = try {
            val hotelDoc = firestore.collection("hotels").document(hotelRemoteId)
            val bookings = fetchServerDocuments(
                hotelDoc.collection("bookings"), local.first.map { it.remoteId }
            )
            val payments = fetchServerDocuments(
                hotelDoc.collection("bookingPayments"), local.second.map { it.remoteId }
            )
            bookings to payments
        } catch (_: Exception) {
            // Never fall back to Firestore's cache: absence in a stale cache is unsafe.
            return Legacy103AuditReport.blocked("AUTHORITATIVE_CLOUD_READ_FAILED")
        }

        return Legacy103RecordComparator.compare(
            localBookings = local.first,
            cloudBookings = remote.first,
            localPayments = local.second,
            cloudPayments = remote.second
        )
    }

    private fun readBackedUpRows(table: String): List<Legacy103SnapshotRow> {
        require(table == BOOKINGS_BACKUP || table == PAYMENTS_BACKUP)
        val rows = mutableListOf<Legacy103SnapshotRow>()
        db.openHelper.readableDatabase.query(
            "SELECT * FROM $table WHERE hotelRemoteId = ?",
            arrayOf(hotelRemoteId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val fields = linkedMapOf<String, Any?>()
                for (index in 0 until cursor.columnCount) {
                    fields[cursor.getColumnName(index)] = when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                        else -> error("Unsupported backup column type")
                    }
                }
                val id = fields["remoteId"]?.toString()?.trim().orEmpty()
                require(id.isNotEmpty() && fields["hotelRemoteId"] == hotelRemoteId) {
                    "Invalid legacy snapshot identity"
                }
                rows += Legacy103SnapshotRow(id, fields)
            }
        }
        return rows
    }

    private suspend fun fetchServerDocuments(
        collection: com.google.firebase.firestore.CollectionReference,
        ids: List<String>
    ): Map<String, Map<String, Any?>> {
        val records = mutableMapOf<String, Map<String, Any?>>()
        ids.distinct().chunked(30).forEach { batch ->
            if (batch.isNotEmpty()) {
                collection.whereIn(FieldPath.documentId(), batch)
                    .get(Source.SERVER)
                    .await()
                    .documents
                    .forEach { document -> records[document.id] = document.data.orEmpty() }
            }
        }
        return records
    }

    private companion object {
        const val BOOKINGS_BACKUP = "legacy_v11_backup_bookings"
        const val PAYMENTS_BACKUP = "legacy_v11_backup_booking_payments"
    }
}

internal data class Legacy103SnapshotRow(
    val remoteId: String,
    val fields: Map<String, Any?>
)

/**
 * Report covers ONLY preserved booking/payment data. It does NOT authorize migration.
 * Issue identifiers never contain guest names, phone numbers or amounts.
 */
internal data class Legacy103AuditReport(
    val originalBookings: Int,
    val originalPayments: Int,
    val matchedBookings: Int,
    val matchedPayments: Int,
    val issues: List<String>
) {
    val snapshotReconciled: Boolean
        get() = issues.isEmpty() && originalBookings > 0 &&
            matchedBookings == originalBookings && matchedPayments == originalPayments

    companion object {
        fun blocked(reason: String) = Legacy103AuditReport(0, 0, 0, 0, listOf(reason))
    }
}

/**
 * Pure comparison code; testable without Firebase or an Android device.
 * Compare the entire original business payload, excluding only local sync metadata.
 * Legitimate post-upgrade edits may show as mismatches: that is intentional.
 * Any such mismatch requires manual review, never an automatic overwrite.
 */
internal object Legacy103RecordComparator {
    private val syncOnlyColumns = setOf(
        "localId", "remoteId", "updatedAt", "lastSyncedAt", "syncState",
        "lastSyncError", "revision", "baseRevision", "updatedByUid",
        "serverUpdatedAt"
    )

    fun compare(
        localBookings: List<Legacy103SnapshotRow>,
        cloudBookings: Map<String, Map<String, Any?>>,
        localPayments: List<Legacy103SnapshotRow>,
        cloudPayments: Map<String, Map<String, Any?>>
    ): Legacy103AuditReport {
        val issues = mutableListOf<String>()
        if (localBookings.isEmpty()) issues += "ORIGINAL_BOOKING_SNAPSHOT_EMPTY"
        if (localBookings.map { it.remoteId }.distinct().size != localBookings.size) {
            issues += "DUPLICATE_LOCAL_BOOKING_IDENTITIES"
        }
        if (localPayments.map { it.remoteId }.distinct().size != localPayments.size) {
            issues += "DUPLICATE_LOCAL_PAYMENT_IDENTITIES"
        }
        val bookingIds = localBookings.map { it.remoteId }.toSet()
        var matchedBookings = 0
        var matchedPayments = 0

        for (booking in localBookings) {
            val cloud = cloudBookings[booking.remoteId]
            when {
                cloud == null -> issues += "BOOKING_ABSENT:${booking.remoteId}"
                !businessValuesMatch(booking.fields, cloud) ->
                    issues += "BOOKING_MISMATCH:${booking.remoteId}"
                else -> matchedBookings++
            }
        }
        for (payment in localPayments) {
            val cloud = cloudPayments[payment.remoteId]
            when {
                payment.fields["bookingRemoteId"]?.toString() !in bookingIds ->
                    issues += "ORIGINAL_PAYMENT_PARENT_NOT_IN_SNAPSHOT:${payment.remoteId}"
                cloud == null -> issues += "PAYMENT_ABSENT:${payment.remoteId}"
                !businessValuesMatch(payment.fields, cloud) ->
                    issues += "PAYMENT_MISMATCH:${payment.remoteId}"
                else -> matchedPayments++
            }
        }
        return Legacy103AuditReport(
            originalBookings = localBookings.size,
            originalPayments = localPayments.size,
            matchedBookings = matchedBookings,
            matchedPayments = matchedPayments,
            issues = issues
        )
    }

    private fun businessValuesMatch(
        legacy: Map<String, Any?>,
        cloud: Map<String, Any?>
    ): Boolean = legacy
        .filterKeys { it !in syncOnlyColumns }
        .all { (key, legacyValue) ->
            key in cloud && normalize(key, legacyValue) == normalize(key, cloud[key])
        }

    private fun normalize(field: String, value: Any?): Any? {
        if (value == null) return null
        if (field == "roomRemoteIds") {
            return when (value) {
                is List<*> -> value.map { it?.toString()?.trim().orEmpty() }
                is String -> value.split(',').map(String::trim).filter(String::isNotBlank)
                else -> value
            }
        }
        if (field == "isDeleted") {
            return when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                else -> value.toString().equals("true", ignoreCase = true)
            }
        }
        if (value is Number) return try {
            BigDecimal(value.toString()).stripTrailingZeros()
        } catch (_: NumberFormatException) { value.toString() }
        return value
    }
}
