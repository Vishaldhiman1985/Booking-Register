package com.example.bookingregister.data.repository

import androidx.lifecycle.LiveData
import com.example.bookingregister.data.AppDatabase
import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.RoomGstSlabEntity
import java.util.UUID

class GstRepository(
    private val db: AppDatabase,
    private val hotelRemoteId: String
) {
    private val roomGstSlabDao = db.roomGstSlabDao()

    fun observeRoomGstSlabs(): LiveData<List<RoomGstSlabEntity>> {
        return roomGstSlabDao.observeSlabs(hotelRemoteId)
    }

    suspend fun getActiveRoomGstSlabs(bookingMillis: Long): List<RoomGstSlabEntity> {
        return roomGstSlabDao.getActiveSlabs(
            hotelRemoteId = hotelRemoteId,
            bookingMillis = bookingMillis
        )
    }

    suspend fun ensureDefaultRoomGstSlabs() {
        val existing = roomGstSlabDao.getActiveSlabs(
            hotelRemoteId = hotelRemoteId,
            bookingMillis = System.currentTimeMillis()
        )

        if (existing.isNotEmpty()) return

        val now = System.currentTimeMillis()

        val defaults = listOf(
            RoomGstSlabEntity(
                remoteId = UUID.randomUUID().toString(),
                hotelRemoteId = hotelRemoteId,
                slabName = "Room tariff up to Rs 1,000",
                minGrossAmount = 0.0,
                maxGrossAmount = 1000.0,
                gstRatePercent = 0.0,
                cgstRatePercent = 0.0,
                sgstRatePercent = 0.0,
                hsnSacCode = "996311",
                effectiveFromMillis = 0L,
                updatedAt = now,
                syncState = SyncState.PENDING
            ),
            RoomGstSlabEntity(
                remoteId = UUID.randomUUID().toString(),
                hotelRemoteId = hotelRemoteId,
                slabName = "Room tariff above Rs 1,000 up to Rs 7,500",
                minGrossAmount = 1000.01,
                maxGrossAmount = 7500.0,
                gstRatePercent = 5.0,
                cgstRatePercent = 2.5,
                sgstRatePercent = 2.5,
                hsnSacCode = "996311",
                effectiveFromMillis = 0L,
                updatedAt = now,
                syncState = SyncState.PENDING
            ),
            RoomGstSlabEntity(
                remoteId = UUID.randomUUID().toString(),
                hotelRemoteId = hotelRemoteId,
                slabName = "Room tariff above Rs 7,500",
                minGrossAmount = 7500.01,
                maxGrossAmount = null,
                gstRatePercent = 18.0,
                cgstRatePercent = 9.0,
                sgstRatePercent = 9.0,
                hsnSacCode = "996311",
                effectiveFromMillis = 0L,
                updatedAt = now,
                syncState = SyncState.PENDING
            )
        )

        defaults.forEach { roomGstSlabDao.upsert(it) }
    }

    suspend fun saveRoomGstSlab(slab: RoomGstSlabEntity) {
        roomGstSlabDao.upsert(
            slab.copy(
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING,
                lastSyncError = null,
                baseRevision = slab.baseRevision.takeIf { it > 0 } ?: slab.revision
            )
        )
    }

    suspend fun deleteRoomGstSlab(slab: RoomGstSlabEntity) {
        roomGstSlabDao.softDelete(
            remoteId = slab.remoteId,
            now = System.currentTimeMillis()
        )
    }
}