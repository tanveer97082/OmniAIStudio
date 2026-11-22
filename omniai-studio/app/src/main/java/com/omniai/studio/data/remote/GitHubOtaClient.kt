package com.omniai.studio.data.remote

import com.omniai.studio.security.NetworkSecurityValidator
import com.omniai.studio.security.VaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class GitHubOtaClient(
    private val vault: VaultManager,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchLatestRelease(): GitHubLatestRelease = withContext(Dispatchers.IO) {
        val repo = vault.githubRepo.trim().trim('/')
        require(repo.contains("/")) { "Configure GitHub repo as owner/repo in Vault Settings." }

        val reqBuilder = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()

        if (vault.githubToken.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${vault.githubToken}")
        }

        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("GitHub release check failed: HTTP ${resp.code}")
            }
            json.decodeFromString<GitHubLatestRelease>(resp.body?.string().orEmpty())
        }
    }

    suspend fun downloadReleaseApk(
        asset: GitHubReleaseAsset,
        destApkFile: File,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        destApkFile.parentFile?.mkdirs()

        // For private repos, download via GitHub API asset endpoint with Accept: application/octet-stream
        val useApiAssetUrl = vault.githubToken.isNotBlank()
        val reqBuilder = Request.Builder()
            .url(if (useApiAssetUrl) asset.url else asset.browserDownloadUrl)
            .get()

        if (useApiAssetUrl) {
            reqBuilder.header("Authorization", "Bearer ${vault.githubToken}")
            reqBuilder.header("Accept", "application/octet-stream")
        }

        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("APK download failed: HTTP ${resp.code}")
            }
            val body = resp.body ?: throw IOException("Empty APK stream")
            val total = if (body.contentLength() > 0L) body.contentLength() else asset.size
            var copied = 0L
            val buffer = ByteArray(16 * 1024)

            body.byteStream().use { input ->
                FileOutputStream(destApkFile).use { output ->
                    var read: Int
                    var lastPct = -1
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        copied += read
                        val pct = if (total > 0L) ((copied * 100L) / total).toInt().coerceIn(0, 100) else 50
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                        }
                    }
                    output.flush()
                }
            }
        }
        onProgress(100)
        destApkFile
    }

    suspend fun runAllConnectionPings(): List<EndpointPingResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<EndpointPingResult>()

        // 1. Primary Tunnel
        if (vault.primaryTunnelUrl.isNotBlank()) {
            val validation = NetworkSecurityValidator.validateEndpointUrl(vault.primaryTunnelUrl)
            if (!validation.isValid) {
                results += EndpointPingResult("Primary Tunnel", false, 0L, 0, validation.errorMessage ?: "Invalid URL")
            } else {
                val url = "${validation.normalizedUrl}/v1/models"
                results += measurePing("Primary Tunnel", url) {
                    if (vault.primaryTunnelApiKey.isNotBlank()) {
                        header("Authorization", "Bearer ${vault.primaryTunnelApiKey}")
                    }
                }
            }
        } else {
            results += EndpointPingResult("Primary Tunnel", false, 0L, 0, "Not configured")
        }

        // 2. Hugging Face
        if (vault.huggingFaceToken.isNotBlank()) {
            results += measurePing("Hugging Face", "https://huggingface.co/api/whoami-v2") {
                header("Authorization", "Bearer ${vault.huggingFaceToken}")
            }
        } else {
            results += EndpointPingResult("Hugging Face", false, 0L, 0, "Not configured")
        }

        // 3. Gemini API
        if (vault.geminiApiKey.isNotBlank()) {
            results += measurePing(
                "Gemini 2.5 Flash",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash?key=${vault.geminiApiKey}"
            ) {}
        } else {
            results += EndpointPingResult("Gemini 2.5 Flash", false, 0L, 0, "Not configured")
        }

        // 4. Kaggle API
        if (vault.kaggleUsername.isNotBlank() && vault.kaggleApiKey.isNotBlank()) {
            results += measurePing(
                "Kaggle API",
                "https://www.kaggle.com/api/v1/kernels/list?mine=true&pageSize=1"
            ) {
                header("Authorization", Credentials.basic(vault.kaggleUsername, vault.kaggleApiKey))
            }
        } else {
            results += EndpointPingResult("Kaggle API", false, 0L, 0, "Not configured")
        }

        // 5. GitHub OTA
        if (vault.githubRepo.contains("/")) {
            results += measurePing(
                "GitHub Releases",
                "https://api.github.com/repos/${vault.githubRepo.trim().trim('/')}/releases/latest"
            ) {
                header("Accept", "application/vnd.github+json")
                if (vault.githubToken.isNotBlank()) {
                    header("Authorization", "Bearer ${vault.githubToken}")
                }
            }
        } else {
            results += EndpointPingResult("GitHub Releases", false, 0L, 0, "Not configured")
        }

        results
    }

    private fun measurePing(
        name: String,
        url: String,
        configure: Request.Builder.() -> Unit
    ): EndpointPingResult {
        val start = System.currentTimeMillis()
        return try {
            val req = Request.Builder().url(url).get().apply(configure).build()
            client.newCall(req).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - start
                EndpointPingResult(
                    name = name,
                    reachable = resp.isSuccessful,
                    latencyMs = elapsed,
                    statusCode = resp.code,
                    detail = if (resp.isSuccessful) "Authenticated OK" else "HTTP ${resp.code}"
                )
            }
        } catch (ex: Exception) {
            val elapsed = System.currentTimeMillis() - start
            EndpointPingResult(name, false, elapsed, 0, ex.message ?: "Unreachable")
        }
    }

    companion object {
        fun isRemoteNewer(currentVersion: String, remoteTag: String): Boolean {
            val currentParts = currentVersion.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            val remoteParts = remoteTag.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(currentParts.size, remoteParts.size)
            for (i in 0 until maxLen) {
                val c = currentParts.getOrElse(i) { 0 }
                val r = remoteParts.getOrElse(i) { 0 }
                if (r != c) return r > c
            }
            return false
        }
    }
}
