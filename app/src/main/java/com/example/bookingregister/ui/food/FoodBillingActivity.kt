package com.example.bookingregister.ui.food

import android.app.DatePickerDialog
import android.graphics.Color
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bookingregister.data.repository.FoodBillingRepository
import com.example.bookingregister.data.repository.SaveResult
import com.example.bookingregister.data.entities.FoodBillEntity
import com.example.bookingregister.data.entities.FoodGstCategoryEntity
import com.example.bookingregister.data.entities.FoodMenuItemEntity
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderItemEntity
import com.example.bookingregister.data.entities.FoodOrderStatus
import com.example.bookingregister.data.entities.RoomEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.example.bookingregister.R
import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.FoodBillItemEntity
import com.example.bookingregister.data.entities.HotelEntity
import com.example.bookingregister.data.entities.ManagedPropertyEntity
import com.example.bookingregister.utils.pdf.InvoicePdfGenerator
class FoodBillingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HOTEL_REMOTE_ID = "hotel_remote_id"
        const val EXTRA_OPEN_MODE = "open_mode"
        const val EXTRA_OPEN_FINAL_BILL_BOOKING_REMOTE_ID = "open_final_bill_booking_remote_id"
        const val EXTRA_TARGET_BOOKING_REMOTE_ID = "target_booking_remote_id"
        const val EXTRA_TARGET_ROOM_REMOTE_ID = "target_room_remote_id"
        const val EXTRA_PROPERTY_REMOTE_ID = "property_remote_id"
        const val MODE_ACTIVE_ORDERS = "active_orders"
        const val MODE_TAKE_ORDER = "take_order"
        const val MODE_BILLS_ARCHIVE = "bills_archive"
        const val MODE_FOOD_MENU = "food_menu"
        private const val BRAND = "#28C7B7"
        private const val GREEN = "#128C7E"
        private const val GREEN_SOFT = "#EAFBF8"
    }

    private lateinit var repository: FoodBillingRepository
    private lateinit var orderList: LinearLayout
    private lateinit var foodBillShareManager: FoodBillShareManager
    private lateinit var activeFoodOrdersRenderer: ActiveFoodOrdersRenderer


    private val menuItems = mutableListOf<FoodMenuItemEntity>()
    private val gstCategories = mutableListOf<FoodGstCategoryEntity>()
    private val orders = mutableListOf<FoodOrderEntity>()
    private val orderItems = mutableListOf<FoodOrderItemEntity>()
    private val foodBills = mutableListOf<FoodBillEntity>()
    private val archivedBills = mutableListOf<FoodBillEntity>()
    private val rooms = mutableListOf<RoomEntity>()
    private val checkedInBookings = mutableListOf<BookingEntity>()
    private val selectedOrderIds = linkedSetOf<String>()
    private val moneyFormat = java.text.DecimalFormat("0.##")
    private val importMenuCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importMenuItemsFromCsv(uri)
    }

    private val sampleMenuCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) writeSampleMenuCsv(uri)
    }

    private val importFoodGstCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) foodGstCategoryCsvImportManager.importCategoriesFromCsv(uri)
    }

    private val sampleFoodGstCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) foodGstCategoryCsvImportManager.writeSampleCsv(uri, gstCategories)
    }
    private var showingArchivedBills = false
    private var showingOlderBills = false
    private var billsMode = false

    private val foodBillItems = mutableListOf<FoodBillItemEntity>()

    private var hotelProfile: HotelEntity? = null
    private val managedProperties = mutableListOf<ManagedPropertyEntity>()
    private val foodBillHeaderResolver = FoodBillHeaderResolver()
    private lateinit var archivedFoodBillsRenderer: ArchivedFoodBillsRenderer
    private lateinit var selectedFoodOrderFooterRenderer: SelectedFoodOrderFooterRenderer
    private lateinit var generateFoodBillDialog: GenerateFoodBillDialog
    private lateinit var foodMenuCsvImportManager: FoodMenuCsvImportManager

    private lateinit var foodGstCategoryCsvImportManager: FoodGstCategoryCsvImportManager
    private var pendingTakeOrder = false
    private var pendingTargetBookingRemoteId: String? = null
    private var pendingTargetRoomRemoteId: String? = null
    private var pendingFinalBillBookingRemoteId: String? = null
    private var selectedPropertyRemoteId: String? = null
    private var foodMenuMode = false
    private var openMenuManagerAfterImport = false
    private var reopenMenuManagerAfterMenuChange = false
    private val selectedBillPropertyIds = linkedSetOf<String>()
    private var billGuestFilter: String = ""
    private var billDateFromMillis: Long? = null
    private var billDateToMillis: Long? = null

    private var reopenFoodGstCategoriesAfterChange = false
    private data class FoodOrderTarget(
        val label: String,
        val booking: BookingEntity?,
        val room: RoomEntity?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hotelRemoteId = intent.getStringExtra(EXTRA_HOTEL_REMOTE_ID)
        if (hotelRemoteId.isNullOrBlank()) {
            finish()
            return
        }
        repository = FoodBillingRepository(
            context = applicationContext,
            scope = lifecycleScope,
            hotelRemoteId = hotelRemoteId
        )

        pendingTargetBookingRemoteId = intent.getStringExtra(EXTRA_TARGET_BOOKING_REMOTE_ID)
        pendingTargetRoomRemoteId = intent.getStringExtra(EXTRA_TARGET_ROOM_REMOTE_ID)
        pendingFinalBillBookingRemoteId = intent.getStringExtra(EXTRA_OPEN_FINAL_BILL_BOOKING_REMOTE_ID)
        selectedPropertyRemoteId = intent.getStringExtra(EXTRA_PROPERTY_REMOTE_ID)?.takeIf { it.isNotBlank() }
        when (intent.getStringExtra(EXTRA_OPEN_MODE)) {
            MODE_BILLS_ARCHIVE -> {
                billsMode = true
                showingArchivedBills = true
            }
            MODE_TAKE_ORDER -> pendingTakeOrder = !isBookingScopedMode()
            MODE_FOOD_MENU -> foodMenuMode = true
            else -> showingArchivedBills = false
        }

        foodBillShareManager = FoodBillShareManager(this)
        buildScreen()
        observeData()
        repository.startRealtimeSync()

        foodMenuCsvImportManager = FoodMenuCsvImportManager(
            context = this,
            repository = repository
        )

        foodGstCategoryCsvImportManager = FoodGstCategoryCsvImportManager(
            context = this,
            repository = repository
        )

        activeFoodOrdersRenderer = ActiveFoodOrdersRenderer(
            context = this,
            dp = ::dp,
            money = ::money,
            rounded = ::rounded,
            onOrderClicked = { order ->
                showFoodOrderDialog(order)
            },
            onSelectionChanged = { orderRemoteId, checked ->
                if (checked) selectedOrderIds.add(orderRemoteId)
                else selectedOrderIds.remove(orderRemoteId)
                renderOrders()
            }
        )
        archivedFoodBillsRenderer = ArchivedFoodBillsRenderer(
            context = this,
            dp = ::dp,
            money = ::money,
            rounded = ::rounded,
            propertyNameForBill = ::displayPropertyNameForBill,
            onBillClicked = { bill ->
                openFoodBillDialog(bill)
            }
        )

        selectedFoodOrderFooterRenderer = SelectedFoodOrderFooterRenderer(
            context = this,
            dp = ::dp,
            money = ::money,
            rounded = ::rounded,
            onPreviewOrders = { selectedOrders ->
                showPreviewOrdersDialog(selectedOrders)
            },
            onGenerateBill = { selectedOrders ->
                showGenerateFoodBillDialog(selectedOrders)
            }
        )
        generateFoodBillDialog = GenerateFoodBillDialog(
            context = this,
            dp = ::dp,
            onGenerate = { selectedOrders, guestName, guestMobile, guestAddress, guestGstin, paymentMode, discountAmount, withGst ->
                lifecycleScope.launch {
                    val result = repository.generateFoodBill(
                        orderRemoteIds = selectedOrders.map { it.remoteId },
                        guestName = guestName,
                        guestMobile = guestMobile,
                        guestAddress = guestAddress,
                        guestGstin = guestGstin,
                        paymentMode = paymentMode,
                        discountAmount = discountAmount,
                        notes = null,
                        withGst = withGst
                    )

                    when (result) {
                        is SaveResult.Success -> {
                            selectedOrderIds.clear()
                            showingArchivedBills = true
                            Toast.makeText(this@FoodBillingActivity, "Food bill generated.", Toast.LENGTH_SHORT).show()
                            renderOrders()
                        }
                        is SaveResult.Error -> Toast.makeText(this@FoodBillingActivity, result.message, Toast.LENGTH_LONG).show()
                        is SaveResult.Conflict -> Toast.makeText(this@FoodBillingActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )

        renderOrders()
        tryOpenPendingTakeOrder()
    }


    override fun onDestroy() {
        repository.stopRealtimeSync()
        super.onDestroy()
    }


    private fun buildScreen() {
        setContentView(R.layout.activity_food_billing)

        orderList = findViewById(R.id.orderList)
        findViewById<TextView>(R.id.tabArchivedBills).visibility = View.GONE
        if (isBookingScopedMode()) {
            findViewById<TextView>(R.id.tvFoodTitle).text = "Food"
            findViewById<TextView>(R.id.btnFoodMore).visibility = View.GONE
            findViewById<Button>(R.id.btnTakeOrder).text = "+  Take New Order"
            findViewById<TextView>(R.id.tabActiveOrders).text = "Placed Orders"
        } else if (foodMenuMode) {
            findViewById<TextView>(R.id.tvFoodTitle).text = "Food Menu"
            findViewById<Button>(R.id.btnTakeOrder).visibility = View.GONE
            findViewById<TextView>(R.id.btnFoodMore).text = "Manage"
            findViewById<TextView>(R.id.tabActiveOrders).text = "Menu Items"
        } else if (billsMode || showingArchivedBills) {
            findViewById<TextView>(R.id.tvFoodTitle).text = "Bills"
            findViewById<Button>(R.id.btnTakeOrder).visibility = View.GONE
            findViewById<TextView>(R.id.btnFoodMore).visibility = View.VISIBLE
            findViewById<TextView>(R.id.btnFoodMore).text = "⋮"
            findViewById<TextView>(R.id.tabActiveOrders).text = "Today Bills"
        } else {
            findViewById<TextView>(R.id.tvFoodTitle).text = "Manage Orders"
            findViewById<Button>(R.id.btnTakeOrder).text = "+  Restaurant Sale"
            findViewById<TextView>(R.id.tabActiveOrders).text = "Active Orders"
            findViewById<TextView>(R.id.tabArchivedBills).visibility = View.VISIBLE
            findViewById<TextView>(R.id.tabArchivedBills).text = "Billed Orders"
        }

        findViewById<Button>(R.id.btnTakeOrder).setOnClickListener {
            if (isBookingScopedMode() && (targetBooking() == null || targetRoom() == null)) {
                Toast.makeText(this, "Booking is loading. Please try again.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showFoodOrderDialog(null)
        }

        findViewById<TextView>(R.id.btnFoodMore).setOnClickListener {
            if (billsMode || showingArchivedBills) {
                showBillsMoreMenu(it)
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("Food Menu")
                .setItems(arrayOf("Menu Items", "Food Item GST Categories", "Import Menu CSV", "Download Sample CSV")) { _, which ->
                    when (which) {
                        0 -> showMenuItemsManagerDialog()
                        1 -> showFoodGstCategoriesDialog()
                        2 -> importMenuCsvLauncher.launch(arrayOf("text/*", "text/csv", "application/csv", "application/vnd.ms-excel"))
                        3 -> sampleMenuCsvLauncher.launch("food-menu-import-sample.csv")
                    }
                }
                .show()
        }

        findViewById<TextView>(R.id.tabActiveOrders).setOnClickListener {
            if (foodMenuMode) {
                renderOrders()
                return@setOnClickListener
            }
            if (billsMode) {
                showingArchivedBills = true
                showingOlderBills = false
                archivedBills.clear()
                renderOrders()
                return@setOnClickListener
            }
            showingArchivedBills = false
            renderOrders()
        }

        findViewById<TextView>(R.id.tabArchivedBills).setOnClickListener {
            showingArchivedBills = true
            renderOrders()
        }

    }
    private fun observeData() {
        repository.observeFoodGstCategories().observe(this) {
            gstCategories.clear()
            gstCategories.addAll(it.filter { category -> !category.isDeleted && category.isActive })

            if (reopenFoodGstCategoriesAfterChange) {
                reopenFoodGstCategoriesAfterChange = false
                orderList.post {
                    showFoodGstCategoriesDialog()
                }
            }
        }
        repository.observeFoodMenuItems().observe(this) { list ->
            menuItems.clear()
            menuItems.addAll(
                list.filter { item ->
                    !item.isDeleted &&
                            item.isActive &&
                            (selectedPropertyRemoteId == null || item.propertyRemoteId == selectedPropertyRemoteId)
                }
                    .sortedBy { item -> item.itemName.lowercase() }
            )
            if (openMenuManagerAfterImport && menuItems.isNotEmpty()) {
                openMenuManagerAfterImport = false
                orderList.post {
                    showMenuItemsManagerDialog()
                }
            } else if (reopenMenuManagerAfterMenuChange) {
                reopenMenuManagerAfterMenuChange = false
                orderList.post {
                    showMenuItemsManagerDialog()
                }
            }
            tryOpenPendingTakeOrder()
        }
        repository.observeFoodOrders().observe(this) {
            orders.clear()
            orders.addAll(it.filter { order -> !order.isDeleted })
            selectedOrderIds.retainAll(
                orders
                    .filter { order -> canGenerateStandaloneFoodBill(order) }
                    .map { order -> order.remoteId }
                    .toSet()
            )
            renderOrders()
        }
        repository.observeFoodOrderItems().observe(this) {
            orderItems.clear()
            orderItems.addAll(it.filter { item -> !item.isDeleted && !item.isCancelled })
            renderOrders()
        }
        repository.observeRooms().observe(this) {
            rooms.clear()
            rooms.addAll(it.filter { room -> !room.isDeleted })
            tryOpenPendingTakeOrder()
        }
        repository.observeBookings().observe(this) { list ->
            checkedInBookings.clear()
            checkedInBookings.addAll(
                list.filter { booking ->
                    !booking.isDeleted && booking.bookingStatus != BookingStatus.CHECKED_OUT
                }.sortedWith(
                    compareBy<BookingEntity> { it.checkOutMillis }
                        .thenBy { it.guestName.lowercase() }
                )
            )
            tryOpenPendingTakeOrder()
        }
        val (billStart, billEnd) = todayRange()
        repository.observeFoodBillsForRange(billStart, billEnd).observe(this) {
            foodBills.clear()
            foodBills.addAll(it.filter { bill -> !bill.isDeleted })
            renderOrders()
            tryOpenPendingFinalBill()
        }

        repository.observeHotel().observe(this) {
            hotelProfile = it
        }

        repository.observeManagedProperties().observe(this) {
            managedProperties.clear()
            managedProperties.addAll(it.filter { property -> !property.isDeleted })
        }
    }

    private fun tryOpenPendingTakeOrder() {
        if (!pendingTakeOrder) return
        val targetBookingId = pendingTargetBookingRemoteId
        if (!targetBookingId.isNullOrBlank() && checkedInBookings.none { it.remoteId == targetBookingId }) {
            return
        }
        pendingTakeOrder = false
        orderList.post { showFoodOrderDialog(null) }
    }

    private fun renderOrders() {
        orderList.removeAllViews()

        if (billsMode || showingArchivedBills) {
            renderArchivedBills()
            return
        }

        if (foodMenuMode) {
            renderFoodMenuLanding()
            return
        }

        if (isBookingScopedMode()) {
            renderBookingScopedOrders()
            return
        }

        val activeOrderGroups = buildActiveOrderGroups()

        if (activeOrderGroups.isEmpty()) {
            orderList.addView(TextView(this).apply {
                text = "No active food orders yet."
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.DKGRAY)
                setPadding(dp(18), dp(34), dp(18), dp(34))
                background = rounded(Color.WHITE, Color.rgb(230, 230, 230), dp(14).toFloat())
            })
            return
        }

        activeFoodOrdersRenderer.renderGroups(
            container = orderList,
            groups = activeOrderGroups
        )

        renderSelectedOrderFooter()
    }

    private fun renderFoodMenuLanding() {
        if (menuItems.isEmpty()) {
            orderList.addView(TextView(this).apply {
                text = "No menu items yet. Tap Manage to add food items for this property."
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.DKGRAY)
                setPadding(dp(18), dp(34), dp(18), dp(34))
                background = rounded(Color.WHITE, Color.rgb(230, 230, 230), dp(14).toFloat())
            })
            return
        }

        orderList.addView(Button(this).apply {
            text = "Share / Print Menu"
            isAllCaps = false
            setOnClickListener { shareFoodMenu() }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply {
            setMargins(0, 0, 0, dp(12))
        })

        menuItems
            .groupBy { it.categoryName ?: "Food" }
            .forEach { (category, items) ->
                orderList.addView(TextView(this).apply {
                    text = category
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor(GREEN))
                    setPadding(dp(6), dp(16), dp(6), dp(8))
                })
                items.forEach { item ->
                    orderList.addView(TextView(this).apply {
                        text = "${item.itemName}  -  Rs ${money(item.price)}"
                        textSize = 15f
                        setTextColor(Color.rgb(20, 24, 32))
                        setPadding(dp(18), dp(12), dp(18), dp(12))
                        background = rounded(Color.WHITE, Color.rgb(226, 235, 230), dp(14).toFloat())
                    }, LinearLayout.LayoutParams(-1, -2).apply {
                        setMargins(0, 0, 0, dp(8))
                    })
                }
            }
    }

    private fun buildActiveOrderGroups(): List<ActiveFoodOrderGroupUiModel> {
        val activeOrders = orders
            .filter { order ->
                order.status != FoodOrderStatus.CANCELLED &&
                        order.status != FoodOrderStatus.BILLED &&
                        order.billRemoteId.isNullOrBlank()
            }
            .sortedWith(
                compareBy<FoodOrderEntity> { displayPropertyNameForOrder(it).lowercase(Locale.getDefault()) }
                    .thenBy { it.roomName ?: "Walk-in Guest" }
                    .thenBy { it.orderMillis }
            )

        return activeOrders
            .groupBy { order -> displayPropertyNameForOrder(order) to (order.roomName ?: "Walk-in Guest") }
            .map { (propertyAndRoom, roomOrders) ->
                ActiveFoodOrderGroupUiModel(
                    propertyName = propertyAndRoom.first,
                    roomName = propertyAndRoom.second,
                    roomTotal = roomOrders.sumOf { it.totalAmount },
                    orders = roomOrders.map { order ->
                        ActiveFoodOrderUiModel(
                            order = order,
                            itemCount = orderItems.count { it.orderRemoteId == order.remoteId },
                            isSelected = selectedOrderIds.contains(order.remoteId),
                            canSelect = canGenerateStandaloneFoodBill(order)
                        )
                    }
                )
            }
    }

    private fun displayPropertyNameForOrder(order: FoodOrderEntity): String {
        val roomPropertyRemoteId = order.roomRemoteId
            ?.let { roomRemoteId -> rooms.firstOrNull { it.remoteId == roomRemoteId }?.propertyRemoteId }
            ?: order.roomName
                ?.let { roomName -> rooms.firstOrNull { it.roomName == roomName }?.propertyRemoteId }
        return displayPropertyName(order.propertyRemoteId ?: roomPropertyRemoteId)
    }

    private fun displayPropertyNameForBill(bill: FoodBillEntity): String {
        return bill.propertyDisplayName?.takeIf { it.isNotBlank() }
            ?: displayPropertyName(bill.propertyRemoteId)
    }

    private fun displayPropertyName(propertyRemoteId: String?): String {
        val cleanPropertyRemoteId = propertyRemoteId?.takeIf { it.isNotBlank() }
        return cleanPropertyRemoteId
            ?.let { id -> managedProperties.firstOrNull { it.remoteId == id }?.propertyName }
            ?: hotelProfile?.hotelName
            ?: "Restaurant / Common"
    }

    private fun canGenerateStandaloneFoodBill(order: FoodOrderEntity): Boolean {
        return order.bookingRemoteId.isNullOrBlank()
    }

    private fun renderBookingScopedOrders() {
        updateBookingScopedHeader()
        val bookingId = pendingTargetBookingRemoteId
        val scopedOrders = orders
            .filter { order ->
                order.bookingRemoteId == bookingId &&
                        order.status != FoodOrderStatus.CANCELLED &&
                        order.status != FoodOrderStatus.BILLED
            }
            .sortedByDescending { it.orderMillis }

        if (scopedOrders.isEmpty()) {
            orderList.addView(TextView(this).apply {
                text = "No food orders linked to this booking yet."
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.DKGRAY)
                setPadding(dp(18), dp(34), dp(18), dp(34))
                background = rounded(Color.WHITE, Color.rgb(230, 230, 230), dp(14).toFloat())
            })
            return
        }

        scopedOrders.forEach { order ->
            orderList.addView(bookingScopedOrderCard(order))
        }
    }

    private fun bookingScopedOrderCard(order: FoodOrderEntity): View {
        val items = orderItems.filter { it.orderRemoteId == order.remoteId }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            background = rounded(Color.WHITE, Color.rgb(232, 232, 238), dp(16).toFloat())
            setOnClickListener { showFoodOrderDialog(order) }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = order.orderNumber ?: "Food Order"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#5B2CF1"))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(header)

        card.addView(TextView(this).apply {
            text = SimpleDateFormat("dd MMM yyyy  -  hh:mm a", Locale.getDefault()).format(Date(order.orderMillis))
            textSize = 14f
            setTextColor(Color.rgb(85, 83, 96))
            setPadding(0, dp(8), 0, dp(16))
        })

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        body.addView(TextView(this).apply {
            text = buildString {
                append("${items.size} Item${if (items.size == 1) "" else "s"}")
                items.take(4).forEach { item ->
                    append("\n")
                    append(money(item.quantity)).append(" x ").append(item.itemName)
                }
            }
            textSize = 15f
            setTextColor(Color.rgb(52, 50, 62))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(TextView(this).apply {
            text = "Rs ${money(order.totalAmount)}   >"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(20, 24, 32))
        })
        card.addView(body)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(card)
            setPadding(0, 0, 0, dp(14))
        }
    }


    private fun renderArchivedBills() {
        renderBillSearchControls()

        val billsToShow = applyBillFilters(if (showingOlderBills) archivedBills else foodBills)
        if (billsToShow.isEmpty()) {
            orderList.addView(TextView(this).apply {
                text = if (showingOlderBills) "No archived bills found for these filters." else "No bills generated today for these filters."
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.DKGRAY)
                setPadding(dp(18), dp(34), dp(18), dp(34))
                background = rounded(Color.WHITE, Color.rgb(230, 230, 230), dp(14).toFloat())
            })
            return
        }

        archivedFoodBillsRenderer.render(
            container = orderList,
            bills = billsToShow
        )
    }

    private fun renderBillSearchControls() {
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(14))
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        buttonRow.addView(Button(this).apply {
            text = "Filters"
            isAllCaps = false
            setOnClickListener { showBillFiltersDialog() }
        }, LinearLayout.LayoutParams(-1, dp(46)))
        controls.addView(buttonRow)

        val filterSummary = activeBillFilterSummary()
        if (filterSummary.isNotBlank()) {
            controls.addView(TextView(this).apply {
                text = filterSummary
                textSize = 12f
                setTextColor(Color.rgb(72, 72, 82))
                setPadding(dp(4), dp(8), dp(4), 0)
            })
        }
        orderList.addView(controls)
    }

    private fun showBillsMoreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Archive Bills")
            menu.add("Today Bills")
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Archive Bills" -> lifecycleScope.launch {
                        archivedBills.clear()
                        archivedBills.addAll(repository.getArchivedFoodBillsBefore(todayRange().first))
                        showingOlderBills = true
                        renderOrders()
                    }
                    "Today Bills" -> {
                        showingOlderBills = false
                        archivedBills.clear()
                        renderOrders()
                    }
                }
                true
            }
            show()
        }
    }

    private fun applyBillFilters(bills: List<FoodBillEntity>): List<FoodBillEntity> {
        val guestQuery = billGuestFilter.trim().lowercase(Locale.getDefault())
        return bills.filter { bill ->
            val propertyMatches = selectedBillPropertyIds.isEmpty() ||
                    bill.propertyRemoteId?.takeIf { it.isNotBlank() } in selectedBillPropertyIds
            val guestMatches = guestQuery.isBlank() ||
                    bill.guestName.orEmpty().lowercase(Locale.getDefault()).contains(guestQuery)
            val fromMatches = billDateFromMillis?.let { bill.billMillis >= it } ?: true
            val toMatches = billDateToMillis?.let { bill.billMillis <= it } ?: true
            propertyMatches && guestMatches && fromMatches && toMatches
        }
    }

    private fun showBillFiltersDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(6))
        }

        val propertyChecks = managedProperties
            .sortedBy { it.propertyName.lowercase(Locale.getDefault()) }
            .map { property ->
                property to CheckBox(this).apply {
                    text = property.propertyName
                    isChecked = selectedBillPropertyIds.contains(property.remoteId)
                    setPadding(0, dp(4), 0, dp(4))
                }
            }

        content.addView(label("Property"))
        if (propertyChecks.isEmpty()) {
            content.addView(label("No properties added yet."))
        } else {
            propertyChecks.forEach { (_, checkBox) -> content.addView(checkBox) }
        }

        val guestInput = input("Guest name", billGuestFilter)
        val checkInButton = dateFilterButton("Check-in", billDateFromMillis) { picked ->
            billDateFromMillis = picked
        }
        val checkOutButton = dateFilterButton("Check-out", billDateToMillis) { picked ->
            billDateToMillis = picked?.let { endOfDay(it) }
        }

        content.addView(label("Guest"))
        content.addView(guestInput)
        content.addView(label("Stay dates"))
        content.addView(checkInButton)
        content.addView(checkOutButton)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Bill Filters")
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("Apply", null)
            .setNeutralButton("Clear", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            selectedBillPropertyIds.clear()
            propertyChecks.filter { (_, checkBox) -> checkBox.isChecked }
                .forEach { (property, _) -> selectedBillPropertyIds.add(property.remoteId) }
            billGuestFilter = guestInput.text.toString().trim()
            dialog.dismiss()
            renderOrders()
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            clearBillFilters()
            dialog.dismiss()
            renderOrders()
        }
    }

    private fun clearBillFilters() {
        selectedBillPropertyIds.clear()
        billGuestFilter = ""
        billDateFromMillis = null
        billDateToMillis = null
    }

    private fun activeBillFilterSummary(): String {
        val parts = mutableListOf<String>()
        if (selectedBillPropertyIds.isNotEmpty()) {
            parts.add("${selectedBillPropertyIds.size} propert${if (selectedBillPropertyIds.size == 1) "y" else "ies"}")
        }
        if (billGuestFilter.isNotBlank()) parts.add("Guest: $billGuestFilter")
        billDateFromMillis?.let { parts.add("Check-in ${formatFilterDate(it)}") }
        billDateToMillis?.let { parts.add("Check-out ${formatFilterDate(it)}") }
        return parts.joinToString("  •  ")
    }

    private fun formatFilterDate(millis: Long?): String {
        return millis?.let { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it)) }.orEmpty()
    }

    private fun dateFilterButton(
        label: String,
        value: Long?,
        onPicked: (Long?) -> Unit
    ): Button {
        return Button(this).apply {
            fun refresh(selected: Long?) {
                text = selected?.let { "$label: ${formatFilterDate(it)}" } ?: "$label: Select"
            }
            isAllCaps = false
            refresh(value)
            setOnClickListener {
                showDatePicker(value ?: System.currentTimeMillis()) { picked ->
                    onPicked(picked)
                    refresh(picked)
                }
            }
        }
    }

    private fun showDatePicker(initialMillis: Long, onPicked: (Long) -> Unit) {
        val calendar = Calendar.getInstance().apply { timeInMillis = initialMillis }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    clear()
                    set(year, month, day, 0, 0, 0)
                }.timeInMillis
                onPicked(picked)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun endOfDay(millis: Long): Long = millis + 86_399_999L

    private fun tryOpenPendingFinalBill() {
        val bookingRemoteId = pendingFinalBillBookingRemoteId ?: return
        val bill = foodBills
            .filter { it.isConsolidatedGuestBill() && it.remoteId.startsWith("${bookingRemoteId}_final_bill_") }
            .maxByOrNull { it.billMillis }
            ?: return

        pendingFinalBillBookingRemoteId = null
        showingArchivedBills = true
        renderOrders()
        orderList.post {
            openFoodBillDialog(bill)
        }
    }

    private fun FoodBillEntity.isConsolidatedGuestBill(): Boolean = remoteId.contains("_final_bill_")
    private data class ConsolidatedBillSummary(
        val stay: Double,
        val food: Double,
        val paid: Double,
        val balance: Double
    )

    private fun FoodBillEntity.consolidatedSummary(): ConsolidatedBillSummary {
        val note = notes.orEmpty()
        fun value(label: String): Double {
            val regex = Regex("$label\\s+([0-9.]+)", RegexOption.IGNORE_CASE)
            return regex.find(note)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        }
        return ConsolidatedBillSummary(
            stay = value("Stay"),
            food = value("Food"),
            paid = value("Paid"),
            balance = value("Balance")
        )
    }

    private fun openFoodBillDialog(bill: FoodBillEntity) {
        lifecycleScope.launch {
            val items = repository.getFoodBillItemsForBill(bill.remoteId)
            showArchivedFoodBillDialog(bill, items)
        }
    }

    private fun showArchivedFoodBillDialog(
        bill: FoodBillEntity,
        items: List<FoodBillItemEntity>
    ) {

        val header = resolveBillPropertyHeader(bill, items)

        val wrapper = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        content.addView(TextView(this).apply {
            text = header.name.uppercase()
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(20, 24, 32))
        })

        if (header.address.isNotBlank()) {
            content.addView(TextView(this).apply {
                text = header.address
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.DKGRAY)
            })
        }

        if (header.gstin.isNotBlank()) {
            content.addView(TextView(this).apply {
                text = "GSTIN: ${header.gstin}"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.DKGRAY)
            })
        }

        content.addView(TextView(this).apply {
            text = if (bill.isConsolidatedGuestBill()) "CONSOLIDATED GUEST BILL" else "TAX INVOICE"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        })

        content.addView(TextView(this).apply {
            text = "Bill No: ${bill.billNumber}\nDate: ${
                SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(bill.billMillis))
            }\nRooms: ${bill.roomsIncluded}"
            textSize = 12f
            setTextColor(Color.rgb(30, 30, 30))
            setPadding(0, 0, 0, dp(10))
        })

        val horizontalScroll = android.widget.HorizontalScrollView(this)

        val table = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        table.addView(billTableRow(
            listOf("Item", "HSN", "Qty", "Rate", "Taxable", "CGST", "SGST", "GST", "Payable"),
            header = true
        ))

        items.forEach { item ->
            table.addView(billTableRow(
                listOf(
                    item.itemName,
                    item.hsnSacCode ?: "-",
                    money(item.quantity),
                    money(item.unitPrice),
                    money(item.taxableAmount),
                    money(item.cgstAmount),
                    money(item.sgstAmount),
                    money(item.gstAmount),
                    money(item.lineTotal)
                ),
                header = false
            ))
        }

        horizontalScroll.addView(table)
        content.addView(horizontalScroll)

        content.addView(TextView(this).apply {
            text = "\nGST Summary"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(20, 24, 32))
        })

        val summaryScroll = android.widget.HorizontalScrollView(this)
        val summaryTable = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        summaryTable.addView(billTableRow(
            listOf("HSN", "GST%", "Taxable", "CGST", "SGST", "GST"),
            header = true
        ))

        items.groupBy { "${it.hsnSacCode ?: "-"}|${it.gstRatePercent}" }
            .forEach { (_, groupItems) ->
                val first = groupItems.first()
                summaryTable.addView(billTableRow(
                    listOf(
                        first.hsnSacCode ?: "-",
                        money(first.gstRatePercent),
                        money(groupItems.sumOf { it.taxableAmount }),
                        money(groupItems.sumOf { it.cgstAmount }),
                        money(groupItems.sumOf { it.sgstAmount }),
                        money(groupItems.sumOf { it.gstAmount })
                    ),
                    header = false
                ))
            }

        summaryScroll.addView(summaryTable)
        content.addView(summaryScroll)

        content.addView(TextView(this).apply {
            text = if (bill.isConsolidatedGuestBill()) {
                val summary = bill.consolidatedSummary()
                buildString {
                    append("\nStay Total: ₹${money(summary.stay)}")
                    append("\nFood Total: ₹${money(summary.food)}")
                    if (bill.discountAmount > 0.0) {
                        append("\nDiscount: ₹${money(bill.discountAmount)}")
                    }
                    append("\nGrand Total: ₹${money(bill.grandTotal)}")
                    append("\nPaid: ₹${money(summary.paid)}")
                    append("\nBalance: ₹${money(summary.balance)}")
                }
            } else {
                "\nSubtotal: ₹${money(bill.subtotal)}" +
                        "\nTaxable: ₹${money(bill.taxableAmount)}" +
                        "\nCGST: ₹${money(bill.cgstAmount)}" +
                        "\nSGST: ₹${money(bill.sgstAmount)}" +
                        "\nTotal GST: ₹${money(bill.gstAmount)}" +
                        "\nGrand Total: ₹${money(bill.grandTotal)}"
            }
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
            setPadding(0, dp(10), 0, dp(16))
        })

        content.addView(TextView(this).apply {
            text = "\n\nHotel Stamp & Authorized Signature"
            textSize = 13f
            gravity = Gravity.END
            setTextColor(Color.DKGRAY)
        })

        wrapper.addView(content)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (bill.isConsolidatedGuestBill()) "Consolidated Bill" else "Food Bill")
            .setView(wrapper)
            .setPositiveButton("Share") { _, _ ->
                shareFoodBillPdf(bill, items)
            }
            .setNeutralButton("Print") { _, _ ->
                shareFoodBillPdf(bill, items)
            }
            .setNegativeButton("Close", null)
            .create()

        dialog.show()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun billTableRow(
        values: List<String>,
        header: Boolean
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(
                if (header) Color.rgb(235, 248, 245) else Color.WHITE,
                Color.rgb(220, 228, 225),
                0f
            )
        }

        val widths = listOf(
            dp(130), // Item
            dp(70),  // HSN
            dp(48),  // Qty
            dp(60),  // Rate
            dp(80),  // Taxable
            dp(70),  // CGST
            dp(70),  // SGST
            dp(70),  // GST
            dp(80)   // Payable
        )

        values.forEachIndexed { index, value ->
            row.addView(TextView(this).apply {
                text = value
                textSize = if (header) 12f else 11f
                typeface = if (header) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(Color.rgb(20, 24, 32))
                gravity = if (index == 0) Gravity.START else Gravity.END
                setPadding(dp(6), dp(8), dp(6), dp(8))
                background = rounded(Color.TRANSPARENT, Color.rgb(226, 232, 230), 0f)
                maxLines = 2
            }, LinearLayout.LayoutParams(widths.getOrElse(index) { dp(70) }, -2))
        }

        return row
    }


    private fun shareFoodBillPdf(
        bill: FoodBillEntity,
        items: List<FoodBillItemEntity>
    ) {
        val headerInfo = resolveBillPropertyHeader(bill, items)

        foodBillShareManager.shareFoodBillPdf(
            bill = bill,
            items = items,
            header = InvoicePdfGenerator.BusinessHeader(
                name = headerInfo.name,
                address = headerInfo.address,
                gstin = headerInfo.gstin,
                phone = headerInfo.phone
            )
        )
    }
    private fun renderSelectedOrderFooter() {
        selectedFoodOrderFooterRenderer.render(
            container = orderList,
            orders = orders,
            selectedOrderIds = selectedOrderIds
        )
    }



    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun showMenuItemsManagerDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 10, 24, 0)
        }
        layout.addView(TextView(this).apply {
            text = "Tap any item to edit price, category, or GST category."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 12)
        })
        if (menuItems.isEmpty()) {
            layout.addView(label("No menu items yet. Use Add Item or Import CSV."))
        } else {
            menuItems.groupBy { it.categoryName ?: "Food" }.forEach { (category, items) ->
                layout.addView(TextView(this).apply {
                    text = category
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor(GREEN))
                    setPadding(0, 14, 0, 6)
                })
                items.forEach { item ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(14, 12, 14, 12)
                        background = rounded(Color.WHITE, Color.rgb(226, 235, 230), 14f)
                        setOnClickListener { showMenuItemDialog(item) }
                    }
                    row.addView(TextView(this).apply {
                        text = "${item.itemName}\nRs ${money(item.price)}"
                        textSize = 15f
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    row.addView(TextView(this).apply {
                        text = "Edit"
                        textSize = 14f
                        setTextColor(Color.parseColor(BRAND))
                        typeface = Typeface.DEFAULT_BOLD
                    })
                    layout.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                        setMargins(0, 0, 0, 8)
                    })
                }
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Menu Items")
            .setView(ScrollView(this).apply { addView(layout) })
            .setPositiveButton("Add Item") { _, _ -> showMenuItemDialog(null) }
            .setNeutralButton("Import CSV") { _, _ ->
                importMenuCsvLauncher.launch(arrayOf("text/*", "text/csv", "application/csv", "application/vnd.ms-excel"))
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun showFoodGstCategoriesDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 10, 24, 0)
        }

        layout.addView(TextView(this).apply {
            text = "Changing GST categories affects future menu items/orders only. Old orders and issued bills will not change."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 12)
        })

        layout.addView(Button(this).apply {
            text = "Download Sample CSV"
            isAllCaps = false
            setOnClickListener {
                sampleFoodGstCsvLauncher.launch("food-item-gst-categories-sample.csv")
            }
        })

        if (gstCategories.isEmpty()) {
            layout.addView(label("No GST categories yet. Add one."))
        } else {
            gstCategories.sortedBy { it.categoryName.lowercase(Locale.getDefault()) }.forEach { category ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(14, 12, 14, 12)
                    background = rounded(Color.WHITE, Color.rgb(226, 235, 230), 14f)
                    setOnClickListener { showFoodGstCategoryEditDialog(category) }
                }

                row.addView(TextView(this).apply {
                    text = "${category.categoryName}\nHSN/SAC: ${category.hsnSacCode ?: "-"} | GST ${money(category.gstRatePercent)}%"
                    textSize = 15f
                }, LinearLayout.LayoutParams(0, -2, 1f))

                row.addView(TextView(this).apply {
                    text = "Edit"
                    textSize = 14f
                    setTextColor(Color.parseColor(BRAND))
                    typeface = Typeface.DEFAULT_BOLD
                })

                layout.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, 0, 0, 8)
                })
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Food Item GST Categories")
            .setView(ScrollView(this).apply { addView(layout) })
            .setPositiveButton("Add Category") { _, _ -> showFoodGstCategoryEditDialog(null) }
            .setNeutralButton("Upload CSV") { _, _ ->
                importFoodGstCsvLauncher.launch(arrayOf("text/*", "text/csv", "application/csv", "application/vnd.ms-excel"))
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun showFoodGstCategoryEditDialog(existing: FoodGstCategoryEntity?) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 16, 36, 0)
        }

        val nameInput = input("Category name", existing?.categoryName)
        val hsnInput = input("HSN/SAC code", existing?.hsnSacCode)
        val gstInput = input("GST %", existing?.gstRatePercent?.let { money(it) }, numeric = true)
        val cgstInput = input("CGST %", existing?.cgstRatePercent?.let { money(it) }, numeric = true)
        val sgstInput = input("SGST %", existing?.sgstRatePercent?.let { money(it) }, numeric = true)
        val cessInput = input("CESS %", existing?.cessRatePercent?.let { money(it) }, numeric = true)

        listOf(nameInput, hsnInput, gstInput, cgstInput, sgstInput, cessInput).forEach {
            layout.addView(it)
        }

        fun saveCategory() {
            val gst = gstInput.text.toString().toDoubleOrNull() ?: 0.0
            val cgst = cgstInput.text.toString().toDoubleOrNull() ?: (gst / 2.0)
            val sgst = sgstInput.text.toString().toDoubleOrNull() ?: (gst / 2.0)
            val cess = cessInput.text.toString().toDoubleOrNull() ?: 0.0
            reopenFoodGstCategoriesAfterChange = true
            repository.saveFoodGstCategory(
                existing = existing,
                categoryName = nameInput.text.toString(),
                hsnSacCode = hsnInput.text.toString(),
                gstRatePercent = gst,
                cgstRatePercent = cgst,
                sgstRatePercent = sgst,
                cessRatePercent = cess,
                isDefault = existing?.isDefault ?: false
            )
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add GST Category" else "Edit GST Category")
            .setView(layout)
            .setPositiveButton("Save", null)
            .setNegativeButton("Back", null)
            .apply {
                if (existing != null) {
                    setNeutralButton("Delete") { _, _ ->
                        repository.deleteFoodGstCategory(existing)
                    }
                }
            }
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val gst = gstInput.text.toString().toDoubleOrNull() ?: 0.0
                        val cgst = cgstInput.text.toString().toDoubleOrNull() ?: (gst / 2.0)
                        val sgst = sgstInput.text.toString().toDoubleOrNull() ?: (gst / 2.0)
                        val cess = cessInput.text.toString().toDoubleOrNull() ?: 0.0
                        val newHsn = hsnInput.text.toString().trim()

                        val taxChanged = existing != null && (
                                existing.hsnSacCode.orEmpty().trim() != newHsn ||
                                        existing.gstRatePercent != gst ||
                                        existing.cgstRatePercent != cgst ||
                                        existing.sgstRatePercent != sgst ||
                                        existing.cessRatePercent != cess
                                )

                        if (!taxChanged) {
                            saveCategory()
                            dialog.dismiss()
                            return@setOnClickListener
                        }

                        val linkedCount = menuItems.count {
                            !it.isDeleted && it.gstCategoryRemoteId == existing?.remoteId
                        }

                        AlertDialog.Builder(this)
                            .setTitle("Update GST/HSN?")
                            .setMessage(
                                "Are you sure you want to change the HSN/GST?\n\n" +
                                        "It will impact $linkedCount food item(s).\n\n" +
                                        "Older orders and issued bills will remain as they are.\n" +
                                        "Future orders and bills will use the new HSN/GST."
                            )
                            .setPositiveButton("Update & Save") { _, _ ->
                                saveCategory()
                                dialog.dismiss()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
                dialog.show()
            }
    }

    private fun showMenuItemDialog(existing: FoodMenuItemEntity?) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 16, 36, 0)
        }
        val nameInput = input("Item name", existing?.itemName)
        val categoryInput = input("Category", existing?.categoryName)
        val priceInput = input("Price", existing?.price?.let { money(it) }, numeric = true)
        val categoryChoices = gstCategories.ifEmpty {
            listOf(FoodGstCategoryEntity(remoteId = "", hotelRemoteId = repository.hotelRemoteId, categoryName = "Restaurant / In-Room Food Service"))
        }
        val gstSpinner = spinner(categoryChoices.map { "${it.categoryName} (${money(it.gstRatePercent)}%)" }).apply {
            val selected = categoryChoices.indexOfFirst { it.remoteId == existing?.gstCategoryRemoteId }
            if (selected >= 0) setSelection(selected)
        }
        listOf(nameInput, categoryInput, priceInput).forEach { layout.addView(it) }
        layout.addView(label("GST Category"))
        layout.addView(gstSpinner)
        layout.addView(TextView(this).apply {
            text = "GST rates and HSN/SAC codes are suggested defaults only. Please confirm the correct GST rate and HSN/SAC code with your CA or tax consultant before using them on invoices."
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, 10, 0, 4)
        })

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add Menu Item" else "Edit Menu Item")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val selectedCategory = categoryChoices.getOrNull(gstSpinner.selectedItemPosition)
                val resolvedGstRate = selectedCategory?.gstRatePercent
                    ?: existing?.gstRatePercent
                    ?: 0.0

                repository.saveFoodMenuItem(
                    existing = existing,
                    itemName = nameInput.text.toString(),
                    categoryName = categoryInput.text.toString(),
                    price = priceInput.text.toString().toDoubleOrNull() ?: 0.0,
                    gstRatePercent = resolvedGstRate,
                    propertyRemoteId = selectedPropertyRemoteId ?: existing?.propertyRemoteId,
                    gstCategoryRemoteId = selectedCategory?.remoteId ?: existing?.gstCategoryRemoteId,
                    gstCategoryName = selectedCategory?.categoryName ?: existing?.gstCategoryName,
                    hsnSacCode = selectedCategory?.hsnSacCode ?: existing?.hsnSacCode,
                    cgstRatePercent = selectedCategory?.cgstRatePercent
                        ?: existing?.cgstRatePercent
                        ?: (resolvedGstRate / 2.0),
                    sgstRatePercent = selectedCategory?.sgstRatePercent
                        ?: existing?.sgstRatePercent
                        ?: (resolvedGstRate / 2.0),
                    cessRatePercent = selectedCategory?.cessRatePercent
                        ?: existing?.cessRatePercent
                        ?: 0.0
                )
                if (foodMenuMode) {
                    reopenMenuManagerAfterMenuChange = true
                }
            }
            .setNegativeButton("Back") { _, _ ->
                if (foodMenuMode) {
                    showMenuItemsManagerDialog()
                }
            }
            .apply {
                if (existing != null) {
                    setNeutralButton("Delete") { _, _ ->
                        repository.deleteFoodMenuItem(existing)
                        if (foodMenuMode) {
                            reopenMenuManagerAfterMenuChange = true
                        }
                    }
                }
            }
            .show()
    }

    private fun showFoodOrderDialog(existing: FoodOrderEntity?) {
        if (menuItems.isEmpty()) {
            Toast.makeText(this, "Add menu items first.", Toast.LENGTH_SHORT).show()
            showMenuItemDialog(null)
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.activity_take_food_order, null)

        val roomSpinner = view.findViewById<Spinner>(R.id.spRoom)
        val guestInput = view.findViewById<EditText>(R.id.etGuestName)
        val searchInput = view.findViewById<EditText>(R.id.etSearch)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupCategories)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvMenu)
        val cartItemsText = view.findViewById<TextView>(R.id.tvCartItems)
        val cartAmountText = view.findViewById<TextView>(R.id.tvCartAmount)
        val previewButton = view.findViewById<Button>(R.id.btnPreviewOrder)
        val restaurantSaleMode = existing == null && !isBookingScopedMode()
        fun updateSearchMode() {
            val searching = searchInput.hasFocus() || searchInput.text.toString().isNotBlank()

            roomSpinner.visibility = if (restaurantSaleMode || searching) View.GONE else View.VISIBLE
            guestInput.visibility = if (restaurantSaleMode || searching) View.GONE else View.VISIBLE
            chipGroup.visibility = if (searching) View.GONE else View.VISIBLE
        }

        val orderTargets = buildFoodOrderTargets(existing)
        val roomLabels = orderTargets.map { target -> target.label }

        roomSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            roomLabels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        val selectedRoomIndex = orderTargets.indexOfFirst { target ->
            if (existing == null && !pendingTargetBookingRemoteId.isNullOrBlank()) {
                target.booking?.remoteId == pendingTargetBookingRemoteId &&
                        (pendingTargetRoomRemoteId.isNullOrBlank() || target.room?.remoteId == pendingTargetRoomRemoteId)
            } else {
                target.room?.remoteId == existing?.roomRemoteId &&
                        target.booking?.remoteId == existing?.bookingRemoteId
            }
        }
        if (selectedRoomIndex >= 0) {
            roomSpinner.setSelection(selectedRoomIndex)
        }

        val launchTarget = orderTargets.getOrNull(selectedRoomIndex)
        guestInput.setText(
            existing?.guestName
                ?: launchTarget?.booking?.guestName
                ?: if (restaurantSaleMode) "Restaurant Sale" else ""
        )
        val lockedLaunchTarget = existing == null && !pendingTargetBookingRemoteId.isNullOrBlank() && selectedRoomIndex >= 0
        if (lockedLaunchTarget) {
            roomSpinner.isEnabled = false
            guestInput.isEnabled = false
        }

        val quantities = linkedMapOf<String, Int>()

        if (existing != null) {
            orderItems
                .filter { it.orderRemoteId == existing.remoteId }
                .forEach { item ->
                    if (!item.menuItemRemoteId.isNullOrBlank()) {
                        quantities[item.menuItemRemoteId] = item.quantity.toInt()
                    }
                }
        }

        val categories = mutableListOf("All")
        categories.addAll(
            menuItems
                .map { it.categoryName?.trim().orEmpty() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        )

        var selectedCategory = "All"
        val filteredMenuItems = menuItems.toMutableList()

        lateinit var adapter: FoodMenuAdapter

        fun buildDraftItems(): MutableList<FoodOrderItemEntity> {
            val draftItems = mutableListOf<FoodOrderItemEntity>()

            quantities.forEach { (menuItemRemoteId, qty) ->
                if (qty <= 0) return@forEach

                val menuItem = menuItems.firstOrNull { it.remoteId == menuItemRemoteId }
                    ?: return@forEach

                val existingOrderItem = if (existing == null) {
                    null
                } else {
                    orderItems.firstOrNull {
                        it.orderRemoteId == existing.remoteId &&
                                it.menuItemRemoteId == menuItem.remoteId
                    }
                }

                val resolvedGstRate = existingOrderItem?.gstRatePercent
                    ?: menuItem.gstRatePercent

                draftItems.add(
                    FoodOrderItemEntity(
                        localId = existingOrderItem?.localId ?: 0,
                        remoteId = existingOrderItem?.remoteId ?: "",
                        hotelRemoteId = repository.hotelRemoteId,
                        orderRemoteId = existing?.remoteId ?: "",
                        menuItemRemoteId = menuItem.remoteId,
                        itemName = menuItem.itemName,
                        quantity = qty.toDouble(),
                        unitPrice = menuItem.price,
                        gstCategoryRemoteId = existingOrderItem?.gstCategoryRemoteId ?: menuItem.gstCategoryRemoteId,
                        gstCategoryName = existingOrderItem?.gstCategoryName ?: menuItem.gstCategoryName,
                        hsnSacCode = existingOrderItem?.hsnSacCode ?: menuItem.hsnSacCode,
                        gstRatePercent = existingOrderItem?.gstRatePercent ?: menuItem.gstRatePercent,
                        cgstRatePercent = existingOrderItem?.cgstRatePercent ?: menuItem.cgstRatePercent,
                        sgstRatePercent = existingOrderItem?.sgstRatePercent ?: menuItem.sgstRatePercent,
                        cessRatePercent = existingOrderItem?.cessRatePercent ?: menuItem.cessRatePercent,
                        syncState = "PENDING"
                    )
                )
            }

            return draftItems
        }

        fun updateCart() {
            val draftItems = buildDraftItems()
            val totalQty = draftItems.sumOf { it.quantity }.toInt()
            val totalAmount = draftItems.sumOf { it.quantity * it.unitPrice }

            cartItemsText.text = "$totalQty Item${if (totalQty == 1) "" else "s"}"
            cartAmountText.text = "₹${money(totalAmount)}"

            previewButton.isEnabled = draftItems.isNotEmpty()
            previewButton.alpha = if (draftItems.isEmpty()) 0.5f else 1f
        }

        fun applyFilters() {
            val query = searchInput.text.toString().trim().lowercase()

            val result = menuItems.filter { item ->
                val categoryMatches =
                    selectedCategory == "All" ||
                            item.categoryName.orEmpty().equals(selectedCategory, ignoreCase = true)

                val queryMatches =
                    query.isBlank() ||
                            item.itemName.lowercase().contains(query) ||
                            item.categoryName.orEmpty().lowercase().contains(query)

                categoryMatches && queryMatches
            }

            adapter.updateData(result)
        }

        chipGroup.removeAllViews()

        categories.forEach { category ->
            val chip = Chip(this).apply {
                text = category
                isCheckable = true
                isChecked = category == "All"
                setOnClickListener {
                    selectedCategory = category
                    applyFilters()
                }
            }
            chipGroup.addView(chip)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = FoodMenuAdapter(
            items = filteredMenuItems,
            quantities = quantities,
            onQuantityChanged = {
                updateCart()
            }
        )
        recyclerView.adapter = adapter

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                applyFilters()
                updateSearchMode()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        searchInput.setOnFocusChangeListener { _, _ ->
            updateSearchMode()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(
                when {
                    existing != null -> "Edit Order"
                    restaurantSaleMode -> "Restaurant Sale"
                    else -> "Take Order"
                }
            )
            .setView(view)
            .setNegativeButton("Back", null)
            .create()

        previewButton.setOnClickListener {
            val draftItems = buildDraftItems()

            if (draftItems.isEmpty()) {
                Toast.makeText(this, "Select at least one item.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val target = if (restaurantSaleMode) orderTargets.first() else orderTargets.getOrNull(roomSpinner.selectedItemPosition)
                ?: orderTargets.first()
            val room = target.room
            val booking = target.booking
            val roomName = room?.roomName ?: "Restaurant Sale"
            val guestName = guestInput.text.toString().trim().ifEmpty {
                booking?.guestName?.takeIf { it.isNotBlank() } ?: roomName
            }

            showFoodOrderPreviewDialog(
                existing = existing,
                roomName = roomName,
                guestName = guestName,
                draftItems = draftItems,
                onSaveKot = { previewDialog ->
                    dialog.dismiss()

                    saveFoodOrderFromNewUi(
                        existing = existing,
                        booking = booking,
                        room = room,
                        guestName = guestName,
                        draftItems = draftItems,
                        status = FoodOrderStatus.KOT,
                        orderDialog = dialog,
                        previewDialog = previewDialog
                    )
                },
                onFinalize = { previewDialog ->
                    dialog.dismiss()

                    saveFoodOrderFromNewUi(
                        existing = existing,
                        booking = booking,
                        room = room,
                        guestName = guestName,
                        draftItems = draftItems,
                        status = FoodOrderStatus.FINALIZED,
                        orderDialog = dialog,
                        previewDialog = previewDialog
                    )
                }
            )
        }

        dialog.setOnShowListener {
            applyFilters()
            updateCart()
        }

        dialog.show()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

    }
    private fun buildFoodOrderTargets(existing: FoodOrderEntity?): List<FoodOrderTarget> {
        val roomById = rooms.associateBy { room -> room.remoteId }
        val targets = mutableListOf(FoodOrderTarget("Restaurant Sale", null, null))
        val allowBookingTargets = isBookingScopedMode() || !existing?.bookingRemoteId.isNullOrBlank()
        val pendingBookingId = pendingTargetBookingRemoteId
        if (allowBookingTargets && !pendingBookingId.isNullOrBlank()) {
            val booking = checkedInBookings.firstOrNull { it.remoteId == pendingBookingId }
            val room = pendingTargetRoomRemoteId
                ?.let { roomRemoteId -> roomById[roomRemoteId] }
                ?: booking?.roomRemoteIds?.firstNotNullOfOrNull { roomRemoteId -> roomById[roomRemoteId] }
            if (booking != null && room != null) {
                targets.add(FoodOrderTarget("${room.roomName} - ${booking.guestName}", booking, room))
            }
        }

        if (allowBookingTargets) checkedInBookings.forEach { booking ->
            booking.roomRemoteIds
                .mapNotNull { roomRemoteId -> roomById[roomRemoteId] }
                .sortedWith(
                    compareBy<RoomEntity> { it.categorySortOrder }
                        .thenBy { it.sortOrder }
                        .thenBy { it.roomName }
                )
                .forEach { room ->
                    val guestLabel = booking.guestName.takeIf { it.isNotBlank() } ?: "Checked-in guest"
                    targets.add(FoodOrderTarget("${room.roomName} - $guestLabel", booking, room))
                }
        }

        if (existing != null) {
            val alreadyIncluded = targets.any { target ->
                target.room?.remoteId == existing.roomRemoteId &&
                        target.booking?.remoteId == existing.bookingRemoteId
            }
            if (!alreadyIncluded) {
                val existingRoom = existing.roomRemoteId?.let { roomById[it] }
                val label = when {
                    existingRoom != null -> "${existingRoom.roomName} - Previous order"
                    !existing.roomName.isNullOrBlank() -> "${existing.roomName} - Previous order"
                    !existing.guestName.isNullOrBlank() -> existing.guestName
                    else -> "Restaurant Sale"
                }
                targets.add(FoodOrderTarget(label, null, existingRoom))
            }
        }

        return targets
    }

    private fun isBookingScopedMode(): Boolean = !pendingTargetBookingRemoteId.isNullOrBlank()

    private fun targetBooking(): BookingEntity? =
        pendingTargetBookingRemoteId?.let { bookingId -> checkedInBookings.firstOrNull { it.remoteId == bookingId } }

    private fun targetRoom(): RoomEntity? {
        val roomById = rooms.associateBy { it.remoteId }
        return pendingTargetRoomRemoteId?.let { roomById[it] }
            ?: targetBooking()?.roomRemoteIds?.firstNotNullOfOrNull { roomById[it] }
    }

    private fun updateBookingScopedHeader() {
        val roomName = targetRoom()?.roomName ?: "Room"
        findViewById<TextView>(R.id.tvFoodTitle).text = "Food\n$roomName"
    }

    private fun todayRange(): Pair<Long, Long> {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        return start to calendar.timeInMillis
    }

    private fun String.displayFoodStatus(): String {
        return when (this) {
            FoodOrderStatus.KOT -> "Preparing"
            FoodOrderStatus.FINALIZED -> "In Progress"
            FoodOrderStatus.BILLED -> "Billed"
            FoodOrderStatus.CANCELLED -> "Cancelled"
            else -> replace('_', ' ').lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase() }
        }
    }

    private fun showFoodOrderPreviewDialog(
        existing: FoodOrderEntity?,
        roomName: String,
        guestName: String,
        draftItems: List<FoodOrderItemEntity>,
        onSaveKot: (AlertDialog) -> Unit,
        onFinalize: (AlertDialog) -> Unit
    ) {
        val previewLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 24, 36, 16)
            background = rounded(Color.WHITE, Color.TRANSPARENT, 0f)
        }

        previewLayout.addView(TextView(this).apply {
            text = roomName
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(GREEN))
        })

        if (guestName.isNotBlank() && guestName != roomName) {
            previewLayout.addView(TextView(this).apply {
                text = guestName
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(0, 4, 0, 14)
            })
        } else {
            previewLayout.addView(TextView(this).apply {
                text = "Order Preview"
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(0, 4, 0, 14)
            })
        }

        previewLayout.addView(TextView(this).apply {
            text = "Items"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.DKGRAY)
            setPadding(0, 6, 0, 8)
        })

        draftItems.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 10, 0, 10)
            }

            row.addView(TextView(this).apply {
                text = item.itemName
                textSize = 15f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f))

            row.addView(TextView(this).apply {
                text = "x${money(item.quantity)}"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.DKGRAY)
            }, LinearLayout.LayoutParams(56, -2))

            row.addView(TextView(this).apply {
                text = "₹${money(item.quantity * item.unitPrice)}"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.END
            }, LinearLayout.LayoutParams(90, -2))

            previewLayout.addView(row)
        }

        val totalAmount = draftItems.sumOf { it.quantity * it.unitPrice }
        val totalQty = draftItems.sumOf { it.quantity }.toInt()

        previewLayout.addView(View(this).apply {
            setBackgroundColor(Color.rgb(230, 230, 230))
        }, LinearLayout.LayoutParams(-1, 1).apply {
            setMargins(0, 12, 0, 12)
        })

        val totalRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        totalRow.addView(TextView(this).apply {
            text = "$totalQty Item${if (totalQty == 1) "" else "s"}"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))

        totalRow.addView(TextView(this).apply {
            text = "Total ₹${money(totalAmount)}"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(GREEN))
        })

        previewLayout.addView(totalRow)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Preview Order" else "Preview Edited Order")
            .setView(ScrollView(this).apply { addView(previewLayout) })
            .setPositiveButton("Finalize", null)
            .setNeutralButton("Save KOT", null)
            .setNegativeButton("Back", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dialog.dismiss()
            onFinalize(dialog)
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            dialog.dismiss()
            onSaveKot(dialog)
        }
    }

    private fun saveFoodOrderFromNewUi(
        existing: FoodOrderEntity?,
        booking: BookingEntity?,
        room: RoomEntity?,
        guestName: String,
        draftItems: List<FoodOrderItemEntity>,
        status: String,
        orderDialog: AlertDialog,
        previewDialog: AlertDialog? = null
    ) {
        lifecycleScope.launch {
            val result = repository.saveFoodOrder(
                existing = existing,
                booking = booking,
                room = room,
                guestName = guestName,
                discountAmount = existing?.discountAmount ?: 0.0,
                notes = existing?.notes,
                status = status,
                items = draftItems
            )

            when (result) {
                is SaveResult.Success -> {
                    Toast.makeText(
                        this@FoodBillingActivity,
                        "Food order saved.",
                        Toast.LENGTH_SHORT
                    ).show()

                    if (!billsMode) showingArchivedBills = false
                    renderOrders()
                }

                is SaveResult.Error -> Toast.makeText(
                    this@FoodBillingActivity,
                    result.message,
                    Toast.LENGTH_LONG
                ).show()

                is SaveResult.Conflict -> Toast.makeText(
                    this@FoodBillingActivity,
                    result.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    private fun showPreviewOrdersDialog(selectedOrders: List<FoodOrderEntity>) {
        val text = buildOrderSummaryText(selectedOrders)
        AlertDialog.Builder(this)
            .setTitle("Order Summary")
            .setView(ScrollView(this).apply {
                addView(TextView(this@FoodBillingActivity).apply {
                    this.text = text
                    textSize = 15f
                    setPadding(32, 18, 32, 18)
                    background = rounded(Color.WHITE, Color.rgb(226, 235, 230), 14f)
                })
            })
            .setPositiveButton("Generate Bill") { _, _ -> showGenerateFoodBillDialog(selectedOrders) }
            .setNeutralButton("Share / Print") { _, _ -> shareOrderSummary(text) }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun buildOrderSummaryText(selectedOrders: List<FoodOrderEntity>): String = buildString {
        appendLine("FOOD ORDER SUMMARY")
        appendLine("Not a final bill or tax invoice")
        appendLine()
        appendLine("Selected Rooms: ${selectedOrders.map { it.roomName ?: "Non Staying Guest" }.distinct().joinToString(", ")}")
        appendLine()
        selectedOrders.groupBy { it.roomName ?: "Non Staying Guest" }.forEach { (roomName, roomOrders) ->
            appendLine(roomName)
            roomOrders.forEach { order ->
                appendLine("${order.orderNumber ?: "Order"} - Rs ${money(order.totalAmount)}")
                orderItems.filter { it.orderRemoteId == order.remoteId }.forEach { item ->
                    appendLine("  ${item.itemName} x ${money(item.quantity)} = Rs ${money(item.quantity * item.unitPrice)}")
                }
            }
            appendLine()
        }
        appendLine("Order Total: Rs ${money(selectedOrders.sumOf { it.totalAmount })}")
    }

    private fun shareOrderSummary(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Food Order Summary")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share Order Summary"))
    }

    private fun shareFoodMenu() {
        if (menuItems.isEmpty()) {
            Toast.makeText(this, "No menu items available to share.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Food Menu")
            putExtra(Intent.EXTRA_TEXT, buildFoodMenuShareText())
        }
        startActivity(Intent.createChooser(intent, "Share / Print Menu"))
    }

    private fun buildFoodMenuShareText(): String = buildString {
        val propertyName = selectedPropertyRemoteId
            ?.let { propertyId -> managedProperties.firstOrNull { it.remoteId == propertyId }?.propertyName }
            ?: hotelProfile?.hotelName
            ?: "Food Menu"

        appendLine(propertyName)
        appendLine("FOOD MENU")
        appendLine()

        menuItems
            .groupBy { it.categoryName ?: "Food" }
            .toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
            .forEach { (category, items) ->
                appendLine(category)
                items.sortedBy { it.itemName.lowercase() }.forEach { item ->
                    appendLine("${item.itemName} - Rs ${money(item.price)}")
                }
                appendLine()
            }
    }

    private fun showGenerateFoodBillDialog(selectedOrders: List<FoodOrderEntity>) {
        generateFoodBillDialog.show(selectedOrders)
    }

    private fun renderDraftItems(container: LinearLayout, items: MutableList<FoodOrderItemEntity>) {
        container.removeAllViews()
        if (items.isEmpty()) {
            container.addView(label("No items added yet."))
            return
        }
        items.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 8, 8, 8)
            }
            row.addView(TextView(this).apply {
                text = "${item.itemName} x ${money(item.quantity)}  Rs ${money(item.quantity * item.unitPrice)}"
                textSize = 15f
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply {
                text = "Remove"
                setOnClickListener {
                    items.removeAt(index)
                    renderDraftItems(container, items)
                }
            })
            container.addView(row)
        }
    }

    private fun importMenuItemsFromCsv(uri: Uri) {
        val importedCount = foodMenuCsvImportManager.importMenuItemsFromCsv(
            uri = uri,
            gstCategories = gstCategories,
            propertyRemoteId = selectedPropertyRemoteId
        )
        if (importedCount > 0) {
            openMenuManagerAfterImport = true
        }
    }

    private fun writeSampleMenuCsv(uri: Uri) {
        foodMenuCsvImportManager.writeSampleCsv(
            uri = uri,
            gstCategories = gstCategories
        )
    }
    private fun input(hintText: String, value: String?, numeric: Boolean = false, singleLine: Boolean = true): EditText {
        return EditText(this).apply {
            hint = hintText
            setText(value.orEmpty())
            setSingleLine(singleLine)
            inputType = if (numeric) {
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            }
        }
    }

    private fun spinner(labels: List<String>): Spinner {
        return Spinner(this).apply {
            adapter = ArrayAdapter(this@FoodBillingActivity, android.R.layout.simple_spinner_item, labels).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
    }

    private fun label(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 14f
            setPadding(0, 18, 0, 6)
            setTextColor(Color.DKGRAY)
        }
    }

    private fun rounded(fill: Int, stroke: Int, radius: Float): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            if (stroke != Color.TRANSPARENT) setStroke(1, stroke)
        }
    }

    private fun resolveBillPropertyHeader(
        bill: FoodBillEntity,
        items: List<FoodBillItemEntity>
    ): FoodBillHeaderResolver.BillHeaderInfo {
        return foodBillHeaderResolver.resolve(
            bill = bill,
            items = items,
            rooms = rooms,
            managedProperties = managedProperties,
            hotelProfile = hotelProfile
        )
    }
    private fun money(value: Double): String = moneyFormat.format(value)
}
