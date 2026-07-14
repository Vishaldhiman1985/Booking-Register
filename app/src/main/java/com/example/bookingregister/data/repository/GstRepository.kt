package com.example.bookingregister.data.repository

import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import com.example.bookingregister.data.AppDatabase
import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.RoomGstSlabEntity
import com.example.bookingregister.data.sync.CloudSyncManager
import com.example.bookingregister.data.sync.CloudWriteResult
import com.example.bookingregister.data.sync.SyncAcknowledgementPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class GstRepository(
    private val db: AppDatabase,
    private val hotelRemoteId: String,
    private val scope: CoroutineScope
) {
    private val roomGstSlabDao = db.roomGstSlabDao()
    private val cloudSyncManager = CloudSyncManager(hotelRemoteId)

    fun observeRoomGstSlabs(): LiveData<List<RoomGstSlabEntity>> =
        roomGstSlabDao.observeSlabs(hotelRemoteId)

    suspend fun getActiveRoomGstSlabs(bookingMillis: Long): List<RoomGstSlabEntity> =
        roomGstSlabDao.getActiveSlabs(hotelRemoteId, bookingMillis)

    fun startRealtimeSync() {
        scope.launch {
            cloudSyncManager.startRoomGstSlabListener(
                // GST slabs are few and legally important. Always reconcile the full cloud set.
                sinceUpdatedAt = null,
                onSlabsChanged = { remoteSlabs ->
                    scope.launch {
                        db.withTransaction {
                            remoteSlabs.forEach { remote ->
                                val local = roomGstSlabDao.getByRemoteId(remote.remoteId)
                                val localHasUnsyncedWork = local?.syncState in setOf(SyncState.PENDING, SyncState.FAILED)
                                val remoteIsNewer = local == null ||
                                    remote.revision > local.revision ||
                                    remote.updatedAt >= local.updatedAt
                                if (!localHasUnsyncedWork && remoteIsNewer) {
                                    roomGstSlabDao.upsert(remote.copy(localId = local?.localId ?: 0).markSynced())
                                }
                            }
                        }
                        retryUnsyncedSlabs()
                    }
                }
            )
            retryUnsyncedSlabs()
        }
    }

    fun stopRealtimeSync() {
        cloudSyncManager.stopRoomGstSlabListener()
    }

    suspend fun ensureDefaultRoomGstSlabs() {
        if (roomGstSlabDao.countAll(hotelRemoteId) > 0) return

        // Never create local defaults before checking the cloud. Otherwise a fresh/reinstalled
        // device could silently add defaults beside the hotel's real custom GST slabs.
        val cloudSlabs = runCatching { cloudSyncManager.fetchRoomGstSlabsOnce() }
            .getOrElse { return }
        if (cloudSlabs.isNotEmpty()) {
            db.withTransaction {
                cloudSlabs.forEach { remote ->
                    val local = roomGstSlabDao.getByRemoteId(remote.remoteId)
                    roomGstSlabDao.upsert(remote.copy(localId = local?.localId ?: 0).markSynced())
                }
            }
            return
        }

        val now = System.currentTimeMillis()
        val defaults = listOf(
            defaultSlab(
                remoteId = "default_room_gst_up_to_1000",
                name = "Room tariff up to Rs 1,000",
                min = 0.0,
                max = 1000.0,
                gst = 0.0,
                cgst = 0.0,
                sgst = 0.0,
                now = now
            ),
            defaultSlab(
                remoteId = "default_room_gst_1000_to_7500",
                name = "Room tariff above Rs 1,000 up to Rs 7,500",
                min = 1000.01,
                max = 7500.0,
                gst = 5.0,
                cgst = 2.5,
                sgst = 2.5,
                now = now
            ),
            defaultSlab(
                remoteId = "default_room_gst_above_7500",
                name = "Room tariff above Rs 7,500",
                min = 7500.01,
                max = null,
                gst = 18.0,
                cgst = 9.0,
                sgst = 9.0,
                now = now
            )
        )
        defaults.forEach { roomGstSlabDao.upsert(it) }
        defaults.forEach { pushAndMark(it) }
    }

    suspend fun saveRoomGstSlab(slab: RoomGstSlabEntity) {
        requireTaxComponents(slab)
        val pending = slab.copy(
            updatedAt = System.currentTimeMillis(),
            syncState = SyncState.PENDING,
            lastSyncError = null,
            baseRevision = slab.baseRevision.takeIf { it > 0 } ?: slab.revision
        )
        roomGstSlabDao.upsert(pending)
        pushAndMark(pending)
    }

    suspend fun deleteRoomGstSlab(slab: RoomGstSlabEntity) {
        val pending = slab.copy(
            isDeleted = true,
            isActive = false,
            updatedAt = System.currentTimeMillis(),
            syncState = SyncState.PENDING,
            lastSyncError = null,
            baseRevision = slab.baseRevision.takeIf { it > 0 } ?: slab.revision
        )
        roomGstSlabDao.upsert(pending)
        pushAndMark(pending)
    }

    suspend fun retryUnsyncedSlabs() {
        roomGstSlabDao.getUnsyncedSlabs(hotelRemoteId).forEach { pushAndMark(it) }
    }

    private suspend fun pushAndMark(sent: RoomGstSlabEntity) {
        runCatching { cloudSyncManager.pushRoomGstSlab(sent) }
            .onSuccess { result ->
                val current = roomGstSlabDao.getByRemoteId(sent.remoteId) ?: return@onSuccess
                val unchanged = SyncAcknowledgementPolicy.isSameVersion(
                    sent.updatedAt, sent.revision, sent.baseRevision,
                    current.updatedAt, current.revision, current.baseRevision
                )
                roomGstSlabDao.upsert(
                    if (unchanged) current.markSynced(result)
                    else current.copy(
                        revision = result.revision,
                        baseRevision = result.revision,
                        syncState = SyncState.PENDING,
                        lastSyncError = null
                    )
                )
            }
            .onFailure { error ->
                val current = roomGstSlabDao.getByRemoteId(sent.remoteId) ?: return@onFailure
                if (current.updatedAt == sent.updatedAt) {
                    roomGstSlabDao.upsert(
                        current.copy(
                            syncState = SyncState.FAILED,
                            lastSyncError = error.message ?: error.javaClass.simpleName
                        )
                    )
                }
            }
    }

    private fun requireTaxComponents(slab: RoomGstSlabEntity) {
        val componentTotal = slab.cgstRatePercent + slab.sgstRatePercent + slab.cessRatePercent
        require(kotlin.math.abs(componentTotal - slab.gstRatePercent) <= 0.001) {
            "CGST + SGST + cess must equal total GST"
        }
        require(slab.minGrossAmount >= 0.0) { "Minimum tariff cannot be negative" }
        require(slab.maxGrossAmount == null || slab.maxGrossAmount >= slab.minGrossAmount) {
            "Maximum tariff must be greater than minimum tariff"
        }
    }

    private fun defaultSlab(
        remoteId: String,
        name: String,
        min: Double,
        max: Double?,
        gst: Double,
        cgst: Double,
        sgst: Double,
        now: Long
    ) = RoomGstSlabEntity(
        remoteId = remoteId,
        hotelRemoteId = hotelRemoteId,
        slabName = name,
        minGrossAmount = min,
        maxGrossAmount = max,
        gstRatePercent = gst,
        cgstRatePercent = cgst,
        sgstRatePercent = sgst,
        hsnSacCode = "996311",
        effectiveFromMillis = 0L,
        updatedAt = now,
        syncState = SyncState.PENDING
    )

    private fun RoomGstSlabEntity.markSynced(result: CloudWriteResult? = null): RoomGstSlabEntity {
        val acceptedRevision = result?.revision ?: revision
        return copy(
            syncState = SyncState.SYNCED,
            lastSyncError = null,
            lastSyncedAt = System.currentTimeMillis(),
            revision = acceptedRevision,
            baseRevision = acceptedRevision,
            updatedByUid = result?.updatedByUid ?: updatedByUid
        )
    }
}
