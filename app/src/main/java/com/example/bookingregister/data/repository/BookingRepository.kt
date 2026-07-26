package com.example.bookingregister.data.repository

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.withTransaction
import com.example.bookingregister.booking.domain.BookingPaymentStatus
import com.example.bookingregister.accounting.domain.ChargeBuckets
import com.example.bookingregister.accounting.domain.FoodBillTotalsCalculator
import com.example.bookingregister.accounting.domain.FinalBillChargeSelectionPolicy
import com.example.bookingregister.accounting.domain.PaymentAllocationPolicy
import com.example.bookingregister.accounting.domain.InitialPaymentFactory
import com.example.bookingregister.accounting.domain.RefundAllocationPolicy
import com.example.bookingregister.accounting.domain.PaymentAllocationRepairPolicy
import com.example.bookingregister.accounting.domain.StayBillItemBuilder
import com.example.bookingregister.accounting.domain.RoomNightFinancialIntegrity
import com.example.bookingregister.billing.domain.InvoiceNumberPolicy
import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.booking.domain.BilledRoomRateLockPolicy
import com.example.bookingregister.booking.domain.BookingPaymentSourcePolicy
import com.example.bookingregister.booking.domain.BookingPricingStatus
import com.example.bookingregister.booking.domain.BookingPropertyPolicy
import com.example.bookingregister.booking.domain.BookingChangeSet
import com.example.bookingregister.booking.domain.DerivedBookingCachePolicy
import com.example.bookingregister.booking.domain.CheckoutBalancePolicy
import com.example.bookingregister.booking.domain.CancellationRequest
import com.example.bookingregister.booking.domain.CancellationSettlementPolicy
import com.example.bookingregister.booking.domain.CancellationSettlementStatus
import com.example.bookingregister.booking.domain.DirectCancellationChoice
import com.example.bookingregister.data.AppDatabase
import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
import com.example.bookingregister.data.entities.BookingAccountingChargeType
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingFinancialLineSource
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import com.example.bookingregister.data.entities.BookingSourceEntity
import com.example.bookingregister.data.entities.BookingSyncOutboxEntity
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.data.entities.FoodBillEntity
import com.example.bookingregister.data.entities.FoodBillItemEntity
import com.example.bookingregister.data.entities.FoodBillStatus
import com.example.bookingregister.data.entities.FoodOrderStatus
import com.example.bookingregister.data.entities.HotelEntity
import com.example.bookingregister.data.entities.ManagedPropertyEntity
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.data.sync.CloudWriteResult
import com.example.bookingregister.data.sync.BookingAggregateWriteResult
import com.example.bookingregister.data.sync.CloudSyncManager
import com.example.bookingregister.data.sync.FoodBillingSyncService
import com.example.bookingregister.finalbill.domain.FinalBillGenerationPolicy
import com.example.bookingregister.folio.domain.FolioSummaryBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.round
import com.example.bookingregister.data.sync.syncFailureText
import com.example.bookingregister.data.sync.CodedSyncFailure
import com.example.bookingregister.data.sync.StructuredSyncException
import com.example.bookingregister.data.sync.SyncFailureCode
import com.example.bookingregister.data.sync.SyncWorkScheduler
import com.example.bookingregister.data.sync.SyncAcknowledgementPolicy
import com.example.bookingregister.data.withCalculatedPayment
import com.example.bookingregister.tax.domain.FoodGstCalculator
import com.example.bookingregister.room.domain.RoomHistoryFacts
import com.example.bookingregister.room.domain.RoomLifecyclePolicy
import com.example.bookingregister.room.domain.RoomLifecycleStatus
import com.example.bookingregister.source.domain.SourceSettlementCalculator



