package com.omniai.studio.niche

import com.omniai.studio.data.local.CharacterProfileEntity
import com.omniai.studio.data.local.NextCommandEntity
import com.omniai.studio.data.local.OmniAiDatabase
import com.omniai.studio.data.local.SeriesEpisodeEntity
import com.omniai.studio.data.remote.AiStreamingClient
import com.omniai.studio.data.remote.NextCommandListDto
import com.omniai.studio.data.remote.OptimizedScenesDto
import com.omniai.studio.data.remote.ScriptAuditResult
import com.omniai.studio.data.remote.SeriesPlanDto
import com.omniai.studio.data.remote.StoryboardScene
import com.omniai.studio.youtube.YouTubeAnalyticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * v4.0–v5.3 Auto-Next Video Continuity Engine, Series Roadmap Generator & Viral Script Auditor.
 * - Generates 4 non-repeating post-video next-command suggestions (Sequel, Deep Dive, Twist, Chat Spin-off).
 * - Injects Closed-Loop YouTube Analytics (top 3 & bottom 3 video performance) so Next-Video suggestions
 *   double down on proven high-retention topics.
 * - Generates 3 context-aware chat follow-up commands (always including 1 "Turn this chat into next video").
 * - Generates ordered 7 / 15 / 30 episode Niche Series Roadmaps.
 * - Audits storyboards pre-render (Hook / Pacing / Visual Clarity / Seamless Loop) and auto-optimizes weak scenes
 *   while strictly preserving locked Character DNA.
 */
