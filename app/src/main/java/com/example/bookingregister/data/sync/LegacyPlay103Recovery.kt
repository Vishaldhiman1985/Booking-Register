package com.example.bookingregister.data.sync

import androidx.room.withTransaction
import com.example.bookingregister.booking.domain.BookingChangeSet
import com.example.bookingregister.data.AppDatabase
import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingSyncOutboxEntity
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.abs

/**
 * Stages only records retained from the public Play 1.0.3 database that a fresh
 * server read proves are absent. The original v11 copies are never changed.
 * Existing cloud records are deliberately left to normal cloud reconciliation.
 */
internal class LegacyPlay103Recovery(
    private val db: AppDatabase,
    private val cloud: LegacyRecordPresence,
    private val hotelRemoteId: String
) {
    data class StageResult(val bookings: Int, val payments: Int)

    suspend fun stageMissingRecords(): StageResult {
        if (!hasLegacyBackup()) return StageResult(0, 0)
        val bookingIds = backupIds("legacy_v11_backup_bookings")
        val paymentIds = backupIds("legacy_v11_backup_booking_payments")
        if (bookingIds.isEmpty() && paymentIds.isEmpty()) return StageResult(0, 0)

        val processedBookings = stagedIds("booking") + stagedIds("booking_review") +
            stagedIds("booking_present")
        val processedPayments = stagedIds("payment") + stagedIds("payment_present")
        val uncheckedBookings = bookingIds.filterNot(processedBookings::contains)
        val uncheckedPayments = paymentIds.filterNot(processedPayments::contains)
        if (uncheckedBookings.isEmpty() && uncheckedPayments.isEmpty()) return StageResult(0, 0)

        // Complete both server reads before changing any local sync state. A network
        // or permission failure leaves the preserved records exactly as they were.
        val cloudBookings = cloud.existingLegacyRecordIds(
            "bookings", if (uncheckedPayments.isEmpty()) uncheckedBookings else bookingIds
        )
        val cloudPayments = cloud.existingLegacyRecordIds("bookingPayments", uncheckedPayments)
        val missingBookings = uncheckedBookings.filterNot(cloudBookings::contains)
        val missingPayments = uncheckedPayments.filterNot(cloudPayments::contains)

        var stagedBookings = 0
        var stagedPayments = 0
        db.withTransaction {
            uncheckedBookings.filter(cloudBookings::contains).forEach {
                markStaged("booking_present", it)
            }
            uncheckedPayments.filter(cloudPayments::contains).forEach {
                markStaged("payment_present", it)
            }
            val pendingBookingIds = db.bookingSyncOutboxDao().getPending(hotelRemoteId)
                .mapTo(mutableSetOf()) { it.bookingRemoteId }
            missingBookings.forEach { bookingId ->
                val booking = db.bookingDao().getByRemoteId(bookingId)
                    ?.takeIf { it.hotelRemoteId == hotelRemoteId && !it.isDeleted }
                    ?: return@forEach
                if (bookingId in pendingBookingIds) return@forEach
                val lines = db.bookingFinancialLineDao()
                    .getAllLinesForBooking(hotelRemoteId, bookingId)
                if (lines.none { !it.isDeleted } &&
                    (abs(booking.propertyTax) > 0.005 ||
                        abs(booking.grossCharges - booking.roomRevenue) > 0.02)) {
                    db.bookingDao().upsert(
                        booking.copy(
                            syncState = SyncState.FAILED,
                            lastSyncError = "Old booking is safe on this phone, but its tax breakdown needs review before cloud recovery."
                        )
                    )
                    markStaged("booking_review", bookingId)
                    return@forEach
                }
                val operationId = UUID.nameUUIDFromBytes(
                    "play-1.0.3-recovery|$hotelRemoteId|$bookingId".toByteArray(StandardCharsets.UTF_8)
                ).toString()
                db.bookingSyncOutboxDao().upsert(
                    BookingSyncOutboxEntity(
                        operationId = operationId,
                        hotelRemoteId = hotelRemoteId,
                        bookingRemoteId = bookingId,
                        changeSetJson = BookingChangeSet.create(
                            previous = null,
                            requested = booking,
                            previousLines = emptyList(),
                            requestedLines = lines
                        ).toJson(),
                        createdAt = booking.updatedAt.takeIf { it > 0 }
                            ?: System.currentTimeMillis()
                    )
                )
                db.bookingDao().upsert(
                    booking.copy(syncState = SyncState.PENDING, lastSyncError = null)
                )
                markStaged("booking", bookingId)
                pendingBookingIds += bookingId
                stagedBookings++
            }

            missingPayments.forEach { paymentId ->
                val payment = db.bookingPaymentDao().getByRemoteId(paymentId)
                    ?.takeIf { it.hotelRemoteId == hotelRemoteId && !it.isDeleted }
                    ?: return@forEach
                val parentExistsOrQueued = payment.bookingRemoteId in cloudBookings ||
                    payment.bookingRemoteId in pendingBookingIds
                if (!parentExistsOrQueued) return@forEach
                // A legacy payment can say SYNCED despite being absent remotely.
                // Reset the base revision only after a server-only missing result.
                db.bookingPaymentDao().upsert(
                    payment.copy(
                        syncState = SyncState.PENDING,
                        baseRevision = 0,
                        lastSyncError = null
                    )
                )
                markStaged("payment", paymentId)
                stagedPayments++
            }
        }
        return StageResult(stagedBookings, stagedPayments)
    }

    private fun hasLegacyBackup(): Boolean = db.openHelper.readableDatabase.query(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'legacy_v11_backup_bookings'"
    ).use { it.moveToFirst() }

    private fun backupIds(table: String): List<String> {
        require(table == "legacy_v11_backup_bookings" ||
            table == "legacy_v11_backup_booking_payments")
        return db.openHelper.readableDatabase.query(
            "SELECT remoteId FROM $table WHERE hotelRemoteId = ? AND isDeleted = 0",
            arrayOf(hotelRemoteId)
        ).use { rows ->
            buildList {
                while (rows.moveToNext()) add(rows.getString(0))
            }
        }
    }

    private fun stagedIds(kind: String): Set<String> = db.openHelper.readableDatabase.query(
        "SELECT remoteId FROM legacy_v11_recovery_stage WHERE kind = ? AND hotelRemoteId = ?",
        arrayOf(kind, hotelRemoteId)
    ).use { rows ->
        buildSet { while (rows.moveToNext()) add(rows.getString(0)) }
    }

    private fun markStaged(kind: String, remoteId: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT OR IGNORE INTO legacy_v11_recovery_stage(kind, hotelRemoteId, remoteId) VALUES (?, ?, ?)",
            arrayOf(kind, hotelRemoteId, remoteId)
        )
    }
}

internal interface LegacyRecordPresence {
    suspend fun existingLegacyRecordIds(collection: String, remoteIds: List<String>): Set<String>
}
