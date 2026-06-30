package com.example.bookingregister.data.sync

import android.util.Log
import com.example.bookingregister.data.AppDatabase
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
        runCatching {
            cloudSyncManager.pushFoodOrder(order)
        }.onSuccess { result ->
            db.foodOrderDao().upsert(order.markFoodSynced(result))
        }.onFailure {
            db.foodOrderDao().upsert(order.markFoodFailed(it))
            logSyncFailure("pushFoodOrder", it)
        }
    }
    suspend fun pushFoodOrderItemAndMark(item: FoodOrderItemEntity) {
        runCatching {
            cloudSyncManager.pushFoodOrderItem(item)
        }.onSuccess { result ->
            db.foodOrderItemDao().upsert(item.markFoodSynced(result))
        }.onFailure {
            db.foodOrderItemDao().upsert(item.markFoodFailed(it))
            logSyncFailure("pushFoodOrderItem", it)
        }
    }
    suspend fun pushFoodBillAndMark(bill: FoodBillEntity) {
        runCatching {
            cloudSyncManager.pushFoodBill(bill)
        }.onSuccess { result ->
            db.foodBillDao().upsert(bill.markFoodSynced(result))
        }.onFailure {
            db.foodBillDao().upsert(bill.markFoodFailed(it))
            logSyncFailure("pushFoodBill", it)
        }
    }

    suspend fun pushFoodBillItemAndMark(item: FoodBillItemEntity) {
        runCatching {
            cloudSyncManager.pushFoodBillItem(item)
        }.onSuccess { result ->
            db.foodBillItemDao().upsert(item.markFoodSynced(result))
        }.onFailure {
            db.foodBillItemDao().upsert(item.markFoodFailed(it))
            logSyncFailure("pushFoodBillItem", it)
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

        db.foodOrderDao().getUnsyncedOrders(hotelRemoteId).forEach {
            pushFoodOrderAndMark(it)
        }

        db.foodOrderItemDao().getUnsyncedItems(hotelRemoteId).forEach {
            pushFoodOrderItemAndMark(it)
        }

        db.foodBillDao().getUnsyncedBills(hotelRemoteId).forEach {
            pushFoodBillAndMark(it)
        }

        db.foodBillItemDao().getUnsyncedItems(hotelRemoteId).forEach {
            pushFoodBillItemAndMark(it)
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
}
