package com.omniai.studio.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY createdAt ASC")
    fun observeWorkspaces(): Flow<List<WorkspaceProfileEntity>>

    @Query("SELECT * FROM workspaces ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<WorkspaceProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workspace: WorkspaceProfileEntity)

    @Delete
    suspend fun delete(workspace: WorkspaceProfileEntity)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_threads ORDER BY isPinned DESC, updatedAt DESC")
    fun observeThreads(): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE workspaceId = :workspaceId AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun observeThreadsForWorkspace(workspaceId: String): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE workspaceId = :workspaceId ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAllThreadsForWorkspace(workspaceId: String): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE workspaceId = :workspaceId AND folderName = :folderName AND isArchived = :archived ORDER BY isPinned DESC, updatedAt DESC")
    fun observeThreadsByFolder(workspaceId: String, folderName: String, archived: Boolean = false): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE workspaceId = :workspaceId AND isArchived = :archived ORDER BY isPinned DESC, updatedAt DESC")
    fun observeThreadsByArchiveState(workspaceId: String, archived: Boolean): Flow<List<ChatThreadEntity>>

    @Query(
        """
        SELECT DISTINCT t.* FROM chat_threads t
        LEFT JOIN chat_messages m ON m.threadId = t.id
        WHERE t.title LIKE '%' || :query || '%'
           OR t.folderName LIKE '%' || :query || '%'
           OR t.tagsCsv LIKE '%' || :query || '%'
           OR m.content LIKE '%' || :query || '%'
        ORDER BY t.isPinned DESC, t.updatedAt DESC
        """
    )
    suspend fun searchThreadsAndMessages(query: String): List<ChatThreadEntity>

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE content LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchMessagesOnce(query: String, limit: Int = 25): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE isBookmarked = 1 ORDER BY createdAt DESC")
    fun observeBookmarkedMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE isBookmarked = 1 ORDER BY createdAt DESC")
    suspend fun getBookmarkedMessagesOnce(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_threads ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getAllThreadsOnce(): List<ChatThreadEntity>

    @Query("SELECT * FROM chat_threads WHERE id = :threadId LIMIT 1")
    suspend fun getThread(threadId: Long): ChatThreadEntity?

    @Query("SELECT COUNT(*) FROM chat_threads")
    suspend fun countThreads(): Int

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun countMessages(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ChatThreadEntity): Long

    @Update
    suspend fun updateThread(thread: ChatThreadEntity)

    @Query("UPDATE chat_threads SET isPinned = :pinned, updatedAt = :updatedAt WHERE id = :threadId")
    suspend fun setThreadPinned(threadId: Long, pinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_threads SET isArchived = :archived, updatedAt = :updatedAt WHERE id = :threadId")
    suspend fun setThreadArchived(threadId: Long, archived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_threads SET folderName = :folderName, tagsCsv = :tagsCsv, updatedAt = :updatedAt WHERE id = :threadId")
    suspend fun updateThreadOrganization(
        threadId: Long,
        folderName: String,
        tagsCsv: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE chat_threads SET providerUsed = :providerUsed, messageCount = :messageCount, updatedAt = :updatedAt WHERE id = :threadId")
    suspend fun updateThreadStats(
        threadId: Long,
        providerUsed: String,
        messageCount: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE chat_threads SET rollingSummary = :summary, summarizedMessageCount = :summarizedCount, updatedAt = :updatedAt WHERE id = :threadId")
    suspend fun updateThreadSummary(
        threadId: Long,
        summary: String,
        summarizedCount: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun deleteThread(thread: ChatThreadEntity)

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(threadId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessagesOnce(threadId: Long): List<ChatMessageEntity>

    @Query(
        """
        SELECT m.* FROM chat_messages m
        INNER JOIN chat_threads t ON t.id = m.threadId
        WHERE t.nicheId = :nicheId
        ORDER BY m.createdAt DESC, m.id DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentMessagesForNicheOnce(nicheId: Long, limit: Int = 16): List<ChatMessageEntity>

    @Query(
        """
        SELECT COUNT(*) FROM chat_messages m
        INNER JOIN chat_threads t ON t.id = m.threadId
        WHERE t.nicheId = :nicheId
        """
    )
    suspend fun countMessagesForNiche(nicheId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET content = :content, providerTag = :providerTag, modelName = :modelName, tokenEstimate = :tokenEstimate WHERE id = :id")
    suspend fun updateMessageContent(
        id: Long,
        content: String,
        providerTag: String,
        modelName: String = providerTag,
        tokenEstimate: Int = (content.length / 4).coerceAtLeast(0)
    )

    @Query("UPDATE chat_messages SET isBookmarked = :bookmarked WHERE id = :messageId")
    suspend fun setMessageBookmarked(messageId: Long, bookmarked: Boolean)
}

@Dao
interface PromptPresetDao {
    @Query("SELECT * FROM prompt_presets ORDER BY createdAt DESC")
    fun observePresets(): Flow<List<PromptPresetEntity>>

    @Query("SELECT * FROM prompt_presets ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<PromptPresetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: PromptPresetEntity): Long

    @Delete
    suspend fun delete(preset: PromptPresetEntity)
}

@Dao
interface PromptChainDao {
    @Query("SELECT * FROM prompt_chains ORDER BY isBuiltIn DESC, createdAt DESC")
    fun observeChains(): Flow<List<PromptChainEntity>>

    @Query("SELECT * FROM prompt_chains ORDER BY isBuiltIn DESC, createdAt DESC")
    suspend fun getAllOnce(): List<PromptChainEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chain: PromptChainEntity): Long

    @Delete
    suspend fun delete(chain: PromptChainEntity)
}

@Dao
interface StoryboardDao {
    @Query("SELECT * FROM storyboards ORDER BY createdAt DESC")
    fun observeStoryboards(): Flow<List<StoryboardEntity>>

    @Query("SELECT * FROM storyboards WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    fun observeStoryboardsForWorkspace(workspaceId: String): Flow<List<StoryboardEntity>>

    @Query("SELECT * FROM storyboards ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<StoryboardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(storyboard: StoryboardEntity): Long

    @Delete
    suspend fun delete(storyboard: StoryboardEntity)
}

@Dao
interface RenderJobDao {
    @Query("SELECT * FROM render_jobs ORDER BY id DESC")
    fun observeJobs(): Flow<List<RenderJobEntity>>

    @Query("SELECT * FROM render_jobs WHERE status = 'QUEUED' ORDER BY id ASC LIMIT 1")
    suspend fun getNextQueuedJob(): RenderJobEntity?

    @Query("SELECT * FROM render_jobs WHERE id = :jobId LIMIT 1")
    suspend fun getJobById(jobId: Long): RenderJobEntity?

    @Query("SELECT * FROM render_jobs ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestJobOnce(): RenderJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: RenderJobEntity): Long

    @Query(
        "UPDATE render_jobs SET status = :status, progressPercent = :progress, statusMessage = :message, outputFilePath = :outputPath, updatedAt = :updatedAt WHERE id = :jobId"
    )
    suspend fun updateStatus(
        jobId: Long,
        status: String,
        progress: Int,
        message: String,
        outputPath: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun delete(job: RenderJobEntity)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeDownloads(): Flow<List<DownloadRecordEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY createdAt DESC")
    fun observeCompletedDownloads(): Flow<List<DownloadRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DownloadRecordEntity): Long

    @Query("UPDATE downloads SET status = :status, progressPercent = :progress, sizeBytes = :sizeBytes WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, progress: Int, sizeBytes: Long)

    @Query("UPDATE downloads SET savedToGallery = :saved WHERE id = :id")
    suspend fun markSavedToGallery(id: Long, saved: Boolean)

    @Delete
    suspend fun delete(record: DownloadRecordEntity)
}

@Dao
interface CustomVideoTemplateDao {
    @Query("SELECT * FROM custom_video_templates ORDER BY updatedAt DESC")
    fun observeTemplates(): Flow<List<CustomVideoTemplateEntity>>

    @Query("SELECT * FROM custom_video_templates ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<CustomVideoTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: CustomVideoTemplateEntity): Long

    @Update
    suspend fun update(template: CustomVideoTemplateEntity)

    @Query("DELETE FROM custom_video_templates WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CharacterProfileDao {
    @Query("SELECT * FROM character_profiles ORDER BY createdAt DESC")
    fun observeProfiles(): Flow<List<CharacterProfileEntity>>

    @Query("SELECT * FROM character_profiles ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<CharacterProfileEntity>

    @Query("SELECT COUNT(*) FROM character_profiles")
    suspend fun countProfiles(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: CharacterProfileEntity): Long

    @Update
    suspend fun update(profile: CharacterProfileEntity)

    @Delete
    suspend fun delete(profile: CharacterProfileEntity)
}

@Dao
interface RenderLogDao {
    @Query("SELECT * FROM render_logs ORDER BY completedAtEpochMs DESC LIMIT 50")
    fun observeRecentLogs(): Flow<List<RenderLogEntity>>

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM render_logs WHERE completedAtEpochMs >= :sinceEpochMs")
    fun observeTotalDurationSecondsSince(sinceEpochMs: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM render_logs WHERE completedAtEpochMs >= :sinceEpochMs")
    suspend fun getTotalDurationSecondsSinceOnce(sinceEpochMs: Long): Long

    @Query("SELECT COALESCE(SUM(durationSeconds), 0) FROM render_logs WHERE accountLabel = :accountLabel AND completedAtEpochMs >= :sinceEpochMs")
    suspend fun getAccountDurationSecondsSince(accountLabel: String, sinceEpochMs: Long): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: RenderLogEntity): Long
}

@Dao
interface ApiMetricDao {
    @Query("SELECT * FROM api_metrics ORDER BY timestamp DESC LIMIT 200")
    fun observeRecentMetrics(): Flow<List<ApiMetricEntity>>

    @Query("SELECT * FROM api_metrics ORDER BY timestamp DESC LIMIT 500")
    suspend fun getRecentMetricsOnce(): List<ApiMetricEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metric: ApiMetricEntity): Long

    @Query("DELETE FROM api_metrics")
    suspend fun clearAll()
}

@Dao
interface NewsTrackerDao {
    @Query("SELECT * FROM rss_feeds ORDER BY createdAt DESC")
    fun observeFeeds(): Flow<List<RssFeedEntity>>

    @Query("SELECT * FROM rss_feeds WHERE enabled = 1 ORDER BY createdAt DESC")
    suspend fun getEnabledFeedsOnce(): List<RssFeedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: RssFeedEntity): Long

    @Delete
    suspend fun deleteFeed(feed: RssFeedEntity)

    @Query("SELECT * FROM news_digests ORDER BY createdAt DESC LIMIT 25")
    fun observeDigests(): Flow<List<NewsDigestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDigest(digest: NewsDigestEntity): Long

    @Delete
    suspend fun deleteDigest(digest: NewsDigestEntity)
}

@Dao
interface KnowledgeBrainDao {
    @Query("SELECT * FROM user_memory_facts ORDER BY createdAt DESC")
    fun observeMemoryFacts(): Flow<List<UserMemoryFactEntity>>

    @Query("SELECT * FROM user_memory_facts WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    fun observeMemoryFactsForWorkspace(workspaceId: String): Flow<List<UserMemoryFactEntity>>

    @Query("SELECT * FROM user_memory_facts ORDER BY createdAt DESC")
    suspend fun getAllMemoryFactsOnce(): List<UserMemoryFactEntity>

    @Query("SELECT * FROM user_memory_facts WHERE enabled = 1 ORDER BY createdAt DESC")
    suspend fun getEnabledMemoryFactsOnce(): List<UserMemoryFactEntity>

    @Query("SELECT * FROM user_memory_facts WHERE enabled = 1 AND workspaceId = :workspaceId ORDER BY createdAt DESC")
    suspend fun getEnabledMemoryFactsForWorkspaceOnce(workspaceId: String): List<UserMemoryFactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoryFact(fact: UserMemoryFactEntity): Long

    @Delete
    suspend fun deleteMemoryFact(fact: UserMemoryFactEntity)

    @Query("SELECT * FROM slash_commands ORDER BY commandName ASC")
    fun observeSlashCommands(): Flow<List<SlashCommandEntity>>

    @Query("SELECT * FROM slash_commands ORDER BY commandName ASC")
    suspend fun getAllSlashCommandsOnce(): List<SlashCommandEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlashCommand(cmd: SlashCommandEntity): Long

    @Delete
    suspend fun deleteSlashCommand(cmd: SlashCommandEntity)

    @Query("SELECT * FROM offline_dataset_items ORDER BY createdAt DESC")
    fun observeDatasetItems(): Flow<List<OfflineDatasetItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDatasetItems(items: List<OfflineDatasetItemEntity>)

    @Delete
    suspend fun deleteDatasetItem(item: OfflineDatasetItemEntity)

    @Query("DELETE FROM offline_dataset_items WHERE packName = :packName")
    suspend fun deleteDatasetPack(packName: String)
}

@Dao
interface TerminalSnippetDao {
    @Query("SELECT * FROM terminal_snippets ORDER BY isBuiltIn DESC, createdAt DESC")
    fun observeSnippets(): Flow<List<TerminalSnippetEntity>>

    @Query("SELECT * FROM terminal_snippets ORDER BY isBuiltIn DESC, createdAt DESC")
    suspend fun getAllOnce(): List<TerminalSnippetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snippet: TerminalSnippetEntity): Long

    @Delete
    suspend fun delete(snippet: TerminalSnippetEntity)
}

// =========================================================================
// v3.5 VIDEO SCRIPTS, SCENES, REVISIONS & SOCIAL SEO DAOS
// =========================================================================

@Dao
interface VideoScriptDao {
    @Transaction
    @Query("SELECT * FROM video_scripts WHERE workspaceId = :workspaceId ORDER BY updatedAt DESC")
    fun observeScriptsWithRelationsForWorkspace(workspaceId: String): Flow<List<ScriptWithScenesAndSocial>>

    @Transaction
    @Query("SELECT * FROM video_scripts ORDER BY updatedAt DESC")
    fun observeAllScriptsWithRelations(): Flow<List<ScriptWithScenesAndSocial>>

    @Transaction
    @Query("SELECT * FROM video_scripts ORDER BY updatedAt DESC")
    suspend fun getAllScriptsWithRelationsOnce(): List<ScriptWithScenesAndSocial>

    @Transaction
    @Query("SELECT * FROM video_scripts WHERE id = :scriptId LIMIT 1")
    suspend fun getScriptWithRelationsById(scriptId: Long): ScriptWithScenesAndSocial?

    @Query("SELECT * FROM video_scripts WHERE id = :scriptId LIMIT 1")
    suspend fun getScriptById(scriptId: Long): VideoScriptEntity?

    @Query("UPDATE video_scripts SET thumbnailPath = :thumbnailPath, updatedAt = :updatedAt WHERE id = :scriptId")
    suspend fun updateThumbnailPath(scriptId: Long, thumbnailPath: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        SELECT DISTINCT s.* FROM video_scripts s
        LEFT JOIN video_scenes sc ON sc.scriptId = s.id
        WHERE s.title LIKE '%' || :query || '%'
           OR s.topic LIKE '%' || :query || '%'
           OR s.styleTag LIKE '%' || :query || '%'
           OR sc.visualPrompt LIKE '%' || :query || '%'
           OR sc.narration LIKE '%' || :query || '%'
        ORDER BY s.updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchScriptsAndScenes(query: String, limit: Int = 25): List<VideoScriptEntity>

    @Query("SELECT * FROM video_scenes WHERE scriptId = :scriptId ORDER BY sceneOrder ASC, id ASC")
    suspend fun getScenesForScriptOnce(scriptId: Long): List<SceneEntity>

    @Query("SELECT * FROM video_scenes WHERE isFavoriteScene = 1 ORDER BY id DESC")
    fun observeFavoriteScenes(): Flow<List<SceneEntity>>

    @Query("SELECT * FROM script_revisions WHERE scriptId = :scriptId ORDER BY savedAt DESC")
    suspend fun getRevisionsForScriptOnce(scriptId: Long): List<ScriptRevisionEntity>

    @Query("SELECT COUNT(*) FROM video_scripts")
    suspend fun countScripts(): Int

    @Query("SELECT COUNT(*) FROM video_scenes")
    suspend fun countScenes(): Int

    @Query("SELECT COUNT(*) FROM script_revisions")
    suspend fun countRevisions(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: VideoScriptEntity): Long

    @Update
    suspend fun updateScript(script: VideoScriptEntity)

    @Transaction
    @Query("SELECT * FROM video_scripts WHERE nicheId = :nicheId ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentScriptsForNicheOnce(nicheId: Long, limit: Int = 8): List<ScriptWithScenesAndSocial>

    @Query("UPDATE video_scripts SET status = :status, linkedVideoPath = :videoPath, updatedAt = :updatedAt WHERE id = :scriptId")
    suspend fun markScriptRendered(
        scriptId: Long,
        status: String = "RENDERED",
        videoPath: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE video_scripts SET status = :status, updatedAt = :updatedAt WHERE id = :scriptId")
    suspend fun updateScriptStatus(scriptId: Long, status: String, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteScript(script: VideoScriptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScene(scene: SceneEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScenes(scenes: List<SceneEntity>)

    @Update
    suspend fun updateScene(scene: SceneEntity)

    @Query("UPDATE video_scenes SET isFavoriteScene = :favorite WHERE id = :sceneId")
    suspend fun setSceneFavorite(sceneId: Long, favorite: Boolean)

    @Query("DELETE FROM video_scenes WHERE scriptId = :scriptId")
    suspend fun deleteScenesForScript(scriptId: Long)

    @Delete
    suspend fun deleteScene(scene: SceneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRevision(revision: ScriptRevisionEntity): Long
}

@Dao
interface SocialSeoDao {
    @Query("SELECT * FROM social_posts ORDER BY updatedAt DESC")
    fun observeAllPosts(): Flow<List<SocialPostEntity>>

    @Query("SELECT * FROM social_posts ORDER BY updatedAt DESC")
    suspend fun getAllPostsOnce(): List<SocialPostEntity>

    @Query("SELECT * FROM social_posts WHERE scriptId = :scriptId ORDER BY platform ASC")
    suspend fun getPostsForScriptOnce(scriptId: Long): List<SocialPostEntity>

    @Query("SELECT * FROM social_posts WHERE id = :postId LIMIT 1")
    suspend fun getPostById(postId: Long): SocialPostEntity?

    @Query("SELECT * FROM social_posts WHERE scriptId = :scriptId AND platform = :platform LIMIT 1")
    suspend fun getPostByScriptAndPlatform(scriptId: Long, platform: String): SocialPostEntity?

    @Query(
        """
        SELECT * FROM social_posts
        WHERE title LIKE '%' || :query || '%'
           OR hookLine LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%'
           OR hashtags LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchSocialPosts(query: String, limit: Int = 25): List<SocialPostEntity>

    @Query("SELECT COUNT(*) FROM social_posts")
    suspend fun countSocialPosts(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: SocialPostEntity): Long

    @Update
    suspend fun updatePost(post: SocialPostEntity)

    @Delete
    suspend fun deletePost(post: SocialPostEntity)

    @Query("SELECT * FROM hashtag_packs ORDER BY useCount DESC, packName ASC")
    fun observeHashtagPacks(): Flow<List<HashtagPackEntity>>

    @Query("SELECT * FROM hashtag_packs ORDER BY useCount DESC, packName ASC")
    suspend fun getAllHashtagPacksOnce(): List<HashtagPackEntity>

    @Query("SELECT COUNT(*) FROM hashtag_packs")
    suspend fun countHashtagPacks(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHashtagPack(pack: HashtagPackEntity): Long

    @Query("UPDATE hashtag_packs SET useCount = useCount + 1 WHERE id = :packId")
    suspend fun incrementPackUseCount(packId: Long)

    @Delete
    suspend fun deleteHashtagPack(pack: HashtagPackEntity)
}

// =========================================================================
// v4.0 NICHE CONTEXT, AUTO-NEXT CONTINUITY & SERIES PLANNER DAOS
// =========================================================================

@Dao
interface NicheDao {
    @Query("SELECT * FROM niches ORDER BY id ASC")
    fun observeNiches(): Flow<List<NicheEntity>>

    @Query("SELECT * FROM niches ORDER BY id ASC")
    suspend fun getAllNichesOnce(): List<NicheEntity>

    @Query("SELECT * FROM niches WHERE id = :nicheId LIMIT 1")
    suspend fun getNicheById(nicheId: Long): NicheEntity?

    @Query("SELECT * FROM niches WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' ORDER BY id ASC LIMIT 1")
    suspend fun findNicheByName(query: String): NicheEntity?

    @Query("SELECT COUNT(*) FROM niches")
    suspend fun countNiches(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(niche: NicheEntity): Long

    @Update
    suspend fun update(niche: NicheEntity)

    @Query("UPDATE niches SET rollingChatSummary = :summary, lastSummarizedMessageCount = :msgCount WHERE id = :nicheId")
    suspend fun updateRollingSummary(nicheId: Long, summary: String, msgCount: Int)

    @Query("UPDATE niches SET episodeCount = episodeCount + 1 WHERE id = :nicheId")
    suspend fun incrementEpisodeCount(nicheId: Long)

    @Query("UPDATE niches SET autoNextEnabled = :enabled WHERE id = :nicheId")
    suspend fun setAutoNextEnabled(nicheId: Long, enabled: Boolean)

    @Delete
    suspend fun delete(niche: NicheEntity)
}

@Dao
interface NextCommandDao {
    @Query("SELECT * FROM next_commands WHERE nicheId = :nicheId ORDER BY isUsed ASC, createdAt DESC LIMIT 20")
    fun observeCommandsForNiche(nicheId: Long): Flow<List<NextCommandEntity>>

    @Query("SELECT * FROM next_commands ORDER BY createdAt DESC")
    suspend fun getAllCommandsOnce(): List<NextCommandEntity>

    @Query("SELECT * FROM next_commands WHERE nicheId = :nicheId AND isUsed = 0 ORDER BY createdAt DESC LIMIT 10")
    suspend fun getUnusedCommandsForNicheOnce(nicheId: Long): List<NextCommandEntity>

    @Query("SELECT COUNT(*) FROM next_commands")
    suspend fun countCommands(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(command: NextCommandEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(commands: List<NextCommandEntity>)

    @Query("UPDATE next_commands SET isUsed = 1 WHERE id = :commandId")
    suspend fun markUsed(commandId: Long)

    @Query("DELETE FROM next_commands WHERE nicheId = :nicheId AND sourceType IN (:sourceTypes) AND isUsed = 0")
    suspend fun clearUnusedBySourceTypes(nicheId: Long, sourceTypes: List<String>)

    @Delete
    suspend fun delete(command: NextCommandEntity)
}

@Dao
interface SeriesEpisodeDao {
    @Query("SELECT * FROM series_episodes WHERE nicheId = :nicheId ORDER BY episodeNumber ASC, id ASC")
    fun observeEpisodesForNiche(nicheId: Long): Flow<List<SeriesEpisodeEntity>>

    @Query("SELECT * FROM series_episodes ORDER BY nicheId ASC, episodeNumber ASC")
    suspend fun getAllEpisodesOnce(): List<SeriesEpisodeEntity>

    @Query("SELECT * FROM series_episodes WHERE nicheId = :nicheId AND status = 'PLANNED' ORDER BY episodeNumber ASC LIMIT 1")
    suspend fun getNextPlannedEpisode(nicheId: Long): SeriesEpisodeEntity?

    @Query("SELECT COUNT(*) FROM series_episodes")
    suspend fun countEpisodes(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(episode: SeriesEpisodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(episodes: List<SeriesEpisodeEntity>)

    @Query("UPDATE series_episodes SET status = :status, linkedScriptId = :linkedScriptId WHERE id = :episodeId")
    suspend fun updateEpisodeStatus(episodeId: Long, status: String, linkedScriptId: Long? = null)

    @Query("UPDATE series_episodes SET status = :status WHERE id = :episodeId")
    suspend fun setEpisodeStatusOnly(episodeId: Long, status: String)

    @Query("DELETE FROM series_episodes WHERE nicheId = :nicheId")
    suspend fun clearEpisodesForNiche(nicheId: Long)

    @Delete
    suspend fun delete(episode: SeriesEpisodeEntity)
}

// =========================================================================
// v4.5 PROJECT MANAGEMENT SYSTEM & ATOMIC GIT SYNC DAO
// =========================================================================

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC, id DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC, id DESC")
    suspend fun getAllProjectsOnce(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE LOWER(slug) = LOWER(:slug) LIMIT 1")
    suspend fun getProjectBySlug(slug: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE LOWER(slug) LIKE '%' || LOWER(:query) || '%' OR LOWER(name) LIKE '%' || LOWER(:query) || '%' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findProjectBySlugOrName(query: String): ProjectEntity?

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun countProjects(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("UPDATE projects SET lastCommitSha = :commitSha, lastPushedAt = :pushedAt, updatedAt = :pushedAt WHERE id = :projectId")
    suspend fun updateLastCommit(projectId: Long, commitSha: String, pushedAt: Long = System.currentTimeMillis())

    @Query("UPDATE projects SET notes = :notes, updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun updateNotes(projectId: Long, notes: String, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    // --- Project Kanban Tasks ---
    @Query("SELECT * FROM project_tasks ORDER BY sortOrder ASC, updatedAt DESC")
    fun observeAllTasks(): Flow<List<ProjectTaskEntity>>

    @Query("SELECT * FROM project_tasks WHERE projectId = :projectId ORDER BY sortOrder ASC, updatedAt DESC")
    fun observeTasksForProject(projectId: Long): Flow<List<ProjectTaskEntity>>

    @Query("SELECT * FROM project_tasks ORDER BY projectId ASC, sortOrder ASC")
    suspend fun getAllTasksOnce(): List<ProjectTaskEntity>

    @Query("SELECT * FROM project_tasks WHERE projectId = :projectId ORDER BY sortOrder ASC, id ASC")
    suspend fun getTasksForProjectOnce(projectId: Long): List<ProjectTaskEntity>

    @Query("SELECT COUNT(*) FROM project_tasks")
    suspend fun countTasks(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: ProjectTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<ProjectTaskEntity>)

    @Update
    suspend fun updateTask(task: ProjectTaskEntity)

    @Query("UPDATE project_tasks SET columnStatus = :columnStatus, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun updateTaskColumn(taskId: Long, columnStatus: String, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteTask(task: ProjectTaskEntity)

    // --- Project File SHA-256 Index ---
    @Query("SELECT * FROM project_file_indexes WHERE projectId = :projectId ORDER BY relativePath ASC")
    fun observeFileIndexesForProject(projectId: Long): Flow<List<ProjectFileIndexEntity>>

    @Query("SELECT * FROM project_file_indexes WHERE projectId = :projectId ORDER BY relativePath ASC")
    suspend fun getFileIndexesForProjectOnce(projectId: Long): List<ProjectFileIndexEntity>

    @Query("SELECT * FROM project_file_indexes ORDER BY projectId ASC, relativePath ASC")
    suspend fun getAllFileIndexesOnce(): List<ProjectFileIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFileIndexes(indexes: List<ProjectFileIndexEntity>)

    @Query("DELETE FROM project_file_indexes WHERE projectId = :projectId")
    suspend fun clearFileIndexesForProject(projectId: Long)

    // --- Git Commit Logs ---
    @Query("SELECT * FROM git_commit_logs WHERE projectId = :projectId ORDER BY timestamp DESC LIMIT 50")
    fun observeCommitLogsForProject(projectId: Long): Flow<List<GitCommitLogEntity>>

    @Query("SELECT * FROM git_commit_logs ORDER BY timestamp DESC")
    suspend fun getAllCommitLogsOnce(): List<GitCommitLogEntity>

    @Query("SELECT COUNT(*) FROM git_commit_logs")
    suspend fun countCommitLogs(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommitLog(log: GitCommitLogEntity): Long
}

// =========================================================================
// v5.3 YOUTUBE DATA API v3 QUOTA TRACKING DAO
// =========================================================================

@Dao
interface YouTubeQuotaDao {
    @Query("SELECT COALESCE(SUM(unitsUsed), 0) FROM youtube_quota_logs WHERE dateKey = :dateKey")
    fun observeUnitsUsedForDate(dateKey: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(unitsUsed), 0) FROM youtube_quota_logs WHERE dateKey = :dateKey")
    suspend fun getUnitsUsedForDateOnce(dateKey: String): Int

    @Query("SELECT * FROM youtube_quota_logs ORDER BY createdAt DESC LIMIT 30")
    fun observeRecentLogs(): Flow<List<YouTubeQuotaEntity>>

    @Query("SELECT * FROM youtube_quota_logs ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<YouTubeQuotaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: YouTubeQuotaEntity): Long

    @Query("DELETE FROM youtube_quota_logs WHERE createdAt < :cutoffMs")
    suspend fun pruneOlderThan(cutoffMs: Long)
}


