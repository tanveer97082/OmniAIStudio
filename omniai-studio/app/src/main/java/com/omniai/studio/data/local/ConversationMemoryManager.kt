package com.omniai.studio.data.local

import com.omniai.studio.data.remote.AiStreamingClient
import com.omniai.studio.data.remote.OpenAiChatMessage

data class ConversationMemorySnapshot(
    val totalStoredMessages: Int = 0,
    val summarizedMessageCount: Int = 0,
    val activeWindowMessages: Int = 0,
    val estimatedPromptTokens: Int = 0,
    val maxContextTokenBudget: Int = ConversationMemoryManager.MAX_CONTEXT_TOKENS,
    val rollingSummary: String = ""
)

data class CompiledConversationContext(
    val effectiveSystemPrompt: String,
    val windowMessages: List<OpenAiChatMessage>,
    val snapshot: ConversationMemorySnapshot
)

/**
 * Manages long-term Room-backed conversation memory using a hybrid strategy:
 * 1. Always preserves the most recent sliding window (`SLIDING_WINDOW_MESSAGES`) verbatim.
 * 2. When total messages exceed the window OR estimated tokens exceed `SUMMARY_TRIGGER_TOKENS`,
 *    older unsummarized turns are compressed into a persistent `rollingSummary` stored in Room
 *    on `ChatThreadEntity`, preventing context overflow while preserving facts, entities, and decisions.
 */
