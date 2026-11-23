package com.omniai.studio.terminal

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NetworkLogEntry(
    val timestamp: String,
    val method: String,
    val url: String,
    val statusCode: Int,
    val durationMs: Long,
    val maskedHeaders: String,
    val error: String? = null
) {
    fun toTerminalLine(): String {
        val statusText = if (error != null) "ERR($error)" else "HTTP $statusCode"
        return "[NET $timestamp] $method $url -> $statusText (${durationMs}ms) | $maskedHeaders"
    }
}

/**
 * Shared OkHttp3 Network Inspector Interceptor for `netlog` live streaming in the Hybrid Terminal.
 * Strictly masks Authorization, X-API-Key, X-Goog-Api-Key, Cookie, and URL query key parameters.
 */
object NetworkLogInterceptor : Interceptor {

    private const val MAX_RING_BUFFER = 120
    private val sensitiveHeaderNames = setOf(
        "authorization",
        "apikey",
        "x-api-key",
        "x-goog-api-key",
        "cookie",
        "set-cookie",
        "proxy-authorization"
    )

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _recentLogs = MutableStateFlow<List<NetworkLogEntry>>(emptyList())
    val recentLogs: StateFlow<List<NetworkLogEntry>> = _recentLogs.asStateFlow()

    private val _liveEvents = MutableSharedFlow<NetworkLogEntry>(extraBufferCapacity = 64)
    val liveEvents: SharedFlow<NetworkLogEntry> = _liveEvents.asSharedFlow()

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
    }

    fun toggle(): Boolean {
        val next = !_isEnabled.value
        _isEnabled.value = next
        return next
    }

    fun clearLogs() {
        _recentLogs.value = emptyList()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startNs = System.nanoTime()
        val sanitizedUrl = sanitizeUrl(request.url.toString())
        val maskedHeaders = buildMaskedHeaderSummary(request)

        return try {
            val response = chain.proceed(request)
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)
            recordEntry(
                NetworkLogEntry(
                    timestamp = formatNow(),
                    method = request.method,
                    url = sanitizedUrl,
                    statusCode = response.code,
                    durationMs = elapsedMs,
                    maskedHeaders = maskedHeaders
                )
            )
            response
        } catch (io: IOException) {
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)
            recordEntry(
                NetworkLogEntry(
                    timestamp = formatNow(),
                    method = request.method,
                    url = sanitizedUrl,
                    statusCode = 0,
                    durationMs = elapsedMs,
                    maskedHeaders = maskedHeaders,
                    error = io.javaClass.simpleName
                )
            )
            throw io
        }
    }

    private fun recordEntry(entry: NetworkLogEntry) {
        val updated = (_recentLogs.value + entry).takeLast(MAX_RING_BUFFER)
        _recentLogs.value = updated
        if (_isEnabled.value) {
            _liveEvents.tryEmit(entry)
        }
    }

    private fun buildMaskedHeaderSummary(request: okhttp3.Request): String {
        val headers = request.headers
        if (headers.size == 0) return "headers=none"
        return (0 until headers.size).joinToString(", ") { idx ->
            val name = headers.name(idx)
            val value = headers.value(idx)
            if (name.lowercase(Locale.US) in sensitiveHeaderNames) {
                val scheme = value.substringBefore(" ", "")
                if (scheme.equals("Bearer", ignoreCase = true) || scheme.equals("Basic", ignoreCase = true)) {
                    "$name=$scheme ***MASKED***"
                } else {
                    "$name=***MASKED***"
                }
            } else {
                "$name=${value.take(32)}"
            }
        }
    }

    private fun sanitizeUrl(rawUrl: String): String {
        return rawUrl
            .replace(Regex("([?&](?:key|token|api_key|apikey|access_token)=)[^&]+", RegexOption.IGNORE_CASE), "$1***MASKED***")
            .replace(Regex("(bot)[0-9]+:[A-Za-z0-9_-]+"), "$1***MASKED***")
    }

    private fun formatNow(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    }
}
