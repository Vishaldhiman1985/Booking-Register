package com.example.bookingregister.utils.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.bookingregister.data.entities.FoodBillEntity
import com.example.bookingregister.data.entities.FoodBillItemEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class InvoicePdfGenerator(
    private val context: Context
) {

    data class BusinessHeader(
        val name: String,
        val address: String?,
        val gstin: String?,
        val phone: String?
    )

    fun createFoodBillPdf(
        bill: FoodBillEntity,
        items: List<FoodBillItemEntity>,
        header: BusinessHeader
    ): File {
        val pdf = PdfDocument()

        val pageWidth = 595
        val pageHeight = 842
        val margin = 28
        var y = 36

        var page = pdf.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        )
        var canvas = page.canvas

        val titlePaint = paint(18f, true, Color.BLACK)
        val boldPaint = paint(10f, true, Color.BLACK)
        val normalPaint = paint(9f, false, Color.BLACK)
        val smallPaint = paint(8f, false, Color.DKGRAY)
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        fun newPage() {
            pdf.finishPage(page)
            page = pdf.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdf.pages.size + 1).create()
            )
            canvas = page.canvas
            y = 36
        }

        fun ensureSpace(required: Int) {
            if (y + required > pageHeight - 70) newPage()
        }

        fun text(value: String, x: Int, yy: Int, p: Paint = normalPaint) {
            canvas.drawText(value, x.toFloat(), yy.toFloat(), p)
        }

        fun money(value: Double): String = "%.2f".format(value)

        fun line() {
            canvas.drawLine(margin.toFloat(), y.toFloat(), (pageWidth - margin).toFloat(), y.toFloat(), linePaint)
            y += 12
        }

        text(header.name.uppercase(), margin, y, titlePaint)
        y += 18

        header.address?.takeIf { it.isNotBlank() }?.let {
            text(it.take(90), margin, y, smallPaint)
            y += 12
        }

        val gstPhone = buildString {
            if (!header.gstin.isNullOrBlank()) append("GSTIN: ${header.gstin}")
            if (!header.phone.isNullOrBlank()) {
                if (isNotBlank()) append("   |   ")
                append("Phone: ${header.phone}")
            }
        }
        if (gstPhone.isNotBlank()) {
            text(gstPhone, margin, y, smallPaint)
            y += 14
        }

        line()

        text(if (bill.isConsolidatedGuestBill()) "CONSOLIDATED GUEST BILL" else "TAX INVOICE - FOOD BILL", margin, y, boldPaint)
        y += 16

        text("Bill No: ${bill.billNumber}", margin, y, normalPaint)
        text(
            "Date: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(bill.billMillis))}",
            330,
            y,
            normalPaint
        )
        y += 13

        text("Rooms: ${bill.roomsIncluded}", margin, y, normalPaint)
        y += 13

        bill.guestName?.takeIf { it.isNotBlank() }?.let {
            text("Guest: $it", margin, y, normalPaint)
            y += 13
        }

        bill.guestGstin?.takeIf { it.isNotBlank() }?.let {
            text("Guest GSTIN: $it", margin, y, normalPaint)
            y += 13
        }

        y += 4
        line()

        val colX = listOf(28, 170, 220, 260, 310, 370, 425, 480, 535)

        fun tableHeader() {
            text("Item", colX[0], y, boldPaint)
            text("HSN", colX[1], y, boldPaint)
            text("Qty", colX[2], y, boldPaint)
            text("Rate", colX[3], y, boldPaint)
            text("Taxable", colX[4], y, boldPaint)
            text("CGST", colX[5], y, boldPaint)
            text("SGST", colX[6], y, boldPaint)
            text("GST", colX[7], y, boldPaint)
            text("Payable", colX[8], y, boldPaint)
            y += 10
            line()
        }

        tableHeader()

        items.forEach { item ->
            ensureSpace(26)

            text(item.itemName.take(24), colX[0], y, normalPaint)
            text((item.hsnSacCode ?: "-").take(8), colX[1], y, normalPaint)
            text(money(item.quantity), colX[2], y, normalPaint)
            text(money(item.unitPrice), colX[3], y, normalPaint)
            text(money(item.taxableAmount), colX[4], y, normalPaint)
            text(money(item.cgstAmount), colX[5], y, normalPaint)
            text(money(item.sgstAmount), colX[6], y, normalPaint)
            text(money(item.gstAmount), colX[7], y, normalPaint)
            text(money(item.lineTotal), colX[8], y, normalPaint)

            y += 15
        }

        y += 4
        line()

        ensureSpace(90)
        text("GST SUMMARY", margin, y, boldPaint)
        y += 16

        text("HSN/SAC", margin, y, boldPaint)
        text("GST%", 130, y, boldPaint)
        text("Taxable", 190, y, boldPaint)
        text("CGST", 270, y, boldPaint)
        text("SGST", 340, y, boldPaint)
        text("Total GST", 410, y, boldPaint)
        y += 12

        items.groupBy { "${it.hsnSacCode ?: "-"}|${it.gstRatePercent}" }
            .forEach { (_, group) ->
                val first = group.first()
                text(first.hsnSacCode ?: "-", margin, y, normalPaint)
                text(money(first.gstRatePercent), 130, y, normalPaint)
                text(money(group.sumOf { it.taxableAmount }), 190, y, normalPaint)
                text(money(group.sumOf { it.cgstAmount }), 270, y, normalPaint)
                text(money(group.sumOf { it.sgstAmount }), 340, y, normalPaint)
                text(money(group.sumOf { it.gstAmount }), 410, y, normalPaint)
                y += 13
            }

        y += 4
        line()

        ensureSpace(105)

        fun totalRow(label: String, value: Double, bold: Boolean = false) {
            val p = if (bold) boldPaint else normalPaint
            text(label, 350, y, p)
            text("₹${money(value)}", 470, y, p)
            y += 14
        }

        totalRow("Subtotal", bill.subtotal)
        if (bill.discountAmount > 0.0) totalRow("Discount", bill.discountAmount)
        totalRow("Taxable", bill.taxableAmount)
        totalRow("CGST", bill.cgstAmount)
        totalRow("SGST", bill.sgstAmount)
        if (bill.cessAmount > 0.0) totalRow("CESS", bill.cessAmount)
        totalRow("Total GST", bill.gstAmount)
        totalRow("Grand Total", bill.grandTotal, true)

        y += 28

        text("Hotel Stamp & Authorized Signature", 350, y, boldPaint)
        y += 28
        canvas.drawLine(350f, y.toFloat(), 560f, y.toFloat(), linePaint)

        pdf.finishPage(page)

        val dir = File(context.cacheDir, "food_bills")
        if (!dir.exists()) dir.mkdirs()

        val safeName = bill.billNumber.replace("/", "_").replace("\\", "_")
        val file = File(dir, "$safeName.pdf")

        FileOutputStream(file).use {
            pdf.writeTo(it)
        }

        pdf.close()
        return file
    }

    private fun FoodBillEntity.isConsolidatedGuestBill(): Boolean = remoteId.contains("_final_bill_")
    private data class ConsolidatedBillSummary(
        val stay: Double,
        val food: Double,
        val paid: Double,
        val balance: Double
    )

    private fun FoodBillEntity.consolidatedSummary(): ConsolidatedBillSummary {
        val note = notes.orEmpty()
        fun value(label: String): Double {
            val regex = Regex("$label\\s+([0-9.]+)", RegexOption.IGNORE_CASE)
            return regex.find(note)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        }
        return ConsolidatedBillSummary(
            stay = value("Stay"),
            food = value("Food"),
            paid = value("Paid"),
            balance = value("Balance")
        )
    }

    private fun paint(size: Float, bold: Boolean, colorValue: Int): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorValue
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }
}
