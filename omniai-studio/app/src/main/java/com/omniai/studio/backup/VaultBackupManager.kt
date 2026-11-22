package com.omniai.studio.backup

import android.content.Context
import android.util.Base64
import com.omniai.studio.data.local.AppSettingsRepository
import com.omniai.studio.data.local.AutoTypeSpeedMode
import com.omniai.studio.data.local.CharacterProfileEntity
import com.omniai.studio.data.local.ChatMessageEntity
import com.omniai.studio.data.local.ChatThreadEntity
import com.omniai.studio.data.local.GitCommitLogEntity
import com.omniai.studio.data.local.HashtagPackEntity
import com.omniai.studio.data.local.NextCommandEntity
import com.omniai.studio.data.local.NicheEntity
import com.omniai.studio.data.local.OmniAiDatabase
import com.omniai.studio.data.local.ProjectEntity
import com.omniai.studio.data.local.ProjectFileIndexEntity
import com.omniai.studio.data.local.ProjectTaskEntity
import com.omniai.studio.data.local.PromptChainEntity
import com.omniai.studio.data.local.PromptPresetEntity
import com.omniai.studio.data.local.SceneEntity
import com.omniai.studio.data.local.ScriptRevisionEntity
import com.omniai.studio.data.local.SeriesEpisodeEntity
import com.omniai.studio.data.local.SlashCommandEntity
import com.omniai.studio.data.local.SocialPostEntity
import com.omniai.studio.data.local.StoryboardEntity
import com.omniai.studio.data.local.UserMemoryFactEntity
import com.omniai.studio.data.local.VideoScriptEntity
import com.omniai.studio.data.local.WorkspaceProfileEntity
import com.omniai.studio.data.remote.KaggleAccountCredential
import com.omniai.studio.data.remote.PresetBackupItem
import com.omniai.studio.data.remote.VaultSnapshot
import com.omniai.studio.security.AppIconVariant
import com.omniai.studio.security.VaultManager
import com.omniai.studio.ui.theme.ThemeAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BackupSelectionOptions(
    val includeVaultAndKagglePool: Boolean = true,
    val includeChatsAndMemory: Boolean = true,
    val includeStoryboardsAndCharacterDna: Boolean = true,
    val includePromptChainsAndSettings: Boolean = true,
    val includeContentScriptsAndSocialDb: Boolean = true,
    val includeNichesContinuityAndSeries: Boolean = true,
    val includeProjectsAndGitLogs: Boolean = true
)

@Serializable
data class EncryptedContainerEnvelope(
    val format: String = "OMNIAI_ENC_V25",
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int = 100_000,
    val cipher: String = "AES/GCM/NoPadding",
    val exportedAtEpochMs: Long,
    val saltBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String
)

