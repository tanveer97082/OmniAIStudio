package com.omniai.studio.niche

import com.omniai.studio.data.local.AppSettingsRepository
import com.omniai.studio.data.local.NicheEntity
import com.omniai.studio.data.local.OmniAiDatabase
import com.omniai.studio.data.remote.AiStreamingClient
import com.omniai.studio.data.remote.NicheBlueprintDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * v4.0 Master Niche Context Builder & Smart Rolling Memory Compressor.
 * - Seeds 5 high-retention starter niches on first launch.
 * - Compacts Niche System Rules + Rolling Summary + last 5 Video Scripts/Endings + last 8 Chat Exchanges
 *   into a tight, token-efficient context block injected into AI Chat & Video Storyboard prompts.
 * - Automatically compresses niche memory whenever a niche accumulates > threshold new messages.
 */
class NicheContextBuilder(
    private val db: OmniAiDatabase,
    private val aiClient: AiStreamingClient,
    private val settingsRepo: AppSettingsRepository,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    suspend fun ensureStarterNichesSeeded(): List<NicheEntity> = withContext(Dispatchers.IO) {
        val existing = db.nicheDao().getAllNichesOnce()
        if (existing.isNotEmpty()) return@withContext existing

        val starters = listOf(
            NicheEntity(
                id = 1L,
                name = "3D Animated Stories",
                emoji = "🧸",
                description = "Emotional, character-driven Pixar/Unreal-style 3D animated micro-stories with crisp visual arcs.",
                systemPromptRules = "Write emotionally gripping 3D animated micro-stories with expressive characters, cozy cinematic lighting, tactile textures, clear 3-second visual hook, and a memorable cliffhanger or twist ending.",
                visualStylePreset = "3D Pixar Animated",
                defaultVoiceId = "English Female",
                defaultAspectRatio = "9:16",
                autoNextEnabled = false
            ),
            NicheEntity(
                id = 2L,
                name = "Science & Space Facts",
                emoji = "🪐",
                description = "Mind-bending cosmic discoveries, quantum physics, and visual deep-space explainers.",
                systemPromptRules = "Deliver scientifically accurate, awe-inspiring space & physics breakdowns. Open with a counter-intuitive cosmic hook, use concrete scale comparisons, and describe sweeping telescopic and volumetric visuals.",
                visualStylePreset = "Sci-Fi Volumetric Cinema",
                defaultVoiceId = "English Male",
                defaultAspectRatio = "9:16",
                autoNextEnabled = false
            ),
            NicheEntity(
                id = 3L,
                name = "AI & Tech News",
                emoji = "🤖",
                description = "Fast-paced AI breakthroughs, open-source models, robotics, and developer deep dives.",
                systemPromptRules = "Cover cutting-edge AI, GPU hardware, and developer breakthroughs with sharp clarity. Lead with why it matters in 3 seconds, avoid fluff, use cyberpunk HUD/lab visuals, and close with a forward-looking question.",
                visualStylePreset = "Cyberpunk Neon Noir",
                defaultVoiceId = "English Male",
                defaultAspectRatio = "9:16",
                autoNextEnabled = false
            ),
            NicheEntity(
                id = 4L,
                name = "Dark History & Mysteries",
                emoji = "🕯️",
                description = "Unsolved historical enigmas, forgotten civilizations, and dramatic archival reconstructions.",
                systemPromptRules = "Narrate forgotten history and mysteries with atmospheric suspense. Use candlelit chiaroscuro imagery, historical grounding, tight pacing, and a haunting final revelation.",
                visualStylePreset = "Cinematic Realism",
                defaultVoiceId = "English Male",
                defaultAspectRatio = "9:16",
                autoNextEnabled = false
            ),
            NicheEntity(
                id = 5L,
                name = "Finance & Strategy",
                emoji = "📈",
                description = "Wealth psychology, macro-economics, business moats, and high-leverage decision frameworks.",
                systemPromptRules = "Explain wealth strategy, market incentives, and business playbooks with punchy authority. Lead with a sharp contrarian insight, keep visuals sleek and architectural, and end with an actionable takeaway.",
                visualStylePreset = "Clean Minimalist Editorial",
                defaultVoiceId = "English Male",
                defaultAspectRatio = "9:16",
                autoNextEnabled = false
            )
        )
        starters.forEach { db.nicheDao().insert(it) }
        db.nicheDao().getAllNichesOnce()
    }

    /**
     * Builds a compact, high-signal context block for the active niche combining:
     * 1) Niche identity & system prompt rules
     * 2) Compressed rolling memory summary
     * 3) Last 5 VideoScriptEntity titles + final scene endings (for continuity & deduplication)
     * 4) Last 8 ChatMessageEntity exchanges (up to 16 messages)
     */
    suspend fun buildCompactNicheContext(nicheId: Long): String = withContext(Dispatchers.IO) {
        val niche = db.nicheDao().getNicheById(nicheId) ?: return@withContext ""
        val recentScripts = db.videoScriptDao().getRecentScriptsForNicheOnce(nicheId, 5)
        val recentMessages = db.chatDao().getRecentMessagesForNicheOnce(nicheId, 16).reversed()

        buildString {
            appendLine("=== ACTIVE NICHE CONTEXT (${niche.emoji} ${niche.name}) ===")
            if (niche.description.isNotBlank()) {
                appendLine("Niche Focus: ${niche.description}")
            }
            if (niche.systemPromptRules.isNotBlank()) {
                appendLine("Niche Editorial & Style Rules: ${niche.systemPromptRules}")
            }
            appendLine("Defaults: Style=${niche.visualStylePreset} | Voice=${niche.defaultVoiceId} | Ratio=${niche.defaultAspectRatio} | Episodes=${niche.episodeCount}")

            if (niche.rollingChatSummary.isNotBlank()) {
                appendLine()
                appendLine("COMPRESSED NICHE MEMORY SUMMARY:")
                appendLine(niche.rollingChatSummary.take(900))
            }

            if (recentScripts.isNotEmpty()) {
                appendLine()
                appendLine("RECENT VIDEOS IN THIS NICHE (Maintain Continuity, Never Repeat Topics):")
                recentScripts.forEachIndexed { idx, bundle ->
                    val orderedScenes = bundle.scenes.sortedBy { it.sceneOrder }
                    val endingLine = orderedScenes.lastOrNull()?.narration?.take(140).orEmpty()
                    appendLine("${idx + 1}. \"${bundle.script.title}\" [${bundle.script.status}] — Topic: ${bundle.script.topic.take(100)}")
                    if (endingLine.isNotBlank()) {
                        appendLine("   Ending narration: \"$endingLine\"")
                    }
                }
            }

            if (recentMessages.isNotEmpty()) {
                appendLine()
                appendLine("RECENT NICHE CHAT EXCHANGES:")
                recentMessages.takeLast(12).forEach { msg ->
                    val roleTag = if (msg.role == "assistant") "AI" else "USER"
                    appendLine("- $roleTag: ${msg.content.replace('\n', ' ').take(180)}")
                }
            }
        }.trim()
    }

    /**
     * Automatically compresses niche chat + video memory into `niche.rollingChatSummary`
     * whenever the niche has accumulated more than `autoSummarizeNicheThreshold` new messages.
     */
    suspend fun compressNicheMemoryIfNeeded(
        nicheId: Long,
        force: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val niche = db.nicheDao().getNicheById(nicheId) ?: return@withContext ""
        val totalMessages = db.chatDao().countMessagesForNiche(nicheId)
        val threshold = settingsRepo.current.autoSummarizeNicheThreshold.coerceIn(8, 60)
        val delta = totalMessages - niche.lastSummarizedMessageCount

        if (!force && delta < threshold) {
            return@withContext niche.rollingChatSummary
        }

        val recentMessages = db.chatDao().getRecentMessagesForNicheOnce(nicheId, 24).reversed()
        val recentScripts = db.videoScriptDao().getRecentScriptsForNicheOnce(nicheId, 6)
        if (recentMessages.isEmpty() && recentScripts.isEmpty()) {
            return@withContext niche.rollingChatSummary
        }

        val sys = """
            You are a rolling memory compressor for a creator's content niche ("${niche.name}").
            Merge the existing rolling summary with the latest niche chat turns and video episodes into <= 160 words.
            Preserve recurring characters, plot threads, audience hooks, tone preferences, and topics already covered so future videos continue smoothly without repetition.
            Return concise bullet points only.
        """.trimIndent()

        val userPrompt = buildString {
            if (niche.rollingChatSummary.isNotBlank()) {
                appendLine("EXISTING NICHE SUMMARY:")
                appendLine(niche.rollingChatSummary)
                appendLine()
            }
            if (recentScripts.isNotEmpty()) {
                appendLine("RECENT VIDEO EPISODES:")
                recentScripts.forEach { b ->
                    appendLine("- ${b.script.title}: ${b.script.topic.take(120)}")
                }
                appendLine()
            }
            if (recentMessages.isNotEmpty()) {
                appendLine("RECENT CHAT MESSAGES:")
                recentMessages.forEach { m ->
                    appendLine("${m.role.uppercase()}: ${m.content.replace('\n', ' ').take(220)}")
                }
            }
        }

        val compressed = runCatching {
            val (reply, _) = aiClient.completeSinglePrompt(sys, userPrompt)
            reply.trim()
        }.getOrElse { "" }

        val finalSummary = if (compressed.isNotBlank()) {
            compressed
        } else {
            // Offline deterministic fallback summary if no provider key is configured yet
            buildString {
                if (recentScripts.isNotEmpty()) {
                    appendLine("• Covered videos: ${recentScripts.joinToString("; ") { it.script.title }.take(240)}")
                }
                val userTopics = recentMessages.filter { it.role == "user" }.takeLast(4)
                if (userTopics.isNotEmpty()) {
                    appendLine("• Recent ideas: ${userTopics.joinToString(" | ") { it.content.take(70) }}")
                }
            }.trim().ifBlank { niche.rollingChatSummary }
        }

        if (finalSummary.isNotBlank()) {
            db.nicheDao().updateRollingSummary(nicheId, finalSummary, totalMessages)
        }
        finalSummary
    }

    /**
     * Uses the configured AI provider to auto-generate a tailored Niche Blueprint
     * (emoji, description, systemPromptRules, visualStylePreset, defaultVoiceId, defaultAspectRatio)
     * from a short niche name entered by the user.
     */
    suspend fun aiAutoFillNicheBlueprint(nicheName: String): NicheBlueprintDto = withContext(Dispatchers.IO) {
        val cleanName = nicheName.trim().ifBlank { "Cinematic Storytelling" }
        val sys = """
            You are a viral YouTube Shorts / Instagram Reels creative director.
            Given a content niche name, output ONLY raw valid JSON (no markdown fences) matching:
            {
              "emoji": "single relevant emoji",
              "description": "1-sentence positioning description",
              "systemPromptRules": "3-sentence strict creative, pacing, hook, and visual style instruction block",
              "visualStylePreset": "one of: Cinematic Realism, 3D Pixar Animated, Cyberpunk Neon Noir, Sci-Fi Volumetric Cinema, Anime Cel-Shaded, Clean Minimalist Editorial",
              "defaultVoiceId": "one of: English Male, English Female, Hindi Male, Hindi Female",
              "defaultAspectRatio": "9:16"
            }
        """.trimIndent()

        runCatching {
            val (raw, _) = aiClient.completeSinglePrompt(sys, "Build a complete creator niche blueprint for: $cleanName")
            val cleaned = aiClient.extractJsonObject(raw)
            json.decodeFromString<NicheBlueprintDto>(cleaned)
        }.getOrElse {
            NicheBlueprintDto(
                emoji = "🎬",
                description = "High-retention short-form and episodic visual storytelling tailored to $cleanName.",
                systemPromptRules = "Create gripping $cleanName episodes with an immediate 3-second hook, vivid Wan 2.1 cinematic motion prompts, concise punchy voiceover narration, and a cliffhanger or loopable closing line.",
                visualStylePreset = "Cinematic Realism",
                defaultVoiceId = "English Male",
                defaultAspectRatio = "9:16"
            )
        }
    }
}
