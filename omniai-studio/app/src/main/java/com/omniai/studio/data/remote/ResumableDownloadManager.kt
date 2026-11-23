package com.omniai.studio.data.remote

import com.omniai.studio.terminal.NetworkLogInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ResumableDownloadResult(
    val outputFile: File,
    val totalBytes: Long,
    val resumedFromBytes: Long,
    val attempts: Int
)

/**
 * v5.0 Resumable Download Manager:
 * Pure OkHttp3 downloader supporting HTTP `Range: bytes=<offset>-` partial resume, `.part` atomic
 * staging files, canonical destination confinement, and bounded exponential backoff retries.
 */
class ResumableDownloadManager {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor(NetworkLogInterceptor)
        .build()

    suspend fun downloadResumable(
        url: String,
        targetFile: File,
        allowedBaseDir: File? = null,
        headers: Map<String, String> = emptyMap(),
        maxRetries: Int = 3,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _, _ -> }
    ): Result<ResumableDownloadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = url.trim()
            require(cleanUrl.startsWith("https://") || cleanUrl.startsWith("http://")) {
                "Download URL must start with https:// or http://"
            }

            val canonicalTarget = targetFile.canonicalFile
            if (allowedBaseDir != null) {
                val canonicalBase = allowedBaseDir.canonicalFile
                require(canonicalTarget.path.startsWith(canonicalBase.path + File.separator)) {
                    "Blocked destination outside allowed storage directory."
                }
            }
            canonicalTarget.parentFile?.mkdirs()

            val partFile = File(canonicalTarget.parentFile, "${canonicalTarget.name}.part")
            var attempt = 0
            var initialResumeOffset = if (partFile.exists()) partFile.length() else 0L
            var lastError: Throwable? = null

            while (attempt < maxRetries.coerceIn(1, 5)) {
                attempt++
                try {
                    val existingBytes = if (partFile.exists()) partFile.length() else 0L
                    if (attempt == 1) initialResumeOffset = existingBytes

                    val reqBuilder = Request.Builder().url(cleanUrl).get()
                    headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                    if (existingBytes > 0L) {
                        reqBuilder.header("Range", "bytes=$existingBytes-")
                    }

                    client.newCall(reqBuilder.build()).execute().use { resp ->
                        if (resp.code == 416 && partFile.exists() && partFile.length() > 0L) {
                            // Range already satisfied — promote .part file directly
                            promotePartFile(partFile, canonicalTarget)
                            val finalSize = canonicalTarget.length()
                            onProgress(100, finalSize, finalSize)
                            return@runCatching ResumableDownloadResult(
                                outputFile = canonicalTarget,
                                totalBytes = finalSize,
                                resumedFromBytes = initialResumeOffset,
                                attempts = attempt
                            )
                        }

                        if (!resp.isSuccessful) {
                            throw IOException("Download failed with HTTP ${resp.code} ${resp.message}")
                        }

                        val body = resp.body ?: throw IOException("Empty response body for $cleanUrl")
                        val isPartial = resp.code == 206 && existingBytes > 0L
                        val appendMode = isPartial
                        val startOffset = if (isPartial) existingBytes else 0L
                        val contentLen = body.contentLength()
                        val expectedTotal = if (contentLen > 0L) startOffset + contentLen else -1L

                        var writtenTotal = startOffset
                        body.byteStream().use { input ->
                            FileOutputStream(partFile, appendMode).use { output ->
                                val buffer = ByteArray(16 * 1024)
                                var read: Int
                                var lastReportedPct = -1
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    writtenTotal += read
                                    val pct = if (expectedTotal > 0L) {
                                        ((writtenTotal * 100L) / expectedTotal).toInt().coerceIn(0, 100)
                                    } else {
                                        -1
                                    }
                                    if (pct != lastReportedPct) {
                                        lastReportedPct = pct
                                        onProgress(pct.coerceAtLeast(0), writtenTotal, expectedTotal)
                                    }
                                }
                                output.flush()
                            }
                        }

                        promotePartFile(partFile, canonicalTarget)
                        val finalSize = canonicalTarget.length()
                        onProgress(100, finalSize, finalSize)
                        return@runCatching ResumableDownloadResult(
                            outputFile = canonicalTarget,
                            totalBytes = finalSize,
                            resumedFromBytes = initialResumeOffset,
                            attempts = attempt
                        )
                    }
                } catch (ex: Throwable) {
                    lastError = ex
                    if (attempt < maxRetries) {
                        delay(1_200L * attempt)
                    }
                }
            }

            throw lastError ?: IOException("Resumable download failed after $attempt attempts.")
        }
    }

    private fun promotePartFile(partFile: File, targetFile: File) {
        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (!partFile.renameTo(targetFile)) {
            partFile.copyTo(targetFile, overwrite = true)
            partFile.delete()
        }
    }
}
