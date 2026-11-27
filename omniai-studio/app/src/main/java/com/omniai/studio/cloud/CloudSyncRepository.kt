package com.omniai.studio.cloud

import com.omniai.studio.data.local.ChatMessageEntity
import com.omniai.studio.data.local.ChatThreadEntity
import com.omniai.studio.data.local.HashtagPackEntity
import com.omniai.studio.data.local.NicheEntity
import com.omniai.studio.data.local.OmniAiDatabase
import com.omniai.studio.data.local.ProjectEntity
import com.omniai.studio.data.local.ProjectTaskEntity
import com.omniai.studio.data.local.PromptChainEntity
import com.omniai.studio.data.local.SceneEntity
import com.omniai.studio.data.local.SocialPostEntity
import com.omniai.studio.data.local.UserMemoryFactEntity
import com.omniai.studio.data.local.VideoScriptEntity
import com.omniai.studio.security.VaultManager
import com.omniai.studio.terminal.NetworkLogInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class CloudSyncEnvelopeRow(
    @SerialName("record_type") val recordType: String,
    @SerialName("record_id") val recordId: String,
    @SerialName("niche_id") val nicheId: Long = 1L,
    @SerialName("title_preview") val titlePreview: String = "",
    @SerialName("compressed_payload") val compressedPayload: String,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("updated_by") val updatedBy: String? = null,
    @SerialName("updated_at") val updatedAt: Long
)

@Serializable
private data class SyncChatThreadDto(
    val id: Long,
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
    val updatedAt: Long
)

@Serializable
private data class SyncChatMessageDto(
    val id: Long,
    val threadId: Long,
    val role: String,
    val content: String,
    val providerTag: String = "",
    val isBookmarked: Boolean = false,
    val tokenEstimate: Int = 0,
    val modelName: String = "",
    val createdAt: Long
)

@Serializable
private data class SyncSceneDto(
    val id: Long,
    val scriptId: Long,
    val sceneOrder: Int,
    val visualPrompt: String,
    val narration: String,
    val cameraMovement: String = "Slow Cinematic Push-In",
    val durationSec: Int = 5,
    val isFavoriteScene: Boolean = false
)

@Serializable
private data class SyncVideoScriptBundleDto(
    val id: Long,
    val workspaceId: String = "default",
    val nicheId: Long = 1L,
    val title: String,
    val topic: String,
    val characterProfileId: Long? = null,
    val aspectRatio: String = "9:16",
    val voiceId: String = "en-US-AriaNeural",
    val styleTag: String = "Cinematic Realism",
    val status: String = "DRAFT",
    val seamlessLoop: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val scenes: List<SyncSceneDto> = emptyList()
)

@Serializable
private data class SyncSocialPostDto(
    val id: Long,
    val scriptId: Long? = null,
    val nicheId: Long = 1L,
    val platform: String,
    val title: String = "",
    val abTitlesJson: String = "[]",
    val hookLine: String = "",
    val description: String = "",
    val hashtags: String = "",
    val pinnedComment: String = "",
    val callToAction: String = "",
    val publishStatus: String = "DRAFT",
    val scheduledAtMillis: Long? = null,
    val publishedUrl: String = "",
    val updatedAt: Long
)

@Serializable
private data class SyncNicheDto(
    val id: Long,
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
    val createdAt: Long
)

