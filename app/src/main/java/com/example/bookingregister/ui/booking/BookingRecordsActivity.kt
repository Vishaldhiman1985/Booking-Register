package com.example.bookingregister.ui.booking

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.data.repository.BookingRepository
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BookingRecordsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HOTEL_REMOTE_ID = "hotel_remote_id"
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }

    private lateinit var repository: BookingRepository
    private lateinit var listContainer: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var countView: TextView

    private val allBookings = mutableListOf<BookingEntity>()
    private val roomsById = mutableMapOf<String, RoomEntity>()

    private var activeFilter: FilterMode = FilterMode.ALL

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val moneyFormat = java.text.NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hotelRemoteId = intent.getStringExtra(EXTRA_HOTEL_REMOTE_ID)
        if (hotelRemoteId.isNullOrBlank()) {
            finish()
            return
        }

        repository = BookingRepository(applicationContext, lifecycleScope, hotelRemoteId)
        setContentView(buildRoot())

        repository.observeRooms().observe(this) { rooms ->
            roomsById.clear()
            rooms.forEach { room ->
                roomsById[room.remoteId] = room
            }
            render()
        }

        repository.observeBookings().observe(this) { bookings ->
            allBookings.clear()
            allBookings.addAll(bookings)
            render()
        }
    }

    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        root.addView(toolbar())
        root.addView(searchSection())

        val scroll = ScrollView(this)
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(24))
        }
        scroll.addView(listContainer)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        return root
    }

    private fun toolbar(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), statusBarHeight(), dp(12), 0)
            setBackgroundColor(Color.parseColor("#FF5A5F"))

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58) + statusBarHeight()
            )

            addView(TextView(this@BookingRecordsActivity).apply {
                text = "←"
                textSize = 26f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(52), LinearLayout.LayoutParams.MATCH_PARENT))

            addView(TextView(this@BookingRecordsActivity).apply {
                text = "Booking Records"
                textSize = 22f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun searchSection(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            setBackgroundColor(Color.WHITE)

            searchBox = EditText(this@BookingRecordsActivity).apply {
                hint = "Search guest, mobile, room, status..."
                textSize = 14f
                setSingleLine(true)
                setPadding(dp(12), 0, dp(12), 0)
                background = roundedDrawable(Color.parseColor("#F7F7F7"), Color.parseColor("#DDDDDD"), 10)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        render()
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
            }

            addView(
                searchBox,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(46)
                )
            )

            addView(filterRow())

            countView = TextView(this@BookingRecordsActivity).apply {
                textSize = 13f
                setTextColor(Color.parseColor("#666666"))
                setPadding(0, dp(8), 0, 0)
            }
            addView(countView)
        }
    }

    private fun filterRow(): View {
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false

            val row = LinearLayout(this@BookingRecordsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }

            FilterMode.values().forEach { mode ->
                row.addView(filterButton(mode))
            }

            addView(row)
        }
    }

    private fun filterButton(mode: FilterMode): View {
        return MaterialButton(this).apply {
            text = mode.label
            isAllCaps = false
            textSize = 12f
            setOnClickListener {
                activeFilter = mode
                render()
            }
        }.also { button ->
            button.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(40)
            ).apply {
                marginEnd = dp(8)
            }
        }
    }

    private fun render() {
        if (!::listContainer.isInitialized) return

        listContainer.removeAllViews()

        val query = searchBox.text?.toString()?.trim().orEmpty().lowercase(Locale.getDefault())

        val filtered = allBookings
            .asSequence()
            .filter { booking -> filterByMode(booking) }
            .filter { booking -> matchesSearch(booking, query) }
            .sortedWith(
                compareByDescending<BookingEntity> { it.checkInMillis }
                    .thenBy { it.guestName.lowercase(Locale.getDefault()) }
            )
            .toList()

        countView.text = "${filtered.size} booking record${if (filtered.size == 1) "" else "s"}"

        if (filtered.isEmpty()) {
            listContainer.addView(emptyView())
            return
        }

        filtered.forEach { booking ->
            listContainer.addView(bookingCard(booking))
        }
    }

    private fun filterByMode(booking: BookingEntity): Boolean {
        val todayStart = startOfToday()
        val tomorrowStart = todayStart + DAY_MILLIS

        return when (activeFilter) {
            FilterMode.ALL -> true
            FilterMode.TODAY -> booking.checkInMillis < tomorrowStart && booking.checkOutMillis > todayStart
            FilterMode.UPCOMING -> booking.checkInMillis >= tomorrowStart
            FilterMode.PAST -> booking.checkOutMillis <= todayStart
            FilterMode.IN_HOUSE -> booking.bookingStatus == BookingStatus.CHECKED_IN
            FilterMode.UNSYNCED -> booking.syncState != "SYNCED"
        }
    }

    private fun matchesSearch(booking: BookingEntity, query: String): Boolean {
        if (query.isBlank()) return true

        val roomText = roomDisplay(booking).lowercase(Locale.getDefault())
        val searchable = listOfNotNull(
            booking.guestName,
            booking.guestMobile,
            booking.sourceName,
            booking.sourceType,
            booking.bookingStatus,
            booking.paymentStatus,
            booking.pricingStatus,
            booking.syncState,
            roomText,
            booking.roomRemoteIds.joinToString(" ")
        ).joinToString(" ").lowercase(Locale.getDefault())

        return searchable.contains(query)
    }

    private fun bookingCard(booking: BookingEntity): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedDrawable(Color.WHITE, Color.parseColor("#E5E5E5"), 12)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }

            addView(TextView(this@BookingRecordsActivity).apply {
                text = booking.guestName.ifBlank { "Unnamed Guest" }
                textSize = 17f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(Color.parseColor("#222222"))
            })

            addView(infoLine("Mobile", booking.guestMobile?.takeIf { it.isNotBlank() } ?: "-"))
            addView(infoLine("Rooms", roomDisplay(booking)))
            addView(infoLine("Stay", "${formatDate(booking.checkInMillis)} → ${formatDate(booking.checkOutMillis)}"))
            addView(infoLine("Status", booking.bookingStatus))
            addView(infoLine("Source", booking.sourceName?.takeIf { it.isNotBlank() } ?: booking.sourceType))
            addView(infoLine("Pricing", booking.pricingStatus))
            addView(infoLine("Payment", booking.paymentStatus))
            addView(infoLine("Gross", money(booking.grossCharges)))
            addView(infoLine("Paid", money(booking.paid)))
            addView(infoLine("Balance", money(booking.balance)))
            addView(infoLine("Sync", syncText(booking)))

            booking.lastSyncError?.takeIf { it.isNotBlank() }?.let { error ->
                addView(TextView(this@BookingRecordsActivity).apply {
                    text = "Sync error: $error"
                    textSize = 12f
                    setTextColor(Color.parseColor("#B00020"))
                    setPadding(0, dp(6), 0, 0)
                })
            }
        }
    }

    private fun infoLine(label: String, value: String): View {
        return TextView(this).apply {
            text = "$label: $value"
            textSize = 13f
            setTextColor(Color.parseColor("#555555"))
            setPadding(0, dp(4), 0, 0)
        }
    }

    private fun roomDisplay(booking: BookingEntity): String {
        if (booking.roomRemoteIds.isEmpty()) return "Room record unavailable"

        return booking.roomRemoteIds.joinToString(", ") { roomId ->
            val room = roomsById[roomId]
            when {
                room?.roomName?.isNotBlank() == true -> {
                    val suffix = when (room.lifecycleStatus) {
                        "DISABLED" -> " (Disabled)"
                        "RETIRED" -> " (Retired)"
                        else -> ""
                    }
                    room.roomName + suffix
                }
                roomId.isNotBlank() -> "Room ID: $roomId"
                else -> "Room record unavailable"
            }
        }
    }

    private fun syncText(booking: BookingEntity): String {
        return when (booking.syncState) {
            "SYNCED" -> "Synced"
            "PENDING" -> "Pending"
            "FAILED" -> "Failed"
            else -> booking.syncState
        }
    }

    private fun emptyView(): View {
        return TextView(this).apply {
            text = "No booking records found."
            textSize = 15f
            setTextColor(Color.parseColor("#777777"))
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(40), dp(12), dp(40))
        }
    }

    private fun money(value: Double): String {
        return moneyFormat.format(value)
    }

    private fun formatDate(millis: Long): String {
        return dateFormat.format(Date(millis))
    }

    private fun startOfToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun statusBarHeight(): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun roundedDrawable(fill: Int, stroke: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            setStroke(dp(1), stroke)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private enum class FilterMode(val label: String) {
        ALL("All"),
        TODAY("Today"),
        UPCOMING("Upcoming"),
        PAST("Past"),
        IN_HOUSE("In-house"),
        UNSYNCED("Unsynced")
    }

}