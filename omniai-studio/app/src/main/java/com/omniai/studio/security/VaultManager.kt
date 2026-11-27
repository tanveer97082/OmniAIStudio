package com.omniai.studio.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.omniai.studio.cloud.UserApiCredentials
import com.omniai.studio.data.remote.CompatibleAiProviderConfig
import com.omniai.studio.data.remote.KaggleAccountCredential
import com.omniai.studio.data.remote.PlatformApiCredential
import com.omniai.studio.data.remote.VaultSnapshot
import com.omniai.studio.data.remote.YouTubeChannelProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class AutoLockTimeout(val label: String, val millis: Long) {
    IMMEDIATE("Immediate", 0L),
    ONE_MINUTE("1 Minute", 60_000L),
    FIVE_MINUTES("5 Minutes", 300_000L),
    NEVER("Never", Long.MAX_VALUE);

    companion object {
        fun fromName(value: String?): AutoLockTimeout =
            entries.firstOrNull { it.name == value } ?: ONE_MINUTE
    }
}

class VaultManager(context: Context) {

    private val appContext = context.applicationContext

    private val masterKey: MasterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        appContext,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var primaryTunnelUrl: String
        get() = prefs.getString(KEY_PRIMARY_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PRIMARY_URL, value.trim()).apply()

    var primaryTunnelApiKey: String
        get() = prefs.getString(KEY_PRIMARY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PRIMARY_API_KEY, value.trim()).apply()

    var primaryModelName: String
        get() = prefs.getString(KEY_PRIMARY_MODEL, "dolphin-mistral") ?: "dolphin-mistral"
        set(value) = prefs.edit().putString(KEY_PRIMARY_MODEL, value.trim()).apply()

    var huggingFaceToken: String
        get() = prefs.getString(KEY_HF_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HF_TOKEN, value.trim()).apply()

