package com.example.bookingregister.ui.booking

import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.bookingregister.R
import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.account.domain.AccountPermission
import com.example.bookingregister.account.domain.BackendAccessManager
import com.example.bookingregister.data.AppDatabase
import com.example.bookingregister.data.repository.BookingRepository
import com.example.bookingregister.data.repository.SaveResult
import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingSourceEntity
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderItemEntity
import com.example.bookingregister.data.entities.FoodBillEntity
import com.example.bookingregister.data.entities.FoodBillItemEntity
import com.example.bookingregister.data.entities.HotelEntity
import com.example.bookingregister.data.entities.ManagedPropertyEntity
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.data.entities.ServiceMenuItemEntity
import com.example.bookingregister.data.repository.FoodBillingRepository
import com.example.bookingregister.reporting.domain.OccupancyCalculator
import com.example.bookingregister.revenue.domain.RevenueCalculator
import com.example.bookingregister.revenue.domain.RevenueLedgerBuilder
import com.example.bookingregister.revenue.domain.RevenuePeriod
import com.example.bookingregister.tax.domain.GstinValidator
import com.example.bookingregister.ui.food.FoodBillingActivity
import com.example.bookingregister.ui.payments.PaymentsActivity
import com.example.bookingregister.ui.reporting.RevenueReportActivity
import com.example.bookingregister.ui.login.LoginActivity
import com.example.bookingregister.ui.views.BookingChartView
import com.example.bookingregister.ui.views.BookingDaysProvider
import com.example.bookingregister.room.domain.RoomLifecycleStatus
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.example.bookingregister.data.repository.GstRepository
import com.example.bookingregister.data.entities.RoomGstSlabEntity
import java.util.UUID

class BookingChartActivity : AppCompatActivity(), BookingChartView.Listener {

    companion object {
        const val EXTRA_HOTEL_REMOTE_ID = "hotel_remote_id"
    }

    private lateinit var repository: BookingRepository
    private lateinit var foodBillingRepository: FoodBillingRepository
    private lateinit var chartView: BookingChartView
    private lateinit var chartLoadingView: View
    private lateinit var titleView: TextView
    private lateinit var revenueSummaryButton: MaterialButton
    private lateinit var occupancySummaryButton: MaterialButton
    private lateinit var balanceSummaryButton: MaterialButton
    private val rooms = mutableListOf<RoomEntity>()
    private val bookings = mutableListOf<BookingEntity>()
    private val unsyncedBookings = mutableListOf<BookingEntity>()
    private val payments = mutableListOf<BookingPaymentEntity>()
    private val unsyncedPayments = mutableListOf<BookingPaymentEntity>()
    private val financialLines = mutableListOf<BookingFinancialLineEntity>()
    private val accountingCharges = mutableListOf<BookingAccountingChargeEntity>()
    private val unsyncedAccountingCharges = mutableListOf<BookingAccountingChargeEntity>()
    private val foodOrders = mutableListOf<FoodOrderEntity>()
    private val foodOrderItems = mutableListOf<FoodOrderItemEntity>()
    private val foodBills = mutableListOf<FoodBillEntity>()
    private val foodBillItems = mutableListOf<FoodBillItemEntity>()
    private val sources = mutableListOf<BookingSourceEntity>()
    private val managedProperties = mutableListOf<ManagedPropertyEntity>()
    private val serviceMenuItems = mutableListOf<ServiceMenuItemEntity>()
    private val roomGstSlabs = mutableListOf<RoomGstSlabEntity>()
    private var currentHotel: HotelEntity? = null
    private var realtimeSyncError: String? = null
    private var outstandingBalance = 0.0
    private var autoHealStarted = false
    private val revenueLedgerBuilder = RevenueLedgerBuilder()
    private val revenueCalculator = RevenueCalculator()
    private val occupancyCalculator = OccupancyCalculator()
    private val accessManager = BackendAccessManager()
    private var currentPermissions: Set<String> = emptySet()
    private var roomsLoaded = false
    private var bookingsLoaded = false
    private var emptyRoomPromptShown = false
    private var initialLoadGraceFinished = false
    private var activeBookingDialog: BookingDialog? = null
    private lateinit var gstRepository: GstRepository



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hotelRemoteId = intent.getStringExtra(EXTRA_HOTEL_REMOTE_ID)
        if (hotelRemoteId.isNullOrBlank()) {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }
        repository = BookingRepository(applicationContext, lifecycleScope, hotelRemoteId)
        gstRepository = GstRepository(AppDatabase.getInstance(applicationContext), hotelRemoteId)
        foodBillingRepository = FoodBillingRepository(applicationContext, lifecycleScope, hotelRemoteId)
        setContentView(R.layout.activity_booking_chart)
        verifyAccessInBackground(hotelRemoteId)

        titleView = findViewById(R.id.tvBookingChartHeading)
        titleView.setOnClickListener {
            showSyncStatusDialog()
        }
        chartView = findViewById<BookingChartView>(R.id.bookingChartView).apply {
            setListener(this@BookingChartActivity)
            setDays(BookingDaysProvider().getDaysList())
        }
        chartLoadingView = findViewById(R.id.chartLoadingView)
        updateChartLoadingState()

        findViewById<MaterialButton>(R.id.btnMenu).setOnClickListener { anchor ->
            showMenu(anchor)
        }
        revenueSummaryButton = findViewById(R.id.btnRevenueSummary)
        occupancySummaryButton = findViewById(R.id.btnOccupancySummary)
        balanceSummaryButton = findViewById(R.id.btnBalanceSummary)
        revenueSummaryButton.setOnClickListener {
            Toast.makeText(this, "Revenue reporting is being verified and is not included in this release", Toast.LENGTH_LONG).show()
        }
        occupancySummaryButton.setOnClickListener {
            startActivity(Intent(this, RevenueReportActivity::class.java).apply {
                putExtra(RevenueReportActivity.EXTRA_REPORT_KIND, RevenueReportActivity.KIND_OCCUPANCY)
                putExtra(RevenueReportActivity.EXTRA_HOTEL_REMOTE_ID, repository.hotelRemoteId)
            })
        }
        balanceSummaryButton.setOnClickListener {
            startActivity(Intent(this, PaymentsActivity::class.java).apply {
                putExtra(PaymentsActivity.EXTRA_HOTEL_REMOTE_ID, repository.hotelRemoteId)
            })
        }
        observeLocalData()
        repository.startRealtimeSync()
        foodBillingRepository.startRealtimeSync()
        startAutoHealSyncLoop()

        lifecycleScope.launch {
            val isFirstTime = !repository.hasHotel()

            repository.ensureDefaultHotelExists()
            repository.ensureDefaultSourceExists()
            gstRepository.ensureDefaultRoomGstSlabs()
            if (isFirstTime) {
                showHotelDialog()
            }
        }

        lifecycleScope.launch {
            delay(2_500)
            initialLoadGraceFinished = true
            updateChartLoadingState()
            showEmptyRoomPromptIfReady()
        }