class ConversationMemoryManager(
    private val chatDao: ChatDao,
    private val aiClient: AiStreamingClient,
    private val settingsRepo: AppSettingsRepository? = null
) {

    private fun activeWindowLimit(): Int =
        (settingsRepo?.current?.chatContextWindow ?: SLIDING_WINDOW_MESSAGES).coerceIn(4, 30)

    suspend fun inspectMemoryState(threadId: Long): ConversationMemorySnapshot {
        val thread = chatDao.getThread(threadId) ?: return ConversationMemorySnapshot()
        val messages = chatDao.getMessagesOnce(threadId)
        val recentSlice = computeSlidingWindow(messages, thread.summarizedMessageCount)
        val tokens = estimateTokens(thread.systemPrompt) +
            estimateTokens(thread.rollingSummary) +
            recentSlice.sumOf { estimateTokens(it.content) }

        return ConversationMemorySnapshot(
            totalStoredMessages = messages.size,
            summarizedMessageCount = thread.summarizedMessageCount.coerceAtMost(messages.size),
            activeWindowMessages = recentSlice.size,
            estimatedPromptTokens = tokens,
            maxContextTokenBudget = MAX_CONTEXT_TOKENS,
            rollingSummary = thread.rollingSummary
        )
    }

    /**
     * Loads full Room history for a resumed or active thread, automatically compacts older turns
     * into `ChatThreadEntity.rollingSummary` when limits are exceeded, and returns the exact
     * system prompt + sliding window payload to send to the LLM.
     */
    suspend fun buildContextAndCompactIfNeeded(
        threadId: Long,
        forceCompaction: Boolean = false
    ): CompiledConversationContext {
        var thread = chatDao.getThread(threadId)
            ?: return CompiledConversationContext(
                effectiveSystemPrompt = "",
                windowMessages = emptyList(),
                snapshot = ConversationMemorySnapshot()
            )

        val allMessages = chatDao.getMessagesOnce(threadId)
            .filter { it.content.isNotBlank() }

        val alreadySummarized = thread.summarizedMessageCount.coerceIn(0, allMessages.size)
        val unsummarized = allMessages.drop(alreadySummarized)
        val unsummarizedTokens = unsummarized.sumOf { estimateTokens(it.content) }

        val windowLimit = activeWindowLimit()
        val needsCompaction = if (forceCompaction) {
            unsummarized.size > MIN_KEEP_RECENT_ON_FORCE
        } else {
            unsummarized.size > windowLimit || unsummarizedTokens > SUMMARY_TRIGGER_TOKENS
        }

        if (needsCompaction) {
            val keepRecentCount = if (forceCompaction) MIN_KEEP_RECENT_ON_FORCE else (windowLimit / 2).coerceAtLeast(2)
            val overflowCount = (unsummarized.size - keepRecentCount).coerceAtLeast(1)
            val messagesToCompress = unsummarized.take(overflowCount)

            val updatedSummary = try {
                aiClient.summarizeConversationHistory(
                    existingSummary = thread.rollingSummary,
                    messagesToCompress = messagesToCompress.map {
                        OpenAiChatMessage(role = it.role, content = it.content)
                    }
                )
            } catch (_: Exception) {
                buildExtractiveFallbackSummary(thread.rollingSummary, messagesToCompress)
            }

            val newSummarizedCount = (alreadySummarized + messagesToCompress.size).coerceAtMost(allMessages.size)
            chatDao.updateThreadSummary(
                threadId = thread.id,
                summary = updatedSummary,
                summarizedCount = newSummarizedCount
            )
            thread = thread.copy(
                rollingSummary = updatedSummary,
                summarizedMessageCount = newSummarizedCount
            )
        }

        val activeWindowEntities = computeSlidingWindow(allMessages, thread.summarizedMessageCount)
        val fittedWindow = trimWindowToTokenBudget(
            messages = activeWindowEntities,
            reservedTokens = estimateTokens(thread.systemPrompt) + estimateTokens(thread.rollingSummary) + 250
        )

        val effectiveSystem = buildEffectiveSystemPrompt(
            baseSystemPrompt = thread.systemPrompt,
            rollingSummary = thread.rollingSummary
        )

        val totalTokens = estimateTokens(effectiveSystem) + fittedWindow.sumOf { estimateTokens(it.content) }
        val snapshot = ConversationMemorySnapshot(
            totalStoredMessages = allMessages.size,
            summarizedMessageCount = thread.summarizedMessageCount.coerceAtMost(allMessages.size),
            activeWindowMessages = fittedWindow.size,
            estimatedPromptTokens = totalTokens,
            maxContextTokenBudget = MAX_CONTEXT_TOKENS,
            rollingSummary = thread.rollingSummary
        )

        return CompiledConversationContext(
            effectiveSystemPrompt = effectiveSystem,
            windowMessages = fittedWindow.map { OpenAiChatMessage(role = it.role, content = it.content) },
            snapshot = snapshot
        )
    }

    private fun computeSlidingWindow(
        allMessages: List<ChatMessageEntity>,
        summarizedCount: Int
    ): List<ChatMessageEntity> {
        val windowLimit = activeWindowLimit()
        val safeStart = summarizedCount.coerceIn(0, allMessages.size)
        val unsummarized = allMessages.drop(safeStart)
        return if (unsummarized.size <= windowLimit) {
            unsummarized
        } else {
            unsummarized.takeLast(windowLimit)
        }
    }

    private fun trimWindowToTokenBudget(
        messages: List<ChatMessageEntity>,
        reservedTokens: Int
    ): List<ChatMessageEntity> {
        val availableBudget = (MAX_CONTEXT_TOKENS - reservedTokens).coerceAtLeast(512)
        val selected = mutableListOf<ChatMessageEntity>()
        var running = 0
        for (msg in messages.asReversed()) {
            val cost = estimateTokens(msg.content) + 8
            if (selected.isNotEmpty() && running + cost > availableBudget) {
                break
            }
            selected.add(msg)
            running += cost
        }
        return selected.asReversed()
    }

    private fun buildEffectiveSystemPrompt(baseSystemPrompt: String, rollingSummary: String): String {
        if (rollingSummary.isBlank()) return baseSystemPrompt
        return buildString {
            if (baseSystemPrompt.isNotBlank()) {
                appendLine(baseSystemPrompt.trim())
                appendLine()
            }
            appendLine("[CONVERSATION MEMORY SUMMARY — EARLIER TURNS]")
            appendLine(rollingSummary.trim())
            appendLine("[END MEMORY SUMMARY — Use the facts and decisions above seamlessly alongside the recent messages below.]")
        }
    }

    private fun buildExtractiveFallbackSummary(
        existingSummary: String,
        messages: List<ChatMessageEntity>
    ): String {
        val digestLines = messages.map { msg ->
            val speaker = if (msg.role == "user") "User" else "Assistant"
            val snippet = msg.content.replace("\n", " ").trim().take(160)
            "- $speaker: $snippet"
        }
        return buildString {
            if (existingSummary.isNotBlank()) {
                appendLine(existingSummary.trim().takeLast(900))
            }
            digestLines.forEach { appendLine(it) }
        }.trim().takeLast(1800)
    }

    companion object {
        const val SLIDING_WINDOW_MESSAGES = 12
        const val MIN_KEEP_RECENT_ON_FORCE = 4
        const val SUMMARY_TRIGGER_TOKENS = 2800
        const val MAX_CONTEXT_TOKENS = 4096

        fun estimateTokens(text: String): Int {
            if (text.isBlank()) return 0
            return ((text.length + 3) / 4).coerceAtLeast(1)
        }
    }
}
