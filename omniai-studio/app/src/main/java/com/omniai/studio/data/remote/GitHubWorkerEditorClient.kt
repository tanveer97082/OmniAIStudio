package com.omniai.studio.data.remote

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubContentGetResponse(
    val name: String = "",
    val path: String = "",
    val sha: String = "",
    val content: String = "",
    val encoding: String = "base64"
)

@Serializable
data class GitHubContentPutRequest(
    val message: String,
    val content: String,
    val sha: String
)

data class RemoteWorkerDocument(
    val path: String,
    val sha: String,
    val decodedContent: String
)

/**
 * In-App GitHub Remote Worker Editor client.
 * Fetches and commits `kaggle_worker/video_worker.py` via GitHub Contents REST API (`GET`/`PUT`)
 * directly from Android so pushing updates triggers `.github/workflows/sync-kaggle-worker.yml`.
 */
class GitHubWorkerEditorClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun fetchWorkerFile(
        repoOwnerSlashName: String,
        githubToken: String,
        filePath: String = DEFAULT_WORKER_PATH
    ): Result<RemoteWorkerDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanRepo = repoOwnerSlashName.trim().removePrefix("https://github.com/").trim('/')
            require(cleanRepo.contains('/')) { "Repository must be formatted as owner/repo." }
            require(githubToken.isNotBlank()) { "GitHub Personal Access Token (repo scope) is required." }

            val request = Request.Builder()
                .url("https://api.github.com/repos/$cleanRepo/contents/$filePath")
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer ${githubToken.trim()}")
                .header("User-Agent", "OmniAI-Studio-Android/2.0")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("GitHub GET failed (HTTP ${response.code}): ${body.take(200)}")
                }
                val parsed = json.decodeFromString<GitHubContentGetResponse>(body)
                val sanitizedBase64 = parsed.content.replace("\n", "").replace("\r", "")
                val decoded = String(Base64.decode(sanitizedBase64, Base64.DEFAULT), Charsets.UTF_8)
                RemoteWorkerDocument(
                    path = parsed.path.ifBlank { filePath },
                    sha = parsed.sha,
                    decodedContent = decoded
                )
            }
        }
    }

    suspend fun commitWorkerFile(
        repoOwnerSlashName: String,
        githubToken: String,
        sha: String,
        updatedSource: String,
        commitMessage: String,
        filePath: String = DEFAULT_WORKER_PATH
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanRepo = repoOwnerSlashName.trim().removePrefix("https://github.com/").trim('/')
            require(cleanRepo.contains('/')) { "Repository must be formatted as owner/repo." }
            require(githubToken.isNotBlank()) { "GitHub PAT is required to commit changes." }
            require(sha.isNotBlank()) { "File SHA is required. Fetch the worker file first." }

            val encodedContent = Base64.encodeToString(
                updatedSource.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            val payload = GitHubContentPutRequest(
                message = commitMessage.ifBlank { "Update kaggle_worker/video_worker.py from OmniAI-Studio Android" },
                content = encodedContent,
                sha = sha
            )
            val requestBody = json.encodeToString(payload)
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("https://api.github.com/repos/$cleanRepo/contents/$filePath")
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer ${githubToken.trim()}")
                .header("User-Agent", "OmniAI-Studio-Android/2.0")
                .put(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("GitHub PUT commit failed (HTTP ${response.code}): ${body.take(200)}")
                }
                "Committed $filePath to $cleanRepo — GitHub Actions sync-kaggle-worker.yml triggered."
            }
        }
    }

    companion object {
        const val DEFAULT_WORKER_PATH = "kaggle_worker/video_worker.py"
    }
}