@Serializable
data class BackupChatMessageItem(
    val role: String,
    val content: String,
    val providerTag: String = "",
    val isBookmarked: Boolean = false,
    val tokenEstimate: Int = 0,
    val modelName: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class BackupChatThreadItem(
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
    val updatedAt: Long = 0L,
    val messages: List<BackupChatMessageItem> = emptyList()
)

@Serializable
data class BackupMemoryFactItem(
    val category: String,
    val factText: String,
    val enabled: Boolean = true,
    val workspaceId: String = "default",
    val nicheId: Long = 1L
)

@Serializable
data class BackupStoryboardItem(
    val title: String,
    val topic: String,
    val aspectRatio: String,
    val stylePreset: String,
    val voice: String,
    val burnSubtitles: Boolean,
    val scenesJson: String,
    val socialMetadataJson: String = "",
    val watermarkText: String = "",
    val bgmUrl: String = "",
    val speedFactor: Float = 1.0f,
    val workspaceId: String = "default"
)

@Serializable
data class BackupCharacterDnaItem(
    val name: String,
    val appearancePrompt: String,
    val negativePrompt: String = "",
    val defaultStyle: String = "Cinematic Realism"
)

@Serializable
data class BackupPromptChainItem(
    val name: String,
    val description: String,
    val stepsJson: String,
    val outputTarget: String = "CHAT",
    val isBuiltIn: Boolean = false
)

@Serializable
data class BackupSlashCommandItem(
    val commandName: String,
    val description: String,
    val templateBody: String
)

@Serializable
data class BackupWorkspaceItem(
    val id: String,
    val name: String,
    val description: String = ""
)

@Serializable
data class BackupSceneItem(
    val sceneOrder: Int,
    val visualPrompt: String,
    val narration: String,
    val cameraMovement: String = "Slow Cinematic Push-In",
    val durationSec: Int = 5,
    val renderedClipPath: String = "",
    val isFavoriteScene: Boolean = false
)

@Serializable
data class BackupScriptRevisionItem(
    val snapshotJson: String,
    val note: String = "Auto-snapshot",
    val savedAt: Long = 0L
)

@Serializable
data class BackupSocialPostItem(
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
    val updatedAt: Long = 0L
)

@Serializable
data class BackupVideoScriptItem(
    val workspaceId: String = "default",
    val nicheId: Long = 1L,
    val title: String,
    val topic: String,
    val characterProfileId: Long? = null,
    val aspectRatio: String = "9:16",
    val voiceId: String = "en-US-AriaNeural",
    val styleTag: String = "Cinematic Realism",
    val status: String = "DRAFT",
    val linkedVideoPath: String = "",
    val seamlessLoop: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val scenes: List<BackupSceneItem> = emptyList(),
    val revisions: List<BackupScriptRevisionItem> = emptyList(),
    val socialPosts: List<BackupSocialPostItem> = emptyList()
)

@Serializable
data class BackupHashtagPackItem(
    val packName: String,
    val platform: String,
    val tagsText: String,
    val useCount: Int = 0
)

@Serializable
data class BackupNicheItem(
    val originalId: Long = 0L,
    val name: String,
    val emoji: String = "🎬",
    val description: String = "",
    val systemPromptRules: String = "",
    val visualStylePreset: String = "Cinematic Realism",
    val defaultVoiceId: String = "English Male",
    val defaultAspectRatio: String = "9:16",
    val linkedCharacterId: Long? = null,
    val linkedHashtagPackId: Long? = null,
    val rollingChatSummary: String = "",
    val lastSummarizedMessageCount: Int = 0,
    val episodeCount: Int = 0,
    val autoNextEnabled: Boolean = false,
    val createdAt: Long = 0L
)

@Serializable
data class BackupNextCommandItem(
    val nicheId: Long = 1L,
    val sourceScriptId: Long? = null,
    val sourceType: String = "POST_VIDEO_SEQUEL",
    val shortLabel: String,
    val fullPromptCommand: String,
    val suggestedEpisodeNumber: Int = 1,
    val isUsed: Boolean = false,
    val createdAt: Long = 0L
)

@Serializable
data class BackupSeriesEpisodeItem(
    val nicheId: Long = 1L,
    val episodeNumber: Int,
    val title: String,
    val hookIdea: String,
    val continuityNote: String = "",
    val status: String = "PLANNED",
    val linkedScriptId: Long? = null,
    val createdAt: Long = 0L
)

@Serializable
data class BackupAppSettingsSnapshot(
    val iconVariant: String = AppIconVariant.DEFAULT.name,
    val themeAccent: String = ThemeAccent.MATRIX_GREEN.name,
    val chatFontSizeSp: Float = 14f,
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 2048,
    val chatContextWindow: Int = 12,
    val autoReadAiReplies: Boolean = false,
    val ttsSpeechRate: Float = 1.0f,
    val wifiOnlyAutoDownload: Boolean = false,
    val kagglePollIntervalSec: Int = 20,
    val autoCleanTempAfterMerge: Boolean = true,
    val activeWorkspaceId: String = "default",
    val telegramAutoArchiveVideos: Boolean = false,
    val activeNicheId: Long = 1L,
    val filterAllNiches: Boolean = false,
    val autoTypeEnabled: Boolean = true,
    val autoTypeSpeedMode: String = AutoTypeSpeedMode.FAST.name,
    val autoGenerateNextAfterRender: Boolean = true,
    val autoSummarizeNicheThreshold: Int = 20,
    val seamlessLoopDefault: Boolean = false,
    val defaultGithubOwner: String = "",
    val defaultGithubBranch: String = "main",
    val autoWatchActionsAfterPush: Boolean = true,
    val aiAutoCommitMessageEnabled: Boolean = true
)

@Serializable
data class BackupProjectTaskItem(
    val title: String,
    val details: String = "",
    val priority: String = "MEDIUM",
    val columnStatus: String = "TO_DO",
    val dueDateMillis: Long? = null,
    val linkedScriptId: Long? = null,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0L
)

@Serializable
data class BackupProjectFileIndexItem(
    val relativePath: String,
    val syncedSha256: String,
    val lastModifiedAt: Long = 0L
)

@Serializable
data class BackupGitCommitLogItem(
    val commitSha: String = "",
    val commitMessage: String,
    val branch: String = "main",
    val filesChanged: Int = 0,
    val status: String = "SUCCESS",
    val errorLog: String = "",
    val timestamp: Long = 0L
)

@Serializable
data class BackupProjectItem(
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
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val tasks: List<BackupProjectTaskItem> = emptyList(),
    val fileIndexes: List<BackupProjectFileIndexItem> = emptyList(),
    val commitLogs: List<BackupGitCommitLogItem> = emptyList()
)

@Serializable
data class SelectiveBackupPayload(
    val schemaVersion: Int = 45,
    val exportedAtEpochMs: Long,
    val selection: BackupSelectionOptions,
    val vault: VaultSnapshot? = null,
    val kagglePool: List<KaggleAccountCredential> = emptyList(),
    val workspaces: List<BackupWorkspaceItem> = emptyList(),
    val chatThreads: List<BackupChatThreadItem> = emptyList(),
    val memoryFacts: List<BackupMemoryFactItem> = emptyList(),
    val storyboards: List<BackupStoryboardItem> = emptyList(),
    val characterProfiles: List<BackupCharacterDnaItem> = emptyList(),
    val promptPresets: List<PresetBackupItem> = emptyList(),
    val promptChains: List<BackupPromptChainItem> = emptyList(),
    val slashCommands: List<BackupSlashCommandItem> = emptyList(),
    val videoScripts: List<BackupVideoScriptItem> = emptyList(),
    val standaloneSocialPosts: List<BackupSocialPostItem> = emptyList(),
    val hashtagPacks: List<BackupHashtagPackItem> = emptyList(),
    val niches: List<BackupNicheItem> = emptyList(),
    val nextCommands: List<BackupNextCommandItem> = emptyList(),
    val seriesEpisodes: List<BackupSeriesEpisodeItem> = emptyList(),
    val projects: List<BackupProjectItem> = emptyList(),
    val settings: BackupAppSettingsSnapshot? = null
)

data class RestoreSummary(
    val restoredVault: Boolean,
    val restoredKaggleAccounts: Int,
    val restoredThreads: Int,
    val restoredMemoryFacts: Int,
    val restoredStoryboards: Int,
    val restoredCharacters: Int,
    val restoredPromptChains: Int,
    val restoredVideoScripts: Int = 0,
    val restoredScenes: Int = 0,
    val restoredSocialPosts: Int = 0,
    val restoredHashtagPacks: Int = 0,
    val restoredNiches: Int = 0,
    val restoredNextCommands: Int = 0,
    val restoredSeriesEpisodes: Int = 0,
    val restoredProjects: Int = 0,
    val restoredProjectTasks: Int = 0,
    val restoredGitCommits: Int = 0,
    val restoredSettings: Boolean
) {
    fun toStatusString(): String = buildString {
        append("Restored ")
        val parts = mutableListOf<String>()
        if (restoredVault) parts.add("Vault + $restoredKaggleAccounts Kaggle acct(s)")
        if (restoredProjects > 0 || restoredProjectTasks > 0 || restoredGitCommits > 0) {
            parts.add("$restoredProjects project(s), $restoredProjectTasks task(s) & $restoredGitCommits commit log(s)")
        }
        if (restoredNiches > 0 || restoredNextCommands > 0 || restoredSeriesEpisodes > 0) {
            parts.add("$restoredNiches niche(s), $restoredNextCommands next-cmd(s) & $restoredSeriesEpisodes series ep(s)")
        }
        if (restoredThreads > 0 || restoredMemoryFacts > 0) {
            parts.add("$restoredThreads chat(s) & $restoredMemoryFacts memory fact(s)")
        }
        if (restoredStoryboards > 0 || restoredCharacters > 0) {
            parts.add("$restoredStoryboards storyboard(s) & $restoredCharacters Character DNA")
        }
        if (restoredVideoScripts > 0 || restoredSocialPosts > 0 || restoredHashtagPacks > 0) {
            parts.add("$restoredVideoScripts script(s), $restoredScenes scene(s), $restoredSocialPosts SEO post(s) & $restoredHashtagPacks tag pack(s)")
        }
        if (restoredPromptChains > 0 || restoredSettings) {
            parts.add("$restoredPromptChains workflow chain(s) & settings")
        }
        append(if (parts.isEmpty()) "empty bundle." else parts.joinToString(", ") + ".")
    }
}

data class LocalBackupSnapshotInfo(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long
)

/**
 * Layer 1 & Layer 4 Selective AES-256-GCM Backup Engine.
 * - PBKDF2WithHmacSHA256 (100,000 iterations) + random 16-byte salt + random 12-byte GCM IV.
 * - Portable `.omniai.enc` JSON container suitable for SAF files, Copy/Paste migration text,
 *   GitHub private repository sync, Telegram Bot archiving, and nightly rolling local snapshots.
 * - v3.5 includes normalized Video Scripts, Scenes, Revisions, YT/IG/FB Social Posts, and Hashtag Packs.
 */
class VaultBackupManager(
    private val context: Context,
    private val db: OmniAiDatabase,
    private val vault: VaultManager,
    private val settingsRepo: AppSettingsRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun createEncryptedContainerJson(
        passphrase: String,
        selection: BackupSelectionOptions = BackupSelectionOptions()
    ): String = withContext(Dispatchers.IO) {
        require(passphrase.length >= 6) { "Backup passphrase must be at least 6 characters." }
        val payload = collectSelectedPayload(selection)
        val plaintext = json.encodeToString(payload)
        encryptToContainerJson(plaintext, passphrase)
    }

    suspend fun createEncryptedMigrationText(
        passphrase: String,
        selection: BackupSelectionOptions = BackupSelectionOptions()
    ): String {
        val containerJson = createEncryptedContainerJson(passphrase, selection)
        val encoded = Base64.encodeToString(containerJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "$MIGRATION_PREFIX$encoded"
    }

    suspend fun restoreFromEncryptedTextOrJson(
        rawContent: String,
        passphrase: String
    ): RestoreSummary = withContext(Dispatchers.IO) {
        require(passphrase.length >= 6) { "Enter the backup passphrase (min 6 chars)." }
        val trimmed = rawContent.trim()
        val containerJson = if (trimmed.startsWith(MIGRATION_PREFIX)) {
            val b64 = trimmed.removePrefix(MIGRATION_PREFIX).trim()
            String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
        } else {
            trimmed
        }
        val plaintext = decryptFromContainerJson(containerJson, passphrase)
        val payload = json.decodeFromString<SelectiveBackupPayload>(plaintext)
        applyRestoredPayload(payload)
    }

    suspend fun writeRollingLocalSnapshot(
        passphrase: String,
        selection: BackupSelectionOptions = BackupSelectionOptions(),
        maxSnapshotsToKeep: Int = MAX_ROLLING_LOCAL_SNAPSHOTS
    ): File = withContext(Dispatchers.IO) {
        val containerJson = createEncryptedContainerJson(passphrase, selection)
        val dir = getLocalBackupsDir()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val target = File(dir, "OmniAI_AutoBackup_$stamp.omniai.enc")
        target.writeText(containerJson, Charsets.UTF_8)
        pruneRollingLocalSnapshots(maxSnapshotsToKeep)
        target
    }

    fun listLocalSnapshots(): List<LocalBackupSnapshotInfo> {
        val dir = getLocalBackupsDir()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".omniai.enc") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { LocalBackupSnapshotInfo(it, it.name, it.length(), it.lastModified()) }
            ?: emptyList()
    }

    fun pruneRollingLocalSnapshots(maxKeep: Int = MAX_ROLLING_LOCAL_SNAPSHOTS) {
        val sorted = listLocalSnapshots()
        if (sorted.size > maxKeep) {
            sorted.drop(maxKeep).forEach { it.file.delete() }
        }
    }

    fun getLocalBackupsDir(): File {
        val ext = context.getExternalFilesDir("Backups")
        val target = ext ?: File(context.filesDir, "Backups")
        if (!target.exists()) target.mkdirs()
        return target
    }

    private suspend fun collectSelectedPayload(selection: BackupSelectionOptions): SelectiveBackupPayload {
        val workspaces = db.workspaceDao().getAllOnce().map {
            BackupWorkspaceItem(it.id, it.name, it.description)
        }

        val vaultSnap = if (selection.includeVaultAndKagglePool) vault.exportSnapshot() else null
        val kagglePool = if (selection.includeVaultAndKagglePool) vault.getKaggleAccountPool() else emptyList()

        val chatThreads = if (selection.includeChatsAndMemory) {
            db.chatDao().getAllThreadsOnce().map { thread ->
                val msgs = db.chatDao().getMessagesOnce(thread.id).map { m ->
                    BackupChatMessageItem(
                        role = m.role,
                        content = m.content,
                        providerTag = m.providerTag,
                        isBookmarked = m.isBookmarked,
                        tokenEstimate = m.tokenEstimate,
                        modelName = m.modelName,
                        createdAt = m.createdAt
                    )
                }
                BackupChatThreadItem(
                    title = thread.title,
                    systemPrompt = thread.systemPrompt,
                    rollingSummary = thread.rollingSummary,
                    summarizedMessageCount = thread.summarizedMessageCount,
                    workspaceId = thread.workspaceId,
                    nicheId = thread.nicheId,
                    folderName = thread.folderName,
                    tagsCsv = thread.tagsCsv,
                    isPinned = thread.isPinned,
                    isArchived = thread.isArchived,
                    providerUsed = thread.providerUsed,
                    messageCount = msgs.size,
                    updatedAt = thread.updatedAt,
                    messages = msgs
                )
            }
        } else {
            emptyList()
        }

        val memoryFacts = if (selection.includeChatsAndMemory) {
            db.knowledgeBrainDao().getAllMemoryFactsOnce().map {
                BackupMemoryFactItem(it.category, it.factText, it.enabled, it.workspaceId, it.nicheId)
            }
        } else {
            emptyList()
        }

        val storyboards = if (selection.includeStoryboardsAndCharacterDna) {
            db.storyboardDao().getAllOnce().map { sb ->
                BackupStoryboardItem(
                    title = sb.title,
                    topic = sb.topic,
                    aspectRatio = sb.aspectRatio,
                    stylePreset = sb.stylePreset,
                    voice = sb.voice,
                    burnSubtitles = sb.burnSubtitles,
                    scenesJson = sb.scenesJson,
                    socialMetadataJson = sb.socialMetadataJson,
                    watermarkText = sb.watermarkText,
                    bgmUrl = sb.bgmUrl,
                    speedFactor = sb.speedFactor,
                    workspaceId = sb.workspaceId
                )
            }
        } else {
            emptyList()
        }

        val characters = if (selection.includeStoryboardsAndCharacterDna) {
            db.characterProfileDao().getAllOnce().map { c ->
                BackupCharacterDnaItem(c.name, c.appearancePrompt, c.negativePrompt, c.defaultStyle)
            }
        } else {
            emptyList()
        }

        val videoScripts = if (selection.includeContentScriptsAndSocialDb) {
            db.videoScriptDao().getAllScriptsWithRelationsOnce().map { bundle ->
                val s = bundle.script
                BackupVideoScriptItem(
                    workspaceId = s.workspaceId,
                    nicheId = s.nicheId,
                    title = s.title,
                    topic = s.topic,
                    characterProfileId = s.characterProfileId,
                    aspectRatio = s.aspectRatio,
                    voiceId = s.voiceId,
                    styleTag = s.styleTag,
                    status = s.status,
                    linkedVideoPath = s.linkedVideoPath,
                    seamlessLoop = s.seamlessLoop,
                    createdAt = s.createdAt,
                    updatedAt = s.updatedAt,
                    scenes = bundle.scenes.sortedBy { it.sceneOrder }.map { sc ->
                        BackupSceneItem(
                            sceneOrder = sc.sceneOrder,
                            visualPrompt = sc.visualPrompt,
                            narration = sc.narration,
                            cameraMovement = sc.cameraMovement,
                            durationSec = sc.durationSec,
                            renderedClipPath = sc.renderedClipPath,
                            isFavoriteScene = sc.isFavoriteScene
                        )
                    },
                    revisions = bundle.revisions.map { rev ->
                        BackupScriptRevisionItem(
                            snapshotJson = rev.snapshotJson,
                            note = rev.note,
                            savedAt = rev.savedAt
                        )
                    },
                    socialPosts = bundle.socialPosts.map { p ->
                        BackupSocialPostItem(
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
                    }
                )
            }
        } else {
            emptyList()
        }

        val standaloneSocialPosts = if (selection.includeContentScriptsAndSocialDb) {
            db.socialSeoDao().getAllPostsOnce()
                .filter { it.scriptId == null }
                .map { p ->
                    BackupSocialPostItem(
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
                }
        } else {
            emptyList()
        }

        val hashtagPacks = if (selection.includeContentScriptsAndSocialDb) {
            db.socialSeoDao().getAllHashtagPacksOnce().map { pack ->
                BackupHashtagPackItem(
                    packName = pack.packName,
                    platform = pack.platform,
                    tagsText = pack.tagsText,
                    useCount = pack.useCount
                )
            }
        } else {
            emptyList()
        }

        val niches = if (selection.includeNichesContinuityAndSeries) {
            db.nicheDao().getAllNichesOnce().map { n ->
                BackupNicheItem(
                    originalId = n.id,
                    name = n.name,
                    emoji = n.emoji,
                    description = n.description,
                    systemPromptRules = n.systemPromptRules,
                    visualStylePreset = n.visualStylePreset,
                    defaultVoiceId = n.defaultVoiceId,
                    defaultAspectRatio = n.defaultAspectRatio,
                    linkedCharacterId = n.linkedCharacterId,
                    linkedHashtagPackId = n.linkedHashtagPackId,
                    rollingChatSummary = n.rollingChatSummary,
                    lastSummarizedMessageCount = n.lastSummarizedMessageCount,
                    episodeCount = n.episodeCount,
                    autoNextEnabled = n.autoNextEnabled,
                    createdAt = n.createdAt
                )
            }
        } else {
            emptyList()
        }

        val nextCommands = if (selection.includeNichesContinuityAndSeries) {
            db.nextCommandDao().getAllCommandsOnce().map { cmd ->
                BackupNextCommandItem(
                    nicheId = cmd.nicheId,
                    sourceScriptId = cmd.sourceScriptId,
                    sourceType = cmd.sourceType,
                    shortLabel = cmd.shortLabel,
                    fullPromptCommand = cmd.fullPromptCommand,
                    suggestedEpisodeNumber = cmd.suggestedEpisodeNumber,
                    isUsed = cmd.isUsed,
                    createdAt = cmd.createdAt
                )
            }
        } else {
            emptyList()
        }

        val seriesEpisodes = if (selection.includeNichesContinuityAndSeries) {
            db.seriesEpisodeDao().getAllEpisodesOnce().map { ep ->
                BackupSeriesEpisodeItem(
                    nicheId = ep.nicheId,
                    episodeNumber = ep.episodeNumber,
                    title = ep.title,
                    hookIdea = ep.hookIdea,
                    continuityNote = ep.continuityNote,
                    status = ep.status,
                    linkedScriptId = ep.linkedScriptId,
                    createdAt = ep.createdAt
                )
            }
        } else {
            emptyList()
        }

        val projects = if (selection.includeProjectsAndGitLogs) {
            val allTasksByProj = db.projectDao().getAllTasksOnce().groupBy { it.projectId }
            val allIdxByProj = db.projectDao().getAllFileIndexesOnce().groupBy { it.projectId }
            val allLogsByProj = db.projectDao().getAllCommitLogsOnce().groupBy { it.projectId }
            db.projectDao().getAllProjectsOnce().map { p ->
                BackupProjectItem(
                    name = p.name,
                    slug = p.slug,
                    description = p.description,
                    projectType = p.projectType,
                    linkedNicheId = p.linkedNicheId,
                    githubOwner = p.githubOwner,
                    githubRepo = p.githubRepo,
                    githubBranch = p.githubBranch,
                    status = p.status,
                    autoPushOnSave = p.autoPushOnSave,
                    lastCommitSha = p.lastCommitSha,
                    lastPushedAt = p.lastPushedAt,
                    notes = p.notes,
                    createdAt = p.createdAt,
                    updatedAt = p.updatedAt,
                    tasks = allTasksByProj[p.id].orEmpty().map { t ->
                        BackupProjectTaskItem(
                            title = t.title,
                            details = t.details,
                            priority = t.priority,
                            columnStatus = t.columnStatus,
                            dueDateMillis = t.dueDateMillis,
                            linkedScriptId = t.linkedScriptId,
                            sortOrder = t.sortOrder,
                            updatedAt = t.updatedAt
                        )
                    },
                    fileIndexes = allIdxByProj[p.id].orEmpty().map { idx ->
                        BackupProjectFileIndexItem(
                            relativePath = idx.relativePath,
                            syncedSha256 = idx.syncedSha256,
                            lastModifiedAt = idx.lastModifiedAt
                        )
                    },
                    commitLogs = allLogsByProj[p.id].orEmpty().map { log ->
                        BackupGitCommitLogItem(
                            commitSha = log.commitSha,
                            commitMessage = log.commitMessage,
                            branch = log.branch,
                            filesChanged = log.filesChanged,
                            status = log.status,
                            errorLog = log.errorLog,
                            timestamp = log.timestamp
                        )
                    }
                )
            }
        } else {
            emptyList()
        }

        val presets = if (selection.includePromptChainsAndSettings) {
            db.promptPresetDao().getAllOnce().map { PresetBackupItem(it.name, it.systemPrompt) }
        } else {
            emptyList()
        }

        val chains = if (selection.includePromptChainsAndSettings) {
            db.promptChainDao().getAllOnce().map { c ->
                BackupPromptChainItem(c.name, c.description, c.stepsJson, c.outputTarget, c.isBuiltIn)
            }
        } else {
            emptyList()
        }

        val slashCmds = if (selection.includePromptChainsAndSettings) {
            db.knowledgeBrainDao().getAllSlashCommandsOnce().map {
                BackupSlashCommandItem(it.commandName, it.description, it.templateBody)
            }
        } else {
            emptyList()
        }

        val settingsSnap = if (selection.includePromptChainsAndSettings) {
            val s = settingsRepo.current
            BackupAppSettingsSnapshot(
                iconVariant = s.iconVariant.name,
                themeAccent = s.themeAccent.name,
                chatFontSizeSp = s.chatFontSizeSp,
                temperature = s.temperature,
                maxOutputTokens = s.maxOutputTokens,
                chatContextWindow = s.chatContextWindow,
                autoReadAiReplies = s.autoReadAiReplies,
                ttsSpeechRate = s.ttsSpeechRate,
                wifiOnlyAutoDownload = s.wifiOnlyAutoDownload,
                kagglePollIntervalSec = s.kagglePollIntervalSec,
                autoCleanTempAfterMerge = s.autoCleanTempAfterMerge,
                activeWorkspaceId = s.activeWorkspaceId,
                telegramAutoArchiveVideos = s.telegramAutoArchiveVideos,
                activeNicheId = s.activeNicheId,
                filterAllNiches = s.filterAllNiches,
                autoTypeEnabled = s.autoTypeEnabled,
                autoTypeSpeedMode = s.autoTypeSpeedMode.name,
                autoGenerateNextAfterRender = s.autoGenerateNextAfterRender,
                autoSummarizeNicheThreshold = s.autoSummarizeNicheThreshold,
                seamlessLoopDefault = s.seamlessLoopDefault,
                defaultGithubOwner = s.defaultGithubOwner,
                defaultGithubBranch = s.defaultGithubBranch,
                autoWatchActionsAfterPush = s.autoWatchActionsAfterPush,
                aiAutoCommitMessageEnabled = s.aiAutoCommitMessageEnabled
            )
        } else {
            null
        }

        return SelectiveBackupPayload(
            exportedAtEpochMs = System.currentTimeMillis(),
            selection = selection,
            vault = vaultSnap,
            kagglePool = kagglePool,
            workspaces = workspaces,
            chatThreads = chatThreads,
            memoryFacts = memoryFacts,
            storyboards = storyboards,
            characterProfiles = characters,
            promptPresets = presets,
            promptChains = chains,
            slashCommands = slashCmds,
            videoScripts = videoScripts,
            standaloneSocialPosts = standaloneSocialPosts,
            hashtagPacks = hashtagPacks,
            niches = niches,
            nextCommands = nextCommands,
            seriesEpisodes = seriesEpisodes,
            projects = projects,
            settings = settingsSnap
        )
    }

    private suspend fun applyRestoredPayload(payload: SelectiveBackupPayload): RestoreSummary {
        payload.workspaces.forEach { ws ->
            db.workspaceDao().insert(WorkspaceProfileEntity(ws.id, ws.name, ws.description))
        }

        var restoredVault = false
        if (payload.vault != null) {
            vault.importSnapshot(payload.vault)
            restoredVault = true
        }
        if (payload.kagglePool.isNotEmpty()) {
            vault.replaceKaggleAccountPool(payload.kagglePool)
        }

        // Restore Niches first so FK references from NextCommandEntity and SeriesEpisodeEntity resolve cleanly
        val nicheIdRemap = mutableMapOf<Long, Long>()
        payload.niches.forEach { n ->
            val insertedId = db.nicheDao().insert(
                NicheEntity(
                    id = if (n.originalId > 0L) n.originalId else 0L,
                    name = n.name,
                    emoji = n.emoji.ifBlank { "🎬" },
                    description = n.description,
                    systemPromptRules = n.systemPromptRules,
                    visualStylePreset = n.visualStylePreset.ifBlank { "Cinematic Realism" },
                    defaultVoiceId = n.defaultVoiceId.ifBlank { "English Male" },
                    defaultAspectRatio = n.defaultAspectRatio.ifBlank { "9:16" },
                    linkedCharacterId = n.linkedCharacterId,
                    linkedHashtagPackId = n.linkedHashtagPackId,
                    rollingChatSummary = n.rollingChatSummary,
                    lastSummarizedMessageCount = n.lastSummarizedMessageCount,
                    episodeCount = n.episodeCount,
                    autoNextEnabled = n.autoNextEnabled,
                    createdAt = if (n.createdAt > 0L) n.createdAt else System.currentTimeMillis()
                )
            )
            if (n.originalId > 0L) {
                nicheIdRemap[n.originalId] = insertedId
            }
        }
        val fallbackNicheId = db.nicheDao().getAllNichesOnce().firstOrNull()?.id ?: 1L
        fun resolveNicheId(rawId: Long): Long = nicheIdRemap[rawId] ?: rawId.takeIf { it > 0L } ?: fallbackNicheId

        payload.chatThreads.forEach { t ->
            val newThreadId = db.chatDao().insertThread(
                ChatThreadEntity(
                    title = t.title,
                    systemPrompt = t.systemPrompt,
                    rollingSummary = t.rollingSummary,
                    summarizedMessageCount = t.summarizedMessageCount,
                    workspaceId = t.workspaceId.ifBlank { "default" },
                    nicheId = resolveNicheId(t.nicheId),
                    folderName = t.folderName.ifBlank { "General" },
                    tagsCsv = t.tagsCsv,
                    isPinned = t.isPinned,
                    isArchived = t.isArchived,
                    providerUsed = t.providerUsed,
                    messageCount = if (t.messageCount > 0) t.messageCount else t.messages.size,
                    updatedAt = if (t.updatedAt > 0L) t.updatedAt else System.currentTimeMillis()
                )
            )
            t.messages.forEach { m ->
                db.chatDao().insertMessage(
                    ChatMessageEntity(
                        threadId = newThreadId,
                        role = m.role,
                        content = m.content,
                        providerTag = m.providerTag,
                        isBookmarked = m.isBookmarked,
                        tokenEstimate = if (m.tokenEstimate > 0) m.tokenEstimate else (m.content.length / 4),
                        modelName = m.modelName.ifBlank { m.providerTag },
                        createdAt = if (m.createdAt > 0L) m.createdAt else System.currentTimeMillis()
                    )
                )
            }
        }

        payload.memoryFacts.forEach { fact ->
            db.knowledgeBrainDao().insertMemoryFact(
                UserMemoryFactEntity(
                    category = fact.category,
                    factText = fact.factText,
                    enabled = fact.enabled,
                    workspaceId = fact.workspaceId.ifBlank { "default" },
                    nicheId = resolveNicheId(fact.nicheId)
                )
            )
        }

        payload.storyboards.forEach { sb ->
            db.storyboardDao().insert(
                StoryboardEntity(
                    title = sb.title,
                    topic = sb.topic,
                    aspectRatio = sb.aspectRatio,
                    stylePreset = sb.stylePreset,
                    voice = sb.voice,
                    burnSubtitles = sb.burnSubtitles,
                    scenesJson = sb.scenesJson,
                    socialMetadataJson = sb.socialMetadataJson,
                    watermarkText = sb.watermarkText,
                    bgmUrl = sb.bgmUrl,
                    speedFactor = sb.speedFactor,
                    workspaceId = sb.workspaceId.ifBlank { "default" }
                )
            )
        }

        payload.characterProfiles.forEach { cp ->
            db.characterProfileDao().insert(
                CharacterProfileEntity(
                    name = cp.name,
                    appearancePrompt = cp.appearancePrompt,
                    negativePrompt = cp.negativePrompt,
                    defaultStyle = cp.defaultStyle
                )
            )
        }

        var restoredScenesCount = 0
        var restoredSocialPostsCount = 0
        payload.videoScripts.forEach { script ->
            val newScriptId = db.videoScriptDao().insertScript(
                VideoScriptEntity(
                    workspaceId = script.workspaceId.ifBlank { "default" },
                    nicheId = resolveNicheId(script.nicheId),
                    title = script.title,
                    topic = script.topic,
                    characterProfileId = script.characterProfileId,
                    aspectRatio = script.aspectRatio,
                    voiceId = script.voiceId,
                    styleTag = script.styleTag,
                    status = script.status,
                    linkedVideoPath = script.linkedVideoPath,
                    seamlessLoop = script.seamlessLoop,
                    createdAt = if (script.createdAt > 0L) script.createdAt else System.currentTimeMillis(),
                    updatedAt = if (script.updatedAt > 0L) script.updatedAt else System.currentTimeMillis()
                )
            )
            if (script.scenes.isNotEmpty()) {
                val sceneEntities = script.scenes.map { sc ->
                    SceneEntity(
                        scriptId = newScriptId,
                        sceneOrder = sc.sceneOrder,
                        visualPrompt = sc.visualPrompt,
                        narration = sc.narration,
                        cameraMovement = sc.cameraMovement,
                        durationSec = sc.durationSec,
                        renderedClipPath = sc.renderedClipPath,
                        isFavoriteScene = sc.isFavoriteScene
                    )
                }
                db.videoScriptDao().insertScenes(sceneEntities)
                restoredScenesCount += sceneEntities.size
            }
            script.revisions.forEach { rev ->
                db.videoScriptDao().insertRevision(
                    ScriptRevisionEntity(
                        scriptId = newScriptId,
                        snapshotJson = rev.snapshotJson,
                        note = rev.note,
                        savedAt = if (rev.savedAt > 0L) rev.savedAt else System.currentTimeMillis()
                    )
                )
            }
            script.socialPosts.forEach { post ->
                db.socialSeoDao().insertPost(
                    SocialPostEntity(
                        scriptId = newScriptId,
                        nicheId = resolveNicheId(post.nicheId),
                        platform = post.platform,
                        title = post.title,
                        abTitlesJson = post.abTitlesJson,
                        hookLine = post.hookLine,
                        description = post.description,
                        hashtags = post.hashtags,
                        pinnedComment = post.pinnedComment,
                        callToAction = post.callToAction,
                        publishStatus = post.publishStatus,
                        scheduledAtMillis = post.scheduledAtMillis,
                        publishedUrl = post.publishedUrl,
                        updatedAt = if (post.updatedAt > 0L) post.updatedAt else System.currentTimeMillis()
                    )
                )
                restoredSocialPostsCount++
            }
        }

        payload.standaloneSocialPosts.forEach { post ->
            db.socialSeoDao().insertPost(
                SocialPostEntity(
                    scriptId = null,
                    nicheId = resolveNicheId(post.nicheId),
                    platform = post.platform,
                    title = post.title,
                    abTitlesJson = post.abTitlesJson,
                    hookLine = post.hookLine,
                    description = post.description,
                    hashtags = post.hashtags,
                    pinnedComment = post.pinnedComment,
                    callToAction = post.callToAction,
                    publishStatus = post.publishStatus,
                    scheduledAtMillis = post.scheduledAtMillis,
                    publishedUrl = post.publishedUrl,
                    updatedAt = if (post.updatedAt > 0L) post.updatedAt else System.currentTimeMillis()
                )
            )
            restoredSocialPostsCount++
        }

        payload.hashtagPacks.forEach { pack ->
            db.socialSeoDao().insertHashtagPack(
                HashtagPackEntity(
                    packName = pack.packName,
                    platform = pack.platform,
                    tagsText = pack.tagsText,
                    useCount = pack.useCount
                )
            )
        }

        val validNicheIds = db.nicheDao().getAllNichesOnce().map { it.id }.toSet()
        var restoredNextCmds = 0
        payload.nextCommands.forEach { cmd ->
            val targetNicheId = resolveNicheId(cmd.nicheId)
            if (targetNicheId in validNicheIds) {
                db.nextCommandDao().insert(
                    NextCommandEntity(
                        nicheId = targetNicheId,
                        sourceScriptId = cmd.sourceScriptId,
                        sourceType = cmd.sourceType,
                        shortLabel = cmd.shortLabel,
                        fullPromptCommand = cmd.fullPromptCommand,
                        suggestedEpisodeNumber = cmd.suggestedEpisodeNumber,
                        isUsed = cmd.isUsed,
                        createdAt = if (cmd.createdAt > 0L) cmd.createdAt else System.currentTimeMillis()
                    )
                )
                restoredNextCmds++
            }
        }

        var restoredSeriesCount = 0
        payload.seriesEpisodes.forEach { ep ->
            val targetNicheId = resolveNicheId(ep.nicheId)
            if (targetNicheId in validNicheIds) {
                db.seriesEpisodeDao().insert(
                    SeriesEpisodeEntity(
                        nicheId = targetNicheId,
                        episodeNumber = ep.episodeNumber,
                        title = ep.title,
                        hookIdea = ep.hookIdea,
                        continuityNote = ep.continuityNote,
                        status = ep.status,
                        linkedScriptId = ep.linkedScriptId,
                        createdAt = if (ep.createdAt > 0L) ep.createdAt else System.currentTimeMillis()
                    )
                )
                restoredSeriesCount++
            }
        }

        payload.promptPresets.forEach { preset ->
            db.promptPresetDao().insert(PromptPresetEntity(name = preset.name, systemPrompt = preset.systemPrompt))
        }

        payload.promptChains.forEach { chain ->
            db.promptChainDao().insert(
                PromptChainEntity(
                    name = chain.name,
                    description = chain.description,
                    stepsJson = chain.stepsJson,
                    outputTarget = chain.outputTarget,
                    isBuiltIn = chain.isBuiltIn
                )
            )
        }

        payload.slashCommands.forEach { cmd ->
            db.knowledgeBrainDao().insertSlashCommand(
                SlashCommandEntity(
                    commandName = cmd.commandName,
                    description = cmd.description,
                    templateBody = cmd.templateBody
                )
            )
        }

        var restoredProjectsCount = 0
        var restoredProjectTasksCount = 0
        var restoredGitCommitsCount = 0
        payload.projects.forEach { proj ->
            val existing = db.projectDao().getProjectBySlug(proj.slug)
            val newProjectId = if (existing != null) {
                db.projectDao().updateProject(
                    existing.copy(
                        name = proj.name,
                        description = proj.description,
                        projectType = proj.projectType,
                        linkedNicheId = proj.linkedNicheId?.let { resolveNicheId(it) },
                        githubOwner = proj.githubOwner,
                        githubRepo = proj.githubRepo,
                        githubBranch = proj.githubBranch.ifBlank { "main" },
                        status = proj.status,
                        autoPushOnSave = proj.autoPushOnSave,
                        lastCommitSha = proj.lastCommitSha,
                        lastPushedAt = proj.lastPushedAt,
                        notes = proj.notes,
                        updatedAt = if (proj.updatedAt > 0L) proj.updatedAt else System.currentTimeMillis()
                    )
                )
                existing.id
            } else {
                db.projectDao().insertProject(
                    ProjectEntity(
                        name = proj.name,
                        slug = proj.slug,
                        description = proj.description,
                        projectType = proj.projectType,
                        linkedNicheId = proj.linkedNicheId?.let { resolveNicheId(it) },
                        githubOwner = proj.githubOwner,
                        githubRepo = proj.githubRepo,
                        githubBranch = proj.githubBranch.ifBlank { "main" },
                        status = proj.status,
                        autoPushOnSave = proj.autoPushOnSave,
                        lastCommitSha = proj.lastCommitSha,
                        lastPushedAt = proj.lastPushedAt,
                        notes = proj.notes,
                        createdAt = if (proj.createdAt > 0L) proj.createdAt else System.currentTimeMillis(),
                        updatedAt = if (proj.updatedAt > 0L) proj.updatedAt else System.currentTimeMillis()
                    )
                )
            }
            restoredProjectsCount++

            if (proj.tasks.isNotEmpty()) {
                val taskEntities = proj.tasks.map { t ->
                    ProjectTaskEntity(
                        projectId = newProjectId,
                        title = t.title,
                        details = t.details,
                        priority = t.priority,
                        columnStatus = t.columnStatus,
                        dueDateMillis = t.dueDateMillis,
                        linkedScriptId = t.linkedScriptId,
                        sortOrder = t.sortOrder,
                        updatedAt = if (t.updatedAt > 0L) t.updatedAt else System.currentTimeMillis()
                    )
                }
                db.projectDao().insertTasks(taskEntities)
                restoredProjectTasksCount += taskEntities.size
            }
            if (proj.fileIndexes.isNotEmpty()) {
                val idxEntities = proj.fileIndexes.map { idx ->
                    ProjectFileIndexEntity(
                        projectId = newProjectId,
                        relativePath = idx.relativePath,
                        syncedSha256 = idx.syncedSha256,
                        lastModifiedAt = if (idx.lastModifiedAt > 0L) idx.lastModifiedAt else System.currentTimeMillis()
                    )
                }
                db.projectDao().insertFileIndexes(idxEntities)
            }
            proj.commitLogs.forEach { log ->
                db.projectDao().insertCommitLog(
                    GitCommitLogEntity(
                        projectId = newProjectId,
                        commitSha = log.commitSha,
                        commitMessage = log.commitMessage,
                        branch = log.branch.ifBlank { "main" },
                        filesChanged = log.filesChanged,
                        status = log.status,
                        errorLog = log.errorLog,
                        timestamp = if (log.timestamp > 0L) log.timestamp else System.currentTimeMillis()
                    )
                )
                restoredGitCommitsCount++
            }
        }

        var restoredSettings = false
        payload.settings?.let { s ->
            settingsRepo.update { current ->
                current.copy(
                    themeAccent = ThemeAccent.fromStoredName(s.themeAccent),
                    chatFontSizeSp = s.chatFontSizeSp,
                    temperature = s.temperature,
                    maxOutputTokens = s.maxOutputTokens,
                    chatContextWindow = s.chatContextWindow,
                    autoReadAiReplies = s.autoReadAiReplies,
                    ttsSpeechRate = s.ttsSpeechRate,
                    wifiOnlyAutoDownload = s.wifiOnlyAutoDownload,
                    kagglePollIntervalSec = s.kagglePollIntervalSec,
                    autoCleanTempAfterMerge = s.autoCleanTempAfterMerge,
                    activeWorkspaceId = s.activeWorkspaceId.ifBlank { "default" },
                    telegramAutoArchiveVideos = s.telegramAutoArchiveVideos,
                    activeNicheId = resolveNicheId(s.activeNicheId),
                    filterAllNiches = s.filterAllNiches,
                    autoTypeEnabled = s.autoTypeEnabled,
                    autoTypeSpeedMode = AutoTypeSpeedMode.fromStoredName(s.autoTypeSpeedMode),
                    autoGenerateNextAfterRender = s.autoGenerateNextAfterRender,
                    autoSummarizeNicheThreshold = s.autoSummarizeNicheThreshold.coerceIn(8, 60),
                    seamlessLoopDefault = s.seamlessLoopDefault,
                    defaultGithubOwner = s.defaultGithubOwner,
                    defaultGithubBranch = s.defaultGithubBranch.ifBlank { "main" },
                    autoWatchActionsAfterPush = s.autoWatchActionsAfterPush,
                    aiAutoCommitMessageEnabled = s.aiAutoCommitMessageEnabled
                )
            }
            restoredSettings = true
        }

        return RestoreSummary(
            restoredVault = restoredVault,
            restoredKaggleAccounts = payload.kagglePool.size,
            restoredThreads = payload.chatThreads.size,
            restoredMemoryFacts = payload.memoryFacts.size,
            restoredStoryboards = payload.storyboards.size,
            restoredCharacters = payload.characterProfiles.size,
            restoredPromptChains = payload.promptChains.size,
            restoredVideoScripts = payload.videoScripts.size,
            restoredScenes = restoredScenesCount,
            restoredSocialPosts = restoredSocialPostsCount,
            restoredHashtagPacks = payload.hashtagPacks.size,
            restoredNiches = payload.niches.size,
            restoredNextCommands = restoredNextCmds,
            restoredSeriesEpisodes = restoredSeriesCount,
            restoredProjects = restoredProjectsCount,
            restoredProjectTasks = restoredProjectTasksCount,
            restoredGitCommits = restoredGitCommitsCount,
            restoredSettings = restoredSettings
        )
    }

    private fun encryptToContainerJson(plaintextJson: String, passphrase: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val key = deriveAesKey(passphrase, salt, PBKDF2_ITERATIONS)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintextJson.toByteArray(Charsets.UTF_8))

        val envelope = EncryptedContainerEnvelope(
            exportedAtEpochMs = System.currentTimeMillis(),
            iterations = PBKDF2_ITERATIONS,
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
        return json.encodeToString(envelope)
    }

    private fun decryptFromContainerJson(containerJson: String, passphrase: String): String {
        val envelope = json.decodeFromString<EncryptedContainerEnvelope>(containerJson)
        require(envelope.format == "OMNIAI_ENC_V25") { "Unsupported encrypted container format: ${envelope.format}" }
        val salt = Base64.decode(envelope.saltBase64, Base64.DEFAULT)
        val iv = Base64.decode(envelope.ivBase64, Base64.DEFAULT)
        val ciphertext = Base64.decode(envelope.ciphertextBase64, Base64.DEFAULT)

        val key = deriveAesKey(passphrase, salt, envelope.iterations.coerceIn(10_000, 500_000))
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, Charsets.UTF_8)
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        private const val SALT_LEN = 16
        private const val IV_LEN = 12
        private const val GCM_TAG_BITS = 128
        private const val KEY_BITS = 256
        private const val PBKDF2_ITERATIONS = 100_000
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MIGRATION_PREFIX = "OMNIAI_ENC_V25:"
        const val MAX_ROLLING_LOCAL_SNAPSHOTS = 5
    }
}
