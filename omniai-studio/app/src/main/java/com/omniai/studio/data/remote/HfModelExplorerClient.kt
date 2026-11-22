package com.omniai.studio.data.remote

import com.omniai.studio.terminal.NetworkLogInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class HfModelSummary(
    val id: String = "",
    val downloads: Long = 0L,
    val likes: Long = 0L,
    @SerialName("pipeline_tag") val pipelineTag: String = "",
    val tags: List<String> = emptyList()
) {
    val repoUrl: String
        get() = "https://huggingface.co/$id"

    val isGguf: Boolean
        get() = tags.any { it.equals("gguf", ignoreCase = true) } || id.contains("gguf", ignoreCase = true)
}

/**
 * Built-in Hugging Face Model Explorer using OkHttp3 + Kotlinx Serialization.
 * Queries GET https://huggingface.co/api/models?search={query}&sort=downloads&direction=-1&limit=20
 */
class HfModelExplorerClient {

    private val client = OkHttpClient.Builder()
        .addInterceptor(NetworkLogInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchModels(
        query: String,
        hfToken: String = ""
    ): Result<List<HfModelSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val urlBuilder = "https://huggingface.co/api/models".toHttpUrl().newBuilder()
                .addQueryParameter("sort", "downloads")
                .addQueryParameter("direction", "-1")
                .addQueryParameter("limit", "20")

            val trimmedQuery = query.trim()
            if (trimmedQuery.isNotEmpty()) {
                urlBuilder.addQueryParameter("search", trimmedQuery)
            }

            val reqBuilder = Request.Builder()
                .url(urlBuilder.build())
                .header("Accept", "application/json")
                .header("User-Agent", "OmniAI-Studio-Android")
                .get()

            if (hfToken.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${hfToken.trim()}")
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("Hugging Face API returned HTTP ${resp.code}")
                }
                val raw = resp.body?.string().orEmpty()
                json.decodeFromString<List<HfModelSummary>>(raw)
                    .filter { it.id.isNotBlank() }
            }
        }
    }
}
