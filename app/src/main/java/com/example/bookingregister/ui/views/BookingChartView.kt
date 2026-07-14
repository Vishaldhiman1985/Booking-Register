package com.example.bookingregister.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.OverScroller
import androidx.core.view.GestureDetectorCompat
import com.example.bookingregister.booking.domain.BookingStatus
import com.example.bookingregister.data.repository.PaymentStatus
import com.example.bookingregister.data.SyncState
import com.example.bookingregister.data.entities.BookingEntity
import com.example.bookingregister.data.entities.BookingSourceType
import com.example.bookingregister.data.entities.RoomEntity
import com.example.bookingregister.room.domain.RoomLifecyclePolicy
import com.example.bookingregister.room.domain.RoomLifecycleStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class BookingChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        fun onEmptyCellClicked(room: RoomEntity, dateMillis: Long)
        fun onBookingClicked(booking: BookingEntity)
    }

    private enum class DragAxis {
        NONE, HORIZONTAL, VERTICAL
    }

    private sealed class ChartRow {
        data class Room(val room: RoomEntity) : ChartRow()
    }

    private var rooms: List<RoomEntity> = emptyList()
    private var bookings: List<BookingEntity> = emptyList()
    private var chartRows: List<ChartRow> = emptyList()
    private var listener: Listener? = null

    private val roomColumnWidth = dpToPx(96f)
    private val dayColumnWidth = dpToPx(80f)
    private val headerHeight = dpToPx(40f)
    private val rowHeight = dpToPx(45f)
    private val bookingCornerRadius = dpToPx(4f)
    private val bookingHorizontalPadding = dpToPx(1.5f)
    private val bookingVerticalPadding = dpToPx(5f)
    private val bookingTextHorizontalPadding = dpToPx(6f)
    private val touchSlop = dpToPx(8f)

    private var horizontalScroll = 0f
    private var verticalScroll = 0f
    private var maxHorizontalScroll = 0f
    private var maxVerticalScroll = 0f
    private var downX = 0f
    private var downY = 0f
    private var isDragging = false
    private var lockedDragAxis = DragAxis.NONE
    private var visibleMonthLabel = ""

    private val minDayOffset = -3650
    private val maxDayOffset = 3650
    private val dayMillis = 24L * 60L * 60L * 1000L
    private val anchorDate: Date = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private val scroller = OverScroller(context)
    private val gestureDetector: GestureDetectorCompat

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FAFAFA")
        style = Paint.Style.FILL
    }
    private val roomBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FCFCFC")
        style = Paint.Style.FILL
    }
    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DDDDDD")
        strokeWidth = dpToPx(0.7f)
        style = Paint.Style.STROKE
    }
    private val todayHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F3F3F3")
        style = Paint.Style.FILL
    }
    private val weekendHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F8F8F8")
        style = Paint.Style.FILL
    }
    private val dateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = spToPx(11f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val dayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = spToPx(10f)
        textAlign = Paint.Align.CENTER
    }
    private val roomTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = spToPx(11f)
        isFakeBoldText = true
    }
    private val cornerMonthTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = spToPx(10f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val bookingTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#202124")
        textSize = spToPx(11f)
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        isFakeBoldText = false
        style = Paint.Style.FILL
        strokeWidth = 0f
        clearShadowLayer()
    }

    private val bookingFullyPaidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#98D9AE")
        style = Paint.Style.FILL
    }
    private val bookingPartiallyPaidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E7E397")
        style = Paint.Style.FILL
    }
    private val bookingNotPaidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DF7B7B")
        style = Paint.Style.FILL
    }
    private val bookingComplimentaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D6D6D6")
        style = Paint.Style.FILL
    }
    private val bookingBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66000000")
        strokeWidth = dpToPx(1f)
        style = Paint.Style.STROKE
    }
    private val statusIconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val statusIconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = dpToPx(1.4f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val statusIconMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = dpToPx(1.8f)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val textBounds = Rect()
    private val dayNumberFormat = SimpleDateFormat("dd", Locale.getDefault())
    private val dayNameFormat = SimpleDateFormat("EEE", Locale.getDefault())
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    init {
        isClickable = true
        isFocusable = true

        gestureDetector = GestureDetectorCompat(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean {
                    if (!scroller.isFinished) scroller.forceFinished(true)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {
                    when (lockedDragAxis) {
                        DragAxis.HORIZONTAL -> {
                            horizontalScroll = clamp(horizontalScroll + distanceX, 0f, maxHorizontalScroll)
                            rebuildChartRowsForViewport()
                            notifyVisibleMonth()
                            invalidate()
                        }
                        DragAxis.VERTICAL -> {
                            verticalScroll = clamp(verticalScroll + distanceY, 0f, maxVerticalScroll)
                            invalidate()
                        }
                        DragAxis.NONE -> Unit
                    }
                    return true
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    when (lockedDragAxis) {
                        DragAxis.HORIZONTAL -> {
                            scroller.fling(
                                horizontalScroll.toInt(),
                                verticalScroll.toInt(),
                                (-velocityX).toInt(),
                                0,
                                0,
                                maxHorizontalScroll.toInt(),
                                verticalScroll.toInt(),
                                verticalScroll.toInt()
                            )
                        }
                        DragAxis.VERTICAL -> {
                            scroller.fling(
                                horizontalScroll.toInt(),
                                verticalScroll.toInt(),
                                0,
                                (-velocityY).toInt(),
                                horizontalScroll.toInt(),
                                horizontalScroll.toInt(),
                                0,
                                maxVerticalScroll.toInt()
                            )
                        }
                        DragAxis.NONE -> return false
                    }
                    postInvalidateOnAnimation()
                    return true
                }
            }
        )
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun setData(r: List<RoomEntity>, b: List<BookingEntity>) {
        rooms = r
        bookings = b
        rebuildChartRowsForViewport()
        recalculateScrollBounds()
        invalidate()
    }

    fun setDays(d: List<Date>) {
        notifyVisibleMonth()
        invalidate()
    }

    fun getTodayColumnIndex(): Int = -minDayOffset

    fun scrollToDay(index: Int) {
        horizontalScroll = clamp(index * dayColumnWidth, 0f, maxHorizontalScroll)
        rebuildChartRowsForViewport()
        notifyVisibleMonth()
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isDragging = false
                lockedDragAxis = DragAxis.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val absDx = kotlin.math.abs(dx)
                val absDy = kotlin.math.abs(dy)
                if (!isDragging && (absDx > touchSlop || absDy > touchSlop)) {
                    isDragging = true
                    lockedDragAxis = if (absDx >= absDy) DragAxis.HORIZONTAL else DragAxis.VERTICAL
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    handleTap(event.x, event.y)
                    performClick()
                }
                lockedDragAxis = DragAxis.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                lockedDragAxis = DragAxis.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            horizontalScroll = scroller.currX.toFloat()
            verticalScroll = scroller.currY.toFloat()
            rebuildChartRowsForViewport()
            notifyVisibleMonth()
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawGrid(canvas)
        drawBookings(canvas)
        drawHeader(canvas)
        drawRoomColumn(canvas)
        drawCornerCell(canvas)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateScrollBounds()
        rebuildChartRowsForViewport()
        notifyVisibleMonth()
    }

    private fun drawGrid(canvas: Canvas) {
        if (chartRows.isEmpty()) return

        val saveCount = canvas.save()
        canvas.clipRect(roomColumnWidth, headerHeight, width.toFloat(), height.toFloat())

        val startRow = max(0, (verticalScroll / rowHeight).toInt())
        val endRow = min(chartRows.lastIndex, startRow + ceil((height - headerHeight) / rowHeight).toInt() + 2)
        val startColumn = max(0, (horizontalScroll / dayColumnWidth).toInt())
        val endColumn = min(getTotalDayCount() - 1, startColumn + ceil((width - roomColumnWidth) / dayColumnWidth).toInt() + 2)

        for (row in startRow..endRow) {
            val top = headerHeight + row * rowHeight - verticalScroll
            val bottom = top + rowHeight
            if (bottom < headerHeight || top > height) continue

            for (column in startColumn..endColumn) {
                val left = roomColumnWidth + column * dayColumnWidth - horizontalScroll
                val right = left + dayColumnWidth
                if (right < roomColumnWidth || left > width) continue

                val day = getDateForColumn(column)
                val cellPaint = when {
                    isToday(day) -> todayHighlightPaint
                    isWeekend(day) -> weekendHighlightPaint
                    else -> bgPaint
                }
                canvas.drawRect(left, top, right, bottom, cellPaint)
                canvas.drawRect(left, top, right, bottom, gridLinePaint)
            }
        }

        canvas.restoreToCount(saveCount)
    }

    private fun drawHeader(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.clipRect(roomColumnWidth, 0f, width.toFloat(), headerHeight)

        canvas.drawRect(roomColumnWidth, 0f, width.toFloat(), headerHeight, headerBgPaint)

        val startColumn = max(0, (horizontalScroll / dayColumnWidth).toInt())
        val endColumn = min(getTotalDayCount() - 1, startColumn + ceil((width - roomColumnWidth) / dayColumnWidth).toInt() + 2)

        for (column in startColumn..endColumn) {
            val left = roomColumnWidth + column * dayColumnWidth - horizontalScroll
            val right = left + dayColumnWidth
            if (right < roomColumnWidth || left > width) continue

            val day = getDateForColumn(column)
            val bg = when {
                isToday(day) -> todayHighlightPaint
                isWeekend(day) -> weekendHighlightPaint
                else -> headerBgPaint
            }
            canvas.drawRect(left, 0f, right, headerHeight, bg)

            val centerX = left + dayColumnWidth / 2f
            canvas.drawText(dayNumberFormat.format(day), centerX, headerHeight / 2f - dpToPx(4f), dateTextPaint)
            canvas.drawText(dayNameFormat.format(day), centerX, headerHeight / 2f + dpToPx(14f), dayTextPaint)
            canvas.drawLine(left, 0f, left, headerHeight, gridLinePaint)
        }

        canvas.drawLine(roomColumnWidth, 0f, roomColumnWidth, headerHeight, gridLinePaint)
        canvas.drawLine(roomColumnWidth, headerHeight, width.toFloat(), headerHeight, gridLinePaint)
        canvas.restoreToCount(saveCount)
    }

    private fun drawRoomColumn(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.clipRect(0f, headerHeight, roomColumnWidth, height.toFloat())

        canvas.drawRect(0f, headerHeight, roomColumnWidth, height.toFloat(), roomBgPaint)

        if (chartRows.isNotEmpty()) {
            val startRow = max(0, (verticalScroll / rowHeight).toInt())
            val endRow = min(chartRows.lastIndex, startRow + ceil((height - headerHeight) / rowHeight).toInt() + 2)

            for (row in startRow..endRow) {
                val top = headerHeight + row * rowHeight - verticalScroll
                val bottom = top + rowHeight
                if (bottom < headerHeight || top > height) continue

                val chartRow = chartRows[row] as ChartRow.Room
                canvas.drawRect(0f, top, roomColumnWidth, bottom, roomBgPaint)
                drawRoomRow(canvas, chartRow.room, top, bottom)

                canvas.drawLine(0f, top, roomColumnWidth, top, gridLinePaint)
            }
        }
        canvas.restoreToCount(saveCount)
    }

    private fun drawRoomRow(canvas: Canvas, room: RoomEntity, top: Float, bottom: Float) {
        val displayName = when (RoomLifecycleStatus.normalize(room.lifecycleStatus)) {
            RoomLifecycleStatus.RETIRED -> "${room.roomName} [Retired]"
            RoomLifecycleStatus.DISABLED -> "${room.roomName} [Disabled]"
            else -> room.roomName
        }
        val roomName = TextUtils.ellipsize(
            displayName,
            TextPaint(roomTextPaint),
            roomColumnWidth - dpToPx(16f),
            TextUtils.TruncateAt.END
        ).toString()

        roomTextPaint.getTextBounds(roomName, 0, roomName.length, textBounds)
        canvas.drawText(
            roomName,
            dpToPx(16f),
            top + rowHeight / 2f + textBounds.height() / 2f,
            roomTextPaint
        )
    }

    private fun drawCornerCell(canvas: Canvas) {
        canvas.drawRect(0f, 0f, roomColumnWidth, headerHeight, headerBgPaint)

        val label = visibleMonthLabel.ifBlank { monthYearFormat.format(anchorDate) }
        val clippedLabel = TextUtils.ellipsize(
            label,
            TextPaint(cornerMonthTextPaint),
            roomColumnWidth - dpToPx(8f),
            TextUtils.TruncateAt.END
        ).toString()

        cornerMonthTextPaint.getTextBounds(clippedLabel, 0, clippedLabel.length, textBounds)
        canvas.drawText(clippedLabel, roomColumnWidth / 2f, headerHeight / 2f + textBounds.height() / 2f, cornerMonthTextPaint)

        canvas.drawLine(roomColumnWidth, 0f, roomColumnWidth, headerHeight, gridLinePaint)
        drawRoomColumnDividerBelowHeader(canvas)
        canvas.drawLine(0f, headerHeight, width.toFloat(), headerHeight, gridLinePaint)
    }

    private fun drawRoomColumnDividerBelowHeader(canvas: Canvas) {
        chartRows.forEachIndexed { index, _ ->
            val top = headerHeight + index * rowHeight - verticalScroll
            val bottom = top + rowHeight
            if (bottom < headerHeight || top > height) return@forEachIndexed
            canvas.drawLine(
                roomColumnWidth,
                max(top, headerHeight),
                roomColumnWidth,
                min(bottom, height.toFloat()),
                gridLinePaint
            )
        }
    }

    private fun drawBookings(canvas: Canvas) {
        if (chartRows.isEmpty() || bookings.isEmpty()) return

        val saveCount = canvas.save()
        canvas.clipRect(roomColumnWidth, headerHeight, width.toFloat(), height.toFloat())

        val roomIndexMap = chartRows.withIndex()
            .mapNotNull { indexed ->
                val roomRow = indexed.value as? ChartRow.Room ?: return@mapNotNull null
                roomRow.room.remoteId to indexed.index
            }
            .toMap()

        val startVisibleColumn = max(0, (horizontalScroll / dayColumnWidth).toInt())
        val endVisibleColumn = min(getTotalDayCount() - 1, startVisibleColumn + ceil((width - roomColumnWidth) / dayColumnWidth).toInt() + 2)
        val startVisibleRow = max(0, (verticalScroll / rowHeight).toInt())
        val endVisibleRow = min(chartRows.lastIndex, startVisibleRow + ceil((height - headerHeight) / rowHeight).toInt() + 2)

        for (booking in bookings) {
            val checkIn = startOfDay(booking.checkInMillis)
            val checkOut = startOfDay(booking.checkOutMillis)
            val startColumn = getColumnIndexForDate(checkIn)
            val endColumnExclusive = getColumnIndexForDate(checkOut).coerceAtLeast(startColumn + 1)

            if (endColumnExclusive <= startVisibleColumn || startColumn > endVisibleColumn) continue

            for (roomId in booking.roomRemoteIds) {
                val rowIndex = roomIndexMap[roomId] ?: continue
                if (rowIndex < startVisibleRow || rowIndex > endVisibleRow) continue

                val rowTop = headerHeight + rowIndex * rowHeight - verticalScroll
                val rowBottom = rowTop + rowHeight
                val left = roomColumnWidth + startColumn * dayColumnWidth - horizontalScroll + bookingHorizontalPadding
                val right = roomColumnWidth + endColumnExclusive * dayColumnWidth - horizontalScroll - bookingHorizontalPadding
                val top = rowTop + bookingVerticalPadding
                val bottom = rowBottom - bookingVerticalPadding

                if (right <= roomColumnWidth || left >= width || bottom <= headerHeight || top >= height) continue

                val clippedLeft = max(left, roomColumnWidth)
                val clippedRight = min(right, width.toFloat())
                if (clippedRight <= clippedLeft) continue

                canvas.drawRoundRect(
                    clippedLeft,
                    top,
                    clippedRight,
                    bottom,
                    bookingCornerRadius,
                    bookingCornerRadius,
                    getBookingPaint(booking)
                )
                drawBookingText(canvas, booking, clippedLeft, clippedRight, top, bottom)
                drawBookingStatusIcon(canvas, booking, clippedLeft, clippedRight, top)
            }
        }

        canvas.restoreToCount(saveCount)
    }

    private fun drawBookingStatusIcon(
        canvas: Canvas,
        booking: BookingEntity,
        left: Float,
        right: Float,
        top: Float
    ) {
        val status = booking.bookingStatus
        if (status != BookingStatus.CHECKED_IN && status != BookingStatus.CHECKED_OUT) return

        val width = right - left
        if (width < dpToPx(14f)) return

        val radius = dpToPx(6f)
        val centerX = (right - dpToPx(10f)).coerceAtLeast(left + radius + dpToPx(2f))
        val centerY = top + dpToPx(9f)

        statusIconFillPaint.color = if (status == BookingStatus.CHECKED_IN) {
            Color.parseColor("#1A7F37")
        } else {
            Color.parseColor("#B42318")
        }

        canvas.drawCircle(centerX, centerY, radius, statusIconFillPaint)

        if (status == BookingStatus.CHECKED_IN) {
            canvas.drawLine(
                centerX - dpToPx(2.8f),
                centerY,
                centerX - dpToPx(0.8f),
                centerY + dpToPx(2.2f),
                statusIconMarkPaint
            )
            canvas.drawLine(
                centerX - dpToPx(0.8f),
                centerY + dpToPx(2.2f),
                centerX + dpToPx(3.2f),
                centerY - dpToPx(2.8f),
                statusIconMarkPaint
            )
        } else {
            canvas.drawLine(
                centerX - dpToPx(2.8f),
                centerY - dpToPx(2.8f),
                centerX + dpToPx(2.8f),
                centerY + dpToPx(2.8f),
                statusIconMarkPaint
            )
            canvas.drawLine(
                centerX + dpToPx(2.8f),
                centerY - dpToPx(2.8f),
                centerX - dpToPx(2.8f),
                centerY + dpToPx(2.8f),
                statusIconMarkPaint
            )
        }
    }

    private fun drawBookingText(
        canvas: Canvas,
        booking: BookingEntity,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ) {
        val availableWidth = right - left - (2 * bookingTextHorizontalPadding)
        if (availableWidth <= 0f) return

        val baseLabel = booking.guestName.ifBlank { "Booking" }
        val label = when (booking.syncState) {
            SyncState.PENDING -> "$baseLabel [Syncing]"
            SyncState.FAILED -> "$baseLabel [Not synced]"
            else -> baseLabel
        }
        bookingTextPaint.color = Color.WHITE
        bookingTextPaint.style = Paint.Style.FILL
        bookingTextPaint.strokeWidth = 0f
        bookingTextPaint.isFakeBoldText = false
        bookingTextPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        bookingTextPaint.clearShadowLayer()

        val clippedText = TextUtils.ellipsize(
            label,
            TextPaint(bookingTextPaint),
            availableWidth,
            TextUtils.TruncateAt.END
        ).toString()

        bookingTextPaint.color = Color.parseColor("#202124")
        bookingTextPaint.style = Paint.Style.FILL
        bookingTextPaint.strokeWidth = 0f
        bookingTextPaint.isFakeBoldText = false
        bookingTextPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        bookingTextPaint.clearShadowLayer()

        bookingTextPaint.getTextBounds(clippedText, 0, clippedText.length, textBounds)
        canvas.drawText(

            clippedText,
            left + bookingTextHorizontalPadding,
            top + ((bottom - top) / 2f) + (textBounds.height() / 2f),
            bookingTextPaint
        )
    }

    private fun handleTap(x: Float, y: Float) {
        if (x < roomColumnWidth || y < headerHeight) return

        val rowIndex = ((y - headerHeight + verticalScroll) / rowHeight).toInt()
        if (rowIndex !in chartRows.indices) return

        val columnIndex = ((x - roomColumnWidth + horizontalScroll) / dayColumnWidth).toInt()
        if (columnIndex !in 0 until getTotalDayCount()) return

        val room = (chartRows[rowIndex] as? ChartRow.Room)?.room ?: return
        val tappedDate = getDateForColumn(columnIndex)
        val tappedBooking = findBookingAt(room.remoteId, tappedDate.time, x, y)

        if (tappedBooking != null) {
            listener?.onBookingClicked(tappedBooking)
        } else {
            listener?.onEmptyCellClicked(room, tappedDate.time)
        }
    }

    private fun findBookingAt(roomRemoteId: String, tappedMillis: Long, x: Float, y: Float): BookingEntity? {
        val normalizedTappedMillis = startOfDay(tappedMillis)
        val roomIndexMap = chartRows.withIndex()
            .mapNotNull { indexed ->
                val roomRow = indexed.value as? ChartRow.Room ?: return@mapNotNull null
                roomRow.room.remoteId to indexed.index
            }
            .toMap()

        for (booking in bookings.reversed()) {
            if (roomRemoteId !in booking.roomRemoteIds) continue

            val rowIndex = roomIndexMap[roomRemoteId] ?: continue
            val checkIn = startOfDay(booking.checkInMillis)
            val checkOut = startOfDay(booking.checkOutMillis)
            val isInRange = normalizedTappedMillis >= checkIn && normalizedTappedMillis < checkOut
            if (!isInRange) continue

            val startColumn = getColumnIndexForDate(checkIn)
            val endColumnExclusive = getColumnIndexForDate(checkOut).coerceAtLeast(startColumn + 1)
            val rowTop = headerHeight + rowIndex * rowHeight - verticalScroll
            val rowBottom = rowTop + rowHeight
            val left = roomColumnWidth + startColumn * dayColumnWidth - horizontalScroll + bookingHorizontalPadding
            val right = roomColumnWidth + endColumnExclusive * dayColumnWidth - horizontalScroll - bookingHorizontalPadding
            val top = rowTop + bookingVerticalPadding
            val bottom = rowBottom - bookingVerticalPadding

            if (x in left..right && y in top..bottom) return booking
        }

        return null
    }

    private fun getBookingPaint(booking: BookingEntity): Paint {
        if (booking.sourceType == BookingSourceType.OTA) return bookingFullyPaidPaint
        return when (booking.paymentStatus) {
            PaymentStatus.FULLY_PAID -> bookingFullyPaidPaint
            PaymentStatus.PARTIALLY_PAID -> bookingPartiallyPaidPaint
            PaymentStatus.COMPLIMENTARY -> bookingComplimentaryPaint
            else -> bookingNotPaidPaint
        }
    }

    private fun recalculateScrollBounds() {
        val contentWidth = roomColumnWidth + getTotalDayCount() * dayColumnWidth
        val contentHeight = headerHeight + chartRows.size * rowHeight
        maxHorizontalScroll = max(0f, contentWidth - width.toFloat())
        maxVerticalScroll = max(0f, contentHeight - height.toFloat())
        horizontalScroll = clamp(horizontalScroll, 0f, maxHorizontalScroll)
        verticalScroll = clamp(verticalScroll, 0f, maxVerticalScroll)
    }

    private fun notifyVisibleMonth() {
        val columnIndex = max(0, (horizontalScroll / dayColumnWidth).toInt())
        visibleMonthLabel = monthYearFormat.format(getDateForColumn(columnIndex))
    }

    private fun rebuildChartRowsForViewport() {
        val startColumn = max(0, (horizontalScroll / dayColumnWidth).toInt())
        val visibleColumns = ceil(((width - roomColumnWidth).coerceAtLeast(dayColumnWidth)) / dayColumnWidth)
            .toInt()
            .coerceAtLeast(1)
        val endColumn = min(getTotalDayCount() - 1, startColumn + visibleColumns)
        val windowStart = startOfDay(getDateForColumn(startColumn).time)
        val windowEnd = startOfDay(getDateForColumn(endColumn).time) + dayMillis
        val now = startOfDay(System.currentTimeMillis())

        val visibleRooms = rooms.filter { room ->
            RoomLifecyclePolicy.isVisibleInChartWindow(
                room = room,
                bookings = bookings,
                windowStartMillis = windowStart,
                windowEndMillis = windowEnd,
                nowMillis = now
            )
        }.toMutableList()

        val knownRoomIds = rooms.mapTo(mutableSetOf()) { it.remoteId }
        bookings.asSequence()
            .filter { !it.isDeleted && it.checkInMillis < windowEnd && it.checkOutMillis > windowStart }
            .flatMap { booking ->
                booking.roomRemoteIds.asSequence()
                    .filterNot { it in knownRoomIds }
                    .map { missingId -> booking to missingId }
            }
            .distinctBy { it.second }
            .map { (booking, missingId) ->
                RoomEntity(
                    remoteId = missingId,
                    hotelRemoteId = booking.hotelRemoteId,
                    propertyRemoteId = booking.propertyRemoteId,
                    roomName = "Unavailable room (${missingId.takeLast(6)})",
                    lifecycleStatus = RoomLifecycleStatus.RETIRED
                )
            }
            .forEach { visibleRooms += it }

        chartRows = buildChartRows(visibleRooms)
        recalculateScrollBounds()
    }

    private fun buildChartRows(sourceRooms: List<RoomEntity>): List<ChartRow> {
        return sourceRooms
            .sortedWith(compareBy<RoomEntity> { it.sortOrder }.thenBy { it.roomName })
            .map { ChartRow.Room(it) }
    }

    private fun getTotalDayCount(): Int = maxDayOffset - minDayOffset + 1

    private fun getDateForColumn(columnIndex: Int): Date {
        val dayOffset = minDayOffset + columnIndex
        return Calendar.getInstance().apply {
            time = anchorDate
            add(Calendar.DAY_OF_MONTH, dayOffset)
        }.time
    }

    private fun getColumnIndexForDate(millis: Long): Int {
        val diff = (startOfDay(millis) - startOfDay(anchorDate.time)) / dayMillis
        return (diff.toInt() - minDayOffset).coerceIn(0, getTotalDayCount() - 1)
    }

    private fun isToday(date: Date): Boolean {
        return startOfDay(date.time) == startOfDay(System.currentTimeMillis())
    }

    private fun isWeekend(date: Date): Boolean {
        val cal = Calendar.getInstance().apply { time = date }
        val day = cal.get(Calendar.DAY_OF_WEEK)
        return day == Calendar.FRIDAY || day == Calendar.SATURDAY
    }

    private fun startOfDay(millis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun clamp(value: Float, minValue: Float, maxValue: Float): Float {
        return max(minValue, min(value, maxValue))
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun spToPx(sp: Float): Float {
        return sp * resources.displayMetrics.scaledDensity
    }
}

