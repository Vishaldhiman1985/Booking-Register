package com.example.bookingregister.ui.reporting

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.bookingregister.reporting.property.OtaReceivableGroup
import com.example.bookingregister.reporting.property.PropertyBalanceBookingFacts
import com.example.bookingregister.reporting.property.PropertyBalanceReport
import com.google.android.material.button.MaterialButton
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Display-only Balance UI.
 *
 * This view receives an immutable PropertyBalanceReport.
 * It has no repository, DAO, Firebase, payment or sync dependency.
 *
 * The only interaction exposed outside this view is changing
 * the selected reporting property.
 */
class PropertyBalanceReportView(
    context: Context
) : LinearLayout(context) {

    init {
        orientation = VERTICAL
    }

    fun bind(
        report: PropertyBalanceReport,
        propertyLabel: String,
        onChangeProperty: () -> Unit
    ) {
        removeAllViews()

        addView(
            propertySection(
                propertyLabel = propertyLabel,
                onChangeProperty = onChangeProperty
            )
        )

        addView(
            currentBalanceSection(
                report = report,
                propertyLabel = propertyLabel
            )
        )

        addView(
            otaReceivablesSection(
                report = report
            )
        )

        addView(
            directGuestBalancesSection(
                report = report
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
                typeface =
                    Typeface.create(
                        Typeface.SANS_SERIF,
                        Typeface.BOLD
                    )
                setTextColor(Color.rgb(80, 80, 80))
            })

            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(TextView(context).apply {
                    text = propertyLabel
                    textSize = 17f
                    typeface =
                        Typeface.create(
                            Typeface.SANS_SERIF,
                            Typeface.BOLD
                        )
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
                    setOnClickListener {
                        onChangeProperty()
                    }
                }, LayoutParams(
                    dp(100),
                    dp(42)
                ))
            })
        }
    }

    private fun currentBalanceSection(
        report: PropertyBalanceReport,
        propertyLabel: String
    ): View {
        val facts = report.facts

        return sectionCard().apply {

            addView(
                sectionHeader(
                    title = "Current Balance",
                    subtitle = propertyLabel
                )
            )

            addView(
                metricStrip(
                    listOf(
                        "Receivable" to money(facts.totalReceivable),
                        "Payments Received" to money(facts.totalReceived),
                        "Applied Received" to money(facts.totalAppliedReceived)
                    )
                )
            )

            addView(
                metricStrip(
                    listOf(
                        "Excess Payment" to money(facts.totalExcessPayment),
                        "Outstanding" to money(facts.totalOutstanding),
                        "Open Bookings" to facts.openBookingCount.toString()
                    )
                )
            )

            addView(
                metricStrip(
                    listOf(
                        "OTA Outstanding" to money(facts.otaOutstanding),
                        "Guest Outstanding" to money(facts.guestOutstanding)
                    )
                )
            )
        }
    }

    private fun otaReceivablesSection(
        report: PropertyBalanceReport
    ): View {
        val groups = report.otaReceivables
        val pendingCount =
            groups.sumOf { it.bookingCount }

        return sectionCard().apply {

            addView(
                sectionHeader(
                    title = "OTA Receivables",
                    subtitle =
                        pendingCount.toString() +
                            " pending booking(s)"
                )
            )

            if (groups.isEmpty()) {
                addView(TextView(context).apply {
                    text = "No pending OTA receivables."
                    textSize = 14f
                    setTextColor(Color.rgb(80, 80, 80))
                    setPadding(
                        0,
                        dp(10),
                        0,
                        dp(4)
                    )
                })
            } else {
                groups.forEach { group ->
                    addView(
                        otaCompanyRow(group)
                    )
                }
            }
        }
    }

    private fun otaCompanyRow(
        group: OtaReceivableGroup
    ): View {
        return LinearLayout(context).apply {

            orientation = VERTICAL
            setPadding(
                0,
                dp(9),
                0,
                dp(9)
            )

            addView(LinearLayout(context).apply {

                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(TextView(context).apply {
                    text =
                        group.sourceName +
                            "\n" +
                            group.bookingCount +
                            " pending booking(s)"
                    textSize = 14f
                    typeface =
                        Typeface.create(
                            Typeface.SANS_SERIF,
                            Typeface.BOLD
                        )
                    setTextColor(Color.rgb(35, 35, 35))
                }, LayoutParams(
                    0,
                    LayoutParams.WRAP_CONTENT,
                    1f
                ))

                addView(TextView(context).apply {
                    text =
                        money(group.totalOutstanding)
                    textSize = 16f
                    typeface =
                        Typeface.create(
                            Typeface.SANS_SERIF,
                            Typeface.BOLD
                        )
                    setTextColor(
                        Color.parseColor("#FF5A5F")
                    )
                    gravity = Gravity.END
                })
            })

            addView(MaterialButton(context).apply {
                text =
                    "Open " + group.sourceName
                textSize = 12f
                isAllCaps = false

                setOnClickListener {
                    showOtaBookings(group)
                }
            }, LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(42)
            ).apply {
                setMargins(
                    0,
                    dp(7),
                    0,
                    0
                )
            })
        }
    }

    private fun showOtaBookings(
        group: OtaReceivableGroup
    ) {
        val rows = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                dp(18),
                dp(8),
                dp(18),
                dp(8)
            )
        }

        rows.addView(TextView(context).apply {
            text =
                group.bookingCount.toString() +
                    " pending booking(s)  |  " +
                    money(group.totalOutstanding)
            textSize = 15f
            typeface =
                Typeface.create(
                    Typeface.SANS_SERIF,
                    Typeface.BOLD
                )
            setTextColor(Color.rgb(35, 90, 55))
            setPadding(
                0,
                0,
                0,
                dp(10)
            )
        })

        group.bookings.forEach { row ->

            rows.addView(
                bookingBalanceRow(row)
            )
        }

        val scroll = ScrollView(context).apply {
            addView(rows)
        }

        AlertDialog.Builder(context)
            .setTitle(group.sourceName + " Receivables")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun bookingBalanceRow(
        row: PropertyBalanceBookingFacts
    ): View {
        val shortId =
            row.bookingRemoteId.takeLast(8)

        return LinearLayout(context).apply {

            orientation = VERTICAL
            setPadding(
                0,
                dp(7),
                0,
                dp(7)
            )

            addView(TextView(context).apply {
                text =
                    row.guestName.ifBlank { "Guest" }
                textSize = 14f
                typeface =
                    Typeface.create(
                        Typeface.SANS_SERIF,
                        Typeface.BOLD
                    )
                setTextColor(Color.rgb(40, 40, 40))
            })

            addView(TextView(context).apply {
                text =
                    "Pending " +
                        money(row.outstanding) +
                        "  |  Booking ..." +
                        shortId
                textSize = 13f
                setTextColor(Color.rgb(90, 90, 90))
            })
        }
    }

    private fun directGuestBalancesSection(
        report: PropertyBalanceReport
    ): View {
        val rows =
            report.directGuestBalances

        return sectionCard().apply {

            addView(
                sectionHeader(
                    title = "Direct Guest Balances",
                    subtitle =
                        rows.size.toString() +
                            " pending booking(s)"
                )
            )

            addView(TextView(context).apply {
                text =
                    "Direct guest payments continue to be recorded from the Booking Chart."
                textSize = 12f
                setTextColor(Color.rgb(90, 90, 90))
                setPadding(
                    0,
                    dp(4),
                    0,
                    dp(8)
                )
            })

            if (rows.isEmpty()) {
                addView(TextView(context).apply {
                    text =
                        "No pending direct guest balances."
                    textSize = 14f
                    setTextColor(Color.rgb(80, 80, 80))
                    setPadding(
                        0,
                        dp(8),
                        0,
                        dp(4)
                    )
                })
            } else {
                rows.forEach { row ->

                    addView(LinearLayout(context).apply {

                        orientation = HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(
                            0,
                            dp(8),
                            0,
                            dp(8)
                        )

                        addView(TextView(context).apply {
                            text =
                                row.guestName.ifBlank {
                                    "Guest"
                                }
                            textSize = 13f
                            setTextColor(
                                Color.rgb(45, 45, 45)
                            )
                        }, LayoutParams(
                            0,
                            LayoutParams.WRAP_CONTENT,
                            1f
                        ))

                        addView(TextView(context).apply {
                            text =
                                money(row.outstanding)
                            textSize = 15f
                            typeface =
                                Typeface.create(
                                    Typeface.SANS_SERIF,
                                    Typeface.BOLD
                                )
                            setTextColor(
                                Color.parseColor("#FF5A5F")
                            )
                            gravity = Gravity.END
                        })
                    })
                }
            }
        }
    }

    private fun sectionHeader(
        title: String,
        subtitle: String
    ): View {
        return LinearLayout(context).apply {

            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(TextView(context).apply {
                text = title
                textSize = 20f
                typeface =
                    Typeface.create(
                        Typeface.SANS_SERIF,
                        Typeface.BOLD
                    )
                setTextColor(Color.rgb(20, 20, 20))
            }, LayoutParams(
                0,
                LayoutParams.WRAP_CONTENT,
                1f
            ))

            addView(TextView(context).apply {
                text = subtitle
                textSize = 13f
                setTextColor(Color.rgb(75, 75, 75))
                gravity = Gravity.END
            })
        }
    }

    private fun metricStrip(
        items: List<Pair<String, String>>
    ): View {
        return LinearLayout(context).apply {

            orientation = HORIZONTAL
            setPadding(
                0,
                dp(10),
                0,
                dp(6)
            )

            items.forEach { item ->

                addView(LinearLayout(context).apply {

                    orientation = VERTICAL
                    gravity = Gravity.CENTER

                    addView(TextView(context).apply {
                        text = item.first
                        textSize = 11f
                        setTextColor(
                            Color.rgb(95, 95, 95)
                        )
                        gravity = Gravity.CENTER
                    })

                    addView(TextView(context).apply {
                        text = item.second
                        textSize = 15f
                        typeface =
                            Typeface.create(
                                Typeface.SANS_SERIF,
                                Typeface.BOLD
                            )
                        setTextColor(
                            Color.rgb(25, 25, 25)
                        )
                        gravity = Gravity.CENTER
                    })

                }, LayoutParams(
                    0,
                    LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(
                        dp(3),
                        0,
                        dp(3),
                        0
                    )
                })
            }
        }
    }

    private fun sectionCard(): LinearLayout {
        return LinearLayout(context).apply {

            orientation = VERTICAL
            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
            )

            background =
                android.graphics.drawable.GradientDrawable()
                    .apply {
                        setColor(Color.WHITE)
                        cornerRadius =
                            dp(12).toFloat()
                        setStroke(
                            dp(1),
                            Color.parseColor("#E6E1EA")
                        )
                    }

            layoutParams =
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(
                        0,
                        0,
                        0,
                        dp(14)
                    )
                }
        }
    }

    private fun money(
        amount: Double
    ): String {
        return "\u20B9" +
            String.format(
                Locale.getDefault(),
                "%,d",
                amount.roundToInt()
            )
    }

    private fun dp(
        value: Int
    ): Int {
        return (
            value *
                resources.displayMetrics.density
            ).roundToInt()
    }
}