class ContinuityEngine(
    private val db: OmniAiDatabase,
    private val aiClient: AiStreamingClient,
    private val nicheContextBuilder: NicheContextBuilder,
    private val youTubeAnalyticsRepository: YouTubeAnalyticsRepository? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {

    suspend fun generatePostVideoNextCommands(
        nicheId: Long,
        sourceScriptId: Long?,
        lastVideoTitle: String,
        finalSceneNarration: String
    ): List<NextCommandEntity> = withContext(Dispatchers.IO) {
        val niche = db.nicheDao().getNicheById(nicheId)
        val nicheName = niche?.name ?: "Creator Niche"
        val nextEpisodeNum = ((niche?.episodeCount ?: 0) + 1).coerceAtLeast(1)
        val compactContext = nicheContextBuilder.buildCompactNicheContext(nicheId)
        val existingTitles = db.videoScriptDao().getRecentScriptsForNicheOnce(nicheId, 12)
            .map { it.script.title }
            .filter { it.isNotBlank() }

        val sys = """
            You are an elite episodic viral video producer for the "$nicheName" niche.
            Using the niche context, recent chat discussions, and the previous episode's ending, generate EXACTLY 4 distinct, ready-to-paste next video prompt commands.
            NEVER repeat any topic in the Already-Covered Blocklist.
            Output ONLY raw valid JSON (no markdown fences):
            {
              "commands": [
                {
                  "sourceType": "POST_VIDEO_SEQUEL",
                  "shortLabel": "Ep #$nextEpisodeNum Sequel",
                  "fullPromptCommand": "Detailed 2-sentence Wan 2.1 video production prompt continuing directly from the previous episode ending",
                  "suggestedEpisodeNumber": $nextEpisodeNum
                },
                {
                  "sourceType": "DEEP_DIVE",
                  "shortLabel": "Deep Dive",
                  "fullPromptCommand": "Detailed 2-sentence deep-dive explainer prompt exploring the biggest unanswered question",
                  "suggestedEpisodeNumber": $nextEpisodeNum
                },
                {
                  "sourceType": "VIRAL_TWIST",
                  "shortLabel": "Viral Twist",
                  "fullPromptCommand": "Detailed 2-sentence contrarian or plot-twist angle built for 95%+ viewer retention",
                  "suggestedEpisodeNumber": $nextEpisodeNum
                },
                {
                  "sourceType": "CHAT_FOLLOWUP",
                  "shortLabel": "From Chat Idea",
                  "fullPromptCommand": "Detailed 2-sentence video prompt turning the latest niche chat ideas into a standalone viral episode",
                  "suggestedEpisodeNumber": $nextEpisodeNum
                }
              ]
            }
        """.trimIndent()

        val ytFeedback = youTubeAnalyticsRepository?.buildContinuityFeedbackPrompt().orEmpty()
        val userPrompt = buildString {
            appendLine(compactContext)
            if (ytFeedback.isNotBlank()) {
                appendLine()
                appendLine(ytFeedback)
            }
            appendLine()
            appendLine("LAST COMPLETED VIDEO: $lastVideoTitle")
            if (finalSceneNarration.isNotBlank()) {
                appendLine("FINAL SCENE NARRATION: $finalSceneNarration")
            }
            if (existingTitles.isNotEmpty()) {
                appendLine("ALREADY-COVERED DEDUPLICATION BLOCKLIST (DO NOT REPEAT):")
                existingTitles.forEach { appendLine("- $it") }
            }
        }

        val parsed = runCatching {
            val (raw, _) = aiClient.completeSinglePrompt(sys, userPrompt)
            json.decodeFromString<NextCommandListDto>(aiClient.extractJsonObject(raw)).commands
        }.getOrElse { emptyList() }

        val baseTitle = lastVideoTitle.ifBlank { nicheName }
        val finalEntities = if (parsed.isNotEmpty()) {
            parsed.take(4).map { dto ->
                NextCommandEntity(
                    nicheId = nicheId,
                    sourceScriptId = sourceScriptId,
                    sourceType = normalizeSourceType(dto.sourceType),
                    shortLabel = dto.shortLabel.ifBlank { "Ep #$nextEpisodeNum Idea" }.take(32),
                    fullPromptCommand = dto.fullPromptCommand.trim(),
                    suggestedEpisodeNumber = dto.suggestedEpisodeNumber.coerceAtLeast(1)
                )
            }
        } else {
            listOf(
                NextCommandEntity(
                    nicheId = nicheId,
                    sourceScriptId = sourceScriptId,
                    sourceType = "POST_VIDEO_SEQUEL",
                    shortLabel = "Ep #$nextEpisodeNum Sequel",
                    fullPromptCommand = "Continue \"$baseTitle\" in Episode #$nextEpisodeNum for $nicheName: pick up directly after \"${finalSceneNarration.take(90).ifBlank { "the final reveal" }}\", escalate the visual stakes in 3 seconds, and end on a dramatic cliffhanger.",
                    suggestedEpisodeNumber = nextEpisodeNum
                ),
                NextCommandEntity(
                    nicheId = nicheId,
                    sourceScriptId = sourceScriptId,
                    sourceType = "DEEP_DIVE",
                    shortLabel = "Deep Dive",
                    fullPromptCommand = "Create a high-retention $nicheName deep dive uncovering the hidden mechanism behind \"$baseTitle\" with macro cinematic cutaways and a shocking visual comparison.",
                    suggestedEpisodeNumber = nextEpisodeNum
                ),
                NextCommandEntity(
                    nicheId = nicheId,
                    sourceScriptId = sourceScriptId,
                    sourceType = "VIRAL_TWIST",
                    shortLabel = "Viral Twist",
                    fullPromptCommand = "Flip expectations on \"$baseTitle\" for $nicheName: open with the most counter-intuitive myth people believe, shatter it by Scene 2, and close with a loopable mic-drop insight.",
                    suggestedEpisodeNumber = nextEpisodeNum
                ),
                NextCommandEntity(
                    nicheId = nicheId,
                    sourceScriptId = sourceScriptId,
                    sourceType = "CHAT_FOLLOWUP",
                    shortLabel = "From Chat Idea",
                    fullPromptCommand = "Turn the latest $nicheName brainstorm notes into a crisp 5-scene vertical short with rapid camera motion, bold opening hook, and seamless narrative payoff.",
                    suggestedEpisodeNumber = nextEpisodeNum
                )
            )
        }

        db.nextCommandDao().clearUnusedBySourceTypes(
            nicheId = nicheId,
            sourceTypes = listOf("POST_VIDEO_SEQUEL", "DEEP_DIVE", "VIRAL_TWIST")
        )
        db.nextCommandDao().insertAll(finalEntities)
        db.nextCommandDao().getUnusedCommandsForNicheOnce(nicheId)
    }

    /**
     * Triggered asynchronously after every AI reply in Tab 1 (Chat).
     * Generates 3 context-aware follow-up commands for the active niche, always including
     * 1 "Turn this chat into next video" prompt command.
     */
    suspend fun generateChatFollowUpCommands(
        nicheId: Long,
        latestUserPrompt: String,
        latestAiReply: String
    ): List<NextCommandEntity> = withContext(Dispatchers.IO) {
        val niche = db.nicheDao().getNicheById(nicheId)
        val nicheName = niche?.name ?: "Creator Niche"
        val nextEp = ((niche?.episodeCount ?: 0) + 1).coerceAtLeast(1)

        val sys = """
            You are a proactive AI creative co-pilot for the "$nicheName" niche.
            Based on the latest user message and AI reply, generate EXACTLY 3 ultra-useful follow-up commands.
            Command #1 MUST be a "Turn this chat into next video" prompt ready for Video Director.
            Commands #2 and #3 should be sharp next chat/research commands expanding the discussion.
            Output ONLY raw valid JSON (no markdown fences):
            {
              "commands": [
                {
                  "sourceType": "CHAT_FOLLOWUP",
                  "shortLabel": "Turn Into Video",
                  "fullPromptCommand": "Complete Wan 2.1 video prompt transforming this chat insight into Episode #$nextEp for $nicheName",
                  "suggestedEpisodeNumber": $nextEp
                },
                {
                  "sourceType": "DEEP_DIVE",
                  "shortLabel": "Go Deeper",
                  "fullPromptCommand": "Sharp follow-up chat prompt unpacking the strongest angle from this reply",
                  "suggestedEpisodeNumber": $nextEp
                },
                {
                  "sourceType": "VIRAL_TWIST",
                  "shortLabel": "Viral Hook Angle",
                  "fullPromptCommand": "Follow-up prompt asking for 3 contrarian viral hooks + visual shot list for this idea",
                  "suggestedEpisodeNumber": $nextEp
                }
              ]
            }
        """.trimIndent()

        val userMsg = """
            ACTIVE NICHE: $nicheName
            USER ASKED: ${latestUserPrompt.take(400)}
            AI REPLIED: ${latestAiReply.take(700)}
        """.trimIndent()

        val parsed = runCatching {
            val (raw, _) = aiClient.completeSinglePrompt(sys, userMsg)
            json.decodeFromString<NextCommandListDto>(aiClient.extractJsonObject(raw)).commands
        }.getOrElse { emptyList() }

        val topicHint = latestUserPrompt.replace('\n', ' ').trim().take(80).ifBlank { nicheName }
        val entities = if (parsed.isNotEmpty()) {
            parsed.take(3).mapIndexed { idx, dto ->
                NextCommandEntity(
                    nicheId = nicheId,
                    sourceScriptId = null,
                    sourceType = if (idx == 0) "CHAT_FOLLOWUP" else normalizeSourceType(dto.sourceType),
                    shortLabel = dto.shortLabel.ifBlank { if (idx == 0) "Turn Into Video" else "Follow-Up" }.take(32),
                    fullPromptCommand = dto.fullPromptCommand.trim(),
                    suggestedEpisodeNumber = dto.suggestedEpisodeNumber.coerceAtLeast(1)
                )
            }
        } else {
            listOf(
                NextCommandEntity(
                    nicheId = nicheId,
                    sourceScriptId = null,
                    sourceType = "CHAT_FOLLOWUP",
                    shortLabel = "Turn Into Video",
                    fullPromptCommand = "Turn our chat on \"$topicHint\" into Episode #$nextEp for $nicheName: open with the most surprising takeaway in 3 seconds, visualize every beat with dynamic camera motion, and close with a loopable hook.",
                    suggestedEpisodeNumber = nextEp
                ),
                NextCommandEntity(
                    nicheId = nicheId,
                    sourceScriptId = null,
                    sourceType = "DEEP_DIVE",
                    shortLabel = "Go Deeper",
                    fullPromptCommand = "Break down the 3 most compelling visual scenes and exact spoken narration hooks from \"$topicHint\" for my $nicheName audience.",
                    suggestedEpisodeNumber = nextEp
                ),
                NextCommandEntity(
                    nicheId = nicheId,
                    sourceScriptId = null,
                    sourceType = "VIRAL_TWIST",
                    shortLabel = "Viral Twist",
                    fullPromptCommand = "Give me a contrarian plot twist and 3 high-CTR YouTube Shorts titles based on \"$topicHint\".",
                    suggestedEpisodeNumber = nextEp
                )
            )
        }

        db.nextCommandDao().clearUnusedBySourceTypes(nicheId, listOf("CHAT_FOLLOWUP"))
        db.nextCommandDao().insertAll(entities)
        db.nextCommandDao().getUnusedCommandsForNicheOnce(nicheId)
    }

    /**
     * Generates an ordered, non-repeating 7 / 15 / 30 episode Niche Series Roadmap
     * stored in `series_episodes`.
     */
    suspend fun generateSeriesRoadmap(
        nicheId: Long,
        episodeCount: Int,
        seriesTheme: String
    ): List<SeriesEpisodeEntity> = withContext(Dispatchers.IO) {
        val count = episodeCount.coerceIn(7, 30)
        val niche = db.nicheDao().getNicheById(nicheId)
        val nicheName = niche?.name ?: "Creator Niche"
        val compactContext = nicheContextBuilder.buildCompactNicheContext(nicheId)
        val existingTitles = db.videoScriptDao().getRecentScriptsForNicheOnce(nicheId, 15).map { it.script.title }
        val cleanTheme = seriesTheme.trim().ifBlank { "$nicheName Master Series" }

        val sys = """
            You are a master episodic showrunner for short-form AI video channels.
            Generate an ordered, non-repeating $count-episode roadmap for the "$nicheName" niche around theme: "$cleanTheme".
            Each episode must build momentum on the prior episode with a unique hook and continuity bridge.
            Output ONLY raw valid JSON (no markdown fences):
            {
              "episodes": [
                {
                  "episodeNumber": 1,
                  "title": "Punchy episode title",
                  "hookIdea": "Specific 3-second opening hook + core visual concept",
                  "continuityNote": "How this bridges from the previous episode or sets up the next"
                }
              ]
            }
            Generate all $count episodes.
        """.trimIndent()

        val ytFeedback = youTubeAnalyticsRepository?.buildContinuityFeedbackPrompt().orEmpty()
        val userPrompt = buildString {
            appendLine(compactContext)
            if (ytFeedback.isNotBlank()) {
                appendLine()
                appendLine(ytFeedback)
            }
            if (existingTitles.isNotEmpty()) {
                appendLine()
                appendLine("DO NOT DUPLICATE EXISTING VIDEOS:")
                existingTitles.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("Create a $count-episode roadmap titled \"$cleanTheme\".")
        }

        val parsed = runCatching {
            val (raw, _) = aiClient.completeSinglePrompt(sys, userPrompt)
            json.decodeFromString<SeriesPlanDto>(aiClient.extractJsonObject(raw)).episodes
        }.getOrElse { emptyList() }

        val entities = if (parsed.isNotEmpty()) {
            parsed.take(count).mapIndexed { idx, ep ->
                SeriesEpisodeEntity(
                    nicheId = nicheId,
                    episodeNumber = if (ep.episodeNumber > 0) ep.episodeNumber else idx + 1,
                    title = ep.title.ifBlank { "$cleanTheme — Episode ${idx + 1}" },
                    hookIdea = ep.hookIdea.ifBlank { "Open with a high-contrast 3-second visual hook exploring angle #${idx + 1}." },
                    continuityNote = ep.continuityNote.ifBlank { "Bridges cleanly into Episode ${idx + 2}." },
                    status = "PLANNED"
                )
            }
        } else {
            val arcBeats = listOf(
                "The Forbidden Spark", "Hidden Signal", "First Breakthrough", "The Unseen Cost",
                "Counter-Intuitive Proof", "Behind The Curtain", "Turning Point", "Myth Shattered",
                "High-Stakes Experiment", "Point of No Return", "Secret Blueprint", "Shadow Rival",
                "Zero-Hour Countdown", "The Missing Variable", "Season Climax"
            )
            (1..count).map { epNum ->
                val beat = arcBeats[(epNum - 1) % arcBeats.size]
                SeriesEpisodeEntity(
                    nicheId = nicheId,
                    episodeNumber = epNum,
                    title = "Ep #$epNum: $beat ($cleanTheme)",
                    hookIdea = "Open in 3 seconds with a bold visual contrast introducing $beat inside $nicheName, then reveal the core visual twist.",
                    continuityNote = if (epNum == 1) {
                        "Establishes the series premise and visual signature."
                    } else {
                        "Continues directly from Ep #${epNum - 1}'s closing cliffhanger."
                    },
                    status = "PLANNED"
                )
            }
        }

        db.seriesEpisodeDao().clearEpisodesForNiche(nicheId)
        db.seriesEpisodeDao().insertAll(entities)
        db.seriesEpisodeDao().getAllEpisodesOnce().filter { it.nicheId == nicheId }
    }

    /**
     * Pre-Render Viral Hook & Retention Auditor.
     * Scores Hook, Pacing, Visual Clarity, and Seamless Loop (1-10) + pinpoints weak scenes.
     */
    suspend fun auditStoryboardScript(
        title: String,
        topic: String,
        scenes: List<StoryboardScene>,
        seamlessLoop: Boolean
    ): ScriptAuditResult = withContext(Dispatchers.IO) {
        val sys = """
            You are a brutal, data-driven YouTube Shorts & Instagram Reels retention auditor.
            Audit the storyboard scenes below and output ONLY raw valid JSON (no markdown fences):
            {
              "hookScore": 8,
              "pacingScore": 7,
              "visualClarityScore": 8,
              "loopScore": 6,
              "critiques": [
                "Specific actionable critique #1",
                "Specific actionable critique #2",
                "Specific actionable critique #3"
              ],
              "weakSceneIndices": [1, 4],
              "summaryVerdict": "One punchy sentence summarizing viral readiness"
            }
            All scores MUST be integers 1..10. `weakSceneIndices` are 1-based scene_index integers that need rewriting.
        """.trimIndent()

        val userPrompt = buildString {
            appendLine("TITLE: $title")
            appendLine("TOPIC: $topic")
            appendLine("SEAMLESS LOOP MODE ENABLED: $seamlessLoop")
            appendLine("SCENES JSON:")
            appendLine(json.encodeToString(scenes))
        }

        runCatching {
            val (raw, _) = aiClient.completeSinglePrompt(sys, userPrompt)
            val parsed = json.decodeFromString<ScriptAuditResult>(aiClient.extractJsonObject(raw))
            parsed.copy(
                hookScore = parsed.hookScore.coerceIn(1, 10),
                pacingScore = parsed.pacingScore.coerceIn(1, 10),
                visualClarityScore = parsed.visualClarityScore.coerceIn(1, 10),
                loopScore = parsed.loopScore.coerceIn(1, 10)
            )
        }.getOrElse {
            // Deterministic local heuristic audit when offline / unconfigured
            val firstNarr = scenes.firstOrNull()?.narration.orEmpty()
            val lastNarr = scenes.lastOrNull()?.narration.orEmpty()
            val hookScore = if (firstNarr.length in 25..110) 8 else 6
            val avgVisualLen = scenes.map { it.visualPrompt.length }.average().takeIf { !it.isNaN() } ?: 0.0
            val visualScore = if (avgVisualLen >= 65) 8 else 6
            val pacingScore = if (scenes.all { it.durationSec in 4..6 }) 8 else 6
            val loopScore = if (seamlessLoop && lastNarr.endsWith("...")) 8 else if (seamlessLoop) 5 else 7
            val weak = buildList {
                scenes.forEach { sc ->
                    if (sc.visualPrompt.length < 55 || sc.narration.length < 18) add(sc.sceneIndex)
                }
                if (isEmpty() && scenes.isNotEmpty()) add(1)
            }
            ScriptAuditResult(
                hookScore = hookScore,
                pacingScore = pacingScore,
                visualClarityScore = visualScore,
                loopScore = loopScore,
                critiques = listOf(
                    "Tighten Scene 1's opening narration to deliver a scroll-stopping curiosity gap within the first 2.5 seconds.",
                    "Add explicit camera motion (dolly-in, whip-pan, orbital crane) and lighting contrast to shorter visual prompts for Wan 2.1.",
                    if (seamlessLoop) {
                        "End the final scene's narration as a syntactic lead-in phrase that flows seamlessly back into Scene 1's opening words."
                    } else {
                        "Close the final scene with a decisive emotional payoff or cliffhanger tease for Part 2."
                    }
                ),
                weakSceneIndices = weak,
                summaryVerdict = "Solid foundation — optimizing ${weak.size} flagged scene(s) will sharpen 3-second hook retention and visual clarity."
            )
        }
    }

    /**
     * 1-Tap "Auto-Optimize Weak Scenes" preserving locked Character DNA and Seamless Loop rules.
     */
    suspend fun optimizeWeakScenes(
        title: String,
        topic: String,
        scenes: List<StoryboardScene>,
        audit: ScriptAuditResult,
        characterDna: CharacterProfileEntity?,
        seamlessLoop: Boolean
    ): List<StoryboardScene> = withContext(Dispatchers.IO) {
        val dnaBlock = if (characterDna != null) {
            "LOCKED CHARACTER DNA (MUST be preserved verbatim in every scene's visual_prompt): ${characterDna.name} — ${characterDna.appearancePrompt}"
        } else {
            ""
        }
        val loopRule = if (seamlessLoop) {
            "SEAMLESS INFINITE LOOP REQUIREMENT: Scene ${scenes.size}'s narration MUST end as a syntactic lead-in clause that connects grammatically and rhythmically directly into Scene 1's opening words with zero pause."
        } else {
            ""
        }

        val sys = """
            You are an elite Wan 2.1 viral storyboard optimizer.
            Upgrade the weak scenes (${audit.weakSceneIndices.joinToString()}) based on the audit critiques while keeping strong scenes cohesive.
            $dnaBlock
            $loopRule
            Output ONLY raw valid JSON (no markdown fences) matching:
            {
              "scenes": [
                {
                  "scene_index": 1,
                  "visual_prompt": "Upgraded cinematic visual prompt",
                  "narration": "Upgraded punchy voiceover narration",
                  "duration_sec": 5
                }
              ]
            }
            Keep exact scene count (${scenes.size}).
        """.trimIndent()

        val userPrompt = buildString {
            appendLine("TITLE: $title | TOPIC: $topic")
            appendLine("AUDIT CRITIQUES: ${audit.critiques.joinToString(" | ")}")
            appendLine("CURRENT SCENES JSON:")
            appendLine(json.encodeToString(scenes))
        }

        val optimized = runCatching {
            val (raw, _) = aiClient.completeSinglePrompt(sys, userPrompt)
            json.decodeFromString<OptimizedScenesDto>(aiClient.extractJsonObject(raw)).scenes
        }.getOrElse { emptyList() }

        val lockedPrefix = characterDna?.appearancePrompt?.trim().orEmpty()
        val baseList = if (optimized.isNotEmpty()) optimized else {
            // Deterministic local upgrade if offline
            scenes.mapIndexed { idx, sc ->
                val isFirst = idx == 0
                val isLast = idx == scenes.lastIndex
                val upgradedVisual = if (sc.visualPrompt.length < 70) {
                    "${sc.visualPrompt}, ultra-detailed volumetric rim lighting, dynamic cinematic dolly camera motion, crisp 4K focus"
                } else {
                    sc.visualPrompt
                }
                val upgradedNarration = when {
                    isFirst && !sc.narration.contains("—") -> "Wait — ${sc.narration}"
                    isLast && seamlessLoop -> "${sc.narration.trimEnd('.', '!', '?')}, and that is exactly why"
                    else -> sc.narration
                }
                sc.copy(visualPrompt = upgradedVisual, narration = upgradedNarration)
            }
        }

        baseList.mapIndexed { idx, scene ->
            val promptWithDna = if (
                lockedPrefix.isNotEmpty() &&
                !scene.visualPrompt.contains(lockedPrefix.take(28), ignoreCase = true)
            ) {
                "[Character DNA: $lockedPrefix] ${scene.visualPrompt}"
            } else {
                scene.visualPrompt
            }
            scene.copy(
                sceneIndex = idx + 1,
                visualPrompt = promptWithDna,
                durationSec = scene.durationSec.coerceIn(3, 10)
            )
        }
    }

    private fun normalizeSourceType(raw: String): String = when (raw.uppercase().trim()) {
        "POST_VIDEO_SEQUEL", "CHAT_FOLLOWUP", "DEEP_DIVE", "VIRAL_TWIST" -> raw.uppercase().trim()
        else -> "POST_VIDEO_SEQUEL"
    }
}
