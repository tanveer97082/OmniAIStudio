package com.omniai.studio.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omniai.studio.OmniAiApplication
import java.util.concurrent.TimeUnit

/**
 * WorkManager background worker executing periodic or on-demand compressed text-only
 * two-way delta sync against Supabase PostgREST.
 */
class CloudSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? OmniAiApplication ?: return Result.failure()
        val session = app.supabaseAuthManager.refreshLocalSessionSnapshot()
        if (!session.isConfigured || !session.isSignedIn || session.isOfflineLocalMode || !session.isApproved) {
            return Result.success()
        }

        val outcome = app.cloudSyncRepository.syncNow()
        return if (outcome.isSuccess) {
            Result.success()
        } else {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "omniai_supabase_periodic_delta_sync"
        private const val ONETIME_WORK_NAME = "omniai_supabase_manual_delta_sync"

        /**
         * Schedules periodic compressed delta sync (15m, 60m, or 0 = Manual/Disabled).
         */
        fun schedulePeriodicSync(
            context: Context,
            intervalMinutes: Int,
            wifiOnly: Boolean = false
        ) {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (intervalMinutes <= 0) {
                wm.cancelUniqueWork(PERIODIC_WORK_NAME)
                return
            }

            val clampedMinutes = intervalMinutes.coerceAtLeast(15).toLong()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(
                clampedMinutes,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            wm.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun enqueueOneTimeSync(context: Context, wifiOnly: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                ONETIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
