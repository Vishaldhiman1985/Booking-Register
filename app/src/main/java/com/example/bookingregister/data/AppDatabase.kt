package com.example.bookingregister.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.bookingregister.data.converters.AppConverters
import com.example.bookingregister.data.dao.BookingAccountingChargeDao
import com.example.bookingregister.data.dao.BookingDao
import com.example.bookingregister.data.dao.BookingFinancialLineDao
import com.example.bookingregister.data.dao.BookingPaymentDao
import com.example.bookingregister.data.dao.BookingSourceDao
import com.example.bookingregister.data.dao.BookingSyncOutboxDao
import com.example.bookingregister.data.dao.FoodBillDao
import com.example.bookingregister.data.dao.FoodBillItemDao
import com.example.bookingregister.data.dao.FoodGstCategoryDao
import com.example.bookingregister.data.dao.FoodMenuItemDao
import com.example.bookingregister.data.dao.FoodOrderDao
import com.example.bookingregister.data.dao.FoodOrderItemDao
import com.example.bookingregister.data.dao.HotelDao
import com.example.bookingregister.data.dao.ManagedPropertyDao
import com.example.bookingregister.data.dao.RoomCategoryDao
import com.example.bookingregister.data.dao.RoomDao
import com.example.bookingregister.data.dao.ServiceMenuItemDao
import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingSourceEntity
import com.example.bookingregister.data.entities.BookingSyncOutboxEntity
import com.example.bookingregister.data.entities.FoodBillEntity
import com.example.bookingregister.data.entities.FoodBillItemEntity
import com.example.bookingregister.data.entities.FoodGstCategoryEntity
import com.example.bookingregister.data.entities.FoodMenuItemEntity
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderItemEntity
import com.example.bookingregister.data.entities.HotelEntity
import com.example.bookingregister.data.entities.ManagedPropertyEntity
import com.example.bookingregister.data.entities.RoomCategoryEntity
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.data.entities.ServiceMenuItemEntity
import com.example.bookingregister.data.dao.RoomGstSlabDao
import com.example.bookingregister.data.entities.RoomGstSlabEntity
@Database(
    entities = [
        HotelEntity::class,
        ManagedPropertyEntity::class,
        RoomCategoryEntity::class,
        RoomEntity::class,
        BookingEntity::class,
        BookingAccountingChargeEntity::class,
        BookingFinancialLineEntity::class,
        BookingPaymentEntity::class,
        BookingSourceEntity::class,
        FoodGstCategoryEntity::class,
        FoodMenuItemEntity::class,
        FoodOrderEntity::class,
        FoodOrderItemEntity::class,
        FoodBillEntity::class,
        FoodBillItemEntity::class,
        ServiceMenuItemEntity::class,
        RoomGstSlabEntity::class,
        BookingSyncOutboxEntity::class,
    ],
    version = 33,
    exportSchema = true
)
@TypeConverters(AppConverters::class)
@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
abstract class AppDatabase : RoomDatabase() {

