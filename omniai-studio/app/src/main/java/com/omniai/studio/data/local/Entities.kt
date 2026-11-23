package com.omniai.studio.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "workspaces")
data class WorkspaceProfileEntity(
    @PrimaryKey val id: String, // e.g. "default", "creator", "research", or custom slug
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_threads",
    indices = [
        Index("workspaceId"),
        Index("nicheId"),
        Index("folderName"),
        Index("isPinned"),
        Index("isArchived")
    ]
)
data class ChatThreadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val systemPrompt: String,
    val rollingSummary: String = "",
    val summarizedMessageCount: Int = 0,
    val workspaceId: String = "default",
    val nicheId: Long = 1L,
    val folderName: String = "General",
    val tagsCsv: String = "",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val providerUsed: String = "",
    val messageCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("threadId"),
        Index("isBookmarked")
    ]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val threadId: Long,
    val role: String, // "user" | "assistant" | "system"
    val content: String,
    val providerTag: String = "",
    val isBookmarked: Boolean = false,
    val tokenEstimate: Int = 0,
    val modelName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "prompt_presets")
data class PromptPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val systemPrompt: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "prompt_chains")
data class PromptChainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String,
    val stepsJson: String, // JSON array of PromptChainStep
    val outputTarget: String = "CHAT", // "CHAT" | "STORYBOARD"
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "storyboards",
    indices = [Index("workspaceId"), Index("nicheId")]
)
data class StoryboardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val topic: String,
    val aspectRatio: String, // "9:16" or "16:9"
    val stylePreset: String,
    val voice: String,
    val burnSubtitles: Boolean,
    val scenesJson: String,
    val socialMetadataJson: String = "",
    val watermarkText: String = "",
    val bgmUrl: String = "",
    val speedFactor: Float = 1.0f,
    val refImagePath: String = "",
    val seamlessLoop: Boolean = false,
    val workspaceId: String = "default",
    val nicheId: Long = 1L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "render_jobs",
    indices = [Index("nicheId"), Index("seriesEpisodeId")]
)
data class RenderJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val storyboardId: Long,
    val title: String,
    val scenesPayloadJson: String,
    val downloadIndividualScenes: Boolean,
    val status: String, // QUEUED, UPLOADING, TRIGGERING, RENDERING, DOWNLOADING, COMPLETED, FAILED
    val progressPercent: Int = 0,
    val statusMessage: String = "Queued",
    val outputFilePath: String? = null,
    val socialMetadataJson: String = "",
    val refImagePath: String = "",
    val accountLabel: String = "Primary",
    val nicheId: Long = 1L,
    val scriptId: Long? = null,
    val seriesEpisodeId: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val filePath: String,
    val sourceUrl: String,
    val category: String, // "RENDER", "SCENE", "MERGED", "COMPRESSED"
    val status: String, // "DOWNLOADING", "COMPLETED", "FAILED"
    val progressPercent: Int = 0,
    val sizeBytes: Long = 0L,
    val savedToGallery: Boolean = false,
    val socialMetadataJson: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

typealias VideoRecordEntity = DownloadRecordEntity

@Entity(tableName = "custom_video_templates")
data class CustomVideoTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val category: String,
    val description: String,
    val aspectRatio: String,
    val defaultVisualStyle: String,
    val defaultVoice: String,
    val burnSubtitlesDefault: Boolean,
    val scenesJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "character_profiles")
data class CharacterProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val appearancePrompt: String,
    val negativePrompt: String = "",
    val defaultStyle: String = "Cinematic Realism",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "render_logs")
data class RenderLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val jobId: Long,
    val title: String,
    val status: String, // "COMPLETED" | "FAILED"
    val durationSeconds: Long,
    val accountLabel: String = "Primary",
    val completedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "api_metrics")
data class ApiMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val providerName: String,
    val estimatedTokens: Int,
    val latencyMs: Long,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "rss_feeds")
data class RssFeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val feedUrl: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "news_digests")
data class NewsDigestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val summaryText: String,
    val suggestedScriptTopic: String,
    val sourceFeedCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "user_memory_facts",
    indices = [
        Index("workspaceId"),
        Index("nicheId")
    ]
)
data class UserMemoryFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val category: String = "Preference",
    val factText: String,
    val enabled: Boolean = true,
    val workspaceId: String = "default",
    val nicheId: Long = 1L,
    val createdAt: Long = System.currentTimeMillis()
)

