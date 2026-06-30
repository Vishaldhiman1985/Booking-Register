package com.example.bookingregister.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.example.bookingregister.data.AppDatabase
import com.example.bookingregister.data.entities.FoodBillEntity
import com.example.bookingregister.data.entities.FoodBillItemEntity
import com.example.bookingregister.data.entities.FoodGstCategoryEntity
import com.example.bookingregister.data.entities.FoodMenuItemEntity
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderItemEntity
import com.example.bookingregister.data.entities.ServiceMenuItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class FoodRealtimeSyncService(
    private val db: AppDatabase,
    private val hotelRemoteId: String,
    private val scope: CoroutineScope,
    private val cloudSyncManager: CloudSyncManager
) {

    fun start(
        onSyncError: (String, Throwable) -> Unit,
        onAfterRemoteChanges: suspend () -> Unit
    ) {
        scope.launch {
            startFoodMenuListener(onSyncError, onAfterRemoteChanges)
            startServiceMenuListener(onSyncError, onAfterRemoteChanges)
            startFoodGstCategoryListener(onSyncError, onAfterRemoteChanges)
            startFoodOrderListener(onSyncError, onAfterRemoteChanges)
            startFoodOrderItemListener(onSyncError, onAfterRemoteChanges)
            startFoodBillListener(onSyncError, onAfterRemoteChanges)
            startFoodBillItemListener(onSyncError, onAfterRemoteChanges)
        }
    }

    private suspend fun startServiceMenuListener(
        onSyncError: (String, Throwable) -> Unit,
        onAfterRemoteChanges: suspend () -> Unit
    ) {
        val dao = db.serviceMenuItemDao()
        val since = syncBoundary(
            localCount = dao.countAllItems(hotelRemoteId),
            maxUpdatedAt = dao.maxUpdatedAt(hotelRemoteId)
        )

        cloudSyncManager.startServiceMenuListener(
            sinceUpdatedAt = since,
            onItemsChanged = { items ->
                scope.launch {
                    items.forEach { upsertRemoteServiceMenuItemIfNewer(it) }
                    onAfterRemoteChanges()
                }
            },
            onSyncError = { onSyncError("Service menu", it) }
        )
    }

    private suspend fun startFoodMenuListener(
        onSyncError: (String, Throwable) -> Unit,
        onAfterRemoteChanges: suspend () -> Unit
    ) {
        val dao = db.foodMenuItemDao()
        val since = syncBoundary(
            localCount = dao.countAllItems(hotelRemoteId),
            maxUpdatedAt = dao.maxUpdatedAt(hotelRemoteId)
        )

        cloudSyncManager.startFoodMenuListener(
            sinceUpdatedAt = since,
            onItemsChanged = { items ->
                scope.launch {
                    db.withTransaction {
                        items.forEach { upsertRemoteFoodMenuItemIfNewer(it) }
                        tombstoneDuplicateFoodMenuItems()
                    }
                    onAfterRemoteChanges()
                }
            },
            onSyncError = { onSyncError("Food menu", it) }
        )
    }

    private suspend fun startFoodGstCategoryListener(
        onSyncError: (String, Throwable) -> Unit,
        onAfterRemoteChanges: suspend () -> Unit
    ) {
        val dao = db.foodGstCategoryDao()
        val since = syncBoundary(
            localCount = dao.countAllCategories(hotelRemoteId),
            maxUpdatedAt = dao.maxUpdatedAt(hotelRemoteId)
        )

        cloudSyncManager.startFoodGstCategoryListener(
            sinceUpdatedAt = since,
            onCategoriesChanged = { categories ->
                scope.launch {
                    categories.forEach { upsertRemoteFoodGstCategoryIfNewer(it) }
                    onAfterRemoteChanges()
                }
            },
            onSyncError = { onSyncError("Food GST", it) }
        )
    }

    private suspend fun startFoodOrderListener(
        onSyncError: (String, Throwable) -> Unit,
        onAfterRemoteChanges: suspend () -> Unit
    ) {
        val dao = db.foodOrderDao()
        val since = syncBoundary(
            localCount = dao.countAllOrders(hotelRemoteId),
            maxUpdatedAt = dao.maxUpdatedAt(hotelRemoteId)
        )

        cloudSyncManager.startFoodOrderListener(
            sinceUpdatedAt = since,
            onOrdersChanged = { orders ->
                scope.launch {
                    orders.forEach { upsertRemoteFoodOrderIfNewer(it) }
                    onAfterRemoteChanges()
                }
            },
            onSyncError = { onSyncError("Food order", it) }
        )
    }

    private suspend fun startFoodOrderItemListener(
        onSyncError: (String, Throwable) -> Unit,
        onAfterRemoteChanges: suspend () -> Unit
    ) {
        val dao = db.foodOrderItemDao()
        val since = syncBoundary(
            localCount = dao.countAllItems(hotelRemoteId),
            maxUpdatedAt = dao.maxUpdatedAt(hotelRemoteId)
        )

        cloudSyncManager.startFoodOrderItemListener(
            sinceUpdatedAt = since,
            onItemsChanged = { items ->
                scope.launch {
                    items.forEach { upsertRemoteFoodOrderItemIfNewer(it) }
                    onAfterRemoteChanges()
                }
            },
            onSyncError = { onSyncError("Food order item", it) }
        )
    }

    private suspend fun startFoodBillListener(
        onSyncError: (String, Throwable) -> Unit,
        onAfterRemoteChanges: suspend () -> Unit
    ) {
        val dao = db.foodBillDao()
        val since = syncBoundary(
            localCount = dao.countAllBills(hotelRemoteId),
            maxUpdatedAt = dao.maxUpdatedAt(hotelRemoteId)
        )

        cloudSyncManager.startFoodBillListener(
            sinceUpdatedAt = since,
            onBillsChanged = { bills ->
                scope.launch {
                    bills.forEach { upsertRemoteFoodBillIfNewer(it) }
                    onAfterRemoteChanges()
                }
            },
            onSyncError = { onSyncError("Food bill", it) }
        )
    }

    private suspend fun startFoodBillItemListener(
        onSyncError: (String, Throwable) -> Unit,
        onAfterRemoteChanges: suspend () -> Unit
    ) {
        val dao = db.foodBillItemDao()
        val since = syncBoundary(
            localCount = dao.countAllItems(hotelRemoteId),
            maxUpdatedAt = dao.maxUpdatedAt(hotelRemoteId)
        )

        cloudSyncManager.startFoodBillItemListener(
            sinceUpdatedAt = since,
            onItemsChanged = { items ->
                scope.launch {

                    items.forEach { upsertRemoteFoodBillItemIfNewer(it) }
                    onAfterRemoteChanges()
                }
            },
            onSyncError = { onSyncError("Food bill item", it) }
        )
    }

    private suspend fun upsertRemoteFoodMenuItemIfNewer(remote: FoodMenuItemEntity) {
        val dao = db.foodMenuItemDao()
        val local = dao.getByRemoteId(remote.remoteId)

        if (local == null || shouldAcceptRemoteFoodEntity(
                localSyncState = local.syncState,
                localRevision = local.revision,
                localUpdatedAt = local.updatedAt,
                remoteRevision = remote.revision,
                remoteUpdatedAt = remote.updatedAt
            )
        ) {
            dao.upsert(remote.copy(localId = local?.localId ?: 0).markFoodSynced())
        }
    }

    private suspend fun upsertRemoteServiceMenuItemIfNewer(remote: ServiceMenuItemEntity) {
        val dao = db.serviceMenuItemDao()
        val local = dao.getByRemoteId(remote.remoteId)

        if (local == null || shouldAcceptRemoteFoodEntity(
                localSyncState = local.syncState,
                localRevision = local.revision,
                localUpdatedAt = local.updatedAt,
                remoteRevision = remote.revision,
                remoteUpdatedAt = remote.updatedAt
            )
        ) {
            dao.upsert(remote.copy(localId = local?.localId ?: 0).markFoodSynced())
        }
    }

    private suspend fun upsertRemoteFoodGstCategoryIfNewer(remote: FoodGstCategoryEntity) {
        val dao = db.foodGstCategoryDao()
        val local = dao.getByRemoteId(remote.remoteId)

        if (local == null || shouldAcceptRemoteFoodEntity(
                localSyncState = local.syncState,
                localRevision = local.revision,
                localUpdatedAt = local.updatedAt,
                remoteRevision = remote.revision,
                remoteUpdatedAt = remote.updatedAt
            )
        ) {
            dao.upsert(remote.copy(localId = local?.localId ?: 0).markFoodSynced())
        }
    }

    private suspend fun upsertRemoteFoodOrderIfNewer(remote: FoodOrderEntity) {
        val dao = db.foodOrderDao()
        val local = dao.getByRemoteId(remote.remoteId)

        if (local == null || shouldAcceptRemoteFoodEntity(
                localSyncState = local.syncState,
                localRevision = local.revision,
                localUpdatedAt = local.updatedAt,
                remoteRevision = remote.revision,
                remoteUpdatedAt = remote.updatedAt
            )
        ) {
            dao.upsert(remote.copy(localId = local?.localId ?: 0).markFoodSynced())
        }
    }

    private suspend fun upsertRemoteFoodOrderItemIfNewer(remote: FoodOrderItemEntity) {
        val dao = db.foodOrderItemDao()
        val local = dao.getByRemoteId(remote.remoteId)

        if (local == null || shouldAcceptRemoteFoodEntity(
                localSyncState = local.syncState,
                localRevision = local.revision,
                localUpdatedAt = local.updatedAt,
                remoteRevision = remote.revision,
                remoteUpdatedAt = remote.updatedAt
            )
        ) {
            dao.upsert(remote.copy(localId = local?.localId ?: 0).markFoodSynced())
        }
    }

    private suspend fun upsertRemoteFoodBillIfNewer(remote: FoodBillEntity) {
        val dao = db.foodBillDao()
        val local = dao.getByRemoteId(remote.remoteId)

        dao.upsert(
            remote.copy(localId = local?.localId ?: 0).markFoodSynced()
        )
    }

    private suspend fun upsertRemoteFoodBillItemIfNewer(remote: FoodBillItemEntity) {
        val dao = db.foodBillItemDao()
        val local = dao.getByRemoteId(remote.remoteId)

        dao.upsert(
            remote.copy(localId = local?.localId ?: 0).markFoodSynced()
        )
    }

    private suspend fun tombstoneDuplicateFoodMenuItems() {
        val dao = db.foodMenuItemDao()
        FoodMenuSyncSanitizer
            .tombstoneDuplicateActiveItems(
                items = dao.getItems(hotelRemoteId),
                nowMillis = System.currentTimeMillis()
            )
            .forEach { duplicate ->
                dao.upsert(duplicate)
            }
    }

    private fun logSyncFailure(operation: String, throwable: Throwable) {
        Log.e("FoodRealtimeSync", "$operation failed: ${throwable.message}", throwable)
    }
}
