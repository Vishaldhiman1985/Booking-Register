package com.example.bookingregister.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.example.bookingregister.data.AppDatabase
import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.FoodMenuItemEntity
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderItemEntity
import com.example.bookingregister.data.entities.FoodBillEntity
import com.example.bookingregister.data.entities.FoodBillItemEntity
import com.example.bookingregister.data.entities.FoodGstCategoryEntity
import com.example.bookingregister.data.entities.ServiceMenuItemEntity

class FoodBillingSyncService(
    private val db: AppDatabase,
    private val hotelRemoteId: String
) {

    private val foodMenuItemDao = db.foodMenuItemDao()
    private val serviceMenuItemDao = db.serviceMenuItemDao()
    private val cloudSyncManager = CloudSyncManager(hotelRemoteId)

    suspend fun pushFoodMenuItemAndMark(item: FoodMenuItemEntity) {
        runCatching { cloudSyncManager.pushFoodMenuItem(item) }
            .onSuccess { result -> foodMenuItemDao.upsert(item.markFoodSynced(result)) }
            .onFailure {
                foodMenuItemDao.upsert(item.markFoodFailed(it))
                logSyncFailure("pushFoodMenuItem", it)
            }
    }

    suspend fun pushServiceMenuItemAndMark(item: ServiceMenuItemEntity) {
        runCatching { cloudSyncManager.pushServiceMenuItem(item) }
            .onSuccess { result -> serviceMenuItemDao.upsert(item.markFoodSynced(result)) }
            .onFailure {
                serviceMenuItemDao.upsert(item.markFoodFailed(it))
                logSyncFailure("pushServiceMenuItem", it)
            }
    }

    suspend fun pushFoodGstCategoryAndMark(category: FoodGstCategoryEntity) {
        runCatching { cloudSyncManager.pushFoodGstCategory(category) }
            .onSuccess { result -> db.foodGstCategoryDao().upsert(category.markFoodSynced(result)) }
            .onFailure {
                db.foodGstCategoryDao().upsert(category.markFoodFailed(it))
                logSyncFailure("pushFoodGstCategory", it)
            }
    }

    /**
     * Food order header and every item are one cloud transaction.
     * Never call the old individual order/item push methods from retry code.
     */
    suspend fun pushFoodOrderAggregateAndMark(orderRemoteId: String) {
        val order = db.foodOrderDao().getByRemoteId(orderRemoteId) ?: return
        val items = db.foodOrderItemDao().getAllItemsForOrder(hotelRemoteId, orderRemoteId)
        val operationVersion = maxOf(order.updatedAt, items.maxOfOrNull { it.updatedAt } ?: 0L)
        val operationId = "food-order:${order.remoteId}:$operationVersion"

        runCatching {
            cloudSyncManager.pushFoodOrderAggregate(operationId, order, items)
        }.onSuccess { result ->
            db.withTransaction {
                val currentOrder = db.foodOrderDao().getByRemoteId(order.remoteId)
                if (currentOrder != null) {
                    val unchanged = sameVersion(order, currentOrder)
                    db.foodOrderDao().upsert(
                        if (unchanged) {
                            currentOrder.markFoodSynced(
                                CloudWriteResult(result.orderRevision, result.updatedByUid)
                            )
                        } else {
                            currentOrder.copy(
                                revision = result.orderRevision,
                                baseRevision = result.orderRevision,
                                syncState = SyncState.PENDING,
                                lastSyncError = null
                            )
                        }
                    )
                }
                items.forEach { sentItem ->
                    val revision = result.orderItemRevisions[sentItem.remoteId] ?: return@forEach
                    val currentItem = db.foodOrderItemDao().getByRemoteId(sentItem.remoteId) ?: return@forEach
                    val unchanged = sameVersion(sentItem, currentItem)
                    db.foodOrderItemDao().upsert(
                        if (unchanged) {
                            currentItem.markFoodSynced(CloudWriteResult(revision, result.updatedByUid))
                        } else {
                            currentItem.copy(
                                revision = revision,
                                baseRevision = revision,
                                syncState = SyncState.PENDING,
                                lastSyncError = null
                            )
                        }
                    )
                }
            }
        }.onFailure { error ->
            db.withTransaction {
                val currentOrder = db.foodOrderDao().getByRemoteId(order.remoteId)
                if (currentOrder != null && currentOrder.updatedAt == order.updatedAt) {
                    db.foodOrderDao().upsert(currentOrder.markFoodFailed(error))
                }
                items.forEach { sentItem ->
                    val currentItem = db.foodOrderItemDao().getByRemoteId(sentItem.remoteId) ?: return@forEach
                    if (currentItem.updatedAt == sentItem.updatedAt) {
                        db.foodOrderItemDao().upsert(currentItem.markFoodFailed(error))
                    }
                }
            }
            logSyncFailure("pushFoodOrderAggregate", error)
        }
    }

    /** Bill, bill items, linked orders/items, and linked charges commit together. */
    suspend fun pushFoodBillAggregateAndMark(billRemoteId: String) {
        val bill = db.foodBillDao().getByRemoteId(billRemoteId) ?: return
        val billItems = db.foodBillItemDao().getAllItemsForBill(hotelRemoteId, billRemoteId)
        val orderIds = bill.orderRemoteIds
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val orders = if (orderIds.isEmpty()) emptyList() else db.foodOrderDao().getByRemoteIds(orderIds)
        val orderItems = orders.flatMap {
            db.foodOrderItemDao().getAllItemsForOrder(hotelRemoteId, it.remoteId)
        }
        val charges = db.bookingAccountingChargeDao().getChargesForFinalBill(hotelRemoteId, billRemoteId)
        val operationVersion = listOfNotNull(
            bill.updatedAt,
            billItems.maxOfOrNull { it.updatedAt },
            orders.maxOfOrNull { it.updatedAt },
            orderItems.maxOfOrNull { it.updatedAt },
            charges.maxOfOrNull { it.updatedAt }
        ).maxOrNull() ?: bill.updatedAt
        val operationId = "food-bill:${bill.remoteId}:$operationVersion"

        runCatching {
            cloudSyncManager.pushFoodBillAggregate(
                operationId = operationId,
                bill = bill,
                billItems = billItems,
                orders = orders,
                orderItems = orderItems,
                accountingCharges = charges
            )
        }.onSuccess { result ->
            db.withTransaction {
                db.foodBillDao().getByRemoteId(bill.remoteId)?.let { current ->
                    db.foodBillDao().upsert(
                        if (sameVersion(bill, current)) {
                            current.markFoodSynced(CloudWriteResult(result.billRevision, result.updatedByUid))
                        } else {
                            current.copy(
                                revision = result.billRevision,
                                baseRevision = result.billRevision,
                                syncState = SyncState.PENDING,
                                lastSyncError = null
                            )
                        }
                    )
                }
                billItems.forEach { sent ->
                    acknowledgeBillItem(sent, result.foodBillItemRevisions[sent.remoteId], result.updatedByUid)
                }
                orders.forEach { sent ->
                    acknowledgeOrder(sent, result.foodOrderRevisions[sent.remoteId], result.updatedByUid)
                }
                orderItems.forEach { sent ->
                    acknowledgeOrderItem(sent, result.foodOrderItemRevisions[sent.remoteId], result.updatedByUid)
                }
                charges.forEach { sent ->
                    val revision = result.accountingChargeRevisions[sent.remoteId] ?: return@forEach
                    val current = db.bookingAccountingChargeDao().getByRemoteId(sent.remoteId) ?: return@forEach
                    val unchanged = SyncAcknowledgementPolicy.isSameVersion(
                        sent.updatedAt, sent.revision, sent.baseRevision,
                        current.updatedAt, current.revision, current.baseRevision
                    )
                    db.bookingAccountingChargeDao().upsert(
                        if (unchanged) current.copy(
                            syncState = SyncState.SYNCED,
                            lastSyncError = null,
                            lastSyncedAt = System.currentTimeMillis(),
                            revision = revision,
                            baseRevision = revision,
                            updatedByUid = result.updatedByUid ?: current.updatedByUid
                        )
                        else current.copy(
                            revision = revision,
                            baseRevision = revision,
                            syncState = SyncState.PENDING,
                            lastSyncError = null
                        )
                    )
                }
            }
        }.onFailure { error ->
            db.withTransaction {
                db.foodBillDao().getByRemoteId(bill.remoteId)?.let { current ->
                    if (current.updatedAt == bill.updatedAt) db.foodBillDao().upsert(current.markFoodFailed(error))
                }
                billItems.forEach { sent ->
                    db.foodBillItemDao().getByRemoteId(sent.remoteId)?.let { current ->
                        if (current.updatedAt == sent.updatedAt) db.foodBillItemDao().upsert(current.markFoodFailed(error))
                    }
                }
            }
            logSyncFailure("pushFoodBillAggregate", error)
        }
    }

    suspend fun retryFailedFoodSync() {
        foodMenuItemDao.getUnsyncedItems(hotelRemoteId).forEach { pushFoodMenuItemAndMark(it) }
        serviceMenuItemDao.getUnsyncedItems(hotelRemoteId).forEach { pushServiceMenuItemAndMark(it) }
        db.foodGstCategoryDao().getUnsyncedCategories(hotelRemoteId).forEach { pushFoodGstCategoryAndMark(it) }

        // Bills first because a bill aggregate owns its linked orders/items/charges.
        val unsyncedBills = db.foodBillDao().getUnsyncedBills(hotelRemoteId)
        val unsyncedBillIds = unsyncedBills.mapTo(mutableSetOf()) { it.remoteId }
        db.foodBillItemDao().getUnsyncedItems(hotelRemoteId).forEach { unsyncedBillIds += it.billRemoteId }
        unsyncedBillIds.forEach { pushFoodBillAggregateAndMark(it) }

        val billedOrderIds = mutableSetOf<String>()
        unsyncedBillIds.forEach { billId ->
            val orderIds = db.foodBillDao().getByRemoteId(billId)?.orderRemoteIds
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            billedOrderIds.addAll(orderIds)
        }
        val unsyncedOrderIds = db.foodOrderDao().getUnsyncedOrders(hotelRemoteId)
            .mapTo(mutableSetOf()) { it.remoteId }
        db.foodOrderItemDao().getUnsyncedItems(hotelRemoteId).forEach { unsyncedOrderIds += it.orderRemoteId }
        unsyncedOrderIds
            .filterNot { it in billedOrderIds }
            .forEach { pushFoodOrderAggregateAndMark(it) }
    }

    fun stop() {
        cloudSyncManager.stop()
    }

    private suspend fun acknowledgeBillItem(
        sent: FoodBillItemEntity,
        revision: Long?,
        updatedByUid: String?
    ) {
        revision ?: return
        val current = db.foodBillItemDao().getByRemoteId(sent.remoteId) ?: return
        db.foodBillItemDao().upsert(
            if (sameVersion(sent, current)) current.markFoodSynced(CloudWriteResult(revision, updatedByUid))
            else current.copy(
                revision = revision,
                baseRevision = revision,
                syncState = SyncState.PENDING,
                lastSyncError = null
            )
        )
    }

    private suspend fun acknowledgeOrder(
        sent: FoodOrderEntity,
        revision: Long?,
        updatedByUid: String?
    ) {
        revision ?: return
        val current = db.foodOrderDao().getByRemoteId(sent.remoteId) ?: return
        db.foodOrderDao().upsert(
            if (sameVersion(sent, current)) current.markFoodSynced(CloudWriteResult(revision, updatedByUid))
            else current.copy(
                revision = revision,
                baseRevision = revision,
                syncState = SyncState.PENDING,
                lastSyncError = null
            )
        )
    }

    private suspend fun acknowledgeOrderItem(
        sent: FoodOrderItemEntity,
        revision: Long?,
        updatedByUid: String?
    ) {
        revision ?: return
        val current = db.foodOrderItemDao().getByRemoteId(sent.remoteId) ?: return
        db.foodOrderItemDao().upsert(
            if (sameVersion(sent, current)) current.markFoodSynced(CloudWriteResult(revision, updatedByUid))
            else current.copy(
                revision = revision,
                baseRevision = revision,
                syncState = SyncState.PENDING,
                lastSyncError = null
            )
        )
    }

    private fun sameVersion(sent: FoodOrderEntity, current: FoodOrderEntity): Boolean =
        SyncAcknowledgementPolicy.isSameVersion(
            sent.updatedAt, sent.revision, sent.baseRevision,
            current.updatedAt, current.revision, current.baseRevision
        )

    private fun sameVersion(sent: FoodOrderItemEntity, current: FoodOrderItemEntity): Boolean =
        SyncAcknowledgementPolicy.isSameVersion(
            sent.updatedAt, sent.revision, sent.baseRevision,
            current.updatedAt, current.revision, current.baseRevision
        )

    private fun sameVersion(sent: FoodBillEntity, current: FoodBillEntity): Boolean =
        SyncAcknowledgementPolicy.isSameVersion(
            sent.updatedAt, sent.revision, sent.baseRevision,
            current.updatedAt, current.revision, current.baseRevision
        )

    private fun sameVersion(sent: FoodBillItemEntity, current: FoodBillItemEntity): Boolean =
        SyncAcknowledgementPolicy.isSameVersion(
            sent.updatedAt, sent.revision, sent.baseRevision,
            current.updatedAt, current.revision, current.baseRevision
        )

    private fun logSyncFailure(operation: String, throwable: Throwable) {
        Log.e("FoodBillingSync", "$operation failed: ${throwable.message}", throwable)
    }
}
