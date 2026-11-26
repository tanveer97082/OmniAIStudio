package com.omniai.studio.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class VideoCompressionPreset(
    val label: String,
    val summary: String,
    val bitrateBps: Int,
    val targetHeightPx: Int?
) {
    HIGH_QUALITY(
        label = "High Quality (4 Mbps)",
        summary = "4 Mbps H.264 · Original Resolution",
        bitrateBps = 4_000_000,
        targetHeightPx = null
    ),
    BALANCED_REELS(
        label = "Balanced Reels (2 Mbps)",
        summary = "2 Mbps H.264 · Reels / Shorts Ideal",
        bitrateBps = 2_000_000,
        targetHeightPx = null
    ),
    SAVER_480P(
        label = "Telegram/WhatsApp Saver (1 Mbps, 480p)",
        summary = "1 Mbps H.264 · Scaled 480p Fast Share",
        bitrateBps = 1_000_000,
        targetHeightPx = 480
    )
}

data class VideoCompressionResult(
    val outputFile: File,
    val galleryUri: Uri?,
    val originalBytes: Long,
    val compressedBytes: Long,
    val preset: VideoCompressionPreset
) {
    val savingsPercent: Int
        get() = if (originalBytes > 0L) {
            (((originalBytes - compressedBytes).toDouble() / originalBytes.toDouble()) * 100.0)
                .toInt()
                .coerceIn(-100, 99)
        } else 0
}

@OptIn(UnstableApi::class)
class Media3VideoCompressor(private val context: Context) {

    suspend fun compressVideo(
        sourceUri: Uri,
        preset: VideoCompressionPreset,
        onProgress: (Int) -> Unit
    ): VideoCompressionResult = withContext(Dispatchers.Main) {
        val originalSizeBytes = withContext(Dispatchers.IO) { resolveUriSizeBytes(sourceUri) }
        val outDir = File(context.filesDir, "merged").apply { mkdirs() }
        val tag = when (preset) {
            VideoCompressionPreset.HIGH_QUALITY -> "4mbps"
            VideoCompressionPreset.BALANCED_REELS -> "2mbps"
            VideoCompressionPreset.SAVER_480P -> "1mbps_480p"
        }
        val outputName = "OmniAI_Compressed_${tag}_${System.currentTimeMillis()}.mp4"
        val outputFile = File(outDir, outputName)
        if (outputFile.exists()) outputFile.delete()

        val mediaItem = MediaItem.fromUri(sourceUri)
        val editedItem = EditedMediaItem.Builder(mediaItem).build()

        val encoderSettings = VideoEncoderSettings.Builder()
            .setBitrate(preset.bitrateBps)
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(encoderSettings)
            .build()

        val progressHolder = ProgressHolder()

        suspendCancellableCoroutine<Unit> { continuation ->
            var transformer: Transformer? = null

            val progressJob = launch(Dispatchers.Main) {
                while (isActive) {
                    delay(200L)
                    val currentTransformer = transformer
                    if (currentTransformer != null) {
                        val state = currentTransformer.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(progressHolder.progress.coerceIn(0, 99))
                        }
                    }
                }
            }

            val createdTransformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        progressJob.cancel()
                        onProgress(100)
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        progressJob.cancel()
                        if (continuation.isActive) {
                            continuation.resumeWithException(exportException)
                        }
                    }
                })
                .build()

            transformer = createdTransformer

            continuation.invokeOnCancellation {
                progressJob.cancel()
                transformer?.cancel()
            }

            createdTransformer.start(editedItem, outputFile.absolutePath)
        }

        val galleryUri = withContext(Dispatchers.IO) {
            StorageGalleryHelper.saveVideoToPublicGallery(context, outputFile, outputName)
        }

        VideoCompressionResult(
            outputFile = outputFile,
            galleryUri = galleryUri,
            originalBytes = originalSizeBytes,
            compressedBytes = outputFile.length(),
            preset = preset
        )
    }

    private fun resolveUriSizeBytes(uri: Uri): Long {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).length() } ?: 0L
        }
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !cursor.isNull(idx)) {
                    return cursor.getLong(idx)
                }
            }
        }
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        }.getOrDefault(0L)
    }
}
