package com.omniai.studio.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omniai.studio.MainActivity
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Human-in-the-Loop Posting Reminder Worker for YouTube, Instagram, and Facebook.
 * Fires an Android notification at the scheduled time with a 1-tap action opening
 * the exact Social Post card inside the Content DB & Social Hub for copy & share.
 */
class PublishReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val postId = inputData.getLong(KEY_POST_ID, -1L)
        val platformRaw = inputData.getString(KEY_PLATFORM).orEmpty().ifBlank { "YOUTUBE" }
        val title = inputData.getString(KEY_TITLE).orEmpty().ifBlank { "Scheduled Creator Post" }

        val platformLabel = when (platformRaw.uppercase(Locale.US)) {
            "YOUTUBE" -> "YouTube"
            "INSTAGRAM" -> "Instagram"
            "FACEBOOK" -> "Facebook"
            else -> platformRaw
        }

        ensureNotificationChannel(applicationContext)

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_SOCIAL_POST_ID, postId)
            putExtra(MainActivity.EXTRA_OPEN_SOCIAL_PLATFORM, platformRaw.uppercase(Locale.US))
        }

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            (postId.toInt() and 0x7FFF).coerceAtLeast(1001),
            openIntent,
            pendingFlags
        )

        val notifTitle = "Ready to post on $platformLabel: $title"
        val notifBody = "Tap to open the $platformLabel SEO card in Content Hub for 1-tap copy & share."

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(notifTitle)
            .setContentText(notifBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notifBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_share,
                "Open Post Card",
                pendingIntent
            )
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val notifId = if (postId > 0L) (NOTIF_BASE_ID + (postId % 10_000L).toInt()) else NOTIF_BASE_ID
        manager?.notify(notifId, notification)

        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "omniai_publish_reminders"
        private const val CHANNEL_NAME = "Social Posting Reminders"
        private const val NOTIF_BASE_ID = 43500
        private const val KEY_POST_ID = "post_id"
        private const val KEY_PLATFORM = "platform"
        private const val KEY_TITLE = "title"

        fun uniqueWorkName(postId: Long): String = "omniai_publish_reminder_$postId"

        fun scheduleReminder(
            context: Context,
            postId: Long,
            platform: String,
            title: String,
            delayMillis: Long
        ) {
            val safeDelayMs = delayMillis.coerceAtLeast(5_000L)
            val data = Data.Builder()
                .putLong(KEY_POST_ID, postId)
                .putString(KEY_PLATFORM, platform)
                .putString(KEY_TITLE, title.take(120))
                .build()

            val request = OneTimeWorkRequestBuilder<PublishReminderWorker>()
                .setInitialDelay(safeDelayMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("omniai_publish_reminder")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(postId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancelReminder(context: Context, postId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(postId))
        }

        private fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val existing = manager.getNotificationChannel(CHANNEL_ID)
                if (existing == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Scheduled Human-in-the-Loop YouTube, Instagram, and Facebook posting reminders."
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }
    }
}
