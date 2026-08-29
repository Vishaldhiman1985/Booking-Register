package com.example.bookingregister.reporting.property.debug

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.bookingregister.common.domain.BusinessDates
import com.example.bookingregister.data.AppDatabase
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingFinancialLineEntity
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.ManagedPropertyEntity
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.data.repository.BookingRepository
import com.example.bookingregister.reporting.property.PropertyReportRawData
import com.example.bookingregister.reporting.property.PropertyReportScope
import com.example.bookingregister.reporting.property.PropertyReportingEngine
import com.example.bookingregister.revenue.domain.RevenuePeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * DEBUG-ONLY, READ-ONLY test screen.
 *
 * This class:
 * - reads the existing local Room database through BookingRepository observers
 * - never calls startRealtimeSync()
 * - exposes no save, edit, payout, refund or correction action
 * - never ships in the release source set
 */
class PropertyReportingDebugActivity : AppCompatActivity() {

    private data class HotelChoice(
        val remoteId: String,
        val name: String
    )

    private data class PropertyChoice(
        val key: String,
        val label: String,
        val propertyRemoteId: String?,
        val includeAllProperties: Boolean
    )

    private lateinit var propertySpinner: Spinner
    private lateinit var hotelLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var reportContainer: LinearLayout

    private var repository: BookingRepository? = null
    private var activeHotel: HotelChoice? = null

    private var properties: List<ManagedPropertyEntity> = emptyList()
    private var rooms: List<RoomEntity> = emptyList()
    private var bookings: List<BookingEntity> = emptyList()
    private var financialLines: List<BookingFinancialLineEntity> = emptyList()
    private var payments: List<BookingPaymentEntity> = emptyList()

    private var propertiesLoaded = false
    private var roomsLoaded = false
    private var bookingsLoaded = false
    private var financialLinesLoaded = false
    private var paymentsLoaded = false

    private var propertyChoices: List<PropertyChoice> = emptyList()
    private var selectedPropertyKey: String = ALL_PROPERTIES_KEY

