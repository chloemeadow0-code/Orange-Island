package com.orangeisland.app.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.orangeisland.app.data.AutoBackupManager
import com.orangeisland.app.data.BackupResult
import com.orangeisland.app.data.MemoryManager
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.data.local.ChatDatabase
import com.orangeisland.app.util.DebugLog
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        DebugLog.d("AutoBackup", "Worker: checking backup")
        val settingsManager = SettingsManager(applicationContext)
        val db = ChatDatabase.build(applicationContext)
        val memoryManager = MemoryManager(applicationContext)
        val manager = AutoBackupManager(
            applicationContext,
            settingsManager,
            db.chatDao(),
            memoryManager
        )

        return try {
            when (manager.checkAndBackup()) {
                BackupResult.FAILED -> {
                    DebugLog.w("AutoBackup", "Worker: backup failed, retrying")
                    Result.retry()
                }
                else -> Result.success()
            }
        } catch (e: Exception) {
            DebugLog.e("AutoBackup", "Worker: unexpected error", e)
            Result.retry()
        } finally {
            manager.destroy()
        }
    }

    companion object {
        private const val WORK_NAME = "auto_backup_periodic"
        private const val TAG = "auto_backup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
