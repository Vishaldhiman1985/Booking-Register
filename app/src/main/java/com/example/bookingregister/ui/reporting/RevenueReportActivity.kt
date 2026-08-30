package com.example.bookingregister.ui.reporting

import android.app.DatePickerDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bookingregister.common.domain.BusinessDates
import com.example.bookingregister.common.domain.DateRange
import com.example.bookingregister.reporting.property.PropertyReportRawData
import com.example.bookingregister.reporting.property.PropertyReportScope
import com.example.bookingregister.reporting.property.PropertyReportingDataSource
import com.example.bookingregister.reporting.property.PropertyReportingEngine
import com.example.bookingregister.reporting.property.OtaReceivableGroup
import com.example.bookingregister.reporting.property.OtaReceivableGrouping
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.data.repository.BookingRepository
import com.example.bookingregister.data.repository.OtaSettlementSelection
import com.example.bookingregister.data.repository.SaveResult
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.room.domain.RoomLifecyclePolicy
import com.example.bookingregister.reporting.domain.OccupancyCalculator
import com.example.bookingregister.revenue.domain.RevenueCalculator
import com.example.bookingregister.revenue.domain.RevenueLedgerBuilder
import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.revenue.domain.RevenueLedgerEntry
import com.example.bookingregister.revenue.domain.RevenueLedgerEntryType
import com.example.bookingregister.revenue.domain.RevenuePeriod
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

class RevenueReportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REPORT_KIND = "report_kind"
        const val EXTRA_HOTEL_REMOTE_ID = "hotel_remote_id"
        const val KIND_REVENUE = "revenue"
        const val KIND_OCCUPANCY = "occupancy"
        const val KIND_BALANCE = "balance"
    }

    private lateinit var dataSource: PropertyReportingDataSource
    private lateinit var hotelRemoteId: String
    private lateinit var settlementRepository: BookingRepository
    private var otaSettlementInProgress: Boolean = false
    private lateinit var content: LinearLayout
    private lateinit var customRangeButton: MaterialButton

    private var rawData: PropertyReportRawData? = null
    private val reportingEngine = PropertyReportingEngine()

    private val rooms = mutableListOf<RoomEntity>()
    private val bookings = mutableListOf<BookingEntity>()
    private val ledgerBuilder = RevenueLedgerBuilder()
    private val revenueCalculator = RevenueCalculator()
    private val occupancyCalculator = OccupancyCalculator()

    private var annualRange: DateRange = RevenuePeriod.financialYearFor()
    private var monthRange: DateRange = currentMonthRange()
    private var weekRange: DateRange = RevenuePeriod.weekContaining(System.currentTimeMillis())
    private var customRange: DateRange? = null
    private var reportKind: ReportKind = ReportKind.REVENUE
    private var selectedProperty = ReportPropertyChoice(
        label = "All Properties - Consolidated",
        propertyRemoteId = null,
        includeAllProperties = true
    )
    private var bookingsLoadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        reportKind = when (intent.getStringExtra(EXTRA_REPORT_KIND)) {
            KIND_OCCUPANCY -> ReportKind.OCCUPANCY
            KIND_BALANCE -> ReportKind.BALANCE
            else -> ReportKind.REVENUE
        }
        val requestedHotelRemoteId = intent.getStringExtra(EXTRA_HOTEL_REMOTE_ID)
        if (requestedHotelRemoteId.isNullOrBlank()) {
            finish()
            return
        }
        hotelRemoteId = requestedHotelRemoteId
        dataSource = PropertyReportingDataSource(applicationContext, hotelRemoteId)
        // This repository is used only for the explicit OTA-settlement command.
        // Creating it does not start realtime sync.
        settlementRepository = BookingRepository(applicationContext, lifecycleScope, hotelRemoteId)
        setContentView(buildRoot())
        refreshReportData()
    }

    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        root.addView(toolbar())

        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(22))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        return root
    }

    private fun toolbar(): View {
        val topInset = statusBarHeight()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), topInset, dp(10), 0)
            setBackgroundColor(Color.parseColor("#FF5A5F"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58) + topInset)

            addView(TextView(this@RevenueReportActivity).apply {
                text = "\u2190"
                textSize = 26f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(52), LinearLayout.LayoutParams.MATCH_PARENT))

            addView(TextView(this@RevenueReportActivity).apply {
                text = "Property Reports"
                textSize = 22f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

            customRangeButton = MaterialButton(this@RevenueReportActivity).apply {
                text = "Custom"
                textSize = 12f
                isAllCaps = false
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)
                visibility = if (reportKind == ReportKind.REVENUE) View.VISIBLE else View.GONE
                setOnClickListener { chooseCustomRange() }
            }
            addView(customRangeButton, LinearLayout.LayoutParams(dp(92), dp(42)))
        }
    }

    private fun refreshReportData() {
        if (!::content.isInitialized) return
        bookingsLoadJob?.cancel()
        bookingsLoadJob = lifecycleScope.launch {
            content.removeAllViews()
            content.addView(TextView(this@RevenueReportActivity).apply {
                text = "Loading one consistent local reporting snapshot..."
                textSize = 14f
                setTextColor(Color.rgb(80, 80, 80))
                setPadding(dp(4), dp(14), dp(4), dp(14))
            })
            try {
                rawData = dataSource.loadRawData()
                ensureSelectedPropertyStillAvailable()
                render()
            } catch (error: Throwable) {
                content.removeAllViews()
                content.addView(TextView(this@RevenueReportActivity).apply {
                    text = "Could not load property report: ${error.message ?: error.javaClass.simpleName}"
                    textSize = 15f
                    setTextColor(Color.rgb(150, 30, 30))
                    setPadding(dp(4), dp(14), dp(4), dp(14))
                })
            }
        }
    }

    private fun visibleReportRange(): DateRange {
        val ranges = mutableListOf(annualRange, monthRange, weekRange)
        customRange?.let { ranges.add(it) }
        return DateRange(
            ranges.minOf { it.startMillis },
            ranges.maxOf { it.endMillis }
        )
    }

    private fun render() {
        if (!::content.isInitialized) return
        val raw = rawData ?: return

        content.removeAllViews()

        val visibleSnapshot = reportingEngine.build(raw, scopeFor(visibleReportRange()))
        rooms.clear()
        rooms.addAll(visibleSnapshot.dataset.rooms)
        bookings.clear()
        bookings.addAll(visibleSnapshot.dataset.bookings)

        if (::customRangeButton.isInitialized) {
            customRangeButton.visibility =
                if (reportKind == ReportKind.REVENUE) View.VISIBLE else View.GONE
        }

        content.addView(reportModeSelector())
        content.addView(spacer(8))
        content.addView(propertySelectorSection())
        content.addView(spacer(10))

        when (reportKind) {
            ReportKind.REVENUE -> renderRevenueDashboard()
            ReportKind.OCCUPANCY -> renderOccupancyDashboard()
            ReportKind.BALANCE -> renderBalanceDashboard()
        }
    }

    private fun renderRevenueDashboard() {
        val ledger = ledgerBuilder.build(rooms, bookings)
        content.addView(revenueSummarySection())

        val annualStats = revenueStats(ledger, annualRange, fiscalMonthBuckets(annualRange))
        val monthStats = revenueStats(ledger, monthRange, dayBuckets(monthRange))
        val weekStats = revenueStats(ledger, weekRange, dayBuckets(weekRange))

        content.addView(revenueSection(
            title = "Annual Revenue",
            rangeText = financialYearTitle(annualRange),
            previous = { annualRange = shiftRange(annualRange, Calendar.YEAR, -1); refreshReportData() },
            next = { annualRange = shiftRange(annualRange, Calendar.YEAR, 1); refreshReportData() },
            stats = annualStats,
            xLabelsVertical = true
        ))

        content.addView(revenueSection(
            title = "Monthly Revenue",
            rangeText = formatMonth(monthRange.startMillis),
            previous = { monthRange = shiftRange(monthRange, Calendar.MONTH, -1); refreshReportData() },
            next = { monthRange = shiftRange(monthRange, Calendar.MONTH, 1); refreshReportData() },
            stats = monthStats,
            xLabelsVertical = false
        ))

        content.addView(revenueSection(
            title = "Weekly Revenue",
            rangeText = weekTitle(weekRange),
            previous = { weekRange = shiftRange(weekRange, Calendar.DAY_OF_MONTH, -7); refreshReportData() },
            next = { weekRange = shiftRange(weekRange, Calendar.DAY_OF_MONTH, 7); refreshReportData() },
            stats = weekStats,
            xLabelsVertical = false
        ))

        customRange?.let { range ->
            val stats = revenueStats(ledger, range, customBuckets(range))
            content.addView(revenueSection(
                title = "Custom Report",
                rangeText = "${formatDate(range.startMillis)} - ${formatDate(range.endMillis - BusinessDates.DAY_MILLIS)}",
                previous = null,
                next = null,
                stats = stats,
                xLabelsVertical = false
            ))
        }

        content.addView(breakdownSection(
            title = "Annual Revenue & Settlement Breakdown",
            rangeText = financialYearTitle(annualRange),
            breakdown = settlementBreakdown(annualRange)
        ))
        content.addView(breakdownSection(
            title = "Monthly Revenue & Settlement Breakdown",
            rangeText = formatMonth(monthRange.startMillis),
            breakdown = settlementBreakdown(monthRange)
        ))
    }

    private fun reportModeSelector(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            listOf(
                ReportKind.REVENUE to "Revenue",
                ReportKind.OCCUPANCY to "Occupancy",
                ReportKind.BALANCE to "Balance"
            ).forEach { (kind, label) ->
                addView(MaterialButton(this@RevenueReportActivity).apply {
                    text = label
                    textSize = 12f
                    isAllCaps = false
                    alpha = if (reportKind == kind) 1f else 0.65f
                    setOnClickListener {
                        if (reportKind != kind) {
                            reportKind = kind
                            render()
                        }
                    }
                }, weightParams())
            }
        }
    }

    private fun propertySelectorSection(): View {
        return sectionCard().apply {
            addView(TextView(this@RevenueReportActivity).apply {
                text = "Property"
                textSize = 13f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(Color.rgb(80, 80, 80))
            })
            addView(LinearLayout(this@RevenueReportActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@RevenueReportActivity).apply {
                    text = selectedProperty.label
                    textSize = 17f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    setTextColor(Color.rgb(25, 25, 25))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(MaterialButton(this@RevenueReportActivity).apply {
                    text = "Change"
                    textSize = 12f
                    isAllCaps = false
                    setOnClickListener { showPropertyPicker() }
                }, LinearLayout.LayoutParams(dp(100), dp(42)))
            })
        }
    }

    private fun showPropertyPicker() {
        val choices = propertyChoices()
        val labels = choices.map { it.label }.toTypedArray()
        val selectedIndex = choices.indexOfFirst {
            it.includeAllProperties == selectedProperty.includeAllProperties &&
                it.propertyRemoteId == selectedProperty.propertyRemoteId
        }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Select property")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                selectedProperty = choices[which]
                dialog.dismiss()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun propertyChoices(): List<ReportPropertyChoice> {
        val raw = rawData ?: return listOf(selectedProperty)
        val choices = mutableListOf(
            ReportPropertyChoice(
                label = "All Properties - Consolidated",
                propertyRemoteId = null,
                includeAllProperties = true
            )
        )

        raw.properties
            .filter { !it.isDeleted }
            .sortedWith(compareBy({ it.sortOrder }, { it.propertyName.lowercase(Locale.getDefault()) }))
            .forEach { property ->
                choices.add(
                    ReportPropertyChoice(
                        label = property.propertyName,
                        propertyRemoteId = property.remoteId,
                        includeAllProperties = false
                    )
                )
            }

        val hasLegacyOrUnassigned =
            raw.rooms.any { !it.isDeleted && it.propertyRemoteId.isNullOrBlank() } ||
                raw.bookings.any { !it.isDeleted && it.propertyRemoteId.isNullOrBlank() }
        if (hasLegacyOrUnassigned) {
            choices.add(
                ReportPropertyChoice(
                    label = "Legacy / Unassigned",
                    propertyRemoteId = null,
                    includeAllProperties = false
                )
            )
        }
        return choices
    }

    private fun ensureSelectedPropertyStillAvailable() {
        if (selectedProperty.includeAllProperties) return
        val stillExists = propertyChoices().any {
            !it.includeAllProperties &&
                it.propertyRemoteId == selectedProperty.propertyRemoteId &&
                it.label == selectedProperty.label
        }
        if (!stillExists) {
            selectedProperty = ReportPropertyChoice(
                label = "All Properties - Consolidated",
                propertyRemoteId = null,
                includeAllProperties = true
            )
        }
    }

    private fun scopeFor(range: DateRange): PropertyReportScope {
        return PropertyReportScope(
            hotelRemoteId = hotelRemoteId,
            propertyRemoteId = selectedProperty.propertyRemoteId,
            includeAllProperties = selectedProperty.includeAllProperties,
            startMillis = range.startMillis,
            endMillis = range.endMillis
        )
    }

    private fun snapshotFor(range: DateRange) =
        reportingEngine.build(rawData ?: error("Reporting data is not loaded"), scopeFor(range))

    private fun revenueSummarySection(): View {
        val facts = snapshotFor(annualRange).revenue
        return sectionCard().apply {
            addView(sectionHeader(
                "Financial Year Summary",
                financialYearTitle(annualRange),
                null,
                null
            ))
            addView(metricStrip(listOf(
                "Gross Billing" to moneyWithCurrency(facts.grossRoomBilling),
                "Revenue ex-GST" to moneyWithCurrency(facts.roomRevenue),
                "GST" to moneyWithCurrency(facts.gstCollected),
                "Payments" to moneyWithCurrency(facts.paymentsRecordedInPeriod)
            )))
            addView(metricStrip(listOf(
                "CGST" to moneyWithCurrency(facts.cgstCollected),
                "SGST" to moneyWithCurrency(facts.sgstCollected),
                "Cess" to moneyWithCurrency(facts.cessCollected),
                "Refunds" to moneyWithCurrency(facts.refundsRecordedInPeriod)
            )))
        }
    }

    private fun renderBalanceDashboard() {
        val balance = snapshotFor(annualRange).balance

        content.addView(sectionCard().apply {
            addView(sectionHeader("Current Balance", selectedProperty.label, null, null))
            addView(metricStrip(listOf(
                "Receivable" to moneyWithCurrency(balance.totalReceivable),
                "Payments Received" to moneyWithCurrency(balance.totalReceived),
                "Applied Received" to moneyWithCurrency(balance.totalAppliedReceived)
            )))
            addView(metricStrip(listOf(
                "Excess Payment" to moneyWithCurrency(balance.totalExcessPayment),
                "Outstanding" to moneyWithCurrency(balance.totalOutstanding),
                "Open Bookings" to balance.openBookingCount.toString()
            )))
            addView(metricStrip(listOf(
                "OTA Outstanding" to moneyWithCurrency(balance.otaOutstanding),
                "Guest Outstanding" to moneyWithCurrency(balance.guestOutstanding)
            )))
        })

        val otaGroups = OtaReceivableGrouping.build(balance.bookings)
        content.addView(sectionCard().apply {
            addView(sectionHeader(
                "OTA Receivables",
                "${otaGroups.sumOf { it.bookingCount }} pending bookings",
                null,
                null
            ))
            if (otaGroups.isEmpty()) {
                addView(TextView(this@RevenueReportActivity).apply {
                    text = "No pending OTA receivables."
                    textSize = 14f
                    setTextColor(Color.rgb(80, 80, 80))
                    setPadding(0, dp(10), 0, dp(4))
                })
            } else {
                otaGroups.forEach { group ->
                    addView(otaReceivableCompanyRow(group))
                }
                if (selectedProperty.propertyRemoteId == null) {
                    addView(TextView(this@RevenueReportActivity).apply {
                        text = "Select one specific property before recording an OTA payment."
                        textSize = 12f
                        setTextColor(Color.rgb(120, 90, 20))
                        setPadding(0, dp(10), 0, dp(2))
                    })
                }
            }
        })

        val directRows = balance.bookings
            .filter {
                it.sourceType != BookingSourceType.OTA &&
                    it.outstanding > 0.001
            }
            .sortedByDescending { it.outstanding }

        content.addView(sectionCard().apply {
            addView(sectionHeader(
                "Direct Guest Balances",
                "${directRows.size} pending bookings",
                null,
                null
            ))
            addView(TextView(this@RevenueReportActivity).apply {
                text = "Direct guest payments continue to be recorded from the Booking Chart."
                textSize = 12f
                setTextColor(Color.rgb(90, 90, 90))
                setPadding(0, dp(4), 0, dp(8))
            })
            if (directRows.isEmpty()) {
                addView(TextView(this@RevenueReportActivity).apply {
                    text = "No pending direct guest balances."
                    textSize = 14f
                    setTextColor(Color.rgb(80, 80, 80))
                    setPadding(0, dp(8), 0, dp(4))
                })
            } else {
                directRows.forEach { row ->
                    addView(LinearLayout(this@RevenueReportActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(8), 0, dp(8))
                        addView(TextView(this@RevenueReportActivity).apply {
                            text = row.guestName.ifBlank { "Guest" }
                            textSize = 13f
                            setTextColor(Color.rgb(45, 45, 45))
                        }, LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ))
                        addView(TextView(this@RevenueReportActivity).apply {
                            text = moneyWithCurrency(row.outstanding)
                            textSize = 15f
                            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                            setTextColor(Color.parseColor("#FF5A5F"))
                            gravity = Gravity.END
                        })
                    })
                }
            }
        })
    }

    private fun otaReceivableCompanyRow(group: OtaReceivableGroup): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(9), 0, dp(9))

            addView(LinearLayout(this@RevenueReportActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(TextView(this@RevenueReportActivity).apply {
                    text = "${group.sourceName}\n${group.bookingCount} pending booking(s)"
                    textSize = 14f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    setTextColor(Color.rgb(35, 35, 35))
                }, LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ))

                addView(TextView(this@RevenueReportActivity).apply {
                    text = moneyWithCurrency(group.totalOutstanding)
                    textSize = 16f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    setTextColor(Color.parseColor("#FF5A5F"))
                    gravity = Gravity.END
                })
            })

            addView(MaterialButton(this@RevenueReportActivity).apply {
                text = if (
                    selectedProperty.propertyRemoteId != null &&
                    !group.sourceRemoteId.isNullOrBlank()
                ) {
                    "Open ${group.sourceName}"
                } else {
                    "Select a property to settle"
                }
                textSize = 12f
                isAllCaps = false
                isEnabled =
                    selectedProperty.propertyRemoteId != null &&
                        !group.sourceRemoteId.isNullOrBlank() &&
                        !otaSettlementInProgress
                setOnClickListener { showOtaSettlementSelection(group) }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            ).apply {
                setMargins(0, dp(7), 0, 0)
            })
        }
    }

    private fun showOtaSettlementSelection(group: OtaReceivableGroup) {
        if (otaSettlementInProgress) {
            Toast.makeText(this, "Another OTA settlement is being recorded.", Toast.LENGTH_SHORT).show()
            return
        }
        val propertyRemoteId = selectedProperty.propertyRemoteId
        if (propertyRemoteId.isNullOrBlank()) {
            Toast.makeText(this, "Select one specific property first.", Toast.LENGTH_LONG).show()
            return
        }
        val sourceRemoteId = group.sourceRemoteId
        if (sourceRemoteId.isNullOrBlank()) {
            Toast.makeText(
                this,
                "This consolidated or legacy OTA source cannot be settled until one property/source is selected.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        OtaSettlementSelectionDialog.show(
            activity = this,
            propertyName = selectedProperty.label,
            sourceName = group.sourceName,
            bookings = group.bookings,
            moneyFormatter = ::moneyWithCurrency
        ) { selectedRows, settlementReference, note ->
            val selectedTotal = selectedRows.sumOf { it.outstanding }
            otaSettlementInProgress = true
            lifecycleScope.launch {
                val result = settlementRepository.recordOtaSettlement(
                    propertyRemoteId = propertyRemoteId,
                    sourceRemoteId = sourceRemoteId,
                    sourceName = group.sourceName,
                    selections = selectedRows.map {
                        OtaSettlementSelection(
                            bookingRemoteId = it.bookingRemoteId,
                            expectedOutstanding = it.outstanding
                        )
                    },
                    settlementReference = settlementReference,
                    note = note
                )

                when (result) {
                    is SaveResult.Success -> {
                        Toast.makeText(
                            this@RevenueReportActivity,
                            "${group.sourceName} payment ${moneyWithCurrency(selectedTotal)} recorded.",
                            Toast.LENGTH_LONG
                        ).show()
                        refreshReportData()
                    }
                    is SaveResult.Conflict -> Toast.makeText(
                        this@RevenueReportActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                    is SaveResult.Error -> Toast.makeText(
                        this@RevenueReportActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
                otaSettlementInProgress = false
                if (result !is SaveResult.Success) {
                    render()
                }
            }
        }
    }

    private fun summaryCard(label: String, value: String, fill: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(9), dp(6), dp(9))
            background = roundedDrawable(Color.parseColor(fill), Color.parseColor("#E6E1EA"), 10)
            addView(TextView(this@RevenueReportActivity).apply {
                text = label
                textSize = 11f
                setTextColor(Color.rgb(70, 70, 70))
                gravity = Gravity.CENTER
            })
            addView(TextView(this@RevenueReportActivity).apply {
                text = value
                textSize = 17f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(Color.rgb(20, 20, 20))
                gravity = Gravity.CENTER
            })
        }
    }

    private fun revenueSection(
        title: String,
        rangeText: String,
        previous: (() -> Unit)?,
        next: (() -> Unit)?,
        stats: RevenueBlockStats,
        xLabelsVertical: Boolean
    ): View {
        return sectionCard().apply {
            addView(sectionHeader(title, rangeText, previous, next))
            addView(metricStrip(listOf(
                "Revenue" to compactMoney(stats.total),
                "ARR" to compactMoney(stats.arr),
                "Occupancy" to "${stats.occupancyPercent}%",
                "Nights" to "${stats.occupiedNights}/${stats.availableNights}"
            )))
            addView(LineTrendChartView(this@RevenueReportActivity).apply {
                setEntries(stats.entries, ChartValueMode.MONEY, xLabelsVertical)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(if (xLabelsVertical) 275 else 220)))
        }
    }

    private fun breakdownSection(title: String, rangeText: String, breakdown: SettlementBreakdown): View {
        return sectionCard().apply {
            addView(sectionHeader(title, rangeText, null, null))
            addView(breakdownGroup(
                title = "Sales",
                items = listOf(
                    "Gross Sales" to money(breakdown.grossSales),
                    "GST Collected" to money(breakdown.gstCollected),
                    "Net Revenue" to money(breakdown.netRevenue)
                ),
                fill = "#F7FBF8"
            ))
            addView(breakdownGroup(
                title = "Source Expenses",
                items = listOf(
                    "Commission" to money(breakdown.commission),
                    "GST on Commission" to money(breakdown.commissionGst),
                    "Source Fees" to money(breakdown.sourceFees)
                ),
                fill = "#FFF8EC"
            ))
            addView(breakdownGroup(
                title = "Tax Credits",
                items = listOf(
                    "TCS" to money(breakdown.tcs),
                    "TDS" to money(breakdown.tds)
                ),
                fill = "#F4F7FF"
            ))
            addView(breakdownTotalRow("Stored Expected OTA Payout", money(breakdown.expectedPayout)))
        }
    }

    private fun breakdownGroup(title: String, items: List<Pair<String, String>>, fill: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundedDrawable(Color.parseColor(fill), Color.parseColor("#E8E0EC"), 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(10), 0, 0) }

            addView(TextView(this@RevenueReportActivity).apply {
                text = title
                textSize = 14f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(Color.rgb(35, 35, 35))
            })
            addView(metricStrip(items))
        }
    }

    private fun breakdownTotalRow(label: String, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedDrawable(Color.parseColor("#FFF1F2"), Color.parseColor("#FFD4D6"), 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(10), 0, 0) }

            addView(TextView(this@RevenueReportActivity).apply {
                text = label
                textSize = 15f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(Color.rgb(35, 25, 45))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@RevenueReportActivity).apply {
                text = value
                textSize = 18f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(Color.parseColor("#FF5A5F"))
                gravity = Gravity.END
            })
        }
    }

    private fun renderOccupancyDashboard() {
        val annual = occupancyStats(annualRange, fiscalMonthBuckets(annualRange))
        val month = occupancyStats(monthRange, dayBuckets(monthRange))
        val week = occupancyStats(weekRange, dayBuckets(weekRange))

        content.addView(occupancySummaryRow(annual, month, week))
        content.addView(spacer(12))
        content.addView(occupancyReportSection(
            title = "Annual Occupancy",
            rangeText = financialYearTitle(annualRange),
            previous = { annualRange = shiftRange(annualRange, Calendar.YEAR, -1); refreshReportData() },
            next = { annualRange = shiftRange(annualRange, Calendar.YEAR, 1); refreshReportData() },
            stats = annual,
            xLabelsVertical = true
        ))
        content.addView(occupancyReportSection(
            title = "Monthly Occupancy",
            rangeText = formatMonth(monthRange.startMillis),
            previous = { monthRange = shiftRange(monthRange, Calendar.MONTH, -1); refreshReportData() },
            next = { monthRange = shiftRange(monthRange, Calendar.MONTH, 1); refreshReportData() },
            stats = month,
            xLabelsVertical = false
        ))
        content.addView(occupancyReportSection(
            title = "Weekly Occupancy",
            rangeText = weekTitle(weekRange),
            previous = { weekRange = shiftRange(weekRange, Calendar.DAY_OF_MONTH, -7); refreshReportData() },
            next = { weekRange = shiftRange(weekRange, Calendar.DAY_OF_MONTH, 7); refreshReportData() },
            stats = week,
            xLabelsVertical = false
        ))
    }

    private fun occupancySummaryRow(annual: OccupancyBlockStats, month: OccupancyBlockStats, week: OccupancyBlockStats): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            addView(summaryCard("Annual", "${annual.percent}%", "#EAF7EE"), weightParams())
            addView(summaryCard("Month", "${month.percent}%", "#FFF5DB"), weightParams())
            addView(summaryCard("Week", "${week.percent}%", "#EEF4FF"), weightParams())
            addView(summaryCard("Rooms", rooms.count { !it.isDeleted }.toString(), "#FFF0F0"), weightParams())
        }
    }

    private fun occupancyReportSection(
        title: String,
        rangeText: String,
        previous: (() -> Unit)?,
        next: (() -> Unit)?,
        stats: OccupancyBlockStats,
        xLabelsVertical: Boolean
    ): View {
        return sectionCard().apply {
            addView(sectionHeader(title, rangeText, previous, next))
            addView(metricStrip(listOf(
                "Occupancy" to "${stats.percent}%",
                "Nights" to "${stats.occupied}/${stats.available}",
                "Rooms/Day" to formatDecimal(stats.averageOccupiedRooms),
                "Rooms" to rooms.count { !it.isDeleted }.toString()
            )))
            addView(LineTrendChartView(this@RevenueReportActivity).apply {
                setEntries(stats.entries, ChartValueMode.PERCENT, xLabelsVertical)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(if (xLabelsVertical) 275 else 220)))
        }
    }
    private fun sectionCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedDrawable(Color.WHITE, Color.parseColor("#E6E1EA"), 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(14)) }
        }
    }

    private fun sectionHeader(title: String, range: String, previous: (() -> Unit)?, next: (() -> Unit)?): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@RevenueReportActivity).apply {
                text = title
                textSize = 20f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(Color.rgb(20, 20, 20))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (previous != null) addView(navButton("<", previous))
            addView(TextView(this@RevenueReportActivity).apply {
                text = range
                textSize = 13f
                setTextColor(Color.rgb(75, 75, 75))
                gravity = Gravity.CENTER
                setPadding(dp(6), 0, dp(6), 0)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)))
            if (next != null) addView(navButton(">", next))
        }
    }

    private fun navButton(text: String, action: () -> Unit): View {
        return TextView(this).apply {
            this.text = text
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.parseColor("#FF5A5F"))
            gravity = Gravity.CENTER
            setOnClickListener { action() }
        }.also { it.layoutParams = LinearLayout.LayoutParams(dp(32), dp(36)) }
    }

    private fun metricStrip(items: List<Pair<String, String>>): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(6))
            items.forEach { (label, value) ->
                addView(LinearLayout(this@RevenueReportActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    addView(TextView(this@RevenueReportActivity).apply {
                        text = label
                        textSize = 11f
                        setTextColor(Color.rgb(95, 95, 95))
                    })
                    addView(TextView(this@RevenueReportActivity).apply {
                        text = value
                        textSize = 15f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        setTextColor(Color.rgb(25, 25, 25))
                    })
                }, weightParams())
            }
        }
    }

    private fun revenueStats(
        ledger: List<RevenueLedgerEntry>,
        range: DateRange,
        buckets: List<PeriodBucket>
    ): RevenueBlockStats {
        // The ledger parameter is intentionally retained so the existing chart method
        // signature and layout remain stable. Monetary values below come only from the
        // property reporting SSOT (stored financial lines), not BookingEntity caches.
        if (ledger.isEmpty()) {
            // No special fallback is needed; snapshotFor() safely returns zero facts.
        }
        val snapshot = snapshotFor(range)
        val facts = snapshot.revenue
        val occupancy = snapshot.occupancy
        val entries = buckets.map { bucket ->
            bucket.label to snapshotFor(bucket.range).revenue.roomRevenue
        }
        val arrDivider = occupancy.occupiedRoomNights.coerceAtLeast(1)
        return RevenueBlockStats(
            total = facts.roomRevenue,
            occupancyPercent = occupancy.occupancyPercent,
            arr = facts.roomRevenue / arrDivider,
            occupiedNights = occupancy.occupiedRoomNights,
            availableNights = occupancy.availableRoomNights,
            entries = entries
        )
    }

    private fun occupancyStats(range: DateRange, buckets: List<PeriodBucket>): OccupancyBlockStats {
        val facts = snapshotFor(range).occupancy
        val entries = buckets.map { bucket ->
            bucket.label to snapshotFor(bucket.range).occupancy.occupancyPercent.toDouble()
        }
        return OccupancyBlockStats(
            percent = facts.occupancyPercent,
            occupied = facts.occupiedRoomNights,
            available = facts.availableRoomNights,
            averageOccupiedRooms = facts.averageOccupiedRoomsPerDay,
            entries = entries
        )
    }

    private fun revenuePeriodValues(ledger: List<RevenueLedgerEntry>, buckets: List<PeriodBucket>): List<Pair<String, Double>> {
        return buckets.map { bucket ->
            val value = ledger
                .filter { it.businessDateMillis in bucket.range.startMillis until bucket.range.endMillis }
                .filter { it.type == RevenueLedgerEntryType.ROOM_REVENUE }
                .sumOf { it.amount }
            bucket.label to value
        }
    }

    private fun settlementBreakdown(range: DateRange): SettlementBreakdown {
        val facts = snapshotFor(range).revenue
        val settlements = facts.bookingSettlements

        fun allocation(periodRevenue: Double, fullRevenue: Double): Double {
            if (fullRevenue <= 0.0 || periodRevenue <= 0.0) return 0.0
            return (periodRevenue / fullRevenue).coerceIn(0.0, 1.0)
        }

        return SettlementBreakdown(
            grossSales = facts.grossRoomBilling,
            gstCollected = facts.gstCollected,
            netRevenue = facts.roomRevenue,
            commission = settlements.sumOf {
                it.storedCommissionAmount * allocation(
                    it.roomRevenueInReportPeriod,
                    it.fullBookingRoomRevenueFromFinancialLines
                )
            },
            commissionGst = settlements.sumOf {
                it.storedCommissionTax * allocation(
                    it.roomRevenueInReportPeriod,
                    it.fullBookingRoomRevenueFromFinancialLines
                )
            },
            sourceFees = settlements.sumOf {
                it.storedSourceFee * allocation(
                    it.roomRevenueInReportPeriod,
                    it.fullBookingRoomRevenueFromFinancialLines
                )
            },
            tcs = settlements.sumOf {
                it.storedTcsAmount * allocation(
                    it.roomRevenueInReportPeriod,
                    it.fullBookingRoomRevenueFromFinancialLines
                )
            },
            tds = settlements.sumOf {
                it.storedTdsAmount * allocation(
                    it.roomRevenueInReportPeriod,
                    it.fullBookingRoomRevenueFromFinancialLines
                )
            },
            expectedPayout = settlements.sumOf {
                it.storedExpectedPayout * allocation(
                    it.roomRevenueInReportPeriod,
                    it.fullBookingRoomRevenueFromFinancialLines
                )
            }
        )
    }

    private fun BookingEntity.periodAllocation(range: DateRange, activeRoomIds: Set<String>): Double {
        val bookedRoomCount = roomRemoteIds.count { it in activeRoomIds }
        if (bookedRoomCount <= 0) return 0.0
        val totalNights = BusinessDates.stayNights(checkInMillis, checkOutMillis)
        if (totalNights <= 0) return 0.0
        val overlapNights = BusinessDates.overlapNights(checkInMillis, checkOutMillis, range)
        if (overlapNights <= 0) return 0.0
        return overlapNights.toDouble() / totalNights.toDouble()
    }

    private fun fiscalMonthBuckets(range: DateRange): List<PeriodBucket> {
        val start = Calendar.getInstance().apply { timeInMillis = range.startMillis }
        return (0 until 12).map { offset ->
            val bucketStart = Calendar.getInstance().apply {
                timeInMillis = start.timeInMillis
                add(Calendar.MONTH, offset)
            }
            val bucketEnd = Calendar.getInstance().apply {
                timeInMillis = bucketStart.timeInMillis
                add(Calendar.MONTH, 1)
            }
            PeriodBucket(
                SimpleDateFormat("MMM", Locale.getDefault()).format(bucketStart.timeInMillis),
                DateRange(BusinessDates.startOfDay(bucketStart.timeInMillis), BusinessDates.startOfDay(bucketEnd.timeInMillis))
            )
        }
    }

    private fun dayBuckets(range: DateRange): List<PeriodBucket> {
        val days = BusinessDates.rangeDays(range).coerceAtMost(31)
        return (0 until days).map { offset ->
            val start = range.startMillis + offset * BusinessDates.DAY_MILLIS
            PeriodBucket(
                SimpleDateFormat(if (days <= 7) "EEE" else "d", Locale.getDefault()).format(start),
                DateRange(start, start + BusinessDates.DAY_MILLIS)
            )
        }
    }

    private fun customBuckets(range: DateRange): List<PeriodBucket> {
        return if (BusinessDates.rangeDays(range) <= 31) dayBuckets(range) else fiscalMonthBuckets(range)
    }

    private fun currentMonthRange(): DateRange {
        val now = Calendar.getInstance()
        return RevenuePeriod.month(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
    }

    private fun shiftRange(range: DateRange, field: Int, amount: Int): DateRange {
        val start = Calendar.getInstance().apply {
            timeInMillis = range.startMillis
            add(field, amount)
        }
        val end = Calendar.getInstance().apply {
            timeInMillis = range.endMillis
            add(field, amount)
        }
        return DateRange(BusinessDates.startOfDay(start.timeInMillis), BusinessDates.startOfDay(end.timeInMillis))
    }

    private fun occupiedRoomNights(range: DateRange): Int {
        val activeRoomIds = rooms
            .filter { RoomLifecyclePolicy.availableNights(it, range) > 0 }
            .map { it.remoteId }
            .toSet()
        if (activeRoomIds.isEmpty()) return 0
        return bookings.filter { !it.isDeleted && it.bookingStatus != BookingStatus.CANCELLED }.sumOf { booking ->
            BusinessDates.overlapNights(booking.checkInMillis, booking.checkOutMillis, range) *
                booking.roomRemoteIds.count { it in activeRoomIds }
        }
    }

    private fun availableRoomNights(range: DateRange): Int {
        return rooms.sumOf { RoomLifecyclePolicy.availableNights(it, range) }
    }

    private fun chooseCustomRange() {
        val start = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            start.set(y, m, d, 0, 0, 0)
            val end = Calendar.getInstance()
            DatePickerDialog(this, { _, ey, em, ed ->
                end.set(ey, em, ed, 0, 0, 0)
                end.add(Calendar.DAY_OF_MONTH, 1)
                if (end.timeInMillis > start.timeInMillis) {
                    customRange = DateRange(BusinessDates.startOfDay(start.timeInMillis), BusinessDates.startOfDay(end.timeInMillis))
                    refreshReportData()
                }
            }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH)).show()
        }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun compactMoney(amount: Double): String {
        val value = amount.roundToInt()
        return when {
            value >= 100_000 -> formatCompact(value / 100_000.0, "L")
            value >= 1_000 -> formatCompact(value / 1_000.0, "K")
            else -> value.toString()
        }
    }

    private fun formatCompact(value: Double, suffix: String): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) "${rounded.toInt()}$suffix" else String.format(Locale.getDefault(), "%.1f%s", rounded, suffix)
    }

    private fun formatDecimal(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else String.format(Locale.getDefault(), "%.1f", rounded)
    }

    private fun money(amount: Double): String {
        val value = amount.roundToInt()
        return String.format(Locale.getDefault(), "%,d", value)
    }

    private fun moneyWithCurrency(amount: Double): String = "\u20B9${money(amount)}"


    private fun financialYearTitle(range: DateRange): String {
        val startYear = Calendar.getInstance().apply { timeInMillis = range.startMillis }.get(Calendar.YEAR)
        return "FY $startYear-${(startYear + 1).toString().takeLast(2)}"
    }

    private fun weekTitle(range: DateRange): String {
        return "${formatDate(range.startMillis)} - ${formatDate(range.endMillis - BusinessDates.DAY_MILLIS)}"
    }

    private fun formatMonth(millis: Long): String = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(millis)

    private fun formatDate(millis: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(millis)

    private fun roundedDrawable(fill: Int, stroke: Int, radiusDp: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    }

    private fun weightParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(3), 0, dp(3), 0)
        }
    }

    private fun spacer(height: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(height))
    }

    private fun statusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun List<RevenueLedgerEntry>.sumByType(type: String): Double = filter { it.type == type }.sumOf { it.amount }

    private data class PeriodBucket(val label: String, val range: DateRange)

    private data class RevenueBlockStats(
        val total: Double,
        val occupancyPercent: Int,
        val arr: Double,
        val occupiedNights: Int,
        val availableNights: Int,
        val entries: List<Pair<String, Double>>
    )

    private enum class ReportKind {
        REVENUE, OCCUPANCY, BALANCE
    }

    private data class ReportPropertyChoice(
        val label: String,
        val propertyRemoteId: String?,
        val includeAllProperties: Boolean
    )

    private data class OccupancyBlockStats(
        val percent: Int,
        val occupied: Int,
        val available: Int,
        val averageOccupiedRooms: Double,
        val entries: List<Pair<String, Double>>
    )

    private data class SettlementBreakdown(
        val grossSales: Double = 0.0,
        val gstCollected: Double = 0.0,
        val netRevenue: Double = 0.0,
        val commission: Double = 0.0,
        val commissionGst: Double = 0.0,
        val sourceFees: Double = 0.0,
        val tcs: Double = 0.0,
        val tds: Double = 0.0,
        val expectedPayout: Double = 0.0
    ) {
        val netCollection: Double
            get() = (grossSales - commission - commissionGst - sourceFees - tcs - tds).coerceAtLeast(0.0)

        fun scaled(factor: Double): SettlementBreakdown {
            return copy(
                grossSales = grossSales * factor,
                gstCollected = gstCollected * factor,
                netRevenue = netRevenue * factor,
                commission = commission * factor,
                commissionGst = commissionGst * factor,
                sourceFees = sourceFees * factor,
                tcs = tcs * factor,
                tds = tds * factor,
                expectedPayout = expectedPayout * factor
            )
        }

        operator fun plus(other: SettlementBreakdown): SettlementBreakdown {
            return SettlementBreakdown(
                grossSales = grossSales + other.grossSales,
                gstCollected = gstCollected + other.gstCollected,
                netRevenue = netRevenue + other.netRevenue,
                commission = commission + other.commission,
                commissionGst = commissionGst + other.commissionGst,
                sourceFees = sourceFees + other.sourceFees,
                tcs = tcs + other.tcs,
                tds = tds + other.tds,
                expectedPayout = expectedPayout + other.expectedPayout
            )
        }
    }
}

