package com.omniai.studio.media

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Offline Android TextToSpeech wrapper that auto-detects Hindi (Devanagari Unicode block)
 * vs English replies, cleans markdown code blocks, and applies configurable playback speed.
 */
class OfflineTtsHelper(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady: Boolean = false

    private val _speakingMessageId = MutableStateFlow<Long?>(null)
    val speakingMessageId: StateFlow<Long?> = _speakingMessageId.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        _speakingMessageId.value = null
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _speakingMessageId.value = null
                    }
                })
            }
        }
    }

    fun speak(messageId: Long, rawText: String, speechRate: Float = 1.0f) {
        val engine = tts ?: return
        if (!isReady) return

        val cleaned = sanitizeForSpeech(rawText)
        if (cleaned.isBlank()) return

        val targetLocale = if (containsDevanagari(cleaned)) {
            Locale("hi", "IN")
        } else {
            Locale.US
        }

        val langResult = engine.setLanguage(targetLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.getDefault()
        }

        engine.setSpeechRate(speechRate.coerceIn(0.75f, 1.5f))
        _speakingMessageId.value = messageId
        engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "omniai_${UUID.randomUUID()}")
    }

    fun stop() {
        tts?.stop()
        _speakingMessageId.value = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        _speakingMessageId.value = null
    }

    companion object {
        fun containsDevanagari(text: String): Boolean =
            text.any { ch -> ch in '\u0900'..'\u097F' }

        fun sanitizeForSpeech(raw: String): String = raw
            .replace(Regex("```[\\s\\S]*?```"), " Code block omitted. ")
            .replace(Regex("[`*_#>]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
