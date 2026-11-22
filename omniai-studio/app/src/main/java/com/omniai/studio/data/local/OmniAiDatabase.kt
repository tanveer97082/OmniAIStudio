package com.omniai.studio.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WorkspaceProfileEntity::class,
        ChatThreadEntity::class,
        ChatMessageEntity::class,
        PromptPresetEntity::class,
        PromptChainEntity::class,
        StoryboardEntity::class,
        RenderJobEntity::class,
        DownloadRecordEntity::class,
        CustomVideoTemplateEntity::class,
        CharacterProfileEntity::class,
        RenderLogEntity::class,
        ApiMetricEntity::class,
        RssFeedEntity::class,
        NewsDigestEntity::class,
        UserMemoryFactEntity::class,
        SlashCommandEntity::class,
        OfflineDatasetItemEntity::class,
        TerminalSnippetEntity::class,
        VideoScriptEntity::class,
        SceneEntity::class,
        ScriptRevisionEntity::class,
        SocialPostEntity::class,
        HashtagPackEntity::class,
        NicheEntity::class,
        NextCommandEntity::class,
        SeriesEpisodeEntity::class,
        ProjectEntity::class,
        ProjectTaskEntity::class,
        ProjectFileIndexEntity::class,
        GitCommitLogEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class OmniAiDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun chatDao(): ChatDao
    abstract fun promptPresetDao(): PromptPresetDao
    abstract fun promptChainDao(): PromptChainDao
    abstract fun storyboardDao(): StoryboardDao
    abstract fun renderJobDao(): RenderJobDao
    abstract fun downloadDao(): DownloadDao
    abstract fun customVideoTemplateDao(): CustomVideoTemplateDao
    abstract fun characterProfileDao(): CharacterProfileDao
    abstract fun renderLogDao(): RenderLogDao
    abstract fun apiMetricDao(): ApiMetricDao
    abstract fun newsTrackerDao(): NewsTrackerDao
    abstract fun knowledgeBrainDao(): KnowledgeBrainDao
    abstract fun terminalSnippetDao(): TerminalSnippetDao
    abstract fun videoScriptDao(): VideoScriptDao
    abstract fun socialSeoDao(): SocialSeoDao
    abstract fun nicheDao(): NicheDao
    abstract fun nextCommandDao(): NextCommandDao
    abstract fun seriesEpisodeDao(): SeriesEpisodeDao
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var INSTANCE: OmniAiDatabase? = null

        fun getInstance(context: Context): OmniAiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OmniAiDatabase::class.java,
                    "omniai_studio_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

typealias AppDatabase = OmniAiDatabase
