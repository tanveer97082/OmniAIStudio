package com.omniai.studio.youtube

import com.omniai.studio.data.local.OmniAiDatabase
import com.omniai.studio.data.local.YouTubeQuotaEntity
import com.omniai.studio.data.remote.MetaReelsUploadResult
import com.omniai.studio.data.remote.YouTubeChannelProfile
import com.omniai.studio.data.remote.YouTubeChannelStats
import com.omniai.studio.data.remote.YouTubeCommentItem
import com.omniai.studio.data.remote.YouTubeUploadRequest
import com.omniai.studio.data.remote.YouTubeUploadResult
import com.omniai.studio.data.remote.YouTubeVideoPerformance
import com.omniai.studio.security.VaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Pure OkHttp3 + Kotlinx Serialization engine for YouTube Data API v3 and Meta Graph API Reels.
 * Zero heavy Google API Java Client JARs required.
 *
 * Security & Memory Guarantees:
 * - OAuth2 tokens and secrets are encrypted in Android Keystore via VaultManager and NEVER logged raw.
 * - Video uploads stream strictly in 2 MB chunks via RandomAccessFile (never loading entire MP4 into heap).
 * - Quota units are persisted locally in Room (`YouTubeQuotaEntity`) against the 10,000 daily free limit.
 */
