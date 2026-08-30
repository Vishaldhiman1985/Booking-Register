package com.example.bookingregister.ui.reporting

import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.bookingregister.reporting.property.OtaSettlementSelectionPolicy
import com.example.bookingregister.reporting.property.PropertyBalanceBookingFacts

object OtaSettlementSelectionDialog {
    fun show(
        activity: AppCompatActivity,
        propertyName: String,
        sourceName: String,
        bookings: List<PropertyBalanceBookingFacts>,
        moneyFormatter: (Double) -> String,
        onConfirmed: (
            selected: List<PropertyBalanceBookingFacts>,
            settlementReference: String?,
            note: String?
        ) -> Unit
    ) {
        if (bookings.isEmpty()) return

        val selectedIds = linkedSetOf<String>()
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), dp(activity, 8))
        }

        root.addView(TextView(activity).apply {
            text = "$propertyName\n$sourceName"
            textSize = 15f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.rgb(35, 35, 35))
            setPadding(0, 0, 0, dp(activity, 10))
        })

        val selectedSummary = TextView(activity).apply {
            text = "No bookings selected"
            textSize = 15f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.rgb(35, 90, 55))
            setPadding(0, 0, 0, dp(activity, 10))
        }
        root.addView(selectedSummary)

        val rowsContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val rowsScroll = ScrollView(activity).apply {
            addView(rowsContainer)
        }
        root.addView(
            rowsScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(activity, 310)
            )
        )

        val referenceInput = EditText(activity).apply {
            hint = "OTA payout / bank reference (optional)"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        root.addView(referenceInput)

        val noteInput = EditText(activity).apply {
            hint = "Note (optional)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2
            maxLines = 3
        }
        root.addView(noteInput)

        val checkBoxes = mutableListOf<CheckBox>()
        var updatePositiveButton: (() -> Unit)? = null

        fun selectedRows(): List<PropertyBalanceBookingFacts> =
            bookings.filter { it.bookingRemoteId in selectedIds }

        fun updateSummary() {
            val selected = selectedRows()
            if (selected.isEmpty()) {
                selectedSummary.text = "No bookings selected"
            } else {
                val summary = OtaSettlementSelectionPolicy.summarize(selected)
                selectedSummary.text =
                    "${summary.bookingCount} booking(s) selected  |  ${moneyFormatter(summary.totalAmount)}"
            }
        }

        bookings.forEach { row ->
            val checkBox = CheckBox(activity).apply {
                val shortId = row.bookingRemoteId.takeLast(8)
                text = "${row.guestName.ifBlank { "Guest" }}\n" +
                    "Pending ${moneyFormatter(row.outstanding)}  |  Booking â€¦$shortId"
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(activity, 5), 0, dp(activity, 5))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedIds.add(row.bookingRemoteId)
                    } else {
                        selectedIds.remove(row.bookingRemoteId)
                    }
                    updateSummary()
                    updatePositiveButton?.invoke()
                }
            }
            checkBoxes += checkBox
            rowsContainer.addView(checkBox)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Select $sourceName paid bookings")
            .setView(root)
            .setPositiveButton("Continue", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            updatePositiveButton = {
                positive.isEnabled = selectedIds.isNotEmpty()
            }
            updatePositiveButton?.invoke()

            positive.setOnClickListener {
                val selected = selectedRows()
                val summary = runCatching {
                    OtaSettlementSelectionPolicy.summarize(selected)
                }.getOrElse { error ->
                    selectedSummary.text = error.message ?: "Invalid selection."
                    return@setOnClickListener
                }

                val reference = referenceInput.text.toString().trim().ifEmpty { null }
                val note = noteInput.text.toString().trim().ifEmpty { null }

                AlertDialog.Builder(activity)
                    .setTitle("Confirm OTA Settlement")
                    .setMessage(
                        "Property: $propertyName\n" +
                            "OTA: $sourceName\n" +
                            "Selected bookings: ${summary.bookingCount}\n" +
                            "Amount being recorded: ${moneyFormatter(summary.totalAmount)}\n\n" +
                            "This will create actual payment records for these bookings. " +
                            "Confirm only if the selected bookings and total exactly match the OTA settlement statement."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Confirm & Record") { _, _ ->
                        dialog.dismiss()
                        onConfirmed(selected, reference, note)
                    }
                    .show()
            }
        }

        dialog.show()
    }

    private fun dp(activity: AppCompatActivity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}