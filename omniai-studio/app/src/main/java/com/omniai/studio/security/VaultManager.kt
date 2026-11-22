package com.omniai.studio.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.omniai.studio.data.remote.KaggleAccountCredential
import com.omniai.studio.data.remote.VaultSnapshot
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

    var customWebhookUrl: String
        get() = prefs.getString(KEY_CUSTOM_WEBHOOK_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_WEBHOOK_URL, value.trim()).apply()

    var autoBackupPassphrase: String
        get() = prefs.getString(KEY_AUTO_BACKUP_PASS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AUTO_BACKUP_PASS, value).apply()

    var activeKaggleAccountId: String
        get() = prefs.getString(KEY_ACTIVE_KAGGLE_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACTIVE_KAGGLE_ID, value.trim()).apply()

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

    fun exportSnapshot(): VaultSnapshot = VaultSnapshot(
        primaryTunnelUrl = primaryTunnelUrl,
        primaryTunnelApiKey = primaryTunnelApiKey,
        primaryModelName = primaryModelName,
        huggingFaceToken = huggingFaceToken,
        huggingFaceModel = huggingFaceModel,
        geminiApiKey = geminiApiKey,
        autoFallbackEnabled = autoFallbackEnabled,
        kaggleUsername = kaggleUsername,
        kaggleApiKey = kaggleApiKey,
        kaggleDatasetSlug = kaggleDatasetSlug,
        kaggleKernelSlug = kaggleKernelSlug,
        githubRepo = githubRepo,
        githubToken = githubToken,
        telegramBotToken = telegramBotToken,
        telegramChatId = telegramChatId,
        customWebhookUrl = customWebhookUrl,
        appLockEnabled = appLockEnabled,
        vaultLockEnabled = vaultLockEnabled,
        flagSecureEnabled = flagSecureEnabled,
        autoLockTimeout = autoLockTimeout.name
    )

    fun importSnapshot(snapshot: VaultSnapshot) {
        prefs.edit()
            .putString(KEY_PRIMARY_URL, snapshot.primaryTunnelUrl)
            .putString(KEY_PRIMARY_API_KEY, snapshot.primaryTunnelApiKey)
            .putString(KEY_PRIMARY_MODEL, snapshot.primaryModelName)
            .putString(KEY_HF_TOKEN, snapshot.huggingFaceToken)
            .putString(KEY_HF_MODEL, snapshot.huggingFaceModel)
            .putString(KEY_GEMINI_KEY, snapshot.geminiApiKey)
            .putBoolean(KEY_AUTO_FALLBACK, snapshot.autoFallbackEnabled)
            .putString(KEY_KAGGLE_USER, snapshot.kaggleUsername)
            .putString(KEY_KAGGLE_KEY, snapshot.kaggleApiKey)
            .putString(KEY_KAGGLE_DATASET, snapshot.kaggleDatasetSlug)
            .putString(KEY_KAGGLE_KERNEL, snapshot.kaggleKernelSlug)
            .putString(KEY_GITHUB_REPO, snapshot.githubRepo)
            .putString(KEY_GITHUB_TOKEN, snapshot.githubToken)
            .putString(KEY_TG_BOT_TOKEN, snapshot.telegramBotToken)
            .putString(KEY_TG_CHAT_ID, snapshot.telegramChatId)
            .putString(KEY_CUSTOM_WEBHOOK_URL, snapshot.customWebhookUrl)
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
        private const val KEY_AUTO_FALLBACK = "auto_fallback_enabled"
        private const val KEY_KAGGLE_USER = "kaggle_username"
        private const val KEY_KAGGLE_KEY = "kaggle_api_key"
        private const val KEY_KAGGLE_DATASET = "kaggle_dataset_slug"
        private const val KEY_KAGGLE_KERNEL = "kaggle_kernel_slug"
        private const val KEY_GITHUB_REPO = "github_repo"
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_TG_BOT_TOKEN = "tg_bot_token"
        private const val KEY_TG_CHAT_ID = "tg_chat_id"
        private const val KEY_CUSTOM_WEBHOOK_URL = "custom_webhook_url"
        private const val KEY_AUTO_BACKUP_PASS = "auto_backup_passphrase"
        private const val KEY_APP_LOCK = "app_lock_enabled"
        private const val KEY_VAULT_LOCK = "vault_lock_enabled"
        private const val KEY_FLAG_SECURE = "flag_secure_enabled"
        private const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
        private const val KEY_KAGGLE_POOL_JSON = "kaggle_account_pool_json"
        private const val KEY_ACTIVE_KAGGLE_ID = "active_kaggle_account_id"
        private const val KEY_TUNNEL_WATCHDOG = "tunnel_watchdog_enabled"

        fun maskSecret(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return "Not configured"
            if (trimmed.length <= 4) return "••••"
            return "••••" + trimmed.takeLast(4)
        }
    }
}
