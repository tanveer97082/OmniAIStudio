package com.omniai.studio.data.local

import android.content.Context
import android.content.SharedPreferences
import com.omniai.studio.security.AppIconVariant
import com.omniai.studio.ui.theme.ThemeAccent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AutoTypeSpeedMode(val label: String, val charDelayMs: Long) {
    FAST("Fast (10ms)", 10L),
    MEDIUM("Medium (22ms)", 22L),
    INSTANT("Instant (0ms)", 0L);

    companion object {
        fun fromStoredName(raw: String?): AutoTypeSpeedMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MEDIUM
    }
}

data class AppSettings(
    val iconVariant: AppIconVariant = AppIconVariant.DEFAULT,
    val themeAccent: ThemeAccent = ThemeAccent.MATRIX_GREEN,
    val chatFontSizeSp: Float = 14f,
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 2048,
    val chatContextWindow: Int = 12,
    val autoReadAiReplies: Boolean = false,
    val ttsSpeechRate: Float = 1.0f,
    val wifiOnlyAutoDownload: Boolean = false,
    val kagglePollIntervalSec: Int = 20,
    val autoCleanTempAfterMerge: Boolean = true,
    val newsAgentEnabled: Boolean = false,
    val newsCheckIntervalHours: Int = 24,
    // v2.5 Pro Ecosystem & 4-Layer Backup Settings
    val activeWorkspaceId: String = "default",
    val githubAutoBackupEnabled: Boolean = false,
    val githubAutoBackupIntervalDays: Int = 1, // 1 = Daily, 7 = Weekly
    val rollingLocalBackupEnabled: Boolean = true,
    val telegramAutoArchiveVideos: Boolean = false,
    // v4.0 Niche Context Engine, Auto-Typer & Seamless Loop Settings
    val activeNicheId: Long = 1L,
    val filterAllNiches: Boolean = false,
    val autoTypeEnabled: Boolean = true,
    val autoTypeSpeedMode: AutoTypeSpeedMode = AutoTypeSpeedMode.MEDIUM,
    val autoGenerateNextAfterRender: Boolean = true,
    val autoSummarizeNicheThreshold: Int = 20,
    val seamlessLoopDefault: Boolean = false,
    // v4.5 Inbuilt Project Management & Atomic Git Automation Settings
    val activeProjectId: Long = 0L,
    val defaultGithubOwner: String = "",
    val defaultGithubBranch: String = "main",
    val autoWatchActionsAfterPush: Boolean = true,
    val aiAutoCommitMessageEnabled: Boolean = true
)

class AppSettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    private fun load(): AppSettings = AppSettings(
        iconVariant = AppIconVariant.fromStoredName(prefs.getString(KEY_ICON_VARIANT, AppIconVariant.DEFAULT.name)),
        themeAccent = ThemeAccent.fromStoredName(prefs.getString(KEY_THEME_ACCENT, ThemeAccent.MATRIX_GREEN.name)),
        chatFontSizeSp = prefs.getFloat(KEY_CHAT_FONT_SP, 14f).coerceIn(12f, 20f),
        temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f).coerceIn(0f, 1.5f),
        maxOutputTokens = prefs.getInt(KEY_MAX_OUTPUT_TOKENS, 2048).coerceIn(256, 8192),
        chatContextWindow = prefs.getInt(KEY_CHAT_CONTEXT_WINDOW, 12).coerceIn(4, 30),
        autoReadAiReplies = prefs.getBoolean(KEY_AUTO_READ_TTS, false),
        ttsSpeechRate = prefs.getFloat(KEY_TTS_SPEED, 1.0f).coerceIn(0.75f, 1.5f),
        wifiOnlyAutoDownload = prefs.getBoolean(KEY_WIFI_ONLY_DL, false),
        kagglePollIntervalSec = prefs.getInt(KEY_KAGGLE_POLL_SEC, 20).let {
            if (it in VALID_POLL_INTERVALS) it else 20
        },
        autoCleanTempAfterMerge = prefs.getBoolean(KEY_AUTO_CLEAN_MERGE, true),
        newsAgentEnabled = prefs.getBoolean(KEY_NEWS_AGENT_ENABLED, false),
        newsCheckIntervalHours = prefs.getInt(KEY_NEWS_INTERVAL_HOURS, 24).let {
            if (it in VALID_NEWS_INTERVALS) it else 24
        },
        activeWorkspaceId = prefs.getString(KEY_ACTIVE_WORKSPACE, "default")?.ifBlank { "default" } ?: "default",
        githubAutoBackupEnabled = prefs.getBoolean(KEY_GH_AUTO_BACKUP, false),
        githubAutoBackupIntervalDays = prefs.getInt(KEY_GH_BACKUP_DAYS, 1).let {
            if (it in VALID_BACKUP_DAYS) it else 1
        },
        rollingLocalBackupEnabled = prefs.getBoolean(KEY_ROLLING_LOCAL_BACKUP, true),
        telegramAutoArchiveVideos = prefs.getBoolean(KEY_TG_AUTO_ARCHIVE, false),
        activeNicheId = prefs.getLong(KEY_ACTIVE_NICHE_ID, 1L).coerceAtLeast(1L),
        filterAllNiches = prefs.getBoolean(KEY_FILTER_ALL_NICHES, false),
        autoTypeEnabled = prefs.getBoolean(KEY_AUTO_TYPE_ENABLED, true),
        autoTypeSpeedMode = AutoTypeSpeedMode.fromStoredName(prefs.getString(KEY_AUTO_TYPE_SPEED, AutoTypeSpeedMode.MEDIUM.name)),
        autoGenerateNextAfterRender = prefs.getBoolean(KEY_AUTO_GEN_NEXT_RENDER, true),
        autoSummarizeNicheThreshold = prefs.getInt(KEY_AUTO_SUMMARIZE_NICHE_THRESHOLD, 20).coerceIn(10, 50),
        seamlessLoopDefault = prefs.getBoolean(KEY_SEAMLESS_LOOP_DEFAULT, false),
        activeProjectId = prefs.getLong(KEY_ACTIVE_PROJECT_ID, 0L).coerceAtLeast(0L),
        defaultGithubOwner = prefs.getString(KEY_DEFAULT_GH_OWNER, "")?.trim().orEmpty(),
        defaultGithubBranch = prefs.getString(KEY_DEFAULT_GH_BRANCH, "main")?.trim()?.ifBlank { "main" } ?: "main",
        autoWatchActionsAfterPush = prefs.getBoolean(KEY_AUTO_WATCH_ACTIONS, true),
        aiAutoCommitMessageEnabled = prefs.getBoolean(KEY_AI_AUTO_COMMIT_MSG, true)
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value).let { s ->
            s.copy(
                chatFontSizeSp = s.chatFontSizeSp.coerceIn(12f, 20f),
                temperature = s.temperature.coerceIn(0f, 1.5f),
                maxOutputTokens = s.maxOutputTokens.coerceIn(256, 8192),
                chatContextWindow = s.chatContextWindow.coerceIn(4, 30),
                ttsSpeechRate = s.ttsSpeechRate.coerceIn(0.75f, 1.5f),
                kagglePollIntervalSec = if (s.kagglePollIntervalSec in VALID_POLL_INTERVALS) {
                    s.kagglePollIntervalSec
                } else 20,
                newsCheckIntervalHours = if (s.newsCheckIntervalHours in VALID_NEWS_INTERVALS) {
                    s.newsCheckIntervalHours
                } else 24,
                activeWorkspaceId = s.activeWorkspaceId.ifBlank { "default" },
                githubAutoBackupIntervalDays = if (s.githubAutoBackupIntervalDays in VALID_BACKUP_DAYS) {
                    s.githubAutoBackupIntervalDays
                } else 1,
                activeNicheId = s.activeNicheId.coerceAtLeast(1L),
                autoSummarizeNicheThreshold = s.autoSummarizeNicheThreshold.coerceIn(10, 50),
                activeProjectId = s.activeProjectId.coerceAtLeast(0L),
                defaultGithubOwner = s.defaultGithubOwner.trim(),
                defaultGithubBranch = s.defaultGithubBranch.trim().ifBlank { "main" }
            )
        }
        prefs.edit()
            .putString(KEY_ICON_VARIANT, updated.iconVariant.name)
            .putString(KEY_THEME_ACCENT, updated.themeAccent.name)
            .putFloat(KEY_CHAT_FONT_SP, updated.chatFontSizeSp)
            .putFloat(KEY_TEMPERATURE, updated.temperature)
            .putInt(KEY_MAX_OUTPUT_TOKENS, updated.maxOutputTokens)
            .putInt(KEY_CHAT_CONTEXT_WINDOW, updated.chatContextWindow)
            .putBoolean(KEY_AUTO_READ_TTS, updated.autoReadAiReplies)
            .putFloat(KEY_TTS_SPEED, updated.ttsSpeechRate)
            .putBoolean(KEY_WIFI_ONLY_DL, updated.wifiOnlyAutoDownload)
            .putInt(KEY_KAGGLE_POLL_SEC, updated.kagglePollIntervalSec)
            .putBoolean(KEY_AUTO_CLEAN_MERGE, updated.autoCleanTempAfterMerge)
            .putBoolean(KEY_NEWS_AGENT_ENABLED, updated.newsAgentEnabled)
            .putInt(KEY_NEWS_INTERVAL_HOURS, updated.newsCheckIntervalHours)
            .putString(KEY_ACTIVE_WORKSPACE, updated.activeWorkspaceId)
            .putBoolean(KEY_GH_AUTO_BACKUP, updated.githubAutoBackupEnabled)
            .putInt(KEY_GH_BACKUP_DAYS, updated.githubAutoBackupIntervalDays)
            .putBoolean(KEY_ROLLING_LOCAL_BACKUP, updated.rollingLocalBackupEnabled)
            .putBoolean(KEY_TG_AUTO_ARCHIVE, updated.telegramAutoArchiveVideos)
            .putLong(KEY_ACTIVE_NICHE_ID, updated.activeNicheId)
            .putBoolean(KEY_FILTER_ALL_NICHES, updated.filterAllNiches)
            .putBoolean(KEY_AUTO_TYPE_ENABLED, updated.autoTypeEnabled)
            .putString(KEY_AUTO_TYPE_SPEED, updated.autoTypeSpeedMode.name)
            .putBoolean(KEY_AUTO_GEN_NEXT_RENDER, updated.autoGenerateNextAfterRender)
            .putInt(KEY_AUTO_SUMMARIZE_NICHE_THRESHOLD, updated.autoSummarizeNicheThreshold)
            .putBoolean(KEY_SEAMLESS_LOOP_DEFAULT, updated.seamlessLoopDefault)
            .putLong(KEY_ACTIVE_PROJECT_ID, updated.activeProjectId)
            .putString(KEY_DEFAULT_GH_OWNER, updated.defaultGithubOwner)
            .putString(KEY_DEFAULT_GH_BRANCH, updated.defaultGithubBranch)
            .putBoolean(KEY_AUTO_WATCH_ACTIONS, updated.autoWatchActionsAfterPush)
            .putBoolean(KEY_AI_AUTO_COMMIT_MSG, updated.aiAutoCommitMessageEnabled)
            .apply()
        _settings.value = updated
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
        _settings.value = AppSettings()
    }

    companion object {
        private const val PREFS_NAME = "omniai_studio_settings"
        private const val KEY_ICON_VARIANT = "icon_variant"
        private const val KEY_THEME_ACCENT = "theme_accent"
        private const val KEY_CHAT_FONT_SP = "chat_font_sp"
        private const val KEY_TEMPERATURE = "ai_temperature"
        private const val KEY_MAX_OUTPUT_TOKENS = "ai_max_output_tokens"
        private const val KEY_CHAT_CONTEXT_WINDOW = "ai_context_window"
        private const val KEY_AUTO_READ_TTS = "tts_auto_read"
        private const val KEY_TTS_SPEED = "tts_speech_rate"
        private const val KEY_WIFI_ONLY_DL = "wifi_only_download"
        private const val KEY_KAGGLE_POLL_SEC = "kaggle_poll_sec"
        private const val KEY_AUTO_CLEAN_MERGE = "auto_clean_after_merge"
        private const val KEY_NEWS_AGENT_ENABLED = "news_agent_enabled"
        private const val KEY_NEWS_INTERVAL_HOURS = "news_interval_hours"
        private const val KEY_ACTIVE_WORKSPACE = "active_workspace_id"
        private const val KEY_GH_AUTO_BACKUP = "github_auto_backup_enabled"
        private const val KEY_GH_BACKUP_DAYS = "github_auto_backup_days"
        private const val KEY_ROLLING_LOCAL_BACKUP = "rolling_local_backup_enabled"
        private const val KEY_TG_AUTO_ARCHIVE = "telegram_auto_archive_videos"
        private const val KEY_ACTIVE_NICHE_ID = "v40_active_niche_id"
        private const val KEY_FILTER_ALL_NICHES = "v40_filter_all_niches"
        private const val KEY_AUTO_TYPE_ENABLED = "v40_auto_type_enabled"
        private const val KEY_AUTO_TYPE_SPEED = "v40_auto_type_speed"
        private const val KEY_AUTO_GEN_NEXT_RENDER = "v40_auto_gen_next_render"
        private const val KEY_AUTO_SUMMARIZE_NICHE_THRESHOLD = "v40_auto_summarize_niche_threshold"
        private const val KEY_SEAMLESS_LOOP_DEFAULT = "v40_seamless_loop_default"
        private const val KEY_ACTIVE_PROJECT_ID = "v45_active_project_id"
        private const val KEY_DEFAULT_GH_OWNER = "v45_default_github_owner"
        private const val KEY_DEFAULT_GH_BRANCH = "v45_default_github_branch"
        private const val KEY_AUTO_WATCH_ACTIONS = "v45_auto_watch_actions"
        private const val KEY_AI_AUTO_COMMIT_MSG = "v45_ai_auto_commit_msg"

        val VALID_POLL_INTERVALS = listOf(15, 20, 30, 60)
        val VALID_NEWS_INTERVALS = listOf(12, 24)
        val VALID_BACKUP_DAYS = listOf(1, 7)
    }
}
