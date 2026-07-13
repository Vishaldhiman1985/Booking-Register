package com.example.bookingregister.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.example.bookingregister.data.AppDatabase
import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
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
    private val foodBillDao = db.foodBillDao()
    private val foodBillItemDao = db.foodBillItemDao()
    private val foodOrderDao = db.foodOrderDao()
    private val foodOrderItemDao = db.foodOrderItemDao()
    private val bookingAccountingChargeDao = db.bookingAccountingChargeDao()
    private val cloudSyncManager = CloudSyncManager(hotelRemoteId)

    suspend fun pushFoodMenuItemAndMark(item: FoodMenuItemEntity) {
        runCatching {
            cloudSyncManager.pushFoodMenuItem(item)
        }.onSuccess { result ->
            foodMenuItemDao.upsert(item.markFoodSynced(result))
        }.onFailure {
            foodMenuItemDao.upsert(item.markFoodFailed(it))
            logSyncFailure("pushFoodMenuItem", it)
        }
    }

    suspend fun pushServiceMenuItemAndMark(item: ServiceMenuItemEntity) {
        runCatching {
            cloudSyncManager.pushServiceMenuItem(item)
        }.onSuccess { result ->
            serviceMenuItemDao.upsert(item.markFoodSynced(result))
        }.onFailure {
            serviceMenuItemDao.upsert(item.markFoodFailed(it))
            logSyncFailure("pushServiceMenuItem", it)
        }
    }

    fun stop() {
        cloudSyncManager.stop()
    }

    private fun logSyncFailure(operation: String, throwable: Throwable) {
        Log.e("FoodBillingSync", "$operation failed: ${throwable.message}", throwable)
    }
    suspend fun pushFoodOrderAndMark(order: FoodOrderEntity) {
        pushFoodOrderAggregateAndMark(order)
    }

    private suspend fun pushFoodOrderAggregateAndMark(order: FoodOrderEntity) {
        val orderItems = foodOrderItemDao.getItemsForOrder(hotelRemoteId, order.remoteId)
        runCatching {
            cloudSyncManager.pushFoodOrderAggregate(
                operationId = foodOrderAggregateOperationId(order, orderItems),
                order = order,
                orderItems = orderItems
            )
        }.onSuccess { result ->
            db.withTransaction {
                foodOrderDao.getByRemoteId(order.remoteId)?.let { currentOrder ->
                    val unchanged = SyncAcknowledgementPolicy.isSameVersion(
                        order.updatedAt, order.revision, order.baseRevision,
                        currentOrder.updatedAt, currentOrder.revision, currentOrder.baseRevision
                    )
                    foodOrderDao.upsert(
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
                orderItems.forEach { sentItem ->
                    val revision = result.orderItemRevisions[sentItem.remoteId] ?: return@forEach
                    foodOrderItemDao.getByRemoteId(sentItem.remoteId)?.let { currentItem ->
                        val unchanged = SyncAcknowledgementPolicy.isSameVersion(
                            sentItem.updatedAt, sentItem.revision, sentItem.baseRevision,
                            currentItem.updatedAt, currentItem.revision, currentItem.baseRevision
                        )
                        foodOrderItemDao.upsert(
                            if (unchanged) {
                                currentItem.markFoodSynced(
                                    CloudWriteResult(revision, result.updatedByUid)
                                )
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
            }
        }.onFailure {
            db.withTransaction {
                foodOrderDao.getByRemoteId(order.remoteId)?.let { currentOrder ->
                    foodOrderDao.upsert(currentOrder.markFoodFailed(it))
                }
                orderItems.forEach { sentItem ->
                    foodOrderItemDao.getByRemoteId(sentItem.remoteId)?.let { currentItem ->
                        foodOrderItemDao.upsert(currentItem.markFoodFailed(it))
                    }
                }
            }
            logSyncFailure("pushFoodOrderAggregate", it)
        }
    }
    suspend fun pushFoodBillAndMark(bill: FoodBillEntity) {
        pushFoodBillAggregateAndMark(bill)
    }

    suspend fun pushFoodBillAggregateAndMark(bill: FoodBillEntity) {
        val billItems = foodBillItemDao.getItemsForBill(hotelRemoteId, bill.remoteId)
        val orders = foodOrderDao.getOrdersForBillAggregate(hotelRemoteId, bill.remoteId)
        val orderItems = orders
            .flatMap { order -> foodOrderItemDao.getItemsForOrder(hotelRemoteId, order.remoteId) }
            .distinctBy { it.remoteId }
        val accountingCharges = bookingAccountingChargeDao.getChargesLinkedToFinalBill(
            hotelRemoteId = hotelRemoteId,
            billRemoteId = bill.remoteId
        )

        if (billItems.isEmpty() && !bill.isDeleted) {
            val error = IllegalStateException("Food bill has no bill items. It was not synced.")
            markFoodBillAggregateFailed(bill, billItems, orders, orderItems, accountingCharges, error)
            logSyncFailure("pushFoodBillAggregate", error)
            return
        }

        runCatching {
            cloudSyncManager.pushFoodBillAggregate(
                operationId = foodBillAggregateOperationId(
                    bill,
                    billItems,
                    orders,
                    orderItems,
                    accountingCharges
                ),
                bill = bill,
                billItems = billItems,
                orders = orders,
                orderItems = orderItems,
                accountingCharges = accountingCharges
            )
        }.onSuccess { result ->
            acknowledgeFoodBillAggregate(bill, billItems, orders, orderItems, accountingCharges, result)
        }.onFailure {
            markFoodBillAggregateFailed(bill, billItems, orders, orderItems, accountingCharges, it)
            logSyncFailure("pushFoodBillAggregate", it)
        }
    }

    suspend fun retryFailedFoodSync() {
        foodMenuItemDao.getUnsyncedItems(hotelRemoteId).forEach {
            pushFoodMenuItemAndMark(it)
        }

        serviceMenuItemDao.getUnsyncedItems(hotelRemoteId).forEach {
            pushServiceMenuItemAndMark(it)
        }

        db.foodGstCategoryDao().getUnsyncedCategories(hotelRemoteId).forEach {
            pushFoodGstCategoryAndMark(it)
        }

        val aggregateBillIds = foodBillAggregateCandidateIds()
        aggregateBillIds.forEach { billRemoteId ->
            foodBillDao.getByRemoteId(billRemoteId)?.let { bill ->
                pushFoodBillAggregateAndMark(bill)
            }
        }

        val billedOrderIds = foodOrderDao.getOrders(hotelRemoteId)
            .filter { !it.billRemoteId.isNullOrBlank() || !it.linkedFinalBillId.isNullOrBlank() }
            .mapTo(mutableSetOf()) { it.remoteId }

        foodOrderDao.getUnsyncedOrders(hotelRemoteId)
            .filter { it.remoteId !in billedOrderIds }
            .forEach {
            pushFoodOrderAndMark(it)
        }

        val aggregatedOrderIds = foodOrderDao.getUnsyncedOrders(hotelRemoteId)
            .filter { it.remoteId !in billedOrderIds }
            .mapTo(mutableSetOf()) { it.remoteId }

        foodOrderItemDao.getUnsyncedItems(hotelRemoteId)
            .filter { it.orderRemoteId !in billedOrderIds && it.orderRemoteId !in aggregatedOrderIds }
            .map { it.orderRemoteId }
            .distinct()
            .forEach { orderRemoteId ->
                foodOrderDao.getByRemoteId(orderRemoteId)?.let { order ->
                    pushFoodOrderAggregateAndMark(order)
                }
        }
    }

    suspend fun pushFoodGstCategoryAndMark(category: FoodGstCategoryEntity) {
        runCatching {
            cloudSyncManager.pushFoodGstCategory(category)
        }.onSuccess { result ->
            db.foodGstCategoryDao().upsert(category.markFoodSynced(result))
        }.onFailure {
            db.foodGstCategoryDao().upsert(category.markFoodFailed(it))
            logSyncFailure("pushFoodGstCategory", it)
        }
    }

    private suspend fun foodBillAggregateCandidateIds(): List<String> {
        val ids = linkedSetOf<String>()
        foodBillDao.getUnsyncedBills(hotelRemoteId).forEach { bill ->
            ids.add(bill.remoteId)
        }
        foodBillItemDao.getUnsyncedItems(hotelRemoteId).forEach { item ->
            item.billRemoteId.takeIf { it.isNotBlank() }?.let(ids::add)
        }
        foodOrderDao.getUnsyncedOrders(hotelRemoteId).forEach { order ->
            order.billRemoteId?.takeIf { it.isNotBlank() }?.let(ids::add)
            order.linkedFinalBillId?.takeIf { it.isNotBlank() }?.let(ids::add)
        }
        bookingAccountingChargeDao.getUnsyncedCharges(hotelRemoteId).forEach { charge ->
            charge.linkedFinalBillId?.takeIf { it.isNotBlank() }?.let(ids::add)
        }
        return ids.toList()
    }

    private suspend fun acknowledgeFoodBillAggregate(
        bill: FoodBillEntity,
        billItems: List<FoodBillItemEntity>,
        orders: List<FoodOrderEntity>,
        orderItems: List<FoodOrderItemEntity>,
        accountingCharges: List<BookingAccountingChargeEntity>,
        result: FoodBillAggregateWriteResult
    ) {
        db.withTransaction {
            foodBillDao.getByRemoteId(bill.remoteId)?.let { currentBill ->
                val unchanged = SyncAcknowledgementPolicy.isSameVersion(
                    bill.updatedAt, bill.revision, bill.baseRevision,
                    currentBill.updatedAt, currentBill.revision, currentBill.baseRevision
                )
                foodBillDao.upsert(
                    if (unchanged) {
                        currentBill.markFoodSynced(
                            CloudWriteResult(result.billRevision, result.updatedByUid)
                        )
                    } else {
                        currentBill.copy(
                            revision = result.billRevision,
                            baseRevision = result.billRevision,
                            syncState = SyncState.PENDING,
                            lastSyncError = null
                        )
                    }
                )
            }

            billItems.forEach { sentItem ->
                val revision = result.foodBillItemRevisions[sentItem.remoteId] ?: return@forEach
                foodBillItemDao.getByRemoteId(sentItem.remoteId)?.let { currentItem ->
                    val unchanged = SyncAcknowledgementPolicy.isSameVersion(
                        sentItem.updatedAt, sentItem.revision, sentItem.baseRevision,
                        currentItem.updatedAt, currentItem.revision, currentItem.baseRevision
                    )
                    foodBillItemDao.upsert(
                        if (unchanged) {
                            currentItem.markFoodSynced(
                                CloudWriteResult(revision, result.updatedByUid)
                            )
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

            orders.forEach { sentOrder ->
                val revision = result.foodOrderRevisions[sentOrder.remoteId] ?: return@forEach
                foodOrderDao.getByRemoteId(sentOrder.remoteId)?.let { currentOrder ->
                    val unchanged = SyncAcknowledgementPolicy.isSameVersion(
                        sentOrder.updatedAt, sentOrder.revision, sentOrder.baseRevision,
                        currentOrder.updatedAt, currentOrder.revision, currentOrder.baseRevision
                    )
                    foodOrderDao.upsert(
                        if (unchanged) {
                            currentOrder.markFoodSynced(
                                CloudWriteResult(revision, result.updatedByUid)
                            )
                        } else {
                            currentOrder.copy(
                                revision = revision,
                                baseRevision = revision,
                                syncState = SyncState.PENDING,
                                lastSyncError = null
                            )
                        }
                    )
                }
            }

            orderItems.forEach { sentItem ->
                val revision = result.foodOrderItemRevisions[sentItem.remoteId] ?: return@forEach
                foodOrderItemDao.getByRemoteId(sentItem.remoteId)?.let { currentItem ->
                    val unchanged = SyncAcknowledgementPolicy.isSameVersion(
                        sentItem.updatedAt, sentItem.revision, sentItem.baseRevision,
                        currentItem.updatedAt, currentItem.revision, currentItem.baseRevision
                    )
                    foodOrderItemDao.upsert(
                        if (unchanged) {
                            currentItem.markFoodSynced(
                                CloudWriteResult(revision, result.updatedByUid)
                            )
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

            accountingCharges.forEach { sentCharge ->
                val revision = result.accountingChargeRevisions[sentCharge.remoteId] ?: return@forEach
                bookingAccountingChargeDao.getByRemoteId(sentCharge.remoteId)?.let { currentCharge ->
                    val unchanged = SyncAcknowledgementPolicy.isSameVersion(
                        sentCharge.updatedAt, sentCharge.revision, sentCharge.baseRevision,
                        currentCharge.updatedAt, currentCharge.revision, currentCharge.baseRevision
                    )
                    bookingAccountingChargeDao.upsert(
                        if (unchanged) {
                            currentCharge.markFoodBillAggregateSynced(
                                CloudWriteResult(revision, result.updatedByUid)
                            )
                        } else {
                            currentCharge.copy(
                                revision = revision,
                                baseRevision = revision,
                                syncState = SyncState.PENDING,
                                lastSyncError = null
                            )
                        }
                    )
                }
            }
        }
    }

    private suspend fun markFoodBillAggregateFailed(
        bill: FoodBillEntity,
        billItems: List<FoodBillItemEntity>,
        orders: List<FoodOrderEntity>,
        orderItems: List<FoodOrderItemEntity>,
        accountingCharges: List<BookingAccountingChargeEntity>,
        throwable: Throwable
    ) {
        db.withTransaction {
            foodBillDao.getByRemoteId(bill.remoteId)?.let {
                foodBillDao.upsert(it.markFoodFailed(throwable))
            }
            billItems.forEach { item ->
                foodBillItemDao.getByRemoteId(item.remoteId)?.let {
                    foodBillItemDao.upsert(it.markFoodFailed(throwable))
                }
            }
            orders.forEach { order ->
                foodOrderDao.getByRemoteId(order.remoteId)?.let {
                    foodOrderDao.upsert(it.markFoodFailed(throwable))
                }
            }
            orderItems.forEach { item ->
                foodOrderItemDao.getByRemoteId(item.remoteId)?.let {
                    foodOrderItemDao.upsert(it.markFoodFailed(throwable))
                }
            }
            accountingCharges.forEach { charge ->
                bookingAccountingChargeDao.getByRemoteId(charge.remoteId)?.let {
                    bookingAccountingChargeDao.upsert(it.markFoodBillAggregateFailed(throwable))
                }
            }
        }
    }

    private fun BookingAccountingChargeEntity.markFoodBillAggregateSynced(
        result: CloudWriteResult
    ): BookingAccountingChargeEntity = copy(
        syncState = SyncState.SYNCED,
        lastSyncError = null,
        lastSyncedAt = System.currentTimeMillis(),
        revision = result.revision,
        baseRevision = result.revision,
        updatedByUid = result.updatedByUid ?: updatedByUid
    )

    private fun BookingAccountingChargeEntity.markFoodBillAggregateFailed(
        throwable: Throwable
    ): BookingAccountingChargeEntity = copy(
        syncState = SyncState.FAILED,
        lastSyncError = throwable.message ?: throwable::class.java.simpleName
    )

    private fun foodOrderAggregateOperationId(
        order: FoodOrderEntity,
        orderItems: List<FoodOrderItemEntity>
    ): String {
        val itemVersion = orderItems
            .sortedBy { it.remoteId }
            .joinToString("|") { "${it.remoteId}:${it.updatedAt}:${it.baseRevision}:${it.isDeleted}" }
            .hashCode()
            .toUInt()
            .toString(16)
        return "food_order_aggregate_${order.remoteId}_${order.updatedAt}_$itemVersion"
    }

    private fun foodBillAggregateOperationId(
        bill: FoodBillEntity,
        billItems: List<FoodBillItemEntity>,
        orders: List<FoodOrderEntity>,
        orderItems: List<FoodOrderItemEntity>,
        accountingCharges: List<BookingAccountingChargeEntity>
    ): String {
        val aggregateVersion = buildList {
            billItems.forEach { add("bi:${it.remoteId}:${it.updatedAt}:${it.baseRevision}:${it.isDeleted}") }
            orders.forEach { add("o:${it.remoteId}:${it.updatedAt}:${it.baseRevision}:${it.isDeleted}") }
            orderItems.forEach { add("oi:${it.remoteId}:${it.updatedAt}:${it.baseRevision}:${it.isDeleted}") }
            accountingCharges.forEach { add("c:${it.remoteId}:${it.updatedAt}:${it.baseRevision}:${it.isDeleted}") }
        }.sorted().joinToString("|").hashCode().toUInt().toString(16)
        return "food_bill_aggregate_${bill.remoteId}_${bill.updatedAt}_$aggregateVersion"
    }
}