class YouTubeApiClient(
    private val vault: VaultManager,
    private val db: OmniAiDatabase
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private data class CachedAccessToken(
        val token: String,
        val expiresAtMs: Long
    )

    private val tokenCache = ConcurrentHashMap<String, CachedAccessToken>()

    fun todayDateKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("America/Los_Angeles") // YouTube quota resets midnight PT
        return sdf.format(Date())
    }

    fun resolveProfile(profileId: String? = null): YouTubeChannelProfile? {
        return vault.getYouTubeProfileById(profileId)
    }

    fun clearTokenCache() {
        tokenCache.clear()
    }

    /**
     * Exchanges the stored YouTube OAuth2 refresh_token for a short-lived access_token via
     * POST https://oauth2.googleapis.com/token and caches it in memory until 60s before expiry.
     */
    suspend fun refreshAccessToken(
        profileId: String? = null,
        forceRefresh: Boolean = false
    ): Result<Pair<String, YouTubeChannelProfile>> = withContext(Dispatchers.IO) {
        val profile = resolveProfile(profileId)
            ?: return@withContext Result.failure(
                IllegalStateException("YouTube OAuth credentials not configured in API Vault.")
            )
        if (profile.clientId.isBlank() || profile.clientSecret.isBlank() || profile.refreshToken.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Incomplete YouTube credentials for '${profile.label}'. Add Client ID, Client Secret, and Refresh Token in Vault.")
            )
        }

        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            tokenCache[profile.id]?.let { cached ->
                if (cached.expiresAtMs - 60_000L > now) {
                    return@withContext Result.success(cached.token to profile)
                }
            }
        }

        runCatching {
            val formBody = FormBody.Builder()
                .add("client_id", profile.clientId)
                .add("client_secret", profile.clientSecret)
                .add("refresh_token", profile.refreshToken)
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url(OAUTH_TOKEN_URL)
                .post(formBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("YouTube OAuth token refresh failed (HTTP ${response.code}): ${sanitizeError(rawBody, profile)}")
                }
                val parsed = json.parseToJsonElement(rawBody).jsonObject
                val accessToken = parsed["access_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val expiresInSec = parsed["expires_in"]?.jsonPrimitive?.longOrNull ?: 3500L
                if (accessToken.isBlank()) {
                    error("YouTube OAuth response missing access_token.")
                }
                tokenCache[profile.id] = CachedAccessToken(
                    token = accessToken,
                    expiresAtMs = System.currentTimeMillis() + (expiresInSec * 1000L)
                )
                accessToken to profile
            }
        }
    }

    /**
     * Tests YouTube OAuth2 token refresh and retrieves the authenticated channel title & subscriber count.
     */
    suspend fun testConnection(profileId: String? = null): Result<String> {
        val statsResult = fetchChannelAndRecentVideos(profileId, maxVideos = 1)
        return statsResult.map { (stats, _) ->
            "Connected: ${stats.channelTitle} (${formatCompact(stats.subscriberCount)} subs · ${formatCompact(stats.totalViewCount)} views)"
        }
    }

    /**
     * Implements official YouTube Data API v3 Resumable Upload protocol:
     * 1. POST /upload/youtube/v3/videos?uploadType=resumable&part=snippet,status -> Location URL
     * 2. Streams MP4 file in 2 MB chunks with Content-Range header (zero OOM risk) emitting 0..100% progress
     * 3. Uploads custom thumbnail if provided (POST /upload/youtube/v3/thumbnails/set?videoId=...)
     * 4. Posts first comment if provided (POST /youtube/v3/commentThreads?part=snippet)
     * 5. Updates Room SocialPostEntity + VideoScriptEntity and logs 1,650 daily quota units.
     */
    suspend fun uploadVideoResumable(
        uploadRequest: YouTubeUploadRequest,
        onProgress: (percent: Int, statusText: String) -> Unit = { _, _ -> }
    ): Result<YouTubeUploadResult> = withContext(Dispatchers.IO) {
        val videoFile = File(uploadRequest.videoFilePath)
        if (!videoFile.exists() || !videoFile.isFile || videoFile.length() <= 0L) {
            return@withContext Result.failure(
                IllegalArgumentException("Video file not found or empty: ${uploadRequest.videoFilePath}")
            )
        }

        val tokenPair = refreshAccessToken(uploadRequest.profileId.ifBlank { null }).getOrElse {
            return@withContext Result.failure(it)
        }
        val (accessToken, profile) = tokenPair

        runCatching {
            onProgress(2, "Initializing YouTube resumable session (${profile.label})...")

            val cleanedTitle = uploadRequest.title.trim().ifBlank { videoFile.nameWithoutExtension }.take(100)
            val cleanedTags = uploadRequest.tags.map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.take(30)
            val effectivePrivacy = if (uploadRequest.publishAtIso.isNotBlank()) {
                "private" // YouTube requires privacyStatus = "private" when scheduling publishAt
            } else {
                uploadRequest.privacyStatus.lowercase(Locale.US).let {
                    if (it in setOf("private", "unlisted", "public")) it else "private"
                }
            }

            val metadataJson = buildJsonObject {
                put("snippet", buildJsonObject {
                    put("title", cleanedTitle)
                    put("description", uploadRequest.description.take(4950))
                    put("tags", buildJsonArray { cleanedTags.forEach { add(JsonPrimitive(it)) } })
                    put("categoryId", uploadRequest.categoryId.ifBlank { "22" })
                })
                put("status", buildJsonObject {
                    put("privacyStatus", effectivePrivacy)
                    put("selfDeclaredMadeForKids", false)
                    if (uploadRequest.publishAtIso.isNotBlank()) {
                        put("publishAt", uploadRequest.publishAtIso.trim())
                    }
                })
            }.toString()

            val totalBytes = videoFile.length()
            val initRequest = Request.Builder()
                .url("$YT_UPLOAD_BASE/videos?uploadType=resumable&part=snippet,status")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("X-Upload-Content-Length", totalBytes.toString())
                .addHeader("X-Upload-Content-Type", "video/mp4")
                .post(metadataJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val uploadSessionUrl = httpClient.newCall(initRequest).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    error("Resumable upload init failed (HTTP ${resp.code}): ${sanitizeError(err, profile)}")
                }
                resp.header("Location")?.trim()
                    ?: error("YouTube did not return a Location header for resumable upload.")
            }

            // Step 2: Stream MP4 strictly in 2 MB chunks via RandomAccessFile
            val buffer = ByteArray(CHUNK_SIZE_BYTES)
            var offset = 0L
            var uploadedVideoId = ""

            RandomAccessFile(videoFile, "r").use { raf ->
                while (offset < totalBytes) {
                    raf.seek(offset)
                    val remaining = totalBytes - offset
                    val bytesToRead = minOf(CHUNK_SIZE_BYTES.toLong(), remaining).toInt()
                    val bytesRead = raf.read(buffer, 0, bytesToRead)
                    if (bytesRead <= 0) break

                    val rangeEnd = offset + bytesRead - 1L
                    val chunkBody = buffer.toRequestBody(VIDEO_MP4_MEDIA_TYPE, 0, bytesRead)
                    val chunkRequest = Request.Builder()
                        .url(uploadSessionUrl)
                        .addHeader("Authorization", "Bearer $accessToken")
                        .addHeader("Content-Range", "bytes $offset-$rangeEnd/$totalBytes")
                        .put(chunkBody)
                        .build()

                    val chunkOutcome = httpClient.newCall(chunkRequest).execute().use { chunkResp ->
                        val code = chunkResp.code
                        val bodyStr = chunkResp.body?.string().orEmpty()
                        when {
                            code == 308 -> {
                                // Resume Incomplete: advance offset using Range header if provided
                                val serverRange = chunkResp.header("Range")
                                val parsedNext = parseNextOffsetFromRange(serverRange)
                                Pair(parsedNext ?: (offset + bytesRead), "")
                            }
                            code in 200..299 -> {
                                val parsedObj = runCatching { json.parseToJsonElement(bodyStr).jsonObject }.getOrNull()
                                val vid = parsedObj?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty()
                                Pair(totalBytes, vid)
                            }
                            else -> {
                                error("Chunk upload failed at byte $offset (HTTP $code): ${sanitizeError(bodyStr, profile)}")
                            }
                        }
                    }

                    offset = chunkOutcome.first
                    if (chunkOutcome.second.isNotBlank()) {
                        uploadedVideoId = chunkOutcome.second
                    }
                    val pct = ((offset * 86L) / totalBytes).toInt().coerceIn(5, 88)
                    val mbDone = String.format(Locale.US, "%.1f", offset / (1024.0 * 1024.0))
                    val mbTotal = String.format(Locale.US, "%.1f", totalBytes / (1024.0 * 1024.0))
                    onProgress(pct, "Uploading to YouTube: $mbDone / $mbTotal MB ($pct%)")
                }
            }

            if (uploadedVideoId.isBlank()) {
                error("Upload stream finished but YouTube did not return a video ID.")
            }

            var totalQuotaLogged = QUOTA_VIDEO_UPLOAD_UNITS

            // Step 3: Upload Custom Thumbnail if provided
            var thumbnailUploaded = false
            val thumbPath = uploadRequest.thumbnailFilePath?.trim().orEmpty()
            if (thumbPath.isNotBlank()) {
                val thumbFile = File(thumbPath)
                if (thumbFile.exists() && thumbFile.isFile && thumbFile.length() > 0L) {
                    onProgress(91, "Uploading custom thumbnail...")
                    thumbnailUploaded = uploadThumbnailInternal(uploadedVideoId, thumbFile, accessToken)
                    if (thumbnailUploaded) {
                        totalQuotaLogged += QUOTA_THUMBNAIL_UNITS
                    }
                }
            }

            // Step 4: Post First / Pinned Comment if provided
            var firstCommentPosted = false
            val firstComment = uploadRequest.firstComment.trim()
            if (firstComment.isNotBlank()) {
                onProgress(95, "Posting first comment...")
                firstCommentPosted = postFirstCommentInternal(uploadedVideoId, firstComment, accessToken)
                if (firstCommentPosted) {
                    totalQuotaLogged += QUOTA_COMMENT_UNITS
                }
            }

            // Step 5: Persist Room status & quota entry (standard 1,650 base unit accounting for upload flow)
            val watchUrl = "https://youtu.be/$uploadedVideoId"
            val effectiveQuotaUnits = maxOf(QUOTA_UPLOAD_PIPELINE_UNITS, totalQuotaLogged)
            logQuotaUnits(
                units = effectiveQuotaUnits,
                actionLabel = "VIDEO_UPLOAD",
                videoId = uploadedVideoId,
                channelLabel = profile.label
            )

            // Update matching SocialPostEntity and VideoScriptEntity
            val targetPost = when {
                uploadRequest.socialPostId != null -> db.socialSeoDao().getPostById(uploadRequest.socialPostId)
                uploadRequest.scriptId != null -> db.socialSeoDao().getPostByScriptAndPlatform(uploadRequest.scriptId, "YOUTUBE")
                else -> null
            }
            if (targetPost != null) {
                db.socialSeoDao().updatePost(
                    targetPost.copy(
                        publishStatus = "POSTED",
                        publishedUrl = watchUrl,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            if (uploadRequest.scriptId != null) {
                db.videoScriptDao().updateScriptStatus(uploadRequest.scriptId, "PUBLISHED")
            }

            onProgress(100, "Uploaded to YouTube: $watchUrl")
            YouTubeUploadResult(
                videoId = uploadedVideoId,
                watchUrl = watchUrl,
                thumbnailUploaded = thumbnailUploaded,
                firstCommentPosted = firstCommentPosted,
                quotaUnitsLogged = effectiveQuotaUnits,
                channelLabel = profile.label
            )
        }
    }

    private fun uploadThumbnailInternal(videoId: String, thumbFile: File, accessToken: String): Boolean {
        val mediaType = if (thumbFile.extension.equals("png", ignoreCase = true)) {
            "image/png".toMediaType()
        } else {
            "image/jpeg".toMediaType()
        }
        val bytes = thumbFile.readBytes() // Thumbnails are <= 2 MB JPEG/PNG
        val request = Request.Builder()
            .url("$YT_UPLOAD_BASE/thumbnails/set?videoId=${URLEncoder.encode(videoId, "UTF-8")}")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(bytes.toRequestBody(mediaType))
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun postFirstCommentInternal(videoId: String, commentText: String, accessToken: String): Boolean {
        val bodyJson = buildJsonObject {
            put("snippet", buildJsonObject {
                put("videoId", videoId)
                put("topLevelComment", buildJsonObject {
                    put("snippet", buildJsonObject {
                        put("textOriginal", commentText.take(2000))
                    })
                })
            })
        }.toString()
        val request = Request.Builder()
            .url("$YT_API_BASE/commentThreads?part=snippet")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /**
     * Fetches channel statistics (`channels?part=snippet,statistics,contentDetails&mine=true`)
     * and the last N uploaded videos' views, likes, and comment counts.
     */
    suspend fun fetchChannelAndRecentVideos(
        profileId: String? = null,
        maxVideos: Int = 10
    ): Result<Pair<YouTubeChannelStats, List<YouTubeVideoPerformance>>> = withContext(Dispatchers.IO) {
        val (accessToken, profile) = refreshAccessToken(profileId).getOrElse {
            return@withContext Result.failure(it)
        }

        runCatching {
            val channelReq = Request.Builder()
                .url("$YT_API_BASE/channels?part=snippet,statistics,contentDetails&mine=true")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val (stats, uploadsPlaylistId) = httpClient.newCall(channelReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("YouTube channels fetch failed (HTTP ${resp.code}): ${sanitizeError(raw, profile)}")
                }
                val root = json.parseToJsonElement(raw).jsonObject
                val items = root["items"]?.jsonArray ?: JsonArray(emptyList())
                val first = items.firstOrNull()?.jsonObject
                    ?: error("No YouTube channel found for this OAuth account.")

                val channelId = first["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val snippet = first["snippet"]?.jsonObject
                val statistics = first["statistics"]?.jsonObject
                val contentDetails = first["contentDetails"]?.jsonObject
                val uploadsId = contentDetails
                    ?.get("relatedPlaylists")?.jsonObject
                    ?.get("uploads")?.jsonPrimitive?.contentOrNull.orEmpty()

                val channelStats = YouTubeChannelStats(
                    channelId = channelId,
                    channelTitle = snippet?.get("title")?.jsonPrimitive?.contentOrNull ?: profile.label,
                    subscriberCount = statistics?.get("subscriberCount")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                    totalViewCount = statistics?.get("viewCount")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                    videoCount = statistics?.get("videoCount")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                    fetchedAtMs = System.currentTimeMillis()
                )
                channelStats to uploadsId
            }

            var unitsSpent = 1
            val videos = mutableListOf<YouTubeVideoPerformance>()

            if (uploadsPlaylistId.isNotBlank() && maxVideos > 0) {
                val plReq = Request.Builder()
                    .url("$YT_API_BASE/playlistItems?part=contentDetails&playlistId=${URLEncoder.encode(uploadsPlaylistId, "UTF-8")}&maxResults=${maxVideos.coerceIn(1, 25)}")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                val videoIds = httpClient.newCall(plReq).execute().use { resp ->
                    if (!resp.isSuccessful) emptyList()
                    else {
                        val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                        val items = root["items"]?.jsonArray ?: JsonArray(emptyList())
                        items.mapNotNull { el ->
                            el.jsonObject["contentDetails"]?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
                        }
                    }
                }
                unitsSpent += 1

                if (videoIds.isNotEmpty()) {
                    val joinedIds = videoIds.joinToString(",")
                    val vidReq = Request.Builder()
                        .url("$YT_API_BASE/videos?part=snippet,statistics&id=${URLEncoder.encode(joinedIds, "UTF-8")}")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .get()
                        .build()

                    httpClient.newCall(vidReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                            val items = root["items"]?.jsonArray ?: JsonArray(emptyList())
                            items.forEach { el ->
                                val obj = el.jsonObject
                                val vid = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                val sn = obj["snippet"]?.jsonObject
                                val st = obj["statistics"]?.jsonObject
                                val thumb = sn?.get("thumbnails")?.jsonObject
                                    ?.get("medium")?.jsonObject
                                    ?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
                                videos.add(
                                    YouTubeVideoPerformance(
                                        videoId = vid,
                                        title = sn?.get("title")?.jsonPrimitive?.contentOrNull ?: "Untitled Video",
                                        viewCount = st?.get("viewCount")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                                        likeCount = st?.get("likeCount")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                                        commentCount = st?.get("commentCount")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                                        publishedAt = sn?.get("publishedAt")?.jsonPrimitive?.contentOrNull.orEmpty(),
                                        thumbnailUrl = thumb
                                    )
                                )
                            }
                        }
                    }
                    unitsSpent += 1
                }
            }

            logQuotaUnits(
                units = unitsSpent,
                actionLabel = "ANALYTICS_READ",
                videoId = stats.channelId,
                channelLabel = profile.label
            )

            stats to videos
        }
    }

    /**
     * Fetches latest comment threads for a video via GET /youtube/v3/commentThreads?part=snippet&videoId={id}.
     */
    suspend fun fetchComments(
        videoIdOrUrl: String,
        onlyUnanswered: Boolean = false,
        profileId: String? = null
    ): Result<List<YouTubeCommentItem>> = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(videoIdOrUrl)
        if (videoId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid YouTube Video ID or URL."))
        }

        val (accessToken, profile) = refreshAccessToken(profileId).getOrElse {
            return@withContext Result.failure(it)
        }

        runCatching {
            val req = Request.Builder()
                .url("$YT_API_BASE/commentThreads?part=snippet&videoId=${URLEncoder.encode(videoId, "UTF-8")}&maxResults=25&order=time&textFormat=plainText")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val comments = httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("Failed to fetch comments (HTTP ${resp.code}): ${sanitizeError(raw, profile)}")
                }
                val root = json.parseToJsonElement(raw).jsonObject
                val items = root["items"]?.jsonArray ?: JsonArray(emptyList())
                items.mapNotNull { el ->
                    val threadObj = el.jsonObject
                    val threadId = threadObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val snippet = threadObj["snippet"]?.jsonObject ?: return@mapNotNull null
                    val totalReplyCount = snippet["totalReplyCount"]?.jsonPrimitive?.intOrNull ?: 0
                    val topObj = snippet["topLevelComment"]?.jsonObject ?: return@mapNotNull null
                    val topId = topObj["id"]?.jsonPrimitive?.contentOrNull ?: threadId
                    val topSn = topObj["snippet"]?.jsonObject ?: return@mapNotNull null

                    YouTubeCommentItem(
                        commentThreadId = threadId,
                        topLevelCommentId = topId,
                        videoId = videoId,
                        authorDisplayName = topSn["authorDisplayName"]?.jsonPrimitive?.contentOrNull ?: "Viewer",
                        textDisplay = topSn["textDisplay"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        likeCount = topSn["likeCount"]?.jsonPrimitive?.intOrNull ?: 0,
                        publishedAt = topSn["publishedAt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        replyCount = totalReplyCount
                    )
                }
            }

            logQuotaUnits(
                units = 1,
                actionLabel = "COMMENTS_READ",
                videoId = videoId,
                channelLabel = profile.label
            )

            if (onlyUnanswered) comments.filter { it.replyCount == 0 } else comments
        }
    }

    /**
     * Posts a reply to a top-level comment via POST /youtube/v3/comments?part=snippet.
     */
    suspend fun postCommentReply(
        parentCommentId: String,
        replyText: String,
        videoId: String = "",
        profileId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanReply = replyText.trim()
        if (cleanReply.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Reply text cannot be empty."))
        }
        val (accessToken, profile) = refreshAccessToken(profileId).getOrElse {
            return@withContext Result.failure(it)
        }

        runCatching {
            val payload = buildJsonObject {
                put("snippet", buildJsonObject {
                    put("parentId", parentCommentId)
                    put("textOriginal", cleanReply.take(2000))
                })
            }.toString()

            val req = Request.Builder()
                .url("$YT_API_BASE/comments?part=snippet")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val replyId = httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("Comment reply failed (HTTP ${resp.code}): ${sanitizeError(raw, profile)}")
                }
                val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
                parsed?.get("id")?.jsonPrimitive?.contentOrNull ?: "posted"
            }

            logQuotaUnits(
                units = QUOTA_COMMENT_UNITS,
                actionLabel = "COMMENT_REPLY",
                videoId = videoId,
                channelLabel = profile.label
            )
            replyId
        }
    }

    /**
     * Optional on-device Meta Graph API Facebook Page Reels upload (3-phase rupload protocol).
     */
    suspend fun publishFacebookPageReel(
        videoFile: File,
        description: String
    ): Result<MetaReelsUploadResult> = withContext(Dispatchers.IO) {
        val pageId = vault.metaPageId.trim()
        val token = vault.metaPageAccessToken.trim()
        if (pageId.isBlank() || token.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Meta Page ID or Page Access Token missing in API Vault.")
            )
        }
        if (!videoFile.exists() || videoFile.length() <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("Video file missing: ${videoFile.path}"))
        }

        runCatching {
            // Phase 1: Start Reel upload session
            val startForm = FormBody.Builder()
                .add("upload_phase", "start")
                .add("access_token", token)
                .build()
            val startReq = Request.Builder()
                .url("$META_GRAPH_BASE/$pageId/video_reels")
                .post(startForm)
                .build()
            val (videoId, uploadUrl) = httpClient.newCall(startReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Meta FB Reels start failed (HTTP ${resp.code}): ${maskRawToken(raw, token)}")
                val obj = json.parseToJsonElement(raw).jsonObject
                val vid = obj["video_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val url = obj["upload_url"]?.jsonPrimitive?.contentOrNull
                    ?: "https://rupload.facebook.com/video-upload/v19.0/$vid"
                vid to url
            }

            // Phase 2: Stream file bytes to rupload.facebook.com
            val fileLength = videoFile.length()
            val streamingBody = object : okhttp3.RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = fileLength
                override fun writeTo(sink: okio.BufferedSink) {
                    val buf = ByteArray(256 * 1024)
                    videoFile.inputStream().use { input ->
                        var read = input.read(buf)
                        while (read >= 0) {
                            sink.write(buf, 0, read)
                            read = input.read(buf)
                        }
                    }
                }
            }
            val uploadReq = Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", "OAuth $token")
                .addHeader("offset", "0")
                .addHeader("file_size", fileLength.toString())
                .post(streamingBody)
                .build()
            httpClient.newCall(uploadReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Meta FB Reels binary upload failed (HTTP ${resp.code}): ${maskRawToken(raw, token)}")
            }

            // Phase 3: Finish & publish
            val finishForm = FormBody.Builder()
                .add("upload_phase", "finish")
                .add("video_id", videoId)
                .add("video_state", "PUBLISHED")
                .add("description", description.take(2200))
                .add("access_token", token)
                .build()
            val finishReq = Request.Builder()
                .url("$META_GRAPH_BASE/$pageId/video_reels")
                .post(finishForm)
                .build()
            httpClient.newCall(finishReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Meta FB Reels finish failed (HTTP ${resp.code}): ${maskRawToken(raw, token)}")
            }

            MetaReelsUploadResult(
                platform = "FACEBOOK_REELS",
                postId = videoId,
                statusMessage = "Published to Facebook Page Reels (ID: $videoId)"
            )
        }
    }

    /**
     * Optional Meta Graph API Instagram Reels container create + publish (requires public video URL).
     */
    suspend fun publishInstagramReel(
        publicVideoUrl: String,
        caption: String
    ): Result<MetaReelsUploadResult> = withContext(Dispatchers.IO) {
        val igUserId = vault.metaIgUserId.trim()
        val token = vault.metaPageAccessToken.trim()
        if (igUserId.isBlank() || token.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Meta IG User ID or Page Access Token missing in API Vault.")
            )
        }
        if (publicVideoUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Instagram Reels Graph API requires a publicly reachable MP4 URL.")
            )
        }

        runCatching {
            val createForm = FormBody.Builder()
                .add("media_type", "REELS")
                .add("video_url", publicVideoUrl.trim())
                .add("caption", caption.take(2200))
                .add("access_token", token)
                .build()
            val createReq = Request.Builder()
                .url("$META_GRAPH_BASE/$igUserId/media")
                .post(createForm)
                .build()
            val creationId = httpClient.newCall(createReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("IG Reel container creation failed (HTTP ${resp.code}): ${maskRawToken(raw, token)}")
                json.parseToJsonElement(raw).jsonObject["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            }

            val publishForm = FormBody.Builder()
                .add("creation_id", creationId)
                .add("access_token", token)
                .build()
            val publishReq = Request.Builder()
                .url("$META_GRAPH_BASE/$igUserId/media_publish")
                .post(publishForm)
                .build()
            val mediaId = httpClient.newCall(publishReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("IG Reel publish failed (HTTP ${resp.code}): ${maskRawToken(raw, token)}")
                json.parseToJsonElement(raw).jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: creationId
            }

            MetaReelsUploadResult(
                platform = "INSTAGRAM_REELS",
                postId = mediaId,
                statusMessage = "Published Instagram Reel (ID: $mediaId)"
            )
        }
    }

    private suspend fun logQuotaUnits(
        units: Int,
        actionLabel: String,
        videoId: String,
        channelLabel: String
    ) {
        runCatching {
            db.youtubeQuotaDao().insert(
                YouTubeQuotaEntity(
                    dateKey = todayDateKey(),
                    unitsUsed = units,
                    actionLabel = actionLabel,
                    videoId = videoId,
                    channelLabel = channelLabel
                )
            )
        }
    }

    private fun parseNextOffsetFromRange(rangeHeader: String?): Long? {
        if (rangeHeader.isNullOrBlank()) return null
        // Format: "bytes=0-2097151"
        val dashIdx = rangeHeader.lastIndexOf('-')
        if (dashIdx < 0) return null
        val endByte = rangeHeader.substring(dashIdx + 1).trim().toLongOrNull() ?: return null
        return endByte + 1L
    }

    private fun sanitizeError(raw: String, profile: YouTubeChannelProfile): String {
        var safe = raw.take(360)
        listOf(profile.clientSecret, profile.refreshToken, profile.clientId)
            .filter { it.length >= 6 }
            .forEach { secret -> safe = safe.replace(secret, "[REDACTED]") }
        return safe.ifBlank { "Unknown API error" }
    }

    private fun maskRawToken(raw: String, token: String): String {
        val safe = if (token.length >= 6) raw.replace(token, "[REDACTED]") else raw
        return safe.take(360).ifBlank { "Unknown Meta API error" }
    }

    companion object {
        private const val OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val YT_API_BASE = "https://www.googleapis.com/youtube/v3"
        private const val YT_UPLOAD_BASE = "https://www.googleapis.com/upload/youtube/v3"
        private const val META_GRAPH_BASE = "https://graph.facebook.com/v19.0"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val VIDEO_MP4_MEDIA_TYPE = "video/mp4".toMediaType()

        const val CHUNK_SIZE_BYTES = 2 * 1024 * 1024 // 2 MB chunked streaming
        const val DAILY_FREE_QUOTA_LIMIT = 10_000
        const val QUOTA_VIDEO_UPLOAD_UNITS = 1600
        const val QUOTA_THUMBNAIL_UNITS = 50
        const val QUOTA_COMMENT_UNITS = 50
        const val QUOTA_UPLOAD_PIPELINE_UNITS = 1650

        fun extractVideoId(input: String): String {
            val trimmed = input.trim()
            if (trimmed.isBlank()) return ""
            val shortRegex = Regex("""youtu\.be/([A-Za-z0-9_-]{11})""")
            shortRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
            val watchRegex = Regex("""[?&]v=([A-Za-z0-9_-]{11})""")
            watchRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
            val shortsRegex = Regex("""/shorts/([A-Za-z0-9_-]{11})""")
            shortsRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
            return trimmed.take(32)
        }

        fun formatCompact(value: Long): String = when {
            value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
            value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
            else -> value.toString()
        }
    }
}
