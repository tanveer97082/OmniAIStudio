package com.omniai.studio.backup

import com.omniai.studio.security.VaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
private data class TelegramBotUser(
    val username: String = "",
    val first_name: String = ""
)

@Serializable
private data class TelegramGetMeResponse(
    val ok: Boolean = false,
    val result: TelegramBotUser? = null,
    val description: String? = null
)

@Serializable
private data class TelegramChatInfo(
    val id: Long = 0L,
    val title: String? = null,
    val username: String? = null,
    val first_name: String? = null,
    val type: String = ""
)

@Serializable
private data class TelegramUpdateMessage(
    val chat: TelegramChatInfo? = null
)

@Serializable
private data class TelegramUpdateItem(
    val update_id: Long = 0L,
    val message: TelegramUpdateMessage? = null,
    val channel_post: TelegramUpdateMessage? = null,
    val edited_message: TelegramUpdateMessage? = null,
    val my_chat_member: TelegramUpdateMessage? = null
)

@Serializable
private data class TelegramGetUpdatesResponse(
    val ok: Boolean = false,
    val result: List<TelegramUpdateItem> = emptyList(),
    val description: String? = null
)

/**
 * Layer 3: Telegram Bot Cloud Archive & Render Alert Client.
 * Uses OkHttp3 Multipart POST to send encrypted `.omniai.enc` snapshots,
 * auto-archive rendered `final_video.mp4` files (<= 49 MB), and send render alerts.
 */
class TelegramBackupClient(
    private val vault: VaultManager
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun testBotConnection(
        overrideToken: String = vault.telegramBotToken,
        overrideChatId: String = vault.telegramChatId
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = overrideToken.trim()
            val chatId = overrideChatId.trim()
            require(token.isNotBlank()) { "Enter TG_BOT_TOKEN first." }
            require(chatId.isNotBlank()) { "Enter TG_CHAT_ID first." }

            val meReq = Request.Builder()
                .url("https://api.telegram.org/bot$token/getMe")
                .get()
                .build()

            val botHandle = client.newCall(meReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IOException("Telegram /getMe HTTP ${resp.code}: ${raw.take(140)}")
                val parsed = json.decodeFromString<TelegramGetMeResponse>(raw)
                if (!parsed.ok) throw IOException(parsed.description ?: "Telegram bot token rejected.")
                parsed.result?.username?.let { "@$it" } ?: parsed.result?.first_name ?: "OmniAI Bot"
            }

            sendStatusMessage(
                text = "OmniAI-Studio v5.2 connected ($botHandle). Encrypted backup, 2 GB Kaggle MTProto uploader & render alerts active.",
                overrideToken = token,
                overrideChatId = chatId
            ).getOrThrow()

            "Connected to Telegram $botHandle and delivered test ping."
        }
    }

    /**
     * v5.2: 1-Tap Auto-Detect Chat ID via Telegram Bot API /getUpdates.
     * Scans recent messages, channel posts, edited messages, and bot membership updates
     * so users never need to manually inspect JSON responses.
     */
    suspend fun autoDetectChatId(
        overrideToken: String = vault.telegramBotToken
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = overrideToken.trim()
            require(token.isNotBlank()) { "Enter TG_BOT_TOKEN first before detecting Chat ID." }

            val req = Request.Builder()
                .url("https://api.telegram.org/bot$token/getUpdates?limit=25")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val safeRaw = raw.replace(token, "***").take(140)
                    throw IOException("Telegram /getUpdates HTTP ${resp.code}: $safeRaw")
                }
                val parsed = json.decodeFromString<TelegramGetUpdatesResponse>(raw)
                if (!parsed.ok) {
                    val safeDesc = (parsed.description ?: "Telegram /getUpdates rejected.").replace(token, "***")
                    throw IOException(safeDesc)
                }
                val latestChat = parsed.result
                    .sortedByDescending { it.update_id }
                    .firstNotNullOfOrNull { item ->
                        item.message?.chat
                            ?: item.channel_post?.chat
                            ?: item.edited_message?.chat
                            ?: item.my_chat_member?.chat
                    }
                    ?: throw IllegalStateException(
                        "No recent messages found. Open your bot in Telegram, tap Start or send 'hi' (or post in your channel where the bot is admin), then tap Auto-Detect again."
                    )

                val chatId = latestChat.id.toString()
                val label = latestChat.title?.takeIf { it.isNotBlank() }
                    ?: latestChat.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
                    ?: latestChat.first_name?.takeIf { it.isNotBlank() }
                    ?: latestChat.type.ifBlank { "Telegram Chat" }
                chatId to label
            }
        }
    }

    suspend fun sendEncryptedBackupDocument(
        encryptedContainerJson: String,
        caption: String = "OmniAI-Studio AES-256-GCM Encrypted Snapshot"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = vault.telegramBotToken.trim()
            val chatId = vault.telegramChatId.trim()
            require(token.isNotBlank() && chatId.isNotBlank()) {
                "Configure TG_BOT_TOKEN and TG_CHAT_ID in Backup & Sync Center first."
            }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val fileName = "OmniAI_Snapshot_$stamp.omniai.enc"
            val bytes = encryptedContainerJson.toByteArray(Charsets.UTF_8)

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", caption)
                .addFormDataPart(
                    "document",
                    fileName,
                    bytes.toRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            val req = Request.Builder()
                .url("https://api.telegram.org/bot$token/sendDocument")
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string()?.take(180).orEmpty()
                    throw IOException("Telegram sendDocument failed (HTTP ${resp.code}): $err")
                }
            }
            "Sent encrypted snapshot '$fileName' (${bytes.size / 1024} KB) to Telegram."
        }
    }

    suspend fun uploadRenderedVideoIfEligible(
        videoFile: File,
        caption: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = vault.telegramBotToken.trim()
            val chatId = vault.telegramChatId.trim()
            require(token.isNotBlank() && chatId.isNotBlank()) {
                "Telegram Bot credentials not configured."
            }
            require(videoFile.exists()) { "Rendered video file does not exist." }
            val sizeBytes = videoFile.length()
            if (sizeBytes > MAX_TELEGRAM_VIDEO_BYTES) {
                throw IllegalStateException("Video exceeds 49 MB Telegram Bot upload limit (${sizeBytes / (1024 * 1024)} MB).")
            }

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", caption.take(900))
                .addFormDataPart(
                    "video",
                    videoFile.name,
                    videoFile.asRequestBody("video/mp4".toMediaType())
                )
                .build()

            val req = Request.Builder()
                .url("https://api.telegram.org/bot$token/sendVideo")
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string()?.take(180).orEmpty()
                    throw IOException("Telegram sendVideo failed (HTTP ${resp.code}): $err")
                }
            }
            "Archived '${videoFile.name}' (${sizeBytes / (1024 * 1024)} MB) to Telegram."
        }
    }

    suspend fun sendStatusMessage(
        text: String,
        overrideToken: String = vault.telegramBotToken,
        overrideChatId: String = vault.telegramChatId
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = overrideToken.trim()
            val chatId = overrideChatId.trim()
            if (token.isBlank() || chatId.isBlank()) return@runCatching

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("text", text.take(3800))
                .build()

            val req = Request.Builder()
                .url("https://api.telegram.org/bot$token/sendMessage")
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("Telegram sendMessage HTTP ${resp.code}")
                }
            }
        }
    }

    companion object {
        const val MAX_TELEGRAM_VIDEO_BYTES = 49L * 1024L * 1024L
    }
}
