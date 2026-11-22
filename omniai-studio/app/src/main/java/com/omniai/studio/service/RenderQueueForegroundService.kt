package com.omniai.studio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.omniai.studio.OmniAiApplication
import com.omniai.studio.backup.TelegramBackupClient
import com.omniai.studio.data.local.AppSettingsRepository
import com.omniai.studio.data.local.DownloadRecordEntity
import com.omniai.studio.data.local.OmniAiDatabase
import com.omniai.studio.data.local.RenderJobEntity
import com.omniai.studio.data.local.RenderLogEntity
import com.omniai.studio.data.remote.KaggleApiClient
import com.omniai.studio.data.remote.StoryboardPayload
import com.omniai.studio.data.remote.WebhookNotifier
import com.omniai.studio.security.VaultManager
import com.omniai.studio.widget.OmniWidgetProvider
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class RenderQueueForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var processorJob: Job? = null

    private lateinit var db: OmniAiDatabase
    private lateinit var vault: VaultManager
    private lateinit var settingsRepo: AppSettingsRepository
    private lateinit var kaggleClient: KaggleApiClient
    private lateinit var telegramClient: TelegramBackupClient
    private lateinit var webhookNotifier: WebhookNotifier

    override fun onCreate() {
        super.onCreate()
        db = OmniAiDatabase.getInstance(this)
        vault = VaultManager(this)
        settingsRepo = AppSettingsRepository(this)
        kaggleClient = KaggleApiClient(vault)
        telegramClient = TelegramBackupClient(vault)
        webhookNotifier = WebhookNotifier(vault)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("OmniAI Render Queue", "Preparing batch pipeline...", 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (processorJob?.isActive != true) {
            processorJob = serviceScope.launch {
                drainBatchQueue()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_REDELIVER_INTENT
    }

    private suspend fun drainBatchQueue() {
        val renderDao = db.renderJobDao()
        while (true) {
            val nextJob = renderDao.getNextQueuedJob() ?: break
            executeSingleRenderJob(nextJob)
        }
    }

    private suspend fun executeSingleRenderJob(job: RenderJobEntity) {
        val renderDao = db.renderJobDao()
        val downloadDao = db.downloadDao()
        val renderLogDao = db.renderLogDao()
        val startedAtMs = System.currentTimeMillis()
        var activeAccountLabel = vault.getActiveKaggleAccount()?.label ?: "Primary"
        try {
            // 0. Multi-Account Kaggle Quota Check: auto-rotate if remaining weekly quota < 1.0 hour
            val weekStart = System.currentTimeMillis() - (7L * 24L * 3600L * 1000L)
            val usedSec = renderLogDao.getAccountDurationSecondsSince(activeAccountLabel, weekStart)
            val remainingHours = (30.0 - (usedSec / 3600.0)).coerceAtLeast(0.0)
            if (remainingHours < 1.0) {
                val rotated = vault.rotateToNextKaggleAccount()
                if (rotated != null) {
                    activeAccountLabel = rotated.label
                    updateProgress(
                        job.id,
                        "UPLOADING",
                        5,
                        "Quota <1h on prior account — auto-rotated to Kaggle pool account: ${rotated.label}"
                    )
                }
            }

            // 1. Upload scenes.json (+ optional ref_image.jpg) to Kaggle Dataset
            val refFile = job.refImagePath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
            updateProgress(
                job.id,
                "UPLOADING",
                10,
                if (refFile != null) "Uploading scenes.json + ref_image.jpg to Kaggle..." else "Uploading scenes.json to Kaggle Dataset..."
            )
            kaggleClient.uploadScenesJson(job.scenesPayloadJson, refFile)

            // 2. Trigger Kaggle T4 x2 Dual-GPU Worker
            updateProgress(job.id, "TRIGGERING", 20, "Triggering Kaggle T4 x2 Wan 2.1 worker ($activeAccountLabel)...")
            kaggleClient.triggerDualGpuKernel()

            // 3. Poll Kaggle Kernel Status at configured interval (15s / 20s / 30s / 60s)
            val pollIntervalSec = settingsRepo.current.kagglePollIntervalSec.coerceIn(15, 60)
            var elapsedPolls = 0
            while (true) {
                delay(pollIntervalSec * 1000L)
                elapsedPolls++
                val statusResp = kaggleClient.pollKernelStatus()
                val normalized = statusResp.status.lowercase()
                when {
                    normalized.contains("complete") -> break
                    normalized.contains("error") || normalized.contains("cancel") -> {
                        throw IllegalStateException(
                            "Kaggle worker failed: ${statusResp.failureMessage ?: statusResp.status}"
                        )
                    }
                    else -> {
                        val estProgress = (22 + elapsedPolls * 3).coerceAtMost(78)
                        updateProgress(
                            job.id,
                            "RENDERING",
                            estProgress,
                            "Rendering on Dual T4 GPU (${statusResp.status}) — poll #$elapsedPolls (${pollIntervalSec}s)"
                        )
                    }
                }
            }

            // 4. Enforce Wi-Fi Only Auto-Download rule if enabled in Settings
            if (settingsRepo.current.wifiOnlyAutoDownload && !isOnWifiNetwork()) {
                throw IllegalStateException(
                    "Render complete on Kaggle, but Wi-Fi Only Auto-Download is enabled. Connect to Wi-Fi and tap Retry."
                )
            }

            // 5. Download final_video.mp4 (and optional individual scene_X.mp4 files)
            updateProgress(job.id, "DOWNLOADING", 82, "Fetching Kaggle render artifacts...")
            val outputFiles = kaggleClient.listKernelOutputs()
            val targetDir = File(filesDir, "videos").apply { mkdirs() }
            val safePrefix = job.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(28)

            val finalArtifact = outputFiles.firstOrNull { it.fileName.endsWith("final_video.mp4") }
                ?: throw IllegalStateException("final_video.mp4 not found in Kaggle kernel output.")

            val finalLocalName = "${safePrefix}_job${job.id}_final.mp4"
            val finalLocalFile = File(targetDir, finalLocalName)
            val dlRecordId = downloadDao.insert(
                DownloadRecordEntity(
                    fileName = finalLocalName,
                    filePath = finalLocalFile.absolutePath,
                    sourceUrl = finalArtifact.url,
                    category = "RENDER",
                    status = "DOWNLOADING",
                    progressPercent = 0,
                    sizeBytes = finalArtifact.totalBytes,
                    socialMetadataJson = job.socialMetadataJson
                )
            )

            val downloadedSize = kaggleClient.downloadFileWithProgress(
                downloadUrl = finalArtifact.url,
                targetFile = finalLocalFile
            ) { pct, bytes ->
                serviceScope.launch {
                    downloadDao.updateProgress(dlRecordId, "DOWNLOADING", pct, bytes)
                    val overall = 82 + (pct * 15 / 100)
                    updateProgress(job.id, "DOWNLOADING", overall, "Downloading final_video.mp4 ($pct%)")
                }
            }
            downloadDao.updateProgress(dlRecordId, "COMPLETED", 100, downloadedSize)

            // Optional individual scene_X.mp4 downloads
            if (job.downloadIndividualScenes) {
                val sceneArtifacts = outputFiles
                    .filter { it.fileName.matches(Regex(".*scene_\\d+\\.mp4$")) }
                    .sortedBy { it.fileName }
                for (sceneArt in sceneArtifacts) {
                    val sceneName = "${safePrefix}_job${job.id}_${sceneArt.fileName.substringAfterLast('/')}"
                    val sceneFile = File(targetDir, sceneName)
                    val sceneDlId = downloadDao.insert(
                        DownloadRecordEntity(
                            fileName = sceneName,
                            filePath = sceneFile.absolutePath,
                            sourceUrl = sceneArt.url,
                            category = "SCENE",
                            status = "DOWNLOADING",
                            progressPercent = 0,
                            sizeBytes = sceneArt.totalBytes
                        )
                    )
                    val size = kaggleClient.downloadFileWithProgress(
                        downloadUrl = sceneArt.url,
                        targetFile = sceneFile
                    ) { pct, bytes ->
                        serviceScope.launch {
                            downloadDao.updateProgress(sceneDlId, "DOWNLOADING", pct, bytes)
                        }
                    }
                    downloadDao.updateProgress(sceneDlId, "COMPLETED", 100, size)
                }
            }

            renderDao.updateStatus(
                jobId = job.id,
                status = "COMPLETED",
                progress = 100,
                message = "Completed: $finalLocalName",
                outputPath = finalLocalFile.absolutePath
            )
            val elapsedSeconds = ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceAtLeast(1L)
            renderLogDao.insert(
                RenderLogEntity(
                    jobId = job.id,
                    title = job.title,
                    status = "COMPLETED",
                    durationSeconds = elapsedSeconds,
                    accountLabel = activeAccountLabel
                )
            )

            // v4.0: Mark linked Script & Series Episode as RENDERED, increment Niche episode count,
            // and automatically generate 4 Auto-Next Video commands when enabled.
            runCatching {
                job.scriptId?.let { scriptId ->
                    db.videoScriptDao().markScriptRendered(
                        scriptId = scriptId,
                        status = "RENDERED",
                        videoPath = finalLocalFile.absolutePath
                    )
                }
                job.seriesEpisodeId?.let { epId ->
                    db.seriesEpisodeDao().updateEpisodeStatus(
                        episodeId = epId,
                        status = "RENDERED",
                        linkedScriptId = job.scriptId
                    )
                }
                db.nicheDao().incrementEpisodeCount(job.nicheId)

                if (settingsRepo.current.autoGenerateNextAfterRender) {
                    val parsedPayload = runCatching {
                        Json { ignoreUnknownKeys = true }.decodeFromString<StoryboardPayload>(job.scenesPayloadJson)
                    }.getOrNull()
                    val finalNarration = parsedPayload?.scenes?.lastOrNull()?.narration.orEmpty()
                    val app = application as? OmniAiApplication
                    app?.continuityEngine?.generatePostVideoNextCommands(
                        nicheId = job.nicheId,
                        sourceScriptId = job.scriptId,
                        lastVideoTitle = job.title,
                        finalSceneNarration = finalNarration
                    )
                }
            }

            OmniWidgetProvider.requestRefresh(this)
            postNotification("Render Complete", "${job.title} saved offline.", 100)

            // v2.5: Optional Telegram Auto-Archive (<= 49 MB) + Instant Status Alerts & Custom Webhook
            if (vault.telegramBotToken.isNotBlank() && vault.telegramChatId.isNotBlank()) {
                if (settingsRepo.current.telegramAutoArchiveVideos &&
                    finalLocalFile.length() <= TelegramBackupClient.MAX_TELEGRAM_VIDEO_BYTES
                ) {
                    telegramClient.uploadRenderedVideoIfEligible(
                        videoFile = finalLocalFile,
                        caption = "OmniAI Render Complete: ${job.title} (${elapsedSeconds}s · $activeAccountLabel)"
                    )
                } else {
                    telegramClient.sendStatusMessage(
                        "OmniAI Render Complete: ${job.title} saved offline (${downloadedSize / (1024 * 1024)} MB · ${elapsedSeconds}s)."
                    )
                }
            }
            if (vault.customWebhookUrl.isNotBlank()) {
                webhookNotifier.sendEvent(
                    event = "RENDER_COMPLETED",
                    title = job.title,
                    message = "Saved $finalLocalName (${downloadedSize / (1024 * 1024)} MB) in ${elapsedSeconds}s on $activeAccountLabel."
                )
            }
        } catch (ex: Exception) {
            renderDao.updateStatus(
                jobId = job.id,
                status = "FAILED",
                progress = 0,
                message = ex.message ?: "Unknown render pipeline error"
            )
            val elapsedSeconds = ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceAtLeast(1L)
            renderLogDao.insert(
                RenderLogEntity(
                    jobId = job.id,
                    title = job.title,
                    status = "FAILED",
                    durationSeconds = elapsedSeconds,
                    accountLabel = activeAccountLabel
                )
            )
            OmniWidgetProvider.requestRefresh(this)
            postNotification("Render Failed", "${job.title}: ${ex.message}", 0)

            if (vault.telegramBotToken.isNotBlank() && vault.telegramChatId.isNotBlank()) {
                telegramClient.sendStatusMessage("OmniAI Render Failed: ${job.title} — ${ex.message}")
            }
            if (vault.customWebhookUrl.isNotBlank()) {
                webhookNotifier.sendEvent(
                    event = "RENDER_FAILED",
                    title = job.title,
                    message = ex.message ?: "Unknown Kaggle render failure."
                )
            }
        }
    }

    private suspend fun updateProgress(jobId: Long, status: String, pct: Int, message: String) {
        db.renderJobDao().updateStatus(jobId, status, pct, message)
        OmniWidgetProvider.requestRefresh(this)
        postNotification("OmniAI Video Director ($pct%)", message, pct)
    }

    private fun postNotification(title: String, body: String, progress: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, body, progress))
    }

    private fun buildNotification(title: String, body: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(body)
            .setOnlyAlertOnce(true)
            .setOngoing(progress in 1..99)
            .setProgress(100, progress, progress == 0)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OmniAI Video Render Queue",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Tracks background Kaggle Dual-GPU video renders and MP4 downloads."
        }
        manager.createNotificationChannel(channel)
    }

    private fun isOnWifiNetwork(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "omniai_render_queue_channel"
        private const val NOTIFICATION_ID = 4091

        fun enqueueStart(context: Context) {
            val intent = Intent(context, RenderQueueForegroundService::class.java)
            context.startForegroundService(intent)
        }
    }
}
