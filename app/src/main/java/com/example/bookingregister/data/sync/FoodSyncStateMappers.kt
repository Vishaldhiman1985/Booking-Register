package com.example.bookingregister.data.sync

import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.FoodBillEntity
import com.example.bookingregister.data.entities.FoodBillItemEntity
import com.example.bookingregister.data.entities.FoodGstCategoryEntity
import com.example.bookingregister.data.entities.FoodMenuItemEntity
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderItemEntity
import com.example.bookingregister.data.entities.ServiceMenuItemEntity

fun FoodMenuItemEntity.markFoodSynced(result: CloudWriteResult? = null): FoodMenuItemEntity = copy(
    syncState = SyncState.SYNCED,
    lastSyncError = null,
    lastSyncedAt = System.currentTimeMillis(),
    revision = result?.revision ?: revision,
    baseRevision = result?.revision ?: revision,
    updatedByUid = result?.updatedByUid ?: updatedByUid
)

fun FoodMenuItemEntity.markFoodFailed(throwable: Throwable): FoodMenuItemEntity = copy(
    syncState = SyncState.FAILED,
    lastSyncError = throwable.message ?: throwable::class.java.simpleName
)

fun ServiceMenuItemEntity.markFoodSynced(result: CloudWriteResult? = null): ServiceMenuItemEntity = copy(
    syncState = SyncState.SYNCED,
    lastSyncError = null,
    lastSyncedAt = System.currentTimeMillis(),
    revision = result?.revision ?: revision,
    baseRevision = result?.revision ?: revision,
    updatedByUid = result?.updatedByUid ?: updatedByUid
)

fun ServiceMenuItemEntity.markFoodFailed(throwable: Throwable): ServiceMenuItemEntity = copy(
    syncState = SyncState.FAILED,
    lastSyncError = throwable.message ?: throwable::class.java.simpleName
)

fun FoodOrderEntity.markFoodSynced(result: CloudWriteResult? = null): FoodOrderEntity = copy(
    syncState = SyncState.SYNCED,
    lastSyncError = null,
    lastSyncedAt = System.currentTimeMillis(),
    revision = result?.revision ?: revision,
    baseRevision = result?.revision ?: revision,
    updatedByUid = result?.updatedByUid ?: updatedByUid
)

fun FoodOrderEntity.markFoodFailed(throwable: Throwable): FoodOrderEntity = copy(
    syncState = SyncState.FAILED,
    lastSyncError = throwable.message ?: throwable::class.java.simpleName
)

fun FoodOrderItemEntity.markFoodSynced(result: CloudWriteResult? = null): FoodOrderItemEntity = copy(
    syncState = SyncState.SYNCED,
    lastSyncError = null,
    lastSyncedAt = System.currentTimeMillis(),
    revision = result?.revision ?: revision,
    baseRevision = result?.revision ?: revision,
    updatedByUid = result?.updatedByUid ?: updatedByUid
)

fun FoodOrderItemEntity.markFoodFailed(throwable: Throwable): FoodOrderItemEntity = copy(
    syncState = SyncState.FAILED,
    lastSyncError = throwable.message ?: throwable::class.java.simpleName
)

fun FoodGstCategoryEntity.markFoodSynced(result: CloudWriteResult? = null): FoodGstCategoryEntity = copy(
    syncState = SyncState.SYNCED,
    lastSyncError = null,
    lastSyncedAt = System.currentTimeMillis(),
    revision = result?.revision ?: revision,
    baseRevision = result?.revision ?: revision,
    updatedByUid = result?.updatedByUid ?: updatedByUid
)

fun FoodGstCategoryEntity.markFoodFailed(throwable: Throwable): FoodGstCategoryEntity = copy(
    syncState = SyncState.FAILED,
    lastSyncError = throwable.message ?: throwable::class.java.simpleName
)

fun FoodBillEntity.markFoodSynced(result: CloudWriteResult? = null): FoodBillEntity = copy(
    syncState = SyncState.SYNCED,
    lastSyncError = null,
    lastSyncedAt = System.currentTimeMillis(),
    revision = result?.revision ?: revision,
    baseRevision = result?.revision ?: revision,
    updatedByUid = result?.updatedByUid ?: updatedByUid
)

fun FoodBillEntity.markFoodFailed(throwable: Throwable): FoodBillEntity = copy(
    syncState = SyncState.FAILED,
    lastSyncError = throwable.message ?: throwable::class.java.simpleName
)

fun FoodBillItemEntity.markFoodSynced(result: CloudWriteResult? = null): FoodBillItemEntity = copy(
    syncState = SyncState.SYNCED,
    lastSyncError = null,
    lastSyncedAt = System.currentTimeMillis(),
    revision = result?.revision ?: revision,
    baseRevision = result?.revision ?: revision,
    updatedByUid = result?.updatedByUid ?: updatedByUid
)

fun FoodBillItemEntity.markFoodFailed(throwable: Throwable): FoodBillItemEntity = copy(
    syncState = SyncState.FAILED,
    lastSyncError = throwable.message ?: throwable::class.java.simpleName
)
