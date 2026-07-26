package com.example.bookingregister.data.sync

import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.booking.domain.BookingChangeSet
import com.example.bookingregister.data.repository.PaymentStatus
import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingSourceEntity
import com.example.bookingregister.data.entities.FoodBillEntity
import com.example.bookingregister.data.entities.FoodBillItemEntity
import com.example.bookingregister.data.entities.FoodGstCategoryEntity
import com.example.bookingregister.data.entities.FoodBillingScope
import com.example.bookingregister.data.entities.FoodMenuItemEntity
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderItemEntity
import com.example.bookingregister.data.entities.HotelEntity
import com.example.bookingregister.data.entities.ManagedPropertyEntity
import com.example.bookingregister.data.entities.RoomCategoryEntity
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.data.entities.ServiceMenuItemEntity
import com.example.bookingregister.data.withCalculatedPayment
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale

class CloudSyncManager(
    private val hotelRemoteId: String
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val functions = FirebaseFunctions.getInstance("asia-south1")
    private val hotelDoc = firestore.collection("hotels").document(hotelRemoteId)

    private var roomsListener: ListenerRegistration? = null
    private var propertiesListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null
    private var bookingsListener: ListenerRegistration? = null
    private var accountingChargesListener: ListenerRegistration? = null
    private var financialLinesListener: ListenerRegistration? = null
    private var paymentsListener: ListenerRegistration? = null
    private var sourcesListener: ListenerRegistration? = null
    private var foodGstCategoriesListener: ListenerRegistration? = null
    private var foodMenuListener: ListenerRegistration? = null
    private var serviceMenuListener: ListenerRegistration? = null
    private var foodOrdersListener: ListenerRegistration? = null
    private var foodOrderItemsListener: ListenerRegistration? = null
    private var foodBillsListener: ListenerRegistration? = null
    private var foodBillItemsListener: ListenerRegistration? = null
    private var hotelListener: ListenerRegistration? = null

    suspend fun reserveInvoiceNumber(prefix: String, billMillis: Long): String {
        val response = functions
            .getHttpsCallable("reserveInvoiceNumber")
            .callSafely(
                mapOf(
                    "hotelId" to hotelRemoteId,
                    "prefix" to prefix,
                    "billMillis" to billMillis
                )
            )
        val data = response.data as? Map<*, *>
        return data?.get("billNumber") as? String
            ?: error("Invoice number reservation failed")
    }

    fun startHotelListener(
        onHotelChanged: (HotelEntity) -> Unit,
        onSyncError: (Throwable) -> Unit = {}
    ) {
        hotelListener?.remove()
        hotelListener = hotelDoc.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onSyncError(error)
                return@addSnapshotListener
            }

            val doc = snapshot ?: return@addSnapshotListener
            if (!doc.exists()) return@addSnapshotListener

            onHotelChanged(
                HotelEntity(
                    remoteId = doc.id,
                    hotelName = doc.getStringCompat("hotelName") ?: "Booking Register",
                    gstNumber = doc.getStringCompat("gstNumber"),
                    address = doc.getStringCompat("address"),
                    phone = doc.getStringCompat("phone"),
                    updatedAt = doc.getLongCompat("updatedAt") ?: System.currentTimeMillis(),
                    isDeleted = doc.getBooleanCompat("isDeleted") ?: false,
                    syncState = SyncState.SYNCED,
                    lastSyncError = null,
                    lastSyncedAt = System.currentTimeMillis(),
                    revision = doc.getLongCompat("revision") ?: 0,
                    baseRevision = doc.getLongCompat("revision") ?: 0,
                    updatedByUid = doc.getStringCompat("updatedByUid")
                )
            )
        }
    }


    fun startRoomListener(
        sinceUpdatedAt: Long?,
        onRoomsChanged: (List<RoomEntity>) -> Unit,
        onSyncError: (Throwable) -> Unit = {}
    ) {
        roomsListener?.remove()
        roomsListener = scopedCollectionListener("rooms", sinceUpdatedAt)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onSyncError(error)
                    return@addSnapshotListener
                }

                val rooms = snapshot?.documents
                    ?.mapNotNull { doc ->
                        RoomEntity(
                            remoteId = doc.id,
                            hotelRemoteId = doc.getStringCompat("hotelRemoteId") ?: hotelRemoteId,
                            roomName = doc.getStringCompat("roomName") ?: return@mapNotNull null,
                            categoryName = doc.getStringCompat("categoryName").orEmpty(),
                            categoryColor = doc.getStringCompat("categoryColor") ?: "#EEF0F2",
                            categorySortOrder = doc.getLongCompat("categorySortOrder")?.toInt() ?: 0,
                            propertyRemoteId = doc.getStringCompat("propertyRemoteId"),
                            sortOrder = doc.getLongCompat("sortOrder")?.toInt() ?: 0,
                            lifecycleStatus = doc.getStringCompat("lifecycleStatus") ?: "ACTIVE",
                            lifecycleReason = doc.getStringCompat("lifecycleReason"),
                            disabledAtMillis = doc.getLongCompat("disabledAtMillis"),
                            retiredAtMillis = doc.getLongCompat("retiredAtMillis"),
                            updatedAt = doc.getLongCompat("updatedAt") ?: System.currentTimeMillis(),
                            isDeleted = doc.getBooleanCompat("isDeleted") ?: false,
                            syncState = SyncState.SYNCED,
                            lastSyncError = null,
                            lastSyncedAt = System.currentTimeMillis(),
                            revision = doc.getLongCompat("revision") ?: 0,
                            baseRevision = doc.getLongCompat("revision") ?: 0,
                            updatedByUid = doc.getStringCompat("updatedByUid")
                        )
                    }
                    ?: return@addSnapshotListener

                onRoomsChanged(rooms)
            }
    }

    fun startCategoryListener(
        sinceUpdatedAt: Long?,
        onCategoriesChanged: (List<RoomCategoryEntity>) -> Unit,
        onSyncError: (Throwable) -> Unit = {}
    ) {
        categoriesListener?.remove()
        categoriesListener = scopedCollectionListener("roomCategories", sinceUpdatedAt)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onSyncError(error)
                    return@addSnapshotListener
                }

                val categories = snapshot?.documents
                    ?.mapNotNull { doc ->
                        RoomCategoryEntity(
                            remoteId = doc.id,
                            hotelRemoteId = doc.getStringCompat("hotelRemoteId") ?: hotelRemoteId,
                            categoryName = doc.getStringCompat("categoryName") ?: return@mapNotNull null,
                            categoryColor = doc.getStringCompat("categoryColor") ?: "#EEF0F2",
                            sortOrder = doc.getLongCompat("sortOrder")?.toInt() ?: 0,
                            updatedAt = doc.getLongCompat("updatedAt") ?: System.currentTimeMillis(),
                            isDeleted = doc.getBooleanCompat("isDeleted") ?: false,
                            syncState = SyncState.SYNCED,
                            lastSyncError = null,
                            lastSyncedAt = System.currentTimeMillis(),
                            revision = doc.getLongCompat("revision") ?: 0,
                            baseRevision = doc.getLongCompat("revision") ?: 0,
                            updatedByUid = doc.getStringCompat("updatedByUid")
                        )
                    }
                    ?: return@addSnapshotListener

                onCategoriesChanged(categories)
            }
    }



    fun startSourceListener(
        sinceUpdatedAt: Long?,
        onSourcesChanged: (List<BookingSourceEntity>) -> Unit,
        onSyncError: (Throwable) -> Unit = {}
    ) {
        sourcesListener?.remove()
        sourcesListener = scopedCollectionListener("bookingSources", sinceUpdatedAt)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onSyncError(error)
                    return@addSnapshotListener
                }
                val sources = snapshot?.documents
                    ?.mapNotNull { doc ->
                        BookingSourceEntity(
                            remoteId = doc.id,
                            hotelRemoteId = doc.getStringCompat("hotelRemoteId") ?: hotelRemoteId,
                            propertyRemoteId = doc.getStringCompat("propertyRemoteId"),
                            sourceName = doc.getStringCompat("sourceName") ?: return@mapNotNull null,
                            sourceType = doc.getStringCompat("sourceType") ?: "DIRECT",
                            commissionPercent = doc.getDoubleCompat("commissionPercent") ?: 0.0,
                            commissionGstPercent = doc.getDoubleCompat("commissionGstPercent") ?: 0.0,
                            tcsPercent = doc.getDoubleCompat("tcsPercent") ?: 0.0,
                            tdsPercent = doc.getDoubleCompat("tdsPercent") ?: 0.0,
                            fixedFee = doc.getDoubleCompat("fixedFee") ?: 0.0,
                            isActive = doc.getBooleanCompat("isActive") ?: true,
                            updatedAt = doc.getLongCompat("updatedAt") ?: System.currentTimeMillis(),
                            isDeleted = doc.getBooleanCompat("isDeleted") ?: false,
                            syncState = SyncState.SYNCED,
                            lastSyncError = null,
                            lastSyncedAt = System.currentTimeMillis(),
                            revision = doc.getLongCompat("revision") ?: 0,
                            baseRevision = doc.getLongCompat("revision") ?: 0,
                            updatedByUid = doc.getStringCompat("updatedByUid")
                        )
                    }
                    ?: return@addSnapshotListener
                onSourcesChanged(sources)
            }
    }
    fun startBookingListener(
        sinceUpdatedAt: Long?,
        onBookingsChanged: (List<BookingEntity>) -> Unit,
        onSyncError: (Throwable) -> Unit = {}
    ) {
        bookingsListener?.remove()
        bookingsListener = scopedCollectionListener("bookings", sinceUpdatedAt)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onSyncError(error)
                    return@addSnapshotListener
                }

                val bookings = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toBookingEntity() }
                    ?: return@addSnapshotListener

                onBookingsChanged(bookings)
            }
    }

    fun startManagedPropertyListener(
        sinceUpdatedAt: Long?,
        onPropertiesChanged: (List<ManagedPropertyEntity>) -> Unit,
        onSyncError: (Throwable) -> Unit = {}
    ) {
        propertiesListener?.remove()
        propertiesListener = scopedCollectionListener("managedProperties", sinceUpdatedAt)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onSyncError(error)
                    return@addSnapshotListener
                }

                val properties = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toManagedPropertyEntity() }
                    ?: return@addSnapshotListener

                onPropertiesChanged(properties)
            }
    }

    fun startPaymentListener(
        sinceUpdatedAt: Long?,
        onPaymentsChanged: (List<BookingPaymentEntity>) -> Unit,
        onSyncError: (Throwable) -> Unit = {}
    ) {
        paymentsListener?.remove()
        paymentsListener = scopedCollectionListener("bookingPayments", sinceUpdatedAt)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onSyncError(error)
                    return@addSnapshotListener
                }

                val payments = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toBookingPaymentEntity() }
                    ?: return@addSnapshotListener

                onPaymentsChanged(payments)
            }
    }

    fun startFinancialLineListener(
        sinceUpdatedAt: Long?,
        onLinesChanged: (List<BookingFinancialLineEntity>) -> Unit,
        onSyncError: (Throwable) -> Unit = {}
    ) {
        financialLinesListener?.remove()
        financialLinesListener = scopedCollectionListener("bookingFinancialLines", sinceUpdatedAt)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onSyncError(error)
                    return@addSnapshotListener
                }

                val lines = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toBookingFinancialLineEntity() }
                    ?: return@addSnapshotListener

                onLinesChanged(lines)
            }
    }

    fun startAccountingChargeListener(
        sinceUpdatedAt: Long?,
        onChargesChanged: (List<BookingAccountingChargeEntity>) -> Unit,
        onSyncError: (Throwable) -> Unit = {}
    ) {
        accountingChargesListener?.remove()
        accountingChargesListener = scopedCollectionListener("bookingAccountingCharges", sinceUpdatedAt)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onSyncError(error)
                    return@addSnapshotListener
                }

                val charges = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toBookingAccountingChargeEntity() }
                    ?: return@addSnapshotListener

                onChargesChanged(charges)
            }
    }

    fun startFoodMenuListener(
        sinceUpdatedAt: Long?,
        onItemsChanged: (List<FoodMenuItemEntity>) -> Unit,
        onSyncError: (Throwable) -> Unit = {}
    ) {
        foodMenuListener?.remove()
        foodMenuListener = scopedCollectionListener("foodMenuItems", sinceUpdatedAt)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onSyncError(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toFoodMenuItemEntity() }
                    ?: return@addSnapshotListener
                onItemsChanged(items)
            }
    }

    fun startServiceMenuListener(
        sinceUpdatedAt: Long?,
        onItemsChanged: (List<ServiceMenuItemEntity>) -> Unit,
        onSyncError: (Throwable) -> Unit = {}
    ) {
        serviceMenuListener?.remove()
        serviceMenuListener = scopedCollectionListener("serviceMenuItems", sinceUpdatedAt)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onSyncError(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toServiceMenuItemEntity() }
                    ?: return@addSnapshotListener
                onItemsChanged(items)
            }
    }

    fun startFoodGstCategoryListener(
        sinceUpdatedAt: Long?,
        onCategoriesChanged: (List<FoodGstCategoryEntity>) -> Unit,
        onSyncError: (Throwable) -> Unit = {}
    ) {
        foodGstCategoriesListener?.remove()
        foodGstCategoriesListener = scopedCollectionListener("foodGstCategories", sinceUpdatedAt)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onSyncError(error)
                    return@addSnapshotListener
                }
                val categories = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toFoodGstCategoryEntity() }
                    ?: return@addSnapshotListener
                onCategoriesChanged(categories)
            }
    }

    fun startFoodOrderListener(
        sinceUpdatedAt: Long?,
        onOrdersChanged: (List<FoodOrderEntity>) -> Unit,
        onSyncError: (Throwable) -> Unit = {}
    ) {
        foodOrdersListener?.remove()
        foodOrdersListener = scopedCollectionListener("foodOrders", sinceUpdatedAt)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onSyncError(error)
                    return@addSnapshotListener
                }
                val orders = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toFoodOrderEntity() }
                    ?: return@addSnapshotListener
                onOrdersChanged(orders)
            }
    }

    fun startFoodOrderItemListener(
        sinceUpdatedAt: Long?,
        onItemsChanged: (List<FoodOrderItemEntity>) -> Unit,
        onSyncError: (Throwable) -> Unit = {}
    ) {
        foodOrderItemsListener?.remove()
        foodOrderItemsListener = scopedCollectionListener("foodOrderItems", sinceUpdatedAt)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onSyncError(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toFoodOrderItemEntity() }
                    ?: return@addSnapshotListener
                onItemsChanged(items)
            }
    }

    fun startFoodBillListener(
        sinceUpdatedAt: Long?,
        onBillsChanged: (List<FoodBillEntity>) -> Unit,
        onSyncError: (Throwable) -> Unit = {}
    ) {
        foodBillsListener?.remove()
        foodBillsListener = scopedCollectionListener("foodBills", sinceUpdatedAt)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onSyncError(error)
                    return@addSnapshotListener
                }
                val bills = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toFoodBillEntity() }
                    ?: return@addSnapshotListener
                onBillsChanged(bills)
            }
    }

    fun startFoodBillItemListener(
        sinceUpdatedAt: Long?,
        onItemsChanged: (List<FoodBillItemEntity>) -> Unit,
        onSyncError: (Throwable) -> Unit = {}
    ) {
        foodBillItemsListener?.remove()
        foodBillItemsListener = scopedCollectionListener("foodBillItems", sinceUpdatedAt)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onSyncError(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents
                    ?.mapNotNull { doc -> doc.toFoodBillItemEntity() }
                    ?: return@addSnapshotListener
                onItemsChanged(items)
            }
    }



    private fun scopedCollectionListener(collectionName: String, sinceUpdatedAt: Long?): Query {
        val collection = hotelDoc.collection(collectionName)
        return if (sinceUpdatedAt != null && sinceUpdatedAt > 0L) {
            collection.whereGreaterThan("updatedAt", sinceUpdatedAt)
        } else {
            collection
        }
    }
    fun stop() {
        hotelListener?.remove()
        categoriesListener?.remove()
        roomsListener?.remove()
        propertiesListener?.remove()
        bookingsListener?.remove()
        accountingChargesListener?.remove()
        financialLinesListener?.remove()
        paymentsListener?.remove()
        sourcesListener?.remove()
        foodGstCategoriesListener?.remove()
        foodMenuListener?.remove()
        serviceMenuListener?.remove()
        foodOrdersListener?.remove()
        foodOrderItemsListener?.remove()
        foodBillsListener?.remove()
        foodBillItemsListener?.remove()
        hotelListener = null
        categoriesListener = null
        roomsListener = null
        propertiesListener = null
        bookingsListener = null
        accountingChargesListener = null
        financialLinesListener = null
        paymentsListener = null
        sourcesListener = null
        foodGstCategoriesListener = null
        foodMenuListener = null
        serviceMenuListener = null
        foodOrdersListener = null
        foodOrderItemsListener = null
        foodBillsListener = null
        foodBillItemsListener = null
    }

    suspend fun pushHotel(hotel: HotelEntity): CloudWriteResult {
        val nextRevision = nextRevisionFor(hotel.revision, hotel.baseRevision)
        hotelDoc.set(
            mapOf(
                "hotelRemoteId" to hotel.remoteId,
                "hotelName" to hotel.hotelName,
                "gstNumber" to hotel.gstNumber,
                "address" to hotel.address,
                "phone" to hotel.phone,
                "updatedAt" to hotel.updatedAt,
                "isDeleted" to hotel.isDeleted,
                "revision" to nextRevision,
                "updatedByUid" to currentUid(),
                "serverUpdatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
        return CloudWriteResult(nextRevision, currentUid())
    }

    suspend fun pushRoom(room: RoomEntity): CloudWriteResult {
        val nextRevision = nextRevisionFor(room.revision, room.baseRevision)
        hotelDoc.collection("rooms")
            .document(room.remoteId)
            .set(
                mapOf(
                    "hotelRemoteId" to room.hotelRemoteId,
                    "roomName" to room.roomName,
                    "categoryName" to room.categoryName,
                    "categoryColor" to room.categoryColor,
                    "categorySortOrder" to room.categorySortOrder,
                    "propertyRemoteId" to room.propertyRemoteId,
                    "sortOrder" to room.sortOrder,
                    "lifecycleStatus" to room.lifecycleStatus,
                    "lifecycleReason" to room.lifecycleReason,
                    "disabledAtMillis" to room.disabledAtMillis,
                    "retiredAtMillis" to room.retiredAtMillis,
                    "updatedAt" to room.updatedAt,
                    "isDeleted" to room.isDeleted,
                    "revision" to nextRevision,
                    "updatedByUid" to currentUid(),
                    "serverUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        return CloudWriteResult(nextRevision, currentUid())
    }

    suspend fun pushCategory(category: RoomCategoryEntity): CloudWriteResult {
        val nextRevision = nextRevisionFor(category.revision, category.baseRevision)
        hotelDoc.collection("roomCategories")
            .document(category.remoteId)
            .set(
                mapOf(
                    "hotelRemoteId" to category.hotelRemoteId,
                    "categoryName" to category.categoryName,
                    "categoryColor" to category.categoryColor,
                    "sortOrder" to category.sortOrder,
                    "updatedAt" to category.updatedAt,
                    "isDeleted" to category.isDeleted,
                    "revision" to nextRevision,
                    "updatedByUid" to currentUid(),
                    "serverUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        return CloudWriteResult(nextRevision, currentUid())
    }


    suspend fun pushSource(source: BookingSourceEntity): CloudWriteResult {
        val nextRevision = nextRevisionFor(source.revision, source.baseRevision)
        hotelDoc.collection("bookingSources")
            .document(source.remoteId)
            .set(
                mapOf(
                    "hotelRemoteId" to source.hotelRemoteId,
                    "propertyRemoteId" to source.propertyRemoteId,
                    "sourceName" to source.sourceName,
                    "sourceType" to source.sourceType,
                    "commissionPercent" to source.commissionPercent,
                    "commissionGstPercent" to source.commissionGstPercent,
                    "tcsPercent" to source.tcsPercent,
                    "tdsPercent" to source.tdsPercent,
                    "fixedFee" to source.fixedFee,
                    "isActive" to source.isActive,
                    "updatedAt" to source.updatedAt,
                    "isDeleted" to source.isDeleted,
                    "revision" to nextRevision,
                    "updatedByUid" to currentUid(),
                    "serverUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        return CloudWriteResult(nextRevision, currentUid())
    }

    suspend fun pushManagedProperty(property: ManagedPropertyEntity): CloudWriteResult {
        val nextRevision = nextRevisionFor(property.revision, property.baseRevision)
        hotelDoc.collection("managedProperties")
            .document(property.remoteId)
            .set(
                mapOf(
                    "hotelRemoteId" to property.hotelRemoteId,
                    "propertyName" to property.propertyName,
                    "legalName" to property.legalName,
                    "gstNumber" to property.gstNumber,
                    "address" to property.address,
                    "phone" to property.phone,
                    "email" to property.email,
                    "invoicePrefix" to property.invoicePrefix,
                    "state" to property.state,
                    "sortOrder" to property.sortOrder,
                    "updatedAt" to property.updatedAt,
                    "isDeleted" to property.isDeleted,
                    "revision" to nextRevision,
                    "updatedByUid" to currentUid(),
                    "serverUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        return CloudWriteResult(nextRevision, currentUid())
    }

    suspend fun pushPayment(payment: BookingPaymentEntity): CloudWriteResult {
        val response = functions
            .getHttpsCallable("saveBookingPaymentServer")
            .callSafely(
                mapOf(
                    "hotelId" to hotelRemoteId,
                    "operationId" to "booking_payment_${payment.remoteId}_${payment.updatedAt}",
                    "entity" to payment.toCallableMap()
                )
            )
        return response.data.toCloudWriteResult()
    }

    suspend fun pushAccountingCharge(charge: BookingAccountingChargeEntity): CloudWriteResult {
        val response = functions
            .getHttpsCallable("saveBookingAccountingChargeServer")
            .callSafely(
                mapOf(
                    "hotelId" to hotelRemoteId,
                    "operationId" to "accounting_charge_${charge.remoteId}_${charge.updatedAt}",
                    "entity" to charge.toCallableMap()
                )
            )
        return response.data.toCloudWriteResult()
    }

    suspend fun pushFoodMenuItem(item: FoodMenuItemEntity): CloudWriteResult {
        val nextRevision = nextRevisionFor(item.revision, item.baseRevision)
        hotelDoc.collection("foodMenuItems")
            .document(item.remoteId)
            .set(
                mapOf(
                    "hotelRemoteId" to item.hotelRemoteId,
                    "propertyRemoteId" to item.propertyRemoteId,
                    "gstCategoryRemoteId" to item.gstCategoryRemoteId,
                    "itemName" to item.itemName,
                    "categoryName" to item.categoryName,
                    "price" to item.price,
                    "gstRatePercent" to item.gstRatePercent,
                    "isActive" to item.isActive,
                    "updatedAt" to item.updatedAt,
                    "isDeleted" to item.isDeleted,
                    "revision" to nextRevision,
                    "updatedByUid" to currentUid(),
                    "serverUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        return CloudWriteResult(nextRevision, currentUid())
    }

    suspend fun pushServiceMenuItem(item: ServiceMenuItemEntity): CloudWriteResult {
        val nextRevision = nextRevisionFor(item.revision, item.baseRevision)
        hotelDoc.collection("serviceMenuItems")
            .document(item.remoteId)
            .set(
                mapOf(
                    "hotelRemoteId" to item.hotelRemoteId,
                    "propertyRemoteId" to item.propertyRemoteId,
                    "serviceName" to item.serviceName,
                    "categoryName" to item.categoryName,
                    "description" to item.description,
                    "unitLabel" to item.unitLabel,
                    "price" to item.price,
                    "sacCode" to item.sacCode,
                    "gstRatePercent" to item.gstRatePercent,
                    "taxInclusive" to item.taxInclusive,
                    "isActive" to item.isActive,
                    "sortOrder" to item.sortOrder,
                    "updatedAt" to item.updatedAt,
                    "isDeleted" to item.isDeleted,
                    "revision" to nextRevision,
                    "updatedByUid" to currentUid(),
                    "serverUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        return CloudWriteResult(nextRevision, currentUid())
    }

    suspend fun pushFoodOrderAggregate(
        operationId: String,
        order: FoodOrderEntity,
        orderItems: List<FoodOrderItemEntity>
    ): FoodOrderAggregateWriteResult {
        require(operationId.isNotBlank()) { "Food order sync operation id is missing" }
        require(order.billRemoteId.isNullOrBlank() && order.linkedFinalBillId.isNullOrBlank()) {
            "Billed food orders must sync through the bill aggregate"
        }
        require(orderItems.all { it.orderRemoteId == order.remoteId }) {
            "Food order item belongs to another order"
        }
        val response = functions
            .getHttpsCallable("saveFoodOrderAggregateServer")
            .callSafely(
                mapOf(
                    "hotelId" to hotelRemoteId,
                    "operationId" to operationId,
                    "order" to order.toCallableMap(),
                    "orderItems" to orderItems.map { it.toCallableMap() }
                )
            )
        return response.data.toFoodOrderAggregateWriteResult()
    }

    suspend fun pushFoodGstCategory(category: FoodGstCategoryEntity): CloudWriteResult {
        val nextRevision = nextRevisionFor(category.revision, category.baseRevision)
        hotelDoc.collection("foodGstCategories")
            .document(category.remoteId)
            .set(
                mapOf(
                    "hotelRemoteId" to category.hotelRemoteId,
                    "categoryName" to category.categoryName,
                    "hsnSacCode" to category.hsnSacCode,
                    "gstRatePercent" to category.gstRatePercent,
                    "cgstRatePercent" to category.cgstRatePercent,
                    "sgstRatePercent" to category.sgstRatePercent,
                    "cessRatePercent" to category.cessRatePercent,
                    "taxType" to category.taxType,
                    "itcType" to category.itcType,
                    "description" to category.description,
                    "isDefault" to category.isDefault,
                    "isActive" to category.isActive,
                    "updatedAt" to category.updatedAt,
                    "isDeleted" to category.isDeleted,
                    "revision" to nextRevision,
                    "updatedByUid" to currentUid(),
                    "serverUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        return CloudWriteResult(nextRevision, currentUid())
    }

    suspend fun pushFoodBillAggregate(
        operationId: String,
        bill: FoodBillEntity,
        billItems: List<FoodBillItemEntity>,
        orders: List<FoodOrderEntity>,
        orderItems: List<FoodOrderItemEntity>,
        accountingCharges: List<BookingAccountingChargeEntity>
    ): FoodBillAggregateWriteResult {
        require(operationId.isNotBlank()) { "Food bill sync operation id is missing" }
        require(billItems.isNotEmpty()) { "Food bill must have at least one item" }
        require(billItems.all { it.billRemoteId == bill.remoteId }) {
            "Food bill item belongs to another bill"
        }
        require(orders.all {
            (it.billRemoteId.isNullOrBlank() || it.billRemoteId == bill.remoteId) &&
                    (it.linkedFinalBillId.isNullOrBlank() || it.linkedFinalBillId == bill.remoteId)
        }) {
            "Food order belongs to another bill"
        }
        val orderIds = orders.mapTo(mutableSetOf()) { it.remoteId }
        require(orderItems.all { it.orderRemoteId in orderIds }) {
            "Food order item belongs to an order outside this bill"
        }
        require(accountingCharges.all { it.linkedFinalBillId.isNullOrBlank() || it.linkedFinalBillId == bill.remoteId }) {
            "Accounting charge belongs to another final bill"
        }

        val response = functions
            .getHttpsCallable("saveFoodBillAggregateServer")
            .callSafely(
                mapOf(
                    "hotelId" to hotelRemoteId,
                    "operationId" to operationId,
                    "bill" to bill.toCallableMap(),
                    "billItems" to billItems.map { it.toCallableMap() },
                    "orders" to orders.map { it.toCallableMap() },
                    "orderItems" to orderItems.map { it.toCallableMap() },
                    "accountingCharges" to accountingCharges.map { it.toCallableMap() }
                )
            )

        return response.data.toFoodBillAggregateWriteResult()
    }

    /** Applies an exact user-authored change set to the latest server booking. */
    suspend fun pushBookingChangeSet(
        operationId: String,
        deviceId: String,
        changeSet: BookingChangeSet
    ): BookingAggregateWriteResult {
        val response = functions
            .getHttpsCallable("applyBookingChangeSetServer")
            .callSafely(
                mapOf(
                    "hotelId" to hotelRemoteId,
                    "operationId" to operationId,
                    "deviceId" to deviceId,
                    "changeSet" to changeSet.toMap()
                )
            )
        return response.data.toBookingAggregateWriteResult()
    }

    private fun BookingEntity.toCloudMap(revision: Long, uid: String?): Map<String, Any?> {
        val normalized = withCalculatedPayment()
        val lifecycleStatus = normalized.bookingStatus.ifBlank { BookingStatus.RESERVED }
        return mapOf(
            "bookingUuid" to normalized.bookingUuid,
            "hotelRemoteId" to normalized.hotelRemoteId,
            "propertyRemoteId" to normalized.propertyRemoteId,
            "guestName" to normalized.guestName,
            "guestMobile" to normalized.guestMobile,
            "sourceName" to normalized.sourceName,
            "sourceRemoteId" to normalized.sourceRemoteId,
            "sourceType" to normalized.sourceType,
            "grossCharges" to normalized.grossCharges,
            "roomRevenue" to normalized.roomRevenue,
            "propertyTax" to normalized.propertyTax,
            "commissionAmount" to normalized.commissionAmount,
            "commissionTax" to normalized.commissionTax,
            "sourceFee" to normalized.sourceFee,
            "tdsAmount" to normalized.tdsAmount,
            "tcsAmount" to normalized.tcsAmount,
            "expectedPayout" to normalized.expectedPayout,
            "adultCount" to normalized.adultCount,
            "childCount" to normalized.childCount,
            "checkInMillis" to normalized.checkInMillis,
            "checkOutMillis" to normalized.checkOutMillis,
            "roomRemoteIds" to normalized.roomRemoteIds,
            "rate" to normalized.rate,
            "receivable" to normalized.receivable,
            "finalPrice" to normalized.receivable,
            "finalAmount" to normalized.receivable,
            "paid" to normalized.paid,
            "advancePaid" to normalized.paid,
            "balance" to normalized.balance,
            "paymentStatus" to normalized.paymentStatus,
            "pricingStatus" to normalized.pricingStatus,
            "bookingStatus" to lifecycleStatus,
            "actualCheckInAt" to normalized.actualCheckInAt,
            "actualCheckOutAt" to normalized.actualCheckOutAt,
            "checkoutNote" to normalized.checkoutNote,
            "reopenNote" to normalized.reopenNote,
            "reopenedAt" to normalized.reopenedAt,
            "cancelledAt" to normalized.cancelledAt,
            "cancelledByUid" to normalized.cancelledByUid,
            "cancellationReason" to normalized.cancellationReason,
            "cancellationSettlementStatus" to normalized.cancellationSettlementStatus,
            "cancellationSettlementOutcome" to normalized.cancellationSettlementOutcome,
            "cancellationApprovedRefundAmount" to normalized.cancellationApprovedRefundAmount,
            "cancellationFeeAmount" to normalized.cancellationFeeAmount,
            "cancellationRefundBaselineAmount" to normalized.cancellationRefundBaselineAmount,
            "cancellationDecisionAt" to normalized.cancellationDecisionAt,
            "cancellationDecisionByUid" to normalized.cancellationDecisionByUid,
            "notes" to normalized.notes,
            "updatedAt" to normalized.updatedAt,
            "isDeleted" to normalized.isDeleted,
            "revision" to revision,
            "updatedByUid" to uid,
            "serverUpdatedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun BookingFinancialLineEntity.toCloudMap(revision: Long, uid: String?): Map<String, Any?> = mapOf(
        "hotelRemoteId" to hotelRemoteId,
        "bookingRemoteId" to bookingRemoteId,
        "roomRemoteId" to roomRemoteId,
        "propertyRemoteId" to propertyRemoteId,
        "businessDateMillis" to businessDateMillis,
        "grossAmount" to grossAmount,
        "taxableAmount" to taxableAmount,
        "gstRatePercent" to gstRatePercent,
        "gstAmount" to gstAmount,
        "hsnSacCode" to hsnSacCode,
        "slabRemoteId" to slabRemoteId,
        "slabName" to slabName,
        "cgstRatePercent" to cgstRatePercent,
        "sgstRatePercent" to sgstRatePercent,
        "cessRatePercent" to cessRatePercent,
        "cgstAmount" to cgstAmount,
        "sgstAmount" to sgstAmount,
        "cessAmount" to cessAmount,
        "source" to source,
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted,
        "revision" to revision,
        "updatedByUid" to uid,
        "serverUpdatedAt" to FieldValue.serverTimestamp()
    )

    private fun BookingEntity.toCallableMap(): Map<String, Any?> {
        val normalized = withCalculatedPayment()
        val lifecycleStatus = normalized.bookingStatus.ifBlank { BookingStatus.RESERVED }
        return mapOf(
            "remoteId" to normalized.remoteId,
            "baseRevision" to (normalized.baseRevision.takeIf { it > 0 } ?: normalized.revision),
            "bookingUuid" to normalized.bookingUuid,
            "hotelRemoteId" to normalized.hotelRemoteId,
            "propertyRemoteId" to normalized.propertyRemoteId,
            "guestName" to normalized.guestName,
            "guestMobile" to normalized.guestMobile,
            "sourceName" to normalized.sourceName,
            "sourceRemoteId" to normalized.sourceRemoteId,
            "sourceType" to normalized.sourceType,
            "grossCharges" to normalized.grossCharges,
            "roomRevenue" to normalized.roomRevenue,
            "propertyTax" to normalized.propertyTax,
            "commissionAmount" to normalized.commissionAmount,
            "commissionTax" to normalized.commissionTax,
            "sourceFee" to normalized.sourceFee,
            "tdsAmount" to normalized.tdsAmount,
            "tcsAmount" to normalized.tcsAmount,
            "expectedPayout" to normalized.expectedPayout,
            "adultCount" to normalized.adultCount,
            "childCount" to normalized.childCount,
            "checkInMillis" to normalized.checkInMillis,
            "checkOutMillis" to normalized.checkOutMillis,
            "roomRemoteIds" to normalized.roomRemoteIds,
            "rate" to normalized.rate,
            "receivable" to normalized.receivable,
            "finalPrice" to normalized.receivable,
            "finalAmount" to normalized.receivable,
            "paid" to normalized.paid,
            "advancePaid" to normalized.paid,
            "balance" to normalized.balance,
            "paymentStatus" to normalized.paymentStatus,
            "pricingStatus" to normalized.pricingStatus,
            "bookingStatus" to lifecycleStatus,
            "actualCheckInAt" to normalized.actualCheckInAt,
            "actualCheckOutAt" to normalized.actualCheckOutAt,
            "checkoutNote" to normalized.checkoutNote,
            "reopenNote" to normalized.reopenNote,
            "reopenedAt" to normalized.reopenedAt,
            "cancelledAt" to normalized.cancelledAt,
            "cancelledByUid" to normalized.cancelledByUid,
            "cancellationReason" to normalized.cancellationReason,
            "cancellationSettlementStatus" to normalized.cancellationSettlementStatus,
            "cancellationSettlementOutcome" to normalized.cancellationSettlementOutcome,
            "cancellationApprovedRefundAmount" to normalized.cancellationApprovedRefundAmount,
            "cancellationFeeAmount" to normalized.cancellationFeeAmount,
            "cancellationRefundBaselineAmount" to normalized.cancellationRefundBaselineAmount,
            "cancellationDecisionAt" to normalized.cancellationDecisionAt,
            "cancellationDecisionByUid" to normalized.cancellationDecisionByUid,
            "notes" to normalized.notes,
            "updatedAt" to normalized.updatedAt,
            "isDeleted" to normalized.isDeleted
        )
    }

    private fun BookingFinancialLineEntity.toCallableMap(): Map<String, Any?> = mapOf(
        "remoteId" to remoteId,
        "baseRevision" to (baseRevision.takeIf { it > 0 } ?: revision),
        "hotelRemoteId" to hotelRemoteId,
        "bookingRemoteId" to bookingRemoteId,
        "roomRemoteId" to roomRemoteId,
        "propertyRemoteId" to propertyRemoteId,
        "businessDateMillis" to businessDateMillis,
        "grossAmount" to grossAmount,
        "taxableAmount" to taxableAmount,
        "gstRatePercent" to gstRatePercent,
        "gstAmount" to gstAmount,
        "hsnSacCode" to hsnSacCode,
        "slabRemoteId" to slabRemoteId,
        "slabName" to slabName,
        "cgstRatePercent" to cgstRatePercent,
        "sgstRatePercent" to sgstRatePercent,
        "cessRatePercent" to cessRatePercent,
        "cgstAmount" to cgstAmount,
        "sgstAmount" to sgstAmount,
        "cessAmount" to cessAmount,
        "source" to source,
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted
    )

    private fun BookingPaymentEntity.toCallableMap(): Map<String, Any?> = mapOf(
        "remoteId" to remoteId,
        "baseRevision" to (baseRevision.takeIf { it > 0 } ?: revision),
        "hotelRemoteId" to hotelRemoteId,
        "bookingRemoteId" to bookingRemoteId,
        "originalPaymentRemoteId" to originalPaymentRemoteId,
        "paymentType" to paymentType,
        "paymentCategory" to paymentCategory,
        "amount" to amount,
        "allocatedStayAmount" to allocatedStayAmount,
        "allocatedFoodAmount" to allocatedFoodAmount,
        "allocatedServiceAmount" to allocatedServiceAmount,
        "allocatedDamageAmount" to allocatedDamageAmount,
        "unappliedAmount" to unappliedAmount,
        "paymentMillis" to paymentMillis,
        "method" to method,
        "note" to note,
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted
    )

    private fun FoodBillEntity.toCallableMap(): Map<String, Any?> = mapOf(
        "remoteId" to remoteId,
        "baseRevision" to (baseRevision.takeIf { it > 0 } ?: revision),
        "hotelRemoteId" to hotelRemoteId,
        "propertyRemoteId" to propertyRemoteId,
        "supplierName" to supplierName,
        "supplierGstin" to supplierGstin,
        "supplierAddress" to supplierAddress,
        "supplierPhone" to supplierPhone,
        "supplierState" to supplierState,
        "propertyDisplayName" to propertyDisplayName,
        "billNumber" to billNumber,
        "billMillis" to billMillis,
        "guestName" to guestName,
        "guestMobile" to guestMobile,
        "guestAddress" to guestAddress,
        "guestGstin" to guestGstin,
        "roomsIncluded" to roomsIncluded,
        "orderRemoteIds" to orderRemoteIds,
        "subtotal" to subtotal,
        "discountAmount" to discountAmount,
        "taxableAmount" to taxableAmount,
        "cgstAmount" to cgstAmount,
        "sgstAmount" to sgstAmount,
        "cessAmount" to cessAmount,
        "gstAmount" to gstAmount,
        "grandTotal" to grandTotal,
        "paymentMode" to paymentMode,
        "notes" to notes,
        "status" to status,
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted
    )

    private fun FoodBillItemEntity.toCallableMap(): Map<String, Any?> = mapOf(
        "remoteId" to remoteId,
        "baseRevision" to (baseRevision.takeIf { it > 0 } ?: revision),
        "hotelRemoteId" to hotelRemoteId,
        "billRemoteId" to billRemoteId,
        "orderRemoteId" to orderRemoteId,
        "orderNumber" to orderNumber,
        "orderMillis" to orderMillis,
        "roomName" to roomName,
        "menuItemRemoteId" to menuItemRemoteId,
        "itemName" to itemName,
        "quantity" to quantity,
        "unitPrice" to unitPrice,
        "lineSubtotal" to lineSubtotal,
        "gstCategoryRemoteId" to gstCategoryRemoteId,
        "gstCategoryName" to gstCategoryName,
        "hsnSacCode" to hsnSacCode,
        "gstRatePercent" to gstRatePercent,
        "cgstRatePercent" to cgstRatePercent,
        "sgstRatePercent" to sgstRatePercent,
        "cessRatePercent" to cessRatePercent,
        "taxableAmount" to taxableAmount,
        "cgstAmount" to cgstAmount,
        "sgstAmount" to sgstAmount,
        "cessAmount" to cessAmount,
        "gstAmount" to gstAmount,
        "lineTotal" to lineTotal,
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted
    )

    private fun FoodOrderEntity.toCallableMap(): Map<String, Any?> = mapOf(
        "remoteId" to remoteId,
        "baseRevision" to (baseRevision.takeIf { it > 0 } ?: revision),
        "hotelRemoteId" to hotelRemoteId,
        "propertyRemoteId" to propertyRemoteId,
        "bookingRemoteId" to bookingRemoteId,
        "billRemoteId" to billRemoteId,
        "orderNumber" to orderNumber,
        "foodBillingScope" to foodBillingScope,
        "linkedFinalBillId" to linkedFinalBillId,
        "archivedAt" to archivedAt,
        "roomRemoteId" to roomRemoteId,
        "roomName" to roomName,
        "guestName" to guestName,
        "orderMillis" to orderMillis,
        "status" to status,
        "subtotal" to subtotal,
        "discountAmount" to discountAmount,
        "taxableAmount" to taxableAmount,
        "gstAmount" to gstAmount,
        "totalAmount" to totalAmount,
        "notes" to notes,
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted
    )

    private fun FoodOrderItemEntity.toCallableMap(): Map<String, Any?> = mapOf(
        "remoteId" to remoteId,
        "baseRevision" to (baseRevision.takeIf { it > 0 } ?: revision),
        "hotelRemoteId" to hotelRemoteId,
        "orderRemoteId" to orderRemoteId,
        "menuItemRemoteId" to menuItemRemoteId,
        "itemName" to itemName,
        "quantity" to quantity,
        "unitPrice" to unitPrice,
        "gstRatePercent" to gstRatePercent,
        "gstCategoryRemoteId" to gstCategoryRemoteId,
        "gstCategoryName" to gstCategoryName,
        "hsnSacCode" to hsnSacCode,
        "cgstRatePercent" to cgstRatePercent,
        "sgstRatePercent" to sgstRatePercent,
        "cessRatePercent" to cessRatePercent,
        "lineSubtotal" to lineSubtotal,
        "lineGst" to lineGst,
        "lineTotal" to lineTotal,
        "isCancelled" to isCancelled,
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted
    )

    private fun BookingAccountingChargeEntity.toCallableMap(): Map<String, Any?> = mapOf(
        "remoteId" to remoteId,
        "baseRevision" to (baseRevision.takeIf { it > 0 } ?: revision),
        "hotelRemoteId" to hotelRemoteId,
        "bookingRemoteId" to bookingRemoteId,
        "chargeType" to chargeType,
        "accountBucket" to accountBucket,
        "amount" to amount,
        "description" to description,
        "reason" to reason,
        "hsnSacCode" to hsnSacCode,
        "gstRatePercent" to gstRatePercent,
        "taxInclusive" to taxInclusive,
        "taxableAmount" to taxableAmount,
        "linkedFinalBillId" to linkedFinalBillId,
        "archivedAt" to archivedAt,
        "approvedBy" to approvedBy,
        "createdBy" to createdBy,
        "chargeMillis" to chargeMillis,
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted
    )

    private fun DocumentSnapshot.toBookingEntity(): BookingEntity? {
        if (!exists()) return null

        val roomIds = (get("roomRemoteIds") as? List<*>)
            ?.mapNotNull { it as? String }
            ?: emptyList()

        val cloudDeleted = getBooleanCompat("isDeleted") ?: false
        val cloudStatus = getStringCompat("bookingStatus")?.ifBlank { BookingStatus.RESERVED }
            ?: BookingStatus.RESERVED
        val legacyCancellation = cloudDeleted && cloudStatus != BookingStatus.CANCELLED
        return BookingEntity(
            remoteId = id,
            bookingUuid = getStringCompat("bookingUuid") ?: id,
            hotelRemoteId = getStringCompat("hotelRemoteId") ?: hotelRemoteId,
            propertyRemoteId = getStringCompat("propertyRemoteId"),
            guestName = getStringCompat("guestName") ?: return null,
            guestMobile = getStringCompat("guestMobile"),
            sourceName = getStringCompat("sourceName"),
            sourceRemoteId = getStringCompat("sourceRemoteId"),
            sourceType = getStringCompat("sourceType") ?: "DIRECT",
            adultCount = getLongCompat("adultCount")?.toInt() ?: 1,
            childCount = getLongCompat("childCount")?.toInt() ?: 0,
            checkInMillis = getLongCompat("checkInMillis") ?: return null,
            checkOutMillis = getLongCompat("checkOutMillis") ?: return null,
            roomRemoteIds = roomIds,
            rate = getDoubleCompat("rate")
                ?: getDoubleCompat("finalPrice")
                ?: getDoubleCompat("finalAmount")
                ?: 0.0,
            receivable = getDoubleCompat("receivable")
                ?: getDoubleCompat("finalPrice")
                ?: getDoubleCompat("finalAmount")
                ?: 0.0,
            paid = getDoubleCompat("paid")
                ?: getDoubleCompat("advancePaid")
                ?: 0.0,
            balance = getDoubleCompat("balance") ?: 0.0,
            paymentStatus = getStringCompat("paymentStatus") ?: PaymentStatus.NOT_PAID,
            pricingStatus = getStringCompat("pricingStatus") ?: com.example.bookingregister.booking.domain.BookingPricingStatus.CONFIRMED,
            grossCharges = getDoubleCompat("grossCharges") ?: 0.0,
            roomRevenue = getDoubleCompat("roomRevenue") ?: getDoubleCompat("receivable") ?: 0.0,
            propertyTax = getDoubleCompat("propertyTax") ?: 0.0,
            commissionAmount = getDoubleCompat("commissionAmount") ?: 0.0,
            commissionTax = getDoubleCompat("commissionTax") ?: 0.0,
            sourceFee = getDoubleCompat("sourceFee") ?: 0.0,
            tdsAmount = getDoubleCompat("tdsAmount") ?: 0.0,
            tcsAmount = getDoubleCompat("tcsAmount") ?: 0.0,
            expectedPayout = getDoubleCompat("expectedPayout") ?: 0.0,
            bookingStatus = if (legacyCancellation) BookingStatus.CANCELLED else cloudStatus,
            actualCheckInAt = getLongCompat("actualCheckInAt"),
            actualCheckOutAt = getLongCompat("actualCheckOutAt"),
            checkoutNote = getStringCompat("checkoutNote"),
            reopenNote = getStringCompat("reopenNote"),
            reopenedAt = getLongCompat("reopenedAt"),
            cancelledAt = getLongCompat("cancelledAt"),
            cancelledByUid = getStringCompat("cancelledByUid"),
            cancellationReason = getStringCompat("cancellationReason")
                ?: if (legacyCancellation) "Cancelled in an earlier app version" else null,
            cancellationSettlementStatus = getStringCompat("cancellationSettlementStatus")
                ?: if (legacyCancellation || cloudStatus == BookingStatus.CANCELLED) {
                    com.example.bookingregister.booking.domain.CancellationSettlementStatus.PENDING
                } else {
                    com.example.bookingregister.booking.domain.CancellationSettlementStatus.NOT_APPLICABLE
                },
            cancellationSettlementOutcome = getStringCompat("cancellationSettlementOutcome"),
            cancellationApprovedRefundAmount = getDoubleCompat("cancellationApprovedRefundAmount") ?: 0.0,
            cancellationFeeAmount = getDoubleCompat("cancellationFeeAmount") ?: 0.0,
            cancellationRefundBaselineAmount = getDoubleCompat("cancellationRefundBaselineAmount") ?: 0.0,
            cancellationDecisionAt = getLongCompat("cancellationDecisionAt"),
            cancellationDecisionByUid = getStringCompat("cancellationDecisionByUid"),
            notes = getStringCompat("notes"),
            updatedAt = getLongCompat("updatedAt") ?: System.currentTimeMillis(),
            isDeleted = false,
            syncState = SyncState.SYNCED,
            lastSyncError = null,
            lastSyncedAt = System.currentTimeMillis(),
            revision = getLongCompat("revision") ?: 0,
            baseRevision = getLongCompat("revision") ?: 0,
            updatedByUid = getStringCompat("updatedByUid")
        ).withCalculatedPayment()
    }

    private fun DocumentSnapshot.toBookingPaymentEntity(): BookingPaymentEntity? {
        if (!exists()) return null
        return BookingPaymentEntity(
            remoteId = id,
            hotelRemoteId = getStringCompat("hotelRemoteId") ?: hotelRemoteId,
            bookingRemoteId = getStringCompat("bookingRemoteId") ?: return null,
            originalPaymentRemoteId = getStringCompat("originalPaymentRemoteId"),
            paymentType = getStringCompat("paymentType") ?: "PAYMENT",
            paymentCategory = getStringCompat("paymentCategory") ?: "AUTO",
            amount = getDoubleCompat("amount") ?: 0.0,
            allocatedStayAmount = getDoubleCompat("allocatedStayAmount") ?: 0.0,
            allocatedFoodAmount = getDoubleCompat("allocatedFoodAmount") ?: 0.0,
            allocatedServiceAmount = getDoubleCompat("allocatedServiceAmount") ?: 0.0,
            allocatedDamageAmount = getDoubleCompat("allocatedDamageAmount") ?: 0.0,
            unappliedAmount = getDoubleCompat("unappliedAmount") ?: 0.0,
            paymentMillis = getLongCompat("paymentMillis") ?: System.currentTimeMillis(),
            method = getStringCompat("method"),
            note = getStringCompat("note"),
            updatedAt = getLongCompat("updatedAt") ?: System.currentTimeMillis(),
            isDeleted = getBooleanCompat("isDeleted") ?: false,
            syncState = SyncState.SYNCED,
            lastSyncError = null,
            lastSyncedAt = System.currentTimeMillis(),
            revision = getLongCompat("revision") ?: 0,
            baseRevision = getLongCompat("revision") ?: 0,
            updatedByUid = getStringCompat("updatedByUid")
        )
    }

    private fun DocumentSnapshot.toBookingFinancialLineEntity(): BookingFinancialLineEntity? {
        if (!exists()) return null
        return BookingFinancialLineEntity(
            remoteId = id,
            hotelRemoteId = getStringCompat("hotelRemoteId") ?: hotelRemoteId,
            bookingRemoteId = getStringCompat("bookingRemoteId") ?: return null,
            roomRemoteId = getStringCompat("roomRemoteId") ?: return null,
            propertyRemoteId = getStringCompat("propertyRemoteId"),
            businessDateMillis = getLongCompat("businessDateMillis") ?: return null,
            grossAmount = getDoubleCompat("grossAmount") ?: 0.0,
            taxableAmount = getDoubleCompat("taxableAmount") ?: 0.0,
            gstRatePercent = getDoubleCompat("gstRatePercent") ?: 0.0,
            gstAmount = getDoubleCompat("gstAmount") ?: 0.0,
            hsnSacCode = getStringCompat("hsnSacCode"),
            slabRemoteId = getStringCompat("slabRemoteId"),
            slabName = getStringCompat("slabName"),
            cgstRatePercent = getDoubleCompat("cgstRatePercent") ?: 0.0,
            sgstRatePercent = getDoubleCompat("sgstRatePercent") ?: 0.0,
            cessRatePercent = getDoubleCompat("cessRatePercent") ?: 0.0,
            cgstAmount = getDoubleCompat("cgstAmount") ?: 0.0,
            sgstAmount = getDoubleCompat("sgstAmount") ?: 0.0,
            cessAmount = getDoubleCompat("cessAmount") ?: 0.0,
            source = getStringCompat("source") ?: "MANUAL",
            updatedAt = getLongCompat("updatedAt") ?: System.currentTimeMillis(),
            isDeleted = getBooleanCompat("isDeleted") ?: false,
            syncState = SyncState.SYNCED,
            lastSyncError = null,
            lastSyncedAt = System.currentTimeMillis(),
            revision = getLongCompat("revision") ?: 0,
            baseRevision = getLongCompat("revision") ?: 0,
            updatedByUid = getStringCompat("updatedByUid")
        )
    }

    private fun DocumentSnapshot.toBookingAccountingChargeEntity(): BookingAccountingChargeEntity? {
        if (!exists()) return null
        return BookingAccountingChargeEntity(
            remoteId = id,
            hotelRemoteId = getStringCompat("hotelRemoteId") ?: hotelRemoteId,
            bookingRemoteId = getStringCompat("bookingRemoteId") ?: return null,
            chargeType = getStringCompat("chargeType") ?: return null,
            accountBucket = getStringCompat("accountBucket"),
            amount = getDoubleCompat("amount") ?: 0.0,
            description = getStringCompat("description") ?: "",
            reason = getStringCompat("reason"),
            hsnSacCode = getStringCompat("hsnSacCode"),
            gstRatePercent = getDoubleCompat("gstRatePercent") ?: 0.0,
            taxInclusive = getBooleanCompat("taxInclusive") ?: true,
            taxableAmount = getDoubleCompat("taxableAmount"),
            linkedFinalBillId = getStringCompat("linkedFinalBillId"),
            archivedAt = getLongCompat("archivedAt"),
            approvedBy = getStringCompat("approvedBy"),
            createdBy = getStringCompat("createdBy"),
            chargeMillis = getLongCompat("chargeMillis") ?: System.currentTimeMillis(),
            updatedAt = getLongCompat("updatedAt") ?: System.currentTimeMillis(),
            isDeleted = getBooleanCompat("isDeleted") ?: false,
            syncState = SyncState.SYNCED,
            lastSyncError = null,
            lastSyncedAt = System.currentTimeMillis(),
            revision = getLongCompat("revision") ?: 0,
            baseRevision = getLongCompat("revision") ?: 0,
            updatedByUid = getStringCompat("updatedByUid")
        )
    }

    private fun DocumentSnapshot.toManagedPropertyEntity(): ManagedPropertyEntity? {
        if (!exists()) return null
        return ManagedPropertyEntity(
            remoteId = id,
            hotelRemoteId = getStringCompat("hotelRemoteId") ?: hotelRemoteId,
            propertyName = getStringCompat("propertyName") ?: return null,
            legalName = getStringCompat("legalName"),
            gstNumber = getStringCompat("gstNumber"),
            address = getStringCompat("address"),
            phone = getStringCompat("phone"),
            email = getStringCompat("email"),
            invoicePrefix = getStringCompat("invoicePrefix"),
            state = getStringCompat("state"),
            sortOrder = getLongCompat("sortOrder")?.toInt() ?: 0,
            updatedAt = getLongCompat("updatedAt") ?: System.currentTimeMillis(),
            isDeleted = getBooleanCompat("isDeleted") ?: false,
            syncState = SyncState.SYNCED,
            lastSyncError = null,
            lastSyncedAt = System.currentTimeMillis(),
            revision = getLongCompat("revision") ?: 0,
            baseRevision = getLongCompat("revision") ?: 0,
            updatedByUid = getStringCompat("updatedByUid")
        )
    }

    private fun DocumentSnapshot.toFoodMenuItemEntity(): FoodMenuItemEntity? {
        if (!exists()) return null
        return FoodMenuItemEntity(
            remoteId = id,
            hotelRemoteId = getStringCompat("hotelRemoteId") ?: hotelRemoteId,
            propertyRemoteId = getStringCompat("propertyRemoteId"),
            gstCategoryRemoteId = getStringCompat("gstCategoryRemoteId"),
            itemName = getStringCompat("itemName") ?: return null,
            categoryName = getStringCompat("categoryName"),
            price = getDoubleCompat("price") ?: 0.0,
            gstRatePercent = getDoubleCompat("gstRatePercent") ?: 0.0,
            isActive = getBooleanCompat("isActive") ?: true,
            updatedAt = getLongCompat("updatedAt") ?: System.currentTimeMillis(),
            isDeleted = getBooleanCompat("isDeleted") ?: false,
            syncState = SyncState.SYNCED,
            lastSyncError = null,
            lastSyncedAt = System.currentTimeMillis(),
            revision = getLongCompat("revision") ?: 0,
            baseRevision = getLongCompat("revision") ?: 0,
            updatedByUid = getStringCompat("updatedByUid")
        )
    }

    private fun DocumentSnapshot.toServiceMenuItemEntity(): ServiceMenuItemEntity? {
        if (!exists()) return null
        return ServiceMenuItemEntity(
            remoteId = id,
            hotelRemoteId = getStringCompat("hotelRemoteId") ?: hotelRemoteId,
            propertyRemoteId = getStringCompat("propertyRemoteId"),
            serviceName = getStringCompat("serviceName") ?: return null,
            categoryName = getStringCompat("categoryName"),
            description = getStringCompat("description"),
            unitLabel = getStringCompat("unitLabel"),
            price = getDoubleCompat("price") ?: 0.0,
            sacCode = getStringCompat("sacCode"),
            gstRatePercent = getDoubleCompat("gstRatePercent") ?: 18.0,
            taxInclusive = getBooleanCompat("taxInclusive") ?: true,
            isActive = getBooleanCompat("isActive") ?: true,
            sortOrder = getLongCompat("sortOrder")?.toInt() ?: 0,
            updatedAt = getLongCompat("updatedAt") ?: System.currentTimeMillis(),
            isDeleted = getBooleanCompat("isDeleted") ?: false,
            syncState = SyncState.SYNCED,
            lastSyncError = null,
            lastSyncedAt = System.currentTimeMillis(),
            revision = getLongCompat("revision") ?: 0,
            baseRevision = getLongCompat("revision") ?: 0,
            updatedByUid = getStringCompat("updatedByUid")
        )
    }

    private fun DocumentSnapshot.toFoodOrderEntity(): FoodOrderEntity? {
        if (!exists()) return null
        return FoodOrderEntity(
            remoteId = id,
            hotelRemoteId = getStringCompat("hotelRemoteId") ?: hotelRemoteId,
            propertyRemoteId = getStringCompat("propertyRemoteId"),
            bookingRemoteId = getStringCompat("bookingRemoteId"),
            billRemoteId = getStringCompat("billRemoteId"),
            orderNumber = getStringCompat("orderNumber"),
            foodBillingScope = getStringCompat("foodBillingScope") ?: FoodBillingScope.WALK_IN,
            linkedFinalBillId = getStringCompat("linkedFinalBillId"),
            archivedAt = getLongCompat("archivedAt"),
            roomRemoteId = getStringCompat("roomRemoteId"),
            roomName = getStringCompat("roomName"),
            guestName = getStringCompat("guestName") ?: getStringCompat("roomName") ?: "Non Staying Guest",
            orderMillis = getLongCompat("orderMillis") ?: System.currentTimeMillis(),
            status = getStringCompat("status") ?: "OPEN",
            subtotal = getDoubleCompat("subtotal") ?: 0.0,
            discountAmount = getDoubleCompat("discountAmount") ?: 0.0,
            taxableAmount = getDoubleCompat("taxableAmount") ?: 0.0,
            gstAmount = getDoubleCompat("gstAmount") ?: 0.0,
            totalAmount = getDoubleCompat("totalAmount") ?: 0.0,
            notes = getStringCompat("notes"),
            updatedAt = getLongCompat("updatedAt") ?: System.currentTimeMillis(),
            isDeleted = getBooleanCompat("isDeleted") ?: false,
            syncState = SyncState.SYNCED,
            lastSyncError = null,
            lastSyncedAt = System.currentTimeMillis(),
            revision = getLongCompat("revision") ?: 0,
            baseRevision = getLongCompat("revision") ?: 0,
            updatedByUid = getStringCompat("updatedByUid")
        )
    }

    private fun DocumentSnapshot.toFoodOrderItemEntity(): FoodOrderItemEntity? {
        if (!exists()) return null
        return FoodOrderItemEntity(
            remoteId = id,
            hotelRemoteId = getStringCompat("hotelRemoteId") ?: hotelRemoteId,
            orderRemoteId = getStringCompat("orderRemoteId") ?: return null,
            menuItemRemoteId = getStringCompat("menuItemRemoteId"),
            itemName = getStringCompat("itemName") ?: return null,
            quantity = getDoubleCompat("quantity") ?: 0.0,
            unitPrice = getDoubleCompat("unitPrice") ?: 0.0,
            gstRatePercent = getDoubleCompat("gstRatePercent") ?: 0.0,
            lineSubtotal = getDoubleCompat("lineSubtotal") ?: 0.0,
            lineGst = getDoubleCompat("lineGst") ?: 0.0,
            lineTotal = getDoubleCompat("lineTotal") ?: 0.0,
            isCancelled = getBooleanCompat("isCancelled") ?: false,
            updatedAt = getLongCompat("updatedAt") ?: System.currentTimeMillis(),
            isDeleted = getBooleanCompat("isDeleted") ?: false,
            syncState = SyncState.SYNCED,
            lastSyncError = null,
            lastSyncedAt = System.currentTimeMillis(),
            revision = getLongCompat("revision") ?: 0,
            baseRevision = getLongCompat("revision") ?: 0,
            updatedByUid = getStringCompat("updatedByUid"),
            gstCategoryRemoteId = getStringCompat("gstCategoryRemoteId"),
            gstCategoryName = getStringCompat("gstCategoryName"),
            hsnSacCode = getStringCompat("hsnSacCode"),
            cgstRatePercent = getDoubleCompat("cgstRatePercent") ?: 0.0,
            sgstRatePercent = getDoubleCompat("sgstRatePercent") ?: 0.0,
            cessRatePercent = getDoubleCompat("cessRatePercent") ?: 0.0,
        )
    }

    private fun DocumentSnapshot.toFoodGstCategoryEntity(): FoodGstCategoryEntity? {
        if (!exists()) return null
        return FoodGstCategoryEntity(
            remoteId = id,
            hotelRemoteId = getStringCompat("hotelRemoteId") ?: hotelRemoteId,
            categoryName = getStringCompat("categoryName") ?: return null,
            hsnSacCode = getStringCompat("hsnSacCode"),
            gstRatePercent = getDoubleCompat("gstRatePercent") ?: 0.0,
            cgstRatePercent = getDoubleCompat("cgstRatePercent") ?: 0.0,
            sgstRatePercent = getDoubleCompat("sgstRatePercent") ?: 0.0,
            cessRatePercent = getDoubleCompat("cessRatePercent") ?: 0.0,
            taxType = getStringCompat("taxType") ?: "GST",
            itcType = getStringCompat("itcType"),
            description = getStringCompat("description"),
            isDefault = getBooleanCompat("isDefault") ?: false,
            isActive = getBooleanCompat("isActive") ?: true,
            updatedAt = getLongCompat("updatedAt") ?: System.currentTimeMillis(),
            isDeleted = getBooleanCompat("isDeleted") ?: false,
            syncState = SyncState.SYNCED,
            lastSyncError = null,
            lastSyncedAt = System.currentTimeMillis(),
            revision = getLongCompat("revision") ?: 0,
            baseRevision = getLongCompat("revision") ?: 0,
            updatedByUid = getStringCompat("updatedByUid")
        )
    }

    private fun DocumentSnapshot.toFoodBillEntity(): FoodBillEntity? {
        if (!exists()) return null
        return FoodBillEntity(
            remoteId = id,
            hotelRemoteId = getStringCompat("hotelRemoteId") ?: hotelRemoteId,
            propertyRemoteId = getStringCompat("propertyRemoteId"),
            supplierName = getStringCompat("supplierName"),
            supplierGstin = getStringCompat("supplierGstin"),
            supplierAddress = getStringCompat("supplierAddress"),
            supplierPhone = getStringCompat("supplierPhone"),
            supplierState = getStringCompat("supplierState"),
            propertyDisplayName = getStringCompat("propertyDisplayName"),
            billNumber = getStringCompat("billNumber") ?: return null,
            billMillis = getLongCompat("billMillis") ?: System.currentTimeMillis(),
            guestName = getStringCompat("guestName"),
            guestMobile = getStringCompat("guestMobile"),
            guestAddress = getStringCompat("guestAddress"),
            guestGstin = getStringCompat("guestGstin"),
            roomsIncluded = getStringCompat("roomsIncluded") ?: "",
            orderRemoteIds = getStringCompat("orderRemoteIds") ?: "",
            subtotal = getDoubleCompat("subtotal") ?: 0.0,
            discountAmount = getDoubleCompat("discountAmount") ?: 0.0,
            taxableAmount = getDoubleCompat("taxableAmount") ?: 0.0,
            cgstAmount = getDoubleCompat("cgstAmount") ?: 0.0,
            sgstAmount = getDoubleCompat("sgstAmount") ?: 0.0,
            cessAmount = getDoubleCompat("cessAmount") ?: 0.0,
            gstAmount = getDoubleCompat("gstAmount") ?: 0.0,
            grandTotal = getDoubleCompat("grandTotal") ?: 0.0,
            paymentMode = getStringCompat("paymentMode"),
            notes = getStringCompat("notes"),
            status = getStringCompat("status") ?: "ISSUED",
            updatedAt = getLongCompat("updatedAt") ?: System.currentTimeMillis(),
            isDeleted = getBooleanCompat("isDeleted") ?: false,
            syncState = SyncState.SYNCED,
            lastSyncError = null,
            lastSyncedAt = System.currentTimeMillis(),
            revision = getLongCompat("revision") ?: 0,
            baseRevision = getLongCompat("revision") ?: 0,
            updatedByUid = getStringCompat("updatedByUid")
        )
    }

    private fun DocumentSnapshot.toFoodBillItemEntity(): FoodBillItemEntity? {
        if (!exists()) return null
        return FoodBillItemEntity(
            remoteId = id,
            hotelRemoteId = getStringCompat("hotelRemoteId") ?: hotelRemoteId,
            billRemoteId = getStringCompat("billRemoteId") ?: return null,
            orderRemoteId = getStringCompat("orderRemoteId") ?: "",
            orderNumber = getStringCompat("orderNumber"),
            orderMillis = getLongCompat("orderMillis") ?: 0L,
            roomName = getStringCompat("roomName"),
            menuItemRemoteId = getStringCompat("menuItemRemoteId"),
            itemName = getStringCompat("itemName") ?: return null,
            quantity = getDoubleCompat("quantity") ?: 0.0,
            unitPrice = getDoubleCompat("unitPrice") ?: 0.0,
            lineSubtotal = getDoubleCompat("lineSubtotal") ?: 0.0,
            gstCategoryRemoteId = getStringCompat("gstCategoryRemoteId"),
            gstCategoryName = getStringCompat("gstCategoryName"),
            hsnSacCode = getStringCompat("hsnSacCode"),
            gstRatePercent = getDoubleCompat("gstRatePercent") ?: 0.0,
            cgstRatePercent = getDoubleCompat("cgstRatePercent") ?: 0.0,
            sgstRatePercent = getDoubleCompat("sgstRatePercent") ?: 0.0,
            cessRatePercent = getDoubleCompat("cessRatePercent") ?: 0.0,
            taxableAmount = getDoubleCompat("taxableAmount") ?: 0.0,
            cgstAmount = getDoubleCompat("cgstAmount") ?: 0.0,
            sgstAmount = getDoubleCompat("sgstAmount") ?: 0.0,
            cessAmount = getDoubleCompat("cessAmount") ?: 0.0,
            gstAmount = getDoubleCompat("gstAmount") ?: 0.0,
            lineTotal = getDoubleCompat("lineTotal") ?: 0.0,
            updatedAt = getLongCompat("updatedAt") ?: System.currentTimeMillis(),
            isDeleted = getBooleanCompat("isDeleted") ?: false,
            syncState = SyncState.SYNCED,
            lastSyncError = null,
            lastSyncedAt = System.currentTimeMillis(),
            revision = getLongCompat("revision") ?: 0,
            baseRevision = getLongCompat("revision") ?: 0,
            updatedByUid = getStringCompat("updatedByUid")
        )
    }

    private fun DocumentSnapshot.getLongCompat(field: String): Long? {
        return when (val value = get(field)) {
            is Number -> value.toLong()
            is Timestamp -> value.toDate().time
            is String -> value.trim().toLongOrNull()
            else -> null
        }
    }

    private fun DocumentSnapshot.getDoubleCompat(field: String): Double? {
        return when (val value = get(field)) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
    }

    private fun DocumentSnapshot.getBooleanCompat(field: String): Boolean? {
        return when (val value = get(field)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> when (value.trim().lowercase(Locale.ROOT)) {
                "true", "1", "yes", "y" -> true
                "false", "0", "no", "n" -> false
                else -> null
            }
            else -> null
        }
    }

    private fun DocumentSnapshot.getStringCompat(field: String): String? {
        return when (val value = get(field)) {
            null -> null
            is String -> value
            else -> value.toString()
        }
    }
    private fun nextRevisionFor(revision: Long, baseRevision: Long): Long {
        return maxOf(revision, baseRevision) + 1
    }

    private fun Any?.toCloudWriteResult(): CloudWriteResult {
        val data = this as? Map<*, *> ?: return CloudWriteResult(0, currentUid())
        return CloudWriteResult(data.longValue("revision"), data["updatedByUid"]?.toString())
    }

    private fun Any?.toBookingAggregateWriteResult(): BookingAggregateWriteResult {
        val data = this as? Map<*, *>
            ?: return BookingAggregateWriteResult(0, emptyMap(), currentUid())
        return BookingAggregateWriteResult(
            bookingRevision = data.longValue("bookingRevision"),
            financialLineRevisions = data.revisionMap("financialLineRevisions"),
            updatedByUid = data["updatedByUid"]?.toString()
        )
    }

    private fun Any?.toFoodOrderAggregateWriteResult(): FoodOrderAggregateWriteResult {
        val data = this as? Map<*, *>
            ?: return FoodOrderAggregateWriteResult(0, emptyMap(), currentUid())
        return FoodOrderAggregateWriteResult(
            orderRevision = data.longValue("orderRevision"),
            orderItemRevisions = data.revisionMap("orderItemRevisions"),
            updatedByUid = data["updatedByUid"]?.toString()
        )
    }

    private fun Any?.toFoodBillAggregateWriteResult(): FoodBillAggregateWriteResult {
        val data = this as? Map<*, *>
            ?: return FoodBillAggregateWriteResult(0, emptyMap(), emptyMap(), emptyMap(), emptyMap(), currentUid())
        return FoodBillAggregateWriteResult(
            billRevision = data.longValue("billRevision"),
            foodBillItemRevisions = data.revisionMap("foodBillItemRevisions"),
            foodOrderRevisions = data.revisionMap("foodOrderRevisions"),
            foodOrderItemRevisions = data.revisionMap("foodOrderItemRevisions"),
            accountingChargeRevisions = data.revisionMap("accountingChargeRevisions"),
            updatedByUid = data["updatedByUid"]?.toString()
        )
    }

    private fun Map<*, *>.longValue(key: String): Long = when (val value = this[key]) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }

    private fun Map<*, *>.revisionMap(key: String): Map<String, Long> =
        ((this[key] as? Map<*, *>).orEmpty()).mapNotNull { (rawKey, rawValue) ->
            val id = rawKey?.toString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val revision = when (rawValue) {
                is Number -> rawValue.toLong()
                is String -> rawValue.toLongOrNull()
                else -> null
            } ?: return@mapNotNull null
            id to revision
        }.toMap()

    private fun currentUid(): String? {
        return auth.currentUser?.uid
    }

    companion object {
        private const val MAX_AGGREGATE_READS = 450
    }
}

data class CloudWriteResult(
    val revision: Long,
    val updatedByUid: String?
)

data class BookingAggregateWriteResult(
    val bookingRevision: Long,
    val financialLineRevisions: Map<String, Long>,
    val updatedByUid: String?
)

data class FoodBillAggregateWriteResult(
    val billRevision: Long,
    val foodBillItemRevisions: Map<String, Long>,
    val foodOrderRevisions: Map<String, Long>,
    val foodOrderItemRevisions: Map<String, Long>,
    val accountingChargeRevisions: Map<String, Long>,
    val updatedByUid: String?
)

data class FoodOrderAggregateWriteResult(
    val orderRevision: Long,
    val orderItemRevisions: Map<String, Long>,
    val updatedByUid: String?
)

internal fun Throwable.toStructuredSyncException(): Throwable {
    if (this is CodedSyncFailure) return this
    val firebaseError = this as? FirebaseFunctionsException
        ?: return StructuredSyncException(SyncFailureCode.UNKNOWN, message ?: "Sync failed. Please retry.", this)
    val code = when (firebaseError.code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> SyncFailureCode.UNAUTHENTICATED
        FirebaseFunctionsException.Code.PERMISSION_DENIED -> SyncFailureCode.PERMISSION_DENIED
        FirebaseFunctionsException.Code.ABORTED -> SyncFailureCode.STALE_REVISION
        FirebaseFunctionsException.Code.ALREADY_EXISTS -> SyncFailureCode.ALREADY_EXISTS
        FirebaseFunctionsException.Code.INVALID_ARGUMENT -> SyncFailureCode.INVALID_ARGUMENT
        FirebaseFunctionsException.Code.FAILED_PRECONDITION -> SyncFailureCode.FAILED_PRECONDITION
        FirebaseFunctionsException.Code.NOT_FOUND -> SyncFailureCode.NOT_FOUND
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> SyncFailureCode.UNAVAILABLE
        FirebaseFunctionsException.Code.INTERNAL -> SyncFailureCode.INTERNAL
        else -> SyncFailureCode.UNKNOWN
    }
    val serverMessage = firebaseError.message?.trim()?.takeUnless { it.equals("INTERNAL", ignoreCase = true) }
    val meaningfulMessage = serverMessage ?: when (code) {
        SyncFailureCode.INTERNAL -> "The server could not complete this sync operation. Retry; if it repeats, contact support."
        SyncFailureCode.UNAVAILABLE -> "The sync service is temporarily unavailable. Your local data is preserved and will retry."
        else -> "Sync failed (${code.name}). Please retry."
    }
    return StructuredSyncException(code, meaningfulMessage, this)
}

private suspend fun HttpsCallableReference.callSafely(data: Any?): HttpsCallableResult = try {
    call(data).await()
} catch (error: Throwable) {
    throw error.toStructuredSyncException()
}
