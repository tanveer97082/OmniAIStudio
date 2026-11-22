package com.omniai.studio.data.local

import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ContentCsvExportResult(
    val filePath: String,
    val rowCount: Int,
    val byteSize: Long
)

@Serializable
data class ChatThreadJsonExport(
    val threadId: Long,
    val title: String,
    val workspaceId: String,
    val folderName: String,
    val tagsCsv: String,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val providerUsed: String,
    val systemPrompt: String,
    val rollingSummary: String,
    val exportedAtEpochMs: Long,
    val messages: List<ChatMessageJsonExport>
)

@Serializable
data class ChatMessageJsonExport(
    val role: String,
    val content: String,
    val providerTag: String,
    val modelName: String,
    val isBookmarked: Boolean,
    val tokenEstimate: Int,
    val createdAt: Long
)

/**
 * 1-Tap RFC-4180 CSV (Google Sheets / Excel) & Markdown Bulk Exporter for Video Scripts,
 * Scenes, and YouTube / Instagram / Facebook Social SEO metadata, plus single-chat exporters.
 */
class ContentDbExporter(
    private val context: Context,
    private val db: OmniAiDatabase
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun buildContentCsv(): Pair<String, Int> = withContext(Dispatchers.IO) {
        val scriptsWithAll = db.videoScriptDao().getAllScriptsWithRelationsOnce()
        val allPosts = db.socialSeoDao().getAllPostsOnce()
        val standalonePosts = allPosts.filter { it.scriptId == null }

        val sb = StringBuilder()
        // UTF-8 BOM helps Microsoft Excel recognize Unicode/Hindi/emoji characters cleanly
        sb.append('\uFEFF')
        sb.appendLine(
            listOf(
                "Record_Type",
                "Script_ID",
                "Workspace",
                "Script_Title",
                "Topic",
                "Status",
                "Aspect_Ratio",
                "Voice_ID",
                "Style_Tag",
                "Scene_Count",
                "Total_Duration_Sec",
                "YT_Title",
                "YT_Description",
                "YT_Hashtags",
                "YT_Status",
                "YT_URL",
                "IG_Title",
                "IG_Caption",
                "IG_Hashtags",
                "IG_Status",
                "IG_URL",
                "FB_Title",
                "FB_Description",
                "FB_Hashtags",
                "FB_Status",
                "FB_URL",
                "Updated_At_UTC"
            ).joinToString(",") { escapeCsvCell(it) }
        )

        val isoFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        var rowCount = 0

        scriptsWithAll.forEach { item ->
            val s = item.script
            val orderedScenes = item.scenes.sortedBy { it.sceneOrder }
            val totalDuration = orderedScenes.sumOf { it.durationSec }
            val yt = item.socialPosts.firstOrNull { it.platform.equals("YOUTUBE", ignoreCase = true) }
            val ig = item.socialPosts.firstOrNull { it.platform.equals("INSTAGRAM", ignoreCase = true) }
            val fb = item.socialPosts.firstOrNull { it.platform.equals("FACEBOOK", ignoreCase = true) }

            val row = listOf(
                "VIDEO_SCRIPT",
                s.id.toString(),
                s.workspaceId,
                s.title,
                s.topic,
                s.status,
                s.aspectRatio,
                s.voiceId,
                s.styleTag,
                orderedScenes.size.toString(),
                totalDuration.toString(),
                yt?.title.orEmpty(),
                yt?.description.orEmpty(),
                yt?.hashtags.orEmpty(),
                yt?.publishStatus.orEmpty(),
                yt?.publishedUrl.orEmpty(),
                ig?.title.orEmpty(),
                ig?.description.orEmpty(),
                ig?.hashtags.orEmpty(),
                ig?.publishStatus.orEmpty(),
                ig?.publishedUrl.orEmpty(),
                fb?.title.orEmpty(),
                fb?.description.orEmpty(),
                fb?.hashtags.orEmpty(),
                fb?.publishStatus.orEmpty(),
                fb?.publishedUrl.orEmpty(),
                isoFmt.format(Date(s.updatedAt))
            )
            sb.appendLine(row.joinToString(",") { escapeCsvCell(it) })
            rowCount++
        }

        standalonePosts.forEach { post ->
            val isYt = post.platform.equals("YOUTUBE", ignoreCase = true)
            val isIg = post.platform.equals("INSTAGRAM", ignoreCase = true)
            val isFb = post.platform.equals("FACEBOOK", ignoreCase = true)

            val row = listOf(
                "STANDALONE_${post.platform.uppercase(Locale.US)}",
                "",
                "default",
                post.title,
                post.hookLine,
                post.publishStatus,
                "",
                "",
                "",
                "0",
                "0",
                if (isYt) post.title else "",
                if (isYt) post.description else "",
                if (isYt) post.hashtags else "",
                if (isYt) post.publishStatus else "",
                if (isYt) post.publishedUrl else "",
                if (isIg) post.title else "",
                if (isIg) post.description else "",
                if (isIg) post.hashtags else "",
                if (isIg) post.publishStatus else "",
                if (isIg) post.publishedUrl else "",
                if (isFb) post.title else "",
                if (isFb) post.description else "",
                if (isFb) post.hashtags else "",
                if (isFb) post.publishStatus else "",
                if (isFb) post.publishedUrl else "",
                isoFmt.format(Date(post.updatedAt))
            )
            sb.appendLine(row.joinToString(",") { escapeCsvCell(it) })
            rowCount++
        }

        sb.toString() to rowCount
    }

    suspend fun buildContentMarkdown(): String = withContext(Dispatchers.IO) {
        val scriptsWithAll = db.videoScriptDao().getAllScriptsWithRelationsOnce()
        val standalonePosts = db.socialSeoDao().getAllPostsOnce().filter { it.scriptId == null }
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        buildString {
            appendLine("# OmniAI-Studio Content & Multi-Platform SEO Vault Export")
            appendLine()
            appendLine("Generated: ${fmt.format(Date())} · Scripts: ${scriptsWithAll.size} · Standalone Social Posts: ${standalonePosts.size}")
            appendLine()

            scriptsWithAll.forEach { item ->
                val s = item.script
                appendLine("---")
                appendLine("## [${s.status}] ${s.title}")
                appendLine("- **Workspace:** `${s.workspaceId}` · **Aspect:** `${s.aspectRatio}` · **Voice:** `${s.voiceId}` · **Style:** `${s.styleTag}`")
                appendLine("- **Topic:** ${s.topic}")
                if (s.linkedVideoPath.isNotBlank()) {
                    appendLine("- **Linked Video:** `${s.linkedVideoPath}`")
                }
                appendLine()

                val orderedScenes = item.scenes.sortedBy { it.sceneOrder }
                if (orderedScenes.isNotEmpty()) {
                    appendLine("### Scenes (${orderedScenes.size})")
                    orderedScenes.forEach { sc ->
                        val star = if (sc.isFavoriteScene) " ★" else ""
                        appendLine("${sc.sceneOrder}.$star **(${sc.durationSec}s · ${sc.cameraMovement})** ${sc.visualPrompt}")
                        appendLine("   - *Narration:* ${sc.narration}")
                    }
                    appendLine()
                }

                if (item.socialPosts.isNotEmpty()) {
                    appendLine("### Platform SEO Kits")
                    item.socialPosts.forEach { p ->
                        appendLine("#### ${p.platform} (${p.publishStatus})")
                        if (p.title.isNotBlank()) appendLine("- **Title:** ${p.title}")
                        if (p.hookLine.isNotBlank()) appendLine("- **Hook:** ${p.hookLine}")
                        if (p.description.isNotBlank()) appendLine("- **Description:** ${p.description}")
                        if (p.hashtags.isNotBlank()) appendLine("- **Hashtags:** ${p.hashtags}")
                        if (p.pinnedComment.isNotBlank()) appendLine("- **Pinned Comment:** ${p.pinnedComment}")
                        if (p.publishedUrl.isNotBlank()) appendLine("- **Live URL:** ${p.publishedUrl}")
                        appendLine()
                    }
                }
            }

            if (standalonePosts.isNotEmpty()) {
                appendLine("---")
                appendLine("## Standalone Social Media Posts")
                standalonePosts.forEach { p ->
                    appendLine("### [${p.platform} · ${p.publishStatus}] ${p.title.ifBlank { "(Untitled)" }}")
                    if (p.description.isNotBlank()) appendLine(p.description)
                    if (p.hashtags.isNotBlank()) appendLine("\n${p.hashtags}")
                    if (p.publishedUrl.isNotBlank()) appendLine("\nLive URL: ${p.publishedUrl}")
                    appendLine()
                }
            }
        }
    }

    suspend fun exportAllToCsvUri(uri: Uri): ContentCsvExportResult = withContext(Dispatchers.IO) {
        val (csv, rows) = buildContentCsv()
        val bytes = csv.toByteArray(Charsets.UTF_8)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
        } ?: error("Unable to open SAF destination for CSV export.")
        ContentCsvExportResult(
            filePath = uri.toString(),
            rowCount = rows,
            byteSize = bytes.size.toLong()
        )
    }

    suspend fun exportAllToMarkdownUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val md = buildContentMarkdown()
        val bytes = md.toByteArray(Charsets.UTF_8)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
        } ?: error("Unable to open SAF destination for Markdown export.")
        bytes.size
    }

    /**
     * Writes RFC-4180 CSV to `Documents/OmniAIStudio/Exports/` inside app external documents
     * (or internal filesDir fallback) so CLI `db export csv` works seamlessly with zero extra permissions.
     */
    suspend fun exportAllToLocalCsvFile(): ContentCsvExportResult = withContext(Dispatchers.IO) {
        val (csv, rows) = buildContentCsv()
        val bytes = csv.toByteArray(Charsets.UTF_8)
        val baseDocs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "Documents")
        val exportDir = File(baseDocs, "OmniAIStudio/Exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(exportDir, "OmniAI_ContentDB_$stamp.csv")
        outFile.writeBytes(bytes)
        ContentCsvExportResult(
            filePath = outFile.absolutePath,
            rowCount = rows,
            byteSize = bytes.size.toLong()
        )
    }

    suspend fun buildThreadMarkdown(threadId: Long): String = withContext(Dispatchers.IO) {
        val thread = db.chatDao().getThread(threadId) ?: return@withContext ""
        val messages = db.chatDao().getMessagesOnce(threadId)
        buildString {
            appendLine("# ${thread.title}")
            appendLine()
            appendLine("- **Workspace:** `${thread.workspaceId}` · **Folder:** `${thread.folderName}` · **Tags:** `${thread.tagsCsv.ifBlank { "none" }}`")
            if (thread.providerUsed.isNotBlank()) {
                appendLine("- **Last Provider:** `${thread.providerUsed}`")
            }
            appendLine()
            if (thread.systemPrompt.isNotBlank()) {
                appendLine("> System Prompt: ${thread.systemPrompt}")
                appendLine()
            }
            messages.forEach { m ->
                val star = if (m.isBookmarked) " ★ BOOKMARKED" else ""
                val speaker = if (m.role == "user") "User" else "Assistant (${m.providerTag.ifBlank { "AI" }})"
                appendLine("## $speaker$star")
                appendLine()
                appendLine(m.content)
                appendLine()
            }
        }
    }

    suspend fun buildThreadJson(threadId: Long): String = withContext(Dispatchers.IO) {
        val thread = db.chatDao().getThread(threadId) ?: return@withContext "{}"
        val messages = db.chatDao().getMessagesOnce(threadId)
        val payload = ChatThreadJsonExport(
            threadId = thread.id,
            title = thread.title,
            workspaceId = thread.workspaceId,
            folderName = thread.folderName,
            tagsCsv = thread.tagsCsv,
            isPinned = thread.isPinned,
            isArchived = thread.isArchived,
            providerUsed = thread.providerUsed,
            systemPrompt = thread.systemPrompt,
            rollingSummary = thread.rollingSummary,
            exportedAtEpochMs = System.currentTimeMillis(),
            messages = messages.map { m ->
                ChatMessageJsonExport(
                    role = m.role,
                    content = m.content,
                    providerTag = m.providerTag,
                    modelName = m.modelName,
                    isBookmarked = m.isBookmarked,
                    tokenEstimate = m.tokenEstimate,
                    createdAt = m.createdAt
                )
            }
        )
        json.encodeToString(payload)
    }

    companion object {
        /**
         * Strict RFC-4180 CSV escaping: wraps fields containing commas, quotes, CR, or LF
         * in double quotes and doubles internal quotes.
         */
        fun escapeCsvCell(raw: String): String {
            val needsQuote = raw.contains(',') || raw.contains('"') || raw.contains('\n') || raw.contains('\r')
            if (!needsQuote) return raw
            return "\"${raw.replace("\"", "\"\"")}\""
        }
    }
}
