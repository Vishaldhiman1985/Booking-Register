package com.example.bookingregister.reporting.property

import android.content.Context
import androidx.room.withTransaction
import com.example.bookingregister.data.AppDatabase

/**
 * Read-only adapter over the existing Room database.
 *
 * Important: there are intentionally no save/update/delete/sync methods here.
 * For this isolated test model it reuses existing DAO reads without changing any DAO.
 */
class PropertyReportingDataSource(
    context: Context,
    private val hotelRemoteId: String
) {
    private val db = AppDatabase.getInstance(context.applicationContext)

    init {
        require(hotelRemoteId.isNotBlank()) { "Hotel id is required for reporting." }
    }

    suspend fun loadRawData(): PropertyReportRawData = db.withTransaction {
        // One read transaction gives every report component the same
        // point-in-time Room database snapshot.
        val properties = db.managedPropertyDao().getProperties(hotelRemoteId)
        val rooms = db.roomDao().getRooms(hotelRemoteId)
        val bookings = db.bookingDao().getBookings(hotelRemoteId)
        val payments = db.bookingPaymentDao().getPayments(hotelRemoteId)
        val financialLines = db.bookingFinancialLineDao().getLines(hotelRemoteId)

        PropertyReportRawData(
            properties = properties,
            rooms = rooms,
            bookings = bookings,
            financialLines = financialLines,
            payments = payments
        )
    }
}
