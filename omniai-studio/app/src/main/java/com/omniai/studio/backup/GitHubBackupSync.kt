package com.omniai.studio.backup

import android.util.Base64
import com.omniai.studio.security.VaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
private data class GitHubBackupContentResponse(
    val sha: String = "",
    val content: String = "",
    val encoding: String = "base64"
)

@Serializable
private data class GitHubBackupCommitRequest(
    val message: String,
    val content: String,
    val sha: String? = null
)

/**
 * Layer 2: Zero-Cost Private GitHub Cloud Auto-Sync.
 * Uploads and restores `backups/omniai_vault.enc` inside the user's private GitHub repository
 * via GitHub Contents REST API with automatic SHA lookup.
 */
class GitHubBackupSync(
    private val vault: VaultManager,
    private val backupManager: VaultBackupManager
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun pushEncryptedBackup(
        passphrase: String,
        selection: BackupSelectionOptions = BackupSelectionOptions()
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = vault.githubRepo.trim()
            val token = vault.githubToken.trim()
            require(repo.contains("/")) { "Configure GitHub Repo (owner/repo) in Vault first." }
            require(token.isNotBlank()) { "Configure GitHub Personal Access Token (contents:write) in Vault." }

            val encryptedJson = backupManager.createEncryptedContainerJson(passphrase, selection)
            val endpoint = "https://api.github.com/repos/$repo/contents/$REMOTE_BACKUP_PATH"

            // Step 1: Lookup existing file SHA (if present) so PUT updates cleanly
            val existingSha = fetchExistingSha(endpoint, token)

            // Step 2: PUT Base64-encoded encrypted container
            val b64Content = Base64.encodeToString(encryptedJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            val commitBody = GitHubBackupCommitRequest(
                message = "OmniAI-Studio encrypted vault backup ($stamp)",
                content = b64Content,
                sha = existingSha
            )

            val putReq = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "OmniAI-Studio-Android")
                .put(json.encodeToString(commitBody).toRequestBody(jsonMediaType))
                .build()

            client.newCall(putReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string()?.take(200).orEmpty()
                    throw IOException("GitHub backup upload failed (HTTP ${resp.code}): $err")
                }
            }

            "Pushed encrypted backup to $repo/$REMOTE_BACKUP_PATH (${encryptedJson.length / 1024} KB)."
        }
    }

    suspend fun restoreLatestBackup(passphrase: String): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = vault.githubRepo.trim()
            val token = vault.githubToken.trim()
            require(repo.contains("/")) { "Configure GitHub Repo (owner/repo) in Vault first." }
            require(token.isNotBlank()) { "Configure GitHub PAT in Vault to read private backups." }

            val endpoint = "https://api.github.com/repos/$repo/contents/$REMOTE_BACKUP_PATH"
            val getReq = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "OmniAI-Studio-Android")
                .get()
                .build()

            val containerJson = client.newCall(getReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("GitHub backup fetch failed (HTTP ${resp.code}). Confirm $REMOTE_BACKUP_PATH exists.")
                }
                val raw = resp.body?.string().orEmpty()
                val parsed = json.decodeFromString<GitHubBackupContentResponse>(raw)
                val cleanB64 = parsed.content.replace("\n", "").replace("\r", "")
                String(Base64.decode(cleanB64, Base64.DEFAULT), Charsets.UTF_8)
            }

            backupManager.restoreFromEncryptedTextOrJson(containerJson, passphrase)
        }
    }

    private fun fetchExistingSha(endpoint: String, token: String): String? {
        val getReq = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "OmniAI-Studio-Android")
            .get()
            .build()
        return client.newCall(getReq).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val raw = resp.body?.string().orEmpty()
            runCatching { json.decodeFromString<GitHubBackupContentResponse>(raw).sha.takeIf { it.isNotBlank() } }
                .getOrNull()
        }
    }

    companion object {
        const val REMOTE_BACKUP_PATH = "backups/omniai_vault.enc"
    }
}
