package com.example.bookingregister.ui.service

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bookingregister.data.entities.BookingAccountingChargeType
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.data.entities.ServiceMenuItemEntity
import com.example.bookingregister.data.repository.BookingRepository
import com.example.bookingregister.data.repository.FoodBillingRepository
import com.example.bookingregister.data.repository.SaveResult
import kotlinx.coroutines.launch

class ServiceBillingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HOTEL_REMOTE_ID = "hotel_remote_id"
        const val EXTRA_TARGET_BOOKING_REMOTE_ID = "target_booking_remote_id"
        const val EXTRA_TARGET_ROOM_REMOTE_ID = "target_room_remote_id"
        private const val PURPLE = "#5B2CF1"
        private const val TEXT = "#11111A"
        private const val MUTED = "#68677A"
    }

    private lateinit var foodRepository: FoodBillingRepository
    private lateinit var bookingRepository: BookingRepository
    private lateinit var listContainer: LinearLayout
    private lateinit var subtitleText: TextView

    private val services = mutableListOf<ServiceMenuItemEntity>()
    private val bookings = mutableListOf<BookingEntity>()
    private val rooms = mutableListOf<RoomEntity>()
    private var targetBookingRemoteId: String? = null
    private var targetRoomRemoteId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hotelRemoteId = intent.getStringExtra(EXTRA_HOTEL_REMOTE_ID)
        targetBookingRemoteId = intent.getStringExtra(EXTRA_TARGET_BOOKING_REMOTE_ID)
        targetRoomRemoteId = intent.getStringExtra(EXTRA_TARGET_ROOM_REMOTE_ID)

        if (hotelRemoteId.isNullOrBlank() || targetBookingRemoteId.isNullOrBlank()) {
            Toast.makeText(this, "Booking is required for service", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        foodRepository = FoodBillingRepository(applicationContext, lifecycleScope, hotelRemoteId)
        bookingRepository = BookingRepository(applicationContext, lifecycleScope, hotelRemoteId)
        buildScreen()
        observeData()
        foodRepository.startRealtimeSync()
        bookingRepository.startRealtimeSync()
    }

    override fun onDestroy() {
        foodRepository.stopRealtimeSync()
        bookingRepository.stopRealtimeSync()
        super.onDestroy()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(10))
        }
        header.addView(Button(this).apply {
            text = "<"
            textSize = 22f
            setTextColor(Color.BLACK)
            background = roundedStroke("#FFFFFF", "#E6E4EF", 12)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(56), dp(56)))

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
        }
        titleBox.addView(TextView(this).apply {
            text = "Services"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(TEXT))
        })
        subtitleText = TextView(this).apply {
            text = "Room"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(PURPLE))
        }
        titleBox.addView(subtitleText)
        header.addView(titleBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        header.addView(Button(this).apply {
            text = "Custom"
            textSize = 14f
            setTextColor(Color.parseColor(PURPLE))
            background = roundedStroke("#FFFFFF", "#C9BDF8", 12)
            setOnClickListener { showCustomServiceDialog() }
        }, LinearLayout.LayoutParams(dp(118), dp(52)))

        root.addView(header)

        val scroll = ScrollView(this)
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(6), dp(20), dp(24))
        }
        scroll.addView(listContainer)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)
    }

    private fun observeData() {
        foodRepository.observeServiceMenuItems().observe(this) { updated ->
            services.clear()
            services.addAll(updated.filter { !it.isDeleted && it.isActive })
            render()
        }
        foodRepository.observeBookings().observe(this) { updated ->
            bookings.clear()
            bookings.addAll(updated.filter { !it.isDeleted })
            render()
        }
        foodRepository.observeRooms().observe(this) { updated ->
            rooms.clear()
            rooms.addAll(updated.filter { !it.isDeleted })
            render()
        }
    }

    private fun render() {
        val booking = targetBooking()
        val roomName = targetRoom()?.roomName
            ?: booking?.roomRemoteIds?.firstOrNull()?.let { id -> rooms.firstOrNull { it.remoteId == id }?.roomName }
            ?: "Room"
        subtitleText.text = roomName
        listContainer.removeAllViews()

        if (booking == null) {
            listContainer.addView(emptyText("Booking is loading..."))
            return
        }

        if (services.isEmpty()) {
            listContainer.addView(emptyText("No service catalog items yet. Use Custom to add a one-time service to this folio."))
            return
        }

        services.forEach { service ->
            listContainer.addView(serviceRow(service, booking))
        }
    }

    private fun serviceRow(service: ServiceMenuItemEntity, booking: BookingEntity): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(14), dp(14))
            background = roundedStroke("#FFFFFF", "#ECEAF3", 14)
        }
        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        textBox.addView(TextView(this).apply {
            text = service.serviceName
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(TEXT))
        })
        textBox.addView(TextView(this).apply {
            text = serviceDetails(service)
            textSize = 14f
            setTextColor(Color.parseColor(MUTED))
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(textBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "Add  +"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(PURPLE))
            background = roundedStroke("#FFFFFF", "#C9BDF8", 12)
            setOnClickListener { addCatalogService(booking, service) }
        }, LinearLayout.LayoutParams(dp(104), dp(48)))

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            setPadding(0, 0, 0, dp(14))
        }
    }

    private fun serviceDetails(service: ServiceMenuItemEntity): String = buildString {
        service.description?.takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
        append("Rs ").append(money(service.price))
        service.unitLabel?.takeIf { it.isNotBlank() }?.let { append(" / ").append(it) }
        append(" | GST ").append(money(service.gstRatePercent)).append("%")
        if (service.taxInclusive) append(" incl.")
        service.sacCode?.takeIf { it.isNotBlank() }?.let { append(" | SAC ").append(it) }
    }

    private fun addCatalogService(booking: BookingEntity, service: ServiceMenuItemEntity) {
        val note = buildString {
            append("Service catalog")
            service.categoryName?.takeIf { it.isNotBlank() }?.let { append(" | ").append(it) }
            append(" | GST ").append(money(service.gstRatePercent)).append("%")
            if (service.taxInclusive) append(" inclusive")
            service.sacCode?.takeIf { it.isNotBlank() }?.let { append(" | SAC ").append(it) }
        }
        val taxableAmount = service.price.coerceAtLeast(0.0)
        val grossAmount = if (service.taxInclusive) {
            taxableAmount
        } else {
            taxableAmount * (1.0 + service.gstRatePercent.coerceAtLeast(0.0) / 100.0)
        }
        saveServiceCharge(
            booking = booking,
            amount = grossAmount,
            description = service.serviceName,
            note = note,
            hsnSacCode = service.sacCode,
            gstRatePercent = service.gstRatePercent,
            taxInclusive = service.taxInclusive,
            taxableAmount = taxableAmount
        )
    }

    private fun showCustomServiceDialog() {
        val booking = targetBooking()
        if (booking == null) {
            Toast.makeText(this, "Booking is loading", Toast.LENGTH_SHORT).show()
            return
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val serviceInput = EditText(this).apply {
            hint = "Service name, e.g. Bonfire"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val amountInput = EditText(this).apply {
            hint = "Amount"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val noteInput = EditText(this).apply {
            hint = "Note optional"
            minLines = 2
            setSingleLine(false)
        }
        container.addView(serviceInput)
        container.addView(amountInput)
        container.addView(noteInput)

        AlertDialog.Builder(this)
            .setTitle("Add Custom Service")
            .setView(container)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    val addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    addButton.setOnClickListener {
                        val serviceName = serviceInput.text.toString().trim()
                        val amount = amountInput.text.toString().trim().toDoubleOrNull() ?: 0.0
                        val note = noteInput.text.toString().trim().ifBlank { null }
                        if (serviceName.isBlank()) {
                            Toast.makeText(this, "Enter service name", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        if (amount <= 0.0) {
                            Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        addButton.isEnabled = false
                        addButton.text = "Adding..."
                        saveServiceCharge(booking, amount, serviceName, note) {
                            dialog.dismiss()
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun saveServiceCharge(
        booking: BookingEntity,
        amount: Double,
        description: String,
        note: String?,
        hsnSacCode: String? = null,
        gstRatePercent: Double = 0.0,
        taxInclusive: Boolean = true,
        taxableAmount: Double? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        lifecycleScope.launch {
            when (val result = bookingRepository.addBookingAccountingCharge(
                booking = booking,
                chargeType = BookingAccountingChargeType.SERVICE_CHARGE,
                amount = amount,
                description = description,
                reason = note,
                accountBucket = BookingPaymentCategory.SERVICE,
                hsnSacCode = hsnSacCode,
                gstRatePercent = gstRatePercent,
                taxInclusive = taxInclusive,
                taxableAmount = taxableAmount
            )) {
                is SaveResult.Success -> {
                    Toast.makeText(this@ServiceBillingActivity, "Service added to folio", Toast.LENGTH_SHORT).show()
                    onSuccess?.invoke()
                }
                is SaveResult.Conflict -> Toast.makeText(this@ServiceBillingActivity, result.message, Toast.LENGTH_LONG).show()
                is SaveResult.Error -> Toast.makeText(this@ServiceBillingActivity, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun targetBooking(): BookingEntity? =
        bookings.firstOrNull { it.remoteId == targetBookingRemoteId }

    private fun targetRoom(): RoomEntity? =
        targetRoomRemoteId?.let { roomId -> rooms.firstOrNull { it.remoteId == roomId } }

    private fun emptyText(message: String): TextView = TextView(this).apply {
        text = message
        textSize = 16f
        setTextColor(Color.parseColor(MUTED))
        setPadding(dp(8), dp(24), dp(8), dp(8))
    }

    private fun roundedStroke(fill: String, stroke: String, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(fill))
            setStroke(dp(1), Color.parseColor(stroke))
            cornerRadius = dp(radius).toFloat()
        }

    private fun money(value: Double): String =
        java.text.DecimalFormat("0.##").format(value)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
