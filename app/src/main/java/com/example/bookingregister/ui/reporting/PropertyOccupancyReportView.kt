package com.example.bookingregister.ui.reporting

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.bookingregister.reporting.property.PropertyOccupancyPeriodReport
import com.example.bookingregister.reporting.property.PropertyOccupancyReport
import com.google.android.material.button.MaterialButton
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Display-only Occupancy UI.
 *
 * The view receives one immutable PropertyOccupancyReport and navigation
 * callbacks. It has no repository, DAO, Firebase, booking-save or sync
 * dependency. Annual/monthly/weekly charts intentionally use the same
 * LineTrendChartView and percent mode as the existing Occupancy screen.
 */
class PropertyOccupancyReportView(
    context: Context
) : LinearLayout(context) {

    init {
        orientation = VERTICAL
    }

    fun bind(
        report: PropertyOccupancyReport,
        propertyLabel: String,
        annualRangeText: String,
        monthRangeText: String,
        weekRangeText: String,
        onChangeProperty: () -> Unit,
        onAnnualPrevious: () -> Unit,
        onAnnualNext: () -> Unit,
        onMonthPrevious: () -> Unit,
        onMonthNext: () -> Unit,
        onWeekPrevious: () -> Unit,
        onWeekNext: () -> Unit
    ) {
        removeAllViews()

        addView(
            propertySection(
                propertyLabel = propertyLabel,
                onChangeProperty = onChangeProperty
            )
        )

        addView(
            summaryRow(report)
        )

        addView(spacer(12))

        addView(
            occupancySection(
                title = "Annual Occupancy",
                rangeText = annualRangeText,
                previous = onAnnualPrevious,
                next = onAnnualNext,
                report = report.annual,
                xLabelsVertical = true
            )
        )

        addView(
            occupancySection(
                title = "Monthly Occupancy",
                rangeText = monthRangeText,
                previous = onMonthPrevious,
                next = onMonthNext,
                report = report.monthly,
                xLabelsVertical = false
            )
        )

        addView(
            occupancySection(
                title = "Weekly Occupancy",
                rangeText = weekRangeText,
                previous = onWeekPrevious,
                next = onWeekNext,
                report = report.weekly,
                xLabelsVertical = false
            )
        )
    }

    private fun propertySection(
        propertyLabel: String,
        onChangeProperty: () -> Unit
    ): View {
        return sectionCard().apply {
            addView(TextView(context).apply {
                text = "Property"
                textSize = 13f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(Color.rgb(80, 80, 80))
            })

            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(TextView(context).apply {
                    text = propertyLabel
                    textSize = 17f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    setTextColor(Color.rgb(25, 25, 25))
                }, LayoutParams(
                    0,
                    LayoutParams.WRAP_CONTENT,
                    1f
                ))

                addView(MaterialButton(context).apply {
                    text = "Change"
                    textSize = 12f
                    isAllCaps = false
                    setOnClickListener { onChangeProperty() }
                }, LayoutParams(
                    dp(100),
                    dp(42)
                ))
            })
        }
    }

    private fun summaryRow(
        report: PropertyOccupancyReport
    ): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            isBaselineAligned = false

            addView(
                summaryCard(
                    "Annual",
                    "${report.annual.facts.occupancyPercent}%",
                    "#EAF7EE"
                ),
                weightParams()
            )
            addView(
                summaryCard(
                    "Month",
                    "${report.monthly.facts.occupancyPercent}%",
                    "#FFF5DB"
                ),
                weightParams()
            )
            addView(
                summaryCard(
                    "Week",
                    "${report.weekly.facts.occupancyPercent}%",
                    "#EEF4FF"
                ),
                weightParams()
            )
            addView(
                summaryCard(
                    "Rooms",
                    report.annual.facts.roomCount.toString(),
                    "#FFF0F0"
                ),
                weightParams()
            )
        }
    }

    private fun occupancySection(
        title: String,
        rangeText: String,
        previous: () -> Unit,
        next: () -> Unit,
        report: PropertyOccupancyPeriodReport,
        xLabelsVertical: Boolean
    ): View {
        val facts = report.facts

        return sectionCard().apply {
            addView(
                sectionHeader(
                    title = title,
                    range = rangeText,
                    previous = previous,
                    next = next
                )
            )

            addView(
                metricStrip(
                    listOf(
                        "Occupancy" to "${facts.occupancyPercent}%",
                        "Nights" to "${facts.occupiedRoomNights}/${facts.availableRoomNights}",
                        "Rooms/Day" to formatDecimal(facts.averageOccupiedRoomsPerDay),
                        "Rooms" to facts.roomCount.toString()
                    )
                )
            )

            addView(
                LineTrendChartView(context).apply {
                    setEntries(
                        report.entries,
                        ChartValueMode.PERCENT,
                        xLabelsVertical
                    )
                },
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    dp(if (xLabelsVertical) 275 else 220)
                )
            )
        }
    }

    private fun summaryCard(
        label: String,
        value: String,
        fill: String
    ): View {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(9), dp(6), dp(9))
            background = roundedDrawable(
                Color.parseColor(fill),
                Color.parseColor("#E6E1EA"),
                10
            )

            addView(TextView(context).apply {
                text = label
                textSize = 11f
                setTextColor(Color.rgb(70, 70, 70))
                gravity = Gravity.CENTER
            })

            addView(TextView(context).apply {
                text = value
                textSize = 17f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(Color.rgb(20, 20, 20))
                gravity = Gravity.CENTER
            })
        }
    }

    private fun sectionCard(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedDrawable(
                Color.WHITE,
                Color.parseColor("#E6E1EA"),
                12
            )
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(14))
            }
        }
    }

    private fun sectionHeader(
        title: String,
        range: String,
        previous: () -> Unit,
        next: () -> Unit
    ): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(TextView(context).apply {
                text = title
                textSize = 20f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                setTextColor(Color.rgb(20, 20, 20))
            }, LayoutParams(
                0,
                LayoutParams.WRAP_CONTENT,
                1f
            ))

            addView(navButton("<", previous))

            addView(TextView(context).apply {
                text = range
                textSize = 13f
                setTextColor(Color.rgb(75, 75, 75))
                gravity = Gravity.CENTER
                setPadding(dp(6), 0, dp(6), 0)
            }, LayoutParams(
                LayoutParams.WRAP_CONTENT,
                dp(36)
            ))

            addView(navButton(">", next))
        }
    }

    private fun navButton(
        text: String,
        action: () -> Unit
    ): View {
        return TextView(context).apply {
            this.text = text
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.parseColor("#FF5A5F"))
            gravity = Gravity.CENTER
            setOnClickListener { action() }
        }.also {
            it.layoutParams = LayoutParams(dp(32), dp(36))
        }
    }

    private fun metricStrip(
        items: List<Pair<String, String>>
    ): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, dp(10), 0, dp(6))

            items.forEach { (label, value) ->
                addView(LinearLayout(context).apply {
                    orientation = VERTICAL
                    gravity = Gravity.CENTER

                    addView(TextView(context).apply {
                        text = label
                        textSize = 11f
                        setTextColor(Color.rgb(95, 95, 95))
                    })

                    addView(TextView(context).apply {
                        text = value
                        textSize = 15f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                        setTextColor(Color.rgb(25, 25, 25))
                    })
                }, weightParams())
            }
        }
    }

    private fun roundedDrawable(
        fill: Int,
        stroke: Int,
        radiusDp: Int
    ): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != Color.TRANSPARENT) {
                setStroke(dp(1), stroke)
            }
        }
    }

    private fun weightParams(): LayoutParams {
        return LayoutParams(
            0,
            LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            setMargins(dp(3), 0, dp(3), 0)
        }
    }

    private fun spacer(height: Int): View {
        return View(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(height)
            )
        }
    }

    private fun formatDecimal(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", rounded)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }
}