typealias AiMemoryEntity = UserMemoryFactEntity

@Entity(tableName = "slash_commands")
data class SlashCommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val commandName: String, // e.g. "/summarize"
    val description: String,
    val templateBody: String, // e.g. "Summarize {{topic}} in {{tone}} tone for {{audience}}"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "offline_dataset_items")
data class OfflineDatasetItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packName: String,
    val tag: String,
    val question: String,
    val answer: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "terminal_snippets")
data class TerminalSnippetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val command: String,
    val useTermuxBridge: Boolean = false,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// =========================================================================
// v3.5 NORMALIZED VIDEO SCRIPT, SCENE, REVISION & YT/IG/FB SOCIAL SEO TABLES
// =========================================================================

@Entity(
    tableName = "video_scripts",
    indices = [
        Index("workspaceId"),
        Index("nicheId"),
        Index("characterProfileId"),
        Index("status")
    ]
)
data class VideoScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workspaceId: String = "default",
    val nicheId: Long = 1L,
    val title: String,
    val topic: String,
    val characterProfileId: Long? = null,
    val aspectRatio: String = "9:16",
    val voiceId: String = "en-US-AriaNeural",
    val styleTag: String = "Cinematic Realism",
    val status: String = "DRAFT", // "DRAFT" | "SCRIPTED" | "RENDERING" | "RENDERED" | "PUBLISHED"
    val linkedVideoPath: String = "",
    val thumbnailPath: String = "",
    val seamlessLoop: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "video_scenes",
    foreignKeys = [
        ForeignKey(
            entity = VideoScriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["scriptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("scriptId"),
        Index("isFavoriteScene")
    ]
)
data class SceneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val scriptId: Long,
    val sceneOrder: Int,
    val visualPrompt: String,
    val narration: String,
    val cameraMovement: String = "Slow Cinematic Push-In",
    val durationSec: Int = 5,
    val renderedClipPath: String = "",
    val isFavoriteScene: Boolean = false
)

@Entity(
    tableName = "script_revisions",
    foreignKeys = [
        ForeignKey(
            entity = VideoScriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["scriptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("scriptId")]
)
data class ScriptRevisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val scriptId: Long,
    val snapshotJson: String,
    val note: String = "Auto-snapshot",
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "social_posts",
    foreignKeys = [
        ForeignKey(
            entity = VideoScriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["scriptId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("scriptId"),
        Index("nicheId"),
        Index("platform"),
        Index("publishStatus")
    ]
)
data class SocialPostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val scriptId: Long? = null, // Nullable so standalone posts also work
    val nicheId: Long = 1L,
    val platform: String, // "YOUTUBE" | "INSTAGRAM" | "FACEBOOK"
    val title: String = "",
    val abTitlesJson: String = "[]", // JSON array of 3 A/B viral title/hook options
    val hookLine: String = "",
    val description: String = "",
    val hashtags: String = "",
    val pinnedComment: String = "",
    val callToAction: String = "",
    val publishStatus: String = "DRAFT", // "DRAFT" | "SCHEDULED" | "POSTED"
    val scheduledAtMillis: Long? = null,
    val publishedUrl: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "hashtag_packs",
    indices = [Index("platform")]
)
data class HashtagPackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packName: String,
    val platform: String, // "YOUTUBE" | "INSTAGRAM" | "FACEBOOK" | "ALL"
    val tagsText: String,
    val useCount: Int = 0
)

/**
 * Atomic Room relation POJO for loading a VideoScript with its ordered scenes,
 * platform-specific YT/IG/FB social posts, and revision snapshots in one transaction.
 */
data class ScriptWithScenesAndSocial(
    @Embedded val script: VideoScriptEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "scriptId"
    )
    val scenes: List<SceneEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "scriptId"
    )
    val socialPosts: List<SocialPostEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "scriptId"
    )
    val revisions: List<ScriptRevisionEntity>
)

// =========================================================================
// v4.0 NICHE CONTEXT ENGINE, AUTO-NEXT CONTINUITY & SERIES PLANNER TABLES
// =========================================================================

