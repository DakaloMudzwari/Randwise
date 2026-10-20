package com.randwise.app.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.randwise.app.MainActivity
import com.randwise.app.R
import com.randwise.app.RandWiseApplication
import com.randwise.app.core.Money
import com.randwise.app.domain.FinanceAnalyzer
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Daily WorkManager job: reminds about unpaid bills due within two days and low safe-to-spend. */
class BillReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as RandWiseApplication
        if (!app.container.preferences.settings.value.billReminders) return Result.success()
        val snapshot = app.container.repository.snapshot.first()
        val today = LocalDate.now()
        val analysis = FinanceAnalyzer.analyze(snapshot, today) ?: return Result.success()

        val dueSoon = analysis.safeToSpend.unpaidBills.filter { ChronoUnit.DAYS.between(today, it.dueDate) in 0..2 }
        if (dueSoon.isNotEmpty()) {
            val total = dueSoon.sumOf { it.bill.expectedAmountMinor }
            val names = dueSoon.joinToString { it.bill.name }
            notify(1001, "${dueSoon.size} bill(s) due soon", "$names — ${Money.format(total)} total. Keep this in your account.")
        }
        if (analysis.safeToSpend.safeToSpend < 0) {
            notify(1002, "Below your safety buffer", "Safe-to-spend is ${Money.format(analysis.safeToSpend.safeToSpend)} with ${analysis.safeToSpend.daysUntilPayday} days to payday.")
        }
        return Result.success()
    }

    private fun notify(id: Int, title: String, body: String) {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val intent = PendingIntent.getActivity(
            applicationContext, 0, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_randwise)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id, n)
    }

    companion object {
        const val NAME = "bill_reminders"
        const val CHANNEL = "bills"
    }
}