    private val engine = PropertyReportingEngine()
    private val moneyFormatter = NumberFormat.getNumberInstance(Locale("en", "IN"))
    private val dateFormatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())

        statusLabel.text = "Finding local hotel data..."
        lifecycleScope.launch {
            val hotels = loadLocalHotels()
            when {
                hotels.isEmpty() -> {
                    statusLabel.text = "No local hotel record found. Open the normal app and login first."
                }
                hotels.size == 1 -> bindHotel(hotels.first())
                else -> chooseHotel(hotels)
            }
        }
    }

    private fun buildContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // Target SDK 36 draws edge-to-edge. Keep this DEBUG header clear of
        // the phone status/navigation bars without changing the production theme.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        root.addView(TextView(this).apply {
            text = "PROPERTY REPORTING — DEBUG / READ ONLY"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setBackgroundColor(Color.rgb(55, 65, 81))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(58)
        ))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(10))
        }

        hotelLabel = TextView(this).apply {
            text = "Hotel: —"
            textSize = 15f
            setTextColor(Color.rgb(45, 45, 45))
        }
        controls.addView(hotelLabel)

        propertySpinner = Spinner(this)
        controls.addView(propertySpinner, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(48)
        ))

        statusLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(100, 100, 100))
            setPadding(0, dp(6), 0, dp(4))
        }
        controls.addView(statusLabel)

        root.addView(controls)

        val scroll = ScrollView(this)
        reportContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(30))
        }
        scroll.addView(reportContainer)

        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        propertySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                propertyChoices.getOrNull(position)?.let {
                    selectedPropertyKey = it.key
                    renderReport()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        return root
    }

    private suspend fun loadLocalHotels(): List<HotelChoice> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT remoteId, hotelName FROM hotels WHERE isDeleted = 0 ORDER BY hotelName"
        )
        cursor.use {
            val remoteIdIndex = it.getColumnIndexOrThrow("remoteId")
            val hotelNameIndex = it.getColumnIndexOrThrow("hotelName")
            buildList {
                while (it.moveToNext()) {
                    val remoteId = it.getString(remoteIdIndex).orEmpty()
                    if (remoteId.isNotBlank()) {
                        add(
                            HotelChoice(
                                remoteId = remoteId,
                                name = it.getString(hotelNameIndex).orEmpty().ifBlank { "Booking Register" }
                            )
                        )
                    }
                }
            }
        }
    }

    private fun chooseHotel(hotels: List<HotelChoice>) {
        AlertDialog.Builder(this)
            .setTitle("Choose local hotel account")
            .setItems(hotels.map { it.name }.toTypedArray()) { _, which ->
                bindHotel(hotels[which])
            }
            .setCancelable(false)
            .show()
    }

    private fun bindHotel(hotel: HotelChoice) {
        activeHotel = hotel
        hotelLabel.text = "Hotel: ${hotel.name}"
        statusLabel.text = "Loading local Room database only..."

        propertiesLoaded = false
        roomsLoaded = false
        bookingsLoaded = false
        financialLinesLoaded = false
        paymentsLoaded = false

        val repo = BookingRepository(applicationContext, lifecycleScope, hotel.remoteId)
        repository = repo

        // Intentionally DO NOT call repo.startRealtimeSync().
        repo.observeManagedProperties().observe(this) {
            properties = it
            propertiesLoaded = true
            rebuildPropertyChoices()
            renderReport()
        }
        repo.observeRooms().observe(this) {
            rooms = it
            roomsLoaded = true
            rebuildPropertyChoices()
            renderReport()
        }
        repo.observeBookings().observe(this) {
            bookings = it
            bookingsLoaded = true
            rebuildPropertyChoices()
            renderReport()
        }
        repo.observeFinancialLines().observe(this) {
            financialLines = it
            financialLinesLoaded = true
            renderReport()
        }
        repo.observePayments().observe(this) {
            payments = it
            paymentsLoaded = true
            renderReport()
        }
    }

    private fun rebuildPropertyChoices() {
        if (!::propertySpinner.isInitialized) return

        val previousKey = selectedPropertyKey
        val activeProperties = properties
            .filter { !it.isDeleted }
            .sortedWith(compareBy<ManagedPropertyEntity> { it.sortOrder }.thenBy { it.propertyName.lowercase() })

        val choices = mutableListOf(
            PropertyChoice(
                key = ALL_PROPERTIES_KEY,
                label = "All Properties — Consolidated",
                propertyRemoteId = null,
                includeAllProperties = true
            )
        )

        activeProperties.forEach { property ->
            choices += PropertyChoice(
                key = property.remoteId,
                label = property.propertyName,
                propertyRemoteId = property.remoteId,
                includeAllProperties = false
            )
        }

        val hasLegacyRecords = rooms.any { !it.isDeleted && it.propertyRemoteId.isNullOrBlank() } ||
            bookings.any { !it.isDeleted && it.propertyRemoteId.isNullOrBlank() }

        if (hasLegacyRecords) {
            choices += PropertyChoice(
                key = LEGACY_PROPERTY_KEY,
                label = "Legacy / Unassigned Property",
                propertyRemoteId = null,
                includeAllProperties = false
            )
        }

        propertyChoices = choices
        val selectedIndex = choices.indexOfFirst { it.key == previousKey }.takeIf { it >= 0 } ?: 0

        propertySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            choices.map { it.label }
        )
        propertySpinner.setSelection(selectedIndex, false)
        selectedPropertyKey = choices[selectedIndex].key
    }

    private fun renderReport() {
        if (!allDataLoaded()) {
            if (::statusLabel.isInitialized) {
                statusLabel.text = "Loading: ${loadingText()}"
            }
            return
        }

        val hotel = activeHotel ?: return
        val choice = propertyChoices.firstOrNull { it.key == selectedPropertyKey }
            ?: propertyChoices.firstOrNull()
            ?: return

        val range = RevenuePeriod.financialYearFor()
        val raw = PropertyReportRawData(
            properties = properties,
            rooms = rooms,
            bookings = bookings,
            financialLines = financialLines,
            payments = payments
        )
        val scope = PropertyReportScope(
            hotelRemoteId = hotel.remoteId,
            propertyRemoteId = choice.propertyRemoteId,
            includeAllProperties = choice.includeAllProperties,
            startMillis = range.startMillis,
            endMillis = range.endMillis
        )
        val snapshot = engine.build(raw, scope)

        statusLabel.text =
            "LOCAL DATABASE ONLY • ${choice.label} • ${formatRange(range.startMillis, range.endMillis)}"

        reportContainer.removeAllViews()

        reportContainer.addView(sectionTitle("Revenue — Financial Year"))
        reportContainer.addView(metric("Gross Room Billing", money(snapshot.revenue.grossRoomBilling)))
        reportContainer.addView(metric("Room Revenue (ex-GST)", money(snapshot.revenue.roomRevenue)))
        reportContainer.addView(metric("GST Collected", money(snapshot.revenue.gstCollected)))
        reportContainer.addView(metric("CGST", money(snapshot.revenue.cgstCollected)))
        reportContainer.addView(metric("SGST", money(snapshot.revenue.sgstCollected)))
        reportContainer.addView(metric("Cess", money(snapshot.revenue.cessCollected)))
        reportContainer.addView(metric("Payments recorded in FY", money(snapshot.revenue.paymentsRecordedInPeriod)))
        reportContainer.addView(metric("Refunds recorded in FY", money(snapshot.revenue.refundsRecordedInPeriod)))

        val settlements = snapshot.revenue.bookingSettlements
        reportContainer.addView(subTitle("Stored OTA / Source Settlement Facts"))
        reportContainer.addView(metric("Commission", money(settlements.sumOf { it.storedCommissionAmount })))
        reportContainer.addView(metric("Commission GST", money(settlements.sumOf { it.storedCommissionTax })))
        reportContainer.addView(metric("Source Fees", money(settlements.sumOf { it.storedSourceFee })))
        reportContainer.addView(metric("TDS", money(settlements.sumOf { it.storedTdsAmount })))
        reportContainer.addView(metric("TCS", money(settlements.sumOf { it.storedTcsAmount })))
        reportContainer.addView(metric("Expected Payout", money(settlements.sumOf { it.storedExpectedPayout })))
        reportContainer.addView(note(
            "Settlement figures above are stored full-booking values for bookings having room-night financial lines in this financial year. No commission/TDS/TCS formula is rerun."
        ))

        reportContainer.addView(sectionTitle("Occupancy — Financial Year"))
        reportContainer.addView(metric("Rooms", snapshot.occupancy.roomCount.toString()))
        reportContainer.addView(metric("Occupancy", "${snapshot.occupancy.occupancyPercent}%"))
        reportContainer.addView(metric(
            "Occupied / Available Nights",
            "${snapshot.occupancy.occupiedRoomNights} / ${snapshot.occupancy.availableRoomNights}"
        ))
        reportContainer.addView(metric(
            "Average Occupied Rooms / Day",
            oneDecimal(snapshot.occupancy.averageOccupiedRoomsPerDay)
        ))

        reportContainer.addView(sectionTitle("Current Balance / Receivables"))
        reportContainer.addView(metric("Total Receivable", money(snapshot.balance.totalReceivable)))
        reportContainer.addView(metric("Actually Received", money(snapshot.balance.totalReceived)))
        reportContainer.addView(metric("Outstanding", money(snapshot.balance.totalOutstanding)))
        reportContainer.addView(metric("OTA Outstanding", money(snapshot.balance.otaOutstanding)))
        reportContainer.addView(metric("Guest Outstanding", money(snapshot.balance.guestOutstanding)))
        reportContainer.addView(metric("Open Bookings", snapshot.balance.openBookingCount.toString()))
        reportContainer.addView(note(
            "Received is reconstructed from actual payment rows. Refunds and corrections reduce received money. This screen cannot record or change a payment."
        ))

        // DEBUG-only reconciliation. This does not alter balance calculations.
        // It explains why SUM(booking outstanding) can be higher than
        // total receivable minus total received when one booking has received
        // more money than its own reporting receivable.
        val excessRows = snapshot.balance.bookings.mapNotNull { row ->
            val excess = (row.received - row.receivable).coerceAtLeast(0.0)
            if (excess > 0.001) row to excess else null
        }
        val receivedAboveReceivable = excessRows.sumOf { it.second }
        val arithmeticOutstanding =
            snapshot.balance.totalReceivable - snapshot.balance.totalReceived
        val reconciliationDifference =
            snapshot.balance.totalOutstanding - arithmeticOutstanding

        reportContainer.addView(sectionTitle("Balance Reconciliation Audit"))
        reportContainer.addView(metric(
            "Receivable âˆ’ Received",
            money(arithmeticOutstanding)
        ))
        reportContainer.addView(metric(
            "Booking-wise Outstanding",
            money(snapshot.balance.totalOutstanding)
        ))
        reportContainer.addView(metric(
            "Difference",
            money(reconciliationDifference)
        ))
        reportContainer.addView(metric(
            "Received Above Receivable",
            money(receivedAboveReceivable)
        ))
        reportContainer.addView(note(
            "Outstanding is calculated booking by booking. Excess money received on one booking is not used to reduce another booking's balance. The diagnostic rows below identify any booking where received money is above that booking's reporting receivable."
        ))

        if (excessRows.isNotEmpty()) {
            reportContainer.addView(subTitle("Bookings causing the difference"))
            excessRows
                .sortedByDescending { it.second }
                .forEach { (row, excess) ->
                    val sourceLabel = row.sourceName
                        ?.takeIf { it.isNotBlank() }
                        ?: row.sourceType
                    reportContainer.addView(metric(
                        "${row.guestName} â€¢ $sourceLabel",
                        "+${money(excess)}"
                    ))
                    reportContainer.addView(note(
                        "Booking ${row.bookingRemoteId}: receivable ${money(row.receivable)}, received ${money(row.received)}, above receivable ${money(excess)}."
                    ))
                }
        } else {
            reportContainer.addView(note(
                "No booking in this property scope has received money above its reporting receivable."
            ))
        }

        reportContainer.addView(sectionTitle("Read-only Audit Counts"))
        reportContainer.addView(metric("Scoped Rooms", snapshot.dataset.rooms.size.toString()))
        reportContainer.addView(metric("Scoped Bookings", snapshot.dataset.bookings.size.toString()))
        reportContainer.addView(metric("Scoped Financial Lines", snapshot.dataset.financialLines.size.toString()))
        reportContainer.addView(metric("Scoped Payment Rows", snapshot.dataset.payments.size.toString()))
    }

    private fun allDataLoaded(): Boolean {
        return propertiesLoaded &&
            roomsLoaded &&
            bookingsLoaded &&
            financialLinesLoaded &&
            paymentsLoaded
    }

    private fun loadingText(): String {
        val pending = buildList {
            if (!propertiesLoaded) add("properties")
            if (!roomsLoaded) add("rooms")
            if (!bookingsLoaded) add("bookings")
            if (!financialLinesLoaded) add("financial lines")
            if (!paymentsLoaded) add("payments")
        }
        return pending.joinToString(", ")
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 19f
            setTextColor(Color.rgb(25, 25, 25))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(8))
        }
    }

    private fun subTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.rgb(55, 55, 55))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(5))
        }
    }

    private fun metric(label: String, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setBackgroundColor(Color.rgb(248, 248, 248))

            addView(TextView(this@PropertyReportingDebugActivity).apply {
                text = label
                textSize = 14f
                setTextColor(Color.rgb(60, 60, 60))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(TextView(this@PropertyReportingDebugActivity).apply {
                text = value
                textSize = 15f
                setTextColor(Color.rgb(20, 20, 20))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.END
            })
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(2))
            }
        }
    }

    private fun note(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.rgb(105, 105, 105))
            setPadding(dp(6), dp(8), dp(6), dp(4))
        }
    }

    private fun money(value: Double): String {
        return "₹${moneyFormatter.format(value.roundToInt())}"
    }

    private fun oneDecimal(value: Double): String {
        return String.format(Locale.getDefault(), "%.1f", value)
    }

    private fun formatRange(startMillis: Long, endMillis: Long): String {
        val inclusiveEnd = endMillis - BusinessDates.DAY_MILLIS
        return "${dateFormatter.format(Date(startMillis))} – ${dateFormatter.format(Date(inclusiveEnd))}"
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    companion object {
        private const val ALL_PROPERTIES_KEY = "__ALL_PROPERTIES__"
        private const val LEGACY_PROPERTY_KEY = "__LEGACY_PROPERTY__"
    }
}
