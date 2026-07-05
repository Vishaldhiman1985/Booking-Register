package com.example.bookingregister.ui.booking

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.bookingregister.R
import com.example.bookingregister.accounting.domain.BookingFinancialCalculator
import com.example.bookingregister.accounting.domain.BookingFinancialSummary
import com.example.bookingregister.data.repository.PaymentStatus
import com.example.bookingregister.data.repository.SaveResult
import com.example.bookingregister.data.entities.BookingAccountingChargeEntity
import com.example.bookingregister.data.entities.BookingAccountingChargeType
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingFinancialLineSource
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import com.example.bookingregister.data.entities.BookingSourceEntity
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.data.entities.FoodOrderEntity
import com.example.bookingregister.data.entities.FoodOrderItemEntity
import com.example.bookingregister.data.entities.ManagedPropertyEntity
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.room.domain.RoomLifecycleStatus
import com.example.bookingregister.data.withCalculatedPayment
import com.example.bookingregister.finalbill.domain.FinalBillPreview
import com.example.bookingregister.finalbill.domain.FinalBillPreviewBuilder
import com.example.bookingregister.finalbill.domain.FinalBillTextFormatter
import com.example.bookingregister.folio.domain.FolioSummaryBuilder
import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.booking.domain.BookingPricingStatus
import com.example.bookingregister.booking.domain.BookingSavePreparation
import com.example.bookingregister.booking.domain.BookingPaymentSourcePolicy
import com.example.bookingregister.source.domain.SourceSettlementCalculator
import com.example.bookingregister.tax.domain.HotelGstCalculator
import com.example.bookingregister.ui.food.FoodBillingActivity
import com.example.bookingregister.ui.service.ServiceBillingActivity
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.example.bookingregister.data.entities.RoomGstSlabEntity
class BookingDialog(
    private val context: Context,
    private val hotelRemoteId: String,
    private val rooms: List<RoomEntity>,
    private val bookings: List<BookingEntity>,
    private val bookingPayments: List<BookingPaymentEntity>,
    private val bookingFinancialLines: List<BookingFinancialLineEntity>,
    private val bookingAccountingCharges: List<BookingAccountingChargeEntity>,
    private var foodOrders: List<FoodOrderEntity>,
    private var foodOrderItems: List<FoodOrderItemEntity>,
    private val bookingSources: List<BookingSourceEntity>,
    private val managedProperties: List<ManagedPropertyEntity>,
    private val roomGstSlabs: List<RoomGstSlabEntity>,
    private val hotelHasGst: Boolean,
    private val selectedRoom: RoomEntity?,
    private val selectedCheckInMillis: Long,
    private val existingBooking: BookingEntity?,
    private val canEditBooking: Boolean = true,
    private val roomRateLocked: Boolean = false,
    private val onBookingSaved: (BookingEntity, List<BookingFinancialLineEntity>, (SaveResult) -> Unit) -> Unit,
    private val onBookingDeleted: (BookingEntity) -> Unit,
    private val onPaymentSaved: (BookingEntity, Double, String, String, String?, (SaveResult) -> Unit) -> Unit,
    private val onAccountingChargeSaved: (BookingEntity, String, Double, String, String?, String?, (SaveResult) -> Unit) -> Unit,
    private val onFinalBillGenerated: (BookingEntity, (SaveResult) -> Unit) -> Unit
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateDisplayMonthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val settlementCalculator = SourceSettlementCalculator()
    private val financialCalculator = BookingFinancialCalculator()
    private val gstCalculator = HotelGstCalculator()
    private val finalBillPreviewBuilder = FinalBillPreviewBuilder()

    private val dialogView: View = LayoutInflater.from(context).inflate(R.layout.dialog_booking, FrameLayout(context), false)
    private val dialogTitle: TextView = dialogView.findViewById(R.id.tvBookingDialogTitle)
    private val bookingIdText: TextView = dialogView.findViewById(R.id.tvBookingId)
    private val bookingMenuButton: TextView = dialogView.findViewById(R.id.btnBookingMenu)
    private val lifecycleActions: View = dialogView.findViewById(R.id.bookingLifecycleActions)
    private val checkInButton: Button = dialogView.findViewById(R.id.btnCheckInBooking)
    private val checkOutButton: Button = dialogView.findViewById(R.id.btnCheckOutBooking)
    private val guestName: EditText = dialogView.findViewById(R.id.etGuestName)
    private val guestMobile: EditText = dialogView.findViewById(R.id.etGuestMobile)
    private val sourceSpinner: Spinner = dialogView.findViewById(R.id.spBookingSource)
    private val callGuestButton: ImageButton = dialogView.findViewById(R.id.btnCallGuest)
    private val adultCount: EditText = dialogView.findViewById(R.id.etAdultCount)
    private val childCount: EditText = dialogView.findViewById(R.id.etChildCount)
    private val selectedRoomsText: TextView = dialogView.findViewById(R.id.tvSelectedRooms)
    private val selectRoomsButton: Button = dialogView.findViewById(R.id.btnSelectRooms)
    private val dateRange: View = dialogView.findViewById(R.id.dateRangeSelector)
    private val dateRangeText: EditText = dialogView.findViewById(R.id.etDateRange)
    private val checkInDateCard: View = dialogView.findViewById(R.id.checkInDateCard)
    private val checkOutDateCard: View = dialogView.findViewById(R.id.checkOutDateCard)
    private val checkInDisplay: TextView = dialogView.findViewById(R.id.tvCheckInDisplay)
    private val checkOutDisplay: TextView = dialogView.findViewById(R.id.tvCheckOutDisplay)
    private val nightCountDisplay: TextView = dialogView.findViewById(R.id.tvNightCount)
    private val checkIn: EditText = dialogView.findViewById(R.id.etCheckInDate)
    private val checkOut: EditText = dialogView.findViewById(R.id.etCheckOutDate)
    private val bookingTotalLayout: TextInputLayout = dialogView.findViewById(R.id.tilBookingTotal)
    private val bookingTotal: EditText = dialogView.findViewById(R.id.etBookingTotal)
    private val propertyTaxLayout: TextInputLayout = dialogView.findViewById(R.id.tilPropertyTax)
    private val propertyTax: EditText = dialogView.findViewById(R.id.etPropertyTax)
    private val settlementPreview: TextView = dialogView.findViewById(R.id.tvSourceSettlement)
    private val detailedOtaRatesButton: Button = dialogView.findViewById(R.id.btnDetailedOtaRates)
    private val priceSummaryHeader: View = dialogView.findViewById(R.id.priceSummaryHeader)
    private val priceSummaryContent: View = dialogView.findViewById(R.id.priceSummaryContent)
    private val priceSummaryToggle: TextView = dialogView.findViewById(R.id.tvPriceSummaryToggle)
    private val advancePaidLayout: TextInputLayout = dialogView.findViewById(R.id.tilAdvancePaid)
    private val advancePaid: EditText = dialogView.findViewById(R.id.etAdvancePaid)
    private val balance: EditText = dialogView.findViewById(R.id.etBalance)
    private val paymentStatusSpinner: Spinner = dialogView.findViewById(R.id.spPaymentStatus)
    private val paymentStatusGroup: RadioGroup = dialogView.findViewById(R.id.rgPaymentStatus)
    private val paymentHistoryHeader: View = dialogView.findViewById(R.id.paymentHistoryHeader)
    private val paymentHistoryContent: View = dialogView.findViewById(R.id.paymentHistoryContent)
    private val paymentHistoryToggle: TextView = dialogView.findViewById(R.id.tvPaymentHistoryToggle)
    private val paymentHistory: TextView = dialogView.findViewById(R.id.tvPaymentHistory)
    private val ledgerEntriesHeader: View = dialogView.findViewById(R.id.ledgerEntriesHeader)
    private val ledgerEntriesToggle: TextView = dialogView.findViewById(R.id.tvLedgerEntriesToggle)
    private val ledgerEntries: TextView = dialogView.findViewById(R.id.tvLedgerEntries)
    private val totalFolioBalance: TextView = dialogView.findViewById(R.id.tvTotalFolioBalance)
    private val foodBalanceView: TextView = dialogView.findViewById(R.id.tvFoodBalance)
    private val serviceBalanceView: TextView = dialogView.findViewById(R.id.tvServiceBalance)
    private val damageBalanceView: TextView = dialogView.findViewById(R.id.tvDamageBalance)
    private val addPaymentButton: Button = dialogView.findViewById(R.id.btnAddPayment)
    private val generateBillButton: Button = dialogView.findViewById(R.id.btnGenerateBookingBill)
    private val complimentaryButton: Button = dialogView.findViewById(R.id.btnComplimentary)
    private val rbFullyPaid: RadioButton = dialogView.findViewById(R.id.rbFullyPaid)
    private val rbPartiallyPaid: RadioButton = dialogView.findViewById(R.id.rbPartiallyPaid)
    private val rbNotPaid: RadioButton = dialogView.findViewById(R.id.rbNotPaid)
    private val notes: EditText = dialogView.findViewById(R.id.etNotes)
    private val shareButton: ImageButton = dialogView.findViewById(R.id.btnShareBooking)
    private val deleteButton: Button = dialogView.findViewById(R.id.btnDeleteBooking)
    private val cancelButton: TextView = dialogView.findViewById(R.id.btnDialogCancel)
    private val saveButton: Button = dialogView.findViewById(R.id.btnDialogSave)
    private val editButton: Button = dialogView.findViewById(R.id.btnEditBooking)
    private val foodButton: Button = dialogView.findViewById(R.id.btnBookingFood)
    private val serviceButton: Button = dialogView.findViewById(R.id.btnBookingService)
    private var bookingEditMode = existingBooking == null && canEditBooking
    private var forceComplimentary = existingBooking?.paymentStatus == PaymentStatus.COMPLIMENTARY ||
            existingBooking?.pricingStatus == BookingPricingStatus.COMPLIMENTARY
    private var priceSummaryExpanded = true
    private var paymentHistoryExpanded = false
    private var ledgerEntriesExpanded = false
    private var paymentStatusFocusReady = false

    private val activeSources = bookingSources
        .filter { !it.isDeleted && it.isActive }
        .ifEmpty { listOf(defaultSource()) }

    private var existingPaymentEntries: List<BookingPaymentEntity> = existingBooking?.let { booking ->
        bookingPayments.filter { !it.isDeleted && it.bookingRemoteId == booking.remoteId }
    }.orEmpty()

    private var existingFinancialLines: List<BookingFinancialLineEntity> = existingBooking?.let { booking ->
        bookingFinancialLines.filter { !it.isDeleted && it.bookingRemoteId == booking.remoteId }
    }.orEmpty()
    private var existingAccountingCharges: List<BookingAccountingChargeEntity> = existingBooking?.let { booking ->
        bookingAccountingCharges.filter { !it.isDeleted && it.bookingRemoteId == booking.remoteId }
    }.orEmpty()
    private var draftFinancialLines: List<BookingFinancialLineEntity> = existingFinancialLines

    private val selectedRoomIds = (
            existingBooking?.roomRemoteIds?.takeIf { it.isNotEmpty() }
                ?: selectedRoom?.let { listOf(it.remoteId) }
                ?: rooms.firstOrNull()?.let { listOf(it.remoteId) }
                ?: emptyList()
            ).toMutableList()

    private val dialog: AlertDialog = AlertDialog.Builder(context)
        .setView(dialogView)
        .create()

    init {
        dialogView.setBackgroundColor(Color.WHITE)
        forceLightDialogColors(dialogView)
        setupSourceSelection()
        bindFields()
        setupRoomSelection()
        setupDatePickers()
        setupPaymentStatus()
        setupPaymentHistory()
        setupCollapsibleSections()
        setupButtons()
        refreshSourceMode()
        applyBookingEditMode()
    }

    fun show() {
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.WHITE))
            decorView.setPadding(0, 0, 0, 0)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        cancelButton.setOnClickListener { dialog.dismiss() }
        saveButton.setOnClickListener {
            if (!bookingEditMode) {
                Toast.makeText(context, "Tap Edit before changing booking details.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val booking = prepareBookingForSave()
            if (booking == null) {
                Toast.makeText(context, "Please check booking details", Toast.LENGTH_SHORT).show()
            } else {
                setSaving(true)
                onBookingSaved(booking, draftFinancialLines) { result ->
                    setSaving(false)
                    when (result) {
                        is SaveResult.Success -> {
                            Toast.makeText(context, "Booking saved", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                        is SaveResult.Conflict -> {
                            Toast.makeText(context, "Updated version loaded. Please try again.", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                        is SaveResult.Error -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun prepareBookingForSave(): BookingEntity? {
        return BookingSavePreparation.prepare(
            buildBooking = ::buildBooking,
            refreshFinancialLines = { booking ->
                if (BookingPricingStatus.isPending(booking.pricingStatus)) {
                    draftFinancialLines = emptyList()
                } else {
                    ensureRoomNightFinancialLinesForBooking(booking)
                }
            }
        )
    }

    fun isShowing(): Boolean = dialog.isShowing

    fun refreshFoodOrders(
        updatedFoodOrders: List<FoodOrderEntity>,
        updatedFoodOrderItems: List<FoodOrderItemEntity>
    ) {
        foodOrders = updatedFoodOrders
        foodOrderItems = updatedFoodOrderItems
        refreshFolioViews()
    }

    fun refreshFolioData(
        updatedPayments: List<BookingPaymentEntity>,
        updatedFinancialLines: List<BookingFinancialLineEntity>,
        updatedAccountingCharges: List<BookingAccountingChargeEntity>,
        updatedFoodOrders: List<FoodOrderEntity>,
        updatedFoodOrderItems: List<FoodOrderItemEntity>
    ) {
        val booking = existingBooking ?: return
        existingPaymentEntries = updatedPayments.filter { !it.isDeleted && it.bookingRemoteId == booking.remoteId }
        val refreshedFinancialLines = updatedFinancialLines.filter { !it.isDeleted && it.bookingRemoteId == booking.remoteId }
        existingFinancialLines = refreshedFinancialLines
        if (!bookingEditMode) {
            draftFinancialLines = refreshedFinancialLines
        }
        existingAccountingCharges = updatedAccountingCharges.filter { !it.isDeleted && it.bookingRemoteId == booking.remoteId }
        foodOrders = updatedFoodOrders
        foodOrderItems = updatedFoodOrderItems
        refreshFolioViews()
    }

    private fun refreshFolioViews() {
        if (!dialog.isShowing) return
        refreshBalanceForCurrentStatus()
        refreshPaymentActionVisibility()
        renderPaymentHistory()
    }

    private fun setSaving(isSaving: Boolean) {
        saveButton.apply {
            isEnabled = !isSaving
            text = if (isSaving) "Saving..." else "Save"
        }
        cancelButton.isEnabled = !isSaving
        bookingMenuButton.isEnabled = !isSaving
        shareButton.isEnabled = !isSaving
        selectRoomsButton.isEnabled = !isSaving
        detailedOtaRatesButton.isEnabled = !isSaving
        deleteButton.isEnabled = !isSaving
        generateBillButton.isEnabled = !isSaving
        editButton.isEnabled = !isSaving && canEditBooking
        foodButton.isEnabled = !isSaving
        serviceButton.isEnabled = !isSaving
    }

    private fun setupSourceSelection() {
        sourceSpinner.adapter = spinnerAdapter(listOf("Booking source") + activeSources.map { it.sourceName })
        sourceSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshSourceMode()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun bindFields() {
        dialogTitle.visibility = View.VISIBLE
        dialogTitle.text = "Rooms"
        dialogTitle.setTextColor(Color.parseColor("#101018"))
        bookingIdText.visibility = View.GONE
        bookingMenuButton.visibility = if (existingBooking == null) View.GONE else View.VISIBLE
        bookingMenuButton.setTextColor(Color.parseColor("#111111"))
        guestName.setText(existingBooking?.guestName.orEmpty())
        guestMobile.setText(existingBooking?.guestMobile.orEmpty())
        val selectedIndex = activeSources.indexOfFirst { source ->
            source.remoteId == existingBooking?.sourceRemoteId ||
                    source.sourceName.equals(existingBooking?.sourceName, ignoreCase = true)
        }.let { index -> if (existingBooking == null || index < 0) 0 else index + 1 }
        sourceSpinner.setSelection(selectedIndex)
        adultCount.setText((existingBooking?.adultCount ?: 2).toString())
        childCount.setText((existingBooking?.childCount ?: 0).toString())
        checkIn.setText(dateFormat.format(Date(existingBooking?.checkInMillis ?: selectedCheckInMillis)))
        val defaultCheckout = existingBooking?.checkOutMillis ?: selectedCheckInMillis + DAY_MILLIS
        checkOut.setText(dateFormat.format(Date(defaultCheckout)))
        updateDateRangeText()
        bookingTotal.setText(
            if (existingBooking == null || BookingPricingStatus.isPending(existingBooking.pricingStatus)) ""
            else amountText(
                existingBooking.grossCharges.takeIf { it > 0.0 }
                    ?: existingBooking.receivable.takeIf { it > 0.0 }
                    ?: existingBooking.rate
            )
        )
        propertyTax.setText(amountText(existingBooking?.propertyTax ?: currentFinancialSummary().propertyTax))
        advancePaid.setText(amountText(existingPaymentEntries.takeIf { it.isNotEmpty() }?.let { stayPaymentTotal(it) } ?: existingBooking?.paid))
        balance.setText(amountText(existingBooking?.copy(paid = existingPaymentEntries.takeIf { it.isNotEmpty() }?.let { stayPaymentTotal(it) } ?: existingBooking.paid)?.withCalculatedPayment()?.balance))
        balance.isEnabled = false
        if (existingPaymentEntries.isNotEmpty()) advancePaid.isEnabled = false
        notes.setText(existingBooking?.notes.orEmpty())
        updateSelectedRoomsText()
        deleteButton.visibility = View.GONE
        when (existingBooking?.paymentStatus ?: detectPaymentStatus(bookingTotal.numberValue(), advancePaid.numberValue())) {
            PaymentStatus.COMPLIMENTARY -> rbNotPaid.isChecked = true
            PaymentStatus.FULLY_PAID -> rbFullyPaid.isChecked = true
            PaymentStatus.PARTIALLY_PAID -> rbPartiallyPaid.isChecked = true
            else -> rbNotPaid.isChecked = true
        }
        refreshComplimentaryState()
    }

    private fun setupRoomSelection() {
        selectedRoomsText.setOnClickListener { selectRoomsButton.performClick() }
        dialogTitle.setOnClickListener { selectRoomsButton.performClick() }
        selectRoomsButton.setOnClickListener {
            val availableRooms = getAvailableRoomsForSelectedDates()
            if (availableRooms.isEmpty()) {
                Toast.makeText(context, "No rooms available for selected dates", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val roomNames = availableRooms.map { it.roomName }.toTypedArray()
            val checkedItems = BooleanArray(availableRooms.size) { index -> selectedRoomIds.contains(availableRooms[index].remoteId) }
            AlertDialog.Builder(context)
                .setTitle("Select Available Rooms")
                .setMultiChoiceItems(roomNames, checkedItems) { _, which, isChecked ->
                    val selectedId = availableRooms[which].remoteId
                    if (isChecked) {
                        if (!selectedRoomIds.contains(selectedId)) selectedRoomIds.add(selectedId)
                    } else {
                        selectedRoomIds.remove(selectedId)
                    }
                }
                .setPositiveButton("OK") { _, _ ->
                    updateSelectedRoomsText()
                    refreshBalanceForCurrentStatus()
                    refreshSourceMode()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupDatePickers() {
        dateRange.setOnClickListener { pickCheckoutDate() }
        checkInDateCard.setOnClickListener { pickCheckInDate() }
        checkOutDateCard.setOnClickListener { pickCheckoutDate() }
        checkInDisplay.setOnClickListener { pickCheckInDate() }
        checkOutDisplay.setOnClickListener { pickCheckoutDate() }
    }

    private fun setupPaymentStatus() {
        val paymentLabels = listOf("Fully Paid", "Partial Paid", "Not Paid", "Complimentary")
        paymentStatusSpinner.adapter = spinnerAdapter(paymentLabels)
        paymentStatusSpinner.setSelection(
            statusSpinnerPosition(
                existingBooking?.paymentStatus ?: detectPaymentStatus(bookingTotal.numberValue(), advancePaid.numberValue())
            ),
            false
        )
        paymentStatusSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val status = selectedStatus()
                forceComplimentary = status == PaymentStatus.COMPLIMENTARY
                syncHiddenPaymentRadio(status)
                applyPaymentStatus(status)
                renderPaymentHistory()
                if (paymentStatusFocusReady) focusFinalPriceField()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        paymentStatusGroup.setOnCheckedChangeListener { _, _ -> applyPaymentStatus(selectedStatus()) }
        bookingTotal.addTextChangedListener(afterTextChanged {
            refreshBalanceForCurrentStatus()
            refreshSourceMode()
        })
        propertyTax.addTextChangedListener(afterTextChanged { refreshSourceMode() })
        advancePaid.addTextChangedListener(afterTextChanged {
            if (selectedStatus() == PaymentStatus.PARTIALLY_PAID) {
                val totalValue = collectableTotal().coerceAtLeast(0.0)
                val paidValue = advancePaid.numberValue().coerceAtLeast(0.0)
                balance.setText(amountText((totalValue - paidValue).coerceAtLeast(0.0)))
            }
        })
        applyPaymentStatus(selectedStatus())
        paymentStatusSpinner.post { paymentStatusFocusReady = true }
    }

    private fun setupPaymentHistory() {
        refreshPaymentActionVisibility()
        renderPaymentHistory()
        addPaymentButton.setOnClickListener {
            val booking = existingBooking
            if (booking == null) {
                Toast.makeText(context, "Save booking before adding payments", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showAddPaymentDialog(booking)
        }
        generateBillButton.visibility = if (existingBooking == null) View.GONE else View.VISIBLE
        generateBillButton.setOnClickListener { showGenerateBillGuard() }
        complimentaryButton.setOnClickListener { markComplimentary() }
        ledgerEntriesHeader.setOnClickListener { setLedgerEntriesExpanded(!ledgerEntriesExpanded) }
    }

    private fun showGenerateBillGuard() {
        val booking = existingBooking
        if (booking == null) {
            Toast.makeText(context, "Save booking before generating bill", Toast.LENGTH_SHORT).show()
            return
        }
        if (BookingPricingStatus.isPending(booking.pricingStatus)) {
            Toast.makeText(context, "Enter and save the room rate before generating the final bill", Toast.LENGTH_LONG).show()
            return
        }

        val preview = currentFinalBillPreview(booking)

        showFinalBillPreviewDialog(preview)
    }

    private fun currentFinalBillPreview(booking: BookingEntity): FinalBillPreview {
        return finalBillPreviewBuilder.build(
            booking = booking,
            rooms = rooms,
            bookingPayments = existingPaymentEntries,
            accountingCharges = existingAccountingCharges,
            foodOrders = foodOrders,
            foodOrderItems = foodOrderItems,
            bookingFinancialLines = draftFinancialLines
        )
    }

    private fun showFinalBillPreviewDialog(preview: FinalBillPreview) {
        val guestCollectableBalance = preview.guestCheckoutBalance
        val previewText = TextView(context).apply {
            text = FinalBillTextFormatter.formatFinalSettlement(preview)
            textSize = 15f
            setTextColor(Color.parseColor("#111827"))
            setPadding(32, 20, 32, 20)
            setLineSpacing(2f, 1.05f)
        }

        val scrollView = ScrollView(context).apply {
            addView(previewText)
        }

        AlertDialog.Builder(context)
            .setTitle("Generate Bill")
            .setView(scrollView)
            .setPositiveButton(
                if (guestCollectableBalance > 0.01)
                    "Collect Rs ${FinalBillTextFormatter.amountText(guestCollectableBalance)}"
                else
                    "Generate Bill"
            ) { _, _ ->
                if (guestCollectableBalance > 0.01) {
                    existingBooking?.let {
                        showPaymentEntryDialog(it, BookingPaymentType.PAYMENT, guestCollectableBalance, generateFinalBillAfterSave = true)
                    }
                } else {
                    confirmFinalBillGeneration()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun confirmFinalBillGeneration() {
        val booking = existingBooking ?: return
        AlertDialog.Builder(context)
            .setTitle("Confirm final bill")
            .setMessage("Generate final bill for ${booking.guestName}? Please confirm after checking all room, food, service, damage and payment entries.")
            .setPositiveButton("Generate") { _, _ ->
                onFinalBillGenerated(booking) { result ->
                    when (result) {
                        is SaveResult.Success -> {
                            Toast.makeText(context, "Bill generated.", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            openGeneratedBill(booking)
                        }
                        is SaveResult.Conflict -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        is SaveResult.Error -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openGeneratedBill(booking: BookingEntity) {
        context.startActivity(Intent(context, FoodBillingActivity::class.java).apply {
            putExtra(FoodBillingActivity.EXTRA_HOTEL_REMOTE_ID, hotelRemoteId)
            putExtra(FoodBillingActivity.EXTRA_OPEN_MODE, FoodBillingActivity.MODE_BILLS_ARCHIVE)
            putExtra(FoodBillingActivity.EXTRA_OPEN_FINAL_BILL_BOOKING_REMOTE_ID, booking.remoteId)
            booking.propertyRemoteId?.takeIf { it.isNotBlank() }?.let { propertyRemoteId ->
                putExtra(FoodBillingActivity.EXTRA_PROPERTY_REMOTE_ID, propertyRemoteId)
            }
        })
    }

    private fun setupCollapsibleSections() {
        setPriceSummaryExpanded(priceSummaryExpanded)
        setPaymentHistoryExpanded(paymentHistoryExpanded)
        priceSummaryHeader.setOnClickListener {
            setPriceSummaryExpanded(!priceSummaryExpanded)
        }
        paymentHistoryHeader.setOnClickListener {
            setPaymentHistoryExpanded(!paymentHistoryExpanded)
        }
    }

    private fun setPriceSummaryExpanded(expanded: Boolean) {
        priceSummaryExpanded = true
        priceSummaryContent.visibility = View.VISIBLE
        priceSummaryToggle.text = ""
        priceSummaryToggle.visibility = View.GONE
    }

    private fun setPaymentHistoryExpanded(expanded: Boolean) {
        paymentHistoryExpanded = expanded
        paymentHistoryContent.visibility = if (expanded) View.VISIBLE else View.GONE
        paymentHistoryToggle.text = ""
        paymentHistoryToggle.visibility = View.GONE
    }


    private fun setLedgerEntriesExpanded(expanded: Boolean) {
        ledgerEntriesExpanded = expanded
        ledgerEntries.visibility = if (expanded) View.VISIBLE else View.GONE
        ledgerEntriesToggle.text = ""
        ledgerEntriesToggle.visibility = View.GONE
    }

    private fun focusFinalPriceField() {
        bookingTotal.post {
            bookingTotal.requestFocus()
            bookingTotal.setSelection(bookingTotal.text?.length ?: 0)
        }
    }
    private fun renderPaymentHistory() {
        if (selectedStatus() == PaymentStatus.COMPLIMENTARY) {
            paymentHistory.text = "Complimentary stay\nNo amount receivable. Inventory remains blocked."
            renderFinancialDashboard(null)
            ledgerEntriesHeader.visibility = View.GONE
            ledgerEntries.visibility = View.GONE
            return
        }
        val booking = existingBooking
        if (booking == null) {
            paymentHistory.text = "Save booking to view payment summary"
            renderFinancialDashboard(null)
            ledgerEntriesHeader.visibility = View.GONE
            ledgerEntries.visibility = View.GONE
            return
        }

        val preview = finalBillPreviewBuilder.build(
            booking = booking,
            rooms = rooms,
            bookingPayments = existingPaymentEntries,
            accountingCharges = existingAccountingCharges,
            foodOrders = foodOrders,
            foodOrderItems = foodOrderItems,
            bookingFinancialLines = draftFinancialLines
        )
        renderFinancialDashboard(preview)
        paymentHistory.text = FinalBillTextFormatter.formatBalanceSummary(preview)
        ledgerEntries.text = FinalBillTextFormatter.formatLedgerEntries(preview)
        ledgerEntriesHeader.visibility = if (preview.folioLines.isEmpty()) View.GONE else View.VISIBLE
        setLedgerEntriesExpanded(ledgerEntriesExpanded && preview.folioLines.isNotEmpty())
    }

    private fun renderFinancialDashboard(preview: FinalBillPreview?) {
        if (preview == null) {
            totalFolioBalance.text = "Total Balance: ${amountText(balance.numberValue())}"
            foodBalanceView.text = "Food Balance\n0"
            serviceBalanceView.text = "Service Balance\n0"
            damageBalanceView.text = "Damage\n0"
            return
        }
        totalFolioBalance.text = "Total Balance: ${amountText(preview.guestCheckoutBalance)}"
        foodBalanceView.text = "Food Balance\n${amountText(preview.foodBalance)}"
        serviceBalanceView.text = "Service Balance\n${amountText(preview.serviceBalance)}"
        damageBalanceView.text = "Damage\n${amountText(preview.damageBalance)}"
    }


    private fun showAddPaymentDialog(booking: BookingEntity) {
        if (selectedStatus() == PaymentStatus.COMPLIMENTARY) {
            Toast.makeText(context, "Change payment status before adding payments", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = arrayOf("Add Payment", "Add Refund", "Add Damage", "Add Discount", "Add Correction")
        AlertDialog.Builder(context)
            .setTitle("Payments / Adjustments")
            .setItems(labels) { _, which ->
                when (which) {
                    1 -> showPaymentEntryDialog(booking, BookingPaymentType.REFUND)
                    2 -> showAccountingAdjustmentDialog(booking, BookingAccountingChargeType.DAMAGE_CHARGE)
                    3 -> showAccountingAdjustmentDialog(booking, BookingAccountingChargeType.DISCOUNT)
                    4 -> showPaymentEntryDialog(booking, BookingPaymentType.ADJUSTMENT)
                    else -> showPaymentEntryDialog(booking, BookingPaymentType.PAYMENT)
                }
            }
            .show()
    }

    private fun showAccountingAdjustmentDialog(
        booking: BookingEntity,
        chargeType: String
    ) {
        val isDiscount = chargeType == BookingAccountingChargeType.DISCOUNT
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
        }
        val bucketOptions = if (isDiscount) {
            if (booking.sourceType == BookingSourceType.OTA) {
                listOf(BookingPaymentCategory.FOOD, BookingPaymentCategory.SERVICE)
            } else {
                listOf(BookingPaymentCategory.STAY, BookingPaymentCategory.FOOD, BookingPaymentCategory.SERVICE)
            }
        } else {
            listOf(BookingPaymentCategory.DAMAGE)
        }
        val bucketSpinner = Spinner(context).apply {
            adapter = spinnerAdapter(bucketOptions.map { it.displayBucketName() })
            visibility = if (isDiscount) View.VISIBLE else View.GONE
        }
        val descriptionInput = EditText(context).apply {
            hint = if (isDiscount) "Reason, e.g. Manager approved" else "Damage, e.g. Broken glass"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val amountInput = EditText(context).apply {
            hint = "Amount"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val noteInput = EditText(context).apply {
            hint = "Note optional"
            minLines = 2
            setSingleLine(false)
        }
        if (isDiscount) {
            container.addView(TextView(context).apply {
                text = "Discount On"
                textSize = 12f
                setTextColor(Color.parseColor("#6B7280"))
                setPadding(0, 12, 0, 0)
            })
            container.addView(bucketSpinner)
            if (booking.sourceType == BookingSourceType.OTA) {
                container.addView(TextView(context).apply {
                    text = "Room discount is disabled for OTA bookings."
                    textSize = 12f
                    setTextColor(Color.parseColor("#8A5A00"))
                    setPadding(0, 8, 0, 8)
                })
            }
        }
        container.addView(descriptionInput)
        container.addView(amountInput)
        container.addView(noteInput)

        AlertDialog.Builder(context)
            .setTitle(if (isDiscount) "Add Discount" else "Add Damage Recovery")
            .setView(container)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
            .also { adjustmentDialog ->
                adjustmentDialog.setOnShowListener {
                    val saveAdjustmentButton = adjustmentDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    saveAdjustmentButton.setOnClickListener {
                        val amount = amountInput.numberValue()
                        val rawDescription = descriptionInput.text.toString().trim()
                        val note = noteInput.text.toString().trim().ifBlank { null }
                        if (rawDescription.isBlank()) {
                            Toast.makeText(context, "Enter a reason", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        if (amount <= 0.0) {
                            Toast.makeText(context, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        val bucket = bucketOptions[bucketSpinner.selectedItemPosition.coerceIn(bucketOptions.indices)]
                        val description = if (isDiscount) {
                            "${bucket.displayBucketName()} Discount - $rawDescription"
                        } else {
                            "Damage Recovery - $rawDescription"
                        }

                        saveAdjustmentButton.isEnabled = false
                        saveAdjustmentButton.text = "Saving..."
                        descriptionInput.isEnabled = false
                        amountInput.isEnabled = false
                        noteInput.isEnabled = false
                        bucketSpinner.isEnabled = false
                        onAccountingChargeSaved(
                            booking,
                            chargeType,
                            amount,
                            description,
                            note,
                            bucket
                        ) { result ->
                            saveAdjustmentButton.isEnabled = true
                            saveAdjustmentButton.text = "Save"
                            descriptionInput.isEnabled = true
                            amountInput.isEnabled = true
                            noteInput.isEnabled = true
                            bucketSpinner.isEnabled = true
                            when (result) {
                                is SaveResult.Success -> {
                                    Toast.makeText(
                                        context,
                                        if (isDiscount) "Discount added to folio" else "Damage recovery added to folio",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    adjustmentDialog.dismiss()
                                    dialog.dismiss()
                                }
                                is SaveResult.Conflict -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                is SaveResult.Error -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                adjustmentDialog.show()
            }
    }

    private fun showPaymentEntryDialog(
        booking: BookingEntity,
        paymentType: String,
        defaultAmountOverride: Double? = null,
        generateFinalBillAfterSave: Boolean = false
    ) {
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
        }
        val amountInput = EditText(context).apply {
            hint = when (paymentType) {
                BookingPaymentType.REFUND -> "Refund amount"
                BookingPaymentType.ADJUSTMENT -> "Correction amount"
                else -> "Payment amount"
            }
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setText(defaultAmountOverride?.takeIf { it > 0.0 }?.let { amountText(it) } ?: defaultPaymentAmount(booking, paymentType))
            setSelection(text.length)
        }
        val paymentCategoryOptions = paymentCategoryOptions(booking)
        val categorySpinner = Spinner(context).apply {
            adapter = spinnerAdapter(paymentCategoryOptions)
            visibility = if (paymentType == BookingPaymentType.PAYMENT) View.VISIBLE else View.GONE
            if (paymentType == BookingPaymentType.PAYMENT) {
                setSelection(
                    paymentCategoryOptions.indexOf(defaultPaymentCategory(booking, paymentCategoryOptions))
                        .takeIf { it >= 0 }
                        ?: 0
                )
            }
        }
        val noteInput = EditText(context).apply {
            hint = if (paymentType == BookingPaymentType.PAYMENT) "Note optional" else "Reason required"
            setSingleLine(false)
            minLines = 2
        }
        container.addView(amountInput)
        if (paymentType == BookingPaymentType.PAYMENT) {
            container.addView(TextView(context).apply {
                text = "Payment For"
                textSize = 12f
                setTextColor(Color.parseColor("#6B7280"))
                setPadding(0, 12, 0, 0)
            })
            container.addView(categorySpinner)
        }
        container.addView(noteInput)
        AlertDialog.Builder(context)
            .setTitle(paymentType.displayPaymentType())
            .setView(container)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
            .also { paymentDialog ->
                paymentDialog.setOnShowListener {
                    val savePaymentButton = paymentDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    savePaymentButton.setOnClickListener {
                        val amount = amountInput.numberValue()
                        if (amount <= 0.0) {
                            Toast.makeText(context, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        val note = noteInput.text.toString().trim()
                        if (paymentType != BookingPaymentType.PAYMENT && note.isBlank()) {
                            Toast.makeText(context, "Please enter a reason", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        savePaymentButton.isEnabled = false
                        savePaymentButton.text = "Saving..."
                        amountInput.isEnabled = false
                        noteInput.isEnabled = false
                        val category = if (paymentType == BookingPaymentType.PAYMENT) categorySpinner.selectedItem as String else BookingPaymentCategory.STAY
                        onPaymentSaved(booking, amount, paymentType, category, note) { result ->
                            savePaymentButton.isEnabled = true
                            savePaymentButton.text = "Save"
                            amountInput.isEnabled = true
                            noteInput.isEnabled = true
                            when (result) {
                                is SaveResult.Success -> {
                                    if (generateFinalBillAfterSave && paymentType == BookingPaymentType.PAYMENT) {
                                        savePaymentButton.text = "Generating..."
                                        onFinalBillGenerated(booking) { billResult ->
                                            when (billResult) {
                                                is SaveResult.Success -> {
                                                    Toast.makeText(context, "Payment saved. Bill generated.", Toast.LENGTH_SHORT).show()
                                                    paymentDialog.dismiss()
                                                    dialog.dismiss()
                                                    openGeneratedBill(booking)
                                                }
                                                is SaveResult.Conflict -> Toast.makeText(context, billResult.message, Toast.LENGTH_LONG).show()
                                                is SaveResult.Error -> Toast.makeText(context, billResult.message, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } else {
                                        val message = if (result.syncPending) {
                                            "${paymentType.displayPaymentType()} saved. Syncing..."
                                        } else {
                                            "${paymentType.displayPaymentType()} saved"
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        paymentDialog.dismiss()
                                        dialog.dismiss()
                                    }
                                }
                                is SaveResult.Conflict -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                is SaveResult.Error -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                paymentDialog.show()
            }
    }

    private fun setupButtons() {
        callGuestButton.setOnClickListener {
            val mobile = guestMobile.text.toString().trim()
            if (mobile.isBlank()) {
                Toast.makeText(context, "Add guest mobile number first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            context.startActivity(Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$mobile") })
        }
        shareButton.setOnClickListener { shareBookingOnWhatsApp() }
        detailedOtaRatesButton.setOnClickListener { showDetailedOtaRatesDialog() }
        bookingMenuButton.setOnClickListener { showBookingMenu() }
        deleteButton.setOnClickListener { confirmCancelBooking() }
        editButton.setOnClickListener {
            if (!canEditBooking) {
                Toast.makeText(context, "You do not have permission to edit booking details.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            bookingEditMode = true
            applyBookingEditMode()
            Toast.makeText(context, "Edit mode enabled. You can update booking details now.", Toast.LENGTH_SHORT).show()
        }
        foodButton.setOnClickListener { openFoodOrders() }
        serviceButton.setOnClickListener {
            val booking = existingBooking
            if (booking == null) {
                Toast.makeText(context, "Save booking before adding service", Toast.LENGTH_SHORT).show()
            } else {
                openServices(booking)
            }
        }
    }

    private fun openFoodOrders() {
        val booking = existingBooking
        if (booking == null) {
            Toast.makeText(context, "Save booking before taking food order", Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(Intent(context, FoodBillingActivity::class.java).apply {
            putExtra(FoodBillingActivity.EXTRA_HOTEL_REMOTE_ID, hotelRemoteId)
            putExtra(FoodBillingActivity.EXTRA_OPEN_MODE, FoodBillingActivity.MODE_TAKE_ORDER)
            putExtra(FoodBillingActivity.EXTRA_TARGET_BOOKING_REMOTE_ID, booking.remoteId)
            selectedRoomIds.firstOrNull()?.let { roomRemoteId ->
                putExtra(FoodBillingActivity.EXTRA_TARGET_ROOM_REMOTE_ID, roomRemoteId)
            }
            selectedBookingPropertyRemoteId()?.let { propertyRemoteId ->
                putExtra(FoodBillingActivity.EXTRA_PROPERTY_REMOTE_ID, propertyRemoteId)
            }
        })
    }

    private fun openServices(booking: BookingEntity) {
        context.startActivity(Intent(context, ServiceBillingActivity::class.java).apply {
            putExtra(ServiceBillingActivity.EXTRA_HOTEL_REMOTE_ID, hotelRemoteId)
            putExtra(ServiceBillingActivity.EXTRA_TARGET_BOOKING_REMOTE_ID, booking.remoteId)
            selectedRoomIds.firstOrNull()?.let { roomRemoteId ->
                putExtra(ServiceBillingActivity.EXTRA_TARGET_ROOM_REMOTE_ID, roomRemoteId)
            }
        })
    }

    private fun applyBookingEditMode() {
        val editable = bookingEditMode && canEditBooking
        val initialAdvanceEditable = editable &&
                BookingPaymentSourcePolicy.canEditInitialAdvance(existingBooking)
        val fields = listOf(
            guestName,
            guestMobile,
            adultCount,
            childCount,
            bookingTotal,
            propertyTax,
            advancePaid,
            notes
        )
        fields.forEach { field ->
            field.isEnabled = editable
            field.alpha = if (editable) 1f else 0.82f
        }
        bookingTotal.isEnabled = editable && !roomRateLocked
        propertyTax.isEnabled = editable && !roomRateLocked
        advancePaid.isEnabled = initialAdvanceEditable &&
                selectedStatus() == PaymentStatus.PARTIALLY_PAID
        bookingTotal.alpha = if (editable && !roomRateLocked) 1f else 0.82f
        propertyTax.alpha = if (editable && !roomRateLocked) 1f else 0.82f
        detailedOtaRatesButton.isEnabled = editable && !roomRateLocked
        detailedOtaRatesButton.alpha = if (editable && !roomRateLocked) 1f else 0.45f
        bookingTotalLayout.helperText = if (roomRateLocked) {
            "Room rate locked after final bill"
        } else if (existingBooking?.let { BookingPricingStatus.isPending(it.pricingStatus) } == true) {
            "Rate pending — booking remains confirmed and rooms stay reserved"
        } else {
            null
        }
        advancePaidLayout.helperText = if (existingBooking != null) {
            "Calculated from payment history. Use Add Payment."
        } else {
            null
        }
        sourceSpinner.isEnabled = editable
        paymentStatusSpinner.isEnabled = initialAdvanceEditable
        checkInDateCard.isEnabled = editable
        checkOutDateCard.isEnabled = editable
        dateRange.isEnabled = editable
        selectRoomsButton.isEnabled = editable
        selectedRoomsText.isEnabled = editable
        dialogTitle.isEnabled = editable
        saveButton.isEnabled = editable
        saveButton.alpha = if (editable) 1f else 0.45f
        editButton.text = if (editable) "Editing" else "Edit"
        editButton.isEnabled = canEditBooking && !editable
        editButton.alpha = if (canEditBooking) 1f else 0.45f
    }
    private fun showBookingMenu() {
        PopupMenu(context, bookingMenuButton).apply {
            val booking = existingBooking
            val canUseBookingActions = booking != null
            menu.add("Pricing Summary").isEnabled = canUseBookingActions
            menu.add("Payment History").isEnabled = canUseBookingActions
            menu.add("Ledger Entries").isEnabled = canUseBookingActions
            menu.add("Cancel Booking").isEnabled = canUseBookingActions
            menu.add("Share")
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Pricing Summary" -> {
                        booking?.let { showFolioDetailDialog("Pricing Summary", settlementText(selectedSource())) }
                        true
                    }
                    "Payment History" -> {
                        booking?.let { showFolioDetailDialog("Payment History", FinalBillTextFormatter.formatBalanceSummary(currentFinalBillPreview(it))) }
                        true
                    }
                    "Ledger Entries" -> {
                        booking?.let { showFolioDetailDialog("Ledger Entries", FinalBillTextFormatter.formatLedgerEntries(currentFinalBillPreview(it))) }
                        true
                    }
                    "Cancel Booking" -> {
                        confirmCancelBooking()
                        true
                    }
                    "Share" -> {
                        shareBookingOnWhatsApp()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showFolioDetailDialog(title: String, body: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(body.ifBlank { "No entries yet." })
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showServiceChargeDialog(booking: BookingEntity) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
        }
        val serviceInput = EditText(context).apply {
            hint = "Service name, e.g. Bonfire"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val amountInput = EditText(context).apply {
            hint = "Amount"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val noteInput = EditText(context).apply {
            hint = "Note optional"
            setSingleLine(false)
            minLines = 2
        }
        container.addView(serviceInput)
        container.addView(amountInput)
        container.addView(noteInput)

        AlertDialog.Builder(context)
            .setTitle("Add Service To Folio")
            .setView(container)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()
            .also { serviceDialog ->
                serviceDialog.setOnShowListener {
                    val addButton = serviceDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    addButton.setOnClickListener {
                        val serviceName = serviceInput.text.toString().trim()
                        val amount = amountInput.numberValue()
                        val note = noteInput.text.toString().trim().ifBlank { null }
                        if (serviceName.isBlank()) {
                            Toast.makeText(context, "Enter service name", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        if (amount <= 0.0) {
                            Toast.makeText(context, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        addButton.isEnabled = false
                        addButton.text = "Adding..."
                        serviceInput.isEnabled = false
                        amountInput.isEnabled = false
                        noteInput.isEnabled = false
                        onAccountingChargeSaved(
                            booking,
                            BookingAccountingChargeType.SERVICE_CHARGE,
                            amount,
                            serviceName,
                            note,
                            BookingPaymentCategory.SERVICE
                        ) { result ->
                            addButton.isEnabled = true
                            addButton.text = "Add"
                            serviceInput.isEnabled = true
                            amountInput.isEnabled = true
                            noteInput.isEnabled = true
                            when (result) {
                                is SaveResult.Success -> {
                                    Toast.makeText(context, "Service added to folio", Toast.LENGTH_SHORT).show()
                                    serviceDialog.dismiss()
                                    dialog.dismiss()
                                }
                                is SaveResult.Conflict -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                is SaveResult.Error -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                serviceDialog.show()
            }
    }

    private fun confirmCancelBooking() {
        val booking = existingBooking ?: return
        AlertDialog.Builder(context)
            .setTitle("Cancel Booking")
            .setMessage("Cancel this booking and release these rooms on all devices?")
            .setPositiveButton("Cancel Booking") { _, _ ->
                onBookingDeleted(booking)
                Toast.makeText(context, "Booking cancelled", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDetailedOtaRatesDialog() {
        val selectedRooms = rooms.filter { selectedRoomIds.contains(it.remoteId) }
        val stayDates = selectedStayDates()
        if (selectedRooms.isEmpty() || stayDates.isEmpty()) {
            Toast.makeText(context, "Select rooms and dates first", Toast.LENGTH_SHORT).show()
            return
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 8, 28, 0)
        }
        val inputRows = mutableListOf<Pair<Pair<String, Long>, EditText>>()
        selectedRooms.forEach { room ->
            stayDates.forEach { dateMillis ->
                val existingLine = draftFinancialLines.firstOrNull {
                    it.roomRemoteId == room.remoteId && it.businessDateMillis == dateMillis
                }
                val amountInput = EditText(context).apply {
                    hint = "${room.roomName} - ${friendlyDate(dateMillis)} gross"
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setSingleLine(true)
                    setText(existingLine?.grossAmount?.takeIf { it > 0.0 }?.let { amountText(it) }.orEmpty())
                    setTextColor(Color.parseColor("#24212A"))
                    setHintTextColor(Color.parseColor("#8A858D"))
                }
                container.addView(amountInput)
                inputRows.add((room.remoteId to dateMillis) to amountInput)
            }
        }

        val scrollView = ScrollView(context).apply { addView(container) }
        AlertDialog.Builder(context)
            .setTitle("Detailed OTA rates")
            .setMessage("Enter gross room amount for each room-night.")
            .setView(scrollView)
            .setPositiveButton("Apply", null)
            .setNegativeButton("Cancel", null)
            .create()
            .also { ratesDialog ->
                ratesDialog.setOnShowListener {
                    ratesDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val parsedRows = inputRows.map { (key, input) ->
                            val amount = input.text.toString().trim().toDoubleOrNull()
                            if (amount == null || amount < 0.0) {
                                Toast.makeText(context, "Enter valid gross amounts", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }
                            key to amount
                        }
                        val now = System.currentTimeMillis()
                        draftFinancialLines = parsedRows.map { (key, amount) ->
                            val (roomRemoteId, dateMillis) = key
                            val existingLine = draftFinancialLines.firstOrNull {
                                it.roomRemoteId == roomRemoteId && it.businessDateMillis == dateMillis
                            }
                            val propertyRemoteId = rooms.firstOrNull { it.remoteId == roomRemoteId }
                                ?.propertyRemoteId
                                ?.takeIf { it.isNotBlank() }
                            financialCalculator.lineFromGross(
                                remoteId = existingLine?.remoteId ?: UUID.randomUUID().toString(),
                                hotelRemoteId = hotelRemoteId,
                                bookingRemoteId = existingBooking?.remoteId.orEmpty(),
                                roomRemoteId = roomRemoteId,
                                businessDateMillis = dateMillis,
                                grossAmount = amount,
                                gstEnabled = hotelHasGst,
                                source = BookingFinancialLineSource.MANUAL,
                                roomGstSlabs = roomGstSlabs

                            ).copy(
                                localId = existingLine?.localId ?: 0,
                                propertyRemoteId = propertyRemoteId ?: existingLine?.propertyRemoteId,
                                updatedAt = now,
                                syncState = existingLine?.syncState ?: "PENDING",
                                lastSyncedAt = existingLine?.lastSyncedAt,
                                revision = existingLine?.revision ?: 0,
                                baseRevision = existingLine?.baseRevision?.takeIf { it > 0 } ?: existingLine?.revision ?: 0
                            )
                        }
                        val summary = currentFinancialSummary()
                        bookingTotal.setTextIfChanged(amountText(summary.grossCharges))
                        propertyTax.setTextIfChanged(amountText(summary.propertyTax))
                        refreshBalanceForCurrentStatus()
                        refreshSourceMode()
                        ratesDialog.dismiss()
                    }
                }
                ratesDialog.show()
            }
    }

    private fun refreshSourceMode() {
        val source = selectedSource()
        val isOta = source.sourceType == BookingSourceType.OTA
        val hasDeductions = source.hasDeductions()
        bookingTotalLayout.hint = if (hotelHasGst) "Final incl. GST" else "Final Price"
        propertyTaxLayout.visibility = View.GONE
        paymentStatusGroup.visibility = View.GONE
        paymentStatusSpinner.visibility = if (isOta) View.GONE else View.VISIBLE
        advancePaidLayout.visibility = if (isOta) View.GONE else View.VISIBLE
        detailedOtaRatesButton.visibility = if (isOta) View.VISIBLE else View.GONE
        refreshPaymentActionVisibility()
        if (isOta) {
            paymentStatusSpinner.setSelection(statusSpinnerPosition(PaymentStatus.FULLY_PAID), false)
            rbFullyPaid.isChecked = true
            advancePaid.setText("0")
        }
        val showPreview = isOta || hasDeductions || hotelHasGst
        settlementPreview.visibility = if (showPreview) View.VISIBLE else View.GONE
        if (showPreview) settlementPreview.text = settlementText(source)
    }

    private fun settlementText(source: BookingSourceEntity): String {
        val gst = calculatedGst()
        val financialSummary = currentFinancialSummary()
        val settlement = currentSettlement(source)
        val taxLine = if (!hotelHasGst) {
            "GST: Not applicable\n"
        } else if (financialSummary.usesDetailedLines) {
            "GST: ${amountText(financialSummary.propertyTax)} from detailed room-night rates\n"
        } else if (gst.gstEnabled) {
            "GST (${amountText(gst.ratePercent)}% on ${amountText(gst.roomChargePerRoomNight)}/room-night): ${amountText(settlement.propertyTax)}\n"
        } else {
            "GST: Not applicable\n"
        }
        return buildString {
            appendLine("Expected Settlement")
            appendLine("Room Revenue: ${amountText(settlement.roomRevenue)}")
            append(taxLine)
            appendLine("Gross Charges: ${amountText(settlement.grossCharges)}")
            appendLine("Commission: ${amountText(settlement.commission)}")
            appendLine("GST on Commission: ${amountText(settlement.commissionTax)}")
            appendLine("Fee: ${amountText(settlement.fixedFee)}")
            appendLine("TDS: ${amountText(settlement.tds)}")
            appendLine("TCS: ${amountText(settlement.tcs)}")
            append("Expected Net Payout: ${amountText(settlement.expectedPayout)}")
        }
    }

    private fun buildBooking(
        forcedRemoteId: String? = null,
        forcedBookingUuid: String? = null
    ): BookingEntity? {
        val guest = guestName.text.toString().trim()
        if (guest.isBlank()) return null
        val source = selectedSourceOrNull()
        if (source == null) {
            Toast.makeText(context, "Select booking source", Toast.LENGTH_SHORT).show()
            return null
        }
        val parsedCheckIn = parseDate(checkIn.text.toString().trim()) ?: return null
        val parsedCheckOut = parseDate(checkOut.text.toString().trim()) ?: return null
        if (parsedCheckOut <= parsedCheckIn) return null
        if (selectedRoomIds.isEmpty()) return null

        val isOta = source.sourceType == BookingSourceType.OTA
        val amountWasEntered = bookingTotal.text.toString().trim().isNotEmpty()
        val pricingStatus = when {
            forceComplimentary || selectedStatus() == PaymentStatus.COMPLIMENTARY -> BookingPricingStatus.COMPLIMENTARY
            amountWasEntered -> BookingPricingStatus.CONFIRMED
            else -> BookingPricingStatus.PENDING
        }
        if (existingBooking != null &&
            !BookingPricingStatus.isPending(existingBooking.pricingStatus) &&
            pricingStatus == BookingPricingStatus.PENDING
        ) {
            Toast.makeText(context, "A confirmed room rate cannot be cleared", Toast.LENGTH_LONG).show()
            return null
        }
        val settlement = currentSettlement(source)
        val total = collectableTotal(source, settlement)
        val paid = if (isOta) 0.0 else existingPaymentEntries.takeIf { it.isNotEmpty() }?.let { stayPaymentTotal(it) }
            ?: paymentPaidForStatus(selectedStatus(), total, advancePaid.numberValue())
        val adults = adultCount.intValue(defaultValue = 1).coerceAtLeast(1)
        val kids = childCount.intValue(defaultValue = 0).coerceAtLeast(0)
        if (paid < 0.0) return null
        if (!isOta && total > 0.0 && paid > total) {
            Toast.makeText(context, "Advance paid cannot exceed booking total", Toast.LENGTH_SHORT).show()
            return null
        }

        return BookingEntity(
            localId = existingBooking?.localId ?: 0,
            remoteId = existingBooking?.remoteId?.takeIf { it.isNotBlank() }
                ?: forcedRemoteId
                ?: UUID.randomUUID().toString(),

            bookingUuid = existingBooking?.bookingUuid?.takeIf { it.isNotBlank() }
                ?: forcedBookingUuid
                ?: UUID.randomUUID().toString(),
            hotelRemoteId = hotelRemoteId,
            propertyRemoteId = selectedBookingPropertyRemoteId(),
            guestName = guest,
            guestMobile = guestMobile.text.toString().trim().ifBlank { null },
            sourceName = source.sourceName,
            sourceRemoteId = source.remoteId,
            sourceType = source.sourceType,
            adultCount = adults,
            childCount = kids,
            checkInMillis = parsedCheckIn,
            checkOutMillis = parsedCheckOut,
            roomRemoteIds = selectedRoomIds.toList(),
            rate = total,
            receivable = total,
            paid = paid,
            paymentStatus = if (isOta) PaymentStatus.FULLY_PAID else selectedStatus(),
            pricingStatus = pricingStatus,
            bookingStatus = existingBooking?.bookingStatus?.ifBlank { BookingStatus.RESERVED }
                ?: BookingStatus.RESERVED,
            actualCheckInAt = existingBooking?.actualCheckInAt,
            actualCheckOutAt = existingBooking?.actualCheckOutAt,
            checkoutNote = existingBooking?.checkoutNote,
            reopenNote = existingBooking?.reopenNote,
            reopenedAt = existingBooking?.reopenedAt,
            grossCharges = settlement.grossCharges,
            roomRevenue = settlement.roomRevenue.takeIf { it > 0.0 } ?: bookingTotal.numberValue(),
            propertyTax = settlement.propertyTax,
            commissionAmount = settlement.commission,
            commissionTax = settlement.commissionTax,
            sourceFee = settlement.fixedFee,
            tdsAmount = settlement.tds,
            tcsAmount = settlement.tcs,
            expectedPayout = if (isOta) settlement.expectedPayout else 0.0,
            notes = notes.text.toString().trim().ifBlank { null },
            updatedAt = System.currentTimeMillis(),
            isDeleted = false,
            syncState = existingBooking?.syncState ?: "PENDING",
            lastSyncError = null,
            lastSyncedAt = existingBooking?.lastSyncedAt,
            revision = existingBooking?.revision ?: 0,
            baseRevision = existingBooking?.baseRevision?.takeIf { it > 0 } ?: existingBooking?.revision ?: 0,
            updatedByUid = existingBooking?.updatedByUid
        ).withCalculatedPayment()
    }

    private fun selectedBookingPropertyRemoteId(): String? {
        val propertyIds = rooms
            .filter { selectedRoomIds.contains(it.remoteId) }
            .mapNotNull { it.propertyRemoteId?.takeIf { id -> id.isNotBlank() } }
            .distinct()
        return when (propertyIds.size) {
            1 -> propertyIds.first()
            0 -> existingBooking?.propertyRemoteId?.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun shareBookingOnWhatsApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, buildShareText())
            setPackage("com.whatsapp")
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, buildShareText())
            }, "Share Booking"))
        }
    }

    private fun buildShareText(): String {
        val selectedRooms = rooms.filter { selectedRoomIds.contains(it.remoteId) }
        val selectedRoomNames = selectedRooms.joinToString(", ") { it.roomName }.ifBlank { "None" }
        val billingProfile = billingProfileText(selectedRooms)
        val total = bookingTotal.text.toString().ifBlank { "Amount not added" }
        val advance = if (selectedSource().sourceType == BookingSourceType.OTA) "OTA receivable" else advancePaid.text.toString().ifBlank { "0" }
        val adults = adultCount.intValue(defaultValue = 1).coerceAtLeast(1)
        val kids = childCount.intValue(defaultValue = 0).coerceAtLeast(0)
        return """
            Booking Details
            Guest Name: ${guestName.text.toString().trim()}
            Guest Mobile: ${guestMobile.text.toString().trim().ifBlank { "N/A" }}
            Source: ${selectedSource().sourceName}
            $billingProfile
            Adults: $adults
            Kids: $kids
            Rooms: $selectedRoomNames
            Arrival: ${checkIn.text}
            Departure: ${checkOut.text}
            Payment Status: ${if (selectedSource().sourceType == BookingSourceType.OTA) "Paid by OTA" else selectedStatus().displayName()}
            Booking Amount: $total
            Advance Paid: $advance
            Balance Amount: ${balance.text.toString().ifBlank { "0" }}
            Notes: ${notes.text.toString().trim().ifBlank { "None" }}
        """.trimIndent()
    }

    private fun billingProfileText(selectedRooms: List<RoomEntity>): String {
        val propertyIds = selectedRooms.mapNotNull { it.propertyRemoteId }.distinct()
        if (propertyIds.isEmpty()) return "Billing Property: Main hotel"
        if (propertyIds.size > 1) return "Billing Property: Multiple properties"

        val property = managedProperties.firstOrNull { it.remoteId == propertyIds.first() }
            ?: return "Billing Property: Main hotel"
        return buildString {
            append("Billing Property: ${property.propertyName}")
            property.legalName?.takeIf { it.isNotBlank() }?.let { append("\nBilling Name: $it") }
            property.gstNumber?.takeIf { it.isNotBlank() }?.let { append("\nGSTIN: $it") }
            property.address?.takeIf { it.isNotBlank() }?.let { append("\nAddress: $it") }
        }
    }

    private fun selectedSource(): BookingSourceEntity {
        return selectedSourceOrNull() ?: defaultSource()
    }

    private fun selectedSourceOrNull(): BookingSourceEntity? {
        return activeSources.getOrNull(sourceSpinner.selectedItemPosition - 1)
    }

    private fun selectedStatus(): String {
        if (selectedSource().sourceType == BookingSourceType.OTA) return PaymentStatus.FULLY_PAID
        return when (paymentStatusSpinner.selectedItemPosition) {
            0 -> PaymentStatus.FULLY_PAID
            1 -> PaymentStatus.PARTIALLY_PAID
            2 -> PaymentStatus.NOT_PAID
            3 -> PaymentStatus.COMPLIMENTARY
            else -> PaymentStatus.NOT_PAID
        }
    }

    private fun statusSpinnerPosition(status: String): Int {
        return when (status) {
            PaymentStatus.FULLY_PAID -> 0
            PaymentStatus.PARTIALLY_PAID -> 1
            PaymentStatus.COMPLIMENTARY -> 3
            else -> 2
        }
    }

    private fun syncHiddenPaymentRadio(status: String) {
        when (status) {
            PaymentStatus.FULLY_PAID -> rbFullyPaid.isChecked = true
            PaymentStatus.PARTIALLY_PAID -> rbPartiallyPaid.isChecked = true
            else -> rbNotPaid.isChecked = true
        }
    }

    private fun applyPaymentStatus(status: String) {
        if (selectedSource().sourceType == BookingSourceType.OTA) {
            advancePaid.setText("0")
            balance.setText(amountText(currentSettlement().expectedPayout))
            refreshPaymentActionVisibility()
            return
        }
        if (syncMoneyFieldsFromPaymentLedger()) {
            refreshPaymentActionVisibility()
            return
        }
        val totalValue = collectableTotal().coerceAtLeast(0.0)
        when (status) {
            PaymentStatus.FULLY_PAID -> {
                setMoneyFieldsEditable(true)
                advancePaid.setText(amountText(totalValue))
                advancePaid.isEnabled = false
                balance.setText("0")
            }
            PaymentStatus.PARTIALLY_PAID -> {
                setMoneyFieldsEditable(true)
                advancePaid.isEnabled = true
                val currentAdvance = advancePaid.numberValue()
                if (currentAdvance <= 0.0 || currentAdvance >= totalValue) advancePaid.setText("")
                balance.setText(amountText((totalValue - advancePaid.numberValue()).coerceAtLeast(0.0)))
            }
            PaymentStatus.COMPLIMENTARY -> {
                setMoneyFieldsEditable(false)
                bookingTotal.setTextIfChanged("0")
                advancePaid.setTextIfChanged("0")
                balance.setTextIfChanged("0")
            }
            else -> {
                setMoneyFieldsEditable(true)
                advancePaid.setText("0")
                advancePaid.isEnabled = false
                balance.setText(amountText(totalValue))
            }
        }
        refreshPaymentActionVisibility()
    }

    private fun refreshBalanceForCurrentStatus() {
        if (selectedSource().sourceType == BookingSourceType.OTA) {
            balance.setText(amountText(currentSettlement().expectedPayout))
            return
        }
        if (syncMoneyFieldsFromPaymentLedger()) {
            refreshPaymentActionVisibility()
            return
        }
        val totalValue = collectableTotal().coerceAtLeast(0.0)
        when (selectedStatus()) {
            PaymentStatus.FULLY_PAID -> {
                advancePaid.setText(amountText(totalValue))
                balance.setText("0")
            }
            PaymentStatus.NOT_PAID -> {
                advancePaid.setText("0")
                balance.setText(amountText(totalValue))
            }
            PaymentStatus.COMPLIMENTARY -> {
                setMoneyFieldsEditable(false)
                bookingTotal.setTextIfChanged("0")
                advancePaid.setTextIfChanged("0")
                balance.setTextIfChanged("0")
            }
            else -> balance.setText(amountText((totalValue - advancePaid.numberValue().coerceAtLeast(0.0)).coerceAtLeast(0.0)))
        }
        refreshPaymentActionVisibility()
    }

    private fun syncMoneyFieldsFromPaymentLedger(): Boolean {
        val booking = existingBooking ?: return false
        if (existingPaymentEntries.isEmpty() || selectedStatus() == PaymentStatus.COMPLIMENTARY) return false

        val preview = currentFinalBillPreview(booking)

        val paidValue = preview.roomPaid.coerceAtLeast(0.0)
        val balanceValue = preview.roomBalance.coerceAtLeast(0.0)

        advancePaid.setTextIfChanged(amountText(paidValue))
        advancePaid.isEnabled = false
        balance.setTextIfChanged(amountText(balanceValue))

        return true
    }

    private fun updateSelectedRoomsText() {
        val roomNames = rooms.filter { selectedRoomIds.contains(it.remoteId) }
            .joinToString(", ") { it.roomName }
            .ifBlank { "No rooms selected" }
        selectedRoomsText.text = roomNames
        selectedRoomsText.setTextColor(Color.parseColor("#5427F4"))
    }

    private fun updateDateRangeText() {
        val checkInMillis = parseDate(checkIn.text.toString()) ?: return
        val checkOutMillis = parseDate(checkOut.text.toString()) ?: return
        val nights = (((checkOutMillis - checkInMillis) / DAY_MILLIS).toInt()).coerceAtLeast(1)
        dateRangeText.setText("${friendlyDate(checkInMillis)} to ${friendlyDate(checkOutMillis)}")
        checkInDisplay.text = dateCardText(checkInMillis)
        checkOutDisplay.text = dateCardText(checkOutMillis)
        nightCountDisplay.text = if (nights == 1) "1 night" else "$nights nights"
    }

    private fun pickCheckInDate() {
        val currentCheckIn = parseDate(checkIn.text.toString()) ?: selectedCheckInMillis
        pickDateValue(currentCheckIn) { pickedCheckIn ->
            checkIn.setText(dateFormat.format(Date(pickedCheckIn)))
            val currentCheckOut = parseDate(checkOut.text.toString()) ?: (pickedCheckIn + DAY_MILLIS)
            if (currentCheckOut <= pickedCheckIn) {
                checkOut.setText(dateFormat.format(Date(pickedCheckIn + DAY_MILLIS)))
            }
            afterDateChanged()
        }
    }

    private fun pickCheckoutDate() {
        val currentCheckIn = parseDate(checkIn.text.toString()) ?: selectedCheckInMillis
        val currentCheckOut = (parseDate(checkOut.text.toString()) ?: currentCheckIn + DAY_MILLIS)
            .coerceAtLeast(currentCheckIn + DAY_MILLIS)
        pickDateValue(currentCheckOut) { pickedCheckOut ->
            val safeCheckout = pickedCheckOut.coerceAtLeast(currentCheckIn + DAY_MILLIS)
            checkOut.setText(dateFormat.format(Date(safeCheckout)))
            afterDateChanged()
        }
    }

    private fun pickDateValue(initialMillis: Long, onPicked: (Long) -> Unit) {
        val initial = Calendar.getInstance().apply { timeInMillis = initialMillis }
        DatePickerDialog(context, { _, year, month, day ->
            val picked = Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onPicked(picked.timeInMillis)
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun afterDateChanged() {
        updateDateRangeText()
        refreshBalanceForCurrentStatus()
        refreshSourceMode()
    }

    private fun friendlyDate(millis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "$day${ordinalSuffix(day)} ${dateDisplayMonthFormat.format(Date(millis))}"
    }

    private fun dateCardText(millis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val weekday = SimpleDateFormat("EEE", Locale.getDefault()).format(Date(millis))
        val monthYear = SimpleDateFormat("MMM, yyyy", Locale.getDefault()).format(Date(millis))
        return "$day ${monthYear.substringBefore(",")} $weekday"
    }

    private fun ordinalSuffix(day: Int): String {
        if (day in 11..13) return "th"
        return when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }

    private fun afterTextChanged(action: () -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = action()
        }
    }

    private fun EditText.numberValue(): Double = text.toString().trim().toDoubleOrNull() ?: 0.0
    private fun EditText.intValue(defaultValue: Int): Int = text.toString().trim().toIntOrNull() ?: defaultValue
    private fun EditText.setTextIfChanged(value: String) {
        if (text.toString() != value) setText(value)
    }

    private fun detectPaymentStatus(total: Double, paid: Double): String {
        return when {
            paid <= 0.0 -> PaymentStatus.NOT_PAID
            total > 0.0 && paid >= total -> PaymentStatus.FULLY_PAID
            paid > 0.0 && paid < total -> PaymentStatus.PARTIALLY_PAID
            else -> PaymentStatus.NOT_PAID
        }
    }

    private fun paymentPaidForStatus(status: String, total: Double, typedAdvance: Double): Double {
        return when (status) {
            PaymentStatus.FULLY_PAID -> total.coerceAtLeast(0.0)
            PaymentStatus.COMPLIMENTARY -> 0.0
            PaymentStatus.NOT_PAID -> 0.0
            else -> typedAdvance.coerceAtLeast(0.0)
        }
    }

    private fun refreshPaymentActionVisibility() {
        val hidePaymentActions = existingBooking == null ||
                selectedStatus() == PaymentStatus.COMPLIMENTARY
        addPaymentButton.visibility = if (hidePaymentActions) View.GONE else View.VISIBLE
        complimentaryButton.visibility = View.GONE
    }

    private fun paymentCategoryOptions(booking: BookingEntity): List<String> {
        return if (booking.sourceType == BookingSourceType.OTA || BookingPricingStatus.isPending(booking.pricingStatus)) {
            listOf(BookingPaymentCategory.FOOD, BookingPaymentCategory.SERVICE, BookingPaymentCategory.DAMAGE)
        } else {
            BookingPaymentCategory.selectable
        }
    }

    private fun defaultPaymentCategory(booking: BookingEntity, options: List<String>): String {
        val summary = FolioSummaryBuilder.build(
            booking = booking,
            payments = existingPaymentEntries,
            foodOrders = foodOrders.filter { it.bookingRemoteId == booking.remoteId },
            accountingCharges = existingAccountingCharges,
            bookingFinancialLines = existingFinancialLines
        )
        val stayDue = (summary.chargeBuckets.stay - summary.paidBuckets.stay).coerceAtLeast(0.0)
        val foodDue = (summary.chargeBuckets.food - summary.paidBuckets.food).coerceAtLeast(0.0)
        val serviceDue = (summary.chargeBuckets.service - summary.paidBuckets.service).coerceAtLeast(0.0)
        val damageDue = (summary.damageTotal - summary.damagePaid).coerceAtLeast(0.0)
        val preferred = when {
            booking.sourceType == BookingSourceType.OTA && serviceDue > 0.01 -> BookingPaymentCategory.SERVICE
            booking.sourceType == BookingSourceType.OTA && foodDue > 0.01 -> BookingPaymentCategory.FOOD
            booking.sourceType == BookingSourceType.OTA && damageDue > 0.01 -> BookingPaymentCategory.DAMAGE
            serviceDue > 0.01 && foodDue <= 0.01 && stayDue <= 0.01 -> BookingPaymentCategory.SERVICE
            foodDue > 0.01 && serviceDue <= 0.01 && stayDue <= 0.01 -> BookingPaymentCategory.FOOD
            damageDue > 0.01 && stayDue <= 0.01 && foodDue <= 0.01 && serviceDue <= 0.01 -> BookingPaymentCategory.DAMAGE
            else -> BookingPaymentCategory.AUTO
        }
        return preferred.takeIf { it in options } ?: options.firstOrNull().orEmpty()
    }

    private fun String.displayBucketName(): String {
        return when (this) {
            BookingPaymentCategory.STAY -> "Room"
            BookingPaymentCategory.FOOD -> "Food"
            BookingPaymentCategory.SERVICE -> "Service"
            BookingPaymentCategory.DAMAGE -> "Damage"
            else -> "General"
        }
    }

    private fun setMoneyFieldsEditable(isEditable: Boolean) {
        bookingTotal.isEnabled = isEditable && !roomRateLocked
        advancePaid.isEnabled = isEditable &&
                BookingPaymentSourcePolicy.canEditInitialAdvance(existingBooking) &&
                selectedStatus() == PaymentStatus.PARTIALLY_PAID
        balance.isEnabled = false
        listOf(bookingTotal, advancePaid, balance).forEach { field ->
            field.setTextColor(Color.parseColor("#24212A"))
            field.setHintTextColor(Color.parseColor("#8A858D"))
        }
    }

    private fun forceLightDialogColors(view: View) {
        when (view) {
            is EditText -> {
                view.setTextColor(Color.parseColor("#24212A"))
                view.setHintTextColor(Color.parseColor("#8A858D"))
            }
            is Button -> Unit
            is TextView -> view.setTextColor(Color.parseColor("#24212A"))
            is android.view.ViewGroup -> {
                for (index in 0 until view.childCount) {
                    forceLightDialogColors(view.getChildAt(index))
                }
            }
        }
    }

    private fun spinnerAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {
            init {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return tintSpinnerText(super.getView(position, convertView, parent))
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return tintSpinnerText(super.getDropDownView(position, convertView, parent))
            }
        }
    }

    private fun tintSpinnerText(view: View): View {
        (view as? TextView)?.setTextColor(Color.parseColor("#24212A"))
        view.setBackgroundColor(Color.WHITE)
        return view
    }

    private fun currentSettlement(source: BookingSourceEntity = selectedSource()): com.example.bookingregister.source.domain.SourceSettlement {
        val summary = currentFinancialSummary()
        return settlementCalculator.calculate(
            source,
            summary.roomRevenue,
            summary.propertyTax,
            hotelHasGst
        )
    }

    private fun collectableTotal(
        source: BookingSourceEntity = selectedSource(),
        settlement: com.example.bookingregister.source.domain.SourceSettlement = currentSettlement(source)
    ): Double {
        return if (source.sourceType == BookingSourceType.OTA || source.hasDeductions()) {
            settlement.expectedPayout
        } else {
            settlement.grossCharges
        }
    }

    private fun BookingSourceEntity.hasDeductions(): Boolean {
        return commissionPercent > 0.0 || commissionGstPercent > 0.0 ||
                tcsPercent > 0.0 || tdsPercent > 0.0 || fixedFee > 0.0
    }

    private fun currentFinancialSummary(): BookingFinancialSummary {
        return financialCalculator.summarize(
            lines = draftFinancialLines,
            fallbackFinalCharges = bookingTotal.numberValue(),
            fallbackRoomCount = selectedRoomIds.size.coerceAtLeast(1),
            fallbackNights = selectedNights(),
            gstEnabled = hotelHasGst
        )
    }
    private fun ensureRoomNightFinancialLinesForBooking(booking: BookingEntity) {
        val stayDates = selectedStayDates()
        val selectedRooms = rooms.filter { selectedRoomIds.contains(it.remoteId) }

        if (stayDates.isEmpty() || selectedRooms.isEmpty()) return

        val roomNightCount = selectedRooms.size * stayDates.size
        if (roomNightCount <= 0) return

        val grossTotal = bookingTotal.numberValue().coerceAtLeast(0.0)
        val activeExistingLines = draftFinancialLines.filter { !it.isDeleted }

        val expectedLineKeys = selectedRooms.flatMap { room ->
            stayDates.map { dateMillis -> room.remoteId to dateMillis }
        }.toSet()

        val existingLineKeys = activeExistingLines
            .map { it.roomRemoteId to it.businessDateMillis }
            .toSet()

        val existingGrossTotal = activeExistingLines.sumOf { it.grossAmount.coerceAtLeast(0.0) }

        val sameRoomsAndDates = existingLineKeys == expectedLineKeys
        val sameAmount = kotlin.math.abs(existingGrossTotal - grossTotal) <= 0.01

        if (sameRoomsAndDates && sameAmount) return

        val grossPerRoomNight = grossTotal / roomNightCount
        val now = System.currentTimeMillis()

        draftFinancialLines = selectedRooms.flatMap { room ->
            stayDates.map { dateMillis ->
                val existingLine = activeExistingLines.firstOrNull {
                    it.roomRemoteId == room.remoteId &&
                            it.businessDateMillis == dateMillis
                }

                financialCalculator.lineFromGross(
                    remoteId = existingLine?.remoteId ?: UUID.randomUUID().toString(),
                    hotelRemoteId = hotelRemoteId,
                    bookingRemoteId = booking.remoteId,
                    roomRemoteId = room.remoteId,
                    businessDateMillis = dateMillis,
                    grossAmount = grossPerRoomNight,
                    gstEnabled = hotelHasGst,
                    source = BookingFinancialLineSource.MANUAL,
                    roomGstSlabs = roomGstSlabs
                ).copy(
                    localId = existingLine?.localId ?: 0,
                    propertyRemoteId = room.propertyRemoteId?.takeIf { it.isNotBlank() }
                        ?: existingLine?.propertyRemoteId,
                    updatedAt = now,
                    syncState = "PENDING",
                    lastSyncedAt = existingLine?.lastSyncedAt,
                    revision = existingLine?.revision ?: 0,
                    baseRevision = existingLine?.baseRevision?.takeIf { it > 0 }
                        ?: existingLine?.revision
                        ?: 0
                )
            }
        }
    }
    private fun calculatedGst() = gstCalculator.calculate(
        finalCharges = bookingTotal.numberValue(),
        roomCount = selectedRoomIds.size.coerceAtLeast(1),
        nights = selectedNights(),
        gstEnabled = hotelHasGst
    )

    private fun selectedNights(): Int {
        val checkInMillis = parseDate(checkIn.text.toString().trim()) ?: selectedCheckInMillis
        val checkOutMillis = parseDate(checkOut.text.toString().trim()) ?: (checkInMillis + DAY_MILLIS)
        val nights = ((checkOutMillis - checkInMillis) / DAY_MILLIS).toInt()
        return nights.coerceAtLeast(1)
    }

    private fun selectedStayDates(): List<Long> {
        val checkInMillis = parseDate(checkIn.text.toString().trim()) ?: return emptyList()
        val checkOutMillis = parseDate(checkOut.text.toString().trim()) ?: return emptyList()
        if (checkOutMillis <= checkInMillis) return emptyList()
        val dates = mutableListOf<Long>()
        var cursor = checkInMillis
        while (cursor < checkOutMillis) {
            dates.add(cursor)
            cursor += DAY_MILLIS
        }
        return dates
    }

    private fun parseDate(value: String): Long? = runCatching { dateFormat.parse(value)?.time }.getOrNull()

    private fun amountText(value: Double?): String {
        val amount = value ?: 0.0
        return if (amount == 0.0) "0" else if (amount % 1.0 == 0.0) amount.toLong().toString() else "%.2f".format(amount)
    }

    private fun paymentTotal(payments: List<BookingPaymentEntity>): Double {
        return payments.filter { !it.isDeleted }.sumOf { payment ->
            val sign = if (payment.isNegativePayment()) -1.0 else 1.0
            sign * payment.amount
        }.coerceAtLeast(0.0)
    }

    private fun stayPaymentTotal(payments: List<BookingPaymentEntity>): Double {
        return payments.filter { !it.isDeleted }.sumOf { payment ->
            val sign = if (payment.isNegativePayment()) -1.0 else 1.0
            val allocated = payment.allocatedStayAmount + payment.allocatedFoodAmount +
                    payment.allocatedServiceAmount + payment.allocatedDamageAmount
            val stayAmount = if (allocated > 0.0) {
                payment.allocatedStayAmount
            } else if (payment.paymentCategory == BookingPaymentCategory.FOOD || payment.paymentCategory == BookingPaymentCategory.SERVICE) {
                0.0
            } else {
                payment.amount
            }
            sign * stayAmount
        }.coerceAtLeast(0.0)
    }

    private fun defaultPaymentAmount(booking: BookingEntity, paymentType: String): String {
        val summary = FolioSummaryBuilder.build(
            booking = booking,
            payments = existingPaymentEntries,
            foodOrders = foodOrders.filter { it.bookingRemoteId == booking.remoteId },
            accountingCharges = existingAccountingCharges,
            bookingFinancialLines = draftFinancialLines
        )
        val paid = existingPaymentEntries.takeIf { it.isNotEmpty() }?.let { paymentTotal(it) } ?: booking.paid
        val overpaid = (paid - summary.grandTotal).coerceAtLeast(0.0)
        val amount = when (paymentType) {
            BookingPaymentType.REFUND, BookingPaymentType.ADJUSTMENT -> overpaid
            else -> if (booking.sourceType == BookingSourceType.OTA) {
                (summary.foodBalance + summary.serviceBalance).coerceAtLeast(0.0)
            } else {
                summary.grandBalance
            }
        }
        return amount.takeIf { it > 0.0 }?.let { amountText(it) }.orEmpty()
    }

    private fun BookingPaymentEntity.isNegativePayment(): Boolean {
        return paymentType == BookingPaymentType.REFUND || paymentType == BookingPaymentType.ADJUSTMENT
    }

    private fun String.displayPaymentType(): String {
        return when (this) {
            BookingPaymentType.ADVANCE -> "Advance"
            BookingPaymentType.REFUND -> "Refund"
            BookingPaymentType.ADJUSTMENT -> "Correction"
            else -> "Payment"
        }
    }

    private fun String.displayName(): String {
        return when (this) {
            PaymentStatus.FULLY_PAID -> "Fully Paid"
            PaymentStatus.PARTIALLY_PAID -> "Partial Paid"
            PaymentStatus.COMPLIMENTARY -> "Complimentary"
            else -> "Not Paid"
        }
    }

    private fun markComplimentary() {
        AlertDialog.Builder(context)
            .setTitle("Mark Complimentary")
            .setMessage("This will set this booking amount and payment due to zero. Room inventory will remain blocked.")
            .setPositiveButton("Mark") { _, _ ->
                forceComplimentary = true
                paymentStatusSpinner.setSelection(statusSpinnerPosition(PaymentStatus.COMPLIMENTARY), false)
                bookingTotal.setText("0")
                advancePaid.setText("0")
                balance.setText("0")
                val currentNote = notes.text.toString().trim()
                if (!currentNote.contains("Complimentary", ignoreCase = true)) {
                    notes.setText(
                        listOf(currentNote, "Complimentary stay")
                            .filter { it.isNotBlank() }
                            .joinToString("\n")
                    )
                }
                refreshComplimentaryState()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshComplimentaryState() {
        if (!forceComplimentary) return
        setMoneyFieldsEditable(false)
        balance.setText("0")
        paymentHistory.text = "Complimentary stay\nNo amount receivable. Inventory remains blocked."
        refreshPaymentActionVisibility()
    }

    private fun getAvailableRoomsForSelectedDates(): List<RoomEntity> {
        val checkInMillis = parseDate(checkIn.text.toString().trim()) ?: selectedCheckInMillis
        val checkOutMillis = parseDate(checkOut.text.toString().trim()) ?: (checkInMillis + DAY_MILLIS)
        return rooms.filter { room ->
            val isExistingRoom = existingBooking?.roomRemoteIds?.contains(room.remoteId) == true
            (isExistingRoom || RoomLifecycleStatus.normalize(room.lifecycleStatus) == RoomLifecycleStatus.ACTIVE) &&
            bookings.none { booking ->
                !booking.isDeleted &&
                        booking.remoteId != existingBooking?.remoteId &&
                        booking.roomRemoteIds.contains(room.remoteId) &&
                        booking.checkInMillis < checkOutMillis &&
                        booking.checkOutMillis > checkInMillis
            }
        }
    }

    private fun defaultSource(): BookingSourceEntity {
        return BookingSourceEntity(
            remoteId = "local_walk_in",
            hotelRemoteId = hotelRemoteId,
            sourceName = "Walk-in",
            sourceType = BookingSourceType.DIRECT
        )
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
