package com.example.bookingregister.data.sync

import com.example.bookingregister.booking.domain.BookingStatus
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
            .call(
                mapOf(
                    "hotelId" to hotelRemoteId,
                    "prefix" to prefix,
                    "billMillis" to billMillis
                )
            )
            .await()
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
        val nextRevision = nextRevisionFor(payment.revision, payment.baseRevision)
        hotelDoc.collection("bookingPayments")
            .document(payment.remoteId)
            .set(
                mapOf(
                    "hotelRemoteId" to payment.hotelRemoteId,
                    "bookingRemoteId" to payment.bookingRemoteId,
                    "paymentType" to payment.paymentType,
                    "paymentCategory" to payment.paymentCategory,
                    "amount" to payment.amount,
                    "allocatedStayAmount" to payment.allocatedStayAmount,
                    "allocatedFoodAmount" to payment.allocatedFoodAmount,
                    "allocatedServiceAmount" to payment.allocatedServiceAmount,
                    "allocatedDamageAmount" to payment.allocatedDamageAmount,
                    "unappliedAmount" to payment.unappliedAmount,
                    "paymentMillis" to payment.paymentMillis,
                    "method" to payment.method,
                    "note" to payment.note,
                    "updatedAt" to payment.updatedAt,
                    "isDeleted" to payment.isDeleted,
                    "revision" to nextRevision,
                    "updatedByUid" to currentUid(),
                    "serverUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        return CloudWriteResult(nextRevision, currentUid())
    }

    suspend fun pushFinancialLine(line: BookingFinancialLineEntity): CloudWriteResult {
        val nextRevision = nextRevisionFor(line.revision, line.baseRevision)
        hotelDoc.collection("bookingFinancialLines")
            .document(line.remoteId)
            .set(
                mapOf(
                    "hotelRemoteId" to line.hotelRemoteId,
                    "bookingRemoteId" to line.bookingRemoteId,
                    "roomRemoteId" to line.roomRemoteId,
                    "propertyRemoteId" to line.propertyRemoteId,
                    "businessDateMillis" to line.businessDateMillis,
                    "grossAmount" to line.grossAmount,
                    "taxableAmount" to line.taxableAmount,
                    "gstRatePercent" to line.gstRatePercent,
                    "gstAmount" to line.gstAmount,
                    "hsnSacCode" to line.hsnSacCode,
                    "slabRemoteId" to line.slabRemoteId,
                    "slabName" to line.slabName,
                    "cgstRatePercent" to line.cgstRatePercent,
                    "sgstRatePercent" to line.sgstRatePercent,
                    "cessRatePercent" to line.cessRatePercent,
                    "cgstAmount" to line.cgstAmount,
                    "sgstAmount" to line.sgstAmount,
                    "cessAmount" to line.cessAmount,
                    "source" to line.source,
                    "updatedAt" to line.updatedAt,
                    "isDeleted" to line.isDeleted,
                    "revision" to nextRevision,
                    "updatedByUid" to currentUid(),
                    "serverUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        return CloudWriteResult(nextRevision, currentUid())
    }

    suspend fun pushAccountingCharge(charge: BookingAccountingChargeEntity): CloudWriteResult {
        val nextRevision = nextRevisionFor(charge.revision, charge.baseRevision)
        hotelDoc.collection("bookingAccountingCharges")
            .document(charge.remoteId)
            .set(
                mapOf(
                    "hotelRemoteId" to charge.hotelRemoteId,
                    "bookingRemoteId" to charge.bookingRemoteId,
                    "chargeType" to charge.chargeType,
                    "accountBucket" to charge.accountBucket,
                    "amount" to charge.amount,
                    "description" to charge.description,
                    "reason" to charge.reason,
                    "hsnSacCode" to charge.hsnSacCode,
                    "gstRatePercent" to charge.gstRatePercent,
                    "taxInclusive" to charge.taxInclusive,
                    "taxableAmount" to charge.taxableAmount,
                    "linkedFinalBillId" to charge.linkedFinalBillId,
                    "archivedAt" to charge.archivedAt,
                    "approvedBy" to charge.approvedBy,
                    "createdBy" to charge.createdBy,
                    "chargeMillis" to charge.chargeMillis,
                    "updatedAt" to charge.updatedAt,
                    "isDeleted" to charge.isDeleted,
                    "revision" to nextRevision,
                    "updatedByUid" to currentUid(),
                    "serverUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        return CloudWriteResult(nextRevision, currentUid())
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

    suspend fun pushFoodOrder(order: FoodOrderEntity): CloudWriteResult {
        val nextRevision = nextRevisionFor(order.revision, order.baseRevision)
        hotelDoc.collection("foodOrders")
            .document(order.remoteId)
            .set(
                mapOf(
                    "hotelRemoteId" to order.hotelRemoteId,
                    "propertyRemoteId" to order.propertyRemoteId,
                    "bookingRemoteId" to order.bookingRemoteId,
                    "billRemoteId" to order.billRemoteId,
                    "orderNumber" to order.orderNumber,
                    "foodBillingScope" to order.foodBillingScope,
                    "linkedFinalBillId" to order.linkedFinalBillId,
                    "archivedAt" to order.archivedAt,
                    "roomRemoteId" to order.roomRemoteId,
                    "roomName" to order.roomName,
                    "guestName" to order.guestName,
                    "orderMillis" to order.orderMillis,
                    "status" to order.status,
                    "subtotal" to order.subtotal,
                    "discountAmount" to order.discountAmount,
                    "taxableAmount" to order.taxableAmount,
                    "gstAmount" to order.gstAmount,
                    "totalAmount" to order.totalAmount,
                    "notes" to order.notes,
                    "updatedAt" to order.updatedAt,
                    "isDeleted" to order.isDeleted,
                    "revision" to nextRevision,
                    "updatedByUid" to currentUid(),
                    "serverUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        return CloudWriteResult(nextRevision, currentUid())
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

    suspend fun pushFoodBill(bill: FoodBillEntity): CloudWriteResult {
        val nextRevision = nextRevisionFor(bill.revision, bill.baseRevision)
        hotelDoc.collection("foodBills")
            .document(bill.remoteId)
            .set(
                mapOf(
                    "hotelRemoteId" to bill.hotelRemoteId,
                    "propertyRemoteId" to bill.propertyRemoteId,
                    "supplierName" to bill.supplierName,
                    "supplierGstin" to bill.supplierGstin,
                    "supplierAddress" to bill.supplierAddress,
                    "supplierPhone" to bill.supplierPhone,
                    "supplierState" to bill.supplierState,
                    "propertyDisplayName" to bill.propertyDisplayName,
                    "billNumber" to bill.billNumber,
                    "billMillis" to bill.billMillis,
                    "guestName" to bill.guestName,
                    "guestMobile" to bill.guestMobile,
                    "guestAddress" to bill.guestAddress,
                    "guestGstin" to bill.guestGstin,
                    "roomsIncluded" to bill.roomsIncluded,
                    "orderRemoteIds" to bill.orderRemoteIds,
                    "subtotal" to bill.subtotal,
                    "discountAmount" to bill.discountAmount,
                    "taxableAmount" to bill.taxableAmount,
                    "cgstAmount" to bill.cgstAmount,
                    "sgstAmount" to bill.sgstAmount,
                    "cessAmount" to bill.cessAmount,
                    "gstAmount" to bill.gstAmount,
                    "grandTotal" to bill.grandTotal,
                    "paymentMode" to bill.paymentMode,
                    "notes" to bill.notes,
                    "status" to bill.status,
                    "updatedAt" to bill.updatedAt,
                    "isDeleted" to bill.isDeleted,
                    "revision" to nextRevision,
                    "updatedByUid" to currentUid(),
                    "serverUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        return CloudWriteResult(nextRevision, currentUid())
    }

    suspend fun pushFoodBillItem(item: FoodBillItemEntity): CloudWriteResult {
        val nextRevision = nextRevisionFor(item.revision, item.baseRevision)
        hotelDoc.collection("foodBillItems")
            .document(item.remoteId)
            .set(
                mapOf(
                    "hotelRemoteId" to item.hotelRemoteId,
                    "billRemoteId" to item.billRemoteId,
                    "orderRemoteId" to item.orderRemoteId,
                    "orderNumber" to item.orderNumber,
                    "orderMillis" to item.orderMillis,
                    "roomName" to item.roomName,
                    "menuItemRemoteId" to item.menuItemRemoteId,
                    "itemName" to item.itemName,
                    "quantity" to item.quantity,
                    "unitPrice" to item.unitPrice,
                    "lineSubtotal" to item.lineSubtotal,
                    "gstCategoryRemoteId" to item.gstCategoryRemoteId,
                    "gstCategoryName" to item.gstCategoryName,
                    "hsnSacCode" to item.hsnSacCode,
                    "gstRatePercent" to item.gstRatePercent,
                    "cgstRatePercent" to item.cgstRatePercent,
                    "sgstRatePercent" to item.sgstRatePercent,
                    "cessRatePercent" to item.cessRatePercent,
                    "taxableAmount" to item.taxableAmount,
                    "cgstAmount" to item.cgstAmount,
                    "sgstAmount" to item.sgstAmount,
                    "cessAmount" to item.cessAmount,
                    "gstAmount" to item.gstAmount,
                    "lineTotal" to item.lineTotal,
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

    suspend fun pushFoodOrderItem(item: FoodOrderItemEntity): CloudWriteResult {
        val nextRevision = nextRevisionFor(item.revision, item.baseRevision)
        hotelDoc.collection("foodOrderItems")
            .document(item.remoteId)
            .set(
                mapOf(
                    "hotelRemoteId" to item.hotelRemoteId,
                    "orderRemoteId" to item.orderRemoteId,
                    "menuItemRemoteId" to item.menuItemRemoteId,
                    "itemName" to item.itemName,
                    "quantity" to item.quantity,
                    "unitPrice" to item.unitPrice,
                    "gstRatePercent" to item.gstRatePercent,
                    "lineSubtotal" to item.lineSubtotal,
                    "lineGst" to item.lineGst,
                    "lineTotal" to item.lineTotal,
                    "isCancelled" to item.isCancelled,
                    "updatedAt" to item.updatedAt,
                    "isDeleted" to item.isDeleted,
                    "revision" to nextRevision,
                    "updatedByUid" to currentUid(),
                    "serverUpdatedAt" to FieldValue.serverTimestamp(),
                    "gstCategoryRemoteId" to item.gstCategoryRemoteId,
                    "gstCategoryName" to item.gstCategoryName,
                    "hsnSacCode" to item.hsnSacCode,
                    "cgstRatePercent" to item.cgstRatePercent,
                    "sgstRatePercent" to item.sgstRatePercent,
                    "cessRatePercent" to item.cessRatePercent,
                )
            )
            .await()
        return CloudWriteResult(nextRevision, currentUid())
    }
    suspend fun pushBooking(booking: BookingEntity): CloudWriteResult {
        val normalized = booking.withCalculatedPayment()
        return pushBookingDirect(normalized)
    }

    /** Writes the operational booking, its room locks, and all room-night accounting rows atomically. */
    suspend fun pushBookingAggregate(
        booking: BookingEntity,
        financialLines: List<BookingFinancialLineEntity>
    ): BookingAggregateWriteResult {
        val normalized = booking.withCalculatedPayment()
        val bookingDoc = hotelDoc.collection("bookings").document(normalized.remoteId)
        val newLockIds = lockIdsFor(normalized)
        require(financialLines.size + newLockIds.size <= MAX_AGGREGATE_READS) {
            "Booking has too many room-night rows for one atomic sync"
        }
        require(financialLines.all { it.bookingRemoteId == booking.remoteId }) {
            "Financial line belongs to another booking"
        }
        return firestore.runTransaction { transaction ->
            val existingBooking = transaction.get(bookingDoc)
            val remoteRevision = existingBooking.getLongCompat("revision") ?: 0
            if (existingBooking.exists() && remoteRevision != normalized.baseRevision) {
                throw BookingConflictException("This booking was changed on another device. Refresh before saving.")
            }

            val lockSnapshots = newLockIds.associateWith { lockId ->
                transaction.get(hotelDoc.collection("bookingLocks").document(lockId))
            }
            lockSnapshots.forEach { (_, snapshot) ->
                val lockedBy = snapshot.getStringCompat("bookingRemoteId")
                val lockDeleted = snapshot.getBooleanCompat("isDeleted") ?: false
                if (snapshot.exists() && !lockDeleted && lockedBy != normalized.remoteId) {
                    throw BookingConflictException("Selected room is already booked for these dates")
                }
            }

            val lineSnapshots = financialLines.associateWith { line ->
                transaction.get(hotelDoc.collection("bookingFinancialLines").document(line.remoteId))
            }
            lineSnapshots.forEach { (line, snapshot) ->
                val lineRemoteRevision = snapshot.getLongCompat("revision") ?: 0
                if (snapshot.exists() && lineRemoteRevision != line.baseRevision) {
                    throw BookingConflictException("Room-night accounting changed on another device. Refresh before saving.")
                }
            }

            val oldLockIds = if (existingBooking.exists()) {
                val oldRoomIds = (existingBooking.get("roomRemoteIds") as? List<*>)
                    ?.mapNotNull { it as? String }.orEmpty()
                lockIdsFor(
                    roomRemoteIds = oldRoomIds,
                    checkInMillis = existingBooking.getLongCompat("checkInMillis") ?: normalized.checkInMillis,
                    checkOutMillis = existingBooking.getLongCompat("checkOutMillis") ?: normalized.checkOutMillis
                )
            } else emptySet()

            val uid = currentUid()
            val nextBookingRevision = remoteRevision + 1
            oldLockIds.minus(newLockIds).forEach { lockId ->
                transaction.delete(hotelDoc.collection("bookingLocks").document(lockId))
            }
            transaction.set(bookingDoc, normalized.toCloudMap(nextBookingRevision, uid))
            newLockIds.forEach { lockId ->
                val parts = lockId.split("_")
                transaction.set(
                    hotelDoc.collection("bookingLocks").document(lockId),
                    mapOf(
                        "hotelRemoteId" to hotelRemoteId,
                        "bookingRemoteId" to normalized.remoteId,
                        "roomRemoteId" to parts.dropLast(1).joinToString("_"),
                        "dateMillis" to (parts.lastOrNull()?.toLongOrNull() ?: 0L),
                        "isDeleted" to false,
                        "updatedAt" to normalized.updatedAt,
                        "updatedByUid" to uid,
                        "serverUpdatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val lineRevisions = lineSnapshots.mapValues { (_, snapshot) ->
                (snapshot.getLongCompat("revision") ?: 0) + 1
            }
            lineRevisions.forEach { (line, revision) ->
                transaction.set(
                    hotelDoc.collection("bookingFinancialLines").document(line.remoteId),
                    line.toCloudMap(revision, uid)
                )
            }
            BookingAggregateWriteResult(nextBookingRevision, lineRevisions.mapKeys { it.key.remoteId }, uid)
        }.await()
    }

    private suspend fun pushBookingDirect(booking: BookingEntity): CloudWriteResult {
        val normalized = booking.withCalculatedPayment()
        val bookingDoc = hotelDoc.collection("bookings").document(normalized.remoteId)
        val newLockIds = lockIdsFor(normalized)
        val result = firestore.runTransaction { transaction ->
            val existingBooking = transaction.get(bookingDoc)
            val remoteRevision = existingBooking.getLongCompat("revision") ?: 0
            if (existingBooking.exists() && remoteRevision != normalized.baseRevision) {
                throw BookingConflictException("This booking was changed on another device. Refresh before saving.")
            }

            newLockIds.forEach { lockId ->
                val lockDoc = hotelDoc.collection("bookingLocks").document(lockId)
                val lockSnapshot = transaction.get(lockDoc)
                val lockedBy = lockSnapshot.getStringCompat("bookingRemoteId")
                val lockDeleted = lockSnapshot.getBooleanCompat("isDeleted") ?: false
                if (lockSnapshot.exists() && !lockDeleted && lockedBy != normalized.remoteId) {
                    throw BookingConflictException("Selected room is already booked for these dates")
                }
            }

            val oldLockIds = if (existingBooking.exists()) {
                val oldRoomIds = (existingBooking.get("roomRemoteIds") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: emptyList()
                lockIdsFor(
                    roomRemoteIds = oldRoomIds,
                    checkInMillis = existingBooking.getLongCompat("checkInMillis") ?: normalized.checkInMillis,
                    checkOutMillis = existingBooking.getLongCompat("checkOutMillis") ?: normalized.checkOutMillis
                )
            } else {
                emptySet()
            }

            val nextRevision = remoteRevision + 1
            val uid = currentUid()
            oldLockIds.minus(newLockIds).forEach { lockId ->
                transaction.delete(hotelDoc.collection("bookingLocks").document(lockId))
            }
            transaction.set(bookingDoc, normalized.toCloudMap(nextRevision, uid))
            newLockIds.forEach { lockId ->
                val parts = lockId.split("_")
                val roomId = parts.dropLast(1).joinToString("_")
                val dateMillis = parts.lastOrNull()?.toLongOrNull() ?: 0L
                transaction.set(
                    hotelDoc.collection("bookingLocks").document(lockId),
                    mapOf(
                        "hotelRemoteId" to hotelRemoteId,
                        "bookingRemoteId" to normalized.remoteId,
                        "roomRemoteId" to roomId,
                        "dateMillis" to dateMillis,
                        "isDeleted" to false,
                        "updatedAt" to normalized.updatedAt,
                        "updatedByUid" to uid,
                        "serverUpdatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            CloudWriteResult(nextRevision, uid)
        }.await()
        return result
    }

    suspend fun fetchBooking(remoteId: String): BookingEntity? {
        return hotelDoc.collection("bookings")
            .document(remoteId)
            .get()
            .await()
            .toBookingEntity()
    }

    suspend fun repairLegacyBookingLifecycleFields(limit: Long = 1_000L): Int {
        val snapshot = hotelDoc.collection("bookings")
            .limit(limit)
            .get()
            .await()

        val uid = currentUid()
        val now = System.currentTimeMillis()
        var repairedCount = 0
        var batch = firestore.batch()
        var batchSize = 0

        suspend fun commitBatchIfNeeded(force: Boolean = false) {
            if (batchSize == 0 || (!force && batchSize < 450)) return
            batch.commit().await()
            batch = firestore.batch()
            batchSize = 0
        }

        snapshot.documents.forEach { doc ->
            if (!doc.exists()) return@forEach
            val hasLifecycleStatus = doc.contains("bookingStatus")
            if (hasLifecycleStatus) return@forEach

            batch.set(
                doc.reference,
                mapOf(
                    "bookingStatus" to BookingStatus.RESERVED,
                    "actualCheckInAt" to null,
                    "actualCheckOutAt" to null,
                    "checkoutNote" to null,
                    "reopenNote" to null,
                    "reopenedAt" to null,
                    "updatedAt" to now,
                    "updatedByUid" to uid,
                    "serverUpdatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            repairedCount += 1
            batchSize += 1
            commitBatchIfNeeded()
        }

        commitBatchIfNeeded(force = true)
        return repairedCount
    }

    suspend fun deleteBooking(booking: BookingEntity): CloudWriteResult {
        return runCatching { deleteBookingOnServer(booking.withCalculatedPayment()) }
            .getOrElse { error ->
                if (shouldUseDirectBookingFallback(error)) {
                    deleteBookingDirect(booking)
                } else {
                    throw error
                }
            }
    }

    private suspend fun deleteBookingDirect(booking: BookingEntity): CloudWriteResult {
        val bookingDoc = hotelDoc.collection("bookings").document(booking.remoteId)
        val result = firestore.runTransaction { transaction ->
            val existingBooking = transaction.get(bookingDoc)
            val remoteRevision = existingBooking.getLongCompat("revision") ?: 0
            if (existingBooking.exists() && remoteRevision != booking.baseRevision) {
                throw BookingConflictException("This booking was changed on another device. Refresh before deleting.")
            }
            val nextRevision = remoteRevision + 1
            val uid = currentUid()
            val oldRoomIds = if (existingBooking.exists()) {
                (existingBooking.get("roomRemoteIds") as? List<*>)?.mapNotNull { it as? String }
                    ?: booking.roomRemoteIds
            } else {
                booking.roomRemoteIds
            }
            lockIdsFor(
                roomRemoteIds = oldRoomIds,
                checkInMillis = existingBooking.getLongCompat("checkInMillis") ?: booking.checkInMillis,
                checkOutMillis = existingBooking.getLongCompat("checkOutMillis") ?: booking.checkOutMillis
            ).forEach { lockId ->
                transaction.delete(hotelDoc.collection("bookingLocks").document(lockId))
            }
            transaction.set(bookingDoc, booking.copy(isDeleted = true).toCloudMap(nextRevision, uid))
            CloudWriteResult(nextRevision, uid)
        }.await()
        return result
    }

    private suspend fun saveBookingOnServer(booking: BookingEntity): CloudWriteResult {
        val response = functions
            .getHttpsCallable("saveBookingServer")
            .call(
                mapOf(
                    "hotelId" to hotelRemoteId,
                    "booking" to booking.toCallableMap()
                )
            )
            .await()

        return response.data.toCloudWriteResult()
    }

    private suspend fun deleteBookingOnServer(booking: BookingEntity): CloudWriteResult {
        val response = functions
            .getHttpsCallable("deleteBookingServer")
            .call(
                mapOf(
                    "hotelId" to hotelRemoteId,
                    "bookingRemoteId" to booking.remoteId,
                    "baseRevision" to (booking.baseRevision.takeIf { it > 0 } ?: booking.revision),
                    "roomRemoteIds" to booking.roomRemoteIds,
                    "checkInMillis" to booking.checkInMillis,
                    "checkOutMillis" to booking.checkOutMillis
                )
            )
            .await()

        return response.data.toCloudWriteResult()
    }

    private fun Any?.toCloudWriteResult(): CloudWriteResult {
        val data = this as? Map<*, *> ?: return CloudWriteResult(0, currentUid())
        val revision = when (val value = data["revision"]) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
        val updatedByUid = data["updatedByUid"]?.toString()
        return CloudWriteResult(revision, updatedByUid)
    }

    private fun shouldUseDirectBookingFallback(error: Throwable): Boolean {
        val functionsError = error as? FirebaseFunctionsException ?: return false
        return functionsError.code in setOf(
            FirebaseFunctionsException.Code.NOT_FOUND,
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            FirebaseFunctionsException.Code.INTERNAL,
            FirebaseFunctionsException.Code.UNKNOWN
        )
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
            "notes" to normalized.notes,
            "updatedAt" to normalized.updatedAt,
            "isDeleted" to normalized.isDeleted
        )
    }

    private fun DocumentSnapshot.toBookingEntity(): BookingEntity? {
        if (!exists()) return null

        val roomIds = (get("roomRemoteIds") as? List<*>)
            ?.mapNotNull { it as? String }
            ?: emptyList()

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
            bookingStatus = getStringCompat("bookingStatus")?.ifBlank { BookingStatus.RESERVED }
                ?: BookingStatus.RESERVED,
            actualCheckInAt = getLongCompat("actualCheckInAt"),
            actualCheckOutAt = getLongCompat("actualCheckOutAt"),
            checkoutNote = getStringCompat("checkoutNote"),
            reopenNote = getStringCompat("reopenNote"),
            reopenedAt = getLongCompat("reopenedAt"),
            notes = getStringCompat("notes"),
            updatedAt = getLongCompat("updatedAt") ?: System.currentTimeMillis(),
            isDeleted = getBooleanCompat("isDeleted") ?: false,
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
    private fun lockIdsFor(booking: BookingEntity): Set<String> {
        return lockIdsFor(booking.roomRemoteIds, booking.checkInMillis, booking.checkOutMillis)
    }

    private fun lockIdsFor(
        roomRemoteIds: List<String>,
        checkInMillis: Long,
        checkOutMillis: Long
    ): Set<String> {
        val start = startOfDay(checkInMillis)
        val end = startOfDay(checkOutMillis)
        if (end <= start) return emptySet()
        val ids = linkedSetOf<String>()
        roomRemoteIds.forEach { roomId ->
            var day = start
            while (day < end) {
                ids.add("${roomId}_$day")
                day += DAY_MILLIS
            }
        }
        return ids
    }

    private fun startOfDay(millis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun nextRevisionFor(revision: Long, baseRevision: Long): Long {
        return maxOf(revision, baseRevision) + 1
    }

    private fun currentUid(): String? {
        return auth.currentUser?.uid
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
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

class BookingConflictException(message: String) : Exception(message)