        chartView.post {
            chartView.scrollToDay(chartView.getTodayColumnIndex())
        }
    }

    override fun onDestroy() {
        repository.stopRealtimeSync()
        if (::foodBillingRepository.isInitialized) {
            foodBillingRepository.stopRealtimeSync()
        }
        super.onDestroy()
    }
    override fun onResume() {
        super.onResume()
        refreshOpenBookingDialogFolioData()
    }

    private fun verifyAccessInBackground(hotelRemoteId: String) {
        lifecycleScope.launch {
            runCatching { accessManager.getMyAccess(forceRefreshToken = false) }
                .onSuccess { access ->
                    if (!access.allowed || access.hotelId != hotelRemoteId) {
                        clearCachedAccess()
                        FirebaseAuth.getInstance().signOut()
                        startActivity(Intent(this@BookingChartActivity, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        finish()
                    } else {
                        currentPermissions = access.permissions
                    }
                }
        }
    }

    override fun onEmptyCellClicked(room: RoomEntity, dateMillis: Long) {
        if (RoomLifecycleStatus.normalize(room.lifecycleStatus) != RoomLifecycleStatus.ACTIVE) {
            Toast.makeText(this, "This room is not available for new bookings.", Toast.LENGTH_SHORT).show()
            return
        }
        showBookingDialog(existing = null, selectedRoom = room, selectedCheckInMillis = dateMillis, roomRateLocked = false)
    }

    override fun onBookingClicked(booking: BookingEntity) {
        val selectedRoom = rooms.firstOrNull { booking.roomRemoteIds.contains(it.remoteId) }
        lifecycleScope.launch {
            val roomRateLocked = repository.isRoomRateLocked(booking.remoteId)
            showBookingDialog(
                existing = booking,
                selectedRoom = selectedRoom,
                selectedCheckInMillis = booking.checkInMillis,
                roomRateLocked = roomRateLocked
            )
        }
    }

    private fun observeLocalData() {
        repository.observeHotel().observe(this) { hotel ->
            currentHotel = hotel
            updateSyncIndicator()
        }

        repository.observeRooms().observe(this) { updatedRooms ->
            roomsLoaded = true
            rooms.clear()
            rooms.addAll(updatedRooms)
            chartView.setData(rooms, bookings)
            updateReportSummary()
            updateSyncIndicator()
            updateChartLoadingState()
            showEmptyRoomPromptIfReady()
        }

        repository.observeManagedProperties().observe(this) { updatedProperties ->
            managedProperties.clear()
            managedProperties.addAll(updatedProperties)
            updateSyncIndicator()
        }

        repository.observePayments().observe(this) { updatedPayments ->
            payments.clear()
            payments.addAll(updatedPayments)
            refreshOpenBookingDialogFolioData()
        }

        repository.observeFinancialLines().observe(this) { updatedLines ->
            financialLines.clear()
            financialLines.addAll(updatedLines)
            refreshOpenBookingDialogFolioData()
        }

        repository.observeAccountingCharges().observe(this) { updatedCharges ->
            accountingCharges.clear()
            accountingCharges.addAll(updatedCharges)
            refreshOpenBookingDialogFolioData()
        }

        repository.observeUnsyncedAccountingCharges().observe(this) { updatedCharges ->
            unsyncedAccountingCharges.clear()
            unsyncedAccountingCharges.addAll(updatedCharges)
            updateSyncIndicator()
        }

        foodBillingRepository.observeFoodOrders().observe(this) { updatedOrders ->
            foodOrders.clear()
            foodOrders.addAll(updatedOrders)
            refreshOpenBookingDialogFolioData()
        }

        foodBillingRepository.observeFoodOrderItems().observe(this) { updatedItems ->
            foodOrderItems.clear()
            foodOrderItems.addAll(updatedItems)
            refreshOpenBookingDialogFolioData()
        }
        foodBillingRepository.observeFoodBills().observe(this) { updatedBills ->
            foodBills.clear()
            foodBills.addAll(updatedBills)
            updateSyncIndicator()
        }
        foodBillingRepository.observeFoodBillItems().observe(this) { updatedItems ->
            foodBillItems.clear()
            foodBillItems.addAll(updatedItems)
            updateSyncIndicator()
        }

        foodBillingRepository.observeServiceMenuItems().observe(this) { updatedItems ->
            serviceMenuItems.clear()
            serviceMenuItems.addAll(
                updatedItems
                    .filter { !it.isDeleted && it.isActive }
                    .sortedWith(compareBy<ServiceMenuItemEntity> { it.categoryName.orEmpty().lowercase() }
                        .thenBy { it.serviceName.lowercase() })
            )
            updateSyncIndicator()
        }

        repository.observeUnsyncedPayments().observe(this) { updatedPayments ->
            unsyncedPayments.clear()
            unsyncedPayments.addAll(updatedPayments)
            updateSyncIndicator()
        }

        repository.observeSources().observe(this) { updatedSources ->
            sources.clear()
            sources.addAll(updatedSources)
            updateSyncIndicator()
        }
        val (bookingWindowStart, bookingWindowEnd) = operationalBookingWindow()
        repository.observeBookingsForWindow(bookingWindowStart, bookingWindowEnd).observe(this) { updatedBookings ->
            bookingsLoaded = true
            bookings.clear()
            bookings.addAll(updatedBookings)
            chartView.setData(rooms, bookings)
            updateReportSummary()
            updateSyncIndicator()
            updateChartLoadingState()
            showEmptyRoomPromptIfReady()
        }

        repository.observeOutstandingBalance().observe(this) { balance ->
            outstandingBalance = balance
            updateReportSummary()
        }

        repository.observeUnsyncedBookings().observe(this) { updatedBookings ->
            unsyncedBookings.clear()
            unsyncedBookings.addAll(updatedBookings)
            updateSyncIndicator()
        }

        repository.observeRealtimeSyncError().observe(this) { error ->
            realtimeSyncError = error
            updateSyncIndicator()
        }

        gstRepository.observeRoomGstSlabs().observe(this) { slabs ->
            roomGstSlabs.clear()
            roomGstSlabs.addAll(
                slabs
                    .filter { !it.isDeleted }
                    .sortedBy { it.minGrossAmount }
            )
        }
    }

    private fun showEmptyRoomPromptIfReady() {
        if (!roomsLoaded || !bookingsLoaded || !initialLoadGraceFinished || rooms.isNotEmpty() || emptyRoomPromptShown) {
            return
        }

        emptyRoomPromptShown = true
        Toast.makeText(
            this,
            "Add your first room from Menu > Properties > Rooms",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun updateChartLoadingState() {
        if (!::chartLoadingView.isInitialized) return
        chartLoadingView.visibility = if (roomsLoaded && bookingsLoaded && (rooms.isNotEmpty() || initialLoadGraceFinished)) View.GONE else View.VISIBLE
    }

    private fun showMenu(anchor: android.view.View) {
        PopupMenu(this, anchor).apply {
            menu.add("Organization")
            menu.add("Properties")
            menu.add("Manage Orders")
            menu.add("Bills")
            menu.add("Users")
            menu.add("Share Staff Sheet")
            menu.add("Export Bookings")
            menu.add("Booking Records")
            menu.add("Logout")

            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Organization" -> showHotelDialog()
                    "Properties" -> showPropertiesDialog()
                    "Manage Orders" -> openFoodBilling(FoodBillingActivity.MODE_ACTIVE_ORDERS)
                    "Bills" -> openFoodBilling(FoodBillingActivity.MODE_BILLS_ARCHIVE)
                    "Users" -> showAddUserDialog()
                    "Share Staff Sheet" -> shareStaffSheetImage()
                    "Export Bookings" -> showExportOptionsDialog()
                    "Booking Records" -> openBookingRecords()
                    "Logout" -> logout()
                }
                true
            }

            show()
        }
    }

    private fun openFoodBilling(mode: String) {
        startActivity(Intent(this, FoodBillingActivity::class.java).apply {
            putExtra(FoodBillingActivity.EXTRA_HOTEL_REMOTE_ID, repository.hotelRemoteId)
            putExtra(FoodBillingActivity.EXTRA_OPEN_MODE, mode)
        })
    }

    private fun openBookingRecords() {
        startActivity(Intent(this, BookingRecordsActivity::class.java).apply {
            putExtra(BookingRecordsActivity.EXTRA_HOTEL_REMOTE_ID, repository.hotelRemoteId)
        })
    }

    private fun openPropertyFoodMenu(property: ManagedPropertyEntity) {
        startActivity(Intent(this, FoodBillingActivity::class.java).apply {
            putExtra(FoodBillingActivity.EXTRA_HOTEL_REMOTE_ID, repository.hotelRemoteId)
            putExtra(FoodBillingActivity.EXTRA_OPEN_MODE, FoodBillingActivity.MODE_FOOD_MENU)
            putExtra(FoodBillingActivity.EXTRA_PROPERTY_REMOTE_ID, property.remoteId)
        })
    }

    private fun showServicesDialog(property: ManagedPropertyEntity? = null) {
        if (AccountPermission.EDIT_BOOKINGS !in currentPermissions) {
            Toast.makeText(this, "You do not have permission to edit service catalog.", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = mutableListOf("Add Service")
        val visibleServices = serviceMenuItems
            .filter { service -> property == null || service.propertyRemoteId == property.remoteId }
        labels.addAll(visibleServices.map { service ->
            buildString {
                append(service.serviceName)
                append(" - Rs ").append(formatPlainAmount(service.price))
                append(" | GST ").append(formatPlainAmount(service.gstRatePercent)).append("%")
                service.sacCode?.takeIf { it.isNotBlank() }?.let { append(" | SAC ").append(it) }
            }
        })

        AlertDialog.Builder(this)
            .setTitle(property?.let { "Other Services - ${it.propertyName}" } ?: "Services")
            .setItems(labels.toTypedArray()) { _, index ->
                if (index == 0) {
                    showServiceEditorDialog(null, property)
                } else {
                    showServiceEditorDialog(visibleServices[index - 1], property)
                }
            }
            .setNegativeButton("Back") { _, _ ->
                property?.let { showPropertyOptionsDialog(it) }
            }
            .show()
    }

    private fun showServiceEditorDialog(
        service: ServiceMenuItemEntity?,
        lockedProperty: ManagedPropertyEntity? = null
    ) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 0)
        }

        val serviceName = EditText(this).apply {
            hint = "Service name, e.g. Bonfire"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(service?.serviceName.orEmpty())
            setSelection(text.length)
        }
        val price = serviceNumberEditText("Price", service?.price)
        val gstRate = serviceNumberEditText("GST %", service?.gstRatePercent ?: 18.0)
        val sacCode = EditText(this).apply {
            hint = "SAC / HSN code"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(service?.sacCode.orEmpty())
        }
        val unitLabel = EditText(this).apply {
            hint = "Unit, e.g. Per event / Per person / 2 Hours"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(service?.unitLabel.orEmpty())
        }
        val categoryName = EditText(this).apply {
            hint = "Category, e.g. Outdoor Activity"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(service?.categoryName.orEmpty())
        }
        val description = EditText(this).apply {
            hint = "Description optional"
            minLines = 2
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(service?.description.orEmpty())
        }
        val taxOptions = listOf("Tax inclusive", "Tax extra")
        val taxSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@BookingChartActivity,
                android.R.layout.simple_spinner_item,
                taxOptions
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(if (service?.taxInclusive == false) 1 else 0)
        }
        listOf(serviceName, price, gstRate, sacCode, unitLabel, categoryName, description)
            .forEach { layout.addView(it) }
        layout.addView(TextView(this).apply {
            text = "Tax setting"
            setPadding(0, 18, 0, 4)
            setTextColor(Color.parseColor("#666666"))
        })
        layout.addView(taxSpinner)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (service == null) "Add Service" else "Edit Service")
            .setView(layout)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { _, _ ->
                showServicesDialog(lockedProperty)
            }
            .apply {
                if (service != null) setNeutralButton("Delete", null)
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val cleanName = serviceName.text.toString().trim()
                if (cleanName.isEmpty()) {
                    serviceName.error = "Enter service name"
                    return@setOnClickListener
                }
                if (price.numberValue() <= 0.0) {
                    price.error = "Enter price"
                    return@setOnClickListener
                }
                foodBillingRepository.saveServiceMenuItem(
                    existing = service,
                    serviceName = cleanName,
                    categoryName = categoryName.text.toString(),
                    description = description.text.toString(),
                    unitLabel = unitLabel.text.toString(),
                    price = price.numberValue(),
                    sacCode = sacCode.text.toString(),
                    gstRatePercent = gstRate.numberValue(),
                    taxInclusive = taxSpinner.selectedItemPosition == 0,
                    propertyRemoteId = lockedProperty?.remoteId
                        ?: service?.propertyRemoteId,
                    sortOrder = service?.sortOrder ?: serviceMenuItems.size
                )
                Toast.makeText(this, "Service saved", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                showServicesDialog(lockedProperty)
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                service?.let { foodBillingRepository.deleteServiceMenuItem(it) }
                Toast.makeText(this, "Service deleted", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                showServicesDialog(lockedProperty)
            }
        }
        dialog.show()
    }

    private fun serviceNumberEditText(hintText: String, value: Double?): EditText {
        return EditText(this).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            value?.takeIf { it > 0.0 }?.let { setText(formatPlainAmount(it)) }
        }
    }

    private fun formatPlainAmount(value: Double): String {
        return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }

    private fun showAddUserDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 0)
        }

        val displayNameInput = EditText(this).apply {
            hint = "Staff name"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val emailInput = EditText(this).apply {
            hint = "Email"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val passwordInput = EditText(this).apply {
            hint = "Temporary password"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val roleLabels = listOf("Staff", "Manager")
        val roleSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@BookingChartActivity,
                android.R.layout.simple_spinner_item,
                roleLabels
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

        layout.addView(displayNameInput)
        layout.addView(emailInput)
        layout.addView(passwordInput)
        layout.addView(TextView(this).apply {
            text = "Role"
            setPadding(0, 18, 0, 4)
        })
        layout.addView(roleSpinner)
        layout.addView(TextView(this).apply {
            text = "Staff can manage bookings. Managers can also add users."
            setPadding(0, 18, 0, 0)
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
        })

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add User")
            .setView(layout)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            addButton.setOnClickListener {
                val email = emailInput.text.toString().trim()
                val password = passwordInput.text.toString()
                val displayName = displayNameInput.text.toString().trim()
                val role = if (roleSpinner.selectedItemPosition == 1) "MANAGER" else "STAFF"

                when {
                    email.isEmpty() || !email.contains("@") -> {
                        Toast.makeText(this, "Enter a valid email", Toast.LENGTH_SHORT).show()
                    }
                    password.length < 8 -> {
                        Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        addButton.isEnabled = false
                        lifecycleScope.launch {
                            runCatching {
                                accessManager.createHotelUser(
                                    email = email,
                                    password = password,
                                    displayName = displayName,
                                    role = role
                                )
                            }.onSuccess { user ->
                                val action = if (user.created) "created" else "connected"
                                Toast.makeText(
                                    this@BookingChartActivity,
                                    "User $action. Share login details with staff.",
                                    Toast.LENGTH_LONG
                                ).show()
                                dialog.dismiss()
                            }.onFailure { error ->
                                addButton.isEnabled = true
                                Toast.makeText(
                                    this@BookingChartActivity,
                                    readableBackendError(error),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun readableBackendError(error: Throwable): String {
        val message = error.message.orEmpty()
        return message.substringAfter(": ", message)
            .substringAfter(":", message)
            .ifBlank { "Could not add user. Please try again." }
    }

    private fun showRoomsMenu(property: ManagedPropertyEntity? = null) {
        val options = arrayOf("Add Room", "Manage Rooms")
        AlertDialog.Builder(this)
            .setTitle(property?.let { "Rooms - ${it.propertyName}" } ?: "Rooms")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddRoomDialog(property)
                    1 -> showManageRoomsDialog(property)
                }
            }
            .setNegativeButton("Back") { _, _ ->
                property?.let { showPropertyOptionsDialog(it) }
            }
            .show()
    }

    private fun showSourcesDialog(property: ManagedPropertyEntity? = null) {
        val names = mutableListOf("Add Source")
        val visibleSources = sources
            .filter { source -> property == null || source.propertyRemoteId == property.remoteId }
            .sortedWith(compareBy<BookingSourceEntity> { it.sourceName.lowercase() })
        names.addAll(visibleSources.map { "${it.sourceName} (${it.sourceType.displaySourceType()})" })
        AlertDialog.Builder(this)
            .setTitle(property?.let { "Booking Sources - ${it.propertyName}" } ?: "Sources")
            .setItems(names.toTypedArray()) { _, index ->
                if (index == 0) {
                    showSourceEditorDialog(null, property)
                } else {
                    showSourceEditorDialog(visibleSources[index - 1], property)
                }
            }
            .setNegativeButton("Back") { _, _ ->
                property?.let { showPropertyOptionsDialog(it) }
            }
            .show()
    }

    private fun showSourceEditorDialog(
        source: BookingSourceEntity?,
        lockedProperty: ManagedPropertyEntity? = null
    ) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 0)
        }
        val nameInput = EditText(this).apply {
            hint = "Source name"
            setSingleLine(true)
            setText(source?.sourceName.orEmpty())
            setSelection(text.length)
        }
        val typeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@BookingChartActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Direct", "Agent", "OTA")
            )
            setSelection(
                when (source?.sourceType) {
                    BookingSourceType.AGENT -> 1
                    BookingSourceType.OTA -> 2
                    else -> 0
                }
            )
        }
        val commission = sourceEditText("Commission %", source?.commissionPercent)
        val commissionGst = sourceEditText("GST on commission %", source?.commissionGstPercent)
        val tcs = sourceEditText("TCS %", source?.tcsPercent)
        val tds = sourceEditText("TDS %", source?.tdsPercent)
        val fee = sourceEditText("Fixed fee", source?.fixedFee)
        val propertyLabel = lockedProperty?.let { property ->
            TextView(this).apply {
                text = "Property: ${property.propertyName}"
                setPadding(0, 18, 0, 4)
            }
        }

        layout.addView(nameInput)
        propertyLabel?.let { layout.addView(it) }
        layout.addView(TextView(this).apply {
            text = "Type"
            setPadding(0, 18, 0, 4)
        })
        layout.addView(typeSpinner)
        listOf(commission, commissionGst, tcs, tds, fee).forEach { layout.addView(it) }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (source == null) "Add Source" else "Edit Source")
            .setView(layout)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { _, _ ->
                showSourcesDialog(lockedProperty)
            }
            .apply {
                if (source != null) {
                    setNeutralButton("Delete") { _, _ ->
                        repository.deleteSource(source)
                        showSourcesDialog(lockedProperty)
                    }
                }
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Enter source name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val type = when (typeSpinner.selectedItemPosition) {
                    1 -> BookingSourceType.AGENT
                    2 -> BookingSourceType.OTA
                    else -> BookingSourceType.DIRECT
                }
                repository.saveSource(
                    existing = source,
                    sourceName = name,
                    sourceType = type,
                    commissionPercent = commission.numberValue(),
                    commissionGstPercent = commissionGst.numberValue(),
                    tcsPercent = tcs.numberValue(),
                    tdsPercent = tds.numberValue(),
                    fixedFee = fee.numberValue(),
                    propertyRemoteId = lockedProperty?.remoteId ?: source?.propertyRemoteId
                )
                dialog.dismiss()
                showSourcesDialog(lockedProperty)
            }
        }
        dialog.show()
    }

    private fun sourceEditText(hintText: String, value: Double?): EditText {
        return EditText(this).apply {
            hint = hintText
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            val amount = value ?: 0.0
            if (amount > 0.0) setText(if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString())
        }
    }

    private fun EditText.numberValue(): Double {
        return text.toString().trim().toDoubleOrNull() ?: 0.0
    }

    private fun String.displaySourceType(): String {
        return when (this) {
            BookingSourceType.AGENT -> "Agent"
            BookingSourceType.OTA -> "OTA"
            else -> "Direct"
        }
    }
    private fun showHotelDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_hotel, null)
        val hotelName = dialogView.findViewById<EditText>(R.id.etHotelName)
        val phone = dialogView.findViewById<EditText>(R.id.etHotelPhone)
        val address = dialogView.findViewById<EditText>(R.id.etHotelAddress)

        currentHotel?.let { hotel ->
            hotelName.setText(hotel.hotelName)
            phone.setText(hotel.phone.orEmpty())
            address.setText(hotel.address.orEmpty())
        } ?: hotelName.setText("Booking Register")

        val dialog = AlertDialog.Builder(this)
            .setTitle("Organization")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                repository.saveHotel(
                    hotelName = hotelName.text.toString(),
                    gstNumber = currentHotel?.gstNumber,
                    address = address.text.toString(),
                    phone = phone.text.toString()
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showPropertiesDialog() {
        val names = mutableListOf("Add Property")
        names.addAll(managedProperties.map { property ->
            val gst = property.gstNumber?.takeIf { it.isNotBlank() }?.let { " - GST $it" }.orEmpty()
            "${property.propertyName}$gst"
        })

        AlertDialog.Builder(this)
            .setTitle("Properties / GST Billing")
            .setItems(names.toTypedArray()) { _, index ->
                if (index == 0) {
                    showPropertyEditorDialog(null)
                } else {
                    showPropertyOptionsDialog(managedProperties[index - 1])
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showPropertyOptionsDialog(property: ManagedPropertyEntity) {
        val options = arrayOf(
            "Property Details",
            "Rooms",
            "Food Menu",
            "Room GST Slabs",
            "Booking Sources",
            "Other Services"
        )


        AlertDialog.Builder(this)
            .setTitle(property.propertyName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showPropertyEditorDialog(property)
                    1 -> showRoomsMenu(property)
                    2 -> openPropertyFoodMenu(property)
                    3 -> showRoomGstSlabsDialog()
                    4 -> showSourcesDialog(property)
                    5 -> showServicesDialog(property)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showPropertyEditorDialog(property: ManagedPropertyEntity?) {
        if (property != null) {
            lifecycleScope.launch {
                showPropertyEditorDialog(
                    property = property,
                    billingLocked = repository.hasIssuedBillsForProperty(property.remoteId)
                )
            }
            return
        }
        showPropertyEditorDialog(property = null, billingLocked = false)
    }

    private fun showPropertyEditorDialog(
        property: ManagedPropertyEntity?,
        billingLocked: Boolean
    ) {
        var billingIdentityUnlocked = !billingLocked
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 0)
        }

        val propertyName = EditText(this).apply {
            hint = "Property name"
            setSingleLine(true)
            setText(property?.propertyName.orEmpty())
            setSelection(text.length)
        }
        val legalName = EditText(this).apply {
            hint = "Legal / billing name (supplier on GST bill)"
            setSingleLine(true)
            setText(property?.legalName.orEmpty())
        }
        val gstNumber = EditText(this).apply {
            hint = "GSTIN for this property"
            setSingleLine(true)
            setText(property?.gstNumber.orEmpty())
        }
        val phone = EditText(this).apply {
            hint = "Phone"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_PHONE
            setText(property?.phone.orEmpty())
        }
        val email = EditText(this).apply {
            hint = "Email"
            setSingleLine(true)
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(property?.email.orEmpty())
        }
        val invoicePrefix = EditText(this).apply {
            hint = "Invoice prefix"
            setSingleLine(true)
            setText(property?.invoicePrefix.orEmpty())
        }
        val state = EditText(this).apply {
            hint = "State"
            setSingleLine(true)
            setText(property?.state.orEmpty())
        }
        val address = EditText(this).apply {
            hint = "Address"
            minLines = 2
            setText(property?.address.orEmpty())
        }

        fun setBillingFieldsEditable(editable: Boolean) {
            listOf(legalName, gstNumber, invoicePrefix, state, address).forEach { field ->
                field.isEnabled = editable
                field.alpha = if (editable) 1f else 0.72f
            }
        }

        if (billingLocked) {
            setBillingFieldsEditable(false)
            layout.addView(TextView(this).apply {
                text = "Billing identity is locked because this property already has issued bills. Legal name, GSTIN, invoice prefix, state and billing address are preserved for invoice safety."
                setPadding(0, 0, 0, 16)
                textSize = 13f
                setTextColor(Color.parseColor("#8A5A00"))
            })
        }

        listOf(propertyName, legalName, gstNumber, phone, email, invoicePrefix, state, address)
            .forEach { layout.addView(it) }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (property == null) "Add Property Billing Profile" else "Edit Property Billing Profile")
            .setView(layout)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { _, _ ->
                if (property != null) {
                    showPropertyOptionsDialog(property)
                } else {
                    showPropertiesDialog()
                }
            }
            .apply {
                if (property != null) {
                    setNeutralButton(if (billingLocked) "Unlock Billing" else "Delete", null)
                }
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = propertyName.text.toString().trim()
                if (name.isEmpty()) {
                    propertyName.error = "Enter property name"
                    return@setOnClickListener
                }
                val normalizedGstin = GstinValidator.normalize(gstNumber.text.toString())
                if (!GstinValidator.isBlankOrValid(normalizedGstin)) {
                    gstNumber.error = "Enter valid 15-character GSTIN or leave blank"
                    return@setOnClickListener
                }
                repository.saveManagedProperty(
                    existing = property,
                    propertyName = name,
                    legalName = legalName.text.toString(),
                    gstNumber = normalizedGstin,
                    address = address.text.toString(),
                    phone = phone.text.toString(),
                    email = email.text.toString(),
                    invoicePrefix = invoicePrefix.text.toString(),
                    state = state.text.toString(),
                    allowBillingIdentityChange = billingIdentityUnlocked
                )
                dialog.dismiss()
                if (property != null) {
                    showPropertyOptionsDialog(property)
                } else {
                    showPropertiesDialog()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                val currentProperty = property ?: return@setOnClickListener
                if (billingLocked && !billingIdentityUnlocked) {
                    showUnlockBillingIdentityDialog {
                        billingIdentityUnlocked = true
                        setBillingFieldsEditable(true)
                        Toast.makeText(
                            this,
                            "Billing fields unlocked. Changes will affect future bills only.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@setOnClickListener
                }
                val assignedRooms = rooms.count { it.propertyRemoteId == property?.remoteId }
                if (assignedRooms > 0) {
                    Toast.makeText(
                        this,
                        "Remove this property from $assignedRooms room(s) before deleting",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    if (repository.hasIssuedBillsForProperty(currentProperty.remoteId)) {
                        Toast.makeText(
                            this@BookingChartActivity,
                            "This property has issued bills. Billing profiles with bills cannot be deleted.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    repository.deleteManagedProperty(currentProperty)
                    dialog.dismiss()
                    showPropertiesDialog()
                }
            }
        }
        dialog.show()
    }

    private fun showUnlockBillingIdentityDialog(onConfirmed: () -> Unit) {
        val confirmationText = "CHANGE BILLING DETAILS"
        val input = EditText(this).apply {
            hint = confirmationText
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 0)
            addView(TextView(this@BookingChartActivity).apply {
                text = "Old bills will not change. New bills will use the updated legal name, GSTIN, prefix, state and address. Type $confirmationText to continue."
                setPadding(0, 0, 0, 16)
                textSize = 13f
                setTextColor(Color.parseColor("#444444"))
            })
            addView(input)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Unlock Billing Details")
            .setView(layout)
            .setPositiveButton("Unlock", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (input.text.toString().trim().uppercase(Locale.ROOT) != confirmationText) {
                    input.error = "Type $confirmationText"
                    return@setOnClickListener
                }
                dialog.dismiss()
                onConfirmed()
            }
        }
        dialog.show()
    }

    private fun showAddRoomDialog(property: ManagedPropertyEntity? = null) {
        val editor = createRoomEditor(null, property)

        AlertDialog.Builder(this)
            .setTitle(property?.let { "Add Room - ${it.propertyName}" } ?: "Add Room")
            .setView(editor.view)
            .setPositiveButton("Save") { _, _ ->
                val roomName = editor.roomName.text.toString().trim()
                if (roomName.isNotEmpty()) {
                    repository.addRoom(
                        roomName = roomName,
                        categoryName = "",
                        categoryColor = "#EEF0F2",
                        propertyRemoteId = editor.selectedPropertyRemoteId()
                    )
                    property?.let { showRoomsMenu(it) }
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                property?.let { showRoomsMenu(it) }
            }
            .show()
    }

    private fun showBookingDialog(
        existing: BookingEntity?,
        selectedRoom: RoomEntity?,
        selectedCheckInMillis: Long,
        roomRateLocked: Boolean
    ) {
        val canEditBooking = AccountPermission.EDIT_BOOKINGS in currentPermissions
        if (existing == null && !canEditBooking) {
            Toast.makeText(this, "You do not have permission to create or edit bookings.", Toast.LENGTH_SHORT).show()
            return
        }
        if (rooms.isEmpty()) {
            Toast.makeText(
                this,
                "Please add rooms first from Menu > Add Room",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val bookingDialog = BookingDialog(
            context = this,
            hotelRemoteId = repository.hotelRemoteId,
            rooms = rooms.toList(),
            bookings = bookings.toList(),
            bookingPayments = payments.toList(),
            bookingFinancialLines = financialLines.toList(),
            bookingAccountingCharges = accountingCharges.toList(),
            foodOrders = foodOrders.toList(),
            foodOrderItems = foodOrderItems.toList(),
            bookingSources = bookingSourcesForDialog(existing, selectedRoom),
            managedProperties = managedProperties.toList(),
            roomGstSlabs = roomGstSlabs.toList(),
            hotelHasGst = bookingDialogHasPropertyGst(existing, selectedRoom),
            selectedRoom = selectedRoom,
            selectedCheckInMillis = selectedCheckInMillis,
            existingBooking = existing,
            canEditBooking = canEditBooking,
            roomRateLocked = roomRateLocked,
            onBookingSaved = { booking, lines, onResult ->
                lifecycleScope.launch {
                    onResult(repository.saveBookingWithFinancialLines(booking, lines))
                }
            },
            onBookingDeleted = { booking, reason -> repository.cancelBooking(booking, reason) },
            onPaymentSaved = { booking, amount, paymentType, paymentCategory, note, onResult ->
                lifecycleScope.launch {
                    onResult(repository.addBookingPayment(booking, amount, paymentType = paymentType, paymentCategory = paymentCategory, note = note))
                }
            },
            onAccountingChargeSaved = { booking, chargeType, amount, description, reason, accountBucket, onResult ->
                lifecycleScope.launch {
                    onResult(
                        repository.addBookingAccountingCharge(
                            booking = booking,
                            chargeType = chargeType,
                            amount = amount,
                            description = description,
                            reason = reason,
                            accountBucket = accountBucket
                        )
                    )
                }
            },
            onFinalBillGenerated = { booking, onResult ->
                lifecycleScope.launch {
                    onResult(repository.generateFinalBookingBill(booking))
                }
            }
        )
        activeBookingDialog = bookingDialog
        bookingDialog.show()
    }

    private fun refreshOpenBookingDialogFolioData() {
        activeBookingDialog
            ?.takeIf { it.isShowing() }
            ?.refreshFolioData(
                updatedPayments = payments.toList(),
                updatedFinancialLines = financialLines.toList(),
                updatedAccountingCharges = accountingCharges.toList(),
                updatedFoodOrders = foodOrders.toList(),
                updatedFoodOrderItems = foodOrderItems.toList()
            )
    }

    private fun bookingDialogHasPropertyGst(existing: BookingEntity?, selectedRoom: RoomEntity?): Boolean {
        val propertyIds = linkedSetOf<String>()
        existing?.propertyRemoteId?.takeIf { it.isNotBlank() }?.let { propertyIds.add(it) }
        selectedRoom?.propertyRemoteId?.takeIf { it.isNotBlank() }?.let { propertyIds.add(it) }
        existing?.roomRemoteIds
            ?.mapNotNull { roomId -> rooms.firstOrNull { room -> room.remoteId == roomId }?.propertyRemoteId }
            ?.filter { it.isNotBlank() }
            ?.let { propertyIds.addAll(it) }

        return managedProperties.any { property ->
            property.remoteId in propertyIds && GstinValidator.isValid(property.gstNumber)
        }
    }

    private fun bookingSourcesForDialog(
        existing: BookingEntity?,
        selectedRoom: RoomEntity?
    ): List<BookingSourceEntity> {
        val propertyRemoteId = selectedBookingPropertyRemoteId(existing, selectedRoom)
        val visibleSources = if (propertyRemoteId == null) {
            sources.filter { source -> source.propertyRemoteId.isNullOrBlank() }
        } else {
            sources.filter { source -> source.propertyRemoteId == propertyRemoteId }
        }
        return visibleSources.ifEmpty {
            sources.filter { source -> source.propertyRemoteId.isNullOrBlank() }
        }
    }

    private fun selectedBookingPropertyRemoteId(
        existing: BookingEntity?,
        selectedRoom: RoomEntity?
    ): String? {
        val propertyIds = linkedSetOf<String>()
        existing?.propertyRemoteId?.takeIf { it.isNotBlank() }?.let { propertyIds.add(it) }
        selectedRoom?.propertyRemoteId?.takeIf { it.isNotBlank() }?.let { propertyIds.add(it) }
        existing?.roomRemoteIds
            ?.mapNotNull { roomId -> rooms.firstOrNull { room -> room.remoteId == roomId }?.propertyRemoteId }
            ?.filter { it.isNotBlank() }
            ?.let { propertyIds.addAll(it) }
        return propertyIds.singleOrNull()
    }

    private fun operationalBookingWindow(): Pair<Long, Long> {
        val today = startOfDay(System.currentTimeMillis())
        val start = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_MONTH, -370)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_MONTH, 730)
        }.timeInMillis
        return start to end
    }

    private fun visibleAndUnsyncedBookings(): List<BookingEntity> {
        return (bookings + unsyncedBookings).distinctBy { it.remoteId }
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
    private fun logout() {
        repository.stopRealtimeSync()
        clearCachedAccess()
        FirebaseAuth.getInstance().signOut()

        val intent = android.content.Intent(this, com.example.bookingregister.ui.login.LoginActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun clearCachedAccess() {
        getSharedPreferences("cached_hotel_access", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun showManageRoomsDialog(property: ManagedPropertyEntity? = null) {
        val visibleRooms = rooms
            .filter { room -> property == null || room.propertyRemoteId == property.remoteId }
        if (visibleRooms.isEmpty()) {
            Toast.makeText(this, "No rooms available", Toast.LENGTH_SHORT).show()
            property?.let { showRoomsMenu(it) }
            return
        }

        val roomNames = visibleRooms.map { room ->
            val status = RoomLifecycleStatus.normalize(room.lifecycleStatus)
            if (status == RoomLifecycleStatus.ACTIVE) room.roomName else "${room.roomName}  [$status]"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(property?.let { "Manage Rooms - ${it.propertyName}" } ?: "Manage Rooms")
            .setItems(roomNames) { _, index ->
                val selectedRoom = visibleRooms[index]
                showRoomOptions(selectedRoom, property)
            }
            .setNegativeButton("Back") { _, _ ->
                showRoomsMenu(property)
            }
            .show()
    }

    private fun showRoomOptions(room: RoomEntity, property: ManagedPropertyEntity? = null) {
        val status = RoomLifecycleStatus.normalize(room.lifecycleStatus)
        val options = when (status) {
            RoomLifecycleStatus.ACTIVE -> arrayOf("Edit", "Move Up", "Move Down", "Disable", "Retire", "Delete")
            RoomLifecycleStatus.DISABLED -> arrayOf("Reactivate", "Retire", "Delete", "View reason")
            else -> arrayOf("View retirement details")
        }

        AlertDialog.Builder(this)
            .setTitle(room.roomName)
            .setItems(options) { _, which ->
                when (status) {
                    RoomLifecycleStatus.ACTIVE -> when (which) {
                        0 -> showEditRoomDialog(room, property)
                        1 -> {
                            repository.moveRoom(room, -1)
                            showManageRoomsDialog(property)
                        }
                        2 -> {
                            repository.moveRoom(room, 1)
                            showManageRoomsDialog(property)
                        }
                        3 -> showRoomLifecycleReasonDialog(room, RoomLifecycleStatus.DISABLED, property)
                        4 -> showRoomLifecycleReasonDialog(room, RoomLifecycleStatus.RETIRED, property)
                        5 -> confirmDeleteRoom(room, property)
                    }
                    RoomLifecycleStatus.DISABLED -> when (which) {
                        0 -> runRoomLifecycleAction(property) { repository.reactivateRoom(room) }
                        1 -> showRoomLifecycleReasonDialog(room, RoomLifecycleStatus.RETIRED, property)
                        2 -> confirmDeleteRoom(room, property)
                        3 -> showRoomLifecycleDetails(room)
                    }
                    else -> showRoomLifecycleDetails(room)
                }
            }
            .setNegativeButton("Back") { _, _ ->
                showManageRoomsDialog(property)
            }
            .show()
    }
    private fun showEditRoomDialog(room: RoomEntity, property: ManagedPropertyEntity? = null) {
        val editor = createRoomEditor(room, property)

        AlertDialog.Builder(this)
            .setTitle(property?.let { "Edit Room - ${it.propertyName}" } ?: "Edit Room")
            .setView(editor.view)
            .setPositiveButton("Save") { _, _ ->
                repository.updateRoom(
                    room = room,
                    newRoomName = editor.roomName.text.toString(),
                    newCategoryName = "",
                    newCategoryColor = "#EEF0F2",
                    propertyRemoteId = editor.selectedPropertyRemoteId()
                )
                showManageRoomsDialog(property)
            }
            .setNegativeButton("Cancel") { _, _ ->
                showRoomOptions(room, property)
            }
            .show()
    }

    private fun createRoomEditor(
        room: RoomEntity?,
        lockedProperty: ManagedPropertyEntity? = null
    ): RoomEditor {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 0)
        }

        val roomName = EditText(this).apply {
            hint = "Room name"
            setSingleLine(true)
            setText(room?.roomName.orEmpty())
            setSelection(text.length)
        }

        layout.addView(roomName)

        return RoomEditor(
            view = layout,
            roomName = roomName,
            propertyRemoteId = lockedProperty?.remoteId ?: room?.propertyRemoteId
        )
    }

    private fun confirmDeleteRoom(room: RoomEntity, property: ManagedPropertyEntity? = null) {
        AlertDialog.Builder(this)
            .setTitle("Delete Room")
            .setMessage("Delete is allowed only when this room has no booking or billing history.")
            .setPositiveButton("Delete") { _, _ ->
                runRoomLifecycleAction(property) { repository.deleteRoom(room) }
            }
            .setNegativeButton("Cancel") { _, _ ->
                showRoomOptions(room, property)
            }
            .show()
    }

    private fun showRoomLifecycleReasonDialog(
        room: RoomEntity,
        targetStatus: String,
        property: ManagedPropertyEntity?
    ) {
        val reason = EditText(this).apply {
            hint = "Mandatory reason"
            setSingleLine(false)
            minLines = 2
        }
        val action = if (targetStatus == RoomLifecycleStatus.RETIRED) "Retire" else "Disable"
        AlertDialog.Builder(this)
            .setTitle("$action ${room.roomName}")
            .setMessage("Current and future bookings must first be moved, cancelled, or checked out.")
            .setView(reason)
            .setPositiveButton(action) { _, _ ->
                runRoomLifecycleAction(property) {
                    if (targetStatus == RoomLifecycleStatus.RETIRED) {
                        repository.retireRoom(room, reason.text.toString())
                    } else {
                        repository.disableRoom(room, reason.text.toString())
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRoomLifecycleDetails(room: RoomEntity) {
        val status = RoomLifecycleStatus.normalize(room.lifecycleStatus)
        AlertDialog.Builder(this)
            .setTitle("${room.roomName} - $status")
            .setMessage(room.lifecycleReason?.takeIf { it.isNotBlank() } ?: "No reason recorded.")
            .setPositiveButton("Close", null)
            .show()
    }

    private fun runRoomLifecycleAction(
        property: ManagedPropertyEntity?,
        action: suspend () -> SaveResult
    ) {
        lifecycleScope.launch {
            when (val result = action()) {
                is SaveResult.Success -> Toast.makeText(
                    this@BookingChartActivity,
                    "Room updated safely.",
                    Toast.LENGTH_SHORT
                ).show()
                is SaveResult.Error -> AlertDialog.Builder(this@BookingChartActivity)
                    .setTitle("Room not changed")
                    .setMessage(result.message)
                    .setPositiveButton("OK", null)
                    .show()
                is SaveResult.Conflict -> AlertDialog.Builder(this@BookingChartActivity)
                    .setTitle("Room not changed")
                    .setMessage(result.message)
                    .setPositiveButton("OK", null)
                    .show()
            }
            showManageRoomsDialog(property)
        }
    }
    private fun exportBookingsCsv(filterType: String) {
        lifecycleScope.launch {
        val todayStart = startOfDay(System.currentTimeMillis())
        val todayEnd = todayStart + 24L * 60L * 60L * 1000L
        val sourceBookings = when (filterType) {
            "today_checkins", "today_checkouts" -> repository.getBookingsForWindow(todayStart, todayEnd)
            else -> repository.getBookings()
        }

        val filteredBookings = sourceBookings
            .filter { !it.isDeleted }
            .filter { booking ->
                when (filterType) {
                    "today_checkins" -> booking.checkInMillis in todayStart until todayEnd
                    "today_checkouts" -> booking.checkOutMillis in todayStart until todayEnd
                    else -> true
                }
            }
            .sortedBy { it.checkInMillis }

        if (filteredBookings.isEmpty()) {
            Toast.makeText(this@BookingChartActivity, "No bookings found for this export", Toast.LENGTH_SHORT).show()
            return@launch
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val exportName = when (filterType) {
            "today_checkins" -> "today_checkins"
            "today_checkouts" -> "today_checkouts"
            else -> "all_bookings"
        }

        val fileName = "${exportName}_${System.currentTimeMillis()}.csv"
        val file = File(cacheDir, fileName)

        FileWriter(file).use { writer ->
            writer.appendLine("Guest Name,Mobile,Source,Adults,Kids,Rooms,Check In,Check Out,Total,Paid,Balance,Payment Status,Notes")

            filteredBookings.forEach { booking ->
                val roomNames = rooms
                    .filter { booking.roomRemoteIds.contains(it.remoteId) }
                    .joinToString(" | ") { it.roomName }

                writer.appendLine(
                    listOf(
                        booking.guestName,
                        booking.guestMobile.orEmpty(),
                        booking.sourceName.orEmpty(),
                        booking.adultCount.toString(),
                        booking.childCount.toString(),
                        roomNames,
                        dateFormat.format(Date(booking.checkInMillis)),
                        dateFormat.format(Date(booking.checkOutMillis)),
                        booking.receivable.toString(),
                        booking.paid.toString(),
                        booking.balance.toString(),
                        booking.paymentStatus,
                        booking.notes.orEmpty()
                    ).joinToString(",") { csvSafe(it) }
                )
            }
        }

        val uri = FileProvider.getUriForFile(
            this@BookingChartActivity,
            "${packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Share Booking Export"))
        }
    }

    private fun shareStaffSheetImage() {
        val todayStart = startOfDay(System.currentTimeMillis())
        val todayEnd = todayStart + 24L * 60L * 60L * 1000L

        val checkIns = bookings
            .filter { !it.isDeleted && it.bookingStatus != BookingStatus.CANCELLED && it.checkInMillis in todayStart until todayEnd }
            .sortedBy { it.checkInMillis }
            .map { it.toStaffMovement("CHECK-IN", it.checkInMillis) }

        val checkOuts = bookings
            .filter { !it.isDeleted && it.bookingStatus != BookingStatus.CANCELLED && it.checkOutMillis in todayStart until todayEnd }
            .sortedBy { it.checkOutMillis }
            .map { it.toStaffMovement("CHECK-OUT", it.checkOutMillis) }

        if (checkIns.isEmpty() && checkOuts.isEmpty()) {
            Toast.makeText(this, "No check-ins or check-outs for today", Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = createStaffSheetBitmap(checkIns, checkOuts)
        val file = File(cacheDir, "staff_sheet_${System.currentTimeMillis()}.png")

        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(
            this@BookingChartActivity,
            "${packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Share Staff Sheet"))
    }

    private fun BookingEntity.toStaffMovement(type: String, dateMillis: Long): StaffMovement {
        val roomNames = rooms
            .filter { roomRemoteIds.contains(it.remoteId) }
            .joinToString(", ") { it.roomName }
            .ifBlank { "Room not assigned" }

        return StaffMovement(
            type = type,
            rooms = roomNames,
            guestName = guestName,
            guestMobile = guestMobile.orEmpty(),
            dateText = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(dateMillis)),
            notes = notes.orEmpty()
        )
    }

    private fun createStaffSheetBitmap(
        checkIns: List<StaffMovement>,
        checkOuts: List<StaffMovement>
    ): Bitmap {
        val width = 1080
        val padding = 56f
        val sectionHeaderHeight = 68
        val cardHeight = 190
        val emptyHeight = 74
        val footerHeight = 92
        val contentHeight =
            290 +
                    sectionHeaderHeight + if (checkIns.isEmpty()) emptyHeight else checkIns.size * cardHeight +
                    38 +
                    sectionHeaderHeight + if (checkOuts.isEmpty()) emptyHeight else checkOuts.size * cardHeight +
                    footerHeight
        val height = contentHeight.coerceAtLeast(1180)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(246, 248, 247))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val hotelName = currentHotel?.hotelName?.takeIf { it.isNotBlank() } ?: "Booking Register"
        val dateText = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

        drawHeader(canvas, paint, hotelName, dateText, checkIns.size, checkOuts.size, padding)

        var y = 292f
        y = drawStaffSection(canvas, paint, "CHECK-INS", checkIns, y, padding, Color.rgb(0, 121, 107))
        y += 38f
        y = drawStaffSection(canvas, paint, "CHECK-OUTS", checkOuts, y, padding, Color.rgb(34, 91, 139))

        paint.color = Color.rgb(102, 112, 109)
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Share with front desk and housekeeping", padding, y + 48f, paint)

        return bitmap
    }

    private fun drawHeader(
        canvas: Canvas,
        paint: Paint,
        hotelName: String,
        dateText: String,
        checkInCount: Int,
        checkOutCount: Int,
        padding: Float
    ) {
        paint.color = Color.rgb(13, 49, 43)
        paint.textSize = 44f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(hotelName, padding, 82f, paint)

        paint.color = Color.rgb(83, 96, 92)
        paint.textSize = 30f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Front Desk Handover", padding, 126f, paint)
        canvas.drawText(dateText, padding, 168f, paint)

        drawSummaryChip(canvas, paint, padding, 208f, "$checkInCount Arrivals", Color.rgb(0, 121, 107))
        drawSummaryChip(canvas, paint, padding + 286f, 208f, "$checkOutCount Departures", Color.rgb(34, 91, 139))
    }

    private fun drawSummaryChip(
        canvas: Canvas,
        paint: Paint,
        left: Float,
        top: Float,
        text: String,
        color: Int
    ) {
        val rect = RectF(left, top, left + 250f, top + 58f)
        paint.color = color
        canvas.drawRoundRect(rect, 28f, 28f, paint)
        paint.color = Color.WHITE
        paint.textSize = 26f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(text, left + 24f, top + 38f, paint)
    }

    private fun drawStaffSection(
        canvas: Canvas,
        paint: Paint,
        title: String,
        items: List<StaffMovement>,
        startY: Float,
        padding: Float,
        accentColor: Int
    ): Float {
        var y = startY
        paint.color = accentColor
        paint.textSize = 30f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(title, padding, y, paint)
        y += 28f

        if (items.isEmpty()) {
            paint.color = Color.rgb(124, 134, 131)
            paint.textSize = 28f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("No entries", padding, y + 42f, paint)
            return y + 84f
        }

        items.forEach { item ->
            drawStaffCard(canvas, paint, item, y, padding, accentColor)
            y += 190f
        }

        return y
    }

    private fun drawStaffCard(
        canvas: Canvas,
        paint: Paint,
        item: StaffMovement,
        top: Float,
        padding: Float,
        accentColor: Int
    ) {
        val card = RectF(padding, top, 1080f - padding, top + 164f)
        paint.color = Color.WHITE
        canvas.drawRoundRect(card, 18f, 18f, paint)

        paint.color = Color.rgb(219, 228, 225)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(card, 18f, 18f, paint)
        paint.style = Paint.Style.FILL

        paint.color = accentColor
        paint.textSize = 26f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(item.type, padding + 28f, top + 44f, paint)

        paint.color = Color.rgb(14, 37, 34)
        paint.textSize = 40f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(item.rooms, padding + 28f, top + 92f, paint)

        paint.color = Color.rgb(29, 42, 39)
        paint.textSize = 32f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(item.guestName, padding + 340f, top + 54f, paint)

        paint.color = Color.rgb(83, 96, 92)
        paint.textSize = 27f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val mobile = item.guestMobile.ifBlank { "No mobile" }
        canvas.drawText(mobile, padding + 340f, top + 92f, paint)
        canvas.drawText(item.dateText, padding + 340f, top + 130f, paint)

        if (item.notes.isNotBlank()) {
            val note = item.notes.take(42)
            paint.color = Color.rgb(104, 84, 36)
            paint.textSize = 25f
            canvas.drawText("Note: $note", padding + 28f, top + 138f, paint)
        }
    }

    private data class StaffMovement(
        val type: String,
        val rooms: String,
        val guestName: String,
        val guestMobile: String,
        val dateText: String,
        val notes: String
    )

    private fun updateSyncIndicator() {
        val hotelName = currentHotel?.hotelName?.takeIf { it.isNotBlank() } ?: "Booking Register"

        val allSyncStates = mutableListOf<String>()

        currentHotel?.let { allSyncStates.add(it.syncState) }
        allSyncStates.addAll(managedProperties.map { it.syncState })
        allSyncStates.addAll(sources.map { it.syncState })
        allSyncStates.addAll(rooms.map { it.syncState })
        allSyncStates.addAll(visibleAndUnsyncedBookings().map { it.syncState })
        allSyncStates.addAll(unsyncedPayments.map { it.syncState })
        allSyncStates.addAll(unsyncedAccountingCharges.map { it.syncState })
        allSyncStates.addAll(financialLines.map { it.syncState })
        allSyncStates.addAll(foodOrders.map { it.syncState })
        allSyncStates.addAll(foodOrderItems.map { it.syncState })
        allSyncStates.addAll(foodBills.map { it.syncState })
        allSyncStates.addAll(foodBillItems.map { it.syncState })

        val hasFailed = hasActionableSyncFailure()
        val hasPending = allSyncStates.any { it == "PENDING" }
        val hasRealtimeError = isActionableSyncError(realtimeSyncError)
        val hasNetworkIssue = hasNetworkSyncIssue()

        titleView.text = when {
            hasNetworkIssue -> "$hotelName  Offline"
            hasRealtimeError || hasFailed -> "$hotelName  Sync issue"
            hasPending -> "$hotelName  Syncing..."
            else -> "$hotelName  Synced"
        }
    }
    private fun updateReportSummary() {
        if (!::revenueSummaryButton.isInitialized) return

        val range = RevenuePeriod.financialYearFor()
        val occupancy = occupancyCalculator.occupancyPercent(rooms, bookings, range)
        val balance = outstandingBalance

        revenueSummaryButton.text = "Revenue\nComing later"
        occupancySummaryButton.text = "Occupancy\n$occupancy%"
        balanceSummaryButton.text = "Balance\n${formatMoneyShort(balance)}"
    }

    private fun formatMoneyShort(amount: Double): String {
        val rounded = amount.toLong()
        return when {
            rounded >= 100_00_000 -> "Rs ${String.format(Locale.getDefault(), "%.1fCr", rounded / 100_00_000.0)}"
            rounded >= 100_000 -> "Rs ${String.format(Locale.getDefault(), "%.1fL", rounded / 100_000.0)}"
            rounded >= 1_000 -> "Rs ${String.format(Locale.getDefault(), "%.1fK", rounded / 1_000.0)}"
            else -> "Rs $rounded"
        }
    }

    private fun isCancelledSyncNoise(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        return message.contains("Job was cancelled", ignoreCase = true) ||
                message.contains("CancellationException", ignoreCase = true)
    }

    private fun isNetworkSyncError(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        return message.contains("Unable to resolve host", ignoreCase = true) ||
                message.contains("firestore.googleapis.com", ignoreCase = true) ||
                message.contains("UNAVAILABLE", ignoreCase = true) ||
                message.contains("network", ignoreCase = true) ||
                message.contains("offline", ignoreCase = true)
    }

    private fun isActionableSyncError(message: String?): Boolean {
        return !message.isNullOrBlank() && !isCancelledSyncNoise(message)
    }

    private fun friendlySyncError(message: String?): String? {
        if (!isActionableSyncError(message)) return null
        return if (isNetworkSyncError(message)) {
            "Internet connection is unavailable. Changes are saved on this device and will retry automatically."
        } else {
            message
        }
    }

    private fun hasActionableSyncFailure(): Boolean {
        if (isActionableSyncError(realtimeSyncError)) return true
        if (currentHotel?.syncState == "FAILED" && isActionableSyncError(currentHotel?.lastSyncError ?: "Sync failed")) return true
        if (managedProperties.any { it.syncState == "FAILED" && isActionableSyncError(it.lastSyncError ?: "Sync failed") }) return true
        if (sources.any { it.syncState == "FAILED" && isActionableSyncError(it.lastSyncError ?: "Sync failed") }) return true
        if (rooms.any { it.syncState == "FAILED" && isActionableSyncError(it.lastSyncError ?: "Sync failed") }) return true
        if (visibleAndUnsyncedBookings().any { it.syncState == "FAILED" && isActionableSyncError(it.lastSyncError ?: "Sync failed") }) return true
        if (unsyncedPayments.any { it.syncState == "FAILED" && isActionableSyncError(it.lastSyncError ?: "Sync failed") }) return true
        if (financialLines.any { it.syncState == "FAILED" && isActionableSyncError(it.lastSyncError ?: "Sync failed") }) return true
        if (unsyncedAccountingCharges.any { it.syncState == "FAILED" && isActionableSyncError(it.lastSyncError ?: "Sync failed") }) return true
        if (foodOrders.any { it.syncState == "FAILED" && isActionableSyncError(it.lastSyncError ?: "Sync failed") }) return true
        if (foodOrderItems.any { it.syncState == "FAILED" && isActionableSyncError(it.lastSyncError ?: "Sync failed") }) return true
        if (foodBills.any { it.syncState == "FAILED" && isActionableSyncError(it.lastSyncError ?: "Sync failed") }) return true
        if (foodBillItems.any { it.syncState == "FAILED" && isActionableSyncError(it.lastSyncError ?: "Sync failed") }) return true
        return false
    }

    private fun hasNetworkSyncIssue(): Boolean {
        if (isNetworkSyncError(realtimeSyncError)) return true
        if (currentHotel?.syncState == "FAILED" && isNetworkSyncError(currentHotel?.lastSyncError)) return true
        if (managedProperties.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        if (sources.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        if (rooms.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        if (visibleAndUnsyncedBookings().any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        if (unsyncedPayments.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        if (financialLines.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        if (unsyncedAccountingCharges.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        if (foodOrders.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        if (foodOrderItems.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        if (foodBills.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        if (foodBillItems.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        return false
    }

    private fun showSyncStatusDialog() {
        val failedItems = mutableListOf<String>()
        var hasNetworkFailure = false

        fun addFailedItem(label: String, error: String?) {
            val friendlyError = friendlySyncError(error) ?: return
            if (isNetworkSyncError(error)) {
                hasNetworkFailure = true
            } else {
                failedItems.add("$label: $friendlyError")
            }
        }

        addFailedItem("Realtime sync", realtimeSyncError)

        if (currentHotel?.syncState == "FAILED") {
            addFailedItem("Hotel", currentHotel?.lastSyncError ?: "Sync failed")
        }

        managedProperties.filter { it.syncState == "FAILED" }.forEach {
            addFailedItem("Property ${it.propertyName}", it.lastSyncError ?: "Sync failed")
        }

        rooms.filter { it.syncState == "FAILED" }.forEach {
            addFailedItem("Room ${it.roomName}", it.lastSyncError ?: "Sync failed")
        }

        sources.filter { it.syncState == "FAILED" }.forEach {
            addFailedItem("Source ${it.sourceName}", it.lastSyncError ?: "Sync failed")
        }

        visibleAndUnsyncedBookings().filter { it.syncState == "FAILED" }.forEach {
            addFailedItem("Booking ${it.guestName}", it.lastSyncError ?: "Sync failed")
        }

        unsyncedPayments.filter { it.syncState == "FAILED" }.forEach {
            addFailedItem("Payment ${formatMoneyShort(it.amount)}", it.lastSyncError ?: "Sync failed")
        }
        financialLines.filter { it.syncState == "FAILED" }.forEach { addFailedItem("Room-night accounting", it.lastSyncError) }
        unsyncedAccountingCharges.filter { it.syncState == "FAILED" }.forEach { addFailedItem("Charge ${it.description}", it.lastSyncError) }
        foodOrders.filter { it.syncState == "FAILED" }.forEach { addFailedItem("Food order ${it.orderNumber ?: it.guestName}", it.lastSyncError) }
        foodOrderItems.filter { it.syncState == "FAILED" }.forEach { addFailedItem("Food item ${it.itemName}", it.lastSyncError) }
        foodBills.filter { it.syncState == "FAILED" }.forEach { addFailedItem("Bill ${it.billNumber}", it.lastSyncError) }
        foodBillItems.filter { it.syncState == "FAILED" }.forEach { addFailedItem("Bill item ${it.itemName}", it.lastSyncError) }

        val pendingItems = mutableListOf<String>()

        if (currentHotel?.syncState == "PENDING") {
            pendingItems.add("Hotel: ${currentHotel?.hotelName ?: "Hotel details"}")
        }

        managedProperties.filter { it.syncState == "PENDING" }.forEach {
            pendingItems.add("Property: ${it.propertyName}")
        }

        sources.filter { it.syncState == "PENDING" }.forEach {
            pendingItems.add("Source: ${it.sourceName}")
        }

        rooms.filter { it.syncState == "PENDING" }.forEach {
            pendingItems.add("Room: ${it.roomName}")
        }

        visibleAndUnsyncedBookings().filter { it.syncState == "PENDING" }.forEach {
            pendingItems.add("Booking: ${it.guestName}")
        }

        unsyncedPayments.filter { it.syncState == "PENDING" }.forEach {
            pendingItems.add("Payment: ${formatMoneyShort(it.amount)}")
        }
        financialLines.filter { it.syncState == "PENDING" }.forEach { pendingItems.add("Room-night accounting: ${it.bookingRemoteId}") }
        unsyncedAccountingCharges.filter { it.syncState == "PENDING" }.forEach { pendingItems.add("Charge: ${it.description}") }
        foodOrders.filter { it.syncState == "PENDING" }.forEach { pendingItems.add("Food order: ${it.orderNumber ?: it.guestName}") }
        foodOrderItems.filter { it.syncState == "PENDING" }.forEach { pendingItems.add("Food item: ${it.itemName}") }
        foodBills.filter { it.syncState == "PENDING" }.forEach { pendingItems.add("Bill: ${it.billNumber}") }
        foodBillItems.filter { it.syncState == "PENDING" }.forEach { pendingItems.add("Bill item: ${it.itemName}") }

        val failureMessage = buildList {
            if (hasNetworkFailure) {
                add("Internet connection is unavailable. Changes are saved on this device and will retry automatically.")
            }
            addAll(failedItems.distinct())
        }

        val message = when {
            failureMessage.isNotEmpty() -> failureMessage.joinToString("\n\n")
            pendingItems.isNotEmpty() -> "Waiting to sync:\n\n${pendingItems.joinToString("\n")}"
            else -> "All data is safely synced."
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("Sync Status")
            .setMessage(message)

        builder.setPositiveButton("Retry Sync") { _, _ ->
            lifecycleScope.launch {
                repository.retryFailedSync(force = true)
                Toast.makeText(this@BookingChartActivity, "Retrying sync...", Toast.LENGTH_SHORT).show()
            }
        }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun startAutoHealSyncLoop() {
        if (autoHealStarted) return
        autoHealStarted = true

        lifecycleScope.launch {
            while (true) {
                delay(10_000)

                val hasSyncIssue =
                    realtimeSyncError != null ||
                    currentHotel?.syncState == "FAILED" ||
                            currentHotel?.syncState == "PENDING" ||
                            managedProperties.any { it.syncState == "FAILED" || it.syncState == "PENDING" } ||
                            sources.any { it.syncState == "FAILED" || it.syncState == "PENDING" } ||
                            rooms.any { it.syncState == "FAILED" || it.syncState == "PENDING" } ||
                            visibleAndUnsyncedBookings().any { it.syncState == "FAILED" || it.syncState == "PENDING" } ||
                            unsyncedPayments.any { it.syncState == "FAILED" || it.syncState == "PENDING" }

                if (hasSyncIssue) {
                    repository.retryFailedSync(force = true)
                    updateSyncIndicator()
                }
            }
        }
    }

    private fun showExportOptionsDialog() {
        val options = arrayOf(
            "All Bookings",
            "Today's Check-ins",
            "Today's Check-outs"
        )

        AlertDialog.Builder(this)
            .setTitle("Export Bookings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportBookingsCsv("all")
                    1 -> exportBookingsCsv("today_checkins")
                    2 -> exportBookingsCsv("today_checkouts")
                }
            }
            .show()
    }

    private data class RoomEditor(
        val view: LinearLayout,
        val roomName: EditText,
        val propertyRemoteId: String?
    ) {
        fun selectedPropertyRemoteId(): String? = propertyRemoteId?.takeIf { it.isNotBlank() }
    }

    private fun csvSafe(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun showRoomGstSlabsDialog() {
        val visibleSlabs = roomGstSlabs
            .filter { !it.isDeleted }
            .sortedBy { it.minGrossAmount }

        val labels = mutableListOf("Add Room GST Slab")
        labels.addAll(
            visibleSlabs.map { slab ->
                val maxText = slab.maxGrossAmount?.let { formatPlainAmount(it) } ?: "No limit"
                "${slab.slabName} | Rs ${formatPlainAmount(slab.minGrossAmount)} - $maxText | GST ${formatPlainAmount(slab.gstRatePercent)}% | HSN ${slab.hsnSacCode}"
            }
        )

        AlertDialog.Builder(this)
            .setTitle("Room GST Slabs")
            .setItems(labels.toTypedArray()) { _, index ->
                if (index == 0) {
                    showRoomGstSlabEditorDialog(null)
                } else {
                    showRoomGstSlabEditorDialog(visibleSlabs[index - 1])
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showRoomGstSlabEditorDialog(slab: RoomGstSlabEntity?) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 0)
        }

        val slabName = EditText(this).apply {
            hint = "Slab name"
            setSingleLine(true)
            setText(slab?.slabName.orEmpty())
        }

        val minGross = serviceNumberEditText("Min gross room tariff", slab?.minGrossAmount)
        val maxGross = EditText(this).apply {
            hint = "Max gross room tariff, blank = no limit"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            slab?.maxGrossAmount?.let { setText(formatPlainAmount(it)) }
        }

        val gstRate = serviceNumberEditText("GST %", slab?.gstRatePercent)
        val cgstRate = serviceNumberEditText("CGST %", slab?.cgstRatePercent)
        val sgstRate = serviceNumberEditText("SGST %", slab?.sgstRatePercent)
        val cessRate = serviceNumberEditText("CESS %", slab?.cessRatePercent)
        val hsnSac = EditText(this).apply {
            hint = "HSN/SAC code"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(slab?.hsnSacCode ?: "996311")
        }

        val notification = EditText(this).apply {
            hint = "Notification / reference optional"
            setSingleLine(false)
            minLines = 2
            setText(slab?.notificationRef.orEmpty())
        }

        listOf(
            slabName,
            minGross,
            maxGross,
            gstRate,
            cgstRate,
            sgstRate,
            cessRate,
            hsnSac,
            notification
        ).forEach { layout.addView(it) }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (slab == null) "Add Room GST Slab" else "Edit Room GST Slab")
            .setView(layout)
            .setPositiveButton("Save", null)
            .setNegativeButton("Back") { _, _ -> showRoomGstSlabsDialog() }
            .apply {
                if (slab != null) {
                    setNeutralButton("Delete", null)
                }
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = slabName.text.toString().trim()
                val min = minGross.numberValue()
                val max = maxGross.text.toString().trim().toDoubleOrNull()
                val gst = gstRate.numberValue()
                val cgst = cgstRate.numberValue().takeIf { it > 0.0 } ?: gst / 2.0
                val sgst = sgstRate.numberValue().takeIf { it > 0.0 } ?: gst / 2.0
                val cess = cessRate.numberValue()
                val hsn = hsnSac.text.toString().trim().ifBlank { "996311" }

                if (name.isBlank()) {
                    Toast.makeText(this, "Enter slab name", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (max != null && max < min) {
                    Toast.makeText(this, "Max amount cannot be less than min amount", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val now = System.currentTimeMillis()
                val updated = RoomGstSlabEntity(
                    localId = slab?.localId ?: 0,
                    remoteId = slab?.remoteId ?: UUID.randomUUID().toString(),
                    hotelRemoteId = repository.hotelRemoteId,
                    slabName = name,
                    minGrossAmount = min,
                    maxGrossAmount = max,
                    gstRatePercent = gst,
                    cgstRatePercent = cgst,
                    sgstRatePercent = sgst,
                    cessRatePercent = cess,
                    hsnSacCode = hsn,
                    notificationRef = notification.text.toString().trim().ifBlank { null },
                    effectiveFromMillis = slab?.effectiveFromMillis ?: 0L,
                    effectiveToMillis = slab?.effectiveToMillis,
                    isActive = slab?.isActive ?: true,
                    isDeleted = false,
                    updatedAt = now,
                    syncState = slab?.syncState ?: "PENDING",
                    lastSyncError = null,
                    lastSyncedAt = slab?.lastSyncedAt,
                    revision = slab?.revision ?: 0,
                    baseRevision = slab?.baseRevision?.takeIf { it > 0 } ?: slab?.revision ?: 0,
                    updatedByUid = slab?.updatedByUid
                )

                val validationError = validateRoomGstSlab(
                    candidate = updated,
                    existingRemoteId = slab?.remoteId
                )

                if (validationError != null) {
                    Toast.makeText(this, validationError, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    gstRepository.saveRoomGstSlab(updated)
                    Toast.makeText(this@BookingChartActivity, "Room GST slab saved", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    showRoomGstSlabsDialog()
                }
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                val existing = slab ?: return@setOnClickListener
                lifecycleScope.launch {
                    gstRepository.deleteRoomGstSlab(existing)
                    Toast.makeText(this@BookingChartActivity, "Room GST slab deleted", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    showRoomGstSlabsDialog()
                }
            }
        }

        dialog.show()
    }

    private fun validateRoomGstSlab(
        candidate: RoomGstSlabEntity,
        existingRemoteId: String?
    ): String? {
        val slabs = roomGstSlabs
            .filter { !it.isDeleted && it.isActive && it.remoteId != existingRemoteId }
            .plus(candidate)
            .sortedBy { it.minGrossAmount }

        if (slabs.isEmpty()) return "Add at least one slab."

        if (slabs.first().minGrossAmount > 0.0) {
            return "First slab must start from Rs 0."
        }

        val openEndedCount = slabs.count { it.maxGrossAmount == null }
        if (openEndedCount != 1) {
            return "There must be exactly one slab with no maximum limit."
        }

        slabs.forEach { slab ->
            val max = slab.maxGrossAmount
            if (max != null && max < slab.minGrossAmount) {
                return "Max amount cannot be less than min amount."
            }
        }

        for (i in 0 until slabs.lastIndex) {
            val current = slabs[i]
            val next = slabs[i + 1]
            val currentMax = current.maxGrossAmount
                ?: return "Only the last slab can have no maximum limit."

            if (next.minGrossAmount <= currentMax) {
                return "Room GST slabs are overlapping near Rs ${formatPlainAmount(next.minGrossAmount)}."
            }

            if (next.minGrossAmount > currentMax + 0.01) {
                return "Room GST slabs have a gap between Rs ${formatPlainAmount(currentMax)} and Rs ${formatPlainAmount(next.minGrossAmount)}."
            }
        }

        if (slabs.last().maxGrossAmount != null) {
            return "Last slab must have no maximum limit."
        }

        return null
    }
}