    abstract fun roomCategoryDao(): RoomCategoryDao
    abstract fun roomDao(): RoomDao
    abstract fun bookingDao(): BookingDao
    abstract fun bookingAccountingChargeDao(): BookingAccountingChargeDao
    abstract fun bookingFinancialLineDao(): BookingFinancialLineDao
    abstract fun bookingPaymentDao(): BookingPaymentDao
    abstract fun bookingSourceDao(): BookingSourceDao
    abstract fun bookingSyncOutboxDao(): BookingSyncOutboxDao
    abstract fun foodGstCategoryDao(): FoodGstCategoryDao
    abstract fun foodMenuItemDao(): FoodMenuItemDao
    abstract fun foodOrderDao(): FoodOrderDao
    abstract fun foodOrderItemDao(): FoodOrderItemDao
    abstract fun foodBillDao(): FoodBillDao
    abstract fun foodBillItemDao(): FoodBillItemDao
    abstract fun serviceMenuItemDao(): ServiceMenuItemDao
    abstract fun hotelDao(): HotelDao
    abstract fun managedPropertyDao(): ManagedPropertyDao
    abstract fun roomGstSlabDao(): RoomGstSlabDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "booking_register.db"
                )
                    .addMigrations(*allMigrations())
                    .build()

                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureSyncColumns(database)
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
                seedCategoriesFromRooms(database)
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
                seedLegacyPaymentsFromBookings(database)
            }
        }
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
            }
        }
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
                seedDefaultSources(database)
            }
        }
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
            }
        }
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
            }
        }
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
            }
        }
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureFoodBillingSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
            }
        }
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureFoodBillingSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
                seedDefaultFoodGstCategories(database)
            }
        }
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureFoodBillingSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
            }
        }
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureFoodBillingSchema(database)
                ensurePaymentAllocationSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureFoodBillingSchema(database)
                ensurePaymentAllocationSchema(database)
                ensureColumn(database, "booking_payments", "unappliedAmount", "REAL NOT NULL DEFAULT 0.0")
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureFoodBillingSchema(database)
                ensureAccountingChargeSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureFoodBillingSchema(database)
                ensureAccountingChargeSchema(database)
                ensureServiceMenuSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureFoodBillingSchema(database)
                ensureAccountingChargeSchema(database)
                ensureServiceMenuSchema(database)
                ensureBillSupplierSnapshotSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureBookingSourcePropertySchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureCoreSchema(database)
                ensureAccountingChargeSchema(database)
                ensureSyncColumns(database)
                ensureRevisionColumns(database)
            }
        }

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureAccountingChargeSchema(database)
            }
        }

        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureColumn(database, "food_order_items", "gstCategoryRemoteId", "TEXT")
                ensureColumn(database, "food_order_items", "gstCategoryName", "TEXT")
                ensureColumn(database, "food_order_items", "hsnSacCode", "TEXT")
                ensureColumn(database, "food_order_items", "cgstRatePercent", "REAL NOT NULL DEFAULT 0.0")
                ensureColumn(database, "food_order_items", "sgstRatePercent", "REAL NOT NULL DEFAULT 0.0")
                ensureColumn(database, "food_order_items", "cessRatePercent", "REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureColumn(database, "food_menu_items", "gstCategoryName", "TEXT")
                ensureColumn(database, "food_menu_items", "hsnSacCode", "TEXT")
                ensureColumn(database, "food_menu_items", "cgstRatePercent", "REAL NOT NULL DEFAULT 0.0")
                ensureColumn(database, "food_menu_items", "sgstRatePercent", "REAL NOT NULL DEFAULT 0.0")
                ensureColumn(database, "food_menu_items", "cessRatePercent", "REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS room_gst_slabs (
                localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                remoteId TEXT NOT NULL,
                hotelRemoteId TEXT NOT NULL,
                slabName TEXT NOT NULL,
                minGrossAmount REAL NOT NULL,
                maxGrossAmount REAL,
                gstRatePercent REAL NOT NULL,
                cgstRatePercent REAL NOT NULL,
                sgstRatePercent REAL NOT NULL,
                cessRatePercent REAL NOT NULL,
                hsnSacCode TEXT NOT NULL,
                notificationRef TEXT,
                effectiveFromMillis INTEGER NOT NULL,
                effectiveToMillis INTEGER,
                isActive INTEGER NOT NULL,
                isDeleted INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                syncState TEXT NOT NULL,
                lastSyncError TEXT,
                lastSyncedAt INTEGER,
                revision INTEGER NOT NULL,
                baseRevision INTEGER NOT NULL,
                updatedByUid TEXT
            )
            """.trimIndent()
                )

                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_room_gst_slabs_remoteId ON room_gst_slabs(remoteId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_room_gst_slabs_hotelRemoteId ON room_gst_slabs(hotelRemoteId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_room_gst_slabs_effectiveFromMillis ON room_gst_slabs(effectiveFromMillis)"
                )
            }
        }

        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureColumn(database, "booking_financial_lines", "hsnSacCode", "TEXT")
                ensureColumn(database, "booking_financial_lines", "slabRemoteId", "TEXT")
                ensureColumn(database, "booking_financial_lines", "slabName", "TEXT")

                ensureColumn(database, "booking_financial_lines", "cgstRatePercent", "REAL NOT NULL DEFAULT 0.0")
                ensureColumn(database, "booking_financial_lines", "sgstRatePercent", "REAL NOT NULL DEFAULT 0.0")
                ensureColumn(database, "booking_financial_lines", "cessRatePercent", "REAL NOT NULL DEFAULT 0.0")

                ensureColumn(database, "booking_financial_lines", "cgstAmount", "REAL NOT NULL DEFAULT 0.0")
                ensureColumn(database, "booking_financial_lines", "sgstAmount", "REAL NOT NULL DEFAULT 0.0")
                ensureColumn(database, "booking_financial_lines", "cessAmount", "REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureColumn(database, "booking_accounting_charges", "linkedFinalBillId", "TEXT")
                ensureColumn(database, "booking_accounting_charges", "archivedAt", "INTEGER")
            }
        }
        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureColumn(database, "booking_payments", "allocatedDamageAmount", "REAL NOT NULL DEFAULT 0.0")
            }
        }
        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureColumn(database, "bookings", "pricingStatus", "TEXT NOT NULL DEFAULT 'CONFIRMED'")
            }
        }
        private val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureBookingSyncOutboxSchema(database)
            }
        }
        fun allMigrations(): Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
            MIGRATION_20_21,
            MIGRATION_21_22,
            MIGRATION_22_23,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26,
            MIGRATION_26_27,
            MIGRATION_27_28,
            MIGRATION_28_29,
            MIGRATION_29_30,
            MIGRATION_30_31,
            MIGRATION_31_32,
            MIGRATION_32_33
        )

        private fun ensureBookingSyncOutboxSchema(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS booking_sync_outbox (
                    operationId TEXT NOT NULL PRIMARY KEY,
                    hotelRemoteId TEXT NOT NULL,
                    bookingRemoteId TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    attemptCount INTEGER NOT NULL DEFAULT 0,
                    lastError TEXT
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_sync_outbox_hotelRemoteId ON booking_sync_outbox(hotelRemoteId)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_booking_sync_outbox_bookingRemoteId ON booking_sync_outbox(bookingRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_sync_outbox_hotelRemoteId_createdAt ON booking_sync_outbox(hotelRemoteId, createdAt)")
        }

        private fun ensureBookingSourcePropertySchema(database: SupportSQLiteDatabase) {
            ensureColumn(database, "booking_sources", "propertyRemoteId", "TEXT")
            database.execSQL("DROP INDEX IF EXISTS index_booking_sources_hotelRemoteId_sourceName")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_sources_propertyRemoteId ON booking_sources(propertyRemoteId)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_booking_sources_hotelRemoteId_propertyRemoteId_sourceName ON booking_sources(hotelRemoteId, propertyRemoteId, sourceName)")
        }

        private fun ensureBillSupplierSnapshotSchema(database: SupportSQLiteDatabase) {
            ensureColumn(database, "food_bills", "supplierName", "TEXT")
            ensureColumn(database, "food_bills", "supplierGstin", "TEXT")
            ensureColumn(database, "food_bills", "supplierAddress", "TEXT")
            ensureColumn(database, "food_bills", "supplierPhone", "TEXT")
            ensureColumn(database, "food_bills", "supplierState", "TEXT")
            ensureColumn(database, "food_bills", "propertyDisplayName", "TEXT")
        }

        private fun ensureServiceMenuSchema(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS service_menu_items (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    remoteId TEXT NOT NULL,
                    hotelRemoteId TEXT NOT NULL,
                    propertyRemoteId TEXT,
                    serviceName TEXT NOT NULL,
                    categoryName TEXT,
                    description TEXT,
                    unitLabel TEXT,
                    price REAL NOT NULL DEFAULT 0.0,
                    sacCode TEXT,
                    gstRatePercent REAL NOT NULL DEFAULT 18.0,
                    taxInclusive INTEGER NOT NULL DEFAULT 1,
                    isActive INTEGER NOT NULL DEFAULT 1,
                    sortOrder INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_service_menu_items_remoteId ON service_menu_items(remoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_service_menu_items_hotelRemoteId ON service_menu_items(hotelRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_service_menu_items_propertyRemoteId ON service_menu_items(propertyRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_service_menu_items_hotelRemoteId_serviceName ON service_menu_items(hotelRemoteId, serviceName)")
        }
        private fun ensurePaymentAllocationSchema(database: SupportSQLiteDatabase) {
            ensureColumn(database, "booking_payments", "paymentCategory", "TEXT NOT NULL DEFAULT 'AUTO'")
            ensureColumn(database, "booking_payments", "allocatedStayAmount", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "booking_payments", "allocatedFoodAmount", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "booking_payments", "allocatedServiceAmount", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "booking_payments", "allocatedDamageAmount", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "booking_payments", "unappliedAmount", "REAL NOT NULL DEFAULT 0.0")
            database.execSQL("UPDATE booking_payments SET paymentCategory = 'STAY' WHERE paymentCategory = 'AUTO' AND paymentType IN ('ADVANCE', 'PAYMENT') AND allocatedStayAmount = 0.0 AND allocatedFoodAmount = 0.0 AND allocatedServiceAmount = 0.0")
            database.execSQL("UPDATE booking_payments SET allocatedStayAmount = amount WHERE paymentType IN ('ADVANCE', 'PAYMENT') AND allocatedStayAmount = 0.0 AND allocatedFoodAmount = 0.0 AND allocatedServiceAmount = 0.0")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_payments_paymentCategory ON booking_payments(paymentCategory)")
        }
        private fun ensureCoreSchema(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS hotels (
                    remoteId TEXT NOT NULL PRIMARY KEY,
                    hotelName TEXT NOT NULL,
                    gstNumber TEXT,
                    address TEXT,
                    phone TEXT,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS managed_properties (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    remoteId TEXT NOT NULL,
                    hotelRemoteId TEXT NOT NULL,
                    propertyName TEXT NOT NULL,
                    legalName TEXT,
                    gstNumber TEXT,
                    address TEXT,
                    phone TEXT,
                    email TEXT,
                    invoicePrefix TEXT,
                    state TEXT,
                    sortOrder INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS room_categories (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    remoteId TEXT NOT NULL,
                    hotelRemoteId TEXT NOT NULL,
                    categoryName TEXT NOT NULL DEFAULT '',
                    categoryColor TEXT NOT NULL DEFAULT '#EEF0F2',
                    sortOrder INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS rooms (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    remoteId TEXT NOT NULL,
                    hotelRemoteId TEXT NOT NULL,
                    roomName TEXT NOT NULL,
                    categoryName TEXT NOT NULL DEFAULT '',
                    categoryColor TEXT NOT NULL DEFAULT '#EEF0F2',
                    categorySortOrder INTEGER NOT NULL DEFAULT 0,
                    propertyRemoteId TEXT,
                    sortOrder INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS bookings (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    remoteId TEXT NOT NULL,
                    bookingUuid TEXT NOT NULL,
                    hotelRemoteId TEXT NOT NULL,
                    propertyRemoteId TEXT,
                    guestName TEXT NOT NULL,
                    guestMobile TEXT,
                    sourceName TEXT,
                    adultCount INTEGER NOT NULL DEFAULT 1,
                    childCount INTEGER NOT NULL DEFAULT 0,
                    checkInMillis INTEGER NOT NULL,
                    checkOutMillis INTEGER NOT NULL,
                    roomRemoteIds TEXT NOT NULL,
                    rate REAL NOT NULL DEFAULT 0.0,
                    receivable REAL NOT NULL DEFAULT 0.0,
                    paid REAL NOT NULL DEFAULT 0.0,
                    balance REAL NOT NULL DEFAULT 0.0,
                    paymentStatus TEXT NOT NULL DEFAULT 'NOT_PAID',
                    pricingStatus TEXT NOT NULL DEFAULT 'CONFIRMED',
                    bookingStatus TEXT NOT NULL DEFAULT 'RESERVED',
                    actualCheckInAt INTEGER,
                    actualCheckOutAt INTEGER,
                    checkoutNote TEXT,
                    reopenNote TEXT,
                    reopenedAt INTEGER,
                    notes TEXT,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS booking_payments (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    remoteId TEXT NOT NULL,
                    hotelRemoteId TEXT NOT NULL,
                    bookingRemoteId TEXT NOT NULL,
                    paymentType TEXT NOT NULL DEFAULT 'PAYMENT',
                    paymentCategory TEXT NOT NULL DEFAULT 'AUTO',
                    amount REAL NOT NULL DEFAULT 0.0,
                    allocatedStayAmount REAL NOT NULL DEFAULT 0.0,
                    allocatedFoodAmount REAL NOT NULL DEFAULT 0.0,
                    allocatedServiceAmount REAL NOT NULL DEFAULT 0.0,
                    allocatedDamageAmount REAL NOT NULL DEFAULT 0.0,
                    paymentMillis INTEGER NOT NULL DEFAULT 0,
                    method TEXT,
                    note TEXT,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS booking_financial_lines (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    remoteId TEXT NOT NULL,
                    hotelRemoteId TEXT NOT NULL,
                    bookingRemoteId TEXT NOT NULL,
                    roomRemoteId TEXT NOT NULL,
                    propertyRemoteId TEXT,
                    businessDateMillis INTEGER NOT NULL,
                    grossAmount REAL NOT NULL DEFAULT 0.0,
                    taxableAmount REAL NOT NULL DEFAULT 0.0,
                    gstRatePercent REAL NOT NULL DEFAULT 0.0,
                    gstAmount REAL NOT NULL DEFAULT 0.0,
                    source TEXT NOT NULL DEFAULT 'MANUAL',
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )
            ensureAccountingChargeSchema(database)
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS booking_sources (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    remoteId TEXT NOT NULL,
                    hotelRemoteId TEXT NOT NULL,
                    propertyRemoteId TEXT,
                    sourceName TEXT NOT NULL,
                    sourceType TEXT NOT NULL DEFAULT 'DIRECT',
                    commissionPercent REAL NOT NULL DEFAULT 0.0,
                    commissionGstPercent REAL NOT NULL DEFAULT 0.0,
                    tcsPercent REAL NOT NULL DEFAULT 0.0,
                    tdsPercent REAL NOT NULL DEFAULT 0.0,
                    fixedFee REAL NOT NULL DEFAULT 0.0,
                    isActive INTEGER NOT NULL DEFAULT 1,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )
            ensureColumn(database, "hotels", "gstNumber", "TEXT")
            ensureColumn(database, "hotels", "address", "TEXT")
            ensureColumn(database, "hotels", "phone", "TEXT")
            ensureColumn(database, "hotels", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(database, "hotels", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(database, "booking_sources", "sourceType", "TEXT NOT NULL DEFAULT 'DIRECT'")
            ensureColumn(database, "booking_sources", "propertyRemoteId", "TEXT")
            ensureColumn(database, "booking_sources", "commissionPercent", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "booking_sources", "commissionGstPercent", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "booking_sources", "tcsPercent", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "booking_sources", "tdsPercent", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "booking_sources", "fixedFee", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "booking_sources", "isActive", "INTEGER NOT NULL DEFAULT 1")
            ensureColumn(database, "booking_sources", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(database, "booking_sources", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
        ensureColumn(database, "booking_financial_lines", "grossAmount", "REAL NOT NULL DEFAULT 0.0")
        ensureColumn(database, "booking_financial_lines", "propertyRemoteId", "TEXT")
        ensureColumn(database, "booking_financial_lines", "taxableAmount", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "booking_financial_lines", "gstRatePercent", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "booking_financial_lines", "gstAmount", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "booking_financial_lines", "source", "TEXT NOT NULL DEFAULT 'MANUAL'")
            ensureColumn(database, "booking_financial_lines", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(database, "booking_financial_lines", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(database, "room_categories", "categoryColor", "TEXT NOT NULL DEFAULT '#EEF0F2'")
            ensureColumn(database, "room_categories", "sortOrder", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(database, "room_categories", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(database, "room_categories", "isDeleted", "INTEGER NOT NULL DEFAULT 0")

            ensureColumn(database, "rooms", "hotelRemoteId", "TEXT NOT NULL DEFAULT 'default_hotel'")
            ensureColumn(database, "rooms", "categoryName", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(database, "rooms", "categoryColor", "TEXT NOT NULL DEFAULT '#EEF0F2'")
            ensureColumn(database, "rooms", "categorySortOrder", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(database, "rooms", "propertyRemoteId", "TEXT")
            ensureColumn(database, "rooms", "sortOrder", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(database, "rooms", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(database, "rooms", "isDeleted", "INTEGER NOT NULL DEFAULT 0")

        ensureColumn(database, "bookings", "bookingUuid", "TEXT NOT NULL DEFAULT ''")
        ensureColumn(database, "bookings", "hotelRemoteId", "TEXT NOT NULL DEFAULT 'default_hotel'")
        ensureColumn(database, "bookings", "propertyRemoteId", "TEXT")
        ensureColumn(database, "bookings", "guestMobile", "TEXT")
            ensureColumn(database, "bookings", "sourceName", "TEXT")
            ensureColumn(database, "bookings", "sourceRemoteId", "TEXT")
            ensureColumn(database, "bookings", "sourceType", "TEXT NOT NULL DEFAULT 'DIRECT'")
            ensureColumn(database, "bookings", "grossCharges", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "bookings", "roomRevenue", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "bookings", "propertyTax", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "bookings", "commissionAmount", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "bookings", "commissionTax", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "bookings", "sourceFee", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "bookings", "tdsAmount", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "bookings", "tcsAmount", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "bookings", "expectedPayout", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "bookings", "adultCount", "INTEGER NOT NULL DEFAULT 1")
            ensureColumn(database, "bookings", "childCount", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(database, "bookings", "roomRemoteIds", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(database, "bookings", "rate", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "bookings", "receivable", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "bookings", "paid", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "bookings", "balance", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "bookings", "paymentStatus", "TEXT NOT NULL DEFAULT 'NOT_PAID'")
            ensureColumn(database, "bookings", "pricingStatus", "TEXT NOT NULL DEFAULT 'CONFIRMED'")
            ensureColumn(database, "bookings", "bookingStatus", "TEXT NOT NULL DEFAULT 'RESERVED'")
            ensureColumn(database, "bookings", "actualCheckInAt", "INTEGER")
            ensureColumn(database, "bookings", "actualCheckOutAt", "INTEGER")
            ensureColumn(database, "bookings", "checkoutNote", "TEXT")
            ensureColumn(database, "bookings", "reopenNote", "TEXT")
            ensureColumn(database, "bookings", "reopenedAt", "INTEGER")
            ensureColumn(database, "bookings", "notes", "TEXT")
            ensureColumn(database, "bookings", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(database, "bookings", "isDeleted", "INTEGER NOT NULL DEFAULT 0")

            database.execSQL("UPDATE bookings SET bookingUuid = remoteId WHERE bookingUuid = ''")
            seedCategoriesFromRooms(database)
            seedDefaultSources(database)
            createIndices(database)
            ensureFoodBillingSchema(database)
        }

        private fun ensureSyncColumns(database: SupportSQLiteDatabase) {
            listOf(
                "hotels",
                "managed_properties",
                "booking_sources",
                "booking_accounting_charges",
                "room_categories",
                "rooms",
                "bookings",
                "booking_payments",
                "booking_financial_lines",
                "food_gst_categories",
                "food_menu_items",
                "food_orders",
                "food_order_items",
                "food_bills",
                "food_bill_items"
            ).forEach { table ->
                ensureColumn(database, table, "syncState", "TEXT NOT NULL DEFAULT 'SYNCED'")
                ensureColumn(database, table, "lastSyncError", "TEXT")
                ensureColumn(database, table, "lastSyncedAt", "INTEGER")
            }
        }

        private fun ensureRevisionColumns(database: SupportSQLiteDatabase) {
            listOf(
                "hotels",
                "managed_properties",
                "booking_sources",
                "booking_accounting_charges",
                "room_categories",
                "rooms",
                "bookings",
                "booking_payments",
                "booking_financial_lines",
                "food_gst_categories",
                "food_menu_items",
                "food_orders",
                "food_order_items",
                "food_bills",
                "food_bill_items"
            ).forEach { table ->
                ensureColumn(database, table, "revision", "INTEGER NOT NULL DEFAULT 0")
                ensureColumn(database, table, "baseRevision", "INTEGER NOT NULL DEFAULT 0")
                ensureColumn(database, table, "updatedByUid", "TEXT")
            }
        }

        private fun ensureAccountingChargeSchema(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS booking_accounting_charges (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    remoteId TEXT NOT NULL,
                    hotelRemoteId TEXT NOT NULL,
                    bookingRemoteId TEXT NOT NULL,
                    chargeType TEXT NOT NULL,
                    accountBucket TEXT,
                    amount REAL NOT NULL DEFAULT 0.0,
                    description TEXT NOT NULL DEFAULT '',
                    reason TEXT,
                    hsnSacCode TEXT,
                    gstRatePercent REAL NOT NULL DEFAULT 0.0,
                    taxInclusive INTEGER NOT NULL DEFAULT 1,
                    taxableAmount REAL,
                    approvedBy TEXT,
                    createdBy TEXT,
                    chargeMillis INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )
            ensureColumn(database, "booking_accounting_charges", "accountBucket", "TEXT")
            ensureColumn(database, "booking_accounting_charges", "hsnSacCode", "TEXT")
            ensureColumn(database, "booking_accounting_charges", "gstRatePercent", "REAL NOT NULL DEFAULT 0.0")
            ensureColumn(database, "booking_accounting_charges", "taxInclusive", "INTEGER NOT NULL DEFAULT 1")
            ensureColumn(database, "booking_accounting_charges", "taxableAmount", "REAL")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_booking_accounting_charges_remoteId ON booking_accounting_charges(remoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_accounting_charges_hotelRemoteId ON booking_accounting_charges(hotelRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_accounting_charges_bookingRemoteId ON booking_accounting_charges(bookingRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_accounting_charges_chargeType ON booking_accounting_charges(chargeType)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_accounting_charges_hotelRemoteId_bookingRemoteId ON booking_accounting_charges(hotelRemoteId, bookingRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_accounting_charges_hotelRemoteId_chargeMillis ON booking_accounting_charges(hotelRemoteId, chargeMillis)")
        }

        private fun ensureFoodBillingSchema(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS food_gst_categories (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    remoteId TEXT NOT NULL,
                    hotelRemoteId TEXT NOT NULL,
                    categoryName TEXT NOT NULL,
                    hsnSacCode TEXT,
                    gstRatePercent REAL NOT NULL DEFAULT 0.0,
                    cgstRatePercent REAL NOT NULL DEFAULT 0.0,
                    sgstRatePercent REAL NOT NULL DEFAULT 0.0,
                    cessRatePercent REAL NOT NULL DEFAULT 0.0,
                    taxType TEXT NOT NULL DEFAULT 'GST',
                    itcType TEXT,
                    description TEXT,
                    isDefault INTEGER NOT NULL DEFAULT 0,
                    isActive INTEGER NOT NULL DEFAULT 1,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS food_menu_items (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    remoteId TEXT NOT NULL,
                    hotelRemoteId TEXT NOT NULL,
                    propertyRemoteId TEXT,
                    itemName TEXT NOT NULL,
                    categoryName TEXT,
                    price REAL NOT NULL DEFAULT 0.0,
                    gstCategoryRemoteId TEXT,
                    gstRatePercent REAL NOT NULL DEFAULT 5.0,
                    isActive INTEGER NOT NULL DEFAULT 1,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS food_orders (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    remoteId TEXT NOT NULL,
                    hotelRemoteId TEXT NOT NULL,
                    propertyRemoteId TEXT,
                    bookingRemoteId TEXT,
                    billRemoteId TEXT,
                    orderNumber TEXT,
                    foodBillingScope TEXT NOT NULL DEFAULT 'WALK_IN',
                    linkedFinalBillId TEXT,
                    archivedAt INTEGER,
                    roomRemoteId TEXT,
                    roomName TEXT,
                    guestName TEXT NOT NULL,
                    orderMillis INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL DEFAULT 'OPEN',
                    subtotal REAL NOT NULL DEFAULT 0.0,
                    discountAmount REAL NOT NULL DEFAULT 0.0,
                    taxableAmount REAL NOT NULL DEFAULT 0.0,
                    gstAmount REAL NOT NULL DEFAULT 0.0,
                    totalAmount REAL NOT NULL DEFAULT 0.0,
                    notes TEXT,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS food_order_items (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    remoteId TEXT NOT NULL,
                    hotelRemoteId TEXT NOT NULL,
                    orderRemoteId TEXT NOT NULL,
                    menuItemRemoteId TEXT,
                    itemName TEXT NOT NULL,
                    quantity REAL NOT NULL DEFAULT 1.0,
                    unitPrice REAL NOT NULL DEFAULT 0.0,
                    gstRatePercent REAL NOT NULL DEFAULT 5.0,
                    lineSubtotal REAL NOT NULL DEFAULT 0.0,
                    lineGst REAL NOT NULL DEFAULT 0.0,
                    lineTotal REAL NOT NULL DEFAULT 0.0,
                    isCancelled INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS food_bills (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    remoteId TEXT NOT NULL,
                    hotelRemoteId TEXT NOT NULL,
                    propertyRemoteId TEXT,
                    supplierName TEXT,
                    supplierGstin TEXT,
                    supplierAddress TEXT,
                    supplierPhone TEXT,
                    supplierState TEXT,
                    propertyDisplayName TEXT,
                    billNumber TEXT NOT NULL,
                    billMillis INTEGER NOT NULL DEFAULT 0,
                    guestName TEXT,
                    guestMobile TEXT,
                    guestAddress TEXT,
                    guestGstin TEXT,
                    roomsIncluded TEXT NOT NULL,
                    orderRemoteIds TEXT NOT NULL,
                    subtotal REAL NOT NULL DEFAULT 0.0,
                    discountAmount REAL NOT NULL DEFAULT 0.0,
                    taxableAmount REAL NOT NULL DEFAULT 0.0,
                    cgstAmount REAL NOT NULL DEFAULT 0.0,
                    sgstAmount REAL NOT NULL DEFAULT 0.0,
                    cessAmount REAL NOT NULL DEFAULT 0.0,
                    gstAmount REAL NOT NULL DEFAULT 0.0,
                    grandTotal REAL NOT NULL DEFAULT 0.0,
                    paymentMode TEXT,
                    notes TEXT,
                    status TEXT NOT NULL DEFAULT 'ISSUED',
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS food_bill_items (
                    localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    remoteId TEXT NOT NULL,
                    hotelRemoteId TEXT NOT NULL,
                    billRemoteId TEXT NOT NULL,
                    orderRemoteId TEXT NOT NULL,
                    orderNumber TEXT,
                    orderMillis INTEGER NOT NULL DEFAULT 0,
                    roomName TEXT,
                    menuItemRemoteId TEXT,
                    itemName TEXT NOT NULL,
                    quantity REAL NOT NULL DEFAULT 1.0,
                    unitPrice REAL NOT NULL DEFAULT 0.0,
                    lineSubtotal REAL NOT NULL DEFAULT 0.0,
                    gstCategoryRemoteId TEXT,
                    gstCategoryName TEXT,
                    hsnSacCode TEXT,
                    gstRatePercent REAL NOT NULL DEFAULT 0.0,
                    cgstRatePercent REAL NOT NULL DEFAULT 0.0,
                    sgstRatePercent REAL NOT NULL DEFAULT 0.0,
                    cessRatePercent REAL NOT NULL DEFAULT 0.0,
                    taxableAmount REAL NOT NULL DEFAULT 0.0,
                    cgstAmount REAL NOT NULL DEFAULT 0.0,
                    sgstAmount REAL NOT NULL DEFAULT 0.0,
                    cessAmount REAL NOT NULL DEFAULT 0.0,
                    gstAmount REAL NOT NULL DEFAULT 0.0,
                    lineTotal REAL NOT NULL DEFAULT 0.0,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    syncState TEXT NOT NULL DEFAULT 'SYNCED',
                    lastSyncError TEXT,
                    lastSyncedAt INTEGER,
                    revision INTEGER NOT NULL DEFAULT 0,
                    baseRevision INTEGER NOT NULL DEFAULT 0,
                    updatedByUid TEXT
                )
                """.trimIndent()
            )
            ensureColumn(database, "food_menu_items", "gstCategoryRemoteId", "TEXT")
            ensureBillSupplierSnapshotSchema(database)
            ensureColumn(database, "food_orders", "billRemoteId", "TEXT")
            ensureColumn(database, "food_orders", "orderNumber", "TEXT")
            ensureColumn(database, "food_orders", "foodBillingScope", "TEXT NOT NULL DEFAULT 'WALK_IN'")
            ensureColumn(database, "food_orders", "linkedFinalBillId", "TEXT")
            ensureColumn(database, "food_orders", "archivedAt", "INTEGER")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_food_gst_categories_remoteId ON food_gst_categories(remoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_gst_categories_hotelRemoteId ON food_gst_categories(hotelRemoteId)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_food_gst_categories_hotelRemoteId_categoryName ON food_gst_categories(hotelRemoteId, categoryName)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_food_menu_items_remoteId ON food_menu_items(remoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_menu_items_hotelRemoteId ON food_menu_items(hotelRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_menu_items_propertyRemoteId ON food_menu_items(propertyRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_menu_items_hotelRemoteId_itemName ON food_menu_items(hotelRemoteId, itemName)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_food_orders_remoteId ON food_orders(remoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_orders_hotelRemoteId ON food_orders(hotelRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_orders_propertyRemoteId ON food_orders(propertyRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_orders_bookingRemoteId ON food_orders(bookingRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_orders_billRemoteId ON food_orders(billRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_orders_foodBillingScope ON food_orders(foodBillingScope)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_orders_linkedFinalBillId ON food_orders(linkedFinalBillId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_orders_roomRemoteId ON food_orders(roomRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_orders_hotelRemoteId_orderMillis ON food_orders(hotelRemoteId, orderMillis)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_food_order_items_remoteId ON food_order_items(remoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_order_items_hotelRemoteId ON food_order_items(hotelRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_order_items_orderRemoteId ON food_order_items(orderRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_order_items_menuItemRemoteId ON food_order_items(menuItemRemoteId)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_food_bills_remoteId ON food_bills(remoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_bills_hotelRemoteId ON food_bills(hotelRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_bills_propertyRemoteId ON food_bills(propertyRemoteId)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_food_bills_billNumber ON food_bills(billNumber)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_bills_hotelRemoteId_billMillis ON food_bills(hotelRemoteId, billMillis)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_food_bill_items_remoteId ON food_bill_items(remoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_bill_items_hotelRemoteId ON food_bill_items(hotelRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_bill_items_billRemoteId ON food_bill_items(billRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_bill_items_orderRemoteId ON food_bill_items(orderRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_food_bill_items_roomName ON food_bill_items(roomName)")
            seedDefaultFoodGstCategories(database)
        }

        private fun seedDefaultFoodGstCategories(database: SupportSQLiteDatabase) {
            val categories = listOf(
                FoodGstSeed("restaurant_service", "Restaurant / In-Room Food Service", "996331", 5.0, 2.5, 2.5, 0.0, "GST", "Without ITC", true),
                FoodGstSeed("packaged_water", "Packaged Drinking Water", "2201", 18.0, 9.0, 9.0),
                FoodGstSeed("water_20_litre", "Drinking Water Packed in 20 Litre Bottles", "2201", 12.0, 6.0, 6.0),
                FoodGstSeed("soft_drinks", "Soft Drinks / Aerated Drinks", "2202", 28.0, 14.0, 14.0, 12.0),
                FoodGstSeed("packaged_juice", "Packaged Fruit Juice / Fruit Based Drinks", "2202 99 20", 12.0, 6.0, 6.0),
                FoodGstSeed("other_beverages", "Other Non-Alcoholic Beverages", "2202 91 / 2202 99", 18.0, 9.0, 9.0),
                FoodGstSeed("ice_cream", "Packaged Ice Cream", "2105 00 00", 18.0, 9.0, 9.0),
                FoodGstSeed("chocolates", "Chocolates / Cocoa Products", "1806", 18.0, 9.0, 9.0),
                FoodGstSeed("sugar_confectionery", "Sugar Confectionery", "1704", 18.0, 9.0, 9.0),
                FoodGstSeed("packaged_snacks", "Namkeen / Bhujia / Mixture / Packaged Snacks", "2106 90", 12.0, 6.0, 6.0),
                FoodGstSeed("other_food_preparations", "Food Preparations Not Elsewhere Specified", "2106", 18.0, 9.0, 9.0),
                FoodGstSeed("packaged_bakery", "Packaged Bakery Items", "1905", 18.0, 9.0, 9.0),
                FoodGstSeed("bread", "Bread", "1905", 0.0, 0.0, 0.0),
                FoodGstSeed("rusk", "Rusk / Toasted Bread", "1905 40 00", 5.0, 2.5, 2.5),
                FoodGstSeed("pizza_bread", "Pizza Bread", "1905", 5.0, 2.5, 2.5),
                FoodGstSeed("roti", "Khakhra / Plain Chapatti / Roti", "1905 / 2106", 5.0, 2.5, 2.5),
                FoodGstSeed("sweetmeats", "Sweetmeats", "2106 90", 5.0, 2.5, 2.5),
                FoodGstSeed("tobacco", "Tobacco Products", "2401 / 2402 / 2403 / 2404", 28.0, 14.0, 14.0, 0.0, "GST", "Product-specific"),
                FoodGstSeed("alcohol", "Alcoholic Beverages", null, 0.0, 0.0, 0.0, 0.0, "NON_GST", "State tax / excise")
            )
            categories.forEach { seed ->
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO food_gst_categories (
                        remoteId, hotelRemoteId, categoryName, hsnSacCode, gstRatePercent,
                        cgstRatePercent, sgstRatePercent, cessRatePercent, taxType, itcType,
                        description, isDefault, isActive, updatedAt, isDeleted, syncState,
                        revision, baseRevision
                    )
                    SELECT
                        remoteId || '_food_gst_${seed.id}',
                        remoteId,
                        '${seed.name.replace("'", "''")}',
                        ${seed.hsn?.let { "'${it.replace("'", "''")}'" } ?: "NULL"},
                        ${seed.gst},
                        ${seed.cgst},
                        ${seed.sgst},
                        ${seed.cess},
                        '${seed.taxType}',
                        ${seed.itc?.let { "'${it.replace("'", "''")}'" } ?: "NULL"},
                        'Suggested default. Confirm with CA or tax consultant.',
                        ${if (seed.isDefault) 1 else 0},
                        1,
                        strftime('%s','now') * 1000,
                        0,
                        'PENDING_PUSH',
                        1,
                        0
                    FROM hotels
                    """.trimIndent()
                )
            }
        }

        private data class FoodGstSeed(
            val id: String,
            val name: String,
            val hsn: String?,
            val gst: Double,
            val cgst: Double,
            val sgst: Double,
            val cess: Double = 0.0,
            val taxType: String = "GST",
            val itc: String? = null,
            val isDefault: Boolean = false
        )

        private fun ensureColumn(
            database: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            definition: String
        ) {
            if (!hasColumn(database, tableName, columnName)) {
                database.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $definition")
            }
        }

        private fun hasColumn(
            database: SupportSQLiteDatabase,
            tableName: String,
            columnName: String
        ): Boolean {
            database.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == columnName) return true
                }
            }
            return false
        }

        private fun createIndices(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_booking_sources_remoteId ON booking_sources(remoteId)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_managed_properties_remoteId ON managed_properties(remoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_managed_properties_hotelRemoteId ON managed_properties(hotelRemoteId)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_managed_properties_hotelRemoteId_propertyName ON managed_properties(hotelRemoteId, propertyName)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_managed_properties_hotelRemoteId_sortOrder ON managed_properties(hotelRemoteId, sortOrder)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_sources_hotelRemoteId ON booking_sources(hotelRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_sources_propertyRemoteId ON booking_sources(propertyRemoteId)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_booking_sources_hotelRemoteId_propertyRemoteId_sourceName ON booking_sources(hotelRemoteId, propertyRemoteId, sourceName)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_room_categories_remoteId ON room_categories(remoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_room_categories_hotelRemoteId ON room_categories(hotelRemoteId)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_room_categories_hotelRemoteId_categoryName ON room_categories(hotelRemoteId, categoryName)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_room_categories_sortOrder ON room_categories(sortOrder)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_rooms_remoteId ON rooms(remoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_rooms_hotelRemoteId ON rooms(hotelRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_rooms_propertyRemoteId ON rooms(propertyRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_rooms_categorySortOrder_categoryName ON rooms(categorySortOrder, categoryName)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bookings_remoteId ON bookings(remoteId)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bookings_bookingUuid ON bookings(bookingUuid)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_bookings_hotelRemoteId ON bookings(hotelRemoteId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_bookings_hotelRemoteId_bookingStatus ON bookings(hotelRemoteId, bookingStatus)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_bookings_propertyRemoteId ON bookings(propertyRemoteId)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_booking_payments_remoteId ON booking_payments(remoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_payments_hotelRemoteId ON booking_payments(hotelRemoteId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_payments_bookingRemoteId ON booking_payments(bookingRemoteId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_payments_hotelRemoteId_bookingRemoteId ON booking_payments(hotelRemoteId, bookingRemoteId)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_booking_financial_lines_remoteId ON booking_financial_lines(remoteId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_financial_lines_hotelRemoteId ON booking_financial_lines(hotelRemoteId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_financial_lines_bookingRemoteId ON booking_financial_lines(bookingRemoteId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_financial_lines_propertyRemoteId ON booking_financial_lines(propertyRemoteId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_financial_lines_hotelRemoteId_bookingRemoteId ON booking_financial_lines(hotelRemoteId, bookingRemoteId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_financial_lines_hotelRemoteId_propertyRemoteId ON booking_financial_lines(hotelRemoteId, propertyRemoteId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_booking_financial_lines_hotelRemoteId_businessDateMillis ON booking_financial_lines(hotelRemoteId, businessDateMillis)")
        }

        private fun seedLegacyPaymentsFromBookings(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                INSERT OR IGNORE INTO booking_payments (
                    remoteId,
                    hotelRemoteId,
                    bookingRemoteId,
                    paymentType,
                    amount,
                    paymentMillis,
                    method,
                    note,
                    updatedAt,
                    isDeleted,
                    syncState,
                    revision,
                    baseRevision
                )
                SELECT
                    remoteId || '_payment_legacy_paid' AS remoteId,
                    hotelRemoteId,
                    remoteId,
                    'PAYMENT',
                    paid,
                    updatedAt,
                    NULL,
                    'Imported from existing paid amount',
                    updatedAt,
                    0,
                    'SYNCED',
                    0,
                    0
                FROM bookings
                WHERE isDeleted = 0
                AND paid > 0.0
                """.trimIndent()
            )
        }
        private fun seedDefaultSources(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                INSERT OR IGNORE INTO booking_sources (
                    remoteId,
                    hotelRemoteId,
                    sourceName,
                    sourceType,
                    commissionPercent,
                    commissionGstPercent,
                    tcsPercent,
                    tdsPercent,
                    fixedFee,
                    isActive,
                    updatedAt,
                    isDeleted,
                    syncState,
                    revision,
                    baseRevision
                )
                SELECT
                    remoteId || '_source_walk_in' AS remoteId,
                    remoteId,
                    'Walk-in',
                    'DIRECT',
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    1,
                    updatedAt,
                    0,
                    'PENDING',
                    0,
                    0
                FROM hotels
                """.trimIndent()
            )
        }
        private fun seedCategoriesFromRooms(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                INSERT OR IGNORE INTO room_categories (
                    remoteId,
                    hotelRemoteId,
                    categoryName,
                    categoryColor,
                    sortOrder,
                    updatedAt,
                    isDeleted,
                    syncState,
                    revision,
                    baseRevision
                )
                SELECT
                    hotelRemoteId || '_category_' || lower(replace(categoryName, ' ', '_')) AS remoteId,
                    hotelRemoteId,
                    categoryName,
                    MIN(categoryColor),
                    MIN(categorySortOrder),
                    MAX(updatedAt),
                    0,
                    'PENDING',
                    0,
                    0
                FROM rooms
                WHERE isDeleted = 0
                AND trim(categoryName) <> ''
                GROUP BY hotelRemoteId, categoryName
                """.trimIndent()
            )
        }
    }
}