    var huggingFaceModel: String
        get() = prefs.getString(KEY_HF_MODEL, "Qwen/Qwen2.5-72B-Instruct") ?: "Qwen/Qwen2.5-72B-Instruct"
        set(value) = prefs.edit().putString(KEY_HF_MODEL, value.trim()).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_KEY, value.trim()).apply()

    var compatibleAiProviders: List<CompatibleAiProviderConfig>
        get() = poolJson.decodeFromString(
            prefs.getString(KEY_COMPATIBLE_AI_PROVIDERS, "[]") ?: "[]"
        )
        set(value) = prefs.edit()
            .putString(KEY_COMPATIBLE_AI_PROVIDERS, poolJson.encodeToString(value))
            .apply()

    var platformApiCredentials: List<PlatformApiCredential>
        get() = poolJson.decodeFromString(
            prefs.getString(KEY_PLATFORM_API_CREDENTIALS, "[]") ?: "[]"
        )
        set(value) = prefs.edit()
            .putString(KEY_PLATFORM_API_CREDENTIALS, poolJson.encodeToString(value))
            .apply()

    var braveSearchApiKey: String
        get() = prefs.getString(KEY_BRAVE_SEARCH_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BRAVE_SEARCH_API_KEY, value.trim()).apply()

    var autoFallbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FALLBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_FALLBACK, value).apply()

    var kaggleUsername: String
        get() = prefs.getString(KEY_KAGGLE_USER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_KAGGLE_USER, value.trim()).apply()

    var kaggleApiKey: String
        get() = prefs.getString(KEY_KAGGLE_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_KAGGLE_KEY, value.trim()).apply()

    var kaggleDatasetSlug: String
        get() = prefs.getString(KEY_KAGGLE_DATASET, "video-tasks-input") ?: "video-tasks-input"
        set(value) = prefs.edit().putString(KEY_KAGGLE_DATASET, value.trim()).apply()

    var kaggleKernelSlug: String
        get() = prefs.getString(KEY_KAGGLE_KERNEL, "omniai-wan21-dual-gpu-worker") ?: "omniai-wan21-dual-gpu-worker"
        set(value) = prefs.edit().putString(KEY_KAGGLE_KERNEL, value.trim()).apply()

    var githubRepo: String
        get() = prefs.getString(KEY_GITHUB_REPO, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GITHUB_REPO, value.trim()).apply()

    var githubToken: String
        get() = prefs.getString(KEY_GITHUB_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GITHUB_TOKEN, value.trim()).apply()

    var appLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_APP_LOCK, value).apply()

    var vaultLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_VAULT_LOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_VAULT_LOCK, value).apply()

    var flagSecureEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLAG_SECURE, true)
        set(value) = prefs.edit().putBoolean(KEY_FLAG_SECURE, value).apply()

    var autoLockTimeout: AutoLockTimeout
        get() = AutoLockTimeout.fromName(prefs.getString(KEY_AUTO_LOCK_TIMEOUT, AutoLockTimeout.ONE_MINUTE.name))
        set(value) = prefs.edit().putString(KEY_AUTO_LOCK_TIMEOUT, value.name).apply()

    var tunnelWatchdogEnabled: Boolean
        get() = prefs.getBoolean(KEY_TUNNEL_WATCHDOG, false)
        set(value) = prefs.edit().putBoolean(KEY_TUNNEL_WATCHDOG, value).apply()

    var telegramBotToken: String
        get() = prefs.getString(KEY_TG_BOT_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TG_BOT_TOKEN, value.trim()).apply()

    var telegramChatId: String
        get() = prefs.getString(KEY_TG_CHAT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TG_CHAT_ID, value.trim()).apply()

    var telegramApiId: String
        get() = prefs.getString(KEY_TG_API_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TG_API_ID, value.trim()).apply()

    var telegramApiHash: String
        get() = prefs.getString(KEY_TG_API_HASH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TG_API_HASH, value.trim()).apply()

    var customWebhookUrl: String
        get() = prefs.getString(KEY_CUSTOM_WEBHOOK_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_WEBHOOK_URL, value.trim()).apply()

    var autoBackupPassphrase: String
        get() = prefs.getString(KEY_AUTO_BACKUP_PASS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AUTO_BACKUP_PASS, value).apply()

    var activeKaggleAccountId: String
        get() = prefs.getString(KEY_ACTIVE_KAGGLE_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACTIVE_KAGGLE_ID, value.trim()).apply()

    // v5.3 YouTube Data API v3 Multi-Channel Credentials (AES-256-GCM Encrypted)
    var ytClientId: String
        get() = prefs.getString(KEY_YT_CLIENT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_YT_CLIENT_ID, value.trim()).apply()

    var ytClientSecret: String
        get() = prefs.getString(KEY_YT_CLIENT_SECRET, "") ?: ""
        set(value) = prefs.edit().putString(KEY_YT_CLIENT_SECRET, value.trim()).apply()

    var ytRefreshToken: String
        get() = prefs.getString(KEY_YT_REFRESH_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_YT_REFRESH_TOKEN, value.trim()).apply()

    var ytChannelLabel: String
        get() = prefs.getString(KEY_YT_CHANNEL_LABEL, "Primary Channel") ?: "Primary Channel"
        set(value) = prefs.edit().putString(KEY_YT_CHANNEL_LABEL, value.trim().ifBlank { "Primary Channel" }).apply()

    var activeYouTubeProfileId: String
        get() = prefs.getString(KEY_ACTIVE_YT_PROFILE_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACTIVE_YT_PROFILE_ID, value.trim()).apply()

    // v5.3 Meta Graph API (Facebook Page Reels & Instagram Reels) Credentials
    var metaPageId: String
        get() = prefs.getString(KEY_META_PAGE_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_META_PAGE_ID, value.trim()).apply()

    var metaIgUserId: String
        get() = prefs.getString(KEY_META_IG_USER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_META_IG_USER_ID, value.trim()).apply()

    var metaPageAccessToken: String
        get() = prefs.getString(KEY_META_PAGE_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_META_PAGE_TOKEN, value.trim()).apply()

    // v5.5 Supabase PostgreSQL Cloud DB, Google OAuth PKCE & Delta Sync (AES-256-GCM Encrypted)
    var supabaseUrl: String
        get() = prefs.getString(KEY_SUPABASE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SUPABASE_URL, value.trim().trimEnd('/')).apply()

    var supabaseAnonKey: String
        get() = prefs.getString(KEY_SUPABASE_ANON_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SUPABASE_ANON_KEY, value.trim()).apply()

    var cloudAccessToken: String
        get() = prefs.getString(KEY_CLOUD_ACCESS_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLOUD_ACCESS_TOKEN, value.trim()).apply()

    var cloudRefreshToken: String
        get() = prefs.getString(KEY_CLOUD_REFRESH_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLOUD_REFRESH_TOKEN, value.trim()).apply()

    var cloudTokenExpiresAtMs: Long
        get() = prefs.getLong(KEY_CLOUD_TOKEN_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_CLOUD_TOKEN_EXPIRES_AT, value).apply()

    var cloudUserId: String
        get() = prefs.getString(KEY_CLOUD_USER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLOUD_USER_ID, value.trim()).apply()

    var cloudUserEmail: String
        get() = prefs.getString(KEY_CLOUD_USER_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLOUD_USER_EMAIL, value.trim()).apply()

    var cloudUserDisplayName: String
        get() = prefs.getString(KEY_CLOUD_USER_DISPLAY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLOUD_USER_DISPLAY_NAME, value.trim()).apply()

    var cloudUserRole: String
        get() = prefs.getString(KEY_CLOUD_USER_ROLE, "OFFLINE_LOCAL") ?: "OFFLINE_LOCAL"
        set(value) = prefs.edit().putString(KEY_CLOUD_USER_ROLE, value.trim().uppercase()).apply()

    var cloudIsApproved: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_IS_APPROVED, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOUD_IS_APPROVED, value).apply()

    var cloudPkceVerifier: String
        get() = prefs.getString(KEY_CLOUD_PKCE_VERIFIER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLOUD_PKCE_VERIFIER, value.trim()).apply()

    var cloudOfflineLocalMode: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_OFFLINE_LOCAL_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_CLOUD_OFFLINE_LOCAL_MODE, value).apply()

    var cloudLastPushAtMs: Long
        get() = prefs.getLong(KEY_CLOUD_LAST_PUSH_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_CLOUD_LAST_PUSH_AT, value).apply()

    var cloudLastPullAtMs: Long
        get() = prefs.getLong(KEY_CLOUD_LAST_PULL_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_CLOUD_LAST_PULL_AT, value).apply()

    var cloudLastSyncAtMs: Long
        get() = prefs.getLong(KEY_CLOUD_LAST_SYNC_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_CLOUD_LAST_SYNC_AT, value).apply()

    var cloudLastSavedPercent: Int
        get() = prefs.getInt(KEY_CLOUD_LAST_SAVED_PCT, 0)
        set(value) = prefs.edit().putInt(KEY_CLOUD_LAST_SAVED_PCT, value.coerceIn(0, 99)).apply()

    var cloudLastSyncedRows: Int
        get() = prefs.getInt(KEY_CLOUD_LAST_SYNCED_ROWS, 0)
        set(value) = prefs.edit().putInt(KEY_CLOUD_LAST_SYNCED_ROWS, value.coerceAtLeast(0)).apply()

    var cloudSyncIntervalMinutes: Int
        get() = prefs.getInt(KEY_CLOUD_SYNC_INTERVAL_MIN, 60)
        set(value) = prefs.edit().putInt(KEY_CLOUD_SYNC_INTERVAL_MIN, value).apply()

    var cloudSyncWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_SYNC_WIFI_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOUD_SYNC_WIFI_ONLY, value).apply()

    fun clearCloudSession() {
        prefs.edit()
            .putString(KEY_CLOUD_ACCESS_TOKEN, "")
            .putString(KEY_CLOUD_REFRESH_TOKEN, "")
            .putLong(KEY_CLOUD_TOKEN_EXPIRES_AT, 0L)
            .putString(KEY_CLOUD_USER_ID, "")
            .putString(KEY_CLOUD_USER_EMAIL, "")
            .putString(KEY_CLOUD_USER_DISPLAY_NAME, "")
            .putString(KEY_CLOUD_USER_ROLE, "OFFLINE_LOCAL")
            .putBoolean(KEY_CLOUD_IS_APPROVED, false)
            .putString(KEY_CLOUD_PKCE_VERIFIER, "")
            .putBoolean(KEY_CLOUD_OFFLINE_LOCAL_MODE, true)
            .commit()
    }

    private val poolJson = Json { ignoreUnknownKeys = true }

    fun getKaggleAccountPool(): List<KaggleAccountCredential> {
        val raw = prefs.getString(KEY_KAGGLE_POOL_JSON, null)
        val stored = if (!raw.isNullOrBlank()) {
            runCatching { poolJson.decodeFromString<List<KaggleAccountCredential>>(raw) }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }
        if (stored.isNotEmpty()) return stored
        if (kaggleUsername.isNotBlank() && kaggleApiKey.isNotBlank()) {
            return listOf(
                KaggleAccountCredential(
                    id = "primary",
                    label = "Primary (${kaggleUsername})",
                    username = kaggleUsername,
                    apiKey = kaggleApiKey,
                    datasetSlug = kaggleDatasetSlug,
                    kernelSlug = kaggleKernelSlug
                )
            )
        }
        return emptyList()
    }

    fun replaceKaggleAccountPool(accounts: List<KaggleAccountCredential>) {
        prefs.edit().putString(KEY_KAGGLE_POOL_JSON, poolJson.encodeToString(accounts)).apply()
        if (accounts.isNotEmpty() && accounts.none { it.id == activeKaggleAccountId }) {
            selectKaggleAccount(accounts.first().id)
        }
    }

    fun getActiveKaggleAccount(): KaggleAccountCredential? {
        val pool = getKaggleAccountPool()
        if (pool.isEmpty()) return null
        return pool.firstOrNull { it.id == activeKaggleAccountId } ?: pool.first()
    }

    fun saveKaggleAccount(account: KaggleAccountCredential) {
        val updated = getKaggleAccountPool().filterNot { it.id == account.id } + account
        prefs.edit().putString(KEY_KAGGLE_POOL_JSON, poolJson.encodeToString(updated)).apply()
        if (activeKaggleAccountId.isBlank() || activeKaggleAccountId == account.id) {
            selectKaggleAccount(account.id)
        }
    }

    fun removeKaggleAccount(accountId: String) {
        val remaining = getKaggleAccountPool().filterNot { it.id == accountId }
        prefs.edit().putString(KEY_KAGGLE_POOL_JSON, poolJson.encodeToString(remaining)).apply()
        if (activeKaggleAccountId == accountId) {
            remaining.firstOrNull()?.let { selectKaggleAccount(it.id) }
        }
    }

    fun selectKaggleAccount(accountId: String): KaggleAccountCredential? {
        val target = getKaggleAccountPool().firstOrNull { it.id == accountId } ?: return null
        activeKaggleAccountId = target.id
        kaggleUsername = target.username
        kaggleApiKey = target.apiKey
        kaggleDatasetSlug = target.datasetSlug
        kaggleKernelSlug = target.kernelSlug
        return target
    }

    /**
     * Rotates to the next available Kaggle account in the pool when the active account's
     * weekly GPU quota drops below 1.0 hour remaining.
     */
    fun rotateToNextKaggleAccount(): KaggleAccountCredential? {
        val pool = getKaggleAccountPool()
        if (pool.size < 2) return null
        val currentIdx = pool.indexOfFirst { it.id == activeKaggleAccountId }.coerceAtLeast(0)
        val next = pool[(currentIdx + 1) % pool.size]
        return selectKaggleAccount(next.id)
    }

    // --- v5.3 Multi-Channel YouTube OAuth Profile Pool ---

    fun getYouTubeProfiles(): List<YouTubeChannelProfile> {
        val raw = prefs.getString(KEY_YT_PROFILES_JSON, null)
        val stored = if (!raw.isNullOrBlank()) {
            runCatching { poolJson.decodeFromString<List<YouTubeChannelProfile>>(raw) }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }
        if (stored.isNotEmpty()) return stored
        if (ytClientId.isNotBlank() && ytClientSecret.isNotBlank() && ytRefreshToken.isNotBlank()) {
            return listOf(
                YouTubeChannelProfile(
                    id = "primary_yt",
                    label = ytChannelLabel.ifBlank { "Primary Channel" },
                    clientId = ytClientId,
                    clientSecret = ytClientSecret,
                    refreshToken = ytRefreshToken
                )
            )
        }
        return emptyList()
    }

    fun replaceYouTubeProfiles(profiles: List<YouTubeChannelProfile>) {
        prefs.edit().putString(KEY_YT_PROFILES_JSON, poolJson.encodeToString(profiles)).apply()
        if (profiles.isNotEmpty() && profiles.none { it.id == activeYouTubeProfileId }) {
            selectYouTubeProfile(profiles.first().id)
        }
    }

    fun getYouTubeProfileById(profileId: String?): YouTubeChannelProfile? {
        val pool = getYouTubeProfiles()
        if (pool.isEmpty()) return null
        if (!profileId.isNullOrBlank()) {
            pool.firstOrNull { it.id == profileId }?.let { return it }
        }
        return pool.firstOrNull { it.id == activeYouTubeProfileId } ?: pool.first()
    }

    fun getActiveYouTubeProfile(): YouTubeChannelProfile? = getYouTubeProfileById(activeYouTubeProfileId)

    fun saveYouTubeProfile(profile: YouTubeChannelProfile) {
        val updated = getYouTubeProfiles().filterNot { it.id == profile.id } + profile
        prefs.edit().putString(KEY_YT_PROFILES_JSON, poolJson.encodeToString(updated)).apply()
        if (activeYouTubeProfileId.isBlank() || activeYouTubeProfileId == profile.id) {
            selectYouTubeProfile(profile.id)
        }
    }

    fun removeYouTubeProfile(profileId: String) {
        val remaining = getYouTubeProfiles().filterNot { it.id == profileId }
        prefs.edit().putString(KEY_YT_PROFILES_JSON, poolJson.encodeToString(remaining)).apply()
        if (activeYouTubeProfileId == profileId) {
            if (remaining.isNotEmpty()) {
                selectYouTubeProfile(remaining.first().id)
            } else {
                activeYouTubeProfileId = ""
            }
        }
    }

    fun selectYouTubeProfile(profileId: String): YouTubeChannelProfile? {
        val target = getYouTubeProfiles().firstOrNull { it.id == profileId } ?: return null
        activeYouTubeProfileId = target.id
        ytClientId = target.clientId
        ytClientSecret = target.clientSecret
        ytRefreshToken = target.refreshToken
        ytChannelLabel = target.label
        return target
    }

    fun exportSnapshot(): VaultSnapshot = VaultSnapshot(
        primaryTunnelUrl = primaryTunnelUrl,
        primaryTunnelApiKey = primaryTunnelApiKey,
        primaryModelName = primaryModelName,
        huggingFaceToken = huggingFaceToken,
        huggingFaceModel = huggingFaceModel,
        geminiApiKey = geminiApiKey,
        autoFallbackEnabled = autoFallbackEnabled,
        compatibleAiProviders = compatibleAiProviders,
        platformApiCredentials = platformApiCredentials,
        braveSearchApiKey = braveSearchApiKey,
        kaggleUsername = kaggleUsername,
        kaggleApiKey = kaggleApiKey,
        kaggleDatasetSlug = kaggleDatasetSlug,
        kaggleKernelSlug = kaggleKernelSlug,
        githubRepo = githubRepo,
        githubToken = githubToken,
        telegramBotToken = telegramBotToken,
        telegramChatId = telegramChatId,
        telegramApiId = telegramApiId,
        telegramApiHash = telegramApiHash,
        customWebhookUrl = customWebhookUrl,
        ytClientId = ytClientId,
        ytClientSecret = ytClientSecret,
        ytRefreshToken = ytRefreshToken,
        ytChannelLabel = ytChannelLabel,
        ytProfilesJson = prefs.getString(KEY_YT_PROFILES_JSON, "[]") ?: "[]",
        activeYtProfileId = activeYouTubeProfileId,
        metaPageId = metaPageId,
        metaIgUserId = metaIgUserId,
        metaPageAccessToken = metaPageAccessToken,
        supabaseUrl = supabaseUrl,
        supabaseAnonKey = supabaseAnonKey,
        appLockEnabled = appLockEnabled,
        vaultLockEnabled = vaultLockEnabled,
        flagSecureEnabled = flagSecureEnabled,
        autoLockTimeout = autoLockTimeout.name
    )

    fun exportUserApiCredentials(): UserApiCredentials = UserApiCredentials(
        primaryTunnelUrl = primaryTunnelUrl,
        primaryTunnelApiKey = primaryTunnelApiKey,
        primaryModelName = primaryModelName,
        huggingFaceToken = huggingFaceToken,
        huggingFaceModel = huggingFaceModel,
        geminiApiKey = geminiApiKey,
        autoFallbackEnabled = autoFallbackEnabled,
        compatibleAiProviders = compatibleAiProviders,
        platformApiCredentials = platformApiCredentials,
        braveSearchApiKey = braveSearchApiKey,
        kaggleUsername = kaggleUsername,
        kaggleApiKey = kaggleApiKey,
        kaggleDatasetSlug = kaggleDatasetSlug,
        kaggleKernelSlug = kaggleKernelSlug,
        githubRepo = githubRepo,
        githubToken = githubToken,
        telegramBotToken = telegramBotToken,
        telegramChatId = telegramChatId,
        telegramApiId = telegramApiId,
        telegramApiHash = telegramApiHash,
        customWebhookUrl = customWebhookUrl,
        ytClientId = ytClientId,
        ytClientSecret = ytClientSecret,
        ytRefreshToken = ytRefreshToken,
        ytChannelLabel = ytChannelLabel,
        ytProfilesJson = prefs.getString(KEY_YT_PROFILES_JSON, "[]") ?: "[]",
        activeYtProfileId = activeYouTubeProfileId,
        metaPageId = metaPageId,
        metaIgUserId = metaIgUserId,
        metaPageAccessToken = metaPageAccessToken
    )

    fun importUserApiCredentials(credentials: UserApiCredentials): Boolean =
        prefs.edit()
            .putString(KEY_PRIMARY_URL, credentials.primaryTunnelUrl.trim().trimEnd('/'))
            .putString(KEY_PRIMARY_API_KEY, credentials.primaryTunnelApiKey.trim())
            .putString(KEY_PRIMARY_MODEL, credentials.primaryModelName.trim())
            .putString(KEY_HF_TOKEN, credentials.huggingFaceToken.trim())
            .putString(KEY_HF_MODEL, credentials.huggingFaceModel.trim())
            .putString(KEY_GEMINI_KEY, credentials.geminiApiKey.trim())
            .putBoolean(KEY_AUTO_FALLBACK, credentials.autoFallbackEnabled)
            .putString(KEY_COMPATIBLE_AI_PROVIDERS, poolJson.encodeToString(credentials.compatibleAiProviders))
            .putString(KEY_PLATFORM_API_CREDENTIALS, poolJson.encodeToString(credentials.platformApiCredentials))
            .putString(KEY_BRAVE_SEARCH_API_KEY, credentials.braveSearchApiKey.trim())
            .putString(KEY_KAGGLE_USER, credentials.kaggleUsername.trim())
            .putString(KEY_KAGGLE_KEY, credentials.kaggleApiKey.trim())
            .putString(KEY_KAGGLE_DATASET, credentials.kaggleDatasetSlug.trim())
            .putString(KEY_KAGGLE_KERNEL, credentials.kaggleKernelSlug.trim())
            .putString(KEY_KAGGLE_POOL_JSON, poolJson.encodeToString(credentials.kaggleAccountPool))
            .putString(KEY_ACTIVE_KAGGLE_ID, credentials.activeKaggleAccountId.trim())
            .putString(KEY_GITHUB_REPO, credentials.githubRepo.trim())
            .putString(KEY_GITHUB_TOKEN, credentials.githubToken.trim())
            .putString(KEY_TG_BOT_TOKEN, credentials.telegramBotToken.trim())
            .putString(KEY_TG_CHAT_ID, credentials.telegramChatId.trim())
            .putString(KEY_TG_API_ID, credentials.telegramApiId.trim())
            .putString(KEY_TG_API_HASH, credentials.telegramApiHash.trim())
            .putString(KEY_CUSTOM_WEBHOOK_URL, credentials.customWebhookUrl.trim())
            .putString(KEY_YT_CLIENT_ID, credentials.ytClientId.trim())
            .putString(KEY_YT_CLIENT_SECRET, credentials.ytClientSecret.trim())
            .putString(KEY_YT_REFRESH_TOKEN, credentials.ytRefreshToken.trim())
            .putString(KEY_YT_CHANNEL_LABEL, credentials.ytChannelLabel.trim().ifBlank { "Primary Channel" })
            .putString(KEY_YT_PROFILES_JSON, credentials.ytProfilesJson.ifBlank { "[]" })
            .putString(KEY_ACTIVE_YT_PROFILE_ID, credentials.activeYtProfileId.trim())
            .putString(KEY_META_PAGE_ID, credentials.metaPageId.trim())
            .putString(KEY_META_IG_USER_ID, credentials.metaIgUserId.trim())
            .putString(KEY_META_PAGE_TOKEN, credentials.metaPageAccessToken.trim())
            .commit()

    fun importSnapshot(snapshot: VaultSnapshot) {
        prefs.edit()
            .putString(KEY_PRIMARY_URL, snapshot.primaryTunnelUrl)
            .putString(KEY_PRIMARY_API_KEY, snapshot.primaryTunnelApiKey)
            .putString(KEY_PRIMARY_MODEL, snapshot.primaryModelName)
            .putString(KEY_HF_TOKEN, snapshot.huggingFaceToken)
            .putString(KEY_HF_MODEL, snapshot.huggingFaceModel)
            .putString(KEY_GEMINI_KEY, snapshot.geminiApiKey)
            .putBoolean(KEY_AUTO_FALLBACK, snapshot.autoFallbackEnabled)
            .putString(KEY_COMPATIBLE_AI_PROVIDERS, poolJson.encodeToString(snapshot.compatibleAiProviders))
            .putString(KEY_PLATFORM_API_CREDENTIALS, poolJson.encodeToString(snapshot.platformApiCredentials))
            .putString(KEY_BRAVE_SEARCH_API_KEY, snapshot.braveSearchApiKey.trim())
            .putString(KEY_KAGGLE_USER, snapshot.kaggleUsername)
            .putString(KEY_KAGGLE_KEY, snapshot.kaggleApiKey)
            .putString(KEY_KAGGLE_DATASET, snapshot.kaggleDatasetSlug)
            .putString(KEY_KAGGLE_KERNEL, snapshot.kaggleKernelSlug)
            .putString(KEY_GITHUB_REPO, snapshot.githubRepo)
            .putString(KEY_GITHUB_TOKEN, snapshot.githubToken)
            .putString(KEY_TG_BOT_TOKEN, snapshot.telegramBotToken)
            .putString(KEY_TG_CHAT_ID, snapshot.telegramChatId)
            .putString(KEY_TG_API_ID, snapshot.telegramApiId)
            .putString(KEY_TG_API_HASH, snapshot.telegramApiHash)
            .putString(KEY_CUSTOM_WEBHOOK_URL, snapshot.customWebhookUrl)
            .putString(KEY_YT_CLIENT_ID, snapshot.ytClientId)
            .putString(KEY_YT_CLIENT_SECRET, snapshot.ytClientSecret)
            .putString(KEY_YT_REFRESH_TOKEN, snapshot.ytRefreshToken)
            .putString(KEY_YT_CHANNEL_LABEL, snapshot.ytChannelLabel.ifBlank { "Primary Channel" })
            .putString(KEY_YT_PROFILES_JSON, snapshot.ytProfilesJson.ifBlank { "[]" })
            .putString(KEY_ACTIVE_YT_PROFILE_ID, snapshot.activeYtProfileId)
            .putString(KEY_META_PAGE_ID, snapshot.metaPageId)
            .putString(KEY_META_IG_USER_ID, snapshot.metaIgUserId)
            .putString(KEY_META_PAGE_TOKEN, snapshot.metaPageAccessToken)
            .putString(KEY_SUPABASE_URL, snapshot.supabaseUrl.trim().trimEnd('/'))
            .putString(KEY_SUPABASE_ANON_KEY, snapshot.supabaseAnonKey.trim())
            .putBoolean(KEY_APP_LOCK, snapshot.appLockEnabled)
            .putBoolean(KEY_VAULT_LOCK, snapshot.vaultLockEnabled)
            .putBoolean(KEY_FLAG_SECURE, snapshot.flagSecureEnabled)
            .putString(KEY_AUTO_LOCK_TIMEOUT, snapshot.autoLockTimeout)
            .commit()
    }

    /** Shreds all keys by overwriting entries with blank values before clearing EncryptedSharedPreferences. */
    fun panicWipe() {
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            editor.putString(key, "")
        }
        editor.commit()
        prefs.edit().clear().commit()
    }

    companion object {
        private const val PREFS_FILE_NAME = "omniai_secure_vault_v1"

        private const val KEY_PRIMARY_URL = "primary_tunnel_url"
        private const val KEY_PRIMARY_API_KEY = "primary_tunnel_api_key"
        private const val KEY_PRIMARY_MODEL = "primary_model_name"
        private const val KEY_HF_TOKEN = "hf_token"
        private const val KEY_HF_MODEL = "hf_model"
        private const val KEY_GEMINI_KEY = "gemini_api_key"
        private const val KEY_COMPATIBLE_AI_PROVIDERS = "compatible_ai_providers"
        private const val KEY_PLATFORM_API_CREDENTIALS = "platform_api_credentials"
        private const val KEY_BRAVE_SEARCH_API_KEY = "brave_search_api_key"
        private const val KEY_AUTO_FALLBACK = "auto_fallback_enabled"
        private const val KEY_KAGGLE_USER = "kaggle_username"
        private const val KEY_KAGGLE_KEY = "kaggle_api_key"
        private const val KEY_KAGGLE_DATASET = "kaggle_dataset_slug"
        private const val KEY_KAGGLE_KERNEL = "kaggle_kernel_slug"
        private const val KEY_GITHUB_REPO = "github_repo"
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_TG_BOT_TOKEN = "tg_bot_token"
        private const val KEY_TG_CHAT_ID = "tg_chat_id"
        private const val KEY_TG_API_ID = "tg_api_id"
        private const val KEY_TG_API_HASH = "tg_api_hash"
        private const val KEY_CUSTOM_WEBHOOK_URL = "custom_webhook_url"
        private const val KEY_AUTO_BACKUP_PASS = "auto_backup_passphrase"
        private const val KEY_APP_LOCK = "app_lock_enabled"
        private const val KEY_VAULT_LOCK = "vault_lock_enabled"
        private const val KEY_FLAG_SECURE = "flag_secure_enabled"
        private const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
        private const val KEY_KAGGLE_POOL_JSON = "kaggle_account_pool_json"
        private const val KEY_ACTIVE_KAGGLE_ID = "active_kaggle_account_id"
        private const val KEY_TUNNEL_WATCHDOG = "tunnel_watchdog_enabled"
        private const val KEY_YT_CLIENT_ID = "v53_yt_client_id"
        private const val KEY_YT_CLIENT_SECRET = "v53_yt_client_secret"
        private const val KEY_YT_REFRESH_TOKEN = "v53_yt_refresh_token"
        private const val KEY_YT_CHANNEL_LABEL = "v53_yt_channel_label"
        private const val KEY_YT_PROFILES_JSON = "v53_yt_profiles_json"
        private const val KEY_ACTIVE_YT_PROFILE_ID = "v53_active_yt_profile_id"
        private const val KEY_META_PAGE_ID = "v53_meta_page_id"
        private const val KEY_META_IG_USER_ID = "v53_meta_ig_user_id"
        private const val KEY_META_PAGE_TOKEN = "v53_meta_page_access_token"
        private const val KEY_SUPABASE_URL = "SUPABASE_URL"
        private const val KEY_SUPABASE_ANON_KEY = "SUPABASE_ANON_KEY"
        private const val KEY_CLOUD_ACCESS_TOKEN = "CLOUD_ACCESS_TOKEN"
        private const val KEY_CLOUD_REFRESH_TOKEN = "CLOUD_REFRESH_TOKEN"
        private const val KEY_CLOUD_TOKEN_EXPIRES_AT = "CLOUD_TOKEN_EXPIRES_AT"
        private const val KEY_CLOUD_USER_ID = "CLOUD_USER_ID"
        private const val KEY_CLOUD_USER_EMAIL = "CLOUD_USER_EMAIL"
        private const val KEY_CLOUD_USER_DISPLAY_NAME = "CLOUD_USER_DISPLAY_NAME"
        private const val KEY_CLOUD_USER_ROLE = "CLOUD_USER_ROLE"
        private const val KEY_CLOUD_IS_APPROVED = "CLOUD_IS_APPROVED"
        private const val KEY_CLOUD_PKCE_VERIFIER = "CLOUD_PKCE_VERIFIER"
        private const val KEY_CLOUD_OFFLINE_LOCAL_MODE = "CLOUD_OFFLINE_LOCAL_MODE"
        private const val KEY_CLOUD_LAST_PUSH_AT = "CLOUD_LAST_PUSH_AT"
        private const val KEY_CLOUD_LAST_PULL_AT = "CLOUD_LAST_PULL_AT"
        private const val KEY_CLOUD_LAST_SYNC_AT = "CLOUD_LAST_SYNC_AT"
        private const val KEY_CLOUD_LAST_SAVED_PCT = "CLOUD_LAST_SAVED_PCT"
        private const val KEY_CLOUD_LAST_SYNCED_ROWS = "CLOUD_LAST_SYNCED_ROWS"
        private const val KEY_CLOUD_SYNC_INTERVAL_MIN = "CLOUD_SYNC_INTERVAL_MIN"
        private const val KEY_CLOUD_SYNC_WIFI_ONLY = "CLOUD_SYNC_WIFI_ONLY"

        fun maskSecret(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return "Not configured"
            if (trimmed.length <= 4) return "••••"
            return "••••" + trimmed.takeLast(4)
        }
    }
}
