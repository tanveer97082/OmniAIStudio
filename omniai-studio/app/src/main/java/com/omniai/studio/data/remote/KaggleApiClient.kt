package com.omniai.studio.data.remote

import com.omniai.studio.security.VaultManager
import com.omniai.studio.terminal.NetworkLogInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class KaggleApiClient(
    private val vault: VaultManager,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(NetworkLogInterceptor)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val binaryMedia = "application/octet-stream".toMediaType()

    private fun authHeader(): String {
        val user = vault.kaggleUsername
        val key = vault.kaggleApiKey
        require(user.isNotBlank() && key.isNotBlank()) { "Kaggle username and API key must be configured in Vault." }
        return Credentials.basic(user, key)
    }

    /**
     * Uploads `scenes.json` (and optional `ref_image.jpg`) as a new version of `<username>/<datasetSlug>`
     * via Kaggle Blob + Dataset Version API.
     */
    suspend fun uploadScenesJson(
        scenesJson: String,
        refImageFile: File? = null
    ): Unit = withContext(Dispatchers.IO) {
        val uploadedTokens = mutableListOf<KaggleDatasetVersionFile>()

        val scenesToken = uploadSingleBlob(
            fileName = "scenes.json",
            bytes = scenesJson.toByteArray(Charsets.UTF_8)
        )
        uploadedTokens.add(KaggleDatasetVersionFile(token = scenesToken))

        if (refImageFile != null && refImageFile.exists() && refImageFile.length() > 0L) {
            val refToken = uploadSingleBlob(
                fileName = "ref_image.jpg",
                bytes = refImageFile.readBytes()
            )
            uploadedTokens.add(KaggleDatasetVersionFile(token = refToken))
        }

        val versionPayload = json.encodeToString(
            KaggleDatasetVersionRequest(
                versionNotes = "OmniAI-Studio v2.0 automated render batch ${System.currentTimeMillis()}",
                files = uploadedTokens
            )
        )

        val owner = vault.kaggleUsername
        val datasetSlug = vault.kaggleDatasetSlug.ifBlank { "video-tasks-input" }
        val createVersionReq = Request.Builder()
            .url("https://www.kaggle.com/api/v1/datasets/create/version/$owner/$datasetSlug")
            .header("Authorization", authHeader())
            .post(versionPayload.toRequestBody(jsonMedia))
            .build()

        client.newCall(createVersionReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Kaggle dataset version creation failed: HTTP ${resp.code}")
            }
        }
    }

    private fun uploadSingleBlob(fileName: String, bytes: ByteArray): String {
        val blobReqPayload = json.encodeToString(
            KaggleUploadBlobRequest(
                name = fileName,
                contentLength = bytes.size.toLong(),
                lastModifiedEpochSeconds = System.currentTimeMillis() / 1000L
            )
        )

        val initReq = Request.Builder()
            .url("https://www.kaggle.com/api/v1/blobs/upload")
            .header("Authorization", authHeader())
            .post(blobReqPayload.toRequestBody(jsonMedia))
            .build()

        val uploadBlob = client.newCall(initReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Kaggle blob init failed for $fileName: HTTP ${resp.code}")
            }
            json.decodeFromString<KaggleUploadBlobResponse>(resp.body?.string().orEmpty())
        }

        if (uploadBlob.createUrl.isBlank() || uploadBlob.token.isBlank()) {
            throw IOException("Kaggle did not return an upload URL or blob token for $fileName.")
        }

        val putReq = Request.Builder()
            .url(uploadBlob.createUrl)
            .put(bytes.toRequestBody(binaryMedia))
            .build()

        client.newCall(putReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Kaggle blob PUT upload failed for $fileName: HTTP ${resp.code}")
            }
        }
        return uploadBlob.token
    }

    /**
     * Triggers the synced Dual-GPU Wan 2.1 Kaggle Kernel to re-run against the freshly uploaded dataset version.
     */
    suspend fun triggerDualGpuKernel(): Unit = withContext(Dispatchers.IO) {
        val owner = vault.kaggleUsername
        val kernelSlug = vault.kaggleKernelSlug.ifBlank { "omniai-wan21-dual-gpu-worker" }
        val datasetSlug = vault.kaggleDatasetSlug.ifBlank { "video-tasks-input" }

        val pullReq = Request.Builder()
            .url("https://www.kaggle.com/api/v1/kernels/pull?userName=$owner&kernelSlug=$kernelSlug")
            .header("Authorization", authHeader())
            .get()
            .build()

        val pulledBody = client.newCall(pullReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Failed to fetch Kaggle kernel metadata: HTTP ${resp.code}")
            }
            resp.body?.string().orEmpty()
        }

        // Re-push kernel source to start a fresh T4 x2 GPU run
        val sourceCode = extractSourceFromPull(pulledBody)
        val pushJson = """
            {
              "slug": "$owner/$kernelSlug",
              "newTitle": "OmniAI Wan2.1 Dual-GPU Video Worker",
              "text": ${json.encodeToString(sourceCode)},
              "language": "python",
              "kernelType": "script",
              "isPrivate": true,
              "enableGpu": true,
              "enableInternet": true,
              "datasetDataSources": ["$owner/$datasetSlug"]
            }
        """.trimIndent()

        val pushReq = Request.Builder()
            .url("https://www.kaggle.com/api/v1/kernels/push")
            .header("Authorization", authHeader())
            .post(pushJson.toRequestBody(jsonMedia))
            .build()

        client.newCall(pushReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Failed to trigger Kaggle T4 x2 worker: HTTP ${resp.code}")
            }
        }
    }

    private fun extractSourceFromPull(rawJson: String): String {
        val root = json.parseToJsonElement(rawJson)
        val blob = (root as? kotlinx.serialization.json.JsonObject)?.get("blob") as? kotlinx.serialization.json.JsonObject
        val source = blob?.get("source") as? kotlinx.serialization.json.JsonPrimitive
        return source?.content ?: throw IOException("Pulled Kaggle kernel missing source script. Sync kaggle_worker via GitHub Actions first.")
    }

    suspend fun pollKernelStatus(): KaggleKernelStatusResponse = withContext(Dispatchers.IO) {
        val owner = vault.kaggleUsername
        val kernelSlug = vault.kaggleKernelSlug.ifBlank { "omniai-wan21-dual-gpu-worker" }
        val req = Request.Builder()
            .url("https://www.kaggle.com/api/v1/kernels/status?userName=$owner&kernelSlug=$kernelSlug")
            .header("Authorization", authHeader())
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Kaggle status poll failed: HTTP ${resp.code}")
            }
            json.decodeFromString<KaggleKernelStatusResponse>(resp.body?.string().orEmpty())
        }
    }

    suspend fun listKernelOutputs(): List<KaggleOutputFileItem> = withContext(Dispatchers.IO) {
        val owner = vault.kaggleUsername
        val kernelSlug = vault.kaggleKernelSlug.ifBlank { "omniai-wan21-dual-gpu-worker" }
        val req = Request.Builder()
            .url("https://www.kaggle.com/api/v1/kernels/output?userName=$owner&kernelSlug=$kernelSlug")
            .header("Authorization", authHeader())
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Failed to list Kaggle output files: HTTP ${resp.code}")
            }
            json.decodeFromString<KaggleKernelOutputResponse>(resp.body?.string().orEmpty()).files
        }
    }

    suspend fun downloadFileWithProgress(
        downloadUrl: String,
        targetFile: File,
        requiresKaggleAuth: Boolean = true,
        onProgress: (percent: Int, downloadedBytes: Long) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        targetFile.parentFile?.mkdirs()
        val reqBuilder = Request.Builder().url(downloadUrl).get()
        if (requiresKaggleAuth && downloadUrl.contains("kaggle.com")) {
            reqBuilder.header("Authorization", authHeader())
        }

        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Download failed with HTTP ${resp.code}")
            }
            val body = resp.body ?: throw IOException("Empty download response body")
            val total = body.contentLength()
            var copied = 0L
            val buffer = ByteArray(16 * 1024)

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    var read: Int
                    var lastReportedPct = -1
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        copied += read
                        val pct = if (total > 0L) ((copied * 100L) / total).toInt().coerceIn(0, 100) else 50
                        if (pct != lastReportedPct) {
                            lastReportedPct = pct
                            onProgress(pct, copied)
                        }
                    }
                    output.flush()
                }
            }
            onProgress(100, copied)
            copied
        }
    }
}