@Entity(
    tableName = "niches",
    indices = [
        Index("name"),
        Index("linkedCharacterId"),
        Index("linkedHashtagPackId")
    ]
)
data class NicheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val emoji: String = "🎬",
    val description: String = "",
    val systemPromptRules: String = "",
    val visualStylePreset: String = "Cinematic Realism",
    val defaultVoiceId: String = "English Male",
    val defaultAspectRatio: String = "9:16",
    val linkedCharacterId: Long? = null,
    val linkedHashtagPackId: Long? = null,
    val linkedYoutubeProfileId: String = "",
    val rollingChatSummary: String = "",
    val lastSummarizedMessageCount: Int = 0,
    val episodeCount: Int = 0,
    val autoNextEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "next_commands",
    foreignKeys = [
        ForeignKey(
            entity = NicheEntity::class,
            parentColumns = ["id"],
            childColumns = ["nicheId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("nicheId"),
        Index("sourceScriptId"),
        Index("sourceType"),
        Index("isUsed")
    ]
)
data class NextCommandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val nicheId: Long,
    val sourceScriptId: Long? = null,
    val sourceType: String = "POST_VIDEO_SEQUEL", // "POST_VIDEO_SEQUEL" | "CHAT_FOLLOWUP" | "DEEP_DIVE" | "VIRAL_TWIST"
    val shortLabel: String,
    val fullPromptCommand: String,
    val suggestedEpisodeNumber: Int = 1,
    val isUsed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "series_episodes",
    foreignKeys = [
        ForeignKey(
            entity = NicheEntity::class,
            parentColumns = ["id"],
            childColumns = ["nicheId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("nicheId"),
        Index("status"),
        Index("episodeNumber")
    ]
)
data class SeriesEpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val nicheId: Long,
    val episodeNumber: Int,
    val title: String,
    val hookIdea: String,
    val continuityNote: String = "",
    val status: String = "PLANNED", // "PLANNED" | "SCRIPTED" | "RENDERED"
    val linkedScriptId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// =========================================================================
// v4.5 INBUILT PROJECT MANAGEMENT SYSTEM & ATOMIC GIT AUTO-PUSH ENTITIES
// =========================================================================

@Entity(
    tableName = "projects",
    indices = [
        Index(value = ["slug"], unique = true),
        Index("linkedNicheId"),
        Index("status"),
        Index("updatedAt")
    ]
)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val slug: String,
    val description: String = "",
    val projectType: String = "ANDROID_APP", // "ANDROID_APP" | "VIDEO_SERIES" | "AI_TOOL" | "WEB_APP" | "GENERAL"
    val linkedNicheId: Long? = null,
    val githubOwner: String = "",
    val githubRepo: String = "",
    val githubBranch: String = "main",
    val status: String = "ACTIVE", // "PLANNING" | "ACTIVE" | "TESTING" | "COMPLETED" | "ARCHIVED"
    val autoPushOnSave: Boolean = false,
    val lastCommitSha: String = "",
    val lastPushedAt: Long = 0L,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "project_tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("projectId"),
        Index("columnStatus"),
        Index("priority"),
        Index("linkedScriptId")
    ]
)
data class ProjectTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val projectId: Long,
    val title: String,
    val details: String = "",
    val priority: String = "MEDIUM", // "LOW" | "MEDIUM" | "HIGH" | "URGENT"
    val columnStatus: String = "TO_DO", // "TO_DO" | "IN_PROGRESS" | "REVIEW" | "DONE"
    val dueDateMillis: Long? = null,
    val linkedScriptId: Long? = null,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "project_file_indexes",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("projectId"),
        Index(value = ["projectId", "relativePath"], unique = true)
    ]
)
data class ProjectFileIndexEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val projectId: Long,
    val relativePath: String,
    val syncedSha256: String,
    val lastModifiedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "git_commit_logs",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("projectId"),
        Index("timestamp")
    ]
)
data class GitCommitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val projectId: Long,
    val commitSha: String = "",
    val commitMessage: String,
    val branch: String = "main",
    val filesChanged: Int = 0,
    val status: String = "SUCCESS", // "SUCCESS" | "FAILED"
    val errorLog: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// =========================================================================
// v5.3 YOUTUBE DATA API v3 DAILY QUOTA TRACKING
// =========================================================================

@Entity(
    tableName = "youtube_quota_logs",
    indices = [
        Index("dateKey"),
        Index("createdAt")
    ]
)
data class YouTubeQuotaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateKey: String, // yyyy-MM-dd (UTC / local quota window)
    val unitsUsed: Int,
    val actionLabel: String, // e.g. "VIDEO_UPLOAD", "THUMBNAIL_SET", "COMMENT_POST", "ANALYTICS_READ"
    val videoId: String = "",
    val channelLabel: String = "",
    val createdAt: Long = System.currentTimeMillis()
)


