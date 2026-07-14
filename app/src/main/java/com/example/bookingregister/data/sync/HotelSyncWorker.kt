package com.example.bookingregister.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.bookingregister.data.AppDatabase
import com.example.bookingregister.data.repository.BookingRepository
import com.example.bookingregister.data.repository.GstRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HotelSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val hotelRemoteId = SyncWorkScheduler.hotelRemoteIdFrom(inputData)
            ?: return Result.failure()

        return runCatching {
            val db = AppDatabase.getInstance(applicationContext)
            val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val repository = BookingRepository(applicationContext, workerScope, hotelRemoteId)

            repository.retryFailedSync(force = true)
            FoodBillingSyncService(db, hotelRemoteId).retryFailedFoodSync()
            GstRepository(db, hotelRemoteId, workerScope).retryUnsyncedSlabs()

            if (hasPendingSync(db, hotelRemoteId)) Result.retry() else Result.success()
        }.getOrElse { Result.retry() }
    }

    private suspend fun hasPendingSync(db: AppDatabase, hotelRemoteId: String): Boolean {
        return db.hotelDao().getUnsyncedHotels().isNotEmpty() ||
            db.managedPropertyDao().getUnsyncedProperties(hotelRemoteId).isNotEmpty() ||
            db.roomCategoryDao().getUnsyncedCategories(hotelRemoteId).isNotEmpty() ||
            db.roomDao().getUnsyncedRooms(hotelRemoteId).isNotEmpty() ||
            db.bookingSourceDao().getUnsyncedSources(hotelRemoteId).isNotEmpty() ||
            db.bookingDao().getUnsyncedBookings(hotelRemoteId).isNotEmpty() ||
            db.bookingSyncOutboxDao().countPending(hotelRemoteId) > 0 ||
            db.bookingPaymentDao().getUnsyncedPayments(hotelRemoteId).isNotEmpty() ||
            db.bookingFinancialLineDao().getUnsyncedLines(hotelRemoteId).isNotEmpty() ||
            db.bookingAccountingChargeDao().getUnsyncedCharges(hotelRemoteId).isNotEmpty() ||
            db.roomGstSlabDao().getUnsyncedSlabs(hotelRemoteId).isNotEmpty() ||
            db.foodGstCategoryDao().getUnsyncedCategories(hotelRemoteId).isNotEmpty() ||
            db.foodMenuItemDao().getUnsyncedItems(hotelRemoteId).isNotEmpty() ||
            db.serviceMenuItemDao().getUnsyncedItems(hotelRemoteId).isNotEmpty() ||
            db.foodOrderDao().getUnsyncedOrders(hotelRemoteId).isNotEmpty() ||
            db.foodOrderItemDao().getUnsyncedItems(hotelRemoteId).isNotEmpty() ||
            db.foodBillDao().getUnsyncedBills(hotelRemoteId).isNotEmpty() ||
            db.foodBillItemDao().getUnsyncedItems(hotelRemoteId).isNotEmpty()
    }
}
