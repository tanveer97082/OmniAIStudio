package com.omniai.studio.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAiChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    val stream: Boolean = true,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
data class OpenAiStreamDelta(
    val content: String? = null
)

@Serializable
data class OpenAiStreamChoice(
    val delta: OpenAiStreamDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class OpenAiStreamChunk(
    val choices: List<OpenAiStreamChoice> = emptyList()
)

@Serializable
data class GeminiPart(
    val text: String = ""
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart> = emptyList()
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 2048
)

@Serializable
data class GeminiGenerateRequest(
    @SerialName("system_instruction") val systemInstruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)

@Serializable
data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate> = emptyList()
)

@Serializable
data class SocialMetadata(
    val viralTitle: String = "",
    val caption: String = "",
    val hashtags: List<String> = emptyList(),
    val pinnedComment: String = ""
)

@Serializable
data class StoryboardScene(
    @SerialName("scene_index") val sceneIndex: Int,
    @SerialName("visual_prompt") val visualPrompt: String,
    val narration: String,
    @SerialName("duration_sec") val durationSec: Int = 5
)

@Serializable
data class StoryboardPayload(
    val title: String,
    @SerialName("aspect_ratio") val aspectRatio: String,
    val voice: String,
    @SerialName("burn_subtitles") val burnSubtitles: Boolean,
    val scenes: List<StoryboardScene>,
    @SerialName("negative_prompt") val negativePrompt: String = "",
    @SerialName("watermark_text") val watermarkText: String = "",
    @SerialName("bgm_url") val bgmUrl: String = "",
    @SerialName("speed_factor") val speedFactor: Float = 1.0f,
    @SerialName("use_ref_image") val useRefImage: Boolean = false,
    @SerialName("seamless_loop") val seamlessLoop: Boolean = false,
    @SerialName("social_metadata") val socialMetadata: SocialMetadata = SocialMetadata()
)

@Serializable
data class KaggleAccountCredential(
    val id: String,
    val label: String,
    val username: String,
    val apiKey: String,
    val datasetSlug: String = "video-tasks-input",
    val kernelSlug: String = "omniai-wan21-dual-gpu-worker"
)

@Serializable
data class KaggleUploadBlobRequest(
    val type: String = "dataset",
    val name: String = "scenes.json",
    val contentLength: Long,
    val lastModifiedEpochSeconds: Long
)

@Serializable
data class KaggleUploadBlobResponse(
    val token: String = "",
    val createUrl: String = ""
)

@Serializable
data class KaggleDatasetVersionFile(
    val token: String
)

@Serializable
data class KaggleDatasetVersionRequest(
    val versionNotes: String,
    val files: List<KaggleDatasetVersionFile>,
    val deleteOldVersions: Boolean = false
)

@Serializable
data class KaggleKernelStatusResponse(
    val status: String = "unknown",
    val failureMessage: String? = null
)

@Serializable
data class KaggleOutputFileItem(
    val fileName: String = "",
    val url: String = "",
    val totalBytes: Long = 0L
)

@Serializable
data class KaggleKernelOutputResponse(
    val files: List<KaggleOutputFileItem> = emptyList()
)

@Serializable
data class GitHubReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val url: String,
    val size: Long = 0L
)

@Serializable
data class GitHubLatestRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String = "",
    val body: String = "",
    val assets: List<GitHubReleaseAsset> = emptyList()
)

@Serializable
data class VaultSnapshot(
    val primaryTunnelUrl: String = "",
    val primaryTunnelApiKey: String = "",
    val primaryModelName: String = "dolphin-mistral",
    val huggingFaceToken: String = "",
    val huggingFaceModel: String = "Qwen/Qwen2.5-72B-Instruct",
    val geminiApiKey: String = "",
    val autoFallbackEnabled: Boolean = true,
    val kaggleUsername: String = "",
    val kaggleApiKey: String = "",
    val kaggleDatasetSlug: String = "video-tasks-input",
    val kaggleKernelSlug: String = "omniai-wan21-dual-gpu-worker",
    val githubRepo: String = "",
    val githubToken: String = "",
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
    val customWebhookUrl: String = "",
    val appLockEnabled: Boolean = false,
    val vaultLockEnabled: Boolean = true,
    val flagSecureEnabled: Boolean = true,
    val autoLockTimeout: String = "ONE_MINUTE"
)

@Serializable
data class PromptChainStep(
    val stepIndex: Int,
    val title: String,
    val systemPrompt: String = "You are OmniAI, executing one stage of a structured multi-step workflow.",
    val promptTemplate: String // Supports {{input}} and {{prev_output}}
)

@Serializable
data class PresetBackupItem(
    val name: String,
    val systemPrompt: String
)

@Serializable
data class StoryboardBackupItem(
    val title: String,
    val topic: String,
    val aspectRatio: String,
    val stylePreset: String,
    val voice: String,
    val burnSubtitles: Boolean,
    val scenesJson: String
)

@Serializable
data class EncryptedBackupBundle(
    val exportedAtEpochMs: Long,
    val vault: VaultSnapshot,
    val presets: List<PresetBackupItem>,
    val storyboards: List<StoryboardBackupItem>
)

data class EndpointPingResult(
    val name: String,
    val reachable: Boolean,
    val latencyMs: Long,
    val statusCode: Int,
    val detail: String
)

@Serializable
data class PlatformSocialSeoItem(
    val title: String = "",
    val abTitles: List<String> = emptyList(),
    val hookLine: String = "",
    val description: String = "",
    val hashtags: String = "",
    val pinnedComment: String = "",
    val callToAction: String = ""
)

