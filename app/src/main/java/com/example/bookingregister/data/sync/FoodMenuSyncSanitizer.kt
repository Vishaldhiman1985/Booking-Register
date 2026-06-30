package com.example.bookingregister.data.sync

import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.FoodMenuItemEntity
import java.util.Locale

object FoodMenuSyncSanitizer {

    fun tombstoneDuplicateActiveItems(
        items: List<FoodMenuItemEntity>,
        nowMillis: Long
    ): List<FoodMenuItemEntity> {
        return items
            .filter { item -> !item.isDeleted && item.isActive && item.itemName.isNotBlank() }
            .groupBy { item ->
                "${item.propertyRemoteId.orEmpty()}|${item.itemName.trim().lowercase(Locale.getDefault())}"
            }
            .values
            .filter { duplicates -> duplicates.size > 1 }
            .flatMap { duplicates ->
                val itemToKeep = duplicates.maxWithOrNull(
                    compareBy<FoodMenuItemEntity> { it.updatedAt }
                        .thenBy { it.revision }
                        .thenBy { it.remoteId }
                ) ?: return@flatMap emptyList()

                duplicates
                    .filter { item -> item.remoteId != itemToKeep.remoteId }
                    .map { duplicate ->
                        duplicate.copy(
                            isDeleted = true,
                            isActive = false,
                            updatedAt = nowMillis,
                            syncState = SyncState.PENDING,
                            lastSyncError = null,
                            baseRevision = duplicate.baseRevision.takeIf { it > 0 } ?: duplicate.revision
                        )
                    }
            }
    }
}
