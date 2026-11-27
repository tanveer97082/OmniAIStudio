package com.omniai.studio.cloud

import android.content.Context
import com.omniai.studio.data.remote.KaggleAccountCredential
import com.omniai.studio.data.remote.CompatibleAiProviderConfig
import com.omniai.studio.data.remote.PlatformApiCredential
import kotlinx.serialization.Serializable

@Serializable
data class UserApiCredentials(
    val primaryTunnelUrl: String = "",
    val primaryTunnelApiKey: String = "",
    val primaryModelName: String = "dolphin-mistral",
    val huggingFaceToken: String = "",
    val huggingFaceModel: String = "Qwen/Qwen2.5-72B-Instruct",
    val geminiApiKey: String = "",
    val autoFallbackEnabled: Boolean = true,
    val compatibleAiProviders: List<CompatibleAiProviderConfig> = emptyList(),
    val platformApiCredentials: List<PlatformApiCredential> = emptyList(),
    val braveSearchApiKey: String = "",
    val kaggleUsername: String = "",
    val kaggleApiKey: String = "",
    val kaggleDatasetSlug: String = "video-tasks-input",
    val kaggleKernelSlug: String = "omniai-wan21-dual-gpu-worker",
    val kaggleAccountPool: List<KaggleAccountCredential> = emptyList(),
    val activeKaggleAccountId: String = "",
    val githubRepo: String = "",
    val githubToken: String = "",
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
    val telegramApiId: String = "",
    val telegramApiHash: String = "",
    val customWebhookUrl: String = "",
    val ytClientId: String = "",
    val ytClientSecret: String = "",
    val ytRefreshToken: String = "",
    val ytChannelLabel: String = "Primary Channel",
    val ytProfilesJson: String = "[]",
    val activeYtProfileId: String = "",
    val metaPageId: String = "",
    val metaIgUserId: String = "",
    val metaPageAccessToken: String = ""
)

object SupabaseSchemaScript {
    fun load(context: Context): String =
        context.assets.open("schema_and_rls.sql").bufferedReader().use { it.readText() }
}