@Serializable
data class MultiPlatformSocialSeoPayload(
    val youtube: PlatformSocialSeoItem = PlatformSocialSeoItem(),
    val instagram: PlatformSocialSeoItem = PlatformSocialSeoItem(),
    val facebook: PlatformSocialSeoItem = PlatformSocialSeoItem()
)

// =========================================================================
// v4.0 NICHE BLUEPRINT, CONTINUITY COMMANDS, SERIES PLANNER & SCRIPT AUDIT
// =========================================================================

@Serializable
data class NicheBlueprintDto(
    val emoji: String = "🎬",
    val description: String = "",
    val systemPromptRules: String = "",
    val visualStylePreset: String = "Cinematic Realism",
    val defaultVoiceId: String = "English Male",
    val defaultAspectRatio: String = "9:16"
)

@Serializable
data class NextCommandSuggestionDto(
    val sourceType: String = "POST_VIDEO_SEQUEL",
    val shortLabel: String = "",
    val fullPromptCommand: String = "",
    val suggestedEpisodeNumber: Int = 1
)

@Serializable
data class NextCommandListDto(
    val commands: List<NextCommandSuggestionDto> = emptyList()
)

@Serializable
data class SeriesEpisodeDto(
    val episodeNumber: Int = 1,
    val title: String = "",
    val hookIdea: String = "",
    val continuityNote: String = ""
)

@Serializable
data class SeriesPlanDto(
    val episodes: List<SeriesEpisodeDto> = emptyList()
)

@Serializable
data class ScriptAuditResult(
    val hookScore: Int = 7,
    val pacingScore: Int = 7,
    val visualClarityScore: Int = 7,
    val loopScore: Int = 7,
    val critiques: List<String> = emptyList(),
    val weakSceneIndices: List<Int> = emptyList(),
    val summaryVerdict: String = ""
)

@Serializable
data class OptimizedScenesDto(
    val scenes: List<StoryboardScene> = emptyList()
)

// =========================================================================
// v4.5 PROJECT MANAGEMENT, ATOMIC GIT DATA API & GITHUB ACTIONS WATCHER
// =========================================================================

@Serializable
data class AiPlannedTaskDto(
    val title: String = "",
    val details: String = "",
    val priority: String = "MEDIUM",
    val columnStatus: String = "TO_DO"
)

@Serializable
data class AiTaskPlanPayload(
    val tasks: List<AiPlannedTaskDto> = emptyList()
)

data class GitDiffResult(
    val added: List<String> = emptyList(),
    val modified: List<String> = emptyList(),
    val deleted: List<String> = emptyList(),
    val synced: List<String> = emptyList()
) {
    val totalPending: Int get() = added.size + modified.size + deleted.size
    val hasChanges: Boolean get() = totalPending > 0
}

@Serializable
data class GitHubRefObject(
    val sha: String = "",
    val type: String = ""
)

@Serializable
data class GitHubRefResponse(
    val ref: String = "",
    @SerialName("object") val obj: GitHubRefObject = GitHubRefObject()
)

@Serializable
data class GitHubCommitTreeRef(
    val sha: String = ""
)

@Serializable
data class GitHubCommitResponse(
    val sha: String = "",
    val tree: GitHubCommitTreeRef = GitHubCommitTreeRef()
)

@Serializable
data class GitHubBlobCreateResponse(
    val sha: String = ""
)

@Serializable
data class GitHubBlobContentResponse(
    val sha: String = "",
    val size: Long = 0L,
    val content: String = "",
    val encoding: String = "base64"
)

@Serializable
data class GitHubTreeEntryDto(
    val path: String = "",
    val mode: String = "100644",
    val type: String = "blob",
    val sha: String? = null,
    val size: Long = 0L
)

@Serializable
data class GitHubTreeResponse(
    val sha: String = "",
    val tree: List<GitHubTreeEntryDto> = emptyList(),
    val truncated: Boolean = false
)

@Serializable
data class GitHubCreateRepoResponse(
    val id: Long = 0L,
    val name: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
data class GitHubActionsRunDto(
    val id: Long = 0L,
    val name: String = "",
    @SerialName("display_title") val displayTitle: String = "",
    val status: String = "", // "queued" | "in_progress" | "completed"
    val conclusion: String? = null, // "success" | "failure" | "cancelled"
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("head_branch") val headBranch: String = "",
    @SerialName("head_sha") val headSha: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("run_number") val runNumber: Int = 0
)

@Serializable
data class GitHubWorkflowRunsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<GitHubActionsRunDto> = emptyList()
)

@Serializable
data class GitHubActionStepDto(
    val name: String = "",
    val status: String = "",
    val conclusion: String? = null,
    val number: Int = 0
)

@Serializable
data class GitHubActionJobDto(
    val id: Long = 0L,
    val name: String = "",
    val status: String = "",
    val conclusion: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val steps: List<GitHubActionStepDto> = emptyList()
)

@Serializable
data class GitHubActionJobsResponse(
    val jobs: List<GitHubActionJobDto> = emptyList()
)

data class ActionsMonitorState(
    val isWatching: Boolean = false,
    val runId: Long = 0L,
    val runNumber: Int = 0,
    val workflowName: String = "",
    val status: String = "IDLE", // "IDLE" | "QUEUED" | "IN_PROGRESS" | "SUCCESS" | "FAILURE" | "CANCELLED"
    val conclusion: String = "",
    val htmlUrl: String = "",
    val elapsedSec: Long = 0L,
    val failureSummary: String = "",
    val releaseApkAsset: GitHubReleaseAsset? = null,
    val releaseTagName: String = ""
)


