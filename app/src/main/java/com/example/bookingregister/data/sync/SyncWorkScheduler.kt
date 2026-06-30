package com.example.bookingregister.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncWorkScheduler {
    private const val KEY_HOTEL_REMOTE_ID = "hotel_remote_id"
    private const val UNIQUE_PREFIX = "hotel_sync_"

    fun enqueue(context: Context, hotelRemoteId: String) {
        if (hotelRemoteId.isBlank()) return
        val request = OneTimeWorkRequestBuilder<HotelSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                Data.Builder()
                    .putString(KEY_HOTEL_REMOTE_ID, hotelRemoteId)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_PREFIX + hotelRemoteId, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    internal fun hotelRemoteIdFrom(inputData: Data): String? = inputData.getString(KEY_HOTEL_REMOTE_ID)
}

