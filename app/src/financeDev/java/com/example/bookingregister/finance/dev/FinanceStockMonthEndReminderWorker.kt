package com.example.bookingregister.finance.dev

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Local Finance DEV reminder, never an inventory write. WorkManager is best-effort,
 * not an exact end-of-day alarm. The ledger also displays an in-app due/overdue banner.
 */
internal class FinanceStockMonthEndReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
        val today = LocalDate.now()
        val thisMonth = YearMonth.from(today)
        val stock = HotelMonthlyStockStore(applicationContext)
        val first = stock.savedInitialMonths().firstOrNull()?.let(YearMonth::parse)
            ?: return Result.success()
        if (first > thisMonth) return Result.success()
        val lastDay = today.dayOfMonth == thisMonth.lengthOfMonth()
        val previousMonth = thisMonth.minusMonths(1)
        val previousOverdue = today.dayOfMonth == 1 && previousMonth >= first &&
            stock.opening(previousMonth.toString()) != null &&
            stock.all().none { it.month == previousMonth.toString() }
        val currentDue = lastDay && stock.opening(thisMonth.toString()) != null &&
            stock.all().none { it.month == thisMonth.toString() }
        if (!currentDue && !previousOverdue) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "finance_dev_stock_closing"
        if (Build.VERSION.SDK_INT >= 26) notificationManager.createNotificationChannel(
            NotificationChannel(channelId, "Monthly stock closing", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val target = if (currentDue) thisMonth else previousMonth
        val activityIntent = Intent(applicationContext, FinanceDevSafetyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 912, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val message = if (currentDue) "Count and approve the closing quantities for $target today." else
            "Stock closing for $target is overdue. Open Purchase > Monthly Stock to complete it."
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (currentDue) "Monthly stock closing due" else "Stock closing overdue")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        notificationManager.notify(19026, notification)
        Result.success()
        } catch (_: Exception) { Result.retry() }
    }

    companion object {
        private const val WORK_NAME = "finance_dev_monthly_stock_closing_reminder"
        fun schedule(context: Context) {
            val now = ZonedDateTime.now()
            var next = now.withHour(18).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)
            val request = PeriodicWorkRequestBuilder<FinanceStockMonthEndReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
