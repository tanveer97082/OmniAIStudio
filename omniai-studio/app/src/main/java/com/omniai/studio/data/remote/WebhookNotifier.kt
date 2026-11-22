package com.omniai.studio.data.remote

import com.omniai.studio.security.NetworkSecurityValidator
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
import java.util.concurrent.TimeUnit

@Serializable
private data class CustomWebhookPayload(
    val event: String,
    val title: String,
    val message: String,
    val content: String, // Discord webhook compatibility
    val text: String,    // Slack / n8n webhook compatibility
    val timestampEpochMs: Long
)

/**
 * Sends JSON POST notifications to a user-configured CUSTOM_WEBHOOK_URL
 * (compatible with Discord, Slack, and n8n webhooks) for render completions/failures
 * and human-in-the-loop daily RSS AI news briefs.
 */
class WebhookNotifier(
    private val vault: VaultManager
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { encodeDefaults = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendEvent(
        event: String,
        title: String,
        message: String,
        overrideUrl: String = vault.customWebhookUrl
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val rawUrl = overrideUrl.trim()
            if (rawUrl.isBlank()) {
                return@runCatching "Webhook URL not configured."
            }
            val validation = NetworkSecurityValidator.validateEndpointUrl(rawUrl)
            require(validation.isValid) {
                validation.errorMessage ?: "Invalid CUSTOM_WEBHOOK_URL (must be HTTPS or localhost)."
            }

            val formattedLine = "**[$event] $title**\n$message"
            val bodyJson = json.encodeToString(
                CustomWebhookPayload(
                    event = event,
                    title = title,
                    message = message,
                    content = formattedLine,
                    text = "[$event] $title — $message",
                    timestampEpochMs = System.currentTimeMillis()
                )
            )

            val req = Request.Builder()
                .url(validation.normalizedUrl)
                .header("Content-Type", "application/json")
                .header("User-Agent", "OmniAI-Studio-Android")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("Webhook returned HTTP ${resp.code}")
                }
            }
            "Webhook delivered ($event)."
        }
    }
}
