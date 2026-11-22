package com.omniai.studio.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omniai.studio.OmniAiApplication
import java.util.concurrent.TimeUnit

/**
 * Scheduled Auto-Backup Worker (Layer 2 GitHub Cloud Auto-Sync + Layer 4 Rolling Local Snapshots).
 * - GitHub Cloud Auto-Sync runs Daily or Weekly on UNMETERED (Wi-Fi) networks only.
 * - Nightly Rolling Local Snapshots write `.omniai.enc` to `getExternalFilesDir("Backups")`
 *   and automatically prune old backups to keep only the latest 5 files.
 */
class BackupSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? OmniAiApplication ?: return Result.failure()
        val settings = app.settingsRepository.current
        val passphrase = app.vaultManager.autoBackupPassphrase
        if (passphrase.length < 6) {
            return Result.success()
        }

        val mode = inputData.getString(KEY_MODE) ?: MODE_GITHUB_AND_LOCAL
        var hadFailure = false

        if (settings.rollingLocalBackupEnabled || mode == MODE_LOCAL_ONLY) {
            runCatching {
                app.vaultBackupManager.writeRollingLocalSnapshot(
                    passphrase = passphrase,
                    selection = BackupSelectionOptions(),
                    maxSnapshotsToKeep = VaultBackupManager.MAX_ROLLING_LOCAL_SNAPSHOTS
                )
            }.onFailure {
                hadFailure = true
            }
        }

        if (mode != MODE_LOCAL_ONLY && settings.githubAutoBackupEnabled) {
            val pushRes = app.githubBackupSync.pushEncryptedBackup(
                passphrase = passphrase,
                selection = BackupSelectionOptions()
            )
            if (pushRes.isFailure) {
                hadFailure = true
            }
        }

        return if (hadFailure) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_GITHUB_SYNC_WORK = "omniai_github_auto_backup_work"
        private const val UNIQUE_LOCAL_SNAPSHOT_WORK = "omniai_rolling_local_snapshot_work"
        private const val KEY_MODE = "backup_mode"
        private const val MODE_GITHUB_AND_LOCAL = "GITHUB_AND_LOCAL"
        private const val MODE_LOCAL_ONLY = "LOCAL_ONLY"

        fun scheduleGitHubCloudSync(context: Context, enabled: Boolean, intervalDays: Int) {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                wm.cancelUniqueWork(UNIQUE_GITHUB_SYNC_WORK)
                return
            }
            val safeDays = if (intervalDays == 7) 7L else 1L
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi only constraint
                .build()

            val request = PeriodicWorkRequestBuilder<BackupSyncWorker>(safeDays, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            wm.enqueueUniquePeriodicWork(
                UNIQUE_GITHUB_SYNC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun scheduleNightlyLocalSnapshots(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                wm.cancelUniqueWork(UNIQUE_LOCAL_SNAPSHOT_WORK)
                return
            }
            val data = androidx.work.workDataOf(KEY_MODE to MODE_LOCAL_ONLY)
            val request = PeriodicWorkRequestBuilder<BackupSyncWorker>(24, TimeUnit.HOURS)
                .setInputData(data)
                .build()

            wm.enqueueUniquePeriodicWork(
                UNIQUE_LOCAL_SNAPSHOT_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun runImmediateLocalSnapshot(context: Context) {
            val data = androidx.work.workDataOf(KEY_MODE to MODE_LOCAL_ONLY)
            val request = OneTimeWorkRequestBuilder<BackupSyncWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }
    }
}