class BookingRepository(
    context: Context,
    private val scope: CoroutineScope,
    val hotelRemoteId: String
) {
    companion object {
        private const val RETRY_THROTTLE_MILLIS = 30_000L
    }
    private val appContext = context.applicationContext
    init {
        require(hotelRemoteId.isNotBlank()) { "Hotel access is missing. Please login again." }
    }
    private val db = AppDatabase.Companion.getInstance(appContext)
    private val hotelDao = db.hotelDao()
    private val managedPropertyDao = db.managedPropertyDao()
    private val roomDao = db.roomDao()
    private val bookingDao = db.bookingDao()
    private val bookingAccountingChargeDao = db.bookingAccountingChargeDao()
    private val bookingFinancialLineDao = db.bookingFinancialLineDao()
    private val bookingPaymentDao = db.bookingPaymentDao()
    private val bookingSourceDao = db.bookingSourceDao()
    private val bookingSyncOutboxDao = db.bookingSyncOutboxDao()
    private val foodOrderDao = db.foodOrderDao()
    private val foodOrderItemDao = db.foodOrderItemDao()
    private val foodMenuItemDao = db.foodMenuItemDao()
    private val foodGstCategoryDao = db.foodGstCategoryDao()
    private val foodBillDao = db.foodBillDao()
    private val foodBillItemDao = db.foodBillItemDao()
    private val foodBillingSyncService = FoodBillingSyncService(db, hotelRemoteId)
    private val cloudSyncManager = CloudSyncManager(hotelRemoteId)

    private val realtimeSyncError = MutableLiveData<String?>(null)
    private var retryInProgress = false
    private var lastRetryAttemptAt = 0L
    private val foodGstCalculator = FoodGstCalculator()
    private val sourceSettlementCalculator = SourceSettlementCalculator()

    fun observeHotel(): LiveData<HotelEntity?> = hotelDao.observeHotel(hotelRemoteId)

    fun observeManagedProperties(): LiveData<List<ManagedPropertyEntity>> =
        managedPropertyDao.observeProperties(hotelRemoteId)

    fun observeRooms(): LiveData<List<RoomEntity>> = roomDao.observeRooms(hotelRemoteId)

    fun observeBookings(): LiveData<List<BookingEntity>> = bookingDao.observeBookings(hotelRemoteId)

    fun observeChartBookingsForWindow(startMillis: Long, endMillis: Long): LiveData<List<BookingEntity>> =
        bookingDao.observeChartBookingsForWindow(hotelRemoteId, startMillis, endMillis)

    fun observeOutstandingBalance(): LiveData<Double> =
        bookingDao.observeOutstandingBalance(hotelRemoteId)

    fun observeUnsyncedBookings(): LiveData<List<BookingEntity>> =
        bookingDao.observeUnsyncedBookings(hotelRemoteId)

    fun observeUnsyncedPayments(): LiveData<List<BookingPaymentEntity>> =
        bookingPaymentDao.observeUnsyncedPayments(hotelRemoteId)

    fun observeUnsyncedFinancialLines(): LiveData<List<BookingFinancialLineEntity>> =
        bookingFinancialLineDao.observeUnsyncedLines(hotelRemoteId)

    fun observeUnsyncedAccountingCharges(): LiveData<List<BookingAccountingChargeEntity>> =
        bookingAccountingChargeDao.observeUnsyncedCharges(hotelRemoteId)

    suspend fun getBookings(): List<BookingEntity> = bookingDao.getBookings(hotelRemoteId)

    suspend fun getActiveBookings(): List<BookingEntity> =
        bookingDao.getBookingsByStatus(hotelRemoteId, BookingStatus.ACTIVE_STATUSES.toList())

    suspend fun getBookingsForWindow(startMillis: Long, endMillis: Long): List<BookingEntity> =
        bookingDao.getBookingsForWindow(hotelRemoteId, startMillis, endMillis)

    fun observePayments(): LiveData<List<BookingPaymentEntity>> = bookingPaymentDao.observePayments(hotelRemoteId)

    fun observeFinancialLines(): LiveData<List<BookingFinancialLineEntity>> =
        bookingFinancialLineDao.observeLines(hotelRemoteId)

    fun observeAccountingCharges(): LiveData<List<BookingAccountingChargeEntity>> =
        bookingAccountingChargeDao.observeCharges(hotelRemoteId)

    fun observeFinancialLinesForBooking(bookingRemoteId: String): LiveData<List<BookingFinancialLineEntity>> =
        bookingFinancialLineDao.observeLinesForBooking(hotelRemoteId, bookingRemoteId)

    fun observeSources(): LiveData<List<BookingSourceEntity>> = bookingSourceDao.observeSources(hotelRemoteId)

    fun observeRealtimeSyncError(): LiveData<String?> = realtimeSyncError


    suspend fun hasIssuedBillsForProperty(propertyRemoteId: String): Boolean {
        return foodBillDao.countBillsForProperty(
            hotelRemoteId = hotelRemoteId,
            propertyRemoteId = propertyRemoteId
        ) > 0
    }

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

    private suspend fun resolveBillSupplierSnapshot(propertyRemoteId: String?): BillSupplierSnapshot {
        val property = propertyRemoteId
            ?.takeIf { it.isNotBlank() }
            ?.let { managedPropertyDao.getByRemoteId(it) }
        val hotel = hotelDao.getByRemoteId(hotelRemoteId)

        val profile = InvoiceNumberPolicy.resolveBillingProfile(
            organization = hotel,
            property = property,
            fallbackPrefix = "FOL"
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
        return cloudSyncManager.reserveInvoiceNumber(prefix, now)
    }


    fun startRealtimeSync() {
        realtimeSyncError.value = null

        cloudSyncManager.startHotelListener(
            onHotelChanged = { hotel ->
                scope.launch {
                    upsertRemoteHotelIfNewer(hotel.markSynced())
                    clearRealtimeSyncError()
                }
            },
            onSyncError = { markRealtimeSyncError("Hotel", it) }
        )

        scope.launch {
            val roomSince = syncBoundary(
                localCount = roomDao.countAllRooms(hotelRemoteId),
                maxUpdatedAt = roomDao.maxUpdatedAt(hotelRemoteId)
            )
            cloudSyncManager.startRoomListener(
                sinceUpdatedAt = roomSince,
                onRoomsChanged = { rooms ->
                    scope.launch {
                        rooms.forEach { upsertRemoteRoomIfNewer(it.markSynced()) }
                        clearRealtimeSyncErrorIfClean()
                    }
                },
                onSyncError = { markRealtimeSyncError("Room", it) }
            )

            val propertySince = syncBoundary(
                localCount = managedPropertyDao.countAllProperties(hotelRemoteId),
                maxUpdatedAt = managedPropertyDao.maxUpdatedAt(hotelRemoteId)
            )
            cloudSyncManager.startManagedPropertyListener(
                sinceUpdatedAt = propertySince,
                onPropertiesChanged = { properties ->
                    scope.launch {
                        properties.forEach { upsertRemoteManagedPropertyIfNewer(it.markSynced()) }
                        clearRealtimeSyncErrorIfClean()
                    }
                },
                onSyncError = { markRealtimeSyncError("Property", it) }
            )

            val sourceSince = syncBoundary(
                localCount = bookingSourceDao.countAllSources(hotelRemoteId),
                maxUpdatedAt = bookingSourceDao.maxUpdatedAt(hotelRemoteId)
            )
            cloudSyncManager.startSourceListener(
                sinceUpdatedAt = sourceSince,
                onSourcesChanged = { sources ->
                    scope.launch {
                        sources.forEach { upsertRemoteSourceIfNewer(it.markSynced()) }
                        clearRealtimeSyncErrorIfClean()
                    }
                },
                onSyncError = { markRealtimeSyncError("Source", it) }
            )
            cloudSyncManager.startBookingListener(
                sinceUpdatedAt = null,
                onBookingsChanged = { bookings ->
                    scope.launch {
                        bookings.forEach {
                            upsertRemoteBookingIfNewer(
                                it.markSynced().withCalculatedPayment()
                            )
                        }
                        clearRealtimeSyncErrorIfClean()
                    }
                },
                onSyncError = { markRealtimeSyncError("Booking", it) }
            )

            cloudSyncManager.startPaymentListener(
                sinceUpdatedAt = null,
                onPaymentsChanged = { payments ->
                    scope.launch {
                        payments.forEach { upsertRemotePaymentIfNewer(it.markSynced()) }
                        clearRealtimeSyncErrorIfClean()
                    }
                },
                onSyncError = { markRealtimeSyncError("Payment", it) }
            )

            cloudSyncManager.startFinancialLineListener(
                sinceUpdatedAt = null,
                onLinesChanged = { lines ->
                    scope.launch {
                        lines.forEach { upsertRemoteFinancialLineIfNewer(it.markSynced()) }
                        clearRealtimeSyncErrorIfClean()
                    }
                },
                onSyncError = { markRealtimeSyncError("Financial line", it) }
            )

            val accountingChargeSince = syncBoundary(
                localCount = bookingAccountingChargeDao.countAllCharges(hotelRemoteId),
                maxUpdatedAt = bookingAccountingChargeDao.maxUpdatedAt(hotelRemoteId)
            )
            cloudSyncManager.startAccountingChargeListener(
                sinceUpdatedAt = accountingChargeSince,
                onChargesChanged = { charges ->
                    scope.launch {
                        charges.forEach { upsertRemoteAccountingChargeIfNewer(it.markSynced()) }
                        clearRealtimeSyncErrorIfClean()
                    }
                },
                onSyncError = { markRealtimeSyncError("Accounting charge", it) }
            )


            repairAutoPaymentAllocationsOnce()
            repairBookingPaymentAllocationsOnce()
            repairOtaBookingStatuses()
            retryFailedSync(force = true)
        }
    }
    fun stopRealtimeSync() {
        cloudSyncManager.stop()
    }

    fun addRoom(
        roomName: String,
        categoryName: String,
        categoryColor: String,
        propertyRemoteId: String? = null
    ) {
        scope.launch {
            val cleanName = roomName.trim()
            if (cleanName.isEmpty()) return@launch
            val existing = roomDao.getByRoomName(hotelRemoteId, cleanName)
            val remoteId = existing?.remoteId ?: stableRoomRemoteId(cleanName)
            val room = RoomEntity(
                localId = existing?.localId ?: 0,
                remoteId = remoteId,
                hotelRemoteId = hotelRemoteId,
                roomName = cleanName,
                categoryName = "",
                categoryColor = "#EEF0F2",
                categorySortOrder = 0,
                propertyRemoteId = propertyRemoteId?.takeIf { it.isNotBlank() },
                sortOrder = existing?.sortOrder
                    ?: ((roomDao.maxSortOrderForCategory(hotelRemoteId, "") ?: -1) + 1),
                updatedAt = System.currentTimeMillis(),
                isDeleted = false,
                syncState = SyncState.PENDING,
                lastSyncError = null
            )
            roomDao.upsert(room)
            pushRoomAndMark(room)
        }
    }

    fun saveHotel(
        hotelName: String,
        gstNumber: String?,
        address: String?,
        phone: String?
    ) {
        scope.launch {
            val existing = hotelDao.getByRemoteId(hotelRemoteId)
            val hotel = HotelEntity(
                remoteId = hotelRemoteId,
                hotelName = hotelName.trim().ifEmpty { "Booking Register" },
                gstNumber = gstNumber?.trim()?.ifEmpty { null },
                address = address?.trim()?.ifEmpty { null },
                phone = phone?.trim()?.ifEmpty { null },
                updatedAt = System.currentTimeMillis(),
                isDeleted = false,
                syncState = SyncState.PENDING,
                lastSyncError = null,
                lastSyncedAt = existing?.lastSyncedAt
            )
            hotelDao.upsert(hotel)
            pushHotelAndMark(hotel)
        }
    }

    suspend fun saveBooking(booking: BookingEntity): SaveResult =
        saveBookingInternal(booking, financialLines = null)

    suspend fun saveBookingWithFinancialLines(
        booking: BookingEntity,
        financialLines: List<BookingFinancialLineEntity>
    ): SaveResult = saveBookingInternal(booking, financialLines)

    private suspend fun saveBookingInternal(
        booking: BookingEntity,
        financialLines: List<BookingFinancialLineEntity>?
    ): SaveResult {
        val existingBooking = bookingDao.getByRemoteId(booking.remoteId)
        if (existingBooking != null &&
            isRoomRateLocked(booking.remoteId) &&
            BilledRoomRateLockPolicy.bookingFinancialsChanged(existingBooking, booking)
        ) {
            return SaveResult.Error("Room rate is locked because the final bill has been issued.")
        }
        val existingPayments = bookingPaymentDao.getPaymentsForBooking(hotelRemoteId, booking.remoteId)
        val existingFoodOrders = foodOrderDao.getOrdersForBooking(hotelRemoteId, booking.remoteId)
        val existingFinancialLines = bookingFinancialLineDao.getLinesForBooking(hotelRemoteId, booking.remoteId)
        if (financialLines != null &&
            isRoomRateLocked(booking.remoteId) &&
            BilledRoomRateLockPolicy.financialLinesChanged(existingFinancialLines, financialLines)
        ) {
            return SaveResult.Error("Room charges are locked because the final bill has been issued.")
        }
        validateRoomsForBookingSave(booking, existingBooking)?.let { error ->
            return error
        }

        val existingFolio = FolioSummaryBuilder.build(
            booking = booking,
            payments = existingPayments,
            foodOrders = existingFoodOrders,
            foodOrderItems = foodItemsForOrders(existingFoodOrders),
            bookingFinancialLines = existingFinancialLines
        )
        val aggregatePaid = BookingPaymentSourcePolicy.authoritativeStayPaid(
            existingBooking = existingBooking,
            requestedPaid = booking.paid,
            hasPaymentRows = existingPayments.isNotEmpty(),
            stayPaidFromRows = existingFolio.stayPaid
        )
        val bookingWithLineCache = financialLines
            ?.let { applyRoomNightFinancialCache(booking, it) }
            ?: booking
        val propertyRemoteId = bookingWithLineCache.propertyRemoteId
            ?: bookingPropertyForRooms(bookingWithLineCache.roomRemoteIds)
        val normalized = bookingWithLineCache.copy(
            paid = aggregatePaid,
            propertyRemoteId = propertyRemoteId,
            bookingStatus = existingBooking?.bookingStatus?.ifBlank { BookingStatus.RESERVED }
                ?: bookingWithLineCache.bookingStatus.ifBlank { BookingStatus.RESERVED },
            actualCheckInAt = existingBooking?.actualCheckInAt ?: bookingWithLineCache.actualCheckInAt,
            actualCheckOutAt = existingBooking?.actualCheckOutAt ?: bookingWithLineCache.actualCheckOutAt,
            checkoutNote = existingBooking?.checkoutNote ?: bookingWithLineCache.checkoutNote,
            reopenNote = existingBooking?.reopenNote ?: bookingWithLineCache.reopenNote,
            reopenedAt = existingBooking?.reopenedAt ?: bookingWithLineCache.reopenedAt
        ).withCalculatedPayment().copy(
            updatedAt = System.currentTimeMillis(),
            isDeleted = false,
            syncState = SyncState.PENDING,
            lastSyncError = null,
            baseRevision = bookingWithLineCache.baseRevision.takeIf { it > 0 } ?: bookingWithLineCache.revision
        )

        val overlapping = bookingDao.getOverlappingBookings(
            hotelRemoteId = hotelRemoteId,
            checkInMillis = normalized.checkInMillis,
            checkOutMillis = normalized.checkOutMillis
        ).any { existing ->
            existing.remoteId != normalized.remoteId &&
                    existing.roomRemoteIds.any { it in normalized.roomRemoteIds }
        }

        if (overlapping) {
            return SaveResult.Conflict("Selected room is already booked for these dates")
        }

        if (financialLines != null) {
            val integrity = RoomNightFinancialIntegrity.validate(normalized, financialLines)
            if (!integrity.isValid) {
                return SaveResult.Error("Room accounting integrity error: ${integrity.errors.joinToString()}")
            }
        }

        val changedFinancialLines = financialLines?.let { lines ->
            prepareFinancialLineChanges(
                booking = normalized,
                lines = lines,
                current = existingFinancialLines,
                now = normalized.updatedAt
            )
        }.orEmpty()
        val changeSet = BookingChangeSet.create(
            previous = existingBooking,
            requested = normalized,
            previousLines = existingFinancialLines,
            requestedLines = financialLines ?: existingFinancialLines
        )

        db.withTransaction {
            bookingDao.upsert(normalized)
            changedFinancialLines.forEach { line -> bookingFinancialLineDao.upsert(line) }
            seedInitialPaymentIfNeeded(normalized)
            bookingSyncOutboxDao.upsert(
                BookingSyncOutboxEntity(
                    operationId = UUID.randomUUID().toString(),
                    hotelRemoteId = hotelRemoteId,
                    bookingRemoteId = normalized.remoteId,
                    changeSetJson = changeSet.toJson(),
                    createdAt = normalized.updatedAt
                )
            )
        }
        enqueueBackgroundSync()
        return SaveResult.Success(syncPending = true)
    }

    suspend fun addBookingPayment(
        booking: BookingEntity,
        amount: Double,
        paymentType: String = BookingPaymentType.PAYMENT,
        paymentCategory: String = BookingPaymentCategory.AUTO,
        note: String? = null,
        method: String? = null,
        originalPaymentRemoteId: String? = null,
        paymentMillis: Long = System.currentTimeMillis()
    ): SaveResult {
        if (amount <= 0.0) return SaveResult.Error("Enter a valid amount")
        val now = System.currentTimeMillis()
        val result = db.withTransaction {
            val currentBooking = bookingDao.getByRemoteId(booking.remoteId)
                ?: return@withTransaction SaveResult.Error("Booking not found")
            if (currentBooking.bookingStatus == BookingStatus.CANCELLED) {
                when (paymentType) {
                    BookingPaymentType.PAYMENT,
                    BookingPaymentType.ADVANCE ->
                        return@withTransaction SaveResult.Error(
                            "Payments cannot be added to a cancelled booking. Use its cancellation settlement."
                        )
                    BookingPaymentType.REFUND -> {
                        if (currentBooking.cancellationSettlementStatus != CancellationSettlementStatus.DECIDED) {
                            return@withTransaction SaveResult.Error(
                                "Decide the Direct Booking cancellation settlement before recording a refund."
                            )
                        }
                        val refundDue = CancellationSettlementPolicy.refundDue(
                            bookingApprovedAmount = currentBooking.cancellationApprovedRefundAmount,
                            refundBaselineAmount = currentBooking.cancellationRefundBaselineAmount,
                            payments = bookingPaymentDao.getPaymentsForBooking(hotelRemoteId, currentBooking.remoteId)
                        )
                        if (amount > refundDue + 0.001) {
                            return@withTransaction SaveResult.Error(
                                "Refund exceeds the approved cancellation refund of ${formatAmount(refundDue)}."
                            )
                        }
                    }
                }
            }
            val requestedCategory = BookingPaymentCategory.normalize(paymentCategory)
            if (!BookingPricingStatus.canTakeStayPayment(currentBooking.pricingStatus) &&
                paymentType in setOf(BookingPaymentType.PAYMENT, BookingPaymentType.ADVANCE) &&
                requestedCategory in setOf(BookingPaymentCategory.AUTO, BookingPaymentCategory.STAY)
            ) {
                return@withTransaction SaveResult.Error("Enter and save the room rate before taking a stay payment")
            }
            val existingPayments = bookingPaymentDao.getPaymentsForBooking(hotelRemoteId, currentBooking.remoteId)
            val bookingFoodOrders = foodOrderDao.getOrdersForBooking(hotelRemoteId, currentBooking.remoteId)
            val accountingCharges = bookingAccountingChargeDao.getChargesForBooking(hotelRemoteId, currentBooking.remoteId)
            val bookingFinancialLines = bookingFinancialLineDao.getLinesForBooking(hotelRemoteId, currentBooking.remoteId)
            val currentSummary = FolioSummaryBuilder.build(
                booking = currentBooking,
                payments = existingPayments,
                foodOrders = bookingFoodOrders,
                foodOrderItems = foodItemsForOrders(bookingFoodOrders),
                accountingCharges = accountingCharges,
                bookingFinancialLines = bookingFinancialLines
            )
            if (currentSummary.integrityErrors.isNotEmpty()) {
                return@withTransaction SaveResult.Error(
                    "Accounting integrity error: ${currentSummary.integrityErrors.joinToString()}"
                )
            }
            val originalPayment = if (paymentType == BookingPaymentType.REFUND) {
                val originalId = originalPaymentRemoteId?.takeIf { it.isNotBlank() }
                    ?: return@withTransaction SaveResult.Error("Select the original payment to refund")
                existingPayments.firstOrNull {
                    it.remoteId == originalId &&
                        it.paymentType in setOf(BookingPaymentType.PAYMENT, BookingPaymentType.ADVANCE)
                } ?: return@withTransaction SaveResult.Error("Original payment was not found")
            } else null
            val alreadyRefunded = originalPayment?.let { original ->
                existingPayments.filter {
                    it.paymentType == BookingPaymentType.REFUND &&
                        it.originalPaymentRemoteId == original.remoteId
                }.sumOf { it.amount }
            } ?: 0.0
            val allocationCategory = if (paymentType == BookingPaymentType.PAYMENT || paymentType == BookingPaymentType.ADVANCE) {
                paymentCategory
            } else {
                BookingPaymentCategory.STAY
            }
            val allocation = if (originalPayment != null) {
                RefundAllocationPolicy.reverse(originalPayment, amount, alreadyRefunded)
                    ?: return@withTransaction SaveResult.Error("Refund exceeds the remaining refundable amount")
            } else PaymentAllocationPolicy.allocate(
                amount = amount,
                selectedCategory = allocationCategory,
                charges = currentSummary.chargeBuckets,
                alreadyPaid = currentSummary.paidBuckets
            )
            val payment = BookingPaymentEntity(
                remoteId = "${currentBooking.remoteId}_payment_${UUID.randomUUID()}",
                hotelRemoteId = hotelRemoteId,
                bookingRemoteId = currentBooking.remoteId,
                originalPaymentRemoteId = originalPayment?.remoteId,
                paymentType = paymentType,
                paymentCategory = allocation.selectedCategory,
                amount = amount,
                allocatedStayAmount = allocation.stayAmount,
                allocatedFoodAmount = allocation.foodAmount,
                allocatedServiceAmount = allocation.serviceAmount,
                allocatedDamageAmount = allocation.damageAmount,
                unappliedAmount = allocation.unappliedAmount,
                paymentMillis = paymentMillis,
                method = method?.trim()?.ifEmpty { null },
                note = note?.trim()?.ifEmpty { null },
                updatedAt = now,
                isDeleted = false,
                syncState = SyncState.PENDING
            )
            bookingPaymentDao.upsert(payment)
            recalculateBookingPaymentAggregateInTransaction(currentBooking)
            SaveResult.Success(syncPending = true)
        }
        if (result is SaveResult.Success) enqueueBackgroundSync()
        return result
    }

    suspend fun checkInBooking(booking: BookingEntity, note: String? = null): SaveResult {
        val current = bookingDao.getByRemoteId(booking.remoteId)
            ?: return SaveResult.Error("Booking not found")
        if (current.bookingStatus == BookingStatus.CANCELLED) {
            return SaveResult.Error("A cancelled booking cannot be checked in.")
        }
        if (current.bookingStatus == BookingStatus.CHECKED_IN) return SaveResult.Success()
        if (current.bookingStatus == BookingStatus.CHECKED_OUT) {
            return SaveResult.Error("Booking is already checked out. Reopen it first.")
        }
        val now = System.currentTimeMillis()
        val cleanNote = note?.trim()?.ifEmpty { null }
        if (now < current.checkInMillis && cleanNote == null) {
            return SaveResult.Error("Early check-in requires a reason in notes/comments.")
        }
        val occupiedBy = checkedInRoomConflict(current)
        if (occupiedBy != null) {
            return SaveResult.Conflict("Cannot check in. Room is still occupied by previous guest.")
        }
        return updateBookingLifecycle(
            current.copy(
                bookingStatus = BookingStatus.CHECKED_IN,
                actualCheckInAt = current.actualCheckInAt ?: now,
                actualCheckOutAt = null,
                checkoutNote = null,
                notes = appendLifecycleNote(current.notes, "Check-in reason", cleanNote)
            )
        )
    }

    suspend fun checkOutBooking(booking: BookingEntity, note: String? = null): SaveResult {
        val current = bookingDao.getByRemoteId(booking.remoteId)
            ?: return SaveResult.Error("Booking not found")
        if (current.bookingStatus == BookingStatus.CHECKED_OUT) return SaveResult.Success()
        if (current.bookingStatus != BookingStatus.CHECKED_IN) {
            return SaveResult.Error("Check-out is allowed only after check-in.")
        }
        val payments = bookingPaymentDao.getPaymentsForBooking(hotelRemoteId, current.remoteId)
        val foodOrders = foodOrderDao.getOrdersForBooking(hotelRemoteId, current.remoteId)
        val accountingCharges = bookingAccountingChargeDao.getChargesForBooking(hotelRemoteId, current.remoteId)
        val bookingFinancialLines = ensureLegacyRoomFinancialLines(current)
        val roomIntegrity = RoomNightFinancialIntegrity.validate(current, bookingFinancialLines)
        if (!roomIntegrity.isValid) {
            return SaveResult.Error("Cannot check out: ${roomIntegrity.errors.joinToString()}")
        }
        val summary = FolioSummaryBuilder.build(
            booking = current,
            payments = payments,
            foodOrders = foodOrders,
            foodOrderItems = foodItemsForOrders(foodOrders),
            accountingCharges = accountingCharges,
            bookingFinancialLines = bookingFinancialLines
        )
        if (summary.integrityErrors.isNotEmpty()) {
            return SaveResult.Error("Cannot check out: ${summary.integrityErrors.joinToString()}")
        }
        val pendingCheckoutBalance = CheckoutBalancePolicy.pendingBalanceForCheckout(current, summary)
        if (pendingCheckoutBalance > 0.01) {
            return SaveResult.Error("Pending balance: ${formatAmount(pendingCheckoutBalance)}. Please collect payment first.")
        }
        val normalized = current.copy(paid = summary.stayPaid).withCalculatedPayment()
        val now = System.currentTimeMillis()
        val cleanNote = note?.trim()?.ifEmpty { null }
        if (now < current.checkOutMillis && cleanNote == null) {
            return SaveResult.Error("Early check-out requires a reason in notes/comments.")
        }

        return updateBookingLifecycle(
            normalized.copy(
                bookingStatus = BookingStatus.CHECKED_OUT,
                actualCheckOutAt = now,
                checkoutNote = cleanNote
            )
        )
    }

    suspend fun reopenCheckedOutBooking(booking: BookingEntity, note: String): SaveResult {
        val current = bookingDao.getByRemoteId(booking.remoteId)
            ?: return SaveResult.Error("Booking not found")
        if (current.bookingStatus != BookingStatus.CHECKED_OUT) {
            return SaveResult.Error("Only checked-out bookings can be reopened.")
        }
        val cleanNote = note.trim()
        if (cleanNote.isEmpty()) return SaveResult.Error("Please enter a reopen note.")
        val reopened = current.copy(
            bookingStatus = BookingStatus.CHECKED_IN,
            actualCheckOutAt = null,
            reopenNote = cleanNote,
            reopenedAt = System.currentTimeMillis()
        )
        val occupiedBy = checkedInRoomConflict(reopened)
        if (occupiedBy != null) {
            return SaveResult.Conflict("Cannot reopen. Room is occupied by another checked-in booking.")
        }
        return updateBookingLifecycle(reopened)
    }


    fun saveManagedProperty(
        existing: ManagedPropertyEntity?,
        propertyName: String,
        legalName: String?,
        gstNumber: String?,
        address: String?,
        phone: String?,
        email: String?,
        invoicePrefix: String?,
        state: String?,
        allowBillingIdentityChange: Boolean = false
    ) {
        scope.launch {
            val cleanName = propertyName.trim()
            if (cleanName.isEmpty()) return@launch
            val base = existing ?: managedPropertyDao.getByName(hotelRemoteId, cleanName)
            val billingLocked = base?.remoteId
                ?.let { foodBillDao.countBillsForProperty(hotelRemoteId, it) > 0 }
                ?: false
            val preserveBillingIdentity = billingLocked && !allowBillingIdentityChange
            val property = ManagedPropertyEntity(
                localId = base?.localId ?: 0,
                remoteId = base?.remoteId ?: stableManagedPropertyRemoteId(cleanName),
                hotelRemoteId = hotelRemoteId,
                propertyName = cleanName,
                legalName = if (preserveBillingIdentity) base?.legalName else legalName?.trim()?.ifEmpty { null },
                gstNumber = if (preserveBillingIdentity) base?.gstNumber else gstNumber?.trim()?.uppercase(Locale.ROOT)?.ifEmpty { null },
                address = if (preserveBillingIdentity) base?.address else address?.trim()?.ifEmpty { null },
                phone = phone?.trim()?.ifEmpty { null },
                email = email?.trim()?.ifEmpty { null },
                invoicePrefix = if (preserveBillingIdentity) base?.invoicePrefix else invoicePrefix?.trim()?.ifEmpty { null },
                state = if (preserveBillingIdentity) base?.state else state?.trim()?.ifEmpty { null },
                sortOrder = base?.sortOrder ?: managedPropertyDao.countProperties(hotelRemoteId),
                updatedAt = System.currentTimeMillis(),
                isDeleted = false,
                syncState = SyncState.PENDING,
                lastSyncError = null,
                lastSyncedAt = base?.lastSyncedAt,
                revision = base?.revision ?: 0,
                baseRevision = base?.baseRevision?.takeIf { it > 0 } ?: base?.revision ?: 0,
                updatedByUid = base?.updatedByUid
            )
            managedPropertyDao.upsert(property)
            pushManagedPropertyAndMark(property)
        }
    }

    suspend fun saveBookingFinancialLines(
        booking: BookingEntity,
        lines: List<BookingFinancialLineEntity>
    ): SaveResult {
        val now = System.currentTimeMillis()
        val current = bookingFinancialLineDao.getLinesForBooking(hotelRemoteId, booking.remoteId)
        if (isRoomRateLocked(booking.remoteId)) {
            return if (BilledRoomRateLockPolicy.financialLinesChanged(current, lines)) {
                SaveResult.Error("Room charges are locked because the final bill has been issued.")
            } else {
                SaveResult.Success(syncPending = current.any { it.syncState != SyncState.SYNCED })
            }
        }
        val changedLines = prepareFinancialLineChanges(booking, lines, current, now)
        changedLines.forEach { line -> bookingFinancialLineDao.upsert(line) }
        enqueueBackgroundSync()
        return SaveResult.Success(syncPending = changedLines.any { it.syncState != SyncState.SYNCED })
    }

    private suspend fun prepareFinancialLineChanges(
        booking: BookingEntity,
        lines: List<BookingFinancialLineEntity>,
        current: List<BookingFinancialLineEntity>,
        now: Long
    ): List<BookingFinancialLineEntity> {
        val incomingIds = lines.map { it.remoteId }.toSet()
        val deletedLines = current
            .filter { it.remoteId !in incomingIds }
            .map { line ->
                line.copy(
                    updatedAt = now,
                    isDeleted = true,
                    syncState = SyncState.PENDING,
                    lastSyncError = null,
                    baseRevision = line.baseRevision.takeIf { it > 0 } ?: line.revision
                )
            }
        val normalizedLines = lines.map { line ->
            val existing = current.firstOrNull { it.remoteId == line.remoteId }
            val propertyRemoteId = line.propertyRemoteId
                ?: roomDao.getByRemoteId(line.roomRemoteId)
                    ?.propertyRemoteId
                    ?.takeIf { it.isNotBlank() }
            line.copy(
                localId = existing?.localId ?: line.localId,
                hotelRemoteId = hotelRemoteId,
                bookingRemoteId = booking.remoteId,
                propertyRemoteId = propertyRemoteId,
                updatedAt = now,
                isDeleted = false,
                syncState = SyncState.PENDING,
                lastSyncError = null,
                lastSyncedAt = existing?.lastSyncedAt,
                revision = existing?.revision ?: line.revision,
                baseRevision = existing?.baseRevision?.takeIf { it > 0 }
                    ?: existing?.revision
                    ?: line.baseRevision
            )
        }
        return deletedLines + normalizedLines
    }


    private suspend fun ensureLegacyRoomFinancialLines(
        booking: BookingEntity
    ): List<BookingFinancialLineEntity> {
        return bookingFinancialLineDao.getLinesForBooking(hotelRemoteId, booking.remoteId)
    }

    suspend fun addBookingAccountingCharge(
        booking: BookingEntity,
        chargeType: String,
        amount: Double,
        description: String,
        reason: String? = null,
        accountBucket: String? = null,
        hsnSacCode: String? = null,
        gstRatePercent: Double = 0.0,
        taxInclusive: Boolean = true,
        taxableAmount: Double? = null,
        approvedBy: String? = null,
        createdBy: String? = null,
        chargeMillis: Long = System.currentTimeMillis()
    ): SaveResult {
        if (amount <= 0.0) return SaveResult.Error("Enter a valid amount")
        val current = bookingDao.getByRemoteId(booking.remoteId)
            ?: return SaveResult.Error("Booking not found")
        if (current.bookingStatus == BookingStatus.CANCELLED) {
            return SaveResult.Error("New charges cannot be added to a cancelled booking.")
        }
        val cleanDescription = description.trim()
        if (cleanDescription.isEmpty()) return SaveResult.Error("Enter a description")

        val now = System.currentTimeMillis()
        val charge = BookingAccountingChargeEntity(
            remoteId = "${current.remoteId}_accounting_${UUID.randomUUID()}",
            hotelRemoteId = hotelRemoteId,
            bookingRemoteId = current.remoteId,
            chargeType = BookingAccountingChargeType.normalize(chargeType),
            accountBucket = BookingPaymentCategory.normalize(accountBucket)
                .takeIf { it in setOf(BookingPaymentCategory.STAY, BookingPaymentCategory.FOOD, BookingPaymentCategory.SERVICE, BookingPaymentCategory.DAMAGE) },
            amount = amount,
            description = cleanDescription,
            reason = reason?.trim()?.ifEmpty { null },
            hsnSacCode = hsnSacCode?.trim()?.ifEmpty { null },
            gstRatePercent = gstRatePercent.coerceAtLeast(0.0),
            taxInclusive = taxInclusive,
            taxableAmount = taxableAmount?.coerceAtLeast(0.0),
            approvedBy = approvedBy?.trim()?.ifEmpty { null },
            createdBy = createdBy?.trim()?.ifEmpty { null },
            chargeMillis = chargeMillis,
            updatedAt = now,
            isDeleted = false,
            syncState = SyncState.PENDING
        )

        bookingAccountingChargeDao.upsert(charge)
        enqueueBackgroundSync()
        return SaveResult.Success(syncPending = true)
    }

    private suspend fun repairAutoPaymentAllocationsOnce() {
        val now = System.currentTimeMillis()
        bookingDao.getBookings(hotelRemoteId).filter { !it.isDeleted }.forEach { booking ->
            val payments = bookingPaymentDao.getPaymentsForBooking(hotelRemoteId, booking.remoteId)
            if (payments.none { it.paymentCategory == BookingPaymentCategory.AUTO }) return@forEach
            val foodOrders = foodOrderDao.getOrdersForBooking(hotelRemoteId, booking.remoteId)
            val bookingFinancialLines = bookingFinancialLineDao.getLinesForBooking(hotelRemoteId, booking.remoteId)

            val summary = FolioSummaryBuilder.build(
                booking = booking,
                payments = payments,
                foodOrders = foodOrders,
                foodOrderItems = foodItemsForOrders(foodOrders),
                bookingFinancialLines = bookingFinancialLines
            )
            val repairedPayments = PaymentAllocationRepairPolicy.moveAutoStayOverpaymentToFood(
                stayTotal = summary.stayTotal,
                foodTotal = summary.foodTotal,
                payments = payments,
                now = now
            )
            repairedPayments.forEach { payment ->
                bookingPaymentDao.upsert(payment)
            }
            if (repairedPayments.isNotEmpty()) {
                recalculateBookingPaymentAggregate(booking)
                enqueueBackgroundSync()
            }
        }
    }

    private suspend fun repairBookingPaymentAllocationsOnce() {
        val now = System.currentTimeMillis()
        bookingDao.getBookings(hotelRemoteId).filter { !it.isDeleted }.forEach { booking ->
            val payments = bookingPaymentDao.getPaymentsForBooking(hotelRemoteId, booking.remoteId)
                .filter { !it.isDeleted }
                .sortedWith(compareBy<BookingPaymentEntity> { it.paymentMillis }.thenBy { it.localId })
            val allocatablePayments = payments.filter {
                it.paymentType == BookingPaymentType.PAYMENT || it.paymentType == BookingPaymentType.ADVANCE
            }
            if (allocatablePayments.isEmpty()) return@forEach

            val foodOrders = foodOrderDao.getOrdersForBooking(hotelRemoteId, booking.remoteId)
            val accountingCharges = bookingAccountingChargeDao.getChargesForBooking(hotelRemoteId, booking.remoteId)
            val bookingFinancialLines = bookingFinancialLineDao.getLinesForBooking(hotelRemoteId, booking.remoteId)

            val charges = FolioSummaryBuilder.build(
                booking = booking,
                payments = emptyList(),
                foodOrders = foodOrders,
                foodOrderItems = foodItemsForOrders(foodOrders),
                accountingCharges = accountingCharges,
                bookingFinancialLines = bookingFinancialLines
            ).chargeBuckets
            var paid = ChargeBuckets()
            var changed = false

            allocatablePayments.forEach { payment ->
                val allocation = PaymentAllocationPolicy.allocate(
                    amount = payment.amount,
                    selectedCategory = payment.paymentCategory,
                    charges = charges,
                    alreadyPaid = paid
                )
                paid = ChargeBuckets(
                    stay = paid.stay + allocation.stayAmount,
                    food = paid.food + allocation.foodAmount,
                    service = paid.service + allocation.serviceAmount,
                    damage = paid.damage + allocation.damageAmount
                )
                val needsRepair =
                    payment.paymentCategory != allocation.selectedCategory ||
                            payment.allocatedStayAmount != allocation.stayAmount ||
                            payment.allocatedFoodAmount != allocation.foodAmount ||
                            payment.allocatedServiceAmount != allocation.serviceAmount ||
                            payment.allocatedDamageAmount != allocation.damageAmount ||
                            payment.unappliedAmount != allocation.unappliedAmount
                if (!needsRepair) return@forEach

                val repaired = payment.copy(
                    paymentCategory = allocation.selectedCategory,
                    allocatedStayAmount = allocation.stayAmount,
                    allocatedFoodAmount = allocation.foodAmount,
                    allocatedServiceAmount = allocation.serviceAmount,
                    allocatedDamageAmount = allocation.damageAmount,
                    unappliedAmount = allocation.unappliedAmount,
                    updatedAt = now,
                    syncState = SyncState.PENDING,
                    lastSyncError = null,
                    baseRevision = payment.baseRevision.takeIf { it > 0 } ?: payment.revision
                )
                bookingPaymentDao.upsert(repaired)
                changed = true
            }

            if (changed) {
                recalculateBookingPaymentAggregate(booking)
                enqueueBackgroundSync()
            }
        }
    }
    suspend fun addSourcePayment(
        sourceRemoteId: String?,
        sourceName: String,
        amount: Double,
        note: String? = null
    ): SaveResult {
        var remaining = amount.coerceAtLeast(0.0)
        if (remaining <= 0.0) return SaveResult.Error("Enter a valid amount")

        val openBookings = bookingDao.getBookings(hotelRemoteId)
            .filter { booking ->
                !booking.isDeleted &&
                        booking.bookingStatus != BookingStatus.CANCELLED &&
                        booking.sourceType == BookingSourceType.OTA &&
                        booking.balance > 0.0 &&
                        if (!sourceRemoteId.isNullOrBlank()) {
                            booking.sourceRemoteId == sourceRemoteId
                        } else {
                            booking.sourceName.equals(sourceName, ignoreCase = true)
                        }
            }
            .sortedWith(compareBy<BookingEntity> { it.checkOutMillis }.thenBy { it.checkInMillis })

        if (openBookings.isEmpty()) return SaveResult.Error("No open receivables for $sourceName")

        var lastResult: SaveResult = SaveResult.Success()
        for (booking in openBookings) {
            if (remaining <= 0.0) break
            val applied = minOf(remaining, booking.balance)
            lastResult = addBookingPayment(
                booking = booking,
                amount = applied,
                paymentType = BookingPaymentType.PAYMENT,
                note = note?.trim()?.ifEmpty { null } ?: "${sourceName} payout"
            )
            if (lastResult is SaveResult.Conflict) return lastResult
            remaining -= applied
        }

        return if (remaining > 0.0) {
            SaveResult.Error("Saved payout. Extra ${String.format(Locale.getDefault(), "%.0f", remaining)} was not applied.")
        } else {
            lastResult
        }
    }

    suspend fun generateFinalBookingBill(booking: BookingEntity): SaveResult {
        val current = bookingDao.getByRemoteId(booking.remoteId)
            ?: return SaveResult.Error("Booking not found")
        if (current.bookingStatus == BookingStatus.CANCELLED) {
            return SaveResult.Error("A final bill cannot be generated for a cancelled booking.")
        }
        if (!BookingPricingStatus.canGenerateRoomBill(current.pricingStatus)) {
            return SaveResult.Error("Enter and save the room rate before generating the final bill")
        }
        val existingFinalBill = foodBillDao.getFinalBillForBooking(
            hotelRemoteId = hotelRemoteId,
            remoteIdPrefix = "${current.remoteId}_final_bill_"
        )
        val payments = bookingPaymentDao.getPaymentsForBooking(hotelRemoteId, current.remoteId)
        val bookingFoodOrders = foodOrderDao.getOrdersForBooking(hotelRemoteId, current.remoteId)
        val accountingCharges = bookingAccountingChargeDao.getChargesForBooking(hotelRemoteId, current.remoteId)
        val linkedOrders = bookingFoodOrders
            .filter {
                it.status != FoodOrderStatus.CANCELLED &&
                        it.status != FoodOrderStatus.BILLED &&
                        it.status != FoodOrderStatus.BILLED_IN_FOLIO &&
                        it.linkedFinalBillId.isNullOrBlank()
            }
        val unbilledServiceCharges = FinalBillChargeSelectionPolicy.unbilledServiceCharges(accountingCharges)
        val unbilledDamageCharges = FinalBillChargeSelectionPolicy.unbilledDamageCharges(accountingCharges)
        if (existingFinalBill != null && linkedOrders.isEmpty() &&
            unbilledServiceCharges.isEmpty() && unbilledDamageCharges.isEmpty()
        ) {
            return SaveResult.Success(syncPending = existingFinalBill.syncState != SyncState.SYNCED)
        }
        val stayFinancialLines = ensureLegacyRoomFinancialLines(current)
        val roomIntegrity = RoomNightFinancialIntegrity.validate(current, stayFinancialLines)
        if (!roomIntegrity.isValid) {
            return SaveResult.Error("Cannot generate final bill: ${roomIntegrity.errors.joinToString()}")
        }
        val roomDiscount = accountingCharges.discountFor(BookingPaymentCategory.STAY)
        val foodDiscount = accountingCharges.discountFor(BookingPaymentCategory.FOOD)
        val serviceDiscount = accountingCharges.discountFor(BookingPaymentCategory.SERVICE)
        val totalDiscount = roomDiscount + foodDiscount + serviceDiscount
        val summary = FolioSummaryBuilder.build(
            booking = current,
            payments = payments,
            foodOrders = bookingFoodOrders,
            foodOrderItems = foodItemsForOrders(bookingFoodOrders),
            accountingCharges = accountingCharges,
            bookingFinancialLines = stayFinancialLines
        )
        if (summary.integrityErrors.isNotEmpty()) {
            return SaveResult.Error("Cannot generate final bill: ${summary.integrityErrors.joinToString()}")
        }
        val pendingGuestPayableBalance = FinalBillGenerationPolicy.pendingGuestPayableBalance(current, summary)
        if (pendingGuestPayableBalance > 0.01) {
            return SaveResult.Error(
                "Collect guest payable balance ${formatAmount(pendingGuestPayableBalance)} before generating bill"
            )
        }

        val now = System.currentTimeMillis()
        val billRemoteId = "${current.remoteId}_final_bill_${UUID.randomUUID()}"
        val supplierSnapshot = resolveBillSupplierSnapshot(current.propertyRemoteId)
        val billNumber = try {
            nextBillNumber(supplierSnapshot.invoicePrefix, now)
        } catch (error: Exception) {
            return SaveResult.Error("Could not reserve invoice number. Please check internet and try again.")
        }
        val roomsIncluded = roomDao.getRooms(hotelRemoteId)
            .filter { current.roomRemoteIds.contains(it.remoteId) }
            .joinToString(", ") { it.roomName }
            .ifBlank { "Room" }

        val foodItems = linkedOrders.flatMap { order ->
            foodOrderItemDao.getItemsForOrder(hotelRemoteId, order.remoteId)
                .filter { !it.isDeleted && !it.isCancelled && it.quantity > 0.0 }
                .map { order to it }
        }
        val menuItemsById = foodMenuItemDao.getItems(hotelRemoteId).associateBy { it.remoteId }
        val gstCategoriesById = foodGstCategoryDao.getCategories(hotelRemoteId).associateBy { it.remoteId }
        val defaultFoodGstCategory = foodGstCategoryDao.getDefaultCategory(hotelRemoteId)

        val billItems = mutableListOf<FoodBillItemEntity>()
        var grossSubtotalBeforeDiscount = 0.0
        if (existingFinalBill == null) {
            val roomNamesById = roomDao.getRooms(hotelRemoteId)
                .associate { it.remoteId to it.roomName }

            val stayItems = StayBillItemBuilder.build(
                billRemoteId = billRemoteId,
                hotelRemoteId = hotelRemoteId,
                booking = current,
                roomsIncluded = roomsIncluded,
                stayTotal = summary.stayTotal,
                financialLines = stayFinancialLines,
                roomNamesById = roomNamesById,
                now = now
            )
            grossSubtotalBeforeDiscount += stayItems.sumOf { it.lineTotal.takeIf { total -> total > 0.0 } ?: it.lineSubtotal }
            billItems += applyGrossDiscountToBillItems(stayItems, roomDiscount)
        }

        val foodBillItems = mutableListOf<FoodBillItemEntity>()

        foodItems.forEach { (order, item) ->
            val grossLineTotal = item.lineTotal.takeIf { it > 0.0 }
                ?: item.lineSubtotal.takeIf { it > 0.0 }
                ?: item.quantity * item.unitPrice

            val gstRate = item.gstRatePercent.coerceAtLeast(0.0)
            val cgstRate = item.cgstRatePercent.takeIf { it > 0.0 }
            val sgstRate = item.sgstRatePercent.takeIf { it > 0.0 }
            val cessRate = item.cessRatePercent.coerceAtLeast(0.0)

            val gstBreakdown = foodGstCalculator.calculateInclusive(
                grossAmount = grossLineTotal,
                gstRatePercent = gstRate,
                cgstRatePercent = cgstRate,
                sgstRatePercent = sgstRate,
                cessRatePercent = cessRate,
                withGst = gstRate > 0.0 || cessRate > 0.0
            )

            foodBillItems += FoodBillItemEntity(
                remoteId = "${billRemoteId}_food_${UUID.randomUUID()}",
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
                lineSubtotal = grossLineTotal,
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
        }
        grossSubtotalBeforeDiscount += foodBillItems.sumOf { it.lineTotal.takeIf { total -> total > 0.0 } ?: it.lineSubtotal }
        billItems += applyGrossDiscountToBillItems(foodBillItems, foodDiscount)

        val serviceBillItems = unbilledServiceCharges
            .map { charge ->
                val gstRate = serviceGstRate(charge)
                val gstBreakdown = if (charge.taxInclusive) {
                    foodGstCalculator.calculateInclusive(
                        grossAmount = charge.amount,
                        gstRatePercent = gstRate,
                        withGst = gstRate > 0.0
                    )
                } else {
                    foodGstCalculator.calculateExclusive(
                        taxableAmount = charge.taxableAmount ?: charge.amount,
                        gstRatePercent = gstRate,
                        withGst = gstRate > 0.0
                    )
                }
                FoodBillItemEntity(
                    remoteId = "${billRemoteId}_service_${charge.remoteId}",
                    hotelRemoteId = hotelRemoteId,
                    billRemoteId = billRemoteId,
                    orderRemoteId = charge.remoteId,
                    orderNumber = "SERVICE",
                    orderMillis = charge.chargeMillis,
                    roomName = roomsIncluded,
                    itemName = charge.description,
                    quantity = 1.0,
                    unitPrice = charge.amount,
                    lineSubtotal = charge.amount,
                    gstCategoryName = "Service",
                    hsnSacCode = serviceSacCode(charge),
                    gstRatePercent = gstBreakdown.gstRatePercent,
                    cgstRatePercent = gstBreakdown.cgstRatePercent,
                    sgstRatePercent = gstBreakdown.sgstRatePercent,
                    taxableAmount = gstBreakdown.taxableAmount,
                    cgstAmount = gstBreakdown.cgstAmount,
                    sgstAmount = gstBreakdown.sgstAmount,
                    gstAmount = gstBreakdown.gstAmount,
                    lineTotal = gstBreakdown.lineTotal,
                    updatedAt = now,
                    syncState = SyncState.PENDING
                )
            }
        grossSubtotalBeforeDiscount += serviceBillItems.sumOf { it.lineTotal.takeIf { total -> total > 0.0 } ?: it.lineSubtotal }
        billItems += applyGrossDiscountToBillItems(serviceBillItems, serviceDiscount)

        val damageBillItems = unbilledDamageCharges
            .map { charge ->
                FoodBillItemEntity(
                    remoteId = "${billRemoteId}_damage_${charge.remoteId}",
                    hotelRemoteId = hotelRemoteId,
                    billRemoteId = billRemoteId,
                    orderRemoteId = charge.remoteId,
                    orderNumber = "DAMAGE",
                    orderMillis = charge.chargeMillis,
                    roomName = roomsIncluded,
                    itemName = charge.description,
                    quantity = 1.0,
                    unitPrice = charge.amount,
                    lineSubtotal = charge.amount,
                    gstCategoryName = "Damage recovery",
                    taxableAmount = charge.amount,
                    lineTotal = charge.amount,
                    updatedAt = now,
                    syncState = SyncState.PENDING
                )
            }
        grossSubtotalBeforeDiscount += damageBillItems.sumOf { it.lineTotal.takeIf { total -> total > 0.0 } ?: it.lineSubtotal }
        billItems += damageBillItems

        val billTotals = FoodBillTotalsCalculator.calculate(billItems)
        val displaySubtotal = grossSubtotalBeforeDiscount.takeIf { it > 0.0 } ?: billTotals.subtotal
        val serviceTotal = accountingCharges
            .filter {
                !it.isDeleted &&
                        BookingAccountingChargeType.normalize(it.chargeType) == BookingAccountingChargeType.SERVICE_CHARGE
            }
            .sumOf { it.amount.coerceAtLeast(0.0) }
        val damageTotal = accountingCharges
            .filter {
                !it.isDeleted &&
                        BookingAccountingChargeType.normalize(it.chargeType) == BookingAccountingChargeType.DAMAGE_CHARGE
            }
            .sumOf { it.amount.coerceAtLeast(0.0) }
        val billNote = if (current.sourceType == BookingSourceType.OTA) {
            val otaReceivable = (current.expectedPayout.takeIf { it > 0.0 } ?: summary.stayTotal) -
                    summary.stayPaid
            val stayText = if (existingFinalBill == null) "Stay ${summary.stayTotal}; " else "Additional folio bill; "
            "Consolidated booking bill. $stayText Food ${linkedOrders.sumOf { it.totalAmount.takeIf { amount -> amount > 0.0 } ?: it.subtotal }}; Service $serviceTotal; Damage $damageTotal; Discount $totalDiscount; " +
                    "Guest payable balance 0; OTA receivable ${otaReceivable.coerceAtLeast(0.0)} tracked separately."
        } else {
            val stayText = if (existingFinalBill == null) "Stay ${summary.stayTotal}; " else "Additional folio bill; "
            "Consolidated booking bill. $stayText Food ${linkedOrders.sumOf { it.totalAmount.takeIf { amount -> amount > 0.0 } ?: it.subtotal }}; Service $serviceTotal; Damage $damageTotal; Discount $totalDiscount; Paid ${summary.totalPaid}; Balance 0."
        }
        val bill = FoodBillEntity(
            remoteId = billRemoteId,
            hotelRemoteId = hotelRemoteId,
            propertyRemoteId = current.propertyRemoteId,
            supplierName = supplierSnapshot.supplierName,
            supplierGstin = supplierSnapshot.supplierGstin,
            supplierAddress = supplierSnapshot.supplierAddress,
            supplierPhone = supplierSnapshot.supplierPhone,
            supplierState = supplierSnapshot.supplierState,
            propertyDisplayName = supplierSnapshot.propertyDisplayName,
            billNumber = billNumber,
            billMillis = now,
            guestName = current.guestName,
            guestMobile = current.guestMobile,
            roomsIncluded = roomsIncluded,
            orderRemoteIds = linkedOrders.joinToString(",") { it.remoteId },
            subtotal = displaySubtotal,
            discountAmount = totalDiscount,
            taxableAmount = billTotals.taxableAmount,
            cgstAmount = billTotals.cgstAmount,
            sgstAmount = billTotals.sgstAmount,
            cessAmount = billTotals.cessAmount,
            gstAmount = billTotals.gstAmount,
            grandTotal = billTotals.grandTotal,
            paymentMode = "Settled in folio",
            notes = billNote,
            status = FoodBillStatus.ISSUED,
            updatedAt = now,
            syncState = SyncState.PENDING
        )

        val result = db.withTransaction {
            val finalBillCreatedByAnotherFlow = foodBillDao.getFinalBillForBooking(
                hotelRemoteId = hotelRemoteId,
                remoteIdPrefix = "${current.remoteId}_final_bill_"
            )
            if (finalBillCreatedByAnotherFlow != null && existingFinalBill == null) {
                return@withTransaction SaveResult.Success(
                    syncPending = finalBillCreatedByAnotherFlow.syncState != SyncState.SYNCED
                )
            }

            foodBillDao.upsert(bill)
            billItems.forEach { foodBillItemDao.upsert(it) }

            linkedOrders.forEach { order ->
                val archived = order.copy(
                    billRemoteId = billRemoteId,
                    linkedFinalBillId = billRemoteId,
                    archivedAt = now,
                    status = FoodOrderStatus.BILLED_IN_FOLIO,
                    updatedAt = now,
                    syncState = SyncState.PENDING,
                    lastSyncError = null,
                    baseRevision = order.baseRevision.takeIf { it > 0 } ?: order.revision
                )
                foodOrderDao.upsert(archived)
            }

            FinalBillChargeSelectionPolicy.chargesToArchiveAfterFinalBill(accountingCharges)
                .forEach { charge ->
                    bookingAccountingChargeDao.upsert(
                        charge.copy(
                            linkedFinalBillId = billRemoteId,
                            archivedAt = now,
                            updatedAt = now,
                            syncState = SyncState.PENDING,
                            lastSyncError = null,
                            baseRevision = charge.baseRevision.takeIf { it > 0 } ?: charge.revision
                        )
                    )
                }

            SaveResult.Success(syncPending = true)
        }

        if (result.syncPending) enqueueBackgroundSync()
        return result
    }

    suspend fun isRoomRateLocked(bookingRemoteId: String): Boolean {
        if (bookingRemoteId.isBlank()) return false
        return foodBillDao.getFinalBillForBooking(
            hotelRemoteId = hotelRemoteId,
            remoteIdPrefix = "${bookingRemoteId}_final_bill_"
        ) != null
    }

    private fun serviceGstRate(charge: BookingAccountingChargeEntity): Double {
        charge.gstRatePercent.takeIf { it > 0.0 }?.let { return it }
        val note = charge.reason.orEmpty()
        return Regex("GST\\s+([0-9]+(?:\\.[0-9]+)?)%", RegexOption.IGNORE_CASE)
            .find(note)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?.coerceAtLeast(0.0)
            ?: 0.0
    }

    private fun serviceSacCode(charge: BookingAccountingChargeEntity): String? {
        charge.hsnSacCode?.takeIf { it.isNotBlank() }?.let { return it }
        val note = charge.reason.orEmpty()
        return Regex("SAC\\s+([A-Za-z0-9-]+)", RegexOption.IGNORE_CASE)
            .find(note)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun List<BookingAccountingChargeEntity>.discountFor(bucket: String): Double {
        return filter {
            !it.isDeleted &&
                    BookingAccountingChargeType.normalize(it.chargeType) == BookingAccountingChargeType.DISCOUNT &&
                    BookingPaymentCategory.normalize(it.accountBucket) == bucket
        }.sumOf { it.amount.coerceAtLeast(0.0) }
    }

    private fun applyGrossDiscountToBillItems(
        items: List<FoodBillItemEntity>,
        discountAmount: Double
    ): List<FoodBillItemEntity> {
        val grossTotal = items.sumOf { it.lineTotal.takeIf { total -> total > 0.0 } ?: it.lineSubtotal }

        val discountRatio = if (grossTotal > 0.0) {
            discountAmount.coerceIn(0.0, grossTotal) / grossTotal
        } else {
            0.0
        }

        if (discountRatio <= 0.0) return items

        return items.map { item ->
            val originalGross = item.lineTotal.takeIf { it > 0.0 } ?: item.lineSubtotal
            val gstBreakdown = foodGstCalculator.calculateDiscountedInclusive(
                grossAmount = originalGross,
                discountRatio = discountRatio,
                gstRatePercent = item.gstRatePercent,
                cgstRatePercent = item.cgstRatePercent,
                sgstRatePercent = item.sgstRatePercent,
                cessRatePercent = item.cessRatePercent,
                withGst = item.gstRatePercent > 0.0 || item.cessRatePercent > 0.0
            )

            val quantity = item.quantity.coerceAtLeast(1.0)

            item.copy(
                unitPrice = roundMoney(gstBreakdown.lineTotal / quantity),
                lineSubtotal = gstBreakdown.grossAmount,
                taxableAmount = gstBreakdown.taxableAmount,
                cgstRatePercent = gstBreakdown.cgstRatePercent,
                sgstRatePercent = gstBreakdown.sgstRatePercent,
                cessRatePercent = gstBreakdown.cessRatePercent,
                cgstAmount = gstBreakdown.cgstAmount,
                sgstAmount = gstBreakdown.sgstAmount,
                cessAmount = gstBreakdown.cessAmount,
                gstAmount = gstBreakdown.totalTaxAmount,
                lineTotal = gstBreakdown.lineTotal
            )
        }
    }

    private fun roundMoney(amount: Double): Double = round(amount * 100.0) / 100.0

    suspend fun cancelBooking(booking: BookingEntity, request: CancellationRequest): SaveResult {
        var localCommitSucceeded = false
        return try {
            val cleanReason = request.reason.trim()
            if (cleanReason.isBlank()) return SaveResult.Error("Cancellation reason is required.")
            val finalBill = foodBillDao.getFinalBillForBooking(
                hotelRemoteId = hotelRemoteId,
                remoteIdPrefix = "${booking.remoteId}_final_bill_"
            )
            if (finalBill != null && !finalBill.isDeleted) {
                return SaveResult.Error("A billed booking cannot be cancelled; issue corrections/refunds instead.")
            }
            val localResult = db.withTransaction {
                val current = bookingDao.getByRemoteId(booking.remoteId)
                    ?: return@withTransaction SaveResult.Error("Booking not found")
                if (current.bookingStatus == BookingStatus.CANCELLED) {
                    return@withTransaction SaveResult.Success(
                        syncPending = current.syncState != SyncState.SYNCED
                    )
                }
                val payments = bookingPaymentDao.getPaymentsForBooking(hotelRemoteId, current.remoteId)
                val decision = CancellationSettlementPolicy.decide(
                    sourceType = current.sourceType,
                    payments = payments,
                    choice = request.directChoice,
                    partialRefundAmount = request.partialRefundAmount
                ).getOrElse { error ->
                    return@withTransaction SaveResult.Error(
                        error.message ?: "Invalid cancellation settlement."
                    )
                }
                val now = System.currentTimeMillis()
                val cancelled = current.copy(
                    bookingStatus = BookingStatus.CANCELLED,
                    cancelledAt = now,
                    cancellationReason = cleanReason,
                    cancellationSettlementStatus = decision.status,
                    cancellationSettlementOutcome = decision.outcome,
                    cancellationApprovedRefundAmount = decision.approvedRefundAmount,
                    cancellationFeeAmount = decision.cancellationFeeAmount,
                    cancellationRefundBaselineAmount = decision.refundBaselineAmount,
                    cancellationDecisionAt = now.takeIf {
                        decision.status == CancellationSettlementStatus.DECIDED ||
                            decision.status == CancellationSettlementStatus.NOT_REQUIRED
                    },
                    cancellationDecisionByUid = null,
                    isDeleted = false,
                    updatedAt = now,
                    syncState = SyncState.PENDING,
                    lastSyncError = null,
                    baseRevision = current.baseRevision.takeIf { it > 0 } ?: current.revision
                ).withCalculatedPayment()
                bookingDao.upsert(cancelled)
                val lines = bookingFinancialLineDao.getAllLinesForBooking(hotelRemoteId, current.remoteId)
                enqueueBookingOutbox(current, cancelled, lines)
                SaveResult.Success(syncPending = true)
            }
            if (localResult !is SaveResult.Success) return localResult
            localCommitSucceeded = true
            enqueueBackgroundSync()
            localResult
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("BookingRepository", "Could not cancel booking ${booking.remoteId}", error)
            if (localCommitSucceeded) {
                SaveResult.Success(syncPending = true)
            } else {
                SaveResult.Error("Could not cancel booking. No changes were made.")
            }
        }
    }

    suspend fun decideDirectCancellationSettlement(
        booking: BookingEntity,
        choice: DirectCancellationChoice,
        partialRefundAmount: Double? = null
    ): SaveResult {
        if (choice == DirectCancellationChoice.DECIDE_LATER) {
            return SaveResult.Error("Select a final cancellation outcome.")
        }
        return try {
            val result = db.withTransaction {
                val current = bookingDao.getByRemoteId(booking.remoteId)
                    ?: return@withTransaction SaveResult.Error("Booking not found")
                if (current.bookingStatus != BookingStatus.CANCELLED) {
                    return@withTransaction SaveResult.Error("Only a cancelled booking can be settled.")
                }
                if (current.sourceType == BookingSourceType.OTA) {
                    return@withTransaction SaveResult.Error("OTA cancellation settlement is not enabled in this step.")
                }
                if (current.cancellationSettlementStatus != CancellationSettlementStatus.PENDING) {
                    return@withTransaction SaveResult.Error("This cancellation decision is already recorded.")
                }
                val payments = bookingPaymentDao.getPaymentsForBooking(hotelRemoteId, current.remoteId)
                val decision = CancellationSettlementPolicy.decide(
                    sourceType = current.sourceType,
                    payments = payments,
                    choice = choice,
                    partialRefundAmount = partialRefundAmount
                ).getOrElse { error ->
                    return@withTransaction SaveResult.Error(
                        error.message ?: "Invalid cancellation settlement."
                    )
                }
                val now = System.currentTimeMillis()
                val decided = current.copy(
                    cancellationSettlementStatus = decision.status,
                    cancellationSettlementOutcome = decision.outcome,
                    cancellationApprovedRefundAmount = decision.approvedRefundAmount,
                    cancellationFeeAmount = decision.cancellationFeeAmount,
                    cancellationRefundBaselineAmount = decision.refundBaselineAmount,
                    cancellationDecisionAt = now,
                    cancellationDecisionByUid = null,
                    updatedAt = now,
                    syncState = SyncState.PENDING,
                    lastSyncError = null,
                    baseRevision = current.baseRevision.takeIf { it > 0 } ?: current.revision
                )
                bookingDao.upsert(decided)
                val lines = bookingFinancialLineDao.getAllLinesForBooking(hotelRemoteId, current.remoteId)
                enqueueBookingOutbox(current, decided, lines)
                SaveResult.Success(syncPending = true)
            }
            if (result is SaveResult.Success) enqueueBackgroundSync()
            result
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("BookingRepository", "Could not decide cancellation settlement ${booking.remoteId}", error)
            SaveResult.Error("Could not save the cancellation decision. No changes were made.")
        }
    }

    suspend fun ensureDefaultHotelExists() {
        if (hotelDao.getByRemoteId(hotelRemoteId) != null) return
        val hotel = HotelEntity(
            remoteId = hotelRemoteId,
            hotelName = "Booking Register",
            updatedAt = System.currentTimeMillis(),
            syncState = SyncState.SYNCED
        )
        hotelDao.upsert(hotel)
    }

    suspend fun ensureDefaultCategoryExists() {
        // Category is optional. Do not create a fake default category for small properties.
    }
    suspend fun ensureDefaultSourceExists() {
        if (bookingSourceDao.getSources(hotelRemoteId).isNotEmpty()) return
        val source = BookingSourceEntity(
            remoteId = stableSourceRemoteId("Walk-in"),
            hotelRemoteId = hotelRemoteId,
            propertyRemoteId = null,
            sourceName = "Walk-in",
            sourceType = BookingSourceType.DIRECT,
            syncState = SyncState.PENDING
        )
        bookingSourceDao.upsert(source)
        pushSourceAndMark(source)
    }

    fun saveSource(
        existing: BookingSourceEntity?,
        sourceName: String,
        sourceType: String,
        commissionPercent: Double,
        commissionGstPercent: Double,
        tcsPercent: Double,
        tdsPercent: Double,
        fixedFee: Double,
        propertyRemoteId: String? = null,
        isActive: Boolean = true
    ) {
        scope.launch {
            val cleanName = sourceName.trim()
            if (cleanName.isEmpty()) return@launch
            val cleanPropertyRemoteId = propertyRemoteId?.takeIf { it.isNotBlank() }
            val byName = bookingSourceDao.getByName(hotelRemoteId, cleanName, cleanPropertyRemoteId)
            val base = existing ?: byName
            val normalizedType = when (sourceType) {
                BookingSourceType.OTA -> BookingSourceType.OTA
                BookingSourceType.AGENT -> BookingSourceType.AGENT
                else -> BookingSourceType.DIRECT
            }
            val source = BookingSourceEntity(
                localId = base?.localId ?: 0,
                remoteId = base?.remoteId ?: stableSourceRemoteId(cleanName, cleanPropertyRemoteId),
                hotelRemoteId = hotelRemoteId,
                propertyRemoteId = cleanPropertyRemoteId,
                sourceName = cleanName,
                sourceType = normalizedType,
                commissionPercent = commissionPercent.coerceAtLeast(0.0),
                commissionGstPercent = commissionGstPercent.coerceAtLeast(0.0),
                tcsPercent = tcsPercent.coerceAtLeast(0.0),
                tdsPercent = tdsPercent.coerceAtLeast(0.0),
                fixedFee = fixedFee.coerceAtLeast(0.0),
                isActive = isActive,
                updatedAt = System.currentTimeMillis(),
                isDeleted = false,
                syncState = SyncState.PENDING,
                lastSyncError = null,
                lastSyncedAt = base?.lastSyncedAt,
                revision = base?.revision ?: 0,
                baseRevision = base?.baseRevision?.takeIf { it > 0 } ?: base?.revision ?: 0,
                updatedByUid = base?.updatedByUid
            )
            bookingSourceDao.upsert(source)
                    pushSourceAndMark(source)
        }
    }

    fun deleteSource(source: BookingSourceEntity) {
        scope.launch {
            val deleted = source.copy(
                isDeleted = true,
                isActive = false,
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING
            )
            bookingSourceDao.upsert(deleted)
            pushSourceAndMark(deleted)
        }
    }

    private suspend fun repairOtaBookingStatuses() {
        val repairedBookings = bookingDao.getBookings(hotelRemoteId)
            .filter { booking ->
                !booking.isDeleted &&
                        booking.sourceType == BookingSourceType.OTA &&
                        booking.paymentStatus != PaymentStatus.FULLY_PAID
            }
            .map { booking ->
                DerivedBookingCachePolicy.preserveSyncIdentity(
                    original = booking,
                    recalculated = booking.withCalculatedPayment()
                )
            }

        if (repairedBookings.isEmpty()) return

        bookingDao.upsertAll(repairedBookings)
    }
    suspend fun retryFailedSync(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRetryAttemptAt < RETRY_THROTTLE_MILLIS) return
        if (retryInProgress) return

        retryInProgress = true
        lastRetryAttemptAt = now

        try {
            hotelDao.getUnsyncedHotels().forEach { pushHotelAndMark(it) }
            managedPropertyDao.getUnsyncedProperties(hotelRemoteId).forEach { pushManagedPropertyAndMark(it) }
            bookingSourceDao.getUnsyncedSources(hotelRemoteId).forEach { pushSourceAndMark(it) }
            roomDao.getUnsyncedRooms(hotelRemoteId).forEach { pushRoomAndMark(it) }
            bookingPaymentDao.getUnsyncedPayments(hotelRemoteId).forEach { pushPaymentAndMark(it) }
            bookingAccountingChargeDao.getUnsyncedCharges(hotelRemoteId).forEach { charge ->
                val linkedBillId = charge.linkedFinalBillId?.takeIf { it.isNotBlank() }
                if (linkedBillId != null && foodBillDao.getByRemoteId(linkedBillId) != null) {
                    return@forEach
                }
                pushAccountingChargeAndMark(charge)
            }

            val executableBookingIds = bookingSyncOutboxDao.getPending(hotelRemoteId)
                .mapTo(mutableSetOf()) { it.bookingRemoteId }
            bookingDao.getUnsyncedBookings(hotelRemoteId)
                .filter { it.syncState == SyncState.PENDING && it.remoteId !in executableBookingIds }
                .forEach { orphaned ->
                    bookingDao.upsert(
                        orphaned.markFailed(
                            StructuredSyncException(
                                SyncFailureCode.ORPHANED_BOOKING_INTENT,
                                "Booking sync intent is missing. Local data was preserved and was not uploaded; review it before retrying."
                            )
                        )
                    )
                }

            val aggregateOperations = bookingSyncOutboxDao.getPending(hotelRemoteId)

            aggregateOperations.forEach { pushBookingChangeSetAndMark(it) }

            clearRealtimeSyncErrorIfClean()
        } finally {
            retryInProgress = false
        }
    }

    private fun syncBoundary(localCount: Int, maxUpdatedAt: Long?): Long? {
        return if (localCount <= 0) null else maxUpdatedAt?.coerceAtLeast(0L)
    }

    private suspend fun seedInitialPaymentIfNeeded(booking: BookingEntity) {
        if (booking.paid <= 0.0) return
        if (bookingPaymentDao.countPaymentsForBooking(hotelRemoteId, booking.remoteId) > 0) return
        InitialPaymentFactory.create(booking)?.let { bookingPaymentDao.upsert(it) }
    }

    private suspend fun recalculateBookingPaymentAggregate(booking: BookingEntity): SaveResult {
        db.withTransaction {
            recalculateBookingPaymentAggregateInTransaction(booking)
        }
        return SaveResult.Success(syncPending = booking.syncState != SyncState.SYNCED)
    }

    private suspend fun recalculateBookingPaymentAggregateInTransaction(booking: BookingEntity) {
        val payments = bookingPaymentDao.getPaymentsForBooking(hotelRemoteId, booking.remoteId)
        val foodOrders = foodOrderDao.getOrdersForBooking(hotelRemoteId, booking.remoteId)
        val accountingCharges = bookingAccountingChargeDao.getChargesForBooking(hotelRemoteId, booking.remoteId)
        val bookingFinancialLines = bookingFinancialLineDao.getLinesForBooking(hotelRemoteId, booking.remoteId)
        val summary = FolioSummaryBuilder.build(
            booking = booking,
            payments = payments,
            foodOrders = foodOrders,
            foodOrderItems = foodItemsForOrders(foodOrders),
            accountingCharges = accountingCharges,
            bookingFinancialLines = bookingFinancialLines
        )
        val updated = DerivedBookingCachePolicy.preserveSyncIdentity(
            original = booking,
            recalculated = booking.copy(
                paid = summary.stayPaid.coerceAtLeast(0.0)
            ).withCalculatedPayment()
        )
        bookingDao.upsert(updated)
    }

    private fun paymentTotal(payments: List<BookingPaymentEntity>): Double {
        return payments.filter { !it.isDeleted }.sumOf { payment ->
            when (payment.paymentType) {
                BookingPaymentType.REFUND -> -payment.amount
                BookingPaymentType.ADJUSTMENT -> -payment.amount
                else -> payment.amount
            }
        }.coerceAtLeast(0.0)
    }

    private suspend fun applyRoomNightFinancialCache(
        booking: BookingEntity,
        financialLines: List<BookingFinancialLineEntity>
    ): BookingEntity {
        val activeLines = financialLines
            .filter { !it.isDeleted && it.bookingRemoteId == booking.remoteId }
        if (activeLines.isEmpty() || BookingPricingStatus.isPending(booking.pricingStatus)) {
            return booking.withCalculatedPayment()
        }

        val grossCharges = roundMoney(activeLines.sumOf { it.grossAmount.coerceAtLeast(0.0) })
        val roomRevenue = roundMoney(activeLines.sumOf { it.taxableAmount.coerceAtLeast(0.0) })
        val propertyTax = roundMoney(activeLines.sumOf { it.gstAmount.coerceAtLeast(0.0) })
        val source = booking.sourceRemoteId
            ?.takeIf { it.isNotBlank() }
            ?.let { bookingSourceDao.getByRemoteId(it) }
            ?: booking.sourceName
                ?.takeIf { it.isNotBlank() }
                ?.let { bookingSourceDao.getByName(hotelRemoteId, it, booking.propertyRemoteId) }

        val settlement = sourceSettlementCalculator.calculate(
            source = source,
            roomCharges = roomRevenue,
            propertyTax = propertyTax,
            hotelHasGst = propertyTax > 0.0
        )
        val sourceHasDeductions = source?.hasDeductions() == true
        val receivable = if (booking.sourceType == BookingSourceType.OTA || sourceHasDeductions) {
            settlement.expectedPayout
        } else {
            grossCharges
        }

        return booking.copy(
            rate = receivable,
            receivable = receivable,
            grossCharges = grossCharges,
            roomRevenue = roomRevenue,
            propertyTax = propertyTax,
            commissionAmount = settlement.commission,
            commissionTax = settlement.commissionTax,
            sourceFee = settlement.fixedFee,
            tdsAmount = settlement.tds,
            tcsAmount = settlement.tcs,
            expectedPayout = if (booking.sourceType == BookingSourceType.OTA) {
                settlement.expectedPayout
            } else {
                booking.expectedPayout
            }
        ).withCalculatedPayment()
    }

    private fun BookingSourceEntity.hasDeductions(): Boolean {
        return commissionPercent > 0.0 ||
                commissionGstPercent > 0.0 ||
                tcsPercent > 0.0 ||
                tdsPercent > 0.0 ||
                fixedFee > 0.0
    }

    private suspend fun foodItemsForOrders(orders: List<com.example.bookingregister.data.entities.FoodOrderEntity>) =
        orders.flatMap { order -> foodOrderItemDao.getItemsForOrder(hotelRemoteId, order.remoteId) }
    private suspend fun pushHotelAndMark(hotel: HotelEntity) {
        runCatching { cloudSyncManager.pushHotel(hotel) }
            .onSuccess { result ->
                hotelDao.upsert(hotel.markSynced(result))
            }
            .onFailure {
                hotelDao.upsert(hotel.markFailed(it))
                logSyncFailure("pushHotel", it)
            }
    }

    private suspend fun pushRoomAndMark(room: RoomEntity) {
        runCatching { cloudSyncManager.pushRoom(room) }
            .onSuccess { result ->
                roomDao.upsert(room.markSynced(result))
            }
            .onFailure {
                roomDao.upsert(room.markFailed(it))
                logSyncFailure("pushRoom", it)
            }
    }


    private suspend fun pushSourceAndMark(source: BookingSourceEntity) {
        runCatching { cloudSyncManager.pushSource(source) }
            .onSuccess { result ->
                bookingSourceDao.upsert(source.markSynced(result))
            }
            .onFailure {
                bookingSourceDao.upsert(source.markFailed(it))
                logSyncFailure("pushSource", it)
            }
    }

    private suspend fun pushManagedPropertyAndMark(property: ManagedPropertyEntity) {
        runCatching { cloudSyncManager.pushManagedProperty(property) }
            .onSuccess { result ->
                managedPropertyDao.upsert(property.markSynced(result))
            }
            .onFailure {
                managedPropertyDao.upsert(property.markFailed(it))
                logSyncFailure("pushManagedProperty", it)
            }
    }

    private suspend fun pushPaymentAndMark(payment: BookingPaymentEntity) {
        runCatching { cloudSyncManager.pushPayment(payment) }
            .onSuccess { result ->
                bookingPaymentDao.getByRemoteId(payment.remoteId)?.let { current ->
                    val unchanged = SyncAcknowledgementPolicy.isSameVersion(
                        payment.updatedAt, payment.revision, payment.baseRevision,
                        current.updatedAt, current.revision, current.baseRevision
                    )
                    bookingPaymentDao.upsert(
                        if (unchanged) {
                            current.markSynced(result)
                        } else {
                            current.copy(
                                revision = result.revision,
                                baseRevision = result.revision,
                                syncState = SyncState.PENDING,
                                lastSyncError = null
                            )
                        }
                    )
                }
            }
            .onFailure {
                bookingPaymentDao.upsert(payment.markFailed(it))
                logSyncFailure("pushPayment", it)
            }
    }

    private suspend fun pushBookingChangeSetAndMark(operation: BookingSyncOutboxEntity) {
        val booking = bookingDao.getByRemoteId(operation.bookingRemoteId) ?: run {
            bookingSyncOutboxDao.delete(operation.operationId)
            return
        }

        val lines = bookingFinancialLineDao.getAllLinesForBooking(hotelRemoteId, booking.remoteId)
        val sentBooking = booking
        val isLegacySnapshotOperation = operation.changeSetJson.isBlank()
        val changeSet = if (isLegacySnapshotOperation) {
            // Database versions before 37 stored only the booking operation identity. Rebuild
            // the complete requested state without deleting the legacy operation or booking.
            BookingChangeSet.create(
                previous = null,
                requested = booking,
                previousLines = emptyList(),
                requestedLines = lines
            )
        } else {
            BookingChangeSet.fromJson(operation.changeSetJson)
        }
        val deviceId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: "unknown-android-device"

        runCatching {
            cloudSyncManager.pushBookingChangeSet(
                operationId = operation.operationId,
                deviceId = deviceId,
                changeSet = changeSet
            )
        }
            .recoverCatching { error ->
                val failureCode = (error as? CodedSyncFailure)?.syncFailureCode
                if (isLegacySnapshotOperation && failureCode == SyncFailureCode.ALREADY_EXISTS) {
                    // The legacy operation may represent either the booking's first upload or a
                    // later edit. If the booking already exists, apply the same full state as an
                    // update. The unchanged operationId keeps the retry idempotent.
                    cloudSyncManager.pushBookingChangeSet(
                        operationId = operation.operationId,
                        deviceId = deviceId,
                        changeSet = changeSet.copy(create = false)
                    )
                } else {
                    throw error
                }
            }
            .onSuccess { result ->
                acknowledgeBookingAggregate(operation, sentBooking, lines, result)
            }
            .onFailure { error ->
                val message = syncFailureText(error)

                bookingSyncOutboxDao.markFailed(
                    operation.operationId,
                    message
                )

                bookingDao.getByRemoteId(operation.bookingRemoteId)?.let { currentBooking ->
                    bookingDao.upsert(currentBooking.markFailed(error))
                }

                logSyncFailure("pushBookingChangeSet", error)
            }
    }

    private suspend fun acknowledgeBookingAggregate(
        operation: BookingSyncOutboxEntity,
        sentBooking: BookingEntity,
        sentLines: List<BookingFinancialLineEntity>,
        result: BookingAggregateWriteResult
    ) {
        db.withTransaction {
            val currentBooking = bookingDao.getByRemoteId(sentBooking.remoteId)
            if (currentBooking != null) {
                bookingDao.upsert(
                    currentBooking.markSynced(
                        CloudWriteResult(
                            revision = maxOf(currentBooking.revision, result.bookingRevision),
                            updatedByUid = result.updatedByUid
                        )
                    )
                )
            }
            sentLines.forEach { sentLine ->
                val revision = result.financialLineRevisions[sentLine.remoteId] ?: return@forEach
                val currentLine = bookingFinancialLineDao.getByRemoteId(sentLine.remoteId) ?: return@forEach
                val unchanged = SyncAcknowledgementPolicy.isSameVersion(
                    sentLine.updatedAt, sentLine.revision, sentLine.baseRevision,
                    currentLine.updatedAt, currentLine.revision, currentLine.baseRevision
                )
                bookingFinancialLineDao.upsert(
                    if (unchanged) {
                        currentLine.markSynced(CloudWriteResult(revision, result.updatedByUid))
                    } else {
                        currentLine.copy(
                            revision = revision,
                            baseRevision = revision,
                            syncState = SyncState.PENDING,
                            lastSyncError = null
                        )
                    }
                )
            }
            bookingSyncOutboxDao.delete(operation.operationId)
        }
    }

    private suspend fun pushAccountingChargeAndMark(charge: BookingAccountingChargeEntity) {
        runCatching { cloudSyncManager.pushAccountingCharge(charge) }
            .onSuccess { result ->
                bookingAccountingChargeDao.getByRemoteId(charge.remoteId)?.let { current ->
                    val unchanged = SyncAcknowledgementPolicy.isSameVersion(
                        charge.updatedAt, charge.revision, charge.baseRevision,
                        current.updatedAt, current.revision, current.baseRevision
                    )
                    bookingAccountingChargeDao.upsert(
                        if (unchanged) {
                            current.markSynced(result)
                        } else {
                            current.copy(
                                revision = result.revision,
                                baseRevision = result.revision,
                                syncState = SyncState.PENDING,
                                lastSyncError = null
                            )
                        }
                    )
                }
            }
            .onFailure {
                bookingAccountingChargeDao.upsert(charge.markFailed(it))
                logSyncFailure("pushAccountingCharge", it)
            }
    }



    private suspend fun upsertRemoteHotelIfNewer(remote: HotelEntity) {
        val local = hotelDao.getByRemoteId(remote.remoteId)
        if (local == null || shouldAcceptRemote(local, remote.revision, remote.updatedAt)) {
            hotelDao.upsert(remote.markSynced())
        }
    }

    private suspend fun upsertRemoteRoomIfNewer(remote: RoomEntity) {
        val local = roomDao.getByRemoteId(remote.remoteId)
        if (local == null || shouldAcceptRemote(local, remote.revision, remote.updatedAt)) {
            roomDao.upsert(remote.copy(localId = local?.localId ?: 0).markSynced())
        }
    }

    private suspend fun upsertRemoteManagedPropertyIfNewer(remote: ManagedPropertyEntity) {
        val local = managedPropertyDao.getByRemoteId(remote.remoteId)
        if (local == null || shouldAcceptRemote(local, remote.revision, remote.updatedAt)) {
            managedPropertyDao.upsert(remote.copy(localId = local?.localId ?: 0).markSynced())
        }
    }

    private suspend fun upsertRemoteSourceIfNewer(remote: BookingSourceEntity) {
        val local = bookingSourceDao.getByRemoteId(remote.remoteId)
        if (local == null || shouldAcceptRemote(local, remote.revision, remote.updatedAt)) {
            bookingSourceDao.upsert(remote.copy(localId = local?.localId ?: 0).markSynced())
        }
    }

    private suspend fun upsertRemotePaymentIfNewer(remote: BookingPaymentEntity) {
        val local = bookingPaymentDao.getByRemoteId(remote.remoteId)
        if (local == null || shouldAcceptRemote(local, remote.revision, remote.updatedAt)) {
            bookingPaymentDao.upsert(remote.copy(localId = local?.localId ?: 0).markSynced())
            bookingDao.getByRemoteId(remote.bookingRemoteId)?.let { booking ->
                recalculateBookingPaymentAggregate(booking)
            }
        }
    }

    private suspend fun upsertRemoteFinancialLineIfNewer(remote: BookingFinancialLineEntity) {
        val local = bookingFinancialLineDao.getByRemoteId(remote.remoteId)
        if (local == null || remote.revision >= local.revision) {
            db.withTransaction {
                val sameRoomNight = bookingFinancialLineDao.getByRoomNight(
                    hotelRemoteId = remote.hotelRemoteId,
                    bookingRemoteId = remote.bookingRemoteId,
                    roomRemoteId = remote.roomRemoteId,
                    businessDateMillis = remote.businessDateMillis
                )
                if (sameRoomNight != null && sameRoomNight.remoteId != remote.remoteId) {
                    bookingFinancialLineDao.hardDeleteByLocalId(sameRoomNight.localId)
                }
                bookingFinancialLineDao.upsert(remote.copy(localId = local?.localId ?: 0).markSynced())
            }
            repairBookingCacheFromFinancialLines(remote.bookingRemoteId)
            bookingDao.getByRemoteId(remote.bookingRemoteId)?.let { booking ->
                recalculateBookingPaymentAggregate(booking)
            }
        }
    }

    private suspend fun upsertRemoteAccountingChargeIfNewer(remote: BookingAccountingChargeEntity) {
        val local = bookingAccountingChargeDao.getByRemoteId(remote.remoteId)
        if (local == null || shouldAcceptRemote(local, remote.revision, remote.updatedAt)) {
            bookingAccountingChargeDao.upsert(remote.copy(localId = local?.localId ?: 0).markSynced())
        }
    }

    private suspend fun upsertRemoteBookingIfNewer(remote: BookingEntity) {
        val local = bookingDao.getByRemoteId(remote.remoteId)

        if (local == null || remote.revision >= local.revision) {
            bookingDao.upsert(
                remote.copy(localId = local?.localId ?: 0)
                    .withCalculatedPayment()
                    .markSynced()
            )
            repairBookingCacheFromFinancialLines(remote.remoteId)
            bookingDao.getByRemoteId(remote.remoteId)?.let { booking ->
                recalculateBookingPaymentAggregate(booking)
            }
        }
    }

    private suspend fun repairBookingCacheFromFinancialLines(bookingRemoteId: String) {
        val booking = bookingDao.getByRemoteId(bookingRemoteId) ?: return
        if (booking.syncState == SyncState.PENDING || booking.syncState == SyncState.FAILED) return

        val lines = bookingFinancialLineDao.getAllLinesForBooking(hotelRemoteId, bookingRemoteId)
        if (lines.none { !it.isDeleted && it.bookingRemoteId == bookingRemoteId }) return
        val repaired = applyRoomNightFinancialCache(booking, lines)

        if (!bookingFinancialCacheChanged(booking, repaired)) return

        bookingDao.upsert(
            DerivedBookingCachePolicy.preserveSyncIdentity(
                original = booking,
                recalculated = repaired
            )
        )
    }

    private fun bookingFinancialCacheChanged(
        old: BookingEntity,
        new: BookingEntity
    ): Boolean {
        fun changed(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) > 0.01
        return changed(old.rate, new.rate) ||
                changed(old.receivable, new.receivable) ||
                changed(old.balance, new.balance) ||
                changed(old.grossCharges, new.grossCharges) ||
                changed(old.roomRevenue, new.roomRevenue) ||
                changed(old.propertyTax, new.propertyTax) ||
                changed(old.commissionAmount, new.commissionAmount) ||
                changed(old.commissionTax, new.commissionTax) ||
                changed(old.sourceFee, new.sourceFee) ||
                changed(old.tdsAmount, new.tdsAmount) ||
                changed(old.tcsAmount, new.tcsAmount) ||
                changed(old.expectedPayout, new.expectedPayout)
    }

    private fun shouldAcceptRemote(
        local: HotelEntity,
        remoteRevision: Long,
        remoteUpdatedAt: Long
    ): Boolean {
        if (local.syncState == SyncState.PENDING || local.syncState == SyncState.FAILED) return false
        return remoteRevision > local.revision || remoteUpdatedAt >= local.updatedAt
    }

    private fun shouldAcceptRemote(
        local: RoomEntity,
        remoteRevision: Long,
        remoteUpdatedAt: Long
    ): Boolean {
        if (local.syncState == SyncState.PENDING || local.syncState == SyncState.FAILED) return false
        return remoteRevision > local.revision || remoteUpdatedAt >= local.updatedAt
    }

    private fun shouldAcceptRemote(
        local: BookingSourceEntity,
        remoteRevision: Long,
        remoteUpdatedAt: Long
    ): Boolean {
        if (local.syncState == SyncState.PENDING || local.syncState == SyncState.FAILED) return false
        return remoteRevision > local.revision || remoteUpdatedAt >= local.updatedAt
    }

    private fun shouldAcceptRemote(
        local: ManagedPropertyEntity,
        remoteRevision: Long,
        remoteUpdatedAt: Long
    ): Boolean {
        if (local.syncState == SyncState.PENDING || local.syncState == SyncState.FAILED) return false
        return remoteRevision > local.revision || remoteUpdatedAt >= local.updatedAt
    }

    private fun shouldAcceptRemote(
        local: BookingPaymentEntity,
        remoteRevision: Long,
        remoteUpdatedAt: Long
    ): Boolean {
        if (local.syncState == SyncState.PENDING || local.syncState == SyncState.FAILED) return false
        return remoteRevision > local.revision || remoteUpdatedAt >= local.updatedAt
    }

    private fun shouldAcceptRemote(
        local: BookingFinancialLineEntity,
        remoteRevision: Long,
        remoteUpdatedAt: Long
    ): Boolean {
        if (local.syncState == SyncState.PENDING || local.syncState == SyncState.FAILED) return false
        return remoteRevision > local.revision || remoteUpdatedAt >= local.updatedAt
    }

    private fun shouldAcceptRemote(
        local: BookingAccountingChargeEntity,
        remoteRevision: Long,
        remoteUpdatedAt: Long
    ): Boolean {
        if (local.syncState == SyncState.PENDING || local.syncState == SyncState.FAILED) return false
        return remoteRevision > local.revision || remoteUpdatedAt >= local.updatedAt
    }

    private fun shouldAcceptRemote(
        local: BookingEntity,
        remoteRevision: Long,
        remoteUpdatedAt: Long
    ): Boolean {
        if (local.syncState == SyncState.PENDING || local.syncState == SyncState.FAILED) return false
        return remoteRevision > local.revision || remoteUpdatedAt >= local.updatedAt
    }

    private fun hasUnsyncedConflict(local: BookingEntity, remoteRevision: Long): Boolean {
        return (local.syncState == SyncState.PENDING || local.syncState == SyncState.FAILED) &&
                remoteRevision > local.baseRevision
    }

    private fun stableRoomRemoteId(roomName: String): String {
        val slug = roomName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { UUID.randomUUID().toString() }
        return "${hotelRemoteId}_room_$slug"
    }

    private fun stableSourceRemoteId(sourceName: String, propertyRemoteId: String? = null): String {
        val slug = sourceName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { UUID.randomUUID().toString() }
        val propertySlug = propertyRemoteId
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9]+"), "_")
            ?.trim('_')
            ?.takeIf { it.isNotBlank() }
        return if (propertySlug == null) {
            "${hotelRemoteId}_source_$slug"
        } else {
            "${hotelRemoteId}_${propertySlug}_source_$slug"
        }
    }
    private fun stableManagedPropertyRemoteId(propertyName: String): String {
        val slug = propertyName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { UUID.randomUUID().toString() }
        return "${hotelRemoteId}_property_$slug"
    }
    private fun logSyncFailure(operation: String, throwable: Throwable) {
        Log.e("BookingCloudSync", "$operation failed: ${throwable.message}", throwable)
    }

    private fun markRealtimeSyncError(area: String, throwable: Throwable) {
        val message = throwable.message ?: throwable::class.java.simpleName
        realtimeSyncError.postValue("$area sync listener failed: $message")
        logSyncFailure("${area.lowercase()}Listener", throwable)
    }

    private fun clearRealtimeSyncError() {
        if (realtimeSyncError.value != null) {
            realtimeSyncError.postValue(null)
        }
    }

    private suspend fun clearRealtimeSyncErrorIfClean() {
        val hasPendingLocalWrites =
            hotelDao.getUnsyncedHotels().isNotEmpty() ||
                    managedPropertyDao.getUnsyncedProperties(hotelRemoteId).isNotEmpty() ||
                    bookingSourceDao.getUnsyncedSources(hotelRemoteId).isNotEmpty() ||
                    roomDao.getUnsyncedRooms(hotelRemoteId).isNotEmpty() ||
                    bookingPaymentDao.getUnsyncedPayments(hotelRemoteId).isNotEmpty() ||
                    bookingAccountingChargeDao.getUnsyncedCharges(hotelRemoteId).isNotEmpty() ||
                    bookingFinancialLineDao.getUnsyncedLines(hotelRemoteId).isNotEmpty() ||
                    bookingDao.getUnsyncedBookings(hotelRemoteId).isNotEmpty()

        if (!hasPendingLocalWrites) {
            clearRealtimeSyncError()
        }
    }

    suspend fun hasHotel(): Boolean {
        return hotelDao.getByRemoteId(hotelRemoteId) != null
    }

    fun updateRoom(
        room: RoomEntity,
        newRoomName: String,
        newCategoryName: String,
        newCategoryColor: String,
        propertyRemoteId: String? = room.propertyRemoteId
    ) {
        scope.launch {
            val cleanName = newRoomName.trim()
            if (cleanName.isEmpty()) return@launch
            val updated = room.copy(
                roomName = cleanName,
                categoryName = "",
                categoryColor = "#EEF0F2",
                categorySortOrder = 0,
                propertyRemoteId = propertyRemoteId?.takeIf { it.isNotBlank() },
                sortOrder = room.sortOrder,
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING,
                lastSyncError = null,
                baseRevision = room.baseRevision.takeIf { it > 0 } ?: room.revision
            )

            roomDao.upsert(updated)
            pushRoomAndMark(updated)
        }
    }

    fun moveRoom(room: RoomEntity, direction: Int) {
        scope.launch {
            val roomsInCategory = roomDao.getRooms(hotelRemoteId)
                .sortedWith(compareBy<RoomEntity> { it.sortOrder }.thenBy { it.roomName })
            val index = roomsInCategory.indexOfFirst { it.remoteId == room.remoteId }
            val targetIndex = index + direction
            if (index == -1 || targetIndex !in roomsInCategory.indices) return@launch

            val target = roomsInCategory[targetIndex]
            val now = System.currentTimeMillis()
            val updatedRoom = room.copy(
                sortOrder = target.sortOrder,
                updatedAt = now,
                syncState = SyncState.PENDING,
                lastSyncError = null,
                baseRevision = room.baseRevision.takeIf { it > 0 } ?: room.revision
            )
            val updatedTarget = target.copy(
                sortOrder = room.sortOrder,
                updatedAt = now,
                syncState = SyncState.PENDING,
                lastSyncError = null,
                baseRevision = target.baseRevision.takeIf { it > 0 } ?: target.revision
            )
            roomDao.upsertAll(listOf(updatedRoom, updatedTarget))
            pushRoomAndMark(updatedRoom)
            pushRoomAndMark(updatedTarget)
        }
    }

    fun deleteManagedProperty(property: ManagedPropertyEntity) {
        scope.launch {
            if (foodBillDao.countBillsForProperty(hotelRemoteId, property.remoteId) > 0) {
                return@launch
            }
            val assignedRooms = roomDao.getRooms(hotelRemoteId)
                .filter { it.propertyRemoteId == property.remoteId && !it.isDeleted }
            if (assignedRooms.isNotEmpty()) return@launch

            val deleted = property.copy(
                isDeleted = true,
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING,
                lastSyncError = null,
                baseRevision = property.baseRevision.takeIf { it > 0 } ?: property.revision
            )
            managedPropertyDao.upsert(deleted)
            pushManagedPropertyAndMark(deleted)
        }
    }

    suspend fun deleteRoom(room: RoomEntity): SaveResult {
        val now = System.currentTimeMillis()
        val result = db.withTransaction {
            val current = roomDao.getByRemoteId(room.remoteId)
                ?: return@withTransaction SaveResult.Error("Room not found.")
            val history = roomHistoryFacts(current.remoteId)
            RoomLifecyclePolicy.deleteError(history)?.let {
                return@withTransaction SaveResult.Error(it)
            }
            val deleted = current.copy(
                isDeleted = true,
                updatedAt = now,
                syncState = SyncState.PENDING,
                lastSyncError = null,
                baseRevision = current.baseRevision.takeIf { it > 0 } ?: current.revision
            )
            roomDao.upsert(deleted)
            SaveResult.Success(syncPending = true)
        }
        if (result is SaveResult.Success) enqueueBackgroundSync()
        return result
    }

    suspend fun disableRoom(room: RoomEntity, reason: String): SaveResult =
        changeRoomLifecycle(room, RoomLifecycleStatus.DISABLED, reason)

    suspend fun retireRoom(room: RoomEntity, reason: String): SaveResult =
        changeRoomLifecycle(room, RoomLifecycleStatus.RETIRED, reason)

    suspend fun reactivateRoom(room: RoomEntity): SaveResult {
        val now = System.currentTimeMillis()
        val result = db.withTransaction {
            val current = roomDao.getByRemoteId(room.remoteId)
                ?: return@withTransaction SaveResult.Error("Room not found.")
            if (RoomLifecycleStatus.normalize(current.lifecycleStatus) == RoomLifecycleStatus.RETIRED) {
                return@withTransaction SaveResult.Error("A retired room cannot be reactivated.")
            }
            roomDao.upsert(
                current.copy(
                    lifecycleStatus = RoomLifecycleStatus.ACTIVE,
                    lifecycleReason = null,
                    disabledAtMillis = null,
                    updatedAt = now,
                    syncState = SyncState.PENDING,
                    lastSyncError = null,
                    baseRevision = current.baseRevision.takeIf { it > 0 } ?: current.revision
                )
            )
            SaveResult.Success(syncPending = true)
        }
        if (result is SaveResult.Success) enqueueBackgroundSync()
        return result
    }

    private suspend fun changeRoomLifecycle(
        room: RoomEntity,
        targetStatus: String,
        reason: String
    ): SaveResult {
        val cleanReason = reason.trim()
        val now = System.currentTimeMillis()
        val result = db.withTransaction {
            val current = roomDao.getByRemoteId(room.remoteId)
                ?: return@withTransaction SaveResult.Error("Room not found.")
            if (RoomLifecycleStatus.normalize(current.lifecycleStatus) == RoomLifecycleStatus.RETIRED) {
                return@withTransaction SaveResult.Error("This room is permanently retired.")
            }
            val allBookings = bookingDao.getAllBookingsIncludingDeleted(hotelRemoteId)
            val blocking = RoomLifecyclePolicy.blockingBookings(current.remoteId, allBookings, now)
            RoomLifecyclePolicy.inactiveTransitionError(targetStatus, cleanReason, blocking)?.let {
                return@withTransaction SaveResult.Error(it)
            }
            if (targetStatus == RoomLifecycleStatus.RETIRED) {
                val unbilledPastBooking = allBookings
                    .filter {
                        !it.isDeleted &&
                            current.remoteId in it.roomRemoteIds &&
                            it.checkOutMillis <= now
                    }
                    .firstOrNull { pastBooking ->
                        foodBillDao.getFinalBillForBooking(
                            hotelRemoteId,
                            "${pastBooking.remoteId}_final_bill_"
                        ) == null
                    }
                RoomLifecyclePolicy.retirementBillingError(unbilledPastBooking != null)?.let {
                    return@withTransaction SaveResult.Error(it)
                }
            }
            roomDao.upsert(
                current.copy(
                    lifecycleStatus = targetStatus,
                    lifecycleReason = cleanReason,
                    disabledAtMillis = if (targetStatus == RoomLifecycleStatus.DISABLED) now else current.disabledAtMillis,
                    retiredAtMillis = if (targetStatus == RoomLifecycleStatus.RETIRED) now else null,
                    updatedAt = now,
                    syncState = SyncState.PENDING,
                    lastSyncError = null,
                    baseRevision = current.baseRevision.takeIf { it > 0 } ?: current.revision
                )
            )
            SaveResult.Success(syncPending = true)
        }
        if (result is SaveResult.Success) enqueueBackgroundSync()
        return result
    }

    private suspend fun roomHistoryFacts(roomRemoteId: String): RoomHistoryFacts {
        val bookings = bookingDao.getAllBookingsIncludingDeleted(hotelRemoteId)
        return RoomHistoryFacts(
            bookingCount = bookings.count { roomRemoteId in it.roomRemoteIds },
            financialLineCount = bookingFinancialLineDao.countForRoom(hotelRemoteId, roomRemoteId),
            foodOrderCount = foodOrderDao.countForRoom(hotelRemoteId, roomRemoteId)
        )
    }
    private suspend fun validateRoomsForBookingSave(
        requestedBooking: BookingEntity,
        existingBooking: BookingEntity?
    ): SaveResult? {
        if (requestedBooking.roomRemoteIds.isEmpty()) {
            return SaveResult.Error("Select at least one active room for this booking.")
        }

        val requestedRoomIds = requestedBooking.roomRemoteIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (requestedRoomIds.size != requestedBooking.roomRemoteIds.size) {
            return SaveResult.Error("Selected room is invalid. Please choose an active room again.")
        }

        val requestedRooms = requestedRoomIds.map { roomRemoteId ->
            roomDao.getByRemoteId(roomRemoteId)
                ?: return SaveResult.Error("Selected room is no longer available. Please refresh rooms and choose an active room.")
        }
        if (!BookingPropertyPolicy.belongsToSingleProperty(requestedRooms.map { it.propertyRemoteId })) {
            return SaveResult.Error(
                "All rooms in one booking must belong to the same property. Create a separate booking for another property."
            )
        }

        val existingRoomIds = existingBooking
            ?.roomRemoteIds
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        val newlyAddedRoomIds = requestedRoomIds.filter { roomRemoteId ->
            roomRemoteId !in existingRoomIds
        }

        if (newlyAddedRoomIds.isEmpty()) {
            return null
        }

        newlyAddedRoomIds.forEach { roomRemoteId ->
            val room = roomDao.getByRemoteId(roomRemoteId)
                ?: return SaveResult.Error("Selected room is no longer available. Please refresh rooms and choose an active room.")

            if (room.hotelRemoteId != hotelRemoteId || room.isDeleted) {
                return SaveResult.Error("Selected room is deleted or unavailable. Please choose an active room.")
            }

            val lifecycleStatus = RoomLifecycleStatus.normalize(room.lifecycleStatus)

            if (lifecycleStatus == RoomLifecycleStatus.DISABLED) {
                return SaveResult.Error("${room.roomName} is disabled. Enable it before creating a new booking.")
            }

            if (lifecycleStatus == RoomLifecycleStatus.RETIRED) {
                return SaveResult.Error("${room.roomName} is retired and cannot be used for a new booking.")
            }
        }

        return null
    }
    private suspend fun checkedInRoomConflict(booking: BookingEntity): BookingEntity? {
        if (booking.roomRemoteIds.isEmpty()) return null
        return bookingDao.getBookingsByStatus(
            hotelRemoteId = hotelRemoteId,
            statuses = listOf(BookingStatus.CHECKED_IN)
        ).firstOrNull { existing ->
            existing.remoteId != booking.remoteId &&
                    existing.roomRemoteIds.any { roomRemoteId -> roomRemoteId in booking.roomRemoteIds }
        }
    }

    private suspend fun updateBookingLifecycle(booking: BookingEntity): SaveResult {
        val previous = bookingDao.getByRemoteId(booking.remoteId) ?: booking
        val updated = booking.copy(
            updatedAt = System.currentTimeMillis(),
            isDeleted = false,
            syncState = SyncState.PENDING,
            lastSyncError = null,
            baseRevision = booking.baseRevision.takeIf { it > 0 } ?: booking.revision
        )
        db.withTransaction {
            bookingDao.upsert(updated)
            val lines = bookingFinancialLineDao.getAllLinesForBooking(hotelRemoteId, booking.remoteId)
            enqueueBookingOutbox(previous, updated, lines)
        }
        enqueueBackgroundSync()
        return SaveResult.Success(syncPending = true)
    }

    private suspend fun enqueueBookingOutbox(
        previous: BookingEntity?,
        booking: BookingEntity,
        lines: List<BookingFinancialLineEntity>
    ) {
        val changeSet = BookingChangeSet.create(previous, booking, lines, lines)
        bookingSyncOutboxDao.upsert(
            BookingSyncOutboxEntity(
                operationId = UUID.randomUUID().toString(),
                hotelRemoteId = hotelRemoteId,
                bookingRemoteId = booking.remoteId,
                changeSetJson = changeSet.toJson(),
                createdAt = booking.updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
            )
        )
    }

    private suspend fun bookingPropertyForRooms(roomRemoteIds: List<String>): String? {
        val propertyIds = roomRemoteIds
            .mapNotNull { roomRemoteId ->
                roomDao.getByRemoteId(roomRemoteId)
                    ?.propertyRemoteId
                    ?.takeIf { it.isNotBlank() }
            }
            .distinct()
        return propertyIds.singleOrNull()
    }

}

private fun appendLifecycleNote(existingNotes: String?, label: String, note: String?): String? {
    if (note.isNullOrBlank()) return existingNotes
    val entry = "$label: $note"
    return existingNotes?.takeIf { it.isNotBlank() }?.let { "$it\n$entry" } ?: entry
}

private fun formatAmount(amount: Double): String =
    String.format(Locale.getDefault(), "%.0f", amount)

sealed class SaveResult {
    data class Success(val syncPending: Boolean = false) : SaveResult()
    data class Conflict(val message: String) : SaveResult()
    data class Error(val message: String) : SaveResult()
}

object PaymentStatus {
    const val FULLY_PAID = BookingPaymentStatus.FULLY_PAID
    const val PARTIALLY_PAID = BookingPaymentStatus.PARTIALLY_PAID
    const val NOT_PAID = BookingPaymentStatus.NOT_PAID
    const val COMPLIMENTARY = BookingPaymentStatus.COMPLIMENTARY
}


private fun HotelEntity.markSynced(result: CloudWriteResult? = null): HotelEntity = copy(
    syncState = SyncState.SYNCED,
                lastSyncError = null,
    lastSyncedAt = System.currentTimeMillis(),
    revision = result?.revision ?: revision,
    baseRevision = result?.revision ?: revision,
    updatedByUid = result?.updatedByUid ?: updatedByUid
)

private fun HotelEntity.markFailed(throwable: Throwable): HotelEntity = copy(
    syncState = SyncState.FAILED,
    lastSyncError = throwable.message ?: throwable::class.java.simpleName
)

private fun RoomEntity.markSynced(result: CloudWriteResult? = null): RoomEntity = copy(
    syncState = SyncState.SYNCED,
                lastSyncError = null,
    lastSyncedAt = System.currentTimeMillis(),
    revision = result?.revision ?: revision,
    baseRevision = result?.revision ?: revision,
    updatedByUid = result?.updatedByUid ?: updatedByUid
)

private fun RoomEntity.markFailed(throwable: Throwable): RoomEntity = copy(
    syncState = SyncState.FAILED,
    lastSyncError = throwable.message ?: throwable::class.java.simpleName
)

private fun ManagedPropertyEntity.markSynced(result: CloudWriteResult? = null): ManagedPropertyEntity = copy(
    syncState = SyncState.SYNCED,
    lastSyncError = null,
    lastSyncedAt = System.currentTimeMillis(),
    revision = result?.revision ?: revision,
    baseRevision = result?.revision ?: revision,
    updatedByUid = result?.updatedByUid ?: updatedByUid
)

private fun ManagedPropertyEntity.markFailed(throwable: Throwable): ManagedPropertyEntity = copy(
    syncState = SyncState.FAILED,
    lastSyncError = throwable.message ?: throwable::class.java.simpleName
)

private fun BookingSourceEntity.markSynced(result: CloudWriteResult? = null): BookingSourceEntity = copy(
    syncState = SyncState.SYNCED,
                lastSyncError = null,
    lastSyncedAt = System.currentTimeMillis(),
    revision = result?.revision ?: revision,
    baseRevision = result?.revision ?: revision,
    updatedByUid = result?.updatedByUid ?: updatedByUid
)

private fun BookingSourceEntity.markFailed(throwable: Throwable): BookingSourceEntity = copy(
    syncState = SyncState.FAILED,
    lastSyncError = throwable.message ?: throwable::class.java.simpleName
)

private fun BookingPaymentEntity.markSynced(result: CloudWriteResult? = null): BookingPaymentEntity = copy(
    syncState = SyncState.SYNCED,
    lastSyncError = null,
    lastSyncedAt = System.currentTimeMillis(),
    revision = result?.revision ?: revision,
    baseRevision = result?.revision ?: revision,
    updatedByUid = result?.updatedByUid ?: updatedByUid
)

private fun BookingPaymentEntity.markFailed(throwable: Throwable): BookingPaymentEntity = copy(
    syncState = SyncState.FAILED,
    lastSyncError = throwable.message ?: throwable::class.java.simpleName
)

private fun BookingAccountingChargeEntity.markSynced(result: CloudWriteResult? = null): BookingAccountingChargeEntity = copy(
    syncState = SyncState.SYNCED,
    lastSyncError = null,
    lastSyncedAt = System.currentTimeMillis(),
    revision = result?.revision ?: revision,
    baseRevision = result?.revision ?: revision,
    updatedByUid = result?.updatedByUid ?: updatedByUid
)

private fun BookingAccountingChargeEntity.markFailed(throwable: Throwable): BookingAccountingChargeEntity = copy(
    syncState = SyncState.FAILED,
    lastSyncError = throwable.message ?: throwable::class.java.simpleName
)

private fun BookingFinancialLineEntity.markSynced(result: CloudWriteResult? = null): BookingFinancialLineEntity = copy(
    syncState = SyncState.SYNCED,
    lastSyncError = null,
    lastSyncedAt = System.currentTimeMillis(),
    revision = result?.revision ?: revision,
    baseRevision = result?.revision ?: revision,
    updatedByUid = result?.updatedByUid ?: updatedByUid
)

private fun BookingFinancialLineEntity.markFailed(throwable: Throwable): BookingFinancialLineEntity = copy(
    syncState = SyncState.FAILED,
    lastSyncError = throwable.message ?: throwable::class.java.simpleName
)

private fun BookingEntity.markSynced(result: CloudWriteResult? = null): BookingEntity = copy(

    syncState = SyncState.SYNCED,
                lastSyncError = null,
    lastSyncedAt = System.currentTimeMillis(),
    revision = result?.revision ?: revision,
    baseRevision = result?.revision ?: revision,
    updatedByUid = result?.updatedByUid ?: updatedByUid
)

private fun BookingEntity.markFailed(throwable: Throwable): BookingEntity = copy(
    syncState = SyncState.FAILED,
    lastSyncError = syncFailureText(throwable)
)

private fun BookingEntity.markConflict(message: String): BookingEntity = copy(
    syncState = SyncState.FAILED,
    lastSyncError = message
)