enum class ChartValueMode {
    MONEY, PERCENT
}

class LineTrendChartView(context: android.content.Context) : View(context) {
    private val entries = mutableListOf<Pair<String, Double>>()
    private var valueMode: ChartValueMode = ChartValueMode.MONEY
    private var verticalLabels: Boolean = false

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5A5F")
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#14FF5A5F")
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF1F2")
        strokeWidth = 1f
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(108, 108, 108)
        textSize = 21f
        textAlign = Paint.Align.RIGHT
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(82, 82, 82)
        textSize = 21f
        textAlign = Paint.Align.CENTER
    }

    fun setEntries(newEntries: List<Pair<String, Double>>, mode: ChartValueMode, xLabelsVertical: Boolean) {
        entries.clear()
        entries.addAll(newEntries)
        valueMode = mode
        verticalLabels = xLabelsVertical
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return

        val left = 62f
        val top = 24f
        val right = width - 16f
        val bottom = height - if (verticalLabels) 92f else 42f
        val chartWidth = (right - left).coerceAtLeast(1f)
        val chartHeight = (bottom - top).coerceAtLeast(1f)
        val maxValue = niceAxisMax(entries.maxOf { it.second })

        repeat(4) { index ->
            val fraction = index / 3f
            val y = top + fraction * chartHeight
            val value = maxValue * (1f - fraction)
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(formatChartValue(value), left - 8f, y + 7f, axisPaint)
        }

        val points = entries.mapIndexed { index, item ->
            val x = if (entries.size == 1) left + chartWidth / 2f else left + index * (chartWidth / (entries.size - 1))
            val y = bottom - ((item.second / maxValue) * chartHeight).toFloat()
            x to y
        }

        val path = android.graphics.Path()
        points.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.first, point.second) else path.lineTo(point.first, point.second)
        }
        canvas.drawPath(path, linePaint)

        val fillPath = android.graphics.Path(path).apply {
            lineTo(points.last().first, bottom)
            lineTo(points.first().first, bottom)
            close()
        }
        canvas.drawPath(fillPath, fillPaint)

        val labelEvery = labelStep(entries.size)
        entries.forEachIndexed { index, item ->
            if (index % labelEvery == 0 || index == entries.lastIndex) {
                val x = if (entries.size == 1) left + chartWidth / 2f else left + index * (chartWidth / (entries.size - 1))
                if (verticalLabels) {
                    canvas.save()
                    canvas.rotate(-90f, x, height - 24f)
                    canvas.drawText(item.first, x, height - 24f, labelPaint)
                    canvas.restore()
                } else {
                    canvas.drawText(item.first, x, height - 10f, labelPaint)
                }
            }
        }
    }

    private fun labelStep(count: Int): Int = when {
        count <= 12 -> 1
        count <= 31 -> 5
        else -> ceil(count / 8.0).toInt()
    }

    private fun niceAxisMax(maxEntry: Double): Double {
        if (valueMode == ChartValueMode.PERCENT) return 100.0
        return niceMoneyAxisMax(maxEntry)
    }

    private fun formatChartValue(value: Double): String {
        return when (valueMode) {
            ChartValueMode.PERCENT -> "${value.roundToInt()}%"
            ChartValueMode.MONEY -> compactChartMoney(value)
        }
    }
}

