package com.example.bookingregister.ui.payments

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bookingregister.data.repository.BookingRepository
import com.example.bookingregister.data.repository.SaveResult
import com.example.bookingregister.accounting.domain.PaymentCorrectionPolicy
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingPaymentCategory
import com.example.bookingregister.data.entities.BookingPaymentEntity
import com.example.bookingregister.data.entities.BookingPaymentType
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.booking.domain.CancellationSettlementOutcome
import com.example.bookingregister.booking.domain.CancellationSettlementPolicy
import com.example.bookingregister.booking.domain.CancellationSettlementStatus
import com.example.bookingregister.booking.domain.DirectCancellationChoice
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class PaymentsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HOTEL_REMOTE_ID = "hotel_remote_id"
    }

    private lateinit var repository: BookingRepository
    private lateinit var totalBalanceValue: TextView
    private lateinit var totalReceivableValue: TextView
    private lateinit var totalPaidValue: TextView
    private lateinit var listContainer: LinearLayout

    private val rooms = mutableListOf<RoomEntity>()
    private val bookings = mutableListOf<BookingEntity>()
    private val payments = mutableListOf<BookingPaymentEntity>()
    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hotelRemoteId = intent.getStringExtra(EXTRA_HOTEL_REMOTE_ID)
        if (hotelRemoteId.isNullOrBlank()) {
            finish()
            return
        }
        repository = BookingRepository(applicationContext, lifecycleScope, hotelRemoteId)
        setContentView(buildContentView())

        repository.observeRooms().observe(this) { updated ->
            rooms.clear()
            rooms.addAll(updated)
            renderPayments()
        }
        repository.observeBookings().observe(this) { updated ->
            bookings.clear()
            bookings.addAll(updated)
            renderPayments()
        }
        repository.observePayments().observe(this) { updated ->
            payments.clear()
            payments.addAll(updated)
            renderPayments()
        }
    }

    private fun buildContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        root.addView(toolbar())

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(22))
        }
        scroll.addView(content)

        content.addView(summaryRow())
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listContainer)

        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        return root
    }

    private fun toolbar(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(Color.parseColor("#FF5A5F"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56))

            addView(TextView(this@PaymentsActivity).apply {
                text = "<"
                textSize = 34f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.MATCH_PARENT))

            addView(TextView(this@PaymentsActivity).apply {
                text = "Receivables"
                textSize = 22f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun summaryRow(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            totalBalanceValue = statCard(this, "Open", "#FFF0F0")
            totalReceivableValue = statCard(this, "Revenue", "#EAF7EE")
            totalPaidValue = statCard(this, "Paid", "#FFF5DB")
        }
    }

    private fun statCard(parent: LinearLayout, label: String, color: String): TextView {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = roundedDrawable(Color.parseColor(color), Color.parseColor("#E5E5E5"))
        }
        card.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.rgb(70, 70, 70))
            gravity = Gravity.CENTER
        })
        val value = TextView(this).apply {
            text = "Rs 0"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(20, 20, 20))
            gravity = Gravity.CENTER
        }
        card.addView(value)
        parent.addView(card, LinearLayout.LayoutParams(0, dp(84), 1f).apply {
            setMargins(dp(4), 0, dp(4), 0)
        })
        return value
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(25, 25, 25))
            setPadding(0, dp(18), 0, dp(8))
        }
    }

    private fun renderPayments() {
        if (!::listContainer.isInitialized) return
        val activeBookings = bookings.filter {
            !it.isDeleted && it.bookingStatus != BookingStatus.CANCELLED
        }
        val openBookings = activeBookings.filter { it.balance > 0.0 }
        val otaOpenBookings = openBookings.filter { it.sourceType == BookingSourceType.OTA }
        val guestOpenBookings = openBookings.filter { it.sourceType != BookingSourceType.OTA }
        val directCancellationWork = bookings.filter { booking ->
            !booking.isDeleted &&
                booking.bookingStatus == BookingStatus.CANCELLED &&
                booking.sourceType != BookingSourceType.OTA &&
                (
                    booking.cancellationSettlementStatus == CancellationSettlementStatus.PENDING ||
                        CancellationSettlementPolicy.refundDue(
                            booking.cancellationApprovedRefundAmount,
                            booking.cancellationRefundBaselineAmount,
                            payments.filter { it.bookingRemoteId == booking.remoteId }
                        ) > 0.001
                    )
        }

        totalBalanceValue.text = formatMoney(openBookings.sumOf { it.balance })
        totalReceivableValue.text = formatMoney(activeBookings.sumOf { it.roomRevenue.takeIf { amount -> amount > 0.0 } ?: it.receivable })
        totalPaidValue.text = formatMoney(activeBookings.sumOf { it.paid })

        listContainer.removeAllViews()

        if (otaOpenBookings.isNotEmpty()) {
            listContainer.addView(sectionTitle("OTA Receivables"))
            otaOpenBookings
                .groupBy { it.sourceRemoteId.orEmpty() to it.sourceName.orEmpty().ifBlank { "OTA" } }
                .toList()
                .sortedBy { it.first.second.lowercase(Locale.getDefault()) }
                .forEach { (key, groupBookings) ->
                    listContainer.addView(sourceCard(key.first, key.second, groupBookings))
                }
        }

        if (guestOpenBookings.isNotEmpty()) {
            listContainer.addView(sectionTitle("Guest Balances"))
            guestOpenBookings
                .sortedWith(compareByDescending<BookingEntity> { it.balance }.thenBy { it.checkInMillis })
                .forEach { booking -> listContainer.addView(folioCard(booking)) }
        }

        if (openBookings.isEmpty() && directCancellationWork.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "No pending receivables."
                textSize = 15f
                setTextColor(Color.rgb(90, 90, 90))
                setPadding(0, dp(14), 0, dp(14))
            })
        }

        if (directCancellationWork.isNotEmpty()) {
            listContainer.addView(sectionTitle("Direct Booking Cancellation Settlements"))
            directCancellationWork
                .sortedByDescending { it.cancelledAt ?: it.updatedAt }
                .forEach { booking -> listContainer.addView(cancellationSettlementCard(booking)) }
        }
    }

    private fun cancellationSettlementCard(booking: BookingEntity): View {
        val bookingPayments = payments.filter {
            !it.isDeleted && it.bookingRemoteId == booking.remoteId
        }
        val netPaid = CancellationSettlementPolicy.netPaid(bookingPayments)
        val refundDue = CancellationSettlementPolicy.refundDue(
            booking.cancellationApprovedRefundAmount,
            booking.cancellationRefundBaselineAmount,
            bookingPayments
        )
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedDrawable(Color.parseColor("#FFF8E1"), Color.parseColor("#E8C96A"))

            addView(TextView(this@PaymentsActivity).apply {
                text = booking.guestName.ifBlank { "Guest" }
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(25, 25, 25))
            })
            addView(TextView(this@PaymentsActivity).apply {
                text = when (booking.cancellationSettlementStatus) {
                    CancellationSettlementStatus.PENDING ->
                        "Decision pending • Net paid ${formatMoney(netPaid)}"
                    else ->
                        "${booking.cancellationSettlementOutcome.displayCancellationOutcome()} • " +
                            "Approved ${formatMoney(booking.cancellationApprovedRefundAmount)} • " +
                            "Refund due ${formatMoney(refundDue)}"
                }
                textSize = 14f
                setTextColor(Color.rgb(70, 60, 30))
                setPadding(0, dp(5), 0, dp(8))
            })
            addView(MaterialButton(this@PaymentsActivity).apply {
                text = if (booking.cancellationSettlementStatus == CancellationSettlementStatus.PENDING) {
                    "Decide Settlement"
                } else {
                    "Record Approved Refund"
                }
                isAllCaps = false
                setOnClickListener {
                    if (booking.cancellationSettlementStatus == CancellationSettlementStatus.PENDING) {
                        showCancellationDecisionDialog(booking, netPaid)
                    } else {
                        showPaymentEntryDialog(
                            booking,
                            BookingPaymentType.REFUND,
                            preferredRefundAmount = refundDue
                        )
                    }
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
        }.also { card ->
            card.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
        }
    }

    private fun showCancellationDecisionDialog(booking: BookingEntity, netPaid: Double) {
        val options = arrayOf(
            "Cancel without refund",
            "Approve partial refund",
            "Approve full refund (${formatMoney(netPaid)})"
        )
        var selected = 0
        val partialInput = EditText(this).apply {
            hint = "Partial refund amount"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            visibility = View.GONE
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            addView(TextView(this@PaymentsActivity).apply {
                text = "Paid after previous refunds: ${formatMoney(netPaid)}"
                setPadding(0, 0, 0, dp(8))
            })
            addView(partialInput)
        }
        AlertDialog.Builder(this)
            .setTitle("Cancellation Settlement")
            .setSingleChoiceItems(options, selected) { _, which ->
                selected = which
                partialInput.visibility = if (which == 1) View.VISIBLE else View.GONE
            }
            .setView(container)
            .setPositiveButton("Record Decision", null)
            .setNegativeButton("Keep Pending", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val choice = when (selected) {
                            1 -> DirectCancellationChoice.PARTIAL_REFUND
                            2 -> DirectCancellationChoice.FULL_REFUND
                            else -> DirectCancellationChoice.NO_REFUND
                        }
                        val partialAmount = partialInput.text.toString().toDoubleOrNull()
                        if (choice == DirectCancellationChoice.PARTIAL_REFUND &&
                            (partialAmount == null || partialAmount <= 0.0 || partialAmount >= netPaid)
                        ) {
                            partialInput.error = "Enter an amount above zero and below ${formatMoney(netPaid)}"
                            return@setOnClickListener
                        }
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                        lifecycleScope.launch {
                            when (val result = repository.decideDirectCancellationSettlement(
                                booking,
                                choice,
                                partialAmount
                            )) {
                                is SaveResult.Success -> {
                                    Toast.makeText(
                                        this@PaymentsActivity,
                                        "Cancellation decision recorded",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    dialog.dismiss()
                                }
                                is SaveResult.Conflict ->
                                    Toast.makeText(this@PaymentsActivity, result.message, Toast.LENGTH_LONG).show()
                                is SaveResult.Error ->
                                    Toast.makeText(this@PaymentsActivity, result.message, Toast.LENGTH_LONG).show()
                            }
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun sourceCard(sourceRemoteId: String, sourceName: String, sourceBookings: List<BookingEntity>): View {
        val balance = sourceBookings.sumOf { it.balance }
        val revenue = sourceBookings.sumOf { it.roomRevenue.takeIf { amount -> amount > 0.0 } ?: it.receivable }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedDrawable(Color.parseColor("#EAF7EE"), Color.parseColor("#CFE8D8"))

            addView(TextView(this@PaymentsActivity).apply {
                text = sourceName
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(25, 25, 25))
            })
            addView(TextView(this@PaymentsActivity).apply {
                text = "${sourceBookings.size} booking(s)  |  Revenue ${formatMoney(revenue)}"
                textSize = 13f
                setTextColor(Color.rgb(80, 90, 84))
                setPadding(0, dp(4), 0, dp(8))
            })
            addView(TextView(this@PaymentsActivity).apply {
                text = "OTA Receivable ${formatMoney(balance)}"
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(20, 70, 38))
            })
            addView(MaterialButton(this@PaymentsActivity).apply {
                text = "Record Payout"
                isAllCaps = false
                setOnClickListener { showSourcePaymentDialog(sourceRemoteId, sourceName, balance) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply {
                setMargins(0, dp(10), 0, 0)
            })
        }.also { card ->
            card.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
        }
    }

    private fun folioCard(booking: BookingEntity): View {
        val roomNames = rooms
            .filter { booking.roomRemoteIds.contains(it.remoteId) }
            .joinToString(", ") { it.roomName }
            .ifBlank { "Room not assigned" }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedDrawable(Color.WHITE, Color.parseColor("#E0E0E0"))
            setOnClickListener { showPaymentDialog(booking) }

            addView(TextView(this@PaymentsActivity).apply {
                text = booking.guestName.ifBlank { "Guest" }
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(25, 25, 25))
            })
            addView(TextView(this@PaymentsActivity).apply {
                text = "$roomNames  |  ${dateFormat.format(Date(booking.checkInMillis))} - ${dateFormat.format(Date(booking.checkOutMillis))}"
                textSize = 13f
                setTextColor(Color.rgb(90, 90, 90))
                setPadding(0, dp(4), 0, dp(8))
            })
            addView(TextView(this@PaymentsActivity).apply {
                text = "Receivable ${formatMoney(booking.receivable)}   Paid ${formatMoney(booking.paid)}   Balance ${formatMoney(booking.balance)}"
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(40, 40, 40))
            })
            addView(MaterialButton(this@PaymentsActivity).apply {
                text = "Payment / Refund / Correction"
                isAllCaps = false
                setOnClickListener { showPaymentDialog(booking) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply {
                setMargins(0, dp(10), 0, 0)
            })
        }.also { card ->
            card.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
        }
    }

    private fun showSourcePaymentDialog(sourceRemoteId: String, sourceName: String, balance: Double) {
        val amountInput = EditText(this).apply {
            hint = "Payout amount"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setText(balance.roundToInt().toString())
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Record $sourceName Payout")
            .setMessage("Open receivable: ${formatMoney(balance)}")
            .setView(amountInput)
            .setPositiveButton("Save") { _, _ ->
                val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                if (amount <= 0.0) {
                    Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                saveSourcePayment(sourceRemoteId, sourceName, amount)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPaymentDialog(booking: BookingEntity) {
        val options = arrayOf("Add Payment", "Add Refund", "Add Correction")
        AlertDialog.Builder(this)
            .setTitle("Payment Action")
            .setItems(options) { _, which ->
                when (which) {
                    1 -> showPaymentEntryDialog(booking, BookingPaymentType.REFUND)
                    2 -> showPaymentEntryDialog(booking, BookingPaymentType.ADJUSTMENT)
                    else -> showPaymentEntryDialog(booking, BookingPaymentType.PAYMENT)
                }
            }
            .show()
    }

    private fun showPaymentEntryDialog(
        booking: BookingEntity,
        paymentType: String,
        preferredRefundAmount: Double? = null
    ) {
        val reversiblePayments = payments.filter {
            it.bookingRemoteId == booking.remoteId &&
                !it.isDeleted &&
                it.paymentType in setOf(BookingPaymentType.PAYMENT, BookingPaymentType.ADVANCE)
        }.filter { original ->
            PaymentCorrectionPolicy.remainingCorrectable(original, payments) > 0.001
        }.sortedByDescending { it.paymentMillis }
        if (paymentType == BookingPaymentType.REFUND && reversiblePayments.isEmpty()) {
            Toast.makeText(this, "No refundable payment is available", Toast.LENGTH_LONG).show()
            return
        }
        if (paymentType == BookingPaymentType.ADJUSTMENT && reversiblePayments.isEmpty()) {
            Toast.makeText(this, "No payment is available to correct", Toast.LENGTH_LONG).show()
            return
        }
        val amountInput = EditText(this).apply {
            hint = when (paymentType) {
                BookingPaymentType.REFUND -> "Refund amount"
                BookingPaymentType.ADJUSTMENT -> "Correction amount"
                else -> "Payment amount"
            }
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            val firstRemaining = reversiblePayments.firstOrNull()
                ?.let { PaymentCorrectionPolicy.remainingCorrectable(it, payments) }
                ?: 0.0
            val defaultAmount = when (paymentType) {
                BookingPaymentType.REFUND -> preferredRefundAmount
                    ?.coerceAtMost(firstRemaining)
                    ?: (booking.paid - booking.receivable).coerceAtLeast(0.0)
                BookingPaymentType.ADJUSTMENT -> firstRemaining
                else -> booking.balance
            }
            setText(if (defaultAmount > 0.0) defaultAmount.roundToInt().toString() else "")
            setSelection(text.length)
            isEnabled = paymentType != BookingPaymentType.ADJUSTMENT
        }
        val categorySpinner = Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(
                this@PaymentsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                BookingPaymentCategory.selectable
            )
            visibility = if (paymentType == BookingPaymentType.PAYMENT) View.VISIBLE else View.GONE
        }
        val originalPaymentSpinner = Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(
                this@PaymentsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                reversiblePayments.map { payment ->
                    val remaining = PaymentCorrectionPolicy.remainingCorrectable(payment, payments)
                    val displayAmount = if (paymentType == BookingPaymentType.ADJUSTMENT) remaining else payment.amount
                    "${formatMoney(displayAmount)} • ${payment.paymentCategory} • ${dateFormat.format(Date(payment.paymentMillis))}"
                }
            )
            visibility = if (paymentType in setOf(BookingPaymentType.REFUND, BookingPaymentType.ADJUSTMENT)) View.VISIBLE else View.GONE
        }
        if (paymentType == BookingPaymentType.ADJUSTMENT) {
            originalPaymentSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    reversiblePayments.getOrNull(position)?.let { original ->
                        val remaining = PaymentCorrectionPolicy.remainingCorrectable(original, payments)
                        amountInput.setText(remaining.roundToInt().toString())
                        amountInput.setSelection(amountInput.text.length)
                    }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        }
        val noteInput = EditText(this).apply {
            hint = if (paymentType == BookingPaymentType.PAYMENT) "Note optional" else "Reason required"
            setSingleLine(false)
            minLines = 2
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            if (paymentType == BookingPaymentType.ADJUSTMENT) {
                addView(TextView(this@PaymentsActivity).apply { text = "Payment to correct" })
                addView(originalPaymentSpinner)
                addView(TextView(this@PaymentsActivity).apply {
                    text = "The selected payment will be fully reversed. If the intended amount was different, add the correct payment afterwards."
                    textSize = 12f
                    setTextColor(Color.rgb(138, 90, 0))
                    setPadding(0, dp(8), 0, dp(8))
                })
            }
            addView(amountInput)
            if (paymentType == BookingPaymentType.REFUND) {
                addView(TextView(this@PaymentsActivity).apply { text = "Original payment" })
                addView(originalPaymentSpinner)
            }
            if (paymentType == BookingPaymentType.PAYMENT) {
                addView(TextView(this@PaymentsActivity).apply {
                    text = "Payment For"
                    textSize = 12f
                    setTextColor(Color.rgb(90, 90, 90))
                    setPadding(0, dp(8), 0, 0)
                })
                addView(categorySpinner)
            }
            addView(noteInput)
        }

        AlertDialog.Builder(this)
            .setTitle(paymentType.displayPaymentType())
            .setMessage("${booking.guestName}\nBalance: ${formatMoney(booking.balance)}")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                if (amount <= 0.0) {
                    Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val note = noteInput.text.toString().trim()
                if (paymentType != BookingPaymentType.PAYMENT && note.isBlank()) {
                    Toast.makeText(this, "Please enter a reason", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val category = if (paymentType == BookingPaymentType.PAYMENT) categorySpinner.selectedItem as String else BookingPaymentCategory.STAY
                val originalPaymentRemoteId = if (paymentType in setOf(BookingPaymentType.REFUND, BookingPaymentType.ADJUSTMENT)) {
                    reversiblePayments.getOrNull(originalPaymentSpinner.selectedItemPosition)?.remoteId
                } else null
                savePayment(booking, amount, paymentType, category, note, originalPaymentRemoteId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveSourcePayment(sourceRemoteId: String, sourceName: String, amount: Double) {
        lifecycleScope.launch {
            when (val result = repository.addSourcePayment(sourceRemoteId, sourceName, amount)) {
                is SaveResult.Success -> Toast.makeText(this@PaymentsActivity, "Payout saved", Toast.LENGTH_SHORT).show()
                is SaveResult.Conflict -> Toast.makeText(this@PaymentsActivity, result.message, Toast.LENGTH_LONG).show()
                is SaveResult.Error -> Toast.makeText(this@PaymentsActivity, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun savePayment(booking: BookingEntity, amount: Double, paymentType: String, paymentCategory: String, note: String?, originalPaymentRemoteId: String? = null) {
        lifecycleScope.launch {
            when (val result = repository.addBookingPayment(booking, amount, paymentType = paymentType, paymentCategory = paymentCategory, note = note, originalPaymentRemoteId = originalPaymentRemoteId)) {
                is SaveResult.Success -> Toast.makeText(this@PaymentsActivity, "${paymentType.displayPaymentType()} saved", Toast.LENGTH_SHORT).show()
                is SaveResult.Conflict -> Toast.makeText(this@PaymentsActivity, result.message, Toast.LENGTH_LONG).show()
                is SaveResult.Error -> Toast.makeText(this@PaymentsActivity, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun String.displayPaymentType(): String {
        return when (this) {
            BookingPaymentType.REFUND -> "Refund"
            BookingPaymentType.ADJUSTMENT -> "Correction"
            else -> "Payment"
        }
    }

    private fun String?.displayCancellationOutcome(): String = when (this) {
        CancellationSettlementOutcome.NO_REFUND -> "No refund"
        CancellationSettlementOutcome.PARTIAL_REFUND -> "Partial refund"
        CancellationSettlementOutcome.FULL_REFUND -> "Full refund"
        else -> "Settlement decided"
    }

    private fun formatMoney(amount: Double): String {
        return "Rs ${String.format(Locale.getDefault(), "%,.0f", amount)}"
    }

    private fun roundedDrawable(fill: Int, stroke: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(8).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
