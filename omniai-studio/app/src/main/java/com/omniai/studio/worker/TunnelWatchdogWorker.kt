package com.omniai.studio.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omniai.studio.MainActivity
import com.omniai.studio.security.VaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager Tunnel Health Watchdog.
 * Pings the configured Primary Tunnel `/models` endpoint and raises an Android alert notification
 * if the uncensored tunnel or localhost inference server goes offline.
 */
class TunnelWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val vault = VaultManager(applicationContext)
        val baseUrl = vault.primaryTunnelUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            return@withContext Result.success()
        }

        val targetUrl = if (baseUrl.endsWith("/models")) baseUrl else "$baseUrl/models"
        val reqBuilder = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", "OmniAI-TunnelWatchdog/2.0")
        if (vault.primaryTunnelApiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${vault.primaryTunnelApiKey}")
        }

        val reachable = try {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                resp.isSuccessful || resp.code == 401
            }
        } catch (_: Exception) {
            false
        }

        if (!reachable) {
            notifyTunnelOffline(baseUrl)
        }
        Result.success()
    }

    private fun notifyTunnelOffline(url: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tunnel Health Watchdog",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val openVaultIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_LAUNCH_TAB, 4)
        }
        val pi = PendingIntent.getActivity(
            applicationContext,
            9042,
            openVaultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Primary AI Tunnel Unreachable")
            .setContentText("Watchdog could not reach $url. Tap to check Vault or restart tunnel.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val WORK_NAME = "omniai_tunnel_watchdog"
        private const val CHANNEL_ID = "omniai_tunnel_watchdog_channel"
        private const val NOTIFICATION_ID = 9042

        fun schedule(context: Context, enabled: Boolean, intervalMinutes: Long = 30L) {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val safeInterval = intervalMinutes.coerceAtLeast(15L)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<TunnelWatchdogWorker>(safeInterval, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
