package com.example.bookingregister.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import com.example.bookingregister.billing.domain.InvoiceNumberPolicy
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.FoodBillEntity
import com.example.bookingregister.data.entities.FoodBillItemEntity
import com.example.bookingregister.data.entities.FoodBillingScope
import com.example.bookingregister.data.entities.FoodGstCategoryEntity
import com.example.bookingregister.data.entities.FoodMenuItemEntity
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderItemEntity
import com.example.bookingregister.data.entities.HotelEntity
import com.example.bookingregister.data.entities.ManagedPropertyEntity
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.data.entities.ServiceMenuItemEntity
import kotlinx.coroutines.CoroutineScope
import com.example.bookingregister.data.AppDatabase
import com.example.bookingregister.data.sync.FoodBillingSyncService
import com.example.bookingregister.data.SyncState
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import com.example.bookingregister.data.sync.CloudSyncManager
import com.example.bookingregister.data.sync.FoodRealtimeSyncService
import com.example.bookingregister.data.sync.SyncWorkScheduler
import com.example.bookingregister.tax.domain.FoodGstCalculator

class FoodBillingRepository(
    context: Context,
    private val scope: CoroutineScope,
    val hotelRemoteId: String
) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)

    private val foodGstCategoryDao = db.foodGstCategoryDao()
    private val foodMenuItemDao = db.foodMenuItemDao()
    private val serviceMenuItemDao = db.serviceMenuItemDao()
    private val foodOrderDao = db.foodOrderDao()
    private val foodOrderItemDao = db.foodOrderItemDao()
    private val foodBillDao = db.foodBillDao()
    private val foodBillItemDao = db.foodBillItemDao()
    private val bookingDao = db.bookingDao()
    private val foodGstCalculator = FoodGstCalculator()
    private val foodBillingSyncService = FoodBillingSyncService(
        db = db,
        hotelRemoteId = hotelRemoteId
    )

    private val foodCloudSyncManager = CloudSyncManager(hotelRemoteId)

    private val foodRealtimeSyncService = FoodRealtimeSyncService(
        db = db,
        hotelRemoteId = hotelRemoteId,
        scope = scope,
        cloudSyncManager = foodCloudSyncManager
    )
    private val roomDao = db.roomDao()
    private val hotelDao = db.hotelDao()
    private val managedPropertyDao = db.managedPropertyDao()

    private fun enqueueBackgroundSync() {
        SyncWorkScheduler.enqueue(appContext, hotelRemoteId)
    }

    private data class BillSupplierSnapshot(
        val supplierName: String?,
        val supplierGstin: String?,
        val supplierAddress: String?,
        val supplierPhone: String?,
        val supplierState: String?,
        val propertyDisplayName: String?,
        val invoicePrefix: String
    )

    data class FoodMenuImportItem(
        val itemName: String,
        val categoryName: String?,
        val price: Double,
        val gstRatePercent: Double,
        val gstCategoryRemoteId: String?,
        val gstCategoryName: String?,
        val hsnSacCode: String?,
        val cgstRatePercent: Double,
        val sgstRatePercent: Double,
        val cessRatePercent: Double
    )

    private suspend fun resolveBillSupplierSnapshot(propertyRemoteId: String?): BillSupplierSnapshot {
        val property = propertyRemoteId
            ?.takeIf { it.isNotBlank() }
            ?.let { managedPropertyDao.getByRemoteId(it) }
        val hotel = hotelDao.getByRemoteId(hotelRemoteId)

        val profile = InvoiceNumberPolicy.resolveBillingProfile(
            organization = hotel,
            property = property,
            fallbackPrefix = "FB"
        )
        return BillSupplierSnapshot(
            supplierName = profile.supplierName,
            supplierGstin = profile.supplierGstin,
            supplierAddress = profile.supplierAddress,
            supplierPhone = profile.supplierPhone,
            supplierState = profile.supplierState,
            propertyDisplayName = profile.propertyDisplayName,
            invoicePrefix = profile.invoicePrefix
        )
    }

    private suspend fun nextBillNumber(prefix: String, now: Long): String {
        return foodCloudSyncManager.reserveInvoiceNumber(prefix, now)
    }

    fun observeHotel(): LiveData<HotelEntity?> =
        hotelDao.observeHotel(hotelRemoteId)

    fun observeManagedProperties(): LiveData<List<ManagedPropertyEntity>> =
        managedPropertyDao.observeProperties(hotelRemoteId)

    fun observeRooms(): LiveData<List<RoomEntity>> =
        roomDao.observeRooms(hotelRemoteId)

    fun observeBookings(): LiveData<List<BookingEntity>> =
        bookingDao.observeBookings(hotelRemoteId)

    fun observeFoodGstCategories(): LiveData<List<FoodGstCategoryEntity>> =
        foodGstCategoryDao.observeCategories(hotelRemoteId)

    fun observeFoodMenuItems(): LiveData<List<FoodMenuItemEntity>> =
        foodMenuItemDao.observeMenuItems(hotelRemoteId)

    fun observeServiceMenuItems(): LiveData<List<ServiceMenuItemEntity>> =
        serviceMenuItemDao.observeMenuItems(hotelRemoteId)

    fun observeFoodOrders(): LiveData<List<FoodOrderEntity>> =
        foodOrderDao.observeOrders(hotelRemoteId)

    fun observeFoodOrderItems(): LiveData<List<FoodOrderItemEntity>> =
        foodOrderItemDao.observeItems(hotelRemoteId)

    fun observeFoodBills(): LiveData<List<FoodBillEntity>> =
        foodBillDao.observeBills(hotelRemoteId)

    fun observeFoodBillsForRange(startMillis: Long, endMillis: Long): LiveData<List<FoodBillEntity>> =
        foodBillDao.observeBillsForRange(hotelRemoteId, startMillis, endMillis)

    fun observeFoodBillItems(): LiveData<List<FoodBillItemEntity>> =
        foodBillItemDao.observeItems(hotelRemoteId)

    suspend fun getArchivedFoodBillsBefore(beforeMillis: Long, limit: Int = 100): List<FoodBillEntity> =
        foodBillDao.getArchivedBillsBefore(hotelRemoteId, beforeMillis, limit)

    suspend fun searchFoodBills(query: String, limit: Int = 100): List<FoodBillEntity> =
        foodBillDao.searchBills(hotelRemoteId, query.trim(), limit)

    suspend fun getFoodBillItemsForBill(billRemoteId: String): List<FoodBillItemEntity> =
        foodBillItemDao.getItemsForBill(hotelRemoteId, billRemoteId)

    fun startRealtimeSync() {
        foodRealtimeSyncService.start(
            onSyncError = { area, throwable ->
                android.util.Log.e(
                    "FoodRealtimeSync",
                    "$area sync listener failed: ${throwable.message}",
                    throwable
                )
            },
            onAfterRemoteChanges = {
                foodBillingSyncService.retryFailedFoodSync()
            }
        )
        scope.launch {
            foodBillingSyncService.retryFailedFoodSync()
        }
    }

    fun stopRealtimeSync() {
        foodCloudSyncManager.stop()
    }

    fun saveFoodMenuItem(
        existing: FoodMenuItemEntity?,
        itemName: String,
        categoryName: String?,
        price: Double,
        gstRatePercent: Double,
        propertyRemoteId: String? = null,
        gstCategoryRemoteId: String? = null,
        gstCategoryName: String? = null,
        hsnSacCode: String? = null,
        cgstRatePercent: Double = 0.0,
        sgstRatePercent: Double = 0.0,
        cessRatePercent: Double = 0.0
    ) {
        scope.launch {
            val cleanName = itemName.trim()
            if (cleanName.isEmpty()) return@launch

            val now = System.currentTimeMillis()
            val defaultCategory = foodGstCategoryDao.getDefaultCategory(hotelRemoteId)

            val item = FoodMenuItemEntity(
                localId = existing?.localId ?: 0,
                remoteId = existing?.remoteId ?: "${hotelRemoteId}_food_${UUID.randomUUID()}",
                hotelRemoteId = hotelRemoteId,
                propertyRemoteId = propertyRemoteId?.takeIf { it.isNotBlank() },
                itemName = cleanName,
                categoryName = categoryName?.trim()?.ifEmpty { null },
                price = price.coerceAtLeast(0.0),
                gstCategoryRemoteId = gstCategoryRemoteId?.takeIf { it.isNotBlank() }
                    ?: existing?.gstCategoryRemoteId
                    ?: defaultCategory?.remoteId,
                gstCategoryName = gstCategoryName?.trim()?.ifEmpty { null },
                hsnSacCode = hsnSacCode?.trim()?.ifEmpty { null },
                cgstRatePercent = cgstRatePercent.coerceAtLeast(0.0),
                sgstRatePercent = sgstRatePercent.coerceAtLeast(0.0),
                cessRatePercent = cessRatePercent.coerceAtLeast(0.0),
                gstRatePercent = gstRatePercent.coerceAtLeast(0.0),
                isActive = true,
                updatedAt = now,
                isDeleted = false,
                syncState = SyncState.PENDING,
                lastSyncError = null,
                lastSyncedAt = existing?.lastSyncedAt,
                revision = existing?.revision ?: 0,
                baseRevision = existing?.baseRevision?.takeIf { it > 0 } ?: existing?.revision ?: 0,
                updatedByUid = existing?.updatedByUid
            )

            foodMenuItemDao.upsert(item)
            foodBillingSyncService.retryFailedFoodSync()
            enqueueBackgroundSync()
        }
    }

    fun deleteFoodMenuItem(item: FoodMenuItemEntity) {
        scope.launch {
            val deleted = item.copy(
                isDeleted = true,
                isActive = false,
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING,
                lastSyncError = null,
                baseRevision = item.baseRevision.takeIf { it > 0 } ?: item.revision
            )

            foodMenuItemDao.upsert(deleted)
            foodBillingSyncService.retryFailedFoodSync()
            enqueueBackgroundSync()
        }
    }

    fun replaceFoodMenuItems(
        propertyRemoteId: String?,
        importedItems: List<FoodMenuImportItem>
    ) {
        scope.launch {
            val cleanPropertyRemoteId = propertyRemoteId?.takeIf { it.isNotBlank() }
            val now = System.currentTimeMillis()
            db.withTransaction {
                val existingItems = foodMenuItemDao.getItems(hotelRemoteId)
                    .filter { item ->
                        !item.isDeleted &&
                                when {
                                    cleanPropertyRemoteId == null -> item.propertyRemoteId.isNullOrBlank()
                                    else -> item.propertyRemoteId == cleanPropertyRemoteId
                                }
                    }

                existingItems.forEach { item ->
                    foodMenuItemDao.upsert(
                        item.copy(
                            isDeleted = true,
                            isActive = false,
                            updatedAt = now,
                            syncState = SyncState.PENDING,
                            lastSyncError = null,
                            baseRevision = item.baseRevision.takeIf { it > 0 } ?: item.revision
                        )
                    )
                }

                importedItems.forEach { imported ->
                    val item = FoodMenuItemEntity(
                        remoteId = "${hotelRemoteId}_food_${UUID.randomUUID()}",
                        hotelRemoteId = hotelRemoteId,
                        propertyRemoteId = cleanPropertyRemoteId,
                        itemName = imported.itemName.trim(),
                        categoryName = imported.categoryName?.trim()?.ifEmpty { null },
                        price = imported.price.coerceAtLeast(0.0),
                        gstCategoryRemoteId = imported.gstCategoryRemoteId?.takeIf { it.isNotBlank() },
                        gstCategoryName = imported.gstCategoryName,
                        hsnSacCode = imported.hsnSacCode,
                        gstRatePercent = imported.gstRatePercent.coerceAtLeast(0.0),
                        cgstRatePercent = imported.cgstRatePercent.coerceAtLeast(0.0),
                        sgstRatePercent = imported.sgstRatePercent.coerceAtLeast(0.0),
                        cessRatePercent = imported.cessRatePercent.coerceAtLeast(0.0),
                        isActive = true,
                        updatedAt = now,
                        isDeleted = false,
                        syncState = SyncState.PENDING,
                        lastSyncError = null,
                        revision = 0,
                        baseRevision = 0
                    )
                    foodMenuItemDao.upsert(item)
                }
            }

            foodBillingSyncService.retryFailedFoodSync()
            enqueueBackgroundSync()
        }
    }

    fun saveServiceMenuItem(
        existing: ServiceMenuItemEntity?,
        serviceName: String,
        categoryName: String?,
        description: String?,
        unitLabel: String?,
        price: Double,
        sacCode: String?,
        gstRatePercent: Double,
        taxInclusive: Boolean,
        propertyRemoteId: String? = null,
        sortOrder: Int = existing?.sortOrder ?: 0
    ) {
        scope.launch {
            val cleanName = serviceName.trim()
            if (cleanName.isEmpty()) return@launch

            val item = ServiceMenuItemEntity(
                localId = existing?.localId ?: 0,
                remoteId = existing?.remoteId ?: "${hotelRemoteId}_service_${UUID.randomUUID()}",
                hotelRemoteId = hotelRemoteId,
                propertyRemoteId = propertyRemoteId?.takeIf { it.isNotBlank() },
                serviceName = cleanName,
                categoryName = categoryName?.trim()?.ifEmpty { null },
                description = description?.trim()?.ifEmpty { null },
                unitLabel = unitLabel?.trim()?.ifEmpty { null },
                price = price.coerceAtLeast(0.0),
                sacCode = sacCode?.trim()?.ifEmpty { null },
                gstRatePercent = gstRatePercent.coerceAtLeast(0.0),
                taxInclusive = taxInclusive,
                isActive = true,
                sortOrder = sortOrder,
                updatedAt = System.currentTimeMillis(),
                isDeleted = false,
                syncState = SyncState.PENDING,
                lastSyncError = null,
                lastSyncedAt = existing?.lastSyncedAt,
                revision = existing?.revision ?: 0,
                baseRevision = existing?.baseRevision?.takeIf { it > 0 } ?: existing?.revision ?: 0,
                updatedByUid = existing?.updatedByUid
            )

            serviceMenuItemDao.upsert(item)
            enqueueBackgroundSync()
        }
    }

    fun deleteServiceMenuItem(item: ServiceMenuItemEntity) {
        scope.launch {
            val deleted = item.copy(
                isDeleted = true,
                isActive = false,
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING,
                lastSyncError = null,
                baseRevision = item.baseRevision.takeIf { it > 0 } ?: item.revision
            )

            serviceMenuItemDao.upsert(deleted)
            enqueueBackgroundSync()
        }
    }

    suspend fun saveFoodOrder(
        existing: FoodOrderEntity?,
        booking: BookingEntity?,
        room: RoomEntity?,
        guestName: String,
        discountAmount: Double,
        notes: String?,
        status: String,
        items: List<FoodOrderItemEntity>
    ): SaveResult {
        val cleanGuest = guestName.trim().ifEmpty { room?.roomName ?: "Non Staying Guest" }
        val activeItems = items.filter { !it.isDeleted && !it.isCancelled && it.quantity > 0 }

        if (activeItems.isEmpty()) {
            return SaveResult.Error("Add at least one food item")
        }

        val now = System.currentTimeMillis()
        val orderRemoteId = existing?.remoteId ?: "${hotelRemoteId}_food_order_${UUID.randomUUID()}"
        val existingItems = foodOrderItemDao.getItemsForOrder(hotelRemoteId, orderRemoteId)
        val existingItemsById = existingItems.associateBy { it.remoteId }

        val propertyRemoteId = existing?.propertyRemoteId
            ?: booking?.propertyRemoteId
            ?: room?.propertyRemoteId

        val subtotal = activeItems.sumOf { it.quantity * it.unitPrice }
        val discount = discountAmount.coerceIn(0.0, subtotal)
        val discountRatio = if (subtotal > 0.0) discount / subtotal else 0.0

        val normalizedItems = activeItems.map { item ->
            val stored = existingItemsById[item.remoteId]
            val lineSubtotal = item.quantity * item.unitPrice
            val taxable = lineSubtotal * (1.0 - discountRatio)
            val gst = 0.0

            item.copy(
                localId = stored?.localId ?: item.localId,
                remoteId = item.remoteId.ifBlank { "${orderRemoteId}_item_${UUID.randomUUID()}" },
                hotelRemoteId = hotelRemoteId,
                orderRemoteId = orderRemoteId,
                lineSubtotal = lineSubtotal,
                lineGst = gst,
                lineTotal = taxable,
                updatedAt = now,
                isDeleted = false,
                syncState = SyncState.PENDING,
                lastSyncError = null,
                lastSyncedAt = stored?.lastSyncedAt,
                revision = stored?.revision ?: item.revision,
                baseRevision = stored?.baseRevision?.takeIf { it > 0 }
                    ?: stored?.revision
                    ?: item.baseRevision.takeIf { it > 0 }
                    ?: item.revision
            )
        }
        val incomingItemIds = normalizedItems.map { it.remoteId }.toSet()
        val deletedItems = existingItems
            .filter { it.remoteId !in incomingItemIds }
            .map { stored ->
                stored.copy(
                    updatedAt = now,
                    isDeleted = true,
                    syncState = SyncState.PENDING,
                    lastSyncError = null,
                    baseRevision = stored.baseRevision.takeIf { it > 0 } ?: stored.revision
                )
            }

        val taxableAmount = (subtotal - discount).coerceAtLeast(0.0)
        val gstAmount = normalizedItems.sumOf { it.lineGst }

        val order = FoodOrderEntity(
            localId = existing?.localId ?: 0,
            remoteId = orderRemoteId,
            hotelRemoteId = hotelRemoteId,
            propertyRemoteId = propertyRemoteId?.takeIf { it.isNotBlank() },
            bookingRemoteId = booking?.remoteId ?: existing?.bookingRemoteId,
            billRemoteId = existing?.billRemoteId,
            foodBillingScope = if (booking != null) {
                FoodBillingScope.IN_HOUSE_BOOKING
            } else {
                existing?.foodBillingScope ?: FoodBillingScope.WALK_IN
            },
            orderNumber = existing?.orderNumber ?: nextFoodOrderNumber(now),
            roomRemoteId = room?.remoteId ?: existing?.roomRemoteId,
            roomName = room?.roomName ?: existing?.roomName,
            guestName = cleanGuest,
            orderMillis = existing?.orderMillis ?: now,
            status = status,
            subtotal = subtotal,
            discountAmount = discount,
            taxableAmount = taxableAmount,
            gstAmount = gstAmount,
            totalAmount = taxableAmount,
            notes = notes?.trim()?.ifEmpty { null },
            updatedAt = now,
            isDeleted = false,
            syncState = SyncState.PENDING,
            lastSyncError = null,
            lastSyncedAt = existing?.lastSyncedAt,
            revision = existing?.revision ?: 0,
            baseRevision = existing?.baseRevision?.takeIf { it > 0 } ?: existing?.revision ?: 0,
            updatedByUid = existing?.updatedByUid
        )

        db.withTransaction {
            foodOrderDao.upsert(order)
            (deletedItems + normalizedItems).forEach { item ->
                foodOrderItemDao.upsert(item)
            }
        }

        enqueueBackgroundSync()

        return SaveResult.Success(syncPending = true)
    }

    suspend fun generateFoodBill(
        orderRemoteIds: List<String>,
        guestName: String?,
        guestMobile: String?,
        guestAddress: String?,
        guestGstin: String?,
        paymentMode: String?,
        discountAmount: Double,
        notes: String?,
        withGst: Boolean = true
    ): SaveResult {
        val selectedIds = orderRemoteIds.distinct().filter { it.isNotBlank() }

        if (selectedIds.isEmpty()) {
            return SaveResult.Error("Select at least one order")
        }

        val selectedOrders = foodOrderDao.getByRemoteIds(selectedIds)
            .filter { !it.isDeleted && it.status != com.example.bookingregister.data.entities.FoodOrderStatus.CANCELLED }

        if (selectedOrders.size != selectedIds.size) {
            return SaveResult.Error("Some selected orders are not available")
        }

        if (selectedOrders.any {
                it.status == com.example.bookingregister.data.entities.FoodOrderStatus.BILLED ||
                        !it.billRemoteId.isNullOrBlank()
            }
        ) {
            return SaveResult.Error("One selected order is already billed")
        }

        val orderItemsByOrder = selectedOrders.associateWith { order ->
            foodOrderItemDao.getItemsForOrder(hotelRemoteId, order.remoteId)
                .filter { !it.isDeleted && !it.isCancelled && it.quantity > 0.0 }
        }

        if (orderItemsByOrder.values.flatten().isEmpty()) {
            return SaveResult.Error("Selected orders have no items")
        }

        val categories = foodGstCategoryDao.getCategories(hotelRemoteId)
            .associateBy { it.remoteId }

        val menuById = foodMenuItemDao.getItems(hotelRemoteId)
            .associateBy { it.remoteId }

        val now = System.currentTimeMillis()
        val billRemoteId = "${hotelRemoteId}_food_bill_${UUID.randomUUID()}"

        val subtotal = orderItemsByOrder.values.flatten().sumOf {
            it.quantity * it.unitPrice
        }

        val discount = discountAmount.coerceIn(0.0, subtotal)
        val discountRatio = if (subtotal > 0.0) discount / subtotal else 0.0

        var taxableTotal = 0.0
        var cgstTotal = 0.0
        var sgstTotal = 0.0
        var cessTotal = 0.0
        var gstTotal = 0.0

        val billItems = mutableListOf<FoodBillItemEntity>()

        orderItemsByOrder.forEach { (order, itemsForOrder) ->
            itemsForOrder.forEach { item ->
                if (withGst && item.hsnSacCode.isNullOrBlank()) {
                    return SaveResult.Error(
                        "HSN/SAC missing for ${item.itemName}. Please update menu GST category and create a fresh order."
                    )
                }

                val lineSubtotal = item.quantity * item.unitPrice
                val discountedGross = lineSubtotal * (1.0 - discountRatio)

                val gstRate = if (withGst) item.gstRatePercent else 0.0
                val cgstRate = if (withGst) item.cgstRatePercent.takeIf { it > 0.0 } else null
                val sgstRate = if (withGst) item.sgstRatePercent.takeIf { it > 0.0 } else null
                val cessRate = if (withGst) item.cessRatePercent.coerceAtLeast(0.0) else 0.0

                val gstBreakdown = foodGstCalculator.calculateInclusive(
                    grossAmount = discountedGross,
                    gstRatePercent = gstRate,
                    cgstRatePercent = cgstRate,
                    sgstRatePercent = sgstRate,
                    cessRatePercent = cessRate,
                    withGst = withGst && (gstRate > 0.0 || cessRate > 0.0)
                )



                taxableTotal += gstBreakdown.taxableAmount
                cgstTotal += gstBreakdown.cgstAmount
                sgstTotal += gstBreakdown.sgstAmount
                cessTotal += gstBreakdown.cessAmount
                gstTotal += gstBreakdown.totalTaxAmount

                billItems.add(
                    FoodBillItemEntity(
                        remoteId = "${billRemoteId}_item_${UUID.randomUUID()}",
                        hotelRemoteId = hotelRemoteId,
                        billRemoteId = billRemoteId,
                        orderRemoteId = order.remoteId,
                        orderNumber = order.orderNumber,
                        orderMillis = order.orderMillis,
                        roomName = order.roomName,
                        menuItemRemoteId = item.menuItemRemoteId,
                        itemName = item.itemName,
                        quantity = item.quantity,
                        unitPrice = item.unitPrice,
                        lineSubtotal = lineSubtotal,
                        gstCategoryRemoteId = item.gstCategoryRemoteId,
                        gstCategoryName = item.gstCategoryName,
                        hsnSacCode = item.hsnSacCode,
                        gstRatePercent = gstBreakdown.gstRatePercent,
                        cgstRatePercent = gstBreakdown.cgstRatePercent,
                        sgstRatePercent = gstBreakdown.sgstRatePercent,
                        cessRatePercent = gstBreakdown.cessRatePercent,

                        taxableAmount = gstBreakdown.taxableAmount,
                        cgstAmount = gstBreakdown.cgstAmount,
                        sgstAmount = gstBreakdown.sgstAmount,
                        cessAmount = gstBreakdown.cessAmount,

                        gstAmount = gstBreakdown.totalTaxAmount,
                        lineTotal = gstBreakdown.lineTotal,
                        updatedAt = now,
                        syncState = SyncState.PENDING
                    )
                )
            }
        }

        val roomsIncluded = selectedOrders
            .map { it.roomName ?: "Non Staying Guest" }
            .distinct()
            .joinToString(", ")

        val propertyRemoteId = selectedOrders
            .mapNotNull { it.propertyRemoteId }
            .distinct()
            .singleOrNull()
        val supplierSnapshot = resolveBillSupplierSnapshot(propertyRemoteId)
        val billNumber = try {
            nextBillNumber(supplierSnapshot.invoicePrefix, now)
        } catch (error: Exception) {
            return SaveResult.Error("Could not reserve invoice number. Please check internet and try again.")
        }

        val bill = FoodBillEntity(
            remoteId = billRemoteId,
            hotelRemoteId = hotelRemoteId,
            propertyRemoteId = propertyRemoteId,
            supplierName = supplierSnapshot.supplierName,
            supplierGstin = supplierSnapshot.supplierGstin,
            supplierAddress = supplierSnapshot.supplierAddress,
            supplierPhone = supplierSnapshot.supplierPhone,
            supplierState = supplierSnapshot.supplierState,
            propertyDisplayName = supplierSnapshot.propertyDisplayName,
            billNumber = billNumber,
            billMillis = now,
            guestName = guestName?.trim()?.ifEmpty { null },
            guestMobile = guestMobile?.trim()?.ifEmpty { null },
            guestAddress = guestAddress?.trim()?.ifEmpty { null },
            guestGstin = guestGstin?.trim()?.ifEmpty { null },
            roomsIncluded = roomsIncluded,
            orderRemoteIds = selectedOrders.joinToString(",") { it.remoteId },
            subtotal = subtotal,
            discountAmount = discount,
            taxableAmount = taxableTotal,
            cgstAmount = cgstTotal,
            sgstAmount = sgstTotal,
            cessAmount = cessTotal,
            gstAmount = gstTotal,
            grandTotal = (subtotal - discount).coerceAtLeast(0.0),
            paymentMode = paymentMode?.trim()?.ifEmpty { null },
            notes = notes?.trim()?.ifEmpty { null },
            status = com.example.bookingregister.data.entities.FoodBillStatus.ISSUED,
            updatedAt = now,
            syncState = SyncState.PENDING
        )

        val result = db.withTransaction {
            val latestOrders = foodOrderDao.getByRemoteIds(selectedIds)
                .filter { !it.isDeleted && it.status != com.example.bookingregister.data.entities.FoodOrderStatus.CANCELLED }

            if (latestOrders.size != selectedIds.size) {
                return@withTransaction SaveResult.Error("Some selected orders are not available")
            }

            if (latestOrders.any {
                    it.status == com.example.bookingregister.data.entities.FoodOrderStatus.BILLED ||
                            !it.billRemoteId.isNullOrBlank()
                }
            ) {
                return@withTransaction SaveResult.Error("One selected order is already billed")
            }

            foodBillDao.upsert(bill)

            billItems.forEach { item ->
                foodBillItemDao.upsert(item)
            }

            latestOrders.forEach { order ->
                val billedOrder = order.copy(
                    billRemoteId = billRemoteId,
                    status = com.example.bookingregister.data.entities.FoodOrderStatus.BILLED,
                    updatedAt = now,
                    syncState = SyncState.PENDING,
                    lastSyncError = null,
                    baseRevision = order.baseRevision.takeIf { it > 0 } ?: order.revision
                )

                foodOrderDao.upsert(billedOrder)
            }

            SaveResult.Success(syncPending = true)
        }

        if (result is SaveResult.Success) enqueueBackgroundSync()

        return result
    }

    fun cancelFoodOrder(order: FoodOrderEntity) {
        scope.launch {
            val cancelled = order.copy(
                status = com.example.bookingregister.data.entities.FoodOrderStatus.CANCELLED,
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING,
                lastSyncError = null,
                baseRevision = order.baseRevision.takeIf { it > 0 } ?: order.revision
            )

            foodOrderDao.upsert(cancelled)
            enqueueBackgroundSync()
        }
    }

    suspend fun retryFailedFoodSync() {
        foodBillingSyncService.retryFailedFoodSync()
    }
    private fun nextFoodOrderNumber(now: Long): String {
        return "ORD-${java.text.SimpleDateFormat("yy-MM-dd-HHmmss", Locale.US).format(java.util.Date(now))}"
    }

    fun saveFoodGstCategory(
        existing: FoodGstCategoryEntity?,
        categoryName: String,
        hsnSacCode: String?,
        gstRatePercent: Double,
        cgstRatePercent: Double,
        sgstRatePercent: Double,
        cessRatePercent: Double = 0.0,
        isDefault: Boolean = false
    ) {
        scope.launch {
            val cleanName = categoryName.trim()
            if (cleanName.isEmpty()) return@launch

            val now = System.currentTimeMillis()

            val category = FoodGstCategoryEntity(
                localId = existing?.localId ?: 0,
                remoteId = existing?.remoteId ?: "${hotelRemoteId}_food_gst_${UUID.randomUUID()}",
                hotelRemoteId = hotelRemoteId,
                categoryName = cleanName,
                hsnSacCode = hsnSacCode?.trim()?.ifEmpty { null },
                gstRatePercent = gstRatePercent.coerceAtLeast(0.0),
                cgstRatePercent = cgstRatePercent.coerceAtLeast(0.0),
                sgstRatePercent = sgstRatePercent.coerceAtLeast(0.0),
                cessRatePercent = cessRatePercent.coerceAtLeast(0.0),
                isDefault = isDefault,
                isActive = true,
                updatedAt = now,
                isDeleted = false,
                syncState = SyncState.PENDING,
                lastSyncError = null,
                lastSyncedAt = existing?.lastSyncedAt,
                revision = existing?.revision ?: 0,
                baseRevision = existing?.baseRevision?.takeIf { it > 0 } ?: existing?.revision ?: 0,
                updatedByUid = existing?.updatedByUid
            )

            foodGstCategoryDao.upsert(category)
            updateMenuItemsForGstCategory(category)
            foodBillingSyncService.retryFailedFoodSync()
            enqueueBackgroundSync()
        }
    }

    fun deleteFoodGstCategory(category: FoodGstCategoryEntity) {
        scope.launch {
            foodGstCategoryDao.upsert(
                category.copy(
                    isDeleted = true,
                    isActive = false,
                    updatedAt = System.currentTimeMillis(),
                    syncState = SyncState.PENDING,
                    lastSyncError = null,
                    baseRevision = category.baseRevision.takeIf { it > 0 } ?: category.revision
                )
            )
            foodBillingSyncService.retryFailedFoodSync()
            enqueueBackgroundSync()
        }
    }

    private suspend fun updateMenuItemsForGstCategory(category: FoodGstCategoryEntity) {
        val items = foodMenuItemDao.getItems(hotelRemoteId)
            .filter {
                !it.isDeleted &&
                        it.gstCategoryRemoteId == category.remoteId
            }

        val now = System.currentTimeMillis()

        items.forEach { item ->
            foodMenuItemDao.upsert(
                item.copy(
                    gstCategoryName = category.categoryName,
                    hsnSacCode = category.hsnSacCode,
                    gstRatePercent = category.gstRatePercent,
                    cgstRatePercent = category.cgstRatePercent,
                    sgstRatePercent = category.sgstRatePercent,
                    cessRatePercent = category.cessRatePercent,
                    updatedAt = now,
                    syncState = SyncState.PENDING,
                    lastSyncError = null,
                    baseRevision = item.baseRevision.takeIf { it > 0 } ?: item.revision
                )
            )
        }
    }


}

