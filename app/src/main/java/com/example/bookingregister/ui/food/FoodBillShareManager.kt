package com.example.bookingregister.ui.food

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.bookingregister.data.entities.FoodBillEntity
import com.example.bookingregister.data.entities.FoodBillItemEntity
import com.example.bookingregister.utils.pdf.InvoicePdfGenerator

/**
 * Handles food-bill PDF creation and Android share intent.
 *
 * Keep this class UI-light and billing-logic-free:
 * - tax values must already be stored on FoodBillEntity/FoodBillItemEntity
 * - PDF layout stays inside InvoicePdfGenerator
 * - Activity/Fragment only decides when user wants to share
 */
class FoodBillShareManager(
    private val context: Context
) {

    fun shareFoodBillPdf(
        bill: FoodBillEntity,
        items: List<FoodBillItemEntity>,
        header: InvoicePdfGenerator.BusinessHeader
    ) {
        val file = InvoicePdfGenerator(context).createFoodBillPdf(
            bill = bill,
            items = items,
            header = header
        )

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val title = if (bill.remoteId.contains("_final_bill_")) "Share Consolidated Bill PDF" else "Share Food Bill PDF"
        context.startActivity(Intent.createChooser(shareIntent, title))
    }
}
