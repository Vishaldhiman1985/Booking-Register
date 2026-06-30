package com.example.bookingregister.data.sync

import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.FoodMenuItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodMenuSyncSanitizerTest {

    @Test
    fun tombstoneDuplicateActiveItems_keepsNewestItemForSamePropertyAndName() {
        val stale = item(
            remoteId = "old",
            propertyRemoteId = "property-a",
            itemName = "Prantha",
            price = 200.0,
            updatedAt = 1_000L,
            revision = 2L
        )
        val newest = item(
            remoteId = "new",
            propertyRemoteId = "property-a",
            itemName = " prantha ",
            price = 250.0,
            updatedAt = 2_000L,
            revision = 1L
        )

        val tombstones = FoodMenuSyncSanitizer.tombstoneDuplicateActiveItems(
            items = listOf(stale, newest),
            nowMillis = 3_000L
        )

        assertEquals(1, tombstones.size)
        assertEquals("old", tombstones.single().remoteId)
        assertTrue(tombstones.single().isDeleted)
        assertFalse(tombstones.single().isActive)
        assertEquals(SyncState.PENDING, tombstones.single().syncState)
        assertEquals(3_000L, tombstones.single().updatedAt)
    }

    @Test
    fun tombstoneDuplicateActiveItems_doesNotMixSameItemNameAcrossProperties() {
        val propertyA = item(
            remoteId = "a",
            propertyRemoteId = "property-a",
            itemName = "Tea",
            updatedAt = 1_000L
        )
        val propertyB = item(
            remoteId = "b",
            propertyRemoteId = "property-b",
            itemName = "Tea",
            updatedAt = 2_000L
        )

        val tombstones = FoodMenuSyncSanitizer.tombstoneDuplicateActiveItems(
            items = listOf(propertyA, propertyB),
            nowMillis = 3_000L
        )

        assertTrue(tombstones.isEmpty())
    }

    @Test
    fun tombstoneDuplicateActiveItems_ignoresAlreadyDeletedItems() {
        val deleted = item(
            remoteId = "deleted",
            itemName = "Coffee",
            isDeleted = true,
            isActive = false,
            updatedAt = 2_000L
        )
        val active = item(
            remoteId = "active",
            itemName = "Coffee",
            updatedAt = 1_000L
        )

        val tombstones = FoodMenuSyncSanitizer.tombstoneDuplicateActiveItems(
            items = listOf(deleted, active),
            nowMillis = 3_000L
        )

        assertTrue(tombstones.isEmpty())
    }

    private fun item(
        remoteId: String,
        propertyRemoteId: String? = null,
        itemName: String,
        price: Double = 0.0,
        updatedAt: Long,
        revision: Long = 0L,
        isDeleted: Boolean = false,
        isActive: Boolean = true
    ): FoodMenuItemEntity {
        return FoodMenuItemEntity(
            remoteId = remoteId,
            hotelRemoteId = "hotel-1",
            propertyRemoteId = propertyRemoteId,
            itemName = itemName,
            price = price,
            updatedAt = updatedAt,
            revision = revision,
            baseRevision = revision,
            isDeleted = isDeleted,
            isActive = isActive,
            syncState = SyncState.SYNCED
        )
    }
}
