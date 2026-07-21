package com.orangeisland.app.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.orangeisland.app.MainActivity
import com.orangeisland.app.R
import com.orangeisland.app.data.local.ChatDao
import com.orangeisland.app.util.DebugLog
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BackupResult { NOT_DUE, SUCCESS, FAILED }

class AutoBackupManager(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val chatDao: ChatDao,
    private val memoryManager: MemoryManager,
    private val workflowRepository: com.orangeisland.app.data.repository.WorkflowRepository? = null
) {
    companion object {
        /** Cross-instance Mutex — ensures Worker and ChatViewModel instances don't race. */
        private val backupMutex = Mutex()
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "auto_backup"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Public API ────────────────────────────────────────────

    /**
     * Check if a backup is due and perform it if so.
     * Safe to call concurrently from any thread/coroutine — companion Mutex
     * guarantees exactly one backup runs at a time across all instances.
     */
    suspend fun checkAndBackup(): BackupResult {
        // Fast path — read settings without acquiring mutex
        if (!settingsManager.autoBackupEnabled.safeRead(true)) return BackupResult.NOT_DUE

        val lastBackup = settingsManager.lastBackupTimestamp.safeRead(0L)
        val periodHours = settingsManager.autoBackupPeriodHours.safeRead(24)
        val now = System.currentTimeMillis()
        val periodMs = periodHours.toLong() * 3600_000L

        if (now - lastBackup < periodMs) return BackupResult.NOT_DUE

        // Acquire mutex for the actual backup
        backupMutex.withLock {
            // Re-check after acquiring lock (another thread may have just backed up)
            val freshLastBackup = settingsManager.lastBackupTimestamp.safeRead(0L)
            if (now - freshLastBackup < periodMs) return BackupResult.NOT_DUE

            val file = performBackup()
            if (file != null) {
                runCatching { settingsManager.saveLastBackupTimestamp(now) }
                cleanupOldBackups()
                return BackupResult.SUCCESS
            }
            return BackupResult.FAILED
        }
        // unreachable — withLock handles the return above
        @Suppress("UNREACHABLE_CODE")
        return BackupResult.NOT_DUE
    }

    fun destroy() {
        scope.cancel()
    }

    // ── Private ───────────────────────────────────────────────

    private suspend fun performBackup(): File? = withContext(Dispatchers.IO) {
        try {
            val dir = resolveBackupDir() ?: return@withContext null
            if (!dir.exists() && !dir.mkdirs()) return@withContext null

            val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
            val filename = "OrangeIsland_backup_${sdf.format(Date())}.oi"
            val file = File(dir, filename)
            val tmpFile = File(dir, "$filename.tmp")

            val categoryKeys = settingsManager.autoBackupCategories.safeRead("conversations,memories,system_prompts,settings")
                .split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

            val categories = categoryKeys
                .mapNotNull { DataExporter.ExportCategory.fromManifestKey(it) }
                .toSet()

            if (categories.isEmpty()) return@withContext null

            // Honor the user's auto-backup category selection, including API keys (the dialog
            // shows an explicit warning when that box is checked).
            val includeApiKeys = DataExporter.ExportCategory.API_KEYS in categories

            val exporter = DataExporter(context, chatDao, settingsManager, memoryManager, workflowRepository)
            exporter.export(
                uri = Uri.fromFile(tmpFile),
                categories = categories,
                includeApiKeys = includeApiKeys,
                onProgress = {}
            )

            // Atomic: rename temp to final
            if (tmpFile.renameTo(file)) {
                DebugLog.d("AutoBackup", "Backup created: ${file.absolutePath}")
                file
            } else {
                // renameTo may fail across filesystems — try direct write as fallback
                tmpFile.delete()
                sendFailureNotification("Failed to finalize backup file")
                null
            }
        } catch (e: Exception) {
            DebugLog.e("AutoBackup", "Backup failed", e)
            sendFailureNotification(e.localizedMessage ?: "Auto backup failed")
            null
        }
    }

    private suspend fun resolveBackupDir(): File? {
        val stored = settingsManager.autoBackupDirectory.safeRead("Download/OrangeIsland/Backup")

        // Filesystem path
        if (!stored.startsWith("content://")) {
            val dir = File(stored)
            if ((dir.exists() && dir.canWrite()) || dir.mkdirs()) return dir
        }

        // Fallback: default directory
        val fallback = defaultBackupDir()
        if (fallback.exists() || fallback.mkdirs()) {
            runCatching { settingsManager.saveAutoBackupDirectory(fallback.absolutePath) }
            return fallback
        }

        sendFailureNotification("Backup directory unavailable")
        return null
    }

    private fun defaultBackupDir(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "OrangeIsland/Backup"
        )
    }

    private suspend fun cleanupOldBackups() {
        val deleteEnabled = settingsManager.autoDeleteEnabled.safeRead(true)
        if (!deleteEnabled) return

        val deletePeriodHours = settingsManager.autoDeletePeriodHours.safeRead(168)
        val backupPeriodHours = settingsManager.autoBackupPeriodHours.safeRead(24)

        // Defense: auto-delete must be strictly greater than backup period
        if (deletePeriodHours <= backupPeriodHours) return

        val cutoffTime = System.currentTimeMillis() - deletePeriodHours.toLong() * 3600_000L
        val dir = resolveBackupDir() ?: return

        dir.listFiles { f ->
            f.isFile && f.name.startsWith("OrangeIsland_backup_") && f.name.endsWith(".oi")
        }?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
                runCatching { file.delete() }
            }
        }

        // Clean orphaned .tmp files from interrupted backups
        dir.listFiles { f ->
            f.isFile && f.name.startsWith("OrangeIsland_backup_") && f.name.endsWith(".oi.tmp")
        }?.forEach { tmpFile ->
            runCatching { tmpFile.delete() }
        }
    }

    private fun sendFailureNotification(message: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Auto Backup",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Auto backup status" }
            nm.createNotificationChannel(channel)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            nm.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Auto backup failed")
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(pending)
                    .build()
            )
        } catch (e: Exception) {
            DebugLog.e("AutoBackup", "Failed to show notification", e)
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    /** Read a Flow's first value with error tolerance. */
    private suspend fun <T> Flow<T>.safeRead(default: T): T =
        try { first() } catch (_: Exception) { default }
}
