package com.randwise.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.ads.MobileAds
import com.randwise.app.ai.CoachClient
import com.randwise.app.data.AppPreferences
import com.randwise.app.data.FinanceRepository
import com.randwise.app.data.db.RandWiseDatabase
import com.randwise.app.importer.DocumentExtractor
import com.randwise.app.security.DatabaseKeyManager
import com.randwise.app.work.BillReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Manual dependency container — small enough that a DI framework adds nothing. */
class AppContainer(private val context: Context) {
    val preferences = AppPreferences(context)
    val database: RandWiseDatabase by lazy { RandWiseDatabase.open(context, DatabaseKeyManager(context).getOrCreateSqlCipherKey()) }
    val repository: FinanceRepository by lazy { FinanceRepository(database) }
    val extractor by lazy { DocumentExtractor(context) }
    val coachClient by lazy { CoachClient() }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class RandWiseApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
        scheduleReminders(container.preferences.settings.value.billReminders)
        // Open (and key) the encrypted database off the main thread while the first frame renders.
        container.appScope.launch(Dispatchers.IO) { runCatching { container.database.openHelper.writableDatabase } }
        container.appScope.launch(Dispatchers.IO) { MobileAds.initialize(this@RandWiseApplication) }
    }

    fun scheduleReminders(enabled: Boolean) {
        val wm = WorkManager.getInstance(this)
        if (!enabled) { wm.cancelUniqueWork(BillReminderWorker.NAME); return }
        val req = PeriodicWorkRequestBuilder<BillReminderWorker>(1, TimeUnit.DAYS).build()
        wm.enqueueUniquePeriodicWork(BillReminderWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(BillReminderWorker.CHANNEL, getString(R.string.channel_bills), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}
