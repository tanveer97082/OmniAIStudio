package com.omniai.studio.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class LocalServerCandidate(
    val port: Int,
    val baseUrl: String,
    val serviceHint: String,
    val reachable: Boolean,
    val detectedModel: String = "",
    val latencyMs: Long = 0L,
    val statusCode: Int = 0
)

/**
 * Probes standard on-device Termux / llama.cpp / Ollama / vLLM / LocalAI loopback ports
 * (`8080`, `11434`, `8001`, `5000`) on `127.0.0.1` with a strict 1-second timeout.
 */
object LocalhostPortScanner {

    private val TARGET_PORTS = listOf(
        8080 to "llama.cpp / LocalAI",
        11434 to "Ollama OpenAI API",
        8001 to "vLLM / FastChat",
        5000 to "KoboldCpp / TextGen"
    )

    private val probeClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1_000, TimeUnit.MILLISECONDS)
        .readTimeout(1_000, TimeUnit.MILLISECONDS)
        .writeTimeout(1_000, TimeUnit.MILLISECONDS)
        .callTimeout(1_000, TimeUnit.MILLISECONDS)
        .build()

    suspend fun scanCommonPorts(): List<LocalServerCandidate> = withContext(Dispatchers.IO) {
        coroutineScope {
            TARGET_PORTS.map { (port, hint) ->
                async { probePort(port, hint) }
            }.awaitAll()
        }
    }

    private fun probePort(port: Int, hint: String): LocalServerCandidate {
        val baseUrl = "http://127.0.0.1:$port"
        val modelsUrl = "$baseUrl/v1/models"
        val startMs = System.currentTimeMillis()
        return try {
            val request = Request.Builder().url(modelsUrl).get().build()
            probeClient.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - startMs
                val body = response.body?.string().orEmpty()
                val detectedModel = extractFirstModelId(body)
                LocalServerCandidate(
                    port = port,
                    baseUrl = baseUrl,
                    serviceHint = hint,
                    reachable = response.isSuccessful,
                    detectedModel = detectedModel,
                    latencyMs = elapsed,
                    statusCode = response.code
                )
            }
        } catch (_: Exception) {
            LocalServerCandidate(
                port = port,
                baseUrl = baseUrl,
                serviceHint = hint,
                reachable = false,
                latencyMs = System.currentTimeMillis() - startMs,
                statusCode = 0
            )
        }
    }

    private fun extractFirstModelId(rawJson: String): String {
        val regex = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
        return regex.find(rawJson)?.groupValues?.getOrNull(1).orEmpty()
    }
}