@Serializable
private data class SyncProjectDto(
    val id: Long,
    val name: String,
    val slug: String,
    val description: String = "",
    val projectType: String = "ANDROID_APP",
    val linkedNicheId: Long? = null,
    val githubOwner: String = "",
    val githubRepo: String = "",
    val githubBranch: String = "main",
    val status: String = "ACTIVE",
    val autoPushOnSave: Boolean = false,
    val lastCommitSha: String = "",
    val lastPushedAt: Long = 0L,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
private data class SyncProjectTaskDto(
    val id: Long,
    val projectId: Long,
    val title: String,
    val details: String = "",
    val priority: String = "MEDIUM",
    val columnStatus: String = "TO_DO",
    val dueDateMillis: Long? = null,
    val linkedScriptId: Long? = null,
    val sortOrder: Int = 0,
    val updatedAt: Long
)

@Serializable
private data class SyncAiMemoryDto(
    val id: Long,
    val category: String = "Preference",
    val factText: String,
    val enabled: Boolean = true,
    val workspaceId: String = "default",
    val nicheId: Long = 1L,
    val createdAt: Long
)

@Serializable
private data class SyncPromptChainDto(
    val id: Long,
    val name: String,
    val description: String,
    val stepsJson: String,
    val outputTarget: String = "CHAT",
    val isBuiltIn: Boolean = false,
    val createdAt: Long
)

@Serializable
private data class SyncHashtagPackDto(
    val id: Long,
    val packName: String,
    val platform: String,
    val tagsText: String,
    val useCount: Int = 0
)

@Serializable
private data class SyncVaultMetaDto(
    val primaryModelName: String,
    val huggingFaceModel: String,
    val kaggleDatasetSlug: String,
    val kaggleKernelSlug: String,
    val githubRepo: String,
    val ytChannelLabel: String,
    val autoFallbackEnabled: Boolean,
    val updatedAt: Long
)

data class CloudSyncPushStats(
    val pushedRows: Int = 0,
    val rawBytes: Int = 0,
    val compressedBytes: Int = 0,
    val savedPercent: Int = 0
)

data class CloudSyncPullStats(
    val pulledRows: Int = 0,
    val appliedRows: Int = 0,
    val deletedRows: Int = 0,
    val latestTimestampMs: Long = 0L
)

data class CloudSyncSummary(
    val pushedRows: Int,
    val pulledRows: Int,
    val deletedRows: Int,
    val rawBytes: Int,
    val compressedBytes: Int,
    val savedPercent: Int,
    val syncedAtEpochMs: Long,
    val message: String
)

data class CloudSyncStatus(
    val isSyncing: Boolean = false,
    val lastSyncedAtMs: Long = 0L,
    val lastSavedPercent: Int = 0,
    val lastSyncedRows: Int = 0,
    val lastMessage: String = "Never synced"
)

/**
 * Offline-First GZIP-Compressed Text-Only Two-Way Delta Sync Engine over Supabase PostgREST.
 * Uses pure OkHttp3 + Kotlinx Serialization + PayloadGzipCodec (zero extra Gradle dependencies).
 */
class CloudSyncRepository(
    private val db: OmniAiDatabase,
    private val vault: VaultManager,
    private val authManager: SupabaseAuthManager,
    private val roleGuard: RolePermissionGuard
) {
    @Serializable
    private data class UserApiCredentialsUpsert(
        @SerialName("user_id") val userId: String,
        val credentials: UserApiCredentials,
        @SerialName("updated_at") val updatedAt: Long
    )

    @Serializable
    private data class UserApiCredentialsResponse(
        val credentials: UserApiCredentials
    )

    companion object {
        private const val BATCH_SIZE = 35
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(NetworkLogInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _syncStatus = MutableStateFlow(
        CloudSyncStatus(
            isSyncing = false,
            lastSyncedAtMs = vault.cloudLastSyncAtMs,
            lastSavedPercent = vault.cloudLastSavedPercent,
            lastSyncedRows = vault.cloudLastSyncedRows,
            lastMessage = if (vault.cloudLastSyncAtMs > 0L) {
                "Synced ${vault.cloudLastSyncedRows} rows · ${vault.cloudLastSavedPercent}% saved"
            } else {
                "Ready for compressed text delta sync"
            }
        )
    )
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    suspend fun pushUserApiCredentials(): Result<Unit> = withContext(Dispatchers.IO) {
        val accessToken = authManager.ensureValidAccessToken().getOrElse {
            return@withContext Result.failure(it)
        }
        val userId = vault.cloudUserId.trim()
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Sign in to Supabase before saving API credentials."))
        }

        try {
            val row = UserApiCredentialsUpsert(
                userId = userId,
                credentials = vault.exportUserApiCredentials(),
                updatedAt = System.currentTimeMillis()
            )
            val request = Request.Builder()
                .url("${authManager.normalizedBaseUrl()}/rest/v1/user_api_credentials?on_conflict=user_id")
                .header("apikey", vault.supabaseAnonKey.trim())
                .header("Authorization", "Bearer $accessToken")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(json.encodeToString(listOf(row)).toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val setupHint = if (response.code == 404) " Run 'supabase schema' and apply its SQL first." else ""
                    return@withContext Result.failure(
                        IllegalStateException("Supabase API credentials save failed (HTTP ${response.code}).$setupHint")
                    )
                }
            }
            Result.success(Unit)
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    suspend fun pullUserApiCredentials(): Result<UserApiCredentials?> = withContext(Dispatchers.IO) {
        val accessToken = authManager.ensureValidAccessToken().getOrElse {
            return@withContext Result.failure(it)
        }
        val userId = vault.cloudUserId.trim()
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Sign in to Supabase before restoring API credentials."))
        }

        try {
            val request = Request.Builder()
                .url("${authManager.normalizedBaseUrl()}/rest/v1/user_api_credentials?select=credentials&user_id=eq.$userId&limit=1")
                .header("apikey", vault.supabaseAnonKey.trim())
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val setupHint = if (response.code == 404) " Run 'supabase schema' and apply its SQL first." else ""
                    return@withContext Result.failure(
                        IllegalStateException("Supabase API credentials restore failed (HTTP ${response.code}).$setupHint")
                    )
                }
                val body = response.body?.string().orEmpty()
                val rows = json.decodeFromString<List<UserApiCredentialsResponse>>(body)
                Result.success(rows.firstOrNull()?.credentials)
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    suspend fun syncNow(onProgress: ((String) -> Unit)? = null): Result<CloudSyncSummary> = withContext(Dispatchers.IO) {
        val caps = roleGuard.currentCapabilities
        if (caps.isOfflineLocal) {
            return@withContext Result.failure(IllegalStateException("App is in Offline Local Mode. Sign in with Google to sync."))
        }

        if (caps.isPendingApproval) {
            return@withContext Result.failure(IllegalStateException("Account is awaiting Owner approval before syncing."))
        }

        _syncStatus.value = _syncStatus.value.copy(isSyncing = true, lastMessage = "Authenticating & compressing deltas...")
        try {
            val pushStats = if (caps.canEditContent) {
                pushLocalChanges(onProgress).getOrThrow()
            } else {
                onProgress?.invoke("Viewer Role active — skipping push, pulling remote deltas only...")
                CloudSyncPushStats()
            }

            val pullStats = pullRemoteChanges(onProgress).getOrThrow()
            val now = System.currentTimeMillis()
            val totalRows = pushStats.pushedRows + pullStats.appliedRows
            val effectiveSaved = if (pushStats.rawBytes > 0) pushStats.savedPercent else vault.cloudLastSavedPercent

            vault.cloudLastSyncAtMs = now
            vault.cloudLastSavedPercent = effectiveSaved
            vault.cloudLastSyncedRows = totalRows

            val msg = buildString {
                append("Pushed ${pushStats.pushedRows} · Pulled ${pullStats.appliedRows}")
                if (pullStats.deletedRows > 0) append(" (${pullStats.deletedRows} deleted)")
                if (pushStats.rawBytes > 0) {
                    append(" · ${GzipCompressionResult.formatKb(pushStats.rawBytes)} -> ${GzipCompressionResult.formatKb(pushStats.compressedBytes)} (${pushStats.savedPercent}% saved)")
                }
            }

            val summary = CloudSyncSummary(
                pushedRows = pushStats.pushedRows,
                pulledRows = pullStats.appliedRows,
                deletedRows = pullStats.deletedRows,
                rawBytes = pushStats.rawBytes,
                compressedBytes = pushStats.compressedBytes,
                savedPercent = effectiveSaved,
                syncedAtEpochMs = now,
                message = msg
            )
            _syncStatus.value = CloudSyncStatus(
                isSyncing = false,
                lastSyncedAtMs = now,
                lastSavedPercent = effectiveSaved,
                lastSyncedRows = totalRows,
                lastMessage = msg
            )
            Result.success(summary)
        } catch (ex: Exception) {
            _syncStatus.value = _syncStatus.value.copy(
                isSyncing = false,
                lastMessage = "Sync failed: ${ex.message}"
            )
            Result.failure(ex)
        }
    }

    suspend fun pushLocalChanges(onProgress: ((String) -> Unit)? = null): Result<CloudSyncPushStats> = withContext(Dispatchers.IO) {
        val caps = roleGuard.currentCapabilities
        if (!caps.canEditContent) {
            return@withContext Result.failure(IllegalStateException("Read-Only (Viewer Role) cannot push changes."))
        }
        val token = authManager.ensureValidAccessToken().getOrElse { return@withContext Result.failure(it) }
        val baseUrl = authManager.normalizedBaseUrl()
        val anonKey = vault.supabaseAnonKey.trim()
        val userId = vault.cloudUserId.ifBlank { null }
        val sinceMs = vault.cloudLastPushAtMs

        try {
            val envelopes = mutableListOf<CloudSyncEnvelopeRow>()
            var rawTotal = 0
            var compressedTotal = 0

            fun addEnvelope(
                recordType: String,
                recordId: String,
                nicheId: Long,
                titlePreview: String,
                updatedAt: Long,
                rawJson: String
            ) {
                val compressed = PayloadGzipCodec.compress(rawJson)
                rawTotal += compressed.rawBytes
                compressedTotal += compressed.compressedBytes
                envelopes += CloudSyncEnvelopeRow(
                    recordType = recordType,
                    recordId = recordId,
                    nicheId = nicheId,
                    titlePreview = titlePreview.take(120),
                    compressedPayload = compressed.encodedPayload,
                    isDeleted = false,
                    updatedBy = userId,
                    updatedAt = updatedAt.coerceAtLeast(1L)
                )
            }

            // 1. Niches
            db.nicheDao().getAllNichesOnce()
                .filter { sinceMs == 0L || it.createdAt >= sinceMs }
                .forEach { n ->
                    addEnvelope(
                        recordType = "NICHE",
                        recordId = "niche_${n.id}",
                        nicheId = n.id,
                        titlePreview = "${n.emoji} ${n.name}",
                        updatedAt = n.createdAt,
                        rawJson = json.encodeToString(
                            SyncNicheDto(
                                id = n.id,
                                name = n.name,
                                emoji = n.emoji,
                                description = n.description,
                                systemPromptRules = n.systemPromptRules,
                                visualStylePreset = n.visualStylePreset,
                                defaultVoiceId = n.defaultVoiceId,
                                defaultAspectRatio = n.defaultAspectRatio,
                                linkedCharacterId = n.linkedCharacterId,
                                linkedHashtagPackId = n.linkedHashtagPackId,
                                linkedYoutubeProfileId = n.linkedYoutubeProfileId,
                                rollingChatSummary = n.rollingChatSummary,
                                lastSummarizedMessageCount = n.lastSummarizedMessageCount,
                                episodeCount = n.episodeCount,
                                autoNextEnabled = n.autoNextEnabled,
                                createdAt = n.createdAt
                            )
                        )
                    )
                }

            // 2. Chat Threads & Messages
            val threads = db.chatDao().getAllThreadsOnce()
            threads.filter { it.updatedAt > sinceMs }.forEach { t ->
                addEnvelope(
                    recordType = "CHAT_THREAD",
                    recordId = "thread_${t.id}",
                    nicheId = t.nicheId,
                    titlePreview = t.title,
                    updatedAt = t.updatedAt,
                    rawJson = json.encodeToString(
                        SyncChatThreadDto(
                            id = t.id,
                            title = t.title,
                            systemPrompt = t.systemPrompt,
                            rollingSummary = t.rollingSummary,
                            summarizedMessageCount = t.summarizedMessageCount,
                            workspaceId = t.workspaceId,
                            nicheId = t.nicheId,
                            folderName = t.folderName,
                            tagsCsv = t.tagsCsv,
                            isPinned = t.isPinned,
                            isArchived = t.isArchived,
                            providerUsed = t.providerUsed,
                            messageCount = t.messageCount,
                            updatedAt = t.updatedAt
                        )
                    )
                )
            }
            threads.forEach { t ->
                db.chatDao().getMessagesOnce(t.id)
                    .filter { it.createdAt > sinceMs }
                    .forEach { m ->
                        addEnvelope(
                            recordType = "CHAT_MESSAGE",
                            recordId = "msg_${m.id}",
                            nicheId = t.nicheId,
                            titlePreview = "[${m.role}] ${m.content.replace('\n', ' ').take(80)}",
                            updatedAt = m.createdAt,
                            rawJson = json.encodeToString(
                                SyncChatMessageDto(
                                    id = m.id,
                                    threadId = m.threadId,
                                    role = m.role,
                                    content = m.content,
                                    providerTag = m.providerTag,
                                    isBookmarked = m.isBookmarked,
                                    tokenEstimate = m.tokenEstimate,
                                    modelName = m.modelName,
                                    createdAt = m.createdAt
                                )
                            )
                        )
                    }
            }

            // 3. Video Scripts + Normalized Scenes (strictly text-only, no local MP4/JPEG paths)
            db.videoScriptDao().getAllScriptsWithRelationsOnce()
                .filter { it.script.updatedAt > sinceMs }
                .forEach { bundle ->
                    val s = bundle.script
                    addEnvelope(
                        recordType = "VIDEO_SCRIPT",
                        recordId = "script_${s.id}",
                        nicheId = s.nicheId,
                        titlePreview = s.title,
                        updatedAt = s.updatedAt,
                        rawJson = json.encodeToString(
                            SyncVideoScriptBundleDto(
                                id = s.id,
                                workspaceId = s.workspaceId,
                                nicheId = s.nicheId,
                                title = s.title,
                                topic = s.topic,
                                characterProfileId = s.characterProfileId,
                                aspectRatio = s.aspectRatio,
                                voiceId = s.voiceId,
                                styleTag = s.styleTag,
                                status = s.status,
                                seamlessLoop = s.seamlessLoop,
                                createdAt = s.createdAt,
                                updatedAt = s.updatedAt,
                                scenes = bundle.scenes.sortedBy { it.sceneOrder }.map { sc ->
                                    SyncSceneDto(
                                        id = sc.id,
                                        scriptId = sc.scriptId,
                                        sceneOrder = sc.sceneOrder,
                                        visualPrompt = sc.visualPrompt,
                                        narration = sc.narration,
                                        cameraMovement = sc.cameraMovement,
                                        durationSec = sc.durationSec,
                                        isFavoriteScene = sc.isFavoriteScene
                                    )
                                }
                            )
                        )
                    )
                }

            // 4. Social Posts
            db.socialSeoDao().getAllPostsOnce()
                .filter { it.updatedAt > sinceMs }
                .forEach { p ->
                    addEnvelope(
                        recordType = "SOCIAL_POST",
                        recordId = "social_${p.id}",
                        nicheId = p.nicheId,
                        titlePreview = "[${p.platform}] ${p.title.ifBlank { p.hookLine }}",
                        updatedAt = p.updatedAt,
                        rawJson = json.encodeToString(
                            SyncSocialPostDto(
                                id = p.id,
                                scriptId = p.scriptId,
                                nicheId = p.nicheId,
                                platform = p.platform,
                                title = p.title,
                                abTitlesJson = p.abTitlesJson,
                                hookLine = p.hookLine,
                                description = p.description,
                                hashtags = p.hashtags,
                                pinnedComment = p.pinnedComment,
                                callToAction = p.callToAction,
                                publishStatus = p.publishStatus,
                                scheduledAtMillis = p.scheduledAtMillis,
                                publishedUrl = p.publishedUrl,
                                updatedAt = p.updatedAt
                            )
                        )
                    )
                }

            // 5. Projects & Project Tasks
            db.projectDao().getAllProjectsOnce()
                .filter { it.updatedAt > sinceMs }
                .forEach { proj ->
                    addEnvelope(
                        recordType = "PROJECT",
                        recordId = "project_${proj.id}",
                        nicheId = proj.linkedNicheId ?: 1L,
                        titlePreview = "${proj.name} (${proj.slug})",
                        updatedAt = proj.updatedAt,
                        rawJson = json.encodeToString(
                            SyncProjectDto(
                                id = proj.id,
                                name = proj.name,
                                slug = proj.slug,
                                description = proj.description,
                                projectType = proj.projectType,
                                linkedNicheId = proj.linkedNicheId,
                                githubOwner = proj.githubOwner,
                                githubRepo = proj.githubRepo,
                                githubBranch = proj.githubBranch,
                                status = proj.status,
                                autoPushOnSave = proj.autoPushOnSave,
                                lastCommitSha = proj.lastCommitSha,
                                lastPushedAt = proj.lastPushedAt,
                                notes = proj.notes,
                                createdAt = proj.createdAt,
                                updatedAt = proj.updatedAt
                            )
                        )
                    )
                }

            db.projectDao().getAllTasksOnce()
                .filter { it.updatedAt > sinceMs }
                .forEach { task ->
                    addEnvelope(
                        recordType = "PROJECT_TASK",
                        recordId = "task_${task.id}",
                        nicheId = 1L,
                        titlePreview = "[${task.columnStatus}] ${task.title}",
                        updatedAt = task.updatedAt,
                        rawJson = json.encodeToString(
                            SyncProjectTaskDto(
                                id = task.id,
                                projectId = task.projectId,
                                title = task.title,
                                details = task.details,
                                priority = task.priority,
                                columnStatus = task.columnStatus,
                                dueDateMillis = task.dueDateMillis,
                                linkedScriptId = task.linkedScriptId,
                                sortOrder = task.sortOrder,
                                updatedAt = task.updatedAt
                            )
                        )
                    )
                }

            // 6. AI Memory Facts
            db.knowledgeBrainDao().getAllMemoryFactsOnce()
                .filter { it.createdAt > sinceMs }
                .forEach { mem ->
                    addEnvelope(
                        recordType = "AI_MEMORY",
                        recordId = "memory_${mem.id}",
                        nicheId = mem.nicheId,
                        titlePreview = "[${mem.category}] ${mem.factText.take(60)}",
                        updatedAt = mem.createdAt,
                        rawJson = json.encodeToString(
                            SyncAiMemoryDto(
                                id = mem.id,
                                category = mem.category,
                                factText = mem.factText,
                                enabled = mem.enabled,
                                workspaceId = mem.workspaceId,
                                nicheId = mem.nicheId,
                                createdAt = mem.createdAt
                            )
                        )
                    )
                }

            // 7. Prompt Chains
            db.promptChainDao().getAllOnce()
                .filter { !it.isBuiltIn && it.createdAt > sinceMs }
                .forEach { chain ->
                    addEnvelope(
                        recordType = "PROMPT_CHAIN",
                        recordId = "chain_${chain.id}",
                        nicheId = 1L,
                        titlePreview = chain.name,
                        updatedAt = chain.createdAt,
                        rawJson = json.encodeToString(
                            SyncPromptChainDto(
                                id = chain.id,
                                name = chain.name,
                                description = chain.description,
                                stepsJson = chain.stepsJson,
                                outputTarget = chain.outputTarget,
                                isBuiltIn = chain.isBuiltIn,
                                createdAt = chain.createdAt
                            )
                        )
                    )
                }

            // 8. Hashtag Packs
            if (sinceMs == 0L) {
                db.socialSeoDao().getAllHashtagPacksOnce().forEach { pack ->
                    addEnvelope(
                        recordType = "HASHTAG_PACK",
                        recordId = "hashtags_${pack.id}",
                        nicheId = 1L,
                        titlePreview = "${pack.packName} (${pack.platform})",
                        updatedAt = System.currentTimeMillis(),
                        rawJson = json.encodeToString(
                            SyncHashtagPackDto(
                                id = pack.id,
                                packName = pack.packName,
                                platform = pack.platform,
                                tagsText = pack.tagsText,
                                useCount = pack.useCount
                            )
                        )
                    )
                }
            }

            // 9. Non-Secret Vault Metadata (OWNER-only row enforced by PostgreSQL RLS)
            if (caps.canWriteVault && sinceMs == 0L) {
                val metaNow = System.currentTimeMillis()
                addEnvelope(
                    recordType = "VAULT_META",
                    recordId = "vault_meta_primary",
                    nicheId = 1L,
                    titlePreview = "Owner Model & Channel Labels (Zero Secrets)",
                    updatedAt = metaNow,
                    rawJson = json.encodeToString(
                        SyncVaultMetaDto(
                            primaryModelName = vault.primaryModelName,
                            huggingFaceModel = vault.huggingFaceModel,
                            kaggleDatasetSlug = vault.kaggleDatasetSlug,
                            kaggleKernelSlug = vault.kaggleKernelSlug,
                            githubRepo = vault.githubRepo,
                            ytChannelLabel = vault.ytChannelLabel,
                            autoFallbackEnabled = vault.autoFallbackEnabled,
                            updatedAt = metaNow
                        )
                    )
                )
            }

            if (envelopes.isEmpty()) {
                onProgress?.invoke("No new local text deltas to push.")
                return@withContext Result.success(CloudSyncPushStats())
            }

            onProgress?.invoke("Pushing ${envelopes.size} compressed text delta row(s) to Supabase...")
            envelopes.chunked(BATCH_SIZE).forEach { batch ->
                val bodyStr = json.encodeToString(batch)
                val req = Request.Builder()
                    .url("$baseUrl/rest/v1/omni_sync_records?on_conflict=record_type,record_id")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(bodyStr.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(req).execute().use { resp ->
                    val respText = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Supabase batch upsert failed (HTTP ${resp.code}): ${respText.take(200)}")
                    }
                }
            }

            vault.cloudLastPushAtMs = System.currentTimeMillis()
            val savedPct = if (rawTotal > 0) {
                (((rawTotal - compressedTotal).toDouble() / rawTotal.toDouble()) * 100.0)
                    .toInt()
                    .coerceIn(0, 99)
            } else 0

            onProgress?.invoke(
                "Pushed ${envelopes.size} row(s) (${GzipCompressionResult.formatKb(rawTotal)} -> ${GzipCompressionResult.formatKb(compressedTotal)}, $savedPct% saved)."
            )

            Result.success(
                CloudSyncPushStats(
                    pushedRows = envelopes.size,
                    rawBytes = rawTotal,
                    compressedBytes = compressedTotal,
                    savedPercent = savedPct
                )
            )
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    suspend fun pullRemoteChanges(onProgress: ((String) -> Unit)? = null): Result<CloudSyncPullStats> = withContext(Dispatchers.IO) {
        val token = authManager.ensureValidAccessToken().getOrElse { return@withContext Result.failure(it) }
        val baseUrl = authManager.normalizedBaseUrl()
        val anonKey = vault.supabaseAnonKey.trim()
        val lastPull = vault.cloudLastPullAtMs

        try {
            onProgress?.invoke("Pulling remote deltas since epoch $lastPull...")
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/omni_sync_records?updated_at=gt.$lastPull&order=updated_at.asc&limit=500")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .get()
                .build()

            val rows = client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Supabase delta pull failed (HTTP ${resp.code}): ${body.take(200)}")
                }
                json.decodeFromString<List<CloudSyncEnvelopeRow>>(body)
            }

            if (rows.isEmpty()) {
                onProgress?.invoke("Local Room DB is already up to date with Supabase.")
                return@withContext Result.success(CloudSyncPullStats(latestTimestampMs = lastPull))
            }

            var applied = 0
            var deleted = 0
            var maxUpdatedAt = lastPull

            // Apply parent records before children to honor foreign keys cleanly
            val priorityOrder = mapOf(
                "NICHE" to 1,
                "CHAT_THREAD" to 2,
                "CHAT_MESSAGE" to 3,
                "VIDEO_SCRIPT" to 4,
                "SOCIAL_POST" to 5,
                "PROJECT" to 6,
                "PROJECT_TASK" to 7,
                "AI_MEMORY" to 8,
                "PROMPT_CHAIN" to 9,
                "HASHTAG_PACK" to 10,
                "VAULT_META" to 11
            )

            rows.sortedWith(compareBy<CloudSyncEnvelopeRow> { priorityOrder[it.recordType] ?: 99 }.thenBy { it.updatedAt })
                .forEach { row ->
                    if (row.updatedAt > maxUpdatedAt) {
                        maxUpdatedAt = row.updatedAt
                    }
                    val decompressedJson = runCatching { PayloadGzipCodec.decompress(row.compressedPayload) }.getOrNull()
                        ?: return@forEach

                    val changed = applyRemoteRowLww(row, decompressedJson)
                    if (changed) {
                        if (row.isDeleted) deleted++ else applied++
                    }
                }

            if (maxUpdatedAt > lastPull) {
                vault.cloudLastPullAtMs = maxUpdatedAt
            }

            onProgress?.invoke("Pulled ${rows.size} row(s): applied $applied update(s), $deleted soft-delete(s).")
            Result.success(
                CloudSyncPullStats(
                    pulledRows = rows.size,
                    appliedRows = applied,
                    deletedRows = deleted,
                    latestTimestampMs = maxUpdatedAt
                )
            )
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    private suspend fun applyRemoteRowLww(row: CloudSyncEnvelopeRow, payloadJson: String): Boolean {
        return runCatching {
            when (row.recordType) {
                "NICHE" -> {
                    val dto = json.decodeFromString<SyncNicheDto>(payloadJson)
                    val existing = db.nicheDao().getNicheById(dto.id)
                    if (row.isDeleted) {
                        if (existing != null) db.nicheDao().delete(existing)
                        return@runCatching existing != null
                    }
                    if (existing == null || row.updatedAt >= existing.createdAt) {
                        db.nicheDao().insert(
                            NicheEntity(
                                id = dto.id,
                                name = dto.name,
                                emoji = dto.emoji,
                                description = dto.description,
                                systemPromptRules = dto.systemPromptRules,
                                visualStylePreset = dto.visualStylePreset,
                                defaultVoiceId = dto.defaultVoiceId,
                                defaultAspectRatio = dto.defaultAspectRatio,
                                linkedCharacterId = dto.linkedCharacterId,
                                linkedHashtagPackId = dto.linkedHashtagPackId,
                                linkedYoutubeProfileId = dto.linkedYoutubeProfileId,
                                rollingChatSummary = dto.rollingChatSummary,
                                lastSummarizedMessageCount = dto.lastSummarizedMessageCount,
                                episodeCount = dto.episodeCount,
                                autoNextEnabled = dto.autoNextEnabled,
                                createdAt = dto.createdAt
                            )
                        )
                        true
                    } else false
                }
                "CHAT_THREAD" -> {
                    val dto = json.decodeFromString<SyncChatThreadDto>(payloadJson)
                    val existing = db.chatDao().getThread(dto.id)
                    if (row.isDeleted) {
                        if (existing != null) db.chatDao().deleteThread(existing)
                        return@runCatching existing != null
                    }
                    if (existing == null || row.updatedAt >= existing.updatedAt) {
                        db.chatDao().insertThread(
                            ChatThreadEntity(
                                id = dto.id,
                                title = dto.title,
                                systemPrompt = dto.systemPrompt,
                                rollingSummary = dto.rollingSummary,
                                summarizedMessageCount = dto.summarizedMessageCount,
                                workspaceId = dto.workspaceId,
                                nicheId = dto.nicheId,
                                folderName = dto.folderName,
                                tagsCsv = dto.tagsCsv,
                                isPinned = dto.isPinned,
                                isArchived = dto.isArchived,
                                providerUsed = dto.providerUsed,
                                messageCount = dto.messageCount,
                                updatedAt = dto.updatedAt
                            )
                        )
                        true
                    } else false
                }
                "CHAT_MESSAGE" -> {
                    val dto = json.decodeFromString<SyncChatMessageDto>(payloadJson)
                    if (row.isDeleted) return@runCatching false
                    if (db.chatDao().getThread(dto.threadId) == null) return@runCatching false
                    db.chatDao().insertMessage(
                        ChatMessageEntity(
                            id = dto.id,
                            threadId = dto.threadId,
                            role = dto.role,
                            content = dto.content,
                            providerTag = dto.providerTag,
                            isBookmarked = dto.isBookmarked,
                            tokenEstimate = dto.tokenEstimate,
                            modelName = dto.modelName,
                            createdAt = dto.createdAt
                        )
                    )
                    true
                }
                "VIDEO_SCRIPT" -> {
                    val dto = json.decodeFromString<SyncVideoScriptBundleDto>(payloadJson)
                    val existing = db.videoScriptDao().getScriptById(dto.id)
                    if (row.isDeleted) {
                        if (existing != null) db.videoScriptDao().deleteScript(existing)
                        return@runCatching existing != null
                    }
                    if (existing == null || row.updatedAt >= existing.updatedAt) {
                        db.videoScriptDao().insertScript(
                            VideoScriptEntity(
                                id = dto.id,
                                workspaceId = dto.workspaceId,
                                nicheId = dto.nicheId,
                                title = dto.title,
                                topic = dto.topic,
                                characterProfileId = dto.characterProfileId,
                                aspectRatio = dto.aspectRatio,
                                voiceId = dto.voiceId,
                                styleTag = dto.styleTag,
                                status = dto.status,
                                linkedVideoPath = existing?.linkedVideoPath.orEmpty(),
                                thumbnailPath = existing?.thumbnailPath.orEmpty(),
                                seamlessLoop = dto.seamlessLoop,
                                createdAt = dto.createdAt,
                                updatedAt = dto.updatedAt
                            )
                        )
                        if (dto.scenes.isNotEmpty()) {
                            db.videoScriptDao().deleteScenesForScript(dto.id)
                            db.videoScriptDao().insertScenes(
                                dto.scenes.map { sc ->
                                    SceneEntity(
                                        id = sc.id,
                                        scriptId = dto.id,
                                        sceneOrder = sc.sceneOrder,
                                        visualPrompt = sc.visualPrompt,
                                        narration = sc.narration,
                                        cameraMovement = sc.cameraMovement,
                                        durationSec = sc.durationSec,
                                        renderedClipPath = "",
                                        isFavoriteScene = sc.isFavoriteScene
                                    )
                                }
                            )
                        }
                        true
                    } else false
                }
                "SOCIAL_POST" -> {
                    val dto = json.decodeFromString<SyncSocialPostDto>(payloadJson)
                    val existing = db.socialSeoDao().getPostById(dto.id)
                    if (row.isDeleted) {
                        if (existing != null) db.socialSeoDao().deletePost(existing)
                        return@runCatching existing != null
                    }
                    if (existing == null || row.updatedAt >= existing.updatedAt) {
                        val validScriptId = dto.scriptId?.takeIf { db.videoScriptDao().getScriptById(it) != null }
                        db.socialSeoDao().insertPost(
                            SocialPostEntity(
                                id = dto.id,
                                scriptId = validScriptId,
                                nicheId = dto.nicheId,
                                platform = dto.platform,
                                title = dto.title,
                                abTitlesJson = dto.abTitlesJson,
                                hookLine = dto.hookLine,
                                description = dto.description,
                                hashtags = dto.hashtags,
                                pinnedComment = dto.pinnedComment,
                                callToAction = dto.callToAction,
                                publishStatus = dto.publishStatus,
                                scheduledAtMillis = dto.scheduledAtMillis,
                                publishedUrl = dto.publishedUrl,
                                updatedAt = dto.updatedAt
                            )
                        )
                        true
                    } else false
                }
                "PROJECT" -> {
                    val dto = json.decodeFromString<SyncProjectDto>(payloadJson)
                    val existing = db.projectDao().getProjectById(dto.id)
                    if (row.isDeleted) {
                        if (existing != null) db.projectDao().deleteProject(existing)
                        return@runCatching existing != null
                    }
                    if (existing == null || row.updatedAt >= existing.updatedAt) {
                        db.projectDao().insertProject(
                            ProjectEntity(
                                id = dto.id,
                                name = dto.name,
                                slug = dto.slug,
                                description = dto.description,
                                projectType = dto.projectType,
                                linkedNicheId = dto.linkedNicheId,
                                githubOwner = dto.githubOwner,
                                githubRepo = dto.githubRepo,
                                githubBranch = dto.githubBranch,
                                status = dto.status,
                                autoPushOnSave = dto.autoPushOnSave,
                                lastCommitSha = dto.lastCommitSha,
                                lastPushedAt = dto.lastPushedAt,
                                notes = dto.notes,
                                createdAt = dto.createdAt,
                                updatedAt = dto.updatedAt
                            )
                        )
                        true
                    } else false
                }
                "PROJECT_TASK" -> {
                    val dto = json.decodeFromString<SyncProjectTaskDto>(payloadJson)
                    if (db.projectDao().getProjectById(dto.projectId) == null) return@runCatching false
                    val existing = db.projectDao().getAllTasksOnce().firstOrNull { it.id == dto.id }
                    if (row.isDeleted) {
                        if (existing != null) db.projectDao().deleteTask(existing)
                        return@runCatching existing != null
                    }
                    if (existing == null || row.updatedAt >= existing.updatedAt) {
                        db.projectDao().insertTask(
                            ProjectTaskEntity(
                                id = dto.id,
                                projectId = dto.projectId,
                                title = dto.title,
                                details = dto.details,
                                priority = dto.priority,
                                columnStatus = dto.columnStatus,
                                dueDateMillis = dto.dueDateMillis,
                                linkedScriptId = dto.linkedScriptId,
                                sortOrder = dto.sortOrder,
                                updatedAt = dto.updatedAt
                            )
                        )
                        true
                    } else false
                }
                "AI_MEMORY" -> {
                    val dto = json.decodeFromString<SyncAiMemoryDto>(payloadJson)
                    val existing = db.knowledgeBrainDao().getAllMemoryFactsOnce().firstOrNull { it.id == dto.id }
                    if (row.isDeleted) {
                        if (existing != null) db.knowledgeBrainDao().deleteMemoryFact(existing)
                        return@runCatching existing != null
                    }
                    db.knowledgeBrainDao().insertMemoryFact(
                        UserMemoryFactEntity(
                            id = dto.id,
                            category = dto.category,
                            factText = dto.factText,
                            enabled = dto.enabled,
                            workspaceId = dto.workspaceId,
                            nicheId = dto.nicheId,
                            createdAt = dto.createdAt
                        )
                    )
                    true
                }
                "PROMPT_CHAIN" -> {
                    val dto = json.decodeFromString<SyncPromptChainDto>(payloadJson)
                    val existing = db.promptChainDao().getAllOnce().firstOrNull { it.id == dto.id }
                    if (row.isDeleted) {
                        if (existing != null) db.promptChainDao().delete(existing)
                        return@runCatching existing != null
                    }
                    db.promptChainDao().insert(
                        PromptChainEntity(
                            id = dto.id,
                            name = dto.name,
                            description = dto.description,
                            stepsJson = dto.stepsJson,
                            outputTarget = dto.outputTarget,
                            isBuiltIn = dto.isBuiltIn,
                            createdAt = dto.createdAt
                        )
                    )
                    true
                }
                "HASHTAG_PACK" -> {
                    val dto = json.decodeFromString<SyncHashtagPackDto>(payloadJson)
                    val existing = db.socialSeoDao().getAllHashtagPacksOnce().firstOrNull { it.id == dto.id }
                    if (row.isDeleted) {
                        if (existing != null) db.socialSeoDao().deleteHashtagPack(existing)
                        return@runCatching existing != null
                    }
                    db.socialSeoDao().insertHashtagPack(
                        HashtagPackEntity(
                            id = dto.id,
                            packName = dto.packName,
                            platform = dto.platform,
                            tagsText = dto.tagsText,
                            useCount = dto.useCount
                        )
                    )
                    true
                }
                "VAULT_META" -> {
                    if (!roleGuard.currentCapabilities.canWriteVault || row.isDeleted) return@runCatching false
                    val dto = json.decodeFromString<SyncVaultMetaDto>(payloadJson)
                    if (dto.primaryModelName.isNotBlank()) vault.primaryModelName = dto.primaryModelName
                    if (dto.huggingFaceModel.isNotBlank()) vault.huggingFaceModel = dto.huggingFaceModel
                    if (dto.kaggleDatasetSlug.isNotBlank()) vault.kaggleDatasetSlug = dto.kaggleDatasetSlug
                    if (dto.kaggleKernelSlug.isNotBlank()) vault.kaggleKernelSlug = dto.kaggleKernelSlug
                    if (dto.githubRepo.isNotBlank() && vault.githubRepo.isBlank()) vault.githubRepo = dto.githubRepo
                    if (dto.ytChannelLabel.isNotBlank()) vault.ytChannelLabel = dto.ytChannelLabel
                    true
                }
                else -> false
            }
        }.getOrDefault(false)
    }
}
