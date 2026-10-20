package com.randwise.app.security

import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.randwise.app.AppContainer
import com.randwise.app.data.db.RandWiseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * POPIA right to erasure, done properly: wipes rows, closes and deletes the database files,
 * destroys the encryption key, clears the app password, settings and caches, then restarts
 * the process so nothing stale stays in memory.
 */
class DataEraser(private val context: Context, private val container: AppContainer) {

    suspend fun eraseEverything() = withContext(Dispatchers.IO) {
        runCatching { container.repository.purgeAll() }
        runCatching { container.database.close() }
        context.deleteDatabase(RandWiseDatabase.NAME)
        DatabaseKeyManager(context).destroy()
        container.passcode.clear()
        container.preferences.reset()
        runCatching { WorkManager.getInstance(context).cancelAllWork() }
        context.cacheDir.deleteRecursively()
    }

    /** Relaunches the app in a fresh process (the old database instance cannot be reopened). */
    fun restart() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (intent != null) context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
