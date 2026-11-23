package com.omniai.studio

import android.app.Application
import com.omniai.studio.backup.BackupSyncWorker
import com.omniai.studio.backup.GitHubBackupSync
import com.omniai.studio.backup.TelegramBackupClient
import com.omniai.studio.backup.VaultBackupManager
import com.omniai.studio.cloud.CloudSyncRepository
import com.omniai.studio.cloud.CloudSyncWorker
import com.omniai.studio.cloud.RolePermissionGuard
import com.omniai.studio.cloud.SupabaseAuthManager
import com.omniai.studio.data.local.ApiMetricEntity
import com.omniai.studio.data.local.AppSettingsRepository
import com.omniai.studio.data.local.ContentDbExporter
import com.omniai.studio.data.local.ConversationMemoryManager
import com.omniai.studio.data.local.OmniAiDatabase
import com.omniai.studio.data.remote.AiStreamingClient
import com.omniai.studio.data.remote.ApiKeyPoolManager
import com.omniai.studio.data.remote.GitHubOtaClient
import com.omniai.studio.data.remote.GitHubWorkerEditorClient
import com.omniai.studio.data.remote.HfModelExplorerClient
import com.omniai.studio.data.remote.KaggleApiClient
import com.omniai.studio.data.remote.ResumableDownloadManager
import com.omniai.studio.data.remote.WebhookNotifier
import com.omniai.studio.git.AiBuildDoctor
import com.omniai.studio.git.GitHubGitEngine
import com.omniai.studio.lan.LocalLanWebServer
import com.omniai.studio.media.AudioStudioHelper
import com.omniai.studio.media.Media3VideoCompressor
import com.omniai.studio.media.Media3VideoMerger
import com.omniai.studio.media.OfflineTtsHelper
import com.omniai.studio.niche.ContinuityEngine
import com.omniai.studio.niche.NicheContextBuilder
import com.omniai.studio.security.BiometricAuthManager
import com.omniai.studio.security.CrashHandler
import com.omniai.studio.security.DeviceHealthGuard
import com.omniai.studio.security.DuressPinManager
import com.omniai.studio.security.DynamicShortcutHelper
import com.omniai.studio.security.VaultManager
import com.omniai.studio.settings.UiConfigRepository
import com.omniai.studio.terminal.LocalShellExecutor
import com.omniai.studio.terminal.OmniCliInterpreter
import com.omniai.studio.terminal.TermuxBridge
import com.omniai.studio.worker.OutboxFlushWorker
import com.omniai.studio.youtube.YouTubeAnalyticsRepository
import com.omniai.studio.youtube.YouTubeApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OmniAiApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var vaultManager: VaultManager
        private set

    lateinit var duressPinManager: DuressPinManager
        private set

    lateinit var settingsRepository: AppSettingsRepository
        private set

    lateinit var uiConfigRepository: UiConfigRepository
        private set

    lateinit var database: OmniAiDatabase
        private set

    lateinit var contentDbExporter: ContentDbExporter
        private set

    lateinit var biometricAuthManager: BiometricAuthManager
        private set

    lateinit var aiClient: AiStreamingClient
        private set

    lateinit var conversationMemoryManager: ConversationMemoryManager
        private set

    lateinit var nicheContextBuilder: NicheContextBuilder
        private set

    lateinit var continuityEngine: ContinuityEngine
        private set

    lateinit var gitHubGitEngine: GitHubGitEngine
        private set

    lateinit var kaggleClient: KaggleApiClient
        private set

    lateinit var githubOtaClient: GitHubOtaClient
        private set

    lateinit var githubWorkerEditorClient: GitHubWorkerEditorClient
        private set

    lateinit var hfModelExplorerClient: HfModelExplorerClient
        private set

    lateinit var webhookNotifier: WebhookNotifier
        private set

    lateinit var vaultBackupManager: VaultBackupManager
        private set

    lateinit var githubBackupSync: GitHubBackupSync
        private set

    lateinit var telegramBackupClient: TelegramBackupClient
        private set

    lateinit var localShellExecutor: LocalShellExecutor
        private set

    lateinit var termuxBridge: TermuxBridge
        private set

    lateinit var omniCliInterpreter: OmniCliInterpreter
        private set

    lateinit var videoMerger: Media3VideoMerger
        private set

    lateinit var videoCompressor: Media3VideoCompressor
        private set

    lateinit var audioStudioHelper: AudioStudioHelper
        private set

    lateinit var ttsHelper: OfflineTtsHelper
        private set

    lateinit var youTubeApiClient: YouTubeApiClient
        private set

    lateinit var youTubeAnalyticsRepository: YouTubeAnalyticsRepository
        private set

    lateinit var rolePermissionGuard: RolePermissionGuard
        private set

    lateinit var supabaseAuthManager: SupabaseAuthManager
        private set

    lateinit var cloudSyncRepository: CloudSyncRepository
        private set

    lateinit var apiKeyPoolManager: ApiKeyPoolManager
        private set

    lateinit var resumableDownloadManager: ResumableDownloadManager
        private set

    lateinit var deviceHealthGuard: DeviceHealthGuard
        private set

    lateinit var aiBuildDoctor: AiBuildDoctor
        private set

    lateinit var localLanWebServer: LocalLanWebServer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        vaultManager = VaultManager(this)
        duressPinManager = DuressPinManager(this)
        settingsRepository = AppSettingsRepository(this)
        uiConfigRepository = UiConfigRepository(this)
        database = OmniAiDatabase.getInstance(this)
        contentDbExporter = ContentDbExporter(this, database)
        biometricAuthManager = BiometricAuthManager(this)
        aiClient = AiStreamingClient(
            vault = vaultManager,
            settingsRepo = settingsRepository,
            onMetricRecorded = { providerName, estimatedTokens, latencyMs, success ->
                appScope.launch {
                    runCatching {
                        database.apiMetricDao().insert(
                            ApiMetricEntity(
                                providerName = providerName,
                                estimatedTokens = estimatedTokens,
                                latencyMs = latencyMs,
                                success = success
                            )
                        )
                    }
                }
            }
        )
        conversationMemoryManager = ConversationMemoryManager(database.chatDao(), aiClient, settingsRepository)
        nicheContextBuilder = NicheContextBuilder(database, aiClient, settingsRepository)
        youTubeApiClient = YouTubeApiClient(vaultManager, database)
        youTubeAnalyticsRepository = YouTubeAnalyticsRepository(this, youTubeApiClient, database)
        continuityEngine = ContinuityEngine(database, aiClient, nicheContextBuilder, youTubeAnalyticsRepository)
        gitHubGitEngine = GitHubGitEngine(this, database, vaultManager)
        kaggleClient = KaggleApiClient(vaultManager)
        githubOtaClient = GitHubOtaClient(vaultManager)
        githubWorkerEditorClient = GitHubWorkerEditorClient()
        hfModelExplorerClient = HfModelExplorerClient()
        webhookNotifier = WebhookNotifier(vaultManager)
        vaultBackupManager = VaultBackupManager(this, database, vaultManager, settingsRepository)
        githubBackupSync = GitHubBackupSync(vaultManager, vaultBackupManager)
        telegramBackupClient = TelegramBackupClient(vaultManager)
        localShellExecutor = LocalShellExecutor(this)
        termuxBridge = TermuxBridge(this)
        rolePermissionGuard = RolePermissionGuard()
        supabaseAuthManager = SupabaseAuthManager(vaultManager, rolePermissionGuard)
        cloudSyncRepository = CloudSyncRepository(database, vaultManager, supabaseAuthManager, rolePermissionGuard)
        apiKeyPoolManager = ApiKeyPoolManager(vaultManager)
        resumableDownloadManager = ResumableDownloadManager()
        deviceHealthGuard = DeviceHealthGuard(this)
        aiBuildDoctor = AiBuildDoctor(vaultManager, aiClient)
        localLanWebServer = LocalLanWebServer(database)
        omniCliInterpreter = OmniCliInterpreter(
            context = this,
            db = database,
            settingsRepo = settingsRepository,
            contentDbExporter = contentDbExporter,
            continuityEngine = continuityEngine,
            gitHubGitEngine = gitHubGitEngine,
            localShellExecutor = localShellExecutor,
            termuxBridge = termuxBridge,
            aiClient = aiClient,
            kaggleClient = kaggleClient,
            hfClient = hfModelExplorerClient,
            vault = vaultManager,
            vaultBackupManager = vaultBackupManager,
            githubBackupSync = githubBackupSync,
            youTubeApiClient = youTubeApiClient,
            youTubeAnalyticsRepository = youTubeAnalyticsRepository,
            supabaseAuthManager = supabaseAuthManager,
            cloudSyncRepository = cloudSyncRepository,
            deviceHealthGuard = deviceHealthGuard,
            localLanWebServer = localLanWebServer,
            aiBuildDoctor = aiBuildDoctor,
            apiKeyPoolManager = apiKeyPoolManager
        )
        videoMerger = Media3VideoMerger(this)
        videoCompressor = Media3VideoCompressor(this)
        audioStudioHelper = AudioStudioHelper(this)
        ttsHelper = OfflineTtsHelper(this)

        DynamicShortcutHelper.registerShortcuts(this)
        if (settingsRepository.current.rollingLocalBackupEnabled) {
            BackupSyncWorker.scheduleNightlyLocalSnapshots(this, true)
        }
        if (vaultManager.cloudSyncIntervalMinutes > 0 && supabaseAuthManager.isConfigured()) {
            CloudSyncWorker.schedulePeriodicSync(
                context = this,
                intervalMinutes = vaultManager.cloudSyncIntervalMinutes,
                wifiOnly = vaultManager.cloudSyncWifiOnly
            )
        }
        OutboxFlushWorker.scheduleOnReconnect(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        if (::localLanWebServer.isInitialized) {
            localLanWebServer.stop()
        }
        if (::ttsHelper.isInitialized) {
            ttsHelper.shutdown()
        }
    }
}
