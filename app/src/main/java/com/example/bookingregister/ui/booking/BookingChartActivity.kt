package com.example.bookingregister.ui.booking

import android.content.Intent
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.LinearLayout
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
import com.example.bookingregister.booking.domain.BookingRoomPlanChartPolicy
import com.example.bookingregister.booking.domain.BookingRoomNightAssignment
import com.example.bookingregister.account.domain.AccountPermission
import com.example.bookingregister.account.domain.BackendAccessManager
import com.example.bookingregister.data.AppDatabase
import com.example.bookingregister.data.repository.BookingRepository
import com.example.bookingregister.data.repository.BookingRoomPlanReadResolver
import com.example.bookingregister.data.repository.RoomConflictPlanResult
import com.example.bookingregister.data.repository.RoomConflictResolutionPlan
import com.example.bookingregister.data.repository.SaveResult
import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingRoomNightAssignmentEntity
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
import com.example.bookingregister.source.domain.DefaultBookingSourceCatalog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
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
        private const val ROOM_PLAN_DAY_MILLIS =
            24L * 60L * 60L * 1000L
        private const val INITIAL_DASHBOARD_QUIET_MILLIS = 800L
        private const val INITIAL_DASHBOARD_GRACE_MILLIS = 2_500L
        private const val MAX_INITIAL_DASHBOARD_WAIT_MILLIS = 8_000L
        private const val LIVE_DASHBOARD_DEBOUNCE_MILLIS = 80L
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
    private val roomPlanAssignments = mutableListOf<BookingRoomNightAssignmentEntity>()
    private val unsyncedBookings = mutableListOf<BookingEntity>()
    private val payments = mutableListOf<BookingPaymentEntity>()
    private val unsyncedPayments = mutableListOf<BookingPaymentEntity>()
    private val financialLines = mutableListOf<BookingFinancialLineEntity>()
    private val unsyncedFinancialLines = mutableListOf<BookingFinancialLineEntity>()
    private val unsyncedRoomPlanAssignments =
        mutableListOf<BookingRoomNightAssignmentEntity>()
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
    private var roomPlanLoaded = false
    private var balanceLoaded = false
    private var dashboardRenderJob: Job? = null
    private var dashboardReadyAtMillis: Long? = null
    private var lastDashboardMutationAtMillis = SystemClock.elapsedRealtime()
    private var dashboardRevealed = false
    private var emptyRoomPromptShown = false
    private var initialLoadGraceFinished = false
    private var activeBookingDialog: BookingDialog? = null
    private var syncStatusDialog: AlertDialog? = null
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
            startActivity(Intent(this, RevenueReportActivity::class.java).apply {
                putExtra(
                    RevenueReportActivity.EXTRA_REPORT_KIND,
                    RevenueReportActivity.KIND_REVENUE
                )
                putExtra(
                    RevenueReportActivity.EXTRA_HOTEL_REMOTE_ID,
                    repository.hotelRemoteId
                )
            })
        }
        occupancySummaryButton.setOnClickListener {
            startActivity(Intent(this, RevenueReportActivity::class.java).apply {
                putExtra(RevenueReportActivity.EXTRA_REPORT_KIND, RevenueReportActivity.KIND_OCCUPANCY)
                putExtra(RevenueReportActivity.EXTRA_HOTEL_REMOTE_ID, repository.hotelRemoteId)
            })
        }
        balanceSummaryButton.setOnClickListener {
            startActivity(Intent(this, RevenueReportActivity::class.java).apply {
                putExtra(
                    RevenueReportActivity.EXTRA_REPORT_KIND,
                    RevenueReportActivity.KIND_BALANCE
                )
                putExtra(
                    RevenueReportActivity.EXTRA_HOTEL_REMOTE_ID,
                    repository.hotelRemoteId
                )
            })
        }
        observeLocalData()
        repository.startRealtimeSync()
        foodBillingRepository.startRealtimeSync()
        startAutoHealSyncLoop()

        lifecycleScope.launch {
            val isFirstTime = !repository.hasHotel()

            repository.ensureDefaultHotelExists()
            gstRepository.ensureDefaultRoomGstSlabs()
            if (isFirstTime) {
                showHotelDialog()
            }
        }

        lifecycleScope.launch {
            delay(INITIAL_DASHBOARD_GRACE_MILLIS)
            initialLoadGraceFinished = true
            updateChartLoadingState()
            scheduleDashboardRender(recordMutation = false)
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

    private fun renderChartData() {
        val assignmentsByBooking = roomPlanAssignments.groupBy { it.bookingRemoteId }

        val roomSpansByBooking = bookings.associate { booking ->
            val effectivePlan = BookingRoomPlanReadResolver.resolve(
                booking = booking,
                assignmentEntities = assignmentsByBooking[booking.remoteId].orEmpty()
            )

            booking.remoteId to BookingRoomPlanChartPolicy.toRoomSpans(
                effectivePlan.assignments
            )
        }

        chartView.setData(
            r = rooms,
            b = bookings,
            roomSpansByBookingRemoteId = roomSpansByBooking
        )
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
            scheduleDashboardRender()
            updateSyncIndicator()
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

        repository.observeUnsyncedFinancialLines().observe(this) { updatedLines ->
            unsyncedFinancialLines.clear()
            unsyncedFinancialLines.addAll(updatedLines)
            updateSyncIndicator()
        }

        repository.observeUnsyncedRoomPlanAssignments().observe(this) { updatedAssignments ->
            unsyncedRoomPlanAssignments.clear()
            unsyncedRoomPlanAssignments.addAll(updatedAssignments)
            updateSyncIndicator()
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
        repository.observeChartBookingsForWindow(bookingWindowStart, bookingWindowEnd).observe(this) { updatedBookings ->
            bookingsLoaded = true
            bookings.clear()
            bookings.addAll(updatedBookings)
            scheduleDashboardRender()
            updateSyncIndicator()
            showEmptyRoomPromptIfReady()
        }

        repository.observeRoomPlanForWindow(
            bookingWindowStart,
            bookingWindowEnd
        ).observe(this) { updatedAssignments ->
            roomPlanLoaded = true
            roomPlanAssignments.clear()
            roomPlanAssignments.addAll(updatedAssignments)
            scheduleDashboardRender()
            showEmptyRoomPromptIfReady()
        }

        repository.observeOutstandingBalance().observe(this) { balance ->
            balanceLoaded = true
            outstandingBalance = balance
            scheduleDashboardRender()
            showEmptyRoomPromptIfReady()
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
        if (!isDashboardReady() || !initialLoadGraceFinished || rooms.isNotEmpty() || emptyRoomPromptShown) {
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
        if (!isDashboardReady()) {
            chartLoadingView.animate().cancel()
            chartLoadingView.alpha = 1f
            chartLoadingView.visibility = View.VISIBLE
        }
    }

    private fun isDashboardReady(): Boolean =
        initialLoadGraceFinished && BookingChartLoadingPolicy.isReady(
            roomsLoaded = roomsLoaded,
            bookingsLoaded = bookingsLoaded,
            roomPlanLoaded = roomPlanLoaded,
            balanceLoaded = balanceLoaded
        )

    /**
     * Room observers can emit several snapshots in the same main-thread turn.
     * Publish them as one frame so the chart and summary never display different
     * generations of local data.
     */
    private fun scheduleDashboardRender(recordMutation: Boolean = true) {
        if (recordMutation) {
            lastDashboardMutationAtMillis = SystemClock.elapsedRealtime()
        }
        updateChartLoadingState()
        if (!isDashboardReady()) return

        val now = SystemClock.elapsedRealtime()
        val readyAt = dashboardReadyAtMillis ?: now.also {
            dashboardReadyAtMillis = it
        }
        val delayMillis = if (dashboardRevealed) {
            LIVE_DASHBOARD_DEBOUNCE_MILLIS
        } else {
            BookingChartLoadingPolicy.initialRenderDelayMillis(
                nowMillis = now,
                readyAtMillis = readyAt,
                lastMutationAtMillis = lastDashboardMutationAtMillis,
                quietPeriodMillis = INITIAL_DASHBOARD_QUIET_MILLIS,
                maximumWaitMillis = MAX_INITIAL_DASHBOARD_WAIT_MILLIS
            )
        }

        dashboardRenderJob?.cancel()
        dashboardRenderJob = lifecycleScope.launch {
            if (delayMillis > 0L) delay(delayMillis)
            if (isFinishing || isDestroyed || !isDashboardReady()) return@launch
            renderChartData()
            renderReportSummary()
            revealDashboard()
        }
    }

    private fun revealDashboard() {
        if (dashboardRevealed) return
        dashboardRevealed = true
        chartLoadingView.animate()
            .alpha(0f)
            .setDuration(160L)
            .withEndAction {
                chartLoadingView.visibility = View.GONE
                chartLoadingView.alpha = 1f
            }
            .start()
    }

    private fun showMenu(@Suppress("UNUSED_PARAMETER") anchor: android.view.View) {
        showPropertyMenuDialog(
            title = "Menu",
            subtitle = "Manage your hotel",
            actions = listOf(
                PropertyMenuAction("Organization", "Hotel name and contact") { showHotelDialog() },
                PropertyMenuAction("Properties", "Rooms, billing and settings") { showPropertiesDialog() },
                PropertyMenuAction("Manage orders", "Restaurant orders") {
                    openFoodBilling(FoodBillingActivity.MODE_ACTIVE_ORDERS)
                },
                PropertyMenuAction("Bills", "Food billing archive") {
                    openFoodBilling(FoodBillingActivity.MODE_BILLS_ARCHIVE)
                },
                PropertyMenuAction("Users", "Staff access") { showUsersDialog() },
                PropertyMenuAction("Share staff sheet", "Send today's room sheet") {
                    shareStaffSheetImage()
                },
                PropertyMenuAction("Export bookings", "Download booking records") {
                    showExportOptionsDialog()
                },
                PropertyMenuAction("Booking records", "Search all bookings") {
                    openBookingRecords()
                },
                PropertyMenuAction("Logout", "Sign out of this device") { logout() }
            )
        )
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

        val visibleServices = serviceMenuItems
            .filter { service -> property == null || service.propertyRemoteId == property.remoteId }
        showPropertyMenuDialog(
            title = "Other services",
            subtitle = property?.propertyName ?: "Hotel services",
            primaryAction = PropertyMenuAction("+  Add service", "") {
                showServiceEditorDialog(null, property)
            },
            actions = visibleServices.map { service ->
                PropertyMenuAction(
                    title = service.serviceName,
                    detail = "₹${formatPlainAmount(service.price)}  ·  GST ${formatPlainAmount(service.gstRatePercent)}%" +
                        service.sacCode?.takeIf(String::isNotBlank)?.let { "  ·  SAC $it" }.orEmpty(),
                    onClick = { showServiceEditorDialog(service, property) }
                )
            },
            emptyMessage = "No services added yet.",
            onClose = property?.let { { showPropertyOptionsDialog(it) } }
        )
    }

    private fun showServiceEditorDialog(
        service: ServiceMenuItemEntity?,
        lockedProperty: ManagedPropertyEntity? = null
    ) {
        val layout = menuFormLayout()

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
        menuFormSection(layout, "Service")
        menuFormField(layout, serviceName, "Service name")
        menuFormField(layout, price)
        menuFormSection(layout, "Tax")
        menuFormField(layout, gstRate)
        menuFormField(layout, sacCode)
        layout.addView(TextView(this).apply {
            text = "Tax setting"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, propertyDp(6), 0, propertyDp(4))
            setTextColor(Color.parseColor("#5E43C8"))
        })
        layout.addView(taxSpinner, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, propertyDp(52)
        ))
        menuFormSection(layout, "Details")
        menuFormField(layout, unitLabel, "Unit")
        menuFormField(layout, categoryName, "Category")
        menuFormField(layout, description, "Description (optional)")

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (service == null) "Add Service" else "Edit Service")
            .setView(ScrollView(this).apply { addView(layout) })
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel") { _, _ ->
                showServicesDialog(lockedProperty)
            }
            .apply {
                if (service != null) setNeutralButton("Delete", null)
            }
            .create()

        dialog.setOnShowListener {
            styleMenuSaveButton(dialog)
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

    private fun showUsersDialog() {
        if (AccountPermission.MANAGE_STAFF !in currentPermissions) {
            Toast.makeText(
                this,
                "Only owner or manager can view hotel users.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        lifecycleScope.launch {
            runCatching {
                accessManager.listHotelUsers()
            }.onSuccess { result ->
                if (isFinishing || isDestroyed) return@onSuccess

                val content = LinearLayout(this@BookingChartActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(propertyDp(20), propertyDp(12), propertyDp(20), propertyDp(8))
                }

                if (result.users.isEmpty()) {
                    content.addView(TextView(this@BookingChartActivity).apply {
                        text = "No users found for this hotel."
                        textSize = 15f
                        setPadding(0, 24, 0, 24)
                    })
                } else {
                    result.users.forEach { user ->
                        val displayName = user.displayName.ifBlank {
                            user.email.ifBlank { "Unnamed user" }
                        }
                        val emailLine = user.email.takeIf { it.isNotBlank() }
                        val status = if (user.active) "Active" else "Inactive"

                        content.addView(LinearLayout(this@BookingChartActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(propertyDp(14), propertyDp(12), propertyDp(14), propertyDp(12))
                            background = propertyMenuBackground(
                                Color.WHITE, Color.parseColor("#E5E0EC")
                            )
                            addView(TextView(this@BookingChartActivity).apply {
                                text = displayName
                                textSize = 16f
                                setTypeface(typeface, Typeface.BOLD)
                                setTextColor(Color.parseColor("#252333"))
                            })
                            if (emailLine != null && emailLine != displayName) {
                                addView(TextView(this@BookingChartActivity).apply {
                                    text = emailLine
                                    textSize = 13f
                                    setTextColor(Color.parseColor("#6E6A79"))
                                })
                            }
                            addView(TextView(this@BookingChartActivity).apply {
                                text = "${user.role}  ·  $status"
                                textSize = 12f
                                setTextColor(Color.parseColor("#5E43C8"))
                                setPadding(0, propertyDp(6), 0, 0)
                            })
                        }, LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = propertyDp(8) })
                    }
                }

                val scrollView = ScrollView(this@BookingChartActivity).apply {
                    addView(content)
                }

                MaterialAlertDialogBuilder(this@BookingChartActivity)
                    .setTitle("Users (${result.users.size})")
                    .setView(scrollView)
                    .setPositiveButton("Add User") { _, _ ->
                        showAddUserDialog()
                    }
                    .setNegativeButton("Close", null)
                    .show().also(::styleMenuSaveButton)
            }.onFailure { error ->
                if (isFinishing || isDestroyed) return@onFailure

                Toast.makeText(
                    this@BookingChartActivity,
                    readableBackendError(error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    private fun showAddUserDialog() {
        val layout = menuFormLayout()

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

        menuFormSection(layout, "Staff account")
        menuFormField(layout, displayNameInput)
        menuFormField(layout, emailInput)
        menuFormField(layout, passwordInput)
        layout.addView(TextView(this).apply {
            text = "Role"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#5E43C8"))
            setPadding(0, propertyDp(6), 0, propertyDp(4))
        })
        layout.addView(roleSpinner, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, propertyDp(52)
        ))
        layout.addView(TextView(this).apply {
            text = "Staff can manage bookings. Managers can also add users."
            setPadding(0, 18, 0, 0)
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
        })

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Add User")
            .setView(ScrollView(this).apply { addView(layout) })
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            styleMenuSaveButton(dialog)
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
        showPropertyMenuDialog(
            title = "Rooms",
            subtitle = property?.propertyName ?: "Room inventory",
            primaryAction = PropertyMenuAction("+  Add room", "") { showAddRoomDialog(property) },
            actions = listOf(
                PropertyMenuAction("Manage rooms", "Edit, reorder or change status") {
                    showManageRoomsDialog(property)
                }
            ),
            onClose = property?.let { { showPropertyOptionsDialog(it) } }
        )
    }

    private fun showSourcesDialog(property: ManagedPropertyEntity? = null) {
        val canManageSources = AccountPermission.MANAGE_SOURCES in currentPermissions
        val visibleSources = sources
            .filter { source -> property == null || source.propertyRemoteId == property.remoteId }
            .sortedWith(compareBy<BookingSourceEntity> { it.sourceName.lowercase() })

        showPropertyMenuDialog(
            title = "Booking sources",
            subtitle = property?.propertyName ?: "Hotel channels",
            primaryAction = if (canManageSources) {
                PropertyMenuAction("+  Add source", "") { showSourceCatalogDialog(property) }
            } else null,
            actions = visibleSources.map { source ->
                PropertyMenuAction(source.sourceName, source.sourceType.displaySourceType()) {
                    if (canManageSources) {
                        showSourceEditorDialog(source, property)
                    } else {
                        Toast.makeText(
                            this, "Only owner or manager can manage booking sources.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            emptyMessage = "No booking sources configured.",
            onClose = property?.let { { showPropertyOptionsDialog(it) } }
        )
    }

    private fun showSourceCatalogDialog(property: ManagedPropertyEntity? = null) {
        if (AccountPermission.MANAGE_SOURCES !in currentPermissions) {
            Toast.makeText(
                this,
                "Only owner or manager can manage booking sources.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val configuredNames = sources
            .filter { source -> property == null || source.propertyRemoteId == property.remoteId }
            .map { it.sourceName.trim().lowercase(Locale.ROOT) }
            .toSet()

        val availableCatalogSources = DefaultBookingSourceCatalog.sources
            .filterNot { it.displayName.trim().lowercase(Locale.ROOT) in configuredNames }

        showPropertyMenuDialog(
            title = "Choose booking source",
            subtitle = "Select a channel or create your own",
            primaryAction = PropertyMenuAction("+  Custom source", "") {
                showSourceEditorDialog(source = null, lockedProperty = property)
            },
            actions = availableCatalogSources.map { selected ->
                PropertyMenuAction(selected.displayName, selected.sourceType.displaySourceType()) {
                    showSourceEditorDialog(
                        source = null,
                        lockedProperty = property,
                        suggestedName = selected.displayName,
                        suggestedType = selected.sourceType
                    )
                }
            },
            emptyMessage = "All standard sources are already configured.",
            onClose = { showSourcesDialog(property) }
        )
    }
    private fun showSourceEditorDialog(
        source: BookingSourceEntity?,
        lockedProperty: ManagedPropertyEntity? = null,
        suggestedName: String? = null,
        suggestedType: String? = null
    ) {
        if (AccountPermission.MANAGE_SOURCES !in currentPermissions) {
            Toast.makeText(
                this,
                "Only owner or manager can manage booking sources.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val layout = menuFormLayout()
        val nameInput = EditText(this).apply {
            hint = "Source name"
            setSingleLine(true)
            setText(source?.sourceName ?: suggestedName.orEmpty())
            setSelection(text.length)
        }
        val typeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@BookingChartActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Direct", "Agent", "OTA")
            )
            setSelection(
                when (source?.sourceType ?: suggestedType) {
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

        propertyLabel?.let { layout.addView(it) }
        menuFormSection(layout, "Channel")
        menuFormField(layout, nameInput, "Source name")
        layout.addView(TextView(this).apply {
            text = "Type"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#5E43C8"))
            setPadding(0, propertyDp(6), 0, propertyDp(4))
        })
        layout.addView(typeSpinner, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, propertyDp(52)
        ).apply { bottomMargin = propertyDp(12) })
        menuFormSection(layout, "Fees & deductions")
        listOf(commission, commissionGst, tcs, tds, fee).forEach {
            menuFormField(layout, it)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (source == null) "Add Source" else "Edit Source")
            .setView(ScrollView(this).apply { addView(layout) })
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
            styleMenuSaveButton(dialog)
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

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Organization")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            styleMenuSaveButton(dialog)
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

    private data class PropertyMenuAction(
        val title: String,
        val detail: String,
        val onClick: () -> Unit
    )

    private fun propertyDp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun menuFormLayout(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(propertyDp(20), propertyDp(12), propertyDp(20), propertyDp(8))
    }

    private fun menuFormSection(layout: LinearLayout, title: String) {
        layout.addView(TextView(this).apply {
            text = title.uppercase(Locale.ROOT)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#5E43C8"))
            setPadding(0, propertyDp(12), 0, propertyDp(10))
        })
    }

    private fun menuFormField(
        layout: LinearLayout,
        input: EditText,
        label: String = input.hint?.toString().orEmpty()
    ): TextInputLayout {
        input.hint = null
        input.background = null
        val wrapper = TextInputLayout(this).apply {
            hint = label
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxBackgroundColor = Color.WHITE
            setBoxCornerRadii(
                propertyDp(12).toFloat(), propertyDp(12).toFloat(),
                propertyDp(12).toFloat(), propertyDp(12).toFloat()
            )
            addView(input)
        }
        layout.addView(wrapper, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = propertyDp(10) })
        return wrapper
    }

    private fun styleMenuSaveButton(dialog: AlertDialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            isAllCaps = false
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#5E43C8"))
            setTextColor(Color.WHITE)
        }
    }

    private fun propertyMenuBackground(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = propertyDp(16).toFloat()
            setStroke(propertyDp(1), stroke)
        }

    private fun showPropertyMenuDialog(
        title: String,
        subtitle: String,
        actions: List<PropertyMenuAction>,
        primaryAction: PropertyMenuAction? = null,
        onClose: (() -> Unit)? = null,
        emptyMessage: String? = null
    ) {
        val ink = Color.parseColor("#252333")
        val muted = Color.parseColor("#6E6A79")
        val purple = Color.parseColor("#5E43C8")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(propertyDp(20), propertyDp(24), propertyDp(20), propertyDp(8))
        }
        root.addView(TextView(this).apply {
            text = title
            textSize = 23f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(muted)
            setPadding(0, propertyDp(4), 0, propertyDp(18))
        })

        var dialog: AlertDialog? = null
        primaryAction?.let { action ->
            root.addView(MaterialButton(this).apply {
                text = action.title
                isAllCaps = false
                textSize = 15f
                cornerRadius = propertyDp(14)
                minHeight = propertyDp(52)
                backgroundTintList = ColorStateList.valueOf(purple)
                setTextColor(Color.WHITE)
                setOnClickListener {
                    dialog?.dismiss()
                    action.onClick()
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = propertyDp(12) })
        }

        actions.forEachIndexed { index, action ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(propertyDp(14), propertyDp(10), propertyDp(14), propertyDp(10))
                background = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#E8E1FA")),
                    propertyMenuBackground(Color.WHITE, Color.parseColor("#E5E0EC")),
                    null
                )
                isClickable = true
                isFocusable = true
                contentDescription = action.title
                setOnClickListener {
                    dialog?.dismiss()
                    action.onClick()
                }
            }
            row.addView(TextView(this).apply {
                text = "%02d".format(index + 1)
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(purple)
                background = propertyMenuBackground(
                    Color.parseColor("#F0ECFF"), Color.parseColor("#F0ECFF")
                )
            }, LinearLayout.LayoutParams(propertyDp(38), propertyDp(38)))
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(propertyDp(12), 0, propertyDp(6), 0)
                addView(TextView(this@BookingChartActivity).apply {
                    text = action.title
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(ink)
                })
                if (action.detail.isNotBlank()) {
                    addView(TextView(this@BookingChartActivity).apply {
                        text = action.detail
                        textSize = 12f
                        setTextColor(muted)
                    })
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = "›"
                textSize = 28f
                setTextColor(purple)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(propertyDp(24), propertyDp(38)))
            root.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = propertyDp(8) })
        }

        if (actions.isEmpty() && emptyMessage != null) {
            root.addView(TextView(this).apply {
                text = emptyMessage
                textSize = 14f
                setTextColor(muted)
                setPadding(propertyDp(4), propertyDp(8), propertyDp(4), propertyDp(18))
            })
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setView(ScrollView(this).apply {
                isFillViewport = false
                addView(root)
            })
            .setNegativeButton(if (onClose == null) "Close" else "Back") { _, _ ->
                onClose?.invoke()
            }
            .create()
        dialog.show()
    }

    private fun showPropertiesDialog() {
        showPropertyMenuDialog(
            title = "Properties",
            subtitle = "Manage each property's rooms and billing profile",
            primaryAction = PropertyMenuAction("+  Add property", "") {
                showPropertyEditorDialog(null)
            },
            actions = managedProperties.map { property ->
                PropertyMenuAction(
                    title = property.propertyName,
                    detail = property.gstNumber?.takeIf(String::isNotBlank)
                        ?.let { "GSTIN  $it" } ?: "Property settings",
                    onClick = { showPropertyOptionsDialog(property) }
                )
            }
        )
    }

    private fun showPropertyOptionsDialog(property: ManagedPropertyEntity) {
        showPropertyMenuDialog(
            title = property.propertyName,
            subtitle = "Property settings",
            actions = listOf(
                PropertyMenuAction("Property details", "Profile, contact and billing") {
                    showPropertyEditorDialog(property)
                },
                PropertyMenuAction("Rooms", "Manage room inventory") { showRoomsMenu(property) },
                PropertyMenuAction("Food menu", "Items and prices") { openPropertyFoodMenu(property) },
                PropertyMenuAction("Room GST slabs", "Tax rates by tariff") { showRoomGstSlabsDialog() },
                PropertyMenuAction("Booking sources", "Direct, OTA and agent channels") {
                    showSourcesDialog(property)
                },
                PropertyMenuAction("Other services", "Additional charge items") {
                    showServicesDialog(property)
                }
            )
        )
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
            setPadding(propertyDp(20), propertyDp(12), propertyDp(20), propertyDp(8))
        }

        fun section(title: String) {
            layout.addView(TextView(this).apply {
                text = title
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#5E43C8"))
                setPadding(0, propertyDp(12), 0, propertyDp(10))
            })
        }

        fun field(
            label: String,
            value: String,
            type: Int = InputType.TYPE_CLASS_TEXT,
            multiline: Boolean = false
        ): Pair<TextInputLayout, TextInputEditText> {
            val wrapper = TextInputLayout(this).apply {
                hint = label
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                boxBackgroundColor = Color.WHITE
                setBoxCornerRadii(
                    propertyDp(12).toFloat(), propertyDp(12).toFloat(),
                    propertyDp(12).toFloat(), propertyDp(12).toFloat()
                )
            }
            val input = TextInputEditText(this).apply {
                inputType = type
                if (multiline) {
                    minLines = 2
                    maxLines = 4
                    gravity = Gravity.TOP or Gravity.START
                } else {
                    setSingleLine(true)
                }
                setText(value)
            }
            wrapper.addView(input)
            layout.addView(wrapper, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = propertyDp(10) })
            return wrapper to input
        }

        if (billingLocked) {
            layout.addView(TextView(this).apply {
                text = "Billing identity is locked because this property has issued bills. Unlock it only to change details for future bills."
                textSize = 13f
                setTextColor(Color.parseColor("#765006"))
                setPadding(propertyDp(14), propertyDp(12), propertyDp(14), propertyDp(12))
                background = propertyMenuBackground(
                    Color.parseColor("#FFF8E9"), Color.parseColor("#F0DDAA")
                )
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = propertyDp(12) })
        }

        section("PROPERTY")
        val (propertyNameLayout, propertyName) = field(
            "Property name", property?.propertyName.orEmpty()
        )
        propertyName.setSelection(propertyName.text?.length ?: 0)

        section("BILLING IDENTITY")
        val (legalNameLayout, legalName) = field(
            "Legal / billing name", property?.legalName.orEmpty()
        )
        val (gstNumberLayout, gstNumber) = field(
            "GSTIN", property?.gstNumber.orEmpty()
        )

        section("CONTACT")
        val (_, phone) = field(
            "Phone number", property?.phone.orEmpty(), InputType.TYPE_CLASS_PHONE
        )
        val (_, email) = field(
            "Email address", property?.email.orEmpty(),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )

        section("INVOICE DETAILS")
        val (invoicePrefixLayout, invoicePrefix) = field(
            "Invoice prefix", property?.invoicePrefix.orEmpty()
        )
        val (stateLayout, state) = field("State", property?.state.orEmpty())
        val (addressLayout, address) = field(
            "Billing address", property?.address.orEmpty(),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, multiline = true
        )

        fun setBillingFieldsEditable(editable: Boolean) {
            listOf(
                legalNameLayout, gstNumberLayout, invoicePrefixLayout,
                stateLayout, addressLayout
            ).forEach { fieldLayout ->
                fieldLayout.isEnabled = editable
                fieldLayout.alpha = if (editable) 1f else 0.72f
            }
        }

        if (billingLocked) {
            setBillingFieldsEditable(false)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (property == null) "Add Property Billing Profile" else "Edit Property Billing Profile")
            .setView(ScrollView(this).apply { addView(layout) })
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
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                isAllCaps = false
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#5E43C8"))
                setTextColor(Color.WHITE)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = propertyName.text.toString().trim()
                if (name.isEmpty()) {
                    propertyNameLayout.error = "Enter property name"
                    return@setOnClickListener
                }
                propertyNameLayout.error = null
                val normalizedGstin = GstinValidator.normalize(gstNumber.text.toString())
                if (!GstinValidator.isBlankOrValid(normalizedGstin)) {
                    gstNumberLayout.error = "Enter valid 15-character GSTIN or leave blank"
                    return@setOnClickListener
                }
                gstNumberLayout.error = null
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

        MaterialAlertDialogBuilder(this)
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
            .show().also(::styleMenuSaveButton)
    }

    private fun showRoomPlanDialog(booking: BookingEntity) {
        if (AccountPermission.EDIT_BOOKINGS !in currentPermissions) {
            Toast.makeText(
                this,
                "You do not have permission to change the room plan.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (booking.bookingStatus == BookingStatus.CANCELLED) {
            Toast.makeText(
                this,
                "A cancelled booking cannot be moved to another room.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val effectivePlan = BookingRoomPlanReadResolver.resolve(
            booking = booking,
            assignmentEntities = roomPlanAssignments.filter {
                it.bookingRemoteId == booking.remoteId
            }
        )

        if (!effectivePlan.isValid) {
            Toast.makeText(
                this,
                "Current room plan is incomplete: " +
                    effectivePlan.validation.errors.joinToString(),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val stayDates = mutableListOf<Long>()
        var dateMillis = booking.checkInMillis

        while (dateMillis < booking.checkOutMillis) {
            stayDates += dateMillis
            dateMillis += ROOM_PLAN_DAY_MILLIS
        }

        if (stayDates.isEmpty()) {
            Toast.makeText(
                this,
                "This booking has no room nights.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val dateFormat = SimpleDateFormat(
            "dd MMM yyyy",
            Locale.getDefault()
        )

        val dateLabels = stayDates.map { businessDateMillis ->
            val roomNames = effectivePlan.assignments
                .filter { it.businessDateMillis == businessDateMillis }
                .map { assignment ->
                    rooms.firstOrNull {
                        it.remoteId == assignment.roomRemoteId
                    }?.roomName
                        ?: "Room ${assignment.roomRemoteId.takeLast(6)}"
                }
                .distinct()
                .joinToString(", ")
                .ifBlank { "No room" }

            "${dateFormat.format(Date(businessDateMillis))}  •  $roomNames"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Room Plan - Choose Stay Night")
            .setItems(dateLabels) { _, which ->
                showRoomPlanRoomSelectionDialog(
                    booking = booking,
                    effectiveDateMillis = stayDates[which],
                    currentAssignments = effectivePlan.assignments
                )
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showRoomPlanRoomSelectionDialog(
        booking: BookingEntity,
        effectiveDateMillis: Long,
        currentAssignments: List<com.example.bookingregister.booking.domain.BookingRoomNightAssignment>
    ) {
        val selectableRooms = rooms
            .filter {
                !it.isDeleted &&
                    RoomLifecycleStatus.normalize(it.lifecycleStatus) ==
                    RoomLifecycleStatus.ACTIVE
            }
            .sortedWith(
                compareBy<RoomEntity> { room ->
                    managedProperties
                        .firstOrNull {
                            it.remoteId == room.propertyRemoteId
                        }
                        ?.sortOrder
                        ?: Int.MAX_VALUE
                }.thenBy { it.sortOrder }
                    .thenBy { it.roomName }
            )

        if (selectableRooms.isEmpty()) {
            Toast.makeText(
                this,
                "No active rooms are available to assign.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val propertyNames = managedProperties.associate {
            it.remoteId to it.propertyName
        }

        val currentRoomIds = currentAssignments
            .filter {
                it.businessDateMillis == effectiveDateMillis
            }
            .mapTo(mutableSetOf()) {
                it.roomRemoteId
            }

        val selectedIds = currentRoomIds
            .filterTo(linkedSetOf()) { currentId ->
                selectableRooms.any {
                    it.remoteId == currentId
                }
            }

        val labels = selectableRooms.map { room ->
            val propertyName = room.propertyRemoteId
                ?.let(propertyNames::get)
                ?.takeIf(String::isNotBlank)
                ?: "Unassigned property"

            "$propertyName  •  ${room.roomName}"
        }.toTypedArray()

        val checked = BooleanArray(selectableRooms.size) { index ->
            selectableRooms[index].remoteId in selectedIds
        }

        val dateLabel = SimpleDateFormat(
            "dd MMM yyyy",
            Locale.getDefault()
        ).format(Date(effectiveDateMillis))

        val dialog = AlertDialog.Builder(this)
            .setTitle("Shift from $dateLabel")
            .setMessage(
                "Select one or more rooms. Multiple rooms are allowed, but all rooms " +
                    "for the same night must belong to one property.\n\n" +
                    "Changing property here does not move the original booking's billing."
            )
            .setMultiChoiceItems(
                labels,
                checked
            ) { _, which, isChecked ->
                val roomId = selectableRooms[which].remoteId

                if (isChecked) {
                    selectedIds += roomId
                } else {
                    selectedIds -= roomId
                }
            }
            .setPositiveButton("Apply Shift", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {
                if (selectedIds.isEmpty()) {
                    Toast.makeText(
                        this,
                        "Select at least one room.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val selectedRooms = selectableRooms.filter {
                    it.remoteId in selectedIds
                }

                val selectedPropertyIds = selectedRooms
                    .map {
                        it.propertyRemoteId
                            ?.trim()
                            .orEmpty()
                    }
                    .filter {
                        it.isNotBlank()
                    }
                    .distinct()

                if (
                    selectedPropertyIds.size != 1 ||
                    selectedRooms.any {
                        it.propertyRemoteId.isNullOrBlank()
                    }
                ) {
                    Toast.makeText(
                        this,
                        "All selected rooms must belong to one property.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                val currentIdsForDate = currentRoomIds.sorted()
                val requestedIds = selectedIds.sorted()

                if (currentIdsForDate == requestedIds) {
                    Toast.makeText(
                        this,
                        "The selected room plan is already active on this date.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
                ).isEnabled = false

                lifecycleScope.launch {
                    when (
                        val result =
                            repository.replaceBookingRoomPlanFromDate(
                                bookingRemoteId = booking.remoteId,
                                effectiveDateMillis = effectiveDateMillis,
                                replacementRoomRemoteIds = requestedIds
                            )
                    ) {
                        is SaveResult.Success -> {
                            Toast.makeText(
                                this@BookingChartActivity,
                                "Room plan saved. The chart will update automatically.",
                                Toast.LENGTH_LONG
                            ).show()

                            dialog.dismiss()
                        }

                        is SaveResult.Conflict -> {
                            dialog.getButton(
                                AlertDialog.BUTTON_POSITIVE
                            ).isEnabled = true

                            Toast.makeText(
                                this@BookingChartActivity,
                                result.message,
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        is SaveResult.Error -> {
                            dialog.getButton(
                                AlertDialog.BUTTON_POSITIVE
                            ).isEnabled = true

                            Toast.makeText(
                                this@BookingChartActivity,
                                result.message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }

        dialog.show()
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
            initialRoomPlanAssignments = existing?.let { booking ->
                roomPlanAssignments
                    .filter {
                        !it.isDeleted &&
                            it.bookingRemoteId == booking.remoteId
                    }
                    .map {
                        BookingRoomNightAssignment(
                            businessDateMillis = it.businessDateMillis,
                            roomRemoteId = it.roomRemoteId,
                            propertyRemoteId = it.propertyRemoteId
                        )
                    }
                    .takeIf { it.isNotEmpty() }
            },
            onRoomNightAvailabilityRequested = { dateMillis, onResult ->
                lifecycleScope.launch {
                    try {
                        onResult(repository.occupiedRoomIdsForDateWisePicker(
                            dateMillis = dateMillis,
                            editingBookingRemoteId = existing?.remoteId
                        ))
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        onResult(null)
                    }
                }
            },
            onSimpleStayAvailabilityRequested = { checkInMillis, checkOutMillis, onResult ->
                lifecycleScope.launch {
                    try {
                        onResult(repository.occupiedRoomIdsForSimpleStayPicker(
                            checkInMillis = checkInMillis,
                            checkOutMillis = checkOutMillis,
                            editingBookingRemoteId = existing?.remoteId
                        ))
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        onResult(null)
                    }
                }
            },
            onBookingSaved = { booking, lines, roomPlan, onResult ->
                lifecycleScope.launch {
                    onResult(
                        repository.saveBookingWithFinancialLines(
                            booking = booking,
                            financialLines = lines,
                            roomPlanAssignments = roomPlan
                        )
                    )
                }
            },
            onBookingDeleted = { booking, request, onResult ->
                lifecycleScope.launch {
                    onResult(repository.cancelBooking(booking, request))
                }
            },
            onPaymentSaved = { booking, amount, paymentType, paymentCategory, note, originalPaymentRemoteId, onResult ->
                lifecycleScope.launch {
                    onResult(
                        repository.addBookingPayment(
                            booking,
                            amount,
                            paymentType = paymentType,
                            paymentCategory = paymentCategory,
                            note = note,
                            originalPaymentRemoteId = originalPaymentRemoteId
                        )
                    )
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
            showPropertyMenuDialog(
                title = "Manage rooms",
                subtitle = property?.propertyName ?: "Room inventory",
                actions = emptyList(),
                emptyMessage = "No rooms available yet.",
                onClose = { showRoomsMenu(property) }
            )
            return
        }

        showPropertyMenuDialog(
            title = "Manage rooms",
            subtitle = property?.propertyName ?: "Room inventory",
            actions = visibleRooms.map { room ->
                PropertyMenuAction(
                    room.roomName,
                    RoomLifecycleStatus.normalize(room.lifecycleStatus).lowercase()
                        .replaceFirstChar { it.uppercase() }
                ) { showRoomOptions(room, property) }
            },
            onClose = { showRoomsMenu(property) }
        )
    }

    private fun showRoomOptions(room: RoomEntity, property: ManagedPropertyEntity? = null) {
        val status = RoomLifecycleStatus.normalize(room.lifecycleStatus)
        val options = when (status) {
            RoomLifecycleStatus.ACTIVE -> arrayOf("Edit", "Move Up", "Move Down", "Disable", "Retire", "Delete")
            RoomLifecycleStatus.DISABLED -> arrayOf("Reactivate", "Retire", "Delete", "View reason")
            else -> arrayOf("View retirement details")
        }

        showPropertyMenuDialog(
            title = room.roomName,
            subtitle = status.lowercase().replaceFirstChar { it.uppercase() },
            actions = options.mapIndexed { which, option ->
                PropertyMenuAction(option, "") {
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
            },
            onClose = { showManageRoomsDialog(property) }
        )
    }
    private fun showEditRoomDialog(room: RoomEntity, property: ManagedPropertyEntity? = null) {
        val editor = createRoomEditor(room, property)

        MaterialAlertDialogBuilder(this)
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
            .show().also(::styleMenuSaveButton)
    }

    private fun createRoomEditor(
        room: RoomEntity?,
        lockedProperty: ManagedPropertyEntity? = null
    ): RoomEditor {
        val layout = menuFormLayout()

        val roomName = EditText(this).apply {
            hint = "Room name"
            setSingleLine(true)
            setText(room?.roomName.orEmpty())
            setSelection(text.length)
        }

        menuFormSection(layout, "Room")
        menuFormField(layout, roomName)

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
        val layout = menuFormLayout().apply {
            menuFormField(this, reason)
        }
        val action = if (targetStatus == RoomLifecycleStatus.RETIRED) "Retire" else "Disable"
        MaterialAlertDialogBuilder(this)
            .setTitle("$action ${room.roomName}")
            .setMessage("Current and future bookings must first be moved, cancelled, or checked out.")
            .setView(layout)
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
        val allSyncStates = currentSyncStates()

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

        refreshOpenSyncStatusDialog()
    }

    private fun currentCoreRepositorySyncStates(): List<String> = buildList {
        currentHotel?.let { add(it.syncState) }
        addAll(managedProperties.map { it.syncState })
        addAll(sources.map { it.syncState })
        addAll(rooms.map { it.syncState })
        addAll(visibleAndUnsyncedBookings().map { it.syncState })
        addAll(unsyncedPayments.map { it.syncState })
        addAll(unsyncedAccountingCharges.map { it.syncState })
    }

    private fun currentRepositorySyncStates(): List<String> = buildList {
        addAll(currentCoreRepositorySyncStates())
        addAll(unsyncedFinancialLines.map { it.syncState })
        addAll(unsyncedRoomPlanAssignments.map { it.syncState })
    }

    private fun currentSyncStates(): List<String> = buildList {
        addAll(currentRepositorySyncStates())
        addAll(foodOrders.map { it.syncState })
        addAll(foodOrderItems.map { it.syncState })
        addAll(foodBills.map { it.syncState })
        addAll(foodBillItems.map { it.syncState })
    }
    private fun renderReportSummary() {
        if (!::revenueSummaryButton.isInitialized) return

        val range = RevenuePeriod.financialYearFor()
        val occupancy = occupancyCalculator.occupancyPercent(rooms, bookings, range)
        val balance = outstandingBalance

        revenueSummaryButton.text = "Revenue"
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
        if (unsyncedFinancialLines.any { it.syncState == "FAILED" && isActionableSyncError(it.lastSyncError ?: "Sync failed") }) return true
        if (unsyncedRoomPlanAssignments.any { it.syncState == "FAILED" && isActionableSyncError(it.lastSyncError ?: "Sync failed") }) return true
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
        if (unsyncedFinancialLines.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        if (unsyncedRoomPlanAssignments.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        if (unsyncedAccountingCharges.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        if (foodOrders.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        if (foodOrderItems.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        if (foodBills.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        if (foodBillItems.any { it.syncState == "FAILED" && isNetworkSyncError(it.lastSyncError) }) return true
        return false
    }

    private data class SyncStatusDialogState(
        val message: String,
        val roomConflictBookings: List<BookingEntity>,
        val hasRetryableFailure: Boolean,
        val hasOutstandingWork: Boolean
    )

    private fun currentSyncStatusDialogState(): SyncStatusDialogState {
        val failedItems = mutableListOf<String>()
        var hasNetworkFailure = false
        val roomConflictBookings = visibleAndUnsyncedBookings()
            .filter { repository.isRoomConflictBooking(it) }

        fun bookingDisplayName(bookingRemoteId: String): String =
            (bookings + unsyncedBookings)
                .firstOrNull { it.remoteId == bookingRemoteId }
                ?.guestName
                ?.takeIf { it.isNotBlank() }
                ?: bookingRemoteId

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

        visibleAndUnsyncedBookings()
            .filter { it.syncState == "FAILED" && !repository.isRoomConflictBooking(it) }
            .forEach {
                addFailedItem("Booking ${it.guestName}", it.lastSyncError ?: "Sync failed")
            }

        unsyncedPayments.filter { it.syncState == "FAILED" }.forEach {
            addFailedItem("Payment ${formatMoneyShort(it.amount)}", it.lastSyncError ?: "Sync failed")
        }
        unsyncedFinancialLines.filter { it.syncState == "FAILED" }.forEach {
            addFailedItem("Booking charges for ${bookingDisplayName(it.bookingRemoteId)}", it.lastSyncError)
        }
        unsyncedRoomPlanAssignments.filter { it.syncState == "FAILED" }.forEach {
            addFailedItem("Date-wise room plan for ${bookingDisplayName(it.bookingRemoteId)}", it.lastSyncError)
        }
        unsyncedAccountingCharges.filter { it.syncState == "FAILED" }.forEach { addFailedItem("Charge ${it.description}", it.lastSyncError) }
        foodOrders.filter { it.syncState == "FAILED" }.forEach { addFailedItem("Food order ${it.orderNumber ?: it.guestName}", it.lastSyncError) }
        foodOrderItems.filter { it.syncState == "FAILED" }.forEach { addFailedItem("Food item ${it.itemName}", it.lastSyncError) }
        foodBills.filter { it.syncState == "FAILED" }.forEach { addFailedItem("Bill ${it.billNumber}", it.lastSyncError) }
        foodBillItems.filter { it.syncState == "FAILED" }.forEach { addFailedItem("Bill item ${it.itemName}", it.lastSyncError) }

        val conflictMessages = roomConflictBookings.map { booking ->
            "Booking ${booking.guestName}: This room is already booked for these dates. Tap Fix Booking."
        }

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
        unsyncedFinancialLines.filter { it.syncState == "PENDING" }.forEach {
            pendingItems.add("Booking charges: ${bookingDisplayName(it.bookingRemoteId)}")
        }
        unsyncedRoomPlanAssignments.filter { it.syncState == "PENDING" }.forEach {
            pendingItems.add("Date-wise room plan: ${bookingDisplayName(it.bookingRemoteId)}")
        }
        unsyncedAccountingCharges.filter { it.syncState == "PENDING" }.forEach { pendingItems.add("Charge: ${it.description}") }
        foodOrders.filter { it.syncState == "PENDING" }.forEach { pendingItems.add("Food order: ${it.orderNumber ?: it.guestName}") }
        foodOrderItems.filter { it.syncState == "PENDING" }.forEach { pendingItems.add("Food item: ${it.itemName}") }
        foodBills.filter { it.syncState == "PENDING" }.forEach { pendingItems.add("Bill: ${it.billNumber}") }
        foodBillItems.filter { it.syncState == "PENDING" }.forEach { pendingItems.add("Bill item: ${it.itemName}") }

        val failureMessage = buildList {
            addAll(conflictMessages)
            if (hasNetworkFailure) {
                add("Internet connection is unavailable. Changes are saved on this device and will retry automatically.")
            }
            addAll(failedItems.distinct())
        }

        val message = when {
            failureMessage.isNotEmpty() -> failureMessage.joinToString("\n\n")
            pendingItems.isNotEmpty() ->
                "Saved safely on this device. Waiting for cloud confirmation:\n\n" +
                    pendingItems.distinct().joinToString("\n")
            else -> "All data is safely synced."
        }

        return SyncStatusDialogState(
            message = message,
            roomConflictBookings = roomConflictBookings,
            hasRetryableFailure = hasNetworkFailure || failedItems.isNotEmpty(),
            hasOutstandingWork = failureMessage.isNotEmpty() || pendingItems.isNotEmpty()
        )
    }

    private fun showSyncStatusDialog() {
        syncStatusDialog?.dismiss()
        val dialog = AlertDialog.Builder(this)
            .setTitle("Sync Status")
            .setMessage(currentSyncStatusDialogState().message)
            .setPositiveButton("Retry Sync", null)
            .setNeutralButton("Retry Other Sync", null)
            .setNegativeButton("Close", null)
            .create()

        dialog.setOnDismissListener {
            if (syncStatusDialog === dialog) {
                syncStatusDialog = null
            }
        }
        syncStatusDialog = dialog
        dialog.show()
        refreshOpenSyncStatusDialog()
    }

    private fun refreshOpenSyncStatusDialog() {
        val dialog = syncStatusDialog?.takeIf { it.isShowing } ?: return
        val state = currentSyncStatusDialogState()
        dialog.setMessage(state.message)

        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)

        when {
            state.roomConflictBookings.isNotEmpty() -> {
                positiveButton.visibility = View.VISIBLE
                positiveButton.text = "Fix Booking"
                positiveButton.setOnClickListener {
                    dialog.dismiss()
                    showRoomConflictBookingPicker(state.roomConflictBookings)
                }

                neutralButton.visibility =
                    if (state.hasRetryableFailure) View.VISIBLE else View.GONE
                neutralButton.text = "Retry Other Sync"
                neutralButton.setOnClickListener {
                    retrySavedChanges("Retrying other saved changes...")
                }
            }

            state.hasOutstandingWork -> {
                positiveButton.visibility = View.VISIBLE
                positiveButton.text = "Retry Sync"
                positiveButton.setOnClickListener {
                    retrySavedChanges("Retrying saved changes...")
                }
                neutralButton.visibility = View.GONE
            }

            else -> {
                positiveButton.visibility = View.GONE
                neutralButton.visibility = View.GONE
            }
        }
    }

    private fun retrySavedChanges(message: String) {
        lifecycleScope.launch {
            repository.retryFailedSync(force = true)
            Toast.makeText(
                this@BookingChartActivity,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showRoomConflictBookingPicker(conflicts: List<BookingEntity>) {
        if (conflicts.size == 1) {
            openRoomConflictResolution(conflicts.first())
            return
        }

        val labels = conflicts.map { booking ->
            val roomNames = rooms
                .filter { booking.roomRemoteIds.contains(it.remoteId) }
                .joinToString(", ") { it.roomName }
                .ifBlank { "Room needs attention" }
            "${booking.guestName} - $roomNames"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Choose Booking to Fix")
            .setItems(labels) { _, index ->
                openRoomConflictResolution(conflicts[index])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openRoomConflictResolution(booking: BookingEntity) {
        if (AccountPermission.EDIT_BOOKINGS !in currentPermissions) {
            Toast.makeText(
                this,
                "You do not have permission to change bookings.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        Toast.makeText(this, "Checking free rooms...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            when (val result = repository.loadRoomConflictResolutionPlan(booking.remoteId)) {
                is RoomConflictPlanResult.Ready ->
                    showRoomConflictResolutionActions(booking, result.plan)
                is RoomConflictPlanResult.Error ->
                    Toast.makeText(
                        this@BookingChartActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
            }
        }
    }

    private fun showRoomConflictResolutionActions(
        booking: BookingEntity,
        plan: RoomConflictResolutionPlan
    ) {
        val enoughRooms = plan.availableRooms.size >= plan.requiredRoomCount
        val roomMessage = when {
            plan.roomMoveBlockedReason != null -> plan.roomMoveBlockedReason
            enoughRooms && plan.serverBookingExists ->
                "The main-system room is pre-selected if it is still free. Keep it or choose another free room."
            enoughRooms ->
                "Choose a free room for the same dates. Guest details and money will not be changed."
            else ->
                "No suitable free room is available right now. You can close this and try again later."
        }

        val message = buildString {
            append(roomMessage)
            if (plan.canRemoveLocalBooking) {
                append("\n\nThis rejected copy never reached the main system. You may also remove it.")
            } else if (!plan.removalBlockedReason.isNullOrBlank() && !plan.serverBookingExists) {
                append("\n\n")
                append(plan.removalBlockedReason)
            }
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("Fix Booking - ${booking.guestName}")
            .setMessage(message)
            .setNegativeButton("Close", null)

        if (enoughRooms && plan.roomMoveBlockedReason == null) {
            builder.setPositiveButton("Choose Room") { _, _ ->
                showRoomConflictRoomPicker(booking, plan)
            }
        }

        if (plan.canRemoveLocalBooking) {
            builder.setNeutralButton("Remove Booking") { _, _ ->
                confirmRemoveRejectedLocalBooking(booking)
            }
        }

        builder.show()
    }

    private fun showRoomConflictRoomPicker(
        booking: BookingEntity,
        plan: RoomConflictResolutionPlan
    ) {
        val availableRooms = plan.availableRooms
        val requiredCount = plan.requiredRoomCount
        if (availableRooms.size < requiredCount) {
            Toast.makeText(
                this,
                "Not enough free rooms are available right now.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val labels = availableRooms.map { it.roomName }.toTypedArray()
        val initialIds = plan.initialSelectedRoomRemoteIds
            .filter { selectedId -> availableRooms.any { it.remoteId == selectedId } }
            .take(requiredCount)

        if (requiredCount == 1) {
            var selectedId: String? = initialIds.firstOrNull()
            val initialIndex = availableRooms.indexOfFirst { it.remoteId == selectedId }
            val dialog = AlertDialog.Builder(this)
                .setTitle("Choose Available Room")
                .setSingleChoiceItems(labels, initialIndex) { _, which ->
                    selectedId = availableRooms[which].remoteId
                }
                .setPositiveButton("Save Room", null)
                .setNegativeButton("Cancel", null)
                .create()

            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val roomId = selectedId
                    if (roomId == null) {
                        Toast.makeText(this, "Please choose one room.", Toast.LENGTH_SHORT).show()
                    } else {
                        saveRoomConflictSelection(booking, listOf(roomId), dialog)
                    }
                }
            }
            dialog.show()
            return
        }

        val selectedIds = linkedSetOf<String>().apply { addAll(initialIds) }
        val checked = BooleanArray(availableRooms.size) { index ->
            availableRooms[index].remoteId in selectedIds
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Choose $requiredCount Available Rooms")
            .setMultiChoiceItems(labels, checked) { alert, which, isChecked ->
                val roomId = availableRooms[which].remoteId
                if (isChecked) {
                    if (selectedIds.size >= requiredCount) {
                        (alert as? AlertDialog)?.listView?.setItemChecked(which, false)
                        Toast.makeText(
                            this,
                            "This booking needs exactly $requiredCount rooms.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        selectedIds.add(roomId)
                    }
                } else {
                    selectedIds.remove(roomId)
                }
            }
            .setPositiveButton("Save Rooms", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (selectedIds.size != requiredCount) {
                    Toast.makeText(
                        this,
                        "Please select exactly $requiredCount rooms.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    saveRoomConflictSelection(booking, selectedIds.toList(), dialog)
                }
            }
        }
        dialog.show()
    }

    private fun saveRoomConflictSelection(
        booking: BookingEntity,
        selectedRoomRemoteIds: List<String>,
        dialog: AlertDialog
    ) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        lifecycleScope.launch {
            when (
                val result = repository.resolveRoomConflictRooms(
                    booking.remoteId,
                    selectedRoomRemoteIds
                )
            ) {
                is SaveResult.Success -> {
                    Toast.makeText(
                        this@BookingChartActivity,
                        "Room choice saved. It will sync now.",
                        Toast.LENGTH_LONG
                    ).show()
                    dialog.dismiss()
                }
                is SaveResult.Conflict -> {
                    Toast.makeText(
                        this@BookingChartActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                    dialog.dismiss()
                    openRoomConflictResolution(booking)
                }
                is SaveResult.Error -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    Toast.makeText(
                        this@BookingChartActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun confirmRemoveRejectedLocalBooking(booking: BookingEntity) {
        AlertDialog.Builder(this)
            .setTitle("Remove Rejected Booking?")
            .setMessage(
                "The app will check the main system again first. " +
                    "Only a rejected copy that never reached the main system and has no payment, food, service, or bill history can be removed."
            )
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    when (val result = repository.removeRejectedLocalBooking(booking.remoteId)) {
                        is SaveResult.Success ->
                            Toast.makeText(
                                this@BookingChartActivity,
                                "Rejected booking removed. No main-system booking was deleted.",
                                Toast.LENGTH_LONG
                            ).show()
                        is SaveResult.Conflict ->
                            Toast.makeText(
                                this@BookingChartActivity,
                                result.message,
                                Toast.LENGTH_LONG
                            ).show()
                        is SaveResult.Error ->
                            Toast.makeText(
                                this@BookingChartActivity,
                                result.message,
                                Toast.LENGTH_LONG
                            ).show()
                    }
                }
            }
            .setNegativeButton("Keep Booking", null)
            .show()
    }

    private fun startAutoHealSyncLoop() {
        if (autoHealStarted) return
        autoHealStarted = true

        lifecycleScope.launch {
            while (true) {
                delay(10_000)

                val hasSyncIssue = BookingChartSyncPolicy.hasRetryableWork(
                    hasActionableRealtimeError = isActionableSyncError(realtimeSyncError),
                    coreSyncStates = currentCoreRepositorySyncStates(),
                    financialLineSyncStates = unsyncedFinancialLines.map { it.syncState },
                    roomPlanAssignmentSyncStates =
                        unsyncedRoomPlanAssignments.map { it.syncState }
                )

                if (hasSyncIssue) {
                    repository.retryFailedSync(force = true)
                    updateSyncIndicator()
                }
            }
        }
    }

    private fun showExportOptionsDialog() {
        showPropertyMenuDialog(
            title = "Export bookings",
            subtitle = "Choose which records to download",
            actions = listOf(
                PropertyMenuAction("All bookings", "Complete booking history") {
                    exportBookingsCsv("all")
                },
                PropertyMenuAction("Today's check-ins", "Arrivals today") {
                    exportBookingsCsv("today_checkins")
                },
                PropertyMenuAction("Today's check-outs", "Departures today") {
                    exportBookingsCsv("today_checkouts")
                }
            )
        )
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

        showPropertyMenuDialog(
            title = "Room GST slabs",
            subtitle = "Tariff-based tax settings",
            primaryAction = PropertyMenuAction("+  Add room GST slab", "") {
                showRoomGstSlabEditorDialog(null)
            },
            actions = visibleSlabs.map { slab ->
                val maxText = slab.maxGrossAmount?.let { formatPlainAmount(it) } ?: "No limit"
                PropertyMenuAction(
                    title = slab.slabName,
                    detail = "₹${formatPlainAmount(slab.minGrossAmount)}–$maxText  ·  GST ${formatPlainAmount(slab.gstRatePercent)}%  ·  HSN ${slab.hsnSacCode}",
                    onClick = { showRoomGstSlabEditorDialog(slab) }
                )
            },
            emptyMessage = "No room GST slabs configured."
        )
    }

    private fun showRoomGstSlabEditorDialog(slab: RoomGstSlabEntity?) {
        val layout = menuFormLayout()

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

        menuFormSection(layout, "Tariff range")
        menuFormField(layout, slabName)
        menuFormField(layout, minGross)
        menuFormField(layout, maxGross, "Max gross room tariff (blank = no limit)")
        menuFormSection(layout, "Tax rates")
        listOf(gstRate, cgstRate, sgstRate, cessRate).forEach { menuFormField(layout, it) }
        menuFormSection(layout, "Invoice details")
        menuFormField(layout, hsnSac)
        menuFormField(layout, notification, "Notification / reference (optional)")

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (slab == null) "Add Room GST Slab" else "Edit Room GST Slab")
            .setView(ScrollView(this).apply { addView(layout) })
            .setPositiveButton("Save", null)
            .setNegativeButton("Back") { _, _ -> showRoomGstSlabsDialog() }
            .apply {
                if (slab != null) {
                    setNeutralButton("Delete", null)
                }
            }
            .create()

        dialog.setOnShowListener {
            styleMenuSaveButton(dialog)
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
