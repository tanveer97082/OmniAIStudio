package com.omniai.studio.data.remote

import com.omniai.studio.data.local.AppSettingsRepository
import com.omniai.studio.data.local.CharacterProfileEntity
import com.omniai.studio.security.NetworkSecurityValidator
import com.omniai.studio.security.VaultManager
import com.omniai.studio.terminal.NetworkLogInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

data class StreamTokenEvent(
    val token: String,
    val providerTag: String,
    val isComplete: Boolean = false,
    val failoverNotice: String? = null
)

class AiStreamingClient(
    private val vault: VaultManager,
    private val settingsRepo: AppSettingsRepository? = null,
    private val onMetricRecorded: ((providerName: String, estimatedTokens: Int, latencyMs: Long, success: Boolean) -> Unit)? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(NetworkLogInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun applyPurposeMode(systemPrompt: String): String {
        val mode = settingsRepo?.current?.aiPurposeMode ?: return systemPrompt
        if (mode.instruction.isBlank()) return systemPrompt
        return buildString {
            append(systemPrompt)
            append("\n\nACTIVE PURPOSE MODE — ${mode.label}:\n")
            append(mode.instruction)
            append("\nHonor the task's requested format, schema, and explicit constraints.")
        }
    }

    /**
     * Streams chat tokens with ordered failover:
     * 1) Primary Uncensored Tunnel (OpenAI-compatible SSE)
     * 2) Secondary Hugging Face Inference Router (OpenAI-compatible SSE)
     * 3) Tertiary Gemini 2.5 Flash API on timeout / HTTP 429 / HTTP 5xx
     */
    fun streamChat(
        systemPrompt: String,
        history: List<OpenAiChatMessage>
    ): Flow<StreamTokenEvent> = callbackFlow {
        val effectiveSystemPrompt = applyPurposeMode(systemPrompt)
        val fullMessages = buildList {
            if (effectiveSystemPrompt.isNotBlank()) {
                add(OpenAiChatMessage(role = "system", content = effectiveSystemPrompt))
            }
            addAll(history)
        }
        val promptChars = fullMessages.sumOf { it.content.length }

        val providers = buildProviderChain()
        if (providers.isEmpty()) {
            close(IllegalStateException("No AI provider configured in Vault. Add a Tunnel URL, HF Token, or Gemini API key."))
            return@callbackFlow
        }

        var succeeded = false
        var lastError: Throwable? = null

        for ((index, provider) in providers.withIndex()) {
            if (index > 0 && !vault.autoFallbackEnabled) {
                break
            }
            val startedMs = System.currentTimeMillis()
            var outputChars = 0
            try {
                if (index > 0) {
                    trySend(
                        StreamTokenEvent(
                            token = "",
                            providerTag = provider.tag,
                            failoverNotice = "Primary unavailable (${lastError?.message ?: "error"}). Failing over to ${provider.tag}..."
                        )
                    )
                }

                when (provider) {
                    is ProviderTarget.OpenAiCompatible -> {
                        streamOpenAiSseBlocking(provider, fullMessages) { chunk ->
                            outputChars += chunk.length
                            trySend(StreamTokenEvent(token = chunk, providerTag = provider.tag))
                        }
                    }
                    is ProviderTarget.GeminiFlash -> {
                        streamGeminiSseBlocking(provider.apiKey, effectiveSystemPrompt, history) { chunk ->
                            outputChars += chunk.length
                            trySend(StreamTokenEvent(token = chunk, providerTag = provider.tag))
                        }
                    }
                }

                val latency = (System.currentTimeMillis() - startedMs).coerceAtLeast(1L)
                val estTokens = ((promptChars + outputChars) / 4).coerceAtLeast(1)
                onMetricRecorded?.invoke(provider.tag, estTokens, latency, true)

                trySend(StreamTokenEvent(token = "", providerTag = provider.tag, isComplete = true))
                succeeded = true
                break
            } catch (ex: Throwable) {
                val latency = (System.currentTimeMillis() - startedMs).coerceAtLeast(1L)
                onMetricRecorded?.invoke(provider.tag, (promptChars / 4).coerceAtLeast(1), latency, false)
                lastError = ex
            }
        }

        if (!succeeded) {
            close(lastError ?: IOException("All configured AI providers failed."))
        } else {
            close()
        }

        awaitClose { }
    }

    /**
     * Streams tokens from a specific provider slot (0 = Primary, 1 = Secondary, 2 = Tertiary)
     * for Multi-Model Arena side-by-side split-compare mode.
     */
    fun streamSingleProviderByIndex(
        providerIndex: Int,
        systemPrompt: String,
        history: List<OpenAiChatMessage>
    ): Flow<StreamTokenEvent> = callbackFlow {
        val effectiveSystemPrompt = applyPurposeMode(systemPrompt)
        val fullMessages = buildList {
            if (effectiveSystemPrompt.isNotBlank()) {
                add(OpenAiChatMessage(role = "system", content = effectiveSystemPrompt))
            }
            addAll(history)
        }
        val promptChars = fullMessages.sumOf { it.content.length }
        val providers = buildProviderChain()
        val provider = providers.getOrNull(providerIndex) ?: providers.firstOrNull()
        if (provider == null) {
            close(IllegalStateException("Configure at least 1 AI provider in Vault to run Arena Mode."))
            return@callbackFlow
        }
        val startedMs = System.currentTimeMillis()
        var outputChars = 0
        try {
            when (provider) {
                is ProviderTarget.OpenAiCompatible -> {
                    streamOpenAiSseBlocking(provider, fullMessages) { chunk ->
                        outputChars += chunk.length
                        trySend(StreamTokenEvent(token = chunk, providerTag = provider.tag))
                    }
                }
                is ProviderTarget.GeminiFlash -> {
                    streamGeminiSseBlocking(provider.apiKey, effectiveSystemPrompt, history) { chunk ->
                        outputChars += chunk.length
                        trySend(StreamTokenEvent(token = chunk, providerTag = provider.tag))
                    }
                }
            }
            val latency = (System.currentTimeMillis() - startedMs).coerceAtLeast(1L)
            onMetricRecorded?.invoke(provider.tag, ((promptChars + outputChars) / 4).coerceAtLeast(1), latency, true)
            trySend(StreamTokenEvent(token = "", providerTag = provider.tag, isComplete = true))
            close()
        } catch (ex: Throwable) {
            val latency = (System.currentTimeMillis() - startedMs).coerceAtLeast(1L)
            onMetricRecorded?.invoke(provider.tag, (promptChars / 4).coerceAtLeast(1), latency, false)
            close(ex)
        }
        awaitClose { }
    }

    suspend fun completeSinglePrompt(
        systemPrompt: String,
        userPrompt: String
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        var activeProvider = ""
        streamChat(
            systemPrompt = systemPrompt,
            history = listOf(OpenAiChatMessage(role = "user", content = userPrompt))
        ).collect { event ->
            if (event.token.isNotEmpty()) sb.append(event.token)
            if (event.providerTag.isNotEmpty()) activeProvider = event.providerTag
        }
        sb.toString().trim() to activeProvider
    }

    suspend fun enhanceVideoTopicPrompt(topic: String, stylePreset: String): String {
        val sys = "You are an award-winning cinematic AI video director. Rewrite the user's video topic into a vivid, high-detail, visually grounded production brief suitable for Wan 2.1 video diffusion. Keep it under 90 words, no markdown headings."
        val (result, _) = completeSinglePrompt(sys, "Topic: $topic\nVisual Style: $stylePreset")
        return result.ifBlank { topic }
    }

    suspend fun summarizeConversationHistory(
        existingSummary: String,
        messagesToCompress: List<OpenAiChatMessage>
    ): String {
        val sys = """
            You are a precise conversation memory compressor.
            Merge the existing memory summary with the new older messages into a concise, factual summary (under 180 words).
            Preserve user goals, preferences, technical decisions, file names, code constraints, and unresolved questions.
            Return plain bullet points only.
        """.trimIndent()

        val transcript = buildString {
            if (existingSummary.isNotBlank()) {
                appendLine("EXISTING MEMORY SUMMARY:")
                appendLine(existingSummary)
                appendLine()
            }
            appendLine("OLDER MESSAGES TO MERGE INTO MEMORY:")
            messagesToCompress.forEach { msg ->
                appendLine("${msg.role.uppercase()}: ${msg.content.take(600)}")
            }
        }
        val (summary, _) = completeSinglePrompt(sys, transcript)
        return summary.ifBlank { existingSummary }
    }

    suspend fun generateStoryboardJson(
        topic: String,
        sceneCount: Int,
        aspectRatio: String,
        stylePreset: String,
        voice: String,
        burnSubtitles: Boolean,
        characterDna: CharacterProfileEntity? = null,
        watermarkText: String = "",
        bgmUrl: String = "",
        speedFactor: Float = 1.0f,
        useRefImage: Boolean = false,
        nicheContextBlock: String = "",
        seamlessLoop: Boolean = false,
        multiCharacterMode: Boolean = false
    ): StoryboardPayload {
        val dnaBlock = if (characterDna != null) {
            """
            LOCKED CHARACTER DNA (MUST appear consistently across EVERY scene's visual_prompt):
            - Character Name: ${characterDna.name}
            - Visual Appearance Descriptor: ${characterDna.appearancePrompt}
            - Negative Prompt Avoidances: ${characterDna.negativePrompt}
            - Default Visual Style: ${characterDna.defaultStyle}
            """.trimIndent()
        } else {
            ""
        }

        val loopBlock = if (seamlessLoop) {
            """
            SEAMLESS INFINITE LOOP MODE (ENABLED):
            - Write the final scene's narration as a smooth syntactic lead-in clause (ending without a full stop) that connects grammatically and rhythmically directly back into Scene 1's opening words so Reels/Shorts replay infinitely without an audible seam.
            - Visually match the final scene's closing framing/camera motion with Scene 1's opening shot.
            """.trimIndent()
        } else {
            ""
        }

        val effectiveStyle = characterDna?.defaultStyle?.takeIf { it.isNotBlank() } ?: stylePreset
        val dialogueInstructions = if (multiCharacterMode) {
            """
            MULTI-CHARACTER MODE:
            - Create 2–4 named recurring characters and write natural, short conversational exchanges across the scenes.
            - Put every spoken character line in each scene's "dialogue" array with character_name, voice, and text.
            - Use only these voice values: "English Male", "English Female", "Hindi Male", "Hindi Female".
            - Include the speaking characters and consistent visual descriptions in the scene visual_prompt.
            - Leave narration empty for scenes with dialogue so speech is not duplicated. Use narration only for scenes without dialogue.
            """.trimIndent()
        } else {
            "Keep each scene's dialogue array empty and place speech in narration."
        }
        val sys = """
            You are a strict JSON storyboard & social SEO generator for Wan 2.1 AI video generation.
            $nicheContextBlock
            $dnaBlock
            $loopBlock
            $dialogueInstructions
            Output ONLY raw valid JSON matching this exact schema (no markdown fences, no commentary):
            {
              "title": "Short catchy video title",
              "aspect_ratio": "$aspectRatio",
              "voice": "$voice",
              "burn_subtitles": $burnSubtitles,
              "seamless_loop": $seamlessLoop,
              "social_metadata": {
                "viralTitle": "High-CTR YouTube Shorts / Reels title under 70 chars",
                "caption": "Engaging 2-sentence social post description with hook and CTA",
                "hashtags": ["#AI", "#Shorts", "#Tech", "#Cinematic", "#OmniAI"],
                "pinnedComment": "Conversation-starting pinned comment question for viewers"
              },
              "scenes": [
                {
                  "scene_index": 1,
                  "visual_prompt": "Detailed cinematic shot description with camera motion, lighting, subject, and $effectiveStyle aesthetic",
                  "narration": "Spoken voiceover line matching the requested voice language",
                  "duration_sec": 5,
                  "dialogue": []
                }
              ]
            }
            Generate exactly $sceneCount scenes.
        """.trimIndent()

        val (raw, _) = completeSinglePrompt(
            systemPrompt = sys,
            userPrompt = "Create a $sceneCount-scene storyboard + social SEO publishing kit for topic: $topic. Style: $effectiveStyle. Voice: $voice. Multi-character dialogue: $multiCharacterMode."
        )
        val cleaned = extractJsonObject(raw)
        val parsed = json.decodeFromString<StoryboardPayload>(cleaned)

        val lockedPrefix = characterDna?.appearancePrompt?.trim().orEmpty()
        val consistentScenes = parsed.scenes.mapIndexed { idx, scene ->
            val promptWithDna = if (
                lockedPrefix.isNotEmpty() &&
                !scene.visualPrompt.contains(lockedPrefix.take(28), ignoreCase = true)
            ) {
                "[Character DNA: $lockedPrefix] ${scene.visualPrompt}"
            } else {
                scene.visualPrompt
            }
            scene.copy(sceneIndex = idx + 1, visualPrompt = promptWithDna)
        }

        val fallbackMeta = parsed.socialMetadata.let { meta ->
            if (meta.viralTitle.isBlank()) {
                SocialMetadata(
                    viralTitle = parsed.title.ifBlank { topic.take(65) },
                    caption = "Watch till the end for the full visual breakdown of $topic.",
                    hashtags = listOf("#AI", "#Wan21", "#Shorts", "#Cinematic"),
                    pinnedComment = "Which scene stood out the most to you? Drop a comment below!"
                )
            } else {
                meta
            }
        }

        return parsed.copy(
            scenes = consistentScenes,
            negativePrompt = characterDna?.negativePrompt?.trim().orEmpty(),
            watermarkText = watermarkText.trim(),
            bgmUrl = bgmUrl.trim(),
            speedFactor = speedFactor.coerceIn(0.75f, 1.5f),
            useRefImage = useRefImage,
            seamlessLoop = seamlessLoop,
            socialMetadata = fallbackMeta
        )
    }

    /**
     * Auto-detects Devanagari vs Latin script and translates to the counterpart language
     * (English -> Hindi or Hindi -> English) for side-by-side comparison cards.
     */
    suspend fun translateText(sourceText: String): Pair<String, String> {
        val containsHindi = sourceText.any { ch -> ch in '\u0900'..'\u097F' }
        val targetLang = if (containsHindi) "English" else "Hindi (Devanagari)"
        val sys = "You are a precise bilingual translator. Translate the user's text faithfully into $targetLang. Preserve formatting and line breaks. Output ONLY the translated text."
        val (translated, _) = completeSinglePrompt(sys, sourceText)
        return targetLang to translated.ifBlank { sourceText }
    }

    /**
     * Generates tailored YouTube, Instagram, and Facebook SEO metadata simultaneously
     * (including 3 A/B viral titles per platform) from a VideoScript topic + scene summary.
     */
    suspend fun generateMultiPlatformSocialSeo(
        scriptTitle: String,
        topic: String,
        sceneSummaries: List<String>
    ): MultiPlatformSocialSeoPayload {
        val sys = """
            You are an elite viral growth & social media SEO strategist for YouTube, Instagram Reels, and Facebook Reels.
            Return ONLY valid raw JSON (no markdown fences, no commentary) strictly matching this schema:
            {
              "youtube": {
                "title": "High-CTR title under 95 chars",
                "abTitles": ["Option A title (<95 chars)", "Option B curiosity title (<95 chars)", "Option C direct benefit title (<95 chars)"],
                "hookLine": "First 3-second spoken/visual hook",
                "description": "Search-optimized YouTube description with chapters/key points",
                "hashtags": "#YouTubeShorts #AI #Tech",
                "pinnedComment": "Engaging question to pin for comments",
                "callToAction": "Subscribe for the next breakdown"
              },
              "instagram": {
                "title": "Short punchy Reel cover headline",
                "abTitles": ["Reel Hook A", "Reel Hook B", "Reel Hook C"],
                "hookLine": "Scroll-stopping opening line",
                "description": "Conversational Instagram caption under 2000 chars",
                "hashtags": "#reels #ai #cinematic #creator",
                "pinnedComment": "Save this reel & comment your take!",
                "callToAction": "Save + Follow for Part 2"
              },
              "facebook": {
                "title": "Shareable Facebook video headline",
                "abTitles": ["FB Hook A", "FB Hook B", "FB Hook C"],
                "hookLine": "Relatable opening statement",
                "description": "Community-friendly Facebook post caption",
                "hashtags": "#Video #Creator #Trending",
                "pinnedComment": "Tag someone who needs to see this!",
                "callToAction": "Share with a friend & follow the page"
              }
            }
        """.trimIndent()

        val sceneBlock = sceneSummaries.mapIndexed { idx, s -> "Scene ${idx + 1}: $s" }.joinToString("\n")
        val userPrompt = buildString {
            appendLine("Script Title: $scriptTitle")
            appendLine("Core Topic: $topic")
            if (sceneBlock.isNotBlank()) {
                appendLine("Scenes:")
                appendLine(sceneBlock)
            }
        }

        val (raw, _) = completeSinglePrompt(sys, userPrompt)
        val cleanJson = extractJsonObject(raw)
        val parsed = runCatching {
            json.decodeFromString<MultiPlatformSocialSeoPayload>(cleanJson)
        }.getOrElse {
            val baseTitle = scriptTitle.ifBlank { topic.take(70) }.ifBlank { "Creator Video" }
            MultiPlatformSocialSeoPayload(
                youtube = PlatformSocialSeoItem(
                    title = baseTitle.take(95),
                    abTitles = listOf(
                        baseTitle.take(95),
                        "How $baseTitle Changes Everything".take(95),
                        "The Truth About $baseTitle (Explained)".take(95)
                    ),
                    hookLine = "Stop scrolling — here is what most people miss about $baseTitle.",
                    description = "Full breakdown of $topic.\n\nWatch till the end for every visual scene.",
                    hashtags = "#Shorts #YouTubeShorts #AI #Cinematic #OmniAI",
                    pinnedComment = "Which scene stood out most? Tell us below!",
                    callToAction = "Subscribe for daily cinematic AI breakdowns."
                ),
                instagram = PlatformSocialSeoItem(
                    title = baseTitle.take(75),
                    abTitles = listOf(
                        "Wait for Scene 3... ($baseTitle)".take(80),
                        "POV: $baseTitle in 30 seconds".take(80),
                        "Save this before it's gone: $baseTitle".take(80)
                    ),
                    hookLine = "You need to see how $baseTitle plays out.",
                    description = "$topic\n\nSave this reel for inspiration and share with a creator friend.",
                    hashtags = "#reels #instareels #aicreator #cinematic #storytelling #viralreels",
                    pinnedComment = "Drop a 🔥 if you want Part 2!",
                    callToAction = "Save & Follow for more."
                ),
                facebook = PlatformSocialSeoItem(
                    title = baseTitle.take(90),
                    abTitles = listOf(
                        baseTitle.take(90),
                        "Nobody talks about this side of $baseTitle".take(90),
                        "Watch how $baseTitle comes to life".take(90)
                    ),
                    hookLine = "Have you ever wondered how $baseTitle really works?",
                    description = "Watch the complete story on $topic. Let us know your thoughts in the comments!",
                    hashtags = "#FacebookReels #Storytelling #Creator #Trending #AI",
                    pinnedComment = "Tag a friend who would love this!",
                    callToAction = "Like, Follow & Share this video."
                )
            )
        }
        return parsed
    }

    /**
     * v4.5 AI Auto-Plan Tasks: Generates 6-10 actionable Kanban tasks & milestones
     * tailored to the project name, type, and description.
     */
    suspend fun generateProjectTasksPlan(
        projectName: String,
        projectType: String,
        description: String,
        existingTaskTitles: List<String> = emptyList()
    ): List<AiPlannedTaskDto> {
        val sysPrompt = """
            You are a Principal Engineering & Product Manager.
            Respond ONLY with valid raw JSON (no markdown fences) matching this exact schema:
            {
              "tasks": [
                {
                  "title": "Actionable task title (max 70 chars)",
                  "details": "Concrete acceptance criteria & implementation steps",
                  "priority": "LOW|MEDIUM|HIGH|URGENT",
                  "columnStatus": "TO_DO"
                }
              ]
            }
            Generate 6 to 8 high-leverage milestones & tasks in logical execution order.
        """.trimIndent()

        val userPrompt = buildString {
            appendLine("Project Name: $projectName")
            appendLine("Project Type: $projectType")
            appendLine("Description: ${description.ifBlank { "Production-grade $projectType build" }}")
            if (existingTaskTitles.isNotEmpty()) {
                appendLine("Existing tasks (avoid duplicates): ${existingTaskTitles.joinToString("; ")}")
            }
        }

        val raw = runCatching { completeSinglePrompt(sysPrompt, userPrompt).first }.getOrDefault("")
        val parsed = runCatching {
            json.decodeFromString<AiTaskPlanPayload>(extractJsonObject(raw)).tasks
        }.getOrDefault(emptyList())

        val valid = parsed.filter { it.title.isNotBlank() }.map { item ->
            item.copy(
                priority = when (item.priority.uppercase()) {
                    "LOW", "MEDIUM", "HIGH", "URGENT" -> item.priority.uppercase()
                    else -> "MEDIUM"
                },
                columnStatus = when (item.columnStatus.uppercase()) {
                    "TO_DO", "IN_PROGRESS", "REVIEW", "DONE" -> item.columnStatus.uppercase()
                    else -> "TO_DO"
                }
            )
        }
        if (valid.isNotEmpty()) return valid.take(10)

        return listOf(
            AiPlannedTaskDto("Define architecture & repository scaffold", "Set up directory structure, build config, and README for $projectName.", "HIGH", "TO_DO"),
            AiPlannedTaskDto("Implement core data models & persistence", "Build schema and storage layer aligned with $projectType requirements.", "HIGH", "TO_DO"),
            AiPlannedTaskDto("Build primary user workflow & UI screens", "Create main interactive screens and wire state management.", "HIGH", "TO_DO"),
            AiPlannedTaskDto("Add error handling, validation & security guards", "Harden inputs, network timeouts, and edge cases.", "MEDIUM", "TO_DO"),
            AiPlannedTaskDto("Configure CI/CD workflow & automated checks", "Verify build automation, lint rules, and release artifacts.", "MEDIUM", "TO_DO"),
            AiPlannedTaskDto("End-to-end verification & release push", "Run final smoke test and push tagged release to GitHub.", "URGENT", "TO_DO")
        )
    }

    /**
     * v4.5 AI Auto-Generate Commit Message: Produces a concise Conventional Commit message
     * summarizing the pending Added / Modified / Deleted file diff.
     */
    suspend fun generateGitCommitMessage(
        projectName: String,
        diff: GitDiffResult
    ): String {
        val sysPrompt = """
            You are a senior software engineer writing a single-line Conventional Commit message (max 72 chars).
            Output ONLY the plain commit message line with no quotes, markdown, or extra commentary.
            Format: <type>(<scope>): <concise summary>
        """.trimIndent()

        val userPrompt = buildString {
            appendLine("Project: $projectName")
            if (diff.added.isNotEmpty()) appendLine("Added (${diff.added.size}): ${diff.added.take(12).joinToString(", ")}")
            if (diff.modified.isNotEmpty()) appendLine("Modified (${diff.modified.size}): ${diff.modified.take(12).joinToString(", ")}")
            if (diff.deleted.isNotEmpty()) appendLine("Deleted (${diff.deleted.size}): ${diff.deleted.take(8).joinToString(", ")}")
        }

        val generated = runCatching {
            completeSinglePrompt(sysPrompt, userPrompt)
                .first
                .lineSequence()
                .map { it.trim().trim('"', '\'', '`') }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
                .take(72)
        }.getOrDefault("")

        if (generated.isNotBlank()) return generated

        val scope = projectName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(16).ifBlank { "app" }
        val verb = when {
            diff.added.isNotEmpty() && diff.modified.isEmpty() -> "feat($scope): add ${diff.added.size} project file(s)"
            diff.deleted.isNotEmpty() && diff.added.isEmpty() && diff.modified.isEmpty() -> "chore($scope): remove ${diff.deleted.size} obsolete file(s)"
            else -> "feat($scope): update ${diff.totalPending} file(s)"
        }
        return verb.take(72)
    }

    fun extractJsonObject(raw: String): String {
        val stripped = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return stripped.substring(start, end + 1)
        }
        return stripped
    }

    private sealed class ProviderTarget(val tag: String) {
        data class OpenAiCompatible(
            val label: String,
            val endpointUrl: String,
            val apiKey: String,
            val model: String
        ) : ProviderTarget(label)

        data class GeminiFlash(val apiKey: String) : ProviderTarget("Gemini 2.5 Flash")
    }

    private fun buildProviderChain(): List<ProviderTarget> = buildList {
        if (vault.primaryTunnelUrl.isNotBlank()) {
            val validation = NetworkSecurityValidator.validateEndpointUrl(vault.primaryTunnelUrl)
            if (validation.isValid) {
                val base = validation.normalizedUrl
                add(
                    ProviderTarget.OpenAiCompatible(
                        label = "Primary Tunnel",
                        endpointUrl = toChatCompletionsUrl(base),
                        apiKey = vault.primaryTunnelApiKey,
                        model = vault.primaryModelName.ifBlank { "dolphin-mistral" }
                    )
                )
            }
        }
        vault.compatibleAiProviders.filter { it.enabled }.forEach { provider ->
            val validation = NetworkSecurityValidator.validateEndpointUrl(provider.baseUrl)
            if (validation.isValid && provider.name.isNotBlank() && provider.model.isNotBlank()) {
                add(
                    ProviderTarget.OpenAiCompatible(
                        label = provider.name.trim(),
                        endpointUrl = toChatCompletionsUrl(validation.normalizedUrl),
                        apiKey = provider.apiKey.trim(),
                        model = provider.model.trim()
                    )
                )
            }
        }
        if (vault.huggingFaceToken.isNotBlank()) {
            add(
                ProviderTarget.OpenAiCompatible(
                    label = "Hugging Face",
                    endpointUrl = "https://router.huggingface.co/v1/chat/completions",
                    apiKey = vault.huggingFaceToken,
                    model = vault.huggingFaceModel.ifBlank { "Qwen/Qwen2.5-72B-Instruct" }
                )
            )
        }
        if (vault.geminiApiKey.isNotBlank()) {
            add(ProviderTarget.GeminiFlash(vault.geminiApiKey))
        }
    }

    private fun toChatCompletionsUrl(baseUrl: String): String {
        val base = baseUrl.trimEnd('/')
        return when {
            base.endsWith("/chat/completions", ignoreCase = true) -> base
            base.endsWith("/v1", ignoreCase = true) -> "$base/chat/completions"
            base.endsWith("/openai", ignoreCase = true) -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
    }

    private fun streamOpenAiSseBlocking(
        target: ProviderTarget.OpenAiCompatible,
        messages: List<OpenAiChatMessage>,
        onToken: (String) -> Unit
    ) {
        val currentSettings = settingsRepo?.current
        val payload = json.encodeToString(
            OpenAiChatRequest(
                model = target.model,
                messages = messages,
                stream = true,
                temperature = currentSettings?.temperature?.toDouble() ?: 0.7,
                maxTokens = currentSettings?.maxOutputTokens ?: 2048
            )
        )
        val reqBuilder = Request.Builder()
            .url(target.endpointUrl)
            .header("Accept", "text/event-stream")
            .post(payload.toRequestBody(jsonMediaType))

        if (target.apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${target.apiKey}")
        }

        val latch = java.util.concurrent.CountDownLatch(1)
        var failure: Throwable? = null
        var receivedAnyToken = false

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    eventSource.cancel()
                    latch.countDown()
                    return
                }
                try {
                    val parsed = json.decodeFromString<OpenAiStreamChunk>(data)
                    val deltaText = parsed.choices.firstOrNull()?.delta?.content
                    if (!deltaText.isNullOrEmpty()) {
                        receivedAnyToken = true
                        onToken(deltaText)
                    }
                } catch (_: Exception) {
                    // Ignore keep-alive comments or partial control events
                }
            }

            override fun onClosed(eventSource: EventSource) {
                latch.countDown()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val code = response?.code
                if (code != null && (code == 429 || code >= 500 || code == 401 || code == 403 || code == 404)) {
                    failure = IOException("HTTP $code from ${target.label}")
                } else if (!receivedAnyToken) {
                    failure = t ?: IOException("SSE stream failed on ${target.label}")
                }
                latch.countDown()
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(reqBuilder.build(), listener)
        val completed = latch.await(95, TimeUnit.SECONDS)
        if (!completed) {
            eventSource.cancel()
            throw IOException("Timeout waiting for ${target.label}")
        }
        failure?.let { throw it }
    }

    private fun streamGeminiSseBlocking(
        apiKey: String,
        systemPrompt: String,
        history: List<OpenAiChatMessage>,
        onToken: (String) -> Unit
    ) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse&key=$apiKey"
        val contents = history.map { msg ->
            GeminiContent(
                role = if (msg.role == "assistant") "model" else "user",
                parts = listOf(GeminiPart(text = msg.content))
            )
        }
        val currentSettings = settingsRepo?.current
        val requestObj = GeminiGenerateRequest(
            systemInstruction = if (systemPrompt.isNotBlank()) {
                GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
            } else null,
            contents = contents,
            generationConfig = GeminiGenerationConfig(
                temperature = currentSettings?.temperature?.toDouble() ?: 0.7,
                maxOutputTokens = currentSettings?.maxOutputTokens ?: 2048
            )
        )

        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .post(json.encodeToString(requestObj).toRequestBody(jsonMediaType))
            .build()

        val latch = java.util.concurrent.CountDownLatch(1)
        var failure: Throwable? = null
        var receivedAny = false

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val parsed = json.decodeFromString<GeminiGenerateResponse>(data)
                    val text = parsed.candidates.firstOrNull()?.content?.parts?.joinToString("") { it.text }.orEmpty()
                    if (text.isNotEmpty()) {
                        receivedAny = true
                        onToken(text)
                    }
                } catch (_: Exception) {
                }
            }

            override fun onClosed(eventSource: EventSource) {
                latch.countDown()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val code = response?.code
                if (code != null && code >= 400) {
                    failure = IOException("Gemini HTTP $code")
                } else if (!receivedAny) {
                    failure = t ?: IOException("Gemini SSE stream failure")
                }
                latch.countDown()
            }
        }

        val es = EventSources.createFactory(client).newEventSource(request, listener)
        if (!latch.await(95, TimeUnit.SECONDS)) {
            es.cancel()
            throw IOException("Gemini 2.5 Flash request timed out")
        }
        failure?.let { throw it }
    }
}
