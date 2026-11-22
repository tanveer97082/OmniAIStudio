package com.omniai.studio.media

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

/**
 * On-Device Audio Studio for Tab 3 (Media Studio):
 * 1. Records custom `.m4a` voiceovers using native Android `MediaRecorder` (`RECORD_AUDIO` permission).
 * 2. Extracts the audio track from any existing video into `.m4a` using AndroidX Media3 1.2.1 `Transformer`.
 */
class AudioStudioHelper(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var activeOutputFile: File? = null

    fun isRecording(): Boolean = recorder != null

    fun startVoiceoverRecording(): Result<File> {
        return runCatching {
            stopVoiceoverRecording()
            val dir = File(context.getExternalFilesDir("voiceovers") ?: context.filesDir, "takes")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "voiceover_${System.currentTimeMillis()}.m4a")

            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            activeOutputFile = outFile
            outFile
        }
    }

    fun stopVoiceoverRecording(): File? {
        val current = recorder ?: return null
        val output = activeOutputFile
        return try {
            current.stop()
            current.release()
            recorder = null
            activeOutputFile = null
            output?.takeIf { it.exists() && it.length() > 0L }
        } catch (_: Exception) {
            runCatching { current.release() }
            recorder = null
            activeOutputFile = null
            null
        }
    }

    /**
     * Extracts audio-only `.m4a` from a source video URI using AndroidX Media3 Transformer.
     */
    fun extractAudioFromVideo(
        sourceVideoUri: Uri,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                val dir = File(context.getExternalFilesDir("voiceovers") ?: context.filesDir, "extracted")
                if (!dir.exists()) dir.mkdirs()
                val outFile = File(dir, "extracted_audio_${System.currentTimeMillis()}.m4a")

                val mediaItem = MediaItem.fromUri(sourceVideoUri)
                val audioOnlyItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveVideo(true)
                    .build()

                val transformer = Transformer.Builder(context)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            onSuccess(outFile)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            onError(exportException.localizedMessage ?: "Audio extraction failed.")
                        }
                    })
                    .build()

                transformer.start(audioOnlyItem, outFile.absolutePath)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to initialize Media3 audio extractor.")
            }
        }
    }
}
