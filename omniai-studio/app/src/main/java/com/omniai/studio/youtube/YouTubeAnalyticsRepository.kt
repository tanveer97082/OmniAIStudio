package com.omniai.studio.youtube

import android.content.Context
import com.omniai.studio.data.local.OmniAiDatabase
import com.omniai.studio.data.remote.YouTubeChannelStats
import com.omniai.studio.data.remote.YouTubeVideoPerformance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Closed-Loop YouTube Analytics & Quota Repository (v5.3).
 * - Tracks live Channel Subscribers, Total Views, and last 10 uploaded videos' performance.
 * - Tracks local daily YouTube Data API v3 quota usage (Used / 10,000 units) via Room.
 * - Feeds Top 3 highest-viewed & Bottom 3 lowest-viewed video titles into ContinuityEngine
 *   so Next-Video Auto-Typer suggestions prioritize proven high-retention topics.
 */
class YouTubeAnalyticsRepository(
    context: Context,
    private val apiClient: YouTubeApiClient,
    private val db: OmniAiDatabase
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _channelStats = MutableStateFlow<YouTubeChannelStats?>(loadCachedStats())
    val channelStats: StateFlow<YouTubeChannelStats?> = _channelStats.asStateFlow()

    private val _recentVideos = MutableStateFlow<List<YouTubeVideoPerformance>>(loadCachedVideos())
    val recentVideos: StateFlow<List<YouTubeVideoPerformance>> = _recentVideos.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError.asStateFlow()

    fun observeTodayQuotaUnits(): Flow<Int> =
        db.youtubeQuotaDao().observeUnitsUsedForDate(apiClient.todayDateKey())

    suspend fun getTodayQuotaUnitsOnce(): Int =
        db.youtubeQuotaDao().getUnitsUsedForDateOnce(apiClient.todayDateKey())

    suspend fun refresh(profileId: String? = null): Result<Pair<YouTubeChannelStats, List<YouTubeVideoPerformance>>> {
        _isRefreshing.value = true
        _lastError.value = ""
        return try {
            val result = apiClient.fetchChannelAndRecentVideos(profileId = profileId, maxVideos = 10)
            result.onSuccess { (stats, videos) ->
                _channelStats.value = stats
                _recentVideos.value = videos
                persistCache(stats, videos)
            }.onFailure { err ->
                _lastError.value = err.message ?: "Failed to refresh YouTube analytics."
            }
            result
        } finally {
            _isRefreshing.value = false
        }
    }

    /**
     * Builds the Closed-Loop AI Continuity Feedback block injected into ContinuityEngine (v4.0)
     * so next-video suggestions double down on winning topics and avoid low-performing angles.
     */
    fun buildContinuityFeedbackPrompt(): String {
        val videos = _recentVideos.value
        if (videos.isEmpty()) return ""
        val sortedByViews = videos.sortedByDescending { it.viewCount }
        val top3 = sortedByViews.take(3)
        val bottom3 = sortedByViews.takeLast(minOf(3, sortedByViews.size)).reversed()
        val channelName = _channelStats.value?.channelTitle?.ifBlank { "Active Channel" } ?: "Active Channel"

        return buildString {
            appendLine("YOUTUBE CLOSED-LOOP PERFORMANCE FEEDBACK ($channelName):")
            appendLine("- TOP PERFORMING VIDEOS (prioritize similar curiosity hooks, pacing & themes):")
            top3.forEachIndexed { idx, v ->
                appendLine("  ${idx + 1}. \"${v.title}\" (${v.viewCount} views, ${v.likeCount} likes, ${v.commentCount} comments)")
            }
            if (sortedByViews.size > 3) {
                appendLine("- LOWER PERFORMING VIDEOS (avoid repeating weak framing or slow angles):")
                bottom3.forEachIndexed { idx, v ->
                    appendLine("  ${idx + 1}. \"${v.title}\" (${v.viewCount} views)")
                }
            }
        }.trim()
    }

    private fun loadCachedStats(): YouTubeChannelStats? {
        val raw = prefs.getString(KEY_STATS_JSON, null) ?: return null
        return runCatching { json.decodeFromString<YouTubeChannelStats>(raw) }.getOrNull()
    }

    private fun loadCachedVideos(): List<YouTubeVideoPerformance> {
        val raw = prefs.getString(KEY_VIDEOS_JSON, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<YouTubeVideoPerformance>>(raw) }.getOrDefault(emptyList())
    }

    private fun persistCache(stats: YouTubeChannelStats, videos: List<YouTubeVideoPerformance>) {
        prefs.edit()
            .putString(KEY_STATS_JSON, json.encodeToString(stats))
            .putString(KEY_VIDEOS_JSON, json.encodeToString(videos))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "omniai_youtube_analytics_cache"
        private const val KEY_STATS_JSON = "channel_stats_json"
        private const val KEY_VIDEOS_JSON = "recent_videos_json"
    }
}