class ComparisonLineChartView(context: android.content.Context) : View(context) {
    private val previous = mutableListOf<Pair<String, Double>>()
    private val current = mutableListOf<Pair<String, Double>>()
    private val previousPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB3B5")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5A5F")
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF1F2")
        strokeWidth = 1f
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(108, 108, 108)
        textSize = 21f
        textAlign = Paint.Align.RIGHT
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(82, 82, 82)
        textSize = 21f
        textAlign = Paint.Align.CENTER
    }

    fun setEntries(previousEntries: List<Pair<String, Double>>, currentEntries: List<Pair<String, Double>>) {
        previous.clear()
        previous.addAll(previousEntries)
        current.clear()
        current.addAll(currentEntries)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (current.isEmpty()) return
        val left = 62f
        val top = 24f
        val right = width - 16f
        val bottom = height - 74f
        val chartWidth = (right - left).coerceAtLeast(1f)
        val chartHeight = (bottom - top).coerceAtLeast(1f)
        val maxEntry = (current + previous).maxOfOrNull { it.second } ?: 0.0
        val maxValue = niceMoneyAxisMax(maxEntry)

        repeat(4) { index ->
            val fraction = index / 3f
            val y = top + fraction * chartHeight
            val value = maxValue * (1f - fraction)
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(compactChartMoney(value), left - 8f, y + 7f, axisPaint)
        }

        drawLine(canvas, previous, left, right, bottom, chartWidth, chartHeight, maxValue, previousPaint)
        drawLine(canvas, current, left, right, bottom, chartWidth, chartHeight, maxValue, currentPaint)

        current.forEachIndexed { index, item ->
            val x = if (current.size == 1) left + chartWidth / 2f else left + index * (chartWidth / (current.size - 1))
            canvas.save()
            canvas.rotate(-90f, x, height - 24f)
                    canvas.drawText(item.first, x, height - 24f, labelPaint)
            canvas.restore()
        }
    }

    private fun drawLine(
        canvas: Canvas,
        values: List<Pair<String, Double>>,
        left: Float,
        right: Float,
        bottom: Float,
        chartWidth: Float,
        chartHeight: Float,
        maxValue: Double,
        paint: Paint
    ) {
        if (values.isEmpty()) return
        val path = android.graphics.Path()
        values.forEachIndexed { index, item ->
            val x = if (values.size == 1) left + (right - left) / 2f else left + index * (chartWidth / (values.size - 1))
            val y = bottom - ((item.second / maxValue) * chartHeight).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }
}

private fun niceMoneyAxisMax(maxEntry: Double): Double {
    if (maxEntry <= 0.0) return 1.0
    val rawStep = maxEntry / 3.0
    val step = when {
        rawStep >= 100_000.0 -> ceil(rawStep / 100_000.0) * 100_000.0
        rawStep >= 10_000.0 -> ceil(rawStep / 10_000.0) * 10_000.0
        rawStep >= 1_000.0 -> ceil(rawStep / 1_000.0) * 1_000.0
        rawStep >= 100.0 -> ceil(rawStep / 100.0) * 100.0
        rawStep >= 10.0 -> ceil(rawStep / 10.0) * 10.0
        else -> ceil(rawStep).coerceAtLeast(1.0)
    }
    return (step * 3.0).coerceAtLeast(1.0)
}

private fun compactChartMoney(amount: Double): String {
    val value = amount.roundToInt()
    return when {
        value >= 100_000 -> compactDecimal(value / 100_000.0, "L")
        value >= 1_000 -> compactDecimal(value / 1_000.0, "K")
        else -> value.toString()
    }
}

private fun compactDecimal(value: Double, suffix: String): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) "${rounded.toInt()}$suffix" else String.format(Locale.getDefault(), "%.1f%s", rounded, suffix)
}




