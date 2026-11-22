package com.omniai.studio.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omniai.studio.MainActivity
import com.omniai.studio.OmniAiApplication
import com.omniai.studio.data.local.NewsDigestEntity
import com.omniai.studio.security.NetworkSecurityValidator
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.util.concurrent.TimeUnit

/**
 * Scheduled RSS News-to-Script background worker.
 * Parses saved RSS/Atom feeds via Jsoup XML parser, summarizes top headlines with the configured AI,
 * saves a draft digest + script topic to Room DB, and posts an Android notification for human approval.
 *
 * STRICTLY HUMAN-IN-THE-LOOP: Never triggers Kaggle GPU video rendering automatically.
 */
class NewsDigestWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? OmniAiApplication ?: return Result.failure()
        val newsDao = app.database.newsTrackerDao()
        val feeds = newsDao.getEnabledFeedsOnce()
        if (feeds.isEmpty()) {
            return Result.success()
        }

        val collectedHeadlines = mutableListOf<String>()
        var parsedFeedCount = 0

        for (feed in feeds) {
            val validation = NetworkSecurityValidator.validateEndpointUrl(feed.feedUrl)
            if (!validation.isValid) continue
            try {
                val xmlDoc = Jsoup.connect(validation.normalizedUrl)
                    .parser(Parser.xmlParser())
                    .timeout(12_000)
                    .get()

                val items = xmlDoc.select("item").ifEmpty { xmlDoc.select("entry") }
                if (items.isNotEmpty()) {
                    parsedFeedCount++
                }
                items.take(4).forEach { item ->
                    val title = item.selectFirst("title")?.text()?.trim().orEmpty()
                    val desc = (item.selectFirst("description")?.text()
                        ?: item.selectFirst("summary")?.text()).orEmpty()
                        .replace(Regex("<[^>]*>"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .take(180)
                    if (title.isNotBlank()) {
                        collectedHeadlines.add("[${feed.name}] $title — $desc")
                    }
                }
            } catch (_: Exception) {
                // Skip unreachable RSS feed and continue with remaining feeds
            }
        }

        if (collectedHeadlines.isEmpty()) {
            return Result.retry()
        }

        val rawDigestPrompt = collectedHeadlines.take(12).joinToString("\n")
        val summaryText = try {
            val (aiSummary, _) = app.aiClient.completeSinglePrompt(
                systemPrompt = "You are an executive tech & media news analyst. Summarize these RSS headlines into 4 concise bullet points and conclude with a single line starting with 'SUGGESTED VIDEO TOPIC:' proposing an engaging 60-second video angle.",
                userPrompt = rawDigestPrompt
            )
            aiSummary.ifBlank { rawDigestPrompt }
        } catch (_: Exception) {
            "Offline RSS Digest (AI unavailable):\n" + collectedHeadlines.take(5).joinToString("\n• ", prefix = "• ")
        }

        val topicMatch = Regex("SUGGESTED VIDEO TOPIC:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(summaryText)?.groupValues?.getOrNull(1)?.trim()
        val suggestedTopic = topicMatch?.take(180)
            ?: collectedHeadlines.firstOrNull()?.substringAfter("] ")?.substringBefore(" — ")
            ?: "Daily AI & Tech News Brief"

        newsDao.insertDigest(
            NewsDigestEntity(
                title = "Daily AI Brief (${collectedHeadlines.size} headlines)",
                summaryText = summaryText,
                suggestedScriptTopic = suggestedTopic,
                sourceFeedCount = parsedFeedCount.coerceAtLeast(1)
            )
        )

        postBriefReadyNotification(suggestedTopic)
        if (app.vaultManager.customWebhookUrl.isNotBlank()) {
            app.webhookNotifier.sendEvent(
                event = "DAILY_RSS_BRIEF",
                title = "Daily AI Brief (${collectedHeadlines.size} headlines)",
                message = "Suggested Video Angle: $suggestedTopic\n\n${summaryText.take(600)}"
            )
        }
        return Result.success()
    }

    private fun postBriefReadyNotification(previewTopic: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OmniAI News Briefs",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Human-in-the-loop daily RSS AI summaries ready for review."
            }
            manager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            303,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Daily AI Brief Ready for Approval")
            .setContentText(previewTopic)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Draft ready for human review: $previewTopic"))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "omniai_news_digest_periodic"
        private const val CHANNEL_ID = "omniai_news_digest_channel"
        private const val NOTIFICATION_ID = 7021

        fun schedulePeriodic(context: Context, intervalHours: Int) {
            val safeHours = if (intervalHours == 12) 12L else 24L
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<NewsDigestWorker>(safeHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        fun runOnceNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<NewsDigestWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
        }
    }
}
