package com.omniai.studio.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omniai.studio.OmniAiApplication

/**
 * v5.0 Offline Outbox Flush Worker:
 * Runs automatically via WorkManager once network connectivity returns (`NetworkType.CONNECTED`),
 * flushing pending GZIP delta cloud syncs when authenticated & approved and reconciling any
 * deferred background studio tasks with bounded retry limits.
 */
class OutboxFlushWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? OmniAiApplication ?: return Result.success()
        var transientFailure = false

        // 1. Flush Supabase compressed text delta sync if user is signed in and approved
        if (app.supabaseAuthManager.isConfigured() &&
            !app.vaultManager.cloudOfflineLocalMode &&
            app.vaultManager.cloudIsApproved
        ) {
            val syncResult = runCatching { app.cloudSyncRepository.syncNow() }.getOrNull()
            if (syncResult == null || syncResult.isFailure) {
                transientFailure = true
            }
        }

        // 2. Refresh YouTube quota / channel analytics cache softly if configured
        if (app.vaultManager.getActiveYouTubeProfile() != null) {
            runCatching { app.youTubeAnalyticsRepository.refresh(null) }
        }

        return if (transientFailure && runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "omniai_outbox_flush_on_reconnect"
        private const val MAX_RETRY_ATTEMPTS = 3

        fun scheduleOnReconnect(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<OutboxFlushWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
                )
        }
    }
}